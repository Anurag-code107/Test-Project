package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.FiscalQuarter;
import com.tenxengage.app.batch.seed.SeedRecords.NonSalesIncentiveRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

/**
 * Every seeded incentive that gets a Region-level LOCATION audience rule must
 * also receive Country-level LOCATION rules for every country under each
 * chosen region. Without this, the (required) Country MultiSelect in the
 * builder's Step 3 renders empty when a Client Admin clones or edits a seeded
 * incentive.
 *
 * The {@code IncentiveSeeder.insertCountryAudienceRules} helper is the single
 * write point for country-level rows. This test verifies it directly (level
 * UUID, value UUID, region-to-country mapping) plus a high-level seeder-path
 * assertion via {@code createJourneyIncentives}.
 */
@ExtendWith(MockitoExtension.class)
class SeedAudienceLocationIntegrityTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void insertCountryAudienceRules_writesOneRowPerSuppliedCountryName() {
        IncentiveSeeder seeder = new IncentiveSeeder(jdbc);

        UUID incentiveId = UUID.randomUUID();
        UUID countryLevelId = UUID.randomUUID();
        UUID usaId = UUID.randomUUID();
        UUID canadaId = UUID.randomUUID();
        UUID mexicoId = UUID.randomUUID();
        UUID germanyId = UUID.randomUUID();

        Map<String, UUID> countryValueIds = Map.of(
                "United States", usaId,
                "Canada", canadaId,
                "Mexico", mexicoId,
                "Germany", germanyId);

        Timestamp now = Timestamp.from(Instant.parse("2026-05-01T00:00:00Z"));

        seeder.insertCountryAudienceRules(
                incentiveId,
                List.of("United States", "Canada", "Mexico"),
                countryLevelId, countryValueIds, now);

        // Capture every audience-rule INSERT and bucket by the level + value-id pair.
        Collection<Invocation> invocations = mockingDetails(jdbc).getInvocations();
        Set<String> writtenCountryValueIds = new HashSet<>();
        for (Invocation inv : invocations) {
            if (!"update".equals(inv.getMethod().getName())) continue;
            Object[] rawArgs = inv.getArguments();
            if (rawArgs.length < 2 || !(rawArgs[0] instanceof String)) continue;
            String sql = (String) rawArgs[0];
            Object[] userArgs = (rawArgs.length == 2 && rawArgs[1] instanceof Object[])
                    ? (Object[]) rawArgs[1]
                    : Arrays.copyOfRange(rawArgs, 1, rawArgs.length);

            if (sql.startsWith("INSERT INTO incentive_audience_rules")
                    && userArgs.length >= 5
                    && "LOCATION".equals(userArgs[2])
                    && countryLevelId.equals(userArgs[4])) {
                writtenCountryValueIds.add((String) userArgs[3]);
            }
        }

