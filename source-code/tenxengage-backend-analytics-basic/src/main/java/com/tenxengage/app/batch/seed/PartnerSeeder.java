package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.FiscalQuarter;
import com.tenxengage.app.batch.seed.SeedRecords.PartnerLocationRef;
import com.tenxengage.app.batch.seed.SeedRecords.PartnerSets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.tenxengage.app.batch.seed.SeedConstants.*;

/**
 * Creates partner companies with dual-level location assignments (Region + Country).
 * Location assignments live in the partner_company_locations junction table referencing
 * location_values; partner_companies has no region column.
 */
@Component
public class PartnerSeeder {

    private static final Logger log = LoggerFactory.getLogger(PartnerSeeder.class);

    private final JdbcTemplate jdbc;

    public PartnerSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Creates partners distributed across fiscal quarters with growth curve.
     * Returns PartnerSets + populates partnerRegionMap and partnerLocationMap.
     */
    public PartnerSets createPartners(UUID clientId, List<FiscalQuarter> quarters, Random random,
                                      Map<UUID, String> partnerRegionMap,
                                      Map<UUID, PartnerLocationRef> partnerLocationMap) {
        List<UUID> enrolledIds = new ArrayList<>(ENROLLED_PARTNER_COUNT);
        List<UUID> nonEnrolledIds = new ArrayList<>(TOTAL_PARTNER_COUNT - ENROLLED_PARTNER_COUNT);
        List<UUID> allIds = new ArrayList<>(TOTAL_PARTNER_COUNT);
        Map<UUID, Timestamp> creationDates = new HashMap<>();

        // Build quarter boundaries
        List<LocalDate[]> quarterRanges = new ArrayList<>();
        LocalDate seedEnd = LocalDate.now();
        LocalDate qStart = FiscalQuarterCalculator.getSeedStartDate().withDayOfMonth(1);
        while (!qStart.isAfter(seedEnd)) {
            int qIdx = (qStart.getMonthValue() - 1) / 3;
            LocalDate qEnd = LocalDate.of(qStart.getYear(), qIdx * 3 + 1, 1).plusMonths(3).minusDays(1);
            if (qEnd.isAfter(seedEnd)) qEnd = seedEnd;
            quarterRanges.add(new LocalDate[]{qStart, qEnd});
            qStart = qEnd.plusDays(1);
        }
        int numQuarters = quarterRanges.size();

        // Growth-curve weights
        double[] qWeights = new double[numQuarters];
        for (int q = 0; q < numQuarters; q++) {
            qWeights[q] = 0.5 + 1.0 * q / (numQuarters - 1);
        }
        double totalWeight = 0;
        for (double w : qWeights) totalWeight += w;

        int[] enrolledPerQ = distributeCount(ENROLLED_PARTNER_COUNT, qWeights, totalWeight, numQuarters);

        double[] nonEnrolledWeights = new double[numQuarters];
        for (int q = 0; q < numQuarters; q++) {
            nonEnrolledWeights[q] = 0.3 + 1.2 * q / (numQuarters - 1);
        }
        double neTotal = 0;
        for (double w : nonEnrolledWeights) neTotal += w;
        int[] nonEnrolledPerQ = distributeCount(TOTAL_PARTNER_COUNT - ENROLLED_PARTNER_COUNT,
                nonEnrolledWeights, neTotal, numQuarters);

        // Resolve location value UUIDs for regions and countries
        Map<String, UUID> regionValueIds = resolveLocationValues(clientId, 0);
        Map<String, UUID> countryValueIds = resolveLocationValues(clientId, 1);

        List<Object[]> partnerBatch = new ArrayList<>(TOTAL_PARTNER_COUNT);
        List<Object[]> locationBatch = new ArrayList<>(TOTAL_PARTNER_COUNT * 2);
        int partnerIdx = 0;

        // Create enrolled partners
        for (int q = 0; q < numQuarters; q++) {
            LocalDate[] range = quarterRanges.get(q);
            int daySpan = Math.max(1, (int) (range[1].toEpochDay() - range[0].toEpochDay()));
            for (int p = 0; p < enrolledPerQ[q]; p++) {
                UUID id = UUID.randomUUID();
                enrolledIds.add(id);
                allIds.add(id);
                LocalDate createdDate = range[0].plusDays(random.nextInt(daySpan + 1));
                Timestamp createdAt = Timestamp.from(createdDate.atStartOfDay(ZoneOffset.UTC).toInstant());
                creationDates.put(id, createdAt);
                partnerBatch.add(buildPartnerRow(id, clientId, partnerIdx, "ACTIVE", createdAt));
                addLocationAssignments(id, clientId, partnerIdx, random, createdAt,
                        locationBatch, partnerRegionMap, partnerLocationMap,
                        regionValueIds, countryValueIds);
                partnerIdx++;
            }
        }

        // Create non-enrolled partners
        for (int q = 0; q < numQuarters; q++) {
            LocalDate[] range = quarterRanges.get(q);
            int daySpan = Math.max(1, (int) (range[1].toEpochDay() - range[0].toEpochDay()));
            for (int p = 0; p < nonEnrolledPerQ[q]; p++) {
                UUID id = UUID.randomUUID();
                nonEnrolledIds.add(id);
                allIds.add(id);
                LocalDate createdDate = range[0].plusDays(random.nextInt(daySpan + 1));
                Timestamp createdAt = Timestamp.from(createdDate.atStartOfDay(ZoneOffset.UTC).toInstant());
                creationDates.put(id, createdAt);
                String status = (partnerIdx % 100 < 95) ? "ACTIVE" : "INACTIVE";
                partnerBatch.add(buildPartnerRow(id, clientId, partnerIdx, status, createdAt));
                addLocationAssignments(id, clientId, partnerIdx, random, createdAt,
                        locationBatch, partnerRegionMap, partnerLocationMap,
                        regionValueIds, countryValueIds);
                partnerIdx++;
            }
        }

        jdbc.batchUpdate("INSERT INTO partner_companies " +
                "(id, name, client_id, status, contact_phone, website, " +
                "external_partner_id, metadata, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)", partnerBatch);

        // Insert dual-level location assignments (Region + Country)
        batchInsert("INSERT INTO partner_company_locations " +
                "(id, client_id, partner_company_id, location_value_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)", locationBatch);

        log.info("Created {} partners ({} enrolled, {} non-enrolled) with {} location assignments",
                allIds.size(), enrolledIds.size(), nonEnrolledIds.size(), locationBatch.size());

        return new PartnerSets(enrolledIds, nonEnrolledIds, allIds, creationDates);
    }