        assertThat(writtenCountryValueIds)
                .as("Each supplied country name should produce one LOCATION row "
                        + "at the country level UUID")
                .containsExactlyInAnyOrder(
                        usaId.toString(),
                        canadaId.toString(),
                        mexicoId.toString());
        // Germany was not in the supplied country list.
        assertThat(writtenCountryValueIds).doesNotContain(germanyId.toString());
    }

    @Test
    void insertCountryAudienceRules_skipsCountryNamesNotPresentInValueIdsMap() {
        // Defensive: if a country name reaches the writer that doesn't have a
        // location_values row, the seeder must skip it silently rather than
        // crash. (Same shape as insertLocationAudienceRules for region-level
        // rows.)
        IncentiveSeeder seeder = new IncentiveSeeder(jdbc);

        UUID incentiveId = UUID.randomUUID();
        UUID countryLevelId = UUID.randomUUID();
        UUID usaId = UUID.randomUUID();
        Map<String, UUID> countryValueIds = Map.of("United States", usaId);
        Timestamp now = Timestamp.from(Instant.parse("2026-05-01T00:00:00Z"));

        seeder.insertCountryAudienceRules(
                incentiveId, List.of("United States", "Atlantis"),
                countryLevelId, countryValueIds, now);

        Collection<Invocation> invocations = mockingDetails(jdbc).getInvocations();
        long countryRows = invocations.stream()
                .filter(inv -> "update".equals(inv.getMethod().getName()))
                .filter(inv -> inv.getArguments()[0] instanceof String s
                        && s.startsWith("INSERT INTO incentive_audience_rules"))
                .count();
        assertThat(countryRows)
                .as("Atlantis is not in the value-id map so it must be silently "
                        + "skipped, leaving exactly one country row (United States)")
                .isEqualTo(1L);
    }

    @Test
    void insertCountryAudienceRules_isANoOpWhenInputsAreEmpty() {
        IncentiveSeeder seeder = new IncentiveSeeder(jdbc);
        UUID incentiveId = UUID.randomUUID();
        UUID countryLevelId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2026-05-01T00:00:00Z"));

        // Empty country list — nothing to write.
        seeder.insertCountryAudienceRules(incentiveId, List.of(),
                countryLevelId, Map.of(), now);
        // Null country list — defensive guard.
        seeder.insertCountryAudienceRules(incentiveId, null,
                countryLevelId, Map.of(), now);
        // Null country-level UUID — also a no-op (defensive against partial wiring).
        seeder.insertCountryAudienceRules(incentiveId, List.of("United States"),
                null, Map.of(), now);

        Collection<Invocation> invocations = mockingDetails(jdbc).getInvocations();
        long updates = invocations.stream()
                .filter(inv -> "update".equals(inv.getMethod().getName()))
                .count();
        assertThat(updates).isZero();
    }

    @Test
    void pickCountriesForRegions_returnsAllCountriesUnderTheChosenRegionsSometimes() {
        // The picker rolls 40% chance of returning the full union and a
        // uniform-random subset otherwise. With 200 trials the full-union
        // path must fire at least once AND a non-full subset must fire at
        // least once — proves both branches are reachable, without
        // over-constraining the exact split.
        IncentiveSeeder seeder = new IncentiveSeeder(jdbc);
        Map<String, List<String>> regionToCountries = Map.of(
                "AMERICAS", List.of("United States", "Canada", "Mexico", "Brazil"));

        boolean sawFull = false;
        boolean sawSubset = false;
        for (int i = 0; i < 200; i++) {
            Random random = new Random(i);
            List<String> picked = seeder.pickCountriesForRegions(
                    List.of("AMERICAS"), regionToCountries, random);
            assertThat(picked)
                    .as("Picked countries must always be a subset of the "
                            + "available pool (no fabrication)")
                    .isSubsetOf("United States", "Canada", "Mexico", "Brazil");
            assertThat(picked)
                    .as("Picked countries must be deduplicated")
                    .doesNotHaveDuplicates();
            assertThat(picked)
                    .as("Picker never returns empty when regions have "
                            + "children countries")
                    .isNotEmpty();
            if (picked.size() == 4) sawFull = true;
            else sawSubset = true;
        }
        assertThat(sawFull)
                .as("Across 200 seeds, the full-union branch must fire at "
                        + "least once")
                .isTrue();
        assertThat(sawSubset)
                .as("Across 200 seeds, the random-subset branch must fire "
                        + "at least once — proving the count varies per "
                        + "incentive instead of always returning all "
                        + "countries")
                .isTrue();
    }

    @Test
    void pickCountriesForRegions_returnsEmptyForEmptyRegionsOrUnknownRegions() {
        IncentiveSeeder seeder = new IncentiveSeeder(jdbc);
        Random random = new Random(0);

        assertThat(seeder.pickCountriesForRegions(
                List.of(), Map.of("AMERICAS", List.of("United States")), random))
                .as("Empty region list → no countries picked")
                .isEmpty();
        assertThat(seeder.pickCountriesForRegions(
                null, Map.of("AMERICAS", List.of("United States")), random))
                .as("Null region list → no countries picked (defensive)")
                .isEmpty();
        assertThat(seeder.pickCountriesForRegions(
                List.of("ATLANTIS"), Map.of("AMERICAS", List.of("United States")), random))
                .as("Region with no children in the map → empty")
                .isEmpty();
    }

    @Test
    void pickCountriesForRegions_isDeterministicForTheSameSeed() {
        // Same seed → same picked set, so the seed run is reproducible across
        // restarts (matches the rest of the seeder's deterministic shape).
        IncentiveSeeder seeder = new IncentiveSeeder(jdbc);
        Map<String, List<String>> regionToCountries = Map.of(
                "AMERICAS", List.of("United States", "Canada", "Mexico"),
                "EMEAR", List.of("Germany", "France", "United Kingdom"));

        List<String> first = seeder.pickCountriesForRegions(
                List.of("AMERICAS", "EMEAR"), regionToCountries, new Random(42L));
        List<String> second = seeder.pickCountriesForRegions(
                List.of("AMERICAS", "EMEAR"), regionToCountries, new Random(42L));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void createJourneyIncentives_writesBothRegionAndCountryAudienceRulesPerJourney() {
        // High-level path assertion: end-to-end, every Journey emitted by the
        // seeder gets BOTH a region row AND ≥1 country row whose level_id
        // matches the country level. Mirrors the shape of
        // JourneyStageRegionIntegrityTest but asserts against the country axis.
        IncentiveSeeder seeder = spy(new IncentiveSeeder(jdbc));
        doReturn(Map.of(
                SeedConstants.ROLE_COMPANY_ADMIN, UUID.randomUUID(),
                SeedConstants.ROLE_PARTNER_SELLER, UUID.randomUUID()
        )).when(seeder).loadRoleIdsByName(any());

        UUID clientId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        UUID regionLevelId = UUID.randomUUID();
        UUID countryLevelId = UUID.randomUUID();

        Map<String, UUID> regionValueIds = new HashMap<>();
        for (String r : SeedConstants.REGIONS) {
            regionValueIds.put(r, UUID.randomUUID());
        }
        // Two countries under each region; the test only cares that ≥1 country
        // row is written per Journey, not which countries specifically.
        Map<String, UUID> countryValueIds = new HashMap<>();
        Map<String, List<String>> regionToCountries = new HashMap<>();
        for (String r : SeedConstants.REGIONS) {
            String c1 = r + "_country_1";
            String c2 = r + "_country_2";
            countryValueIds.put(c1, UUID.randomUUID());
            countryValueIds.put(c2, UUID.randomUUID());
            regionToCountries.put(r, List.of(c1, c2));
        }

        List<FiscalQuarter> quarters = new ArrayList<>();
        List<NonSalesIncentiveRef> refs = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 2, 1);
        for (int q = 0; q < 6; q++) {
            FiscalQuarter fq = new FiscalQuarter(
                    2026 + (q / 4), "Q" + ((q % 4) + 1),
                    start.plusMonths(q * 3L),
                    start.plusMonths(q * 3L + 3).minusDays(1));
            quarters.add(fq);
            for (String region : SeedConstants.REGIONS) {
                refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "TRAINING", fq, region));
                refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "ACTIVITY", fq, region));
            }
        }

        Random random = new Random(42L);
        seeder.createJourneyIncentives(clientId, adminUserId, quarters, refs,
                regionLevelId, regionValueIds,
                countryLevelId, countryValueIds, regionToCountries, random);

        // Bucket every LOCATION audience rule by (incentive_id, level_id) so we
        // can confirm every Journey id has at least one row at each level.
        Collection<Invocation> invocations = mockingDetails(jdbc).getInvocations();
        Map<UUID, Set<UUID>> levelsByIncentive = new HashMap<>();
        Set<UUID> journeyIds = new HashSet<>();

        for (Invocation inv : invocations) {
            if (!"update".equals(inv.getMethod().getName())) continue;
            Object[] rawArgs = inv.getArguments();
            if (rawArgs.length < 2 || !(rawArgs[0] instanceof String)) continue;
            String sql = (String) rawArgs[0];
            Object[] userArgs = (rawArgs.length == 2 && rawArgs[1] instanceof Object[])
                    ? (Object[]) rawArgs[1]
                    : Arrays.copyOfRange(rawArgs, 1, rawArgs.length);

            if (sql.startsWith("INSERT INTO incentives ")
                    && userArgs.length >= 4
                    && "JOURNEY".equals(userArgs[3])) {
                journeyIds.add((UUID) userArgs[0]);
            } else if (sql.startsWith("INSERT INTO incentive_audience_rules")
                    && userArgs.length >= 5
                    && "LOCATION".equals(userArgs[2])) {
                UUID incId = (UUID) userArgs[1];
                UUID lvlId = (UUID) userArgs[4];
                levelsByIncentive.computeIfAbsent(incId, k -> new HashSet<>()).add(lvlId);
            }
        }

        assertThat(journeyIds)
                .as("Setup must seed at least one Journey for the assertion to be meaningful")
                .isNotEmpty();

        for (UUID jid : journeyIds) {
            Set<UUID> levels = levelsByIncentive.getOrDefault(jid, Set.of());
            assertThat(levels)
                    .as("Journey %s must have a region-level LOCATION rule", jid)
                    .contains(regionLevelId);
            assertThat(levels)
                    .as("Journey %s must also have country-level LOCATION rule(s) "
                            + "so the Country MultiSelect renders pills on clone/edit", jid)
                    .contains(countryLevelId);
        }
    }
}