    private Object[] buildPartnerRow(UUID id, UUID clientId, int idx, String status, Timestamp createdAt) {
        String name = PARTNER_PREFIXES[idx % PARTNER_PREFIXES.length] + " "
                + PARTNER_SUFFIXES[idx / PARTNER_PREFIXES.length % PARTNER_SUFFIXES.length];
        if (idx >= PARTNER_PREFIXES.length * PARTNER_SUFFIXES.length) {
            name = name + " " + (idx + 1);
        }
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        String email = "info@" + slug + ".example.com";
        String partnerType = PARTNER_TYPES[idx % PARTNER_TYPES.length];
        String externalPartnerId = String.format("EXT-%s-%03d",
                partnerType.substring(0, 3).toUpperCase(), idx + 1);
        String website = "https://www." + slug + WEBSITES_TLD[idx % WEBSITES_TLD.length];
        String phone = "+1-" + AREA_CODES[idx % AREA_CODES.length] + "-"
                + String.format("%03d", 100 + (idx * 7) % 900) + "-"
                + String.format("%04d", 1000 + (idx * 13) % 9000);
        String partnerMetadata = String.format(
                "{\"Partner Type\":\"%s\",\"Contact Email\":\"%s\"}",
                partnerType, email);
        return new Object[]{id, name, clientId, status, phone, website,
                externalPartnerId, partnerMetadata, createdAt, createdAt};
    }

    /**
     * Assigns Region (depth-0) AND Country (depth-1) location to a partner.
     * Country is selected using weighted distribution within the region.
     */
    private void addLocationAssignments(UUID partnerId, UUID clientId, int idx, Random random,
                                        Timestamp createdAt, List<Object[]> locationBatch,
                                        Map<UUID, String> partnerRegionMap,
                                        Map<UUID, PartnerLocationRef> partnerLocationMap,
                                        Map<String, UUID> regionValueIds,
                                        Map<String, UUID> countryValueIds) {
        String regionName = REGIONS[idx % REGIONS.length];
        UUID regionValueId = regionValueIds.get(regionName);

        // Pick country within region using weighted distribution
        String countryName = pickCountryInRegion(regionName, random);
        UUID countryValueId = countryValueIds.get(countryName);

        partnerRegionMap.put(partnerId, regionName);

        if (regionValueId != null) {
            locationBatch.add(new Object[]{UUID.randomUUID(), clientId, partnerId, regionValueId, createdAt, createdAt});
        }
        if (countryValueId != null) {
            locationBatch.add(new Object[]{UUID.randomUUID(), clientId, partnerId, countryValueId, createdAt, createdAt});
            partnerLocationMap.put(partnerId, new PartnerLocationRef(
                    regionValueId, regionName, countryValueId, countryName));
        } else if (regionValueId != null) {
            partnerLocationMap.put(partnerId, new PartnerLocationRef(
                    regionValueId, regionName, null, null));
        }
    }

    /** Pick a country within a region using weighted distribution. */
    private String pickCountryInRegion(String region, Random random) {
        String[] countries = COUNTRIES_BY_REGION.get(region);
        double[] weights = COUNTRY_WEIGHTS.get(region);
        if (countries == null || weights == null) return null;

        double r = random.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < Math.min(countries.length, weights.length); i++) {
            cumulative += weights[i];
            if (r < cumulative) return countries[i];
        }
        return countries[countries.length - 1];
    }

    /** Resolve location_value names → UUIDs at a given depth level. */
    public Map<String, UUID> resolveLocationValues(UUID clientId, int depth) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT lv.id, lv.name FROM location_values lv " +
                "JOIN location_levels ll ON ll.id = lv.level_id " +
                "WHERE lv.client_id = ? AND ll.depth = ?",
                clientId, depth);
        Map<String, UUID> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("name"), (UUID) row.get("id"));
        }
        return result;
    }

    /** Resolve region_level UUID for the client (depth=0). */
    public UUID resolveRegionLevelId(UUID clientId) {
        return jdbc.queryForObject(
                "SELECT id FROM location_levels WHERE client_id = ? AND depth = 0",
                UUID.class, clientId);
    }

    /** Resolve country_level UUID for the client (depth=1). */
    public UUID resolveCountryLevelId(UUID clientId) {
        return jdbc.queryForObject(
                "SELECT id FROM location_levels WHERE client_id = ? AND depth = 1",
                UUID.class, clientId);
    }

    /**
     * Resolve a map of region name → list of country names under that region.
     * Used by the incentive seeder to write country-level audience rules
     * alongside the region-level ones. Derived at runtime from the seeded
     * {@code location_values} so the map stays in sync if the seed data
     * evolves (vs. a hard-coded map that would silently drift).
     */
    public Map<String, List<String>> resolveRegionToCountries(UUID clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT region.name AS region_name, country.name AS country_name " +
                "FROM location_values country " +
                "JOIN location_values region ON region.id = country.parent_id " +
                "JOIN location_levels country_level ON country_level.id = country.level_id " +
                "JOIN location_levels region_level ON region_level.id = region.level_id " +
                "WHERE country.client_id = ? " +
                "  AND country_level.depth = 1 " +
                "  AND region_level.depth = 0 " +
                "ORDER BY region.name, country.name",
                clientId);
        Map<String, List<String>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String regionName = (String) row.get("region_name");
            String countryName = (String) row.get("country_name");
            result.computeIfAbsent(regionName, k -> new ArrayList<>()).add(countryName);
        }
        return result;
    }

    static int[] distributeCount(int total, double[] weights, double totalWeight, int buckets) {
        int[] result = new int[buckets];
        int remaining = total;
        for (int i = 0; i < buckets - 1; i++) {
            result[i] = (int) Math.round(total * weights[i] / totalWeight);
            remaining -= result[i];
        }
        result[buckets - 1] = Math.max(0, remaining);
        return result;
    }

    private void batchInsert(String sql, List<Object[]> batch) {
        for (int i = 0; i < batch.size(); i += BATCH_SIZE) {
            List<Object[]> chunk = batch.subList(i, Math.min(i + BATCH_SIZE, batch.size()));
            jdbc.batchUpdate(sql, chunk);
        }
    }
}
