package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.CourseCompletionRecord;
import com.tenxengage.app.batch.seed.SeedRecords.FiscalQuarter;
import com.tenxengage.app.batch.seed.SeedRecords.IncentivePlan;
import com.tenxengage.app.batch.seed.SeedRecords.IncentiveRef;
import com.tenxengage.app.batch.seed.SeedRecords.JourneyCompletionExclusions;
import com.tenxengage.app.batch.seed.SeedRecords.NonSalesIncentiveRef;
import com.tenxengage.app.batch.seed.SeedRecords.PartnerLocationRef;
import com.tenxengage.app.batch.seed.SeedRecords.PartnerSets;
import com.tenxengage.app.batch.seed.SeedRecords.ProductRow;
import com.tenxengage.app.batch.seed.SeedRecords.SellerRef;
import com.tenxengage.app.batch.seed.SeedRecords.UserCreationResult;
import com.tenxengage.app.batch.seed.SeedStateTracker.SeedMode;
import com.tenxengage.app.service.recommendation.RecommendationScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrator for seed data generation. Determines seeding mode (FULL/INCREMENTAL/SKIP)
 * and delegates to focused seeder components.
 *
 * Gated by app.seed.enabled=true. On each startup:
 * - FULL: Wipes all data and regenerates from SEED_START_DATE through today.
 * - INCREMENTAL: Generates data only for missing days since last seed.
 * - SKIP: Data is current through today; nothing to do.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private final JdbcTemplate jdbc;
    private final SeedStateTracker stateTracker;
    private final SeedCleanupService cleanupService;
    private final ProductSeeder productSeeder;
    private final PartnerSeeder partnerSeeder;
    private final UserSeeder userSeeder;
    private final IncentiveSeeder incentiveSeeder;
    private final PurchaseOrderSeeder purchaseOrderSeeder;
    private final EligibilitySeeder eligibilitySeeder;
    private final ClaimSeeder claimSeeder;
    private final CourseCompletionSeeder courseCompletionSeeder;
    private final LmsCourseSeeder lmsCourseSeeder;
    private final ForecastTrainingCorrelationSeeder forecastCorrelationSeeder;
    private final CompletionSeeder completionSeeder;
    private final RecommendationScoringService recommendationScoringService;

    public SeedDataRunner(JdbcTemplate jdbc,
                          SeedStateTracker stateTracker,
                          SeedCleanupService cleanupService,
                          ProductSeeder productSeeder,
                          PartnerSeeder partnerSeeder,
                          UserSeeder userSeeder,
                          IncentiveSeeder incentiveSeeder,
                          PurchaseOrderSeeder purchaseOrderSeeder,
                          EligibilitySeeder eligibilitySeeder,
                          ClaimSeeder claimSeeder,
                          CourseCompletionSeeder courseCompletionSeeder,
                          LmsCourseSeeder lmsCourseSeeder,
                          ForecastTrainingCorrelationSeeder forecastCorrelationSeeder,
                          CompletionSeeder completionSeeder,
                          RecommendationScoringService recommendationScoringService) {
        this.jdbc = jdbc;
        this.stateTracker = stateTracker;
        this.cleanupService = cleanupService;
        this.productSeeder = productSeeder;
        this.partnerSeeder = partnerSeeder;
        this.userSeeder = userSeeder;
        this.incentiveSeeder = incentiveSeeder;
        this.purchaseOrderSeeder = purchaseOrderSeeder;
        this.eligibilitySeeder = eligibilitySeeder;
        this.claimSeeder = claimSeeder;
        this.courseCompletionSeeder = courseCompletionSeeder;
        this.lmsCourseSeeder = lmsCourseSeeder;
        this.forecastCorrelationSeeder = forecastCorrelationSeeder;
        this.completionSeeder = completionSeeder;
        this.recommendationScoringService = recommendationScoringService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<UUID> clientIds = jdbc.queryForList(
                "SELECT id FROM clients ORDER BY created_at LIMIT 1", UUID.class);
        if (clientIds.isEmpty()) {
            log.warn("No clients found — skipping data generation");
            return;
        }
        UUID clientId = clientIds.get(0);

        // Reference-data seeders are idempotent via ON CONFLICT and carry the global
        // LMS catalog + training-lift correlations that the recommendation engine
        // needs. Run them on every startup so a missing catalog can't silently hide
        // Training recommendations, even in SKIP mode.
        lmsCourseSeeder.seedLmsCoursesAndMappings();
        forecastCorrelationSeeder.seedForecastCorrelations(clientId);

        SeedMode mode = stateTracker.determineSeedMode(clientId);
        switch (mode) {
            case SKIP -> {
                log.info("Seed data already current — skipping");
                // Recommendation scoring depends on the LMS catalog above. If the
                // catalog was just populated for the first time, training scores
                // will be empty until we kick off scoring.
                refreshRecommendationScoresIfTrainingEmpty(clientId);
                return;
            }
            case FULL -> {
                log.info("Performing FULL seed from {} to {}", FiscalQuarterCalculator.getSeedStartDate(), LocalDate.now());
                cleanupService.cleanupAllData(clientId);
                fullSeed(clientId);
            }
            case INCREMENTAL -> {
                LocalDate fromDate = stateTracker.getIncrementalStartDate(clientId);
                log.info("Performing INCREMENTAL seed from {} to {}", fromDate, LocalDate.now());
                incrementalSeed(clientId, fromDate, LocalDate.now());
            }
        }
    }

    private void fullSeed(UUID clientId) {
        long start = System.currentTimeMillis();
        Random random = new Random(42);
        LocalDate seedEnd = LocalDate.now();

        // Resolve role IDs (BUG FIX: uses client_roles, not dropped roles table)
        UUID partnerAdminRoleId = userSeeder.resolvePartnerAdminRoleId(clientId);
        UUID partnerSellerRoleId = userSeeder.resolvePartnerSellerRoleId(clientId);
        UUID adminUserId = userSeeder.resolveAdminUserId(clientId);

        // Resolve location hierarchy IDs
        UUID regionLevelId = partnerSeeder.resolveRegionLevelId(clientId);
        UUID countryLevelId = partnerSeeder.resolveCountryLevelId(clientId);
        Map<String, UUID> regionValueIds = partnerSeeder.resolveLocationValues(clientId, 0);
        Map<String, UUID> countryValueIds = partnerSeeder.resolveLocationValues(clientId, 1);
        // Drives the country-level audience-rule writes in the incentive seeder
        // (paired with the region-level writes for the same set of regions).
        Map<String, List<String>> regionToCountries =
                partnerSeeder.resolveRegionToCountries(clientId);

        // Load Sales Data field IDs for eligibility rules
        Map<String, UUID> salesFieldIds = incentiveSeeder.loadSalesDataFieldIds(clientId);
        log.info("Loaded {} Sales Data field IDs", salesFieldIds.size());

        // 1. Products
        List<ProductRow> products = productSeeder.createProducts(clientId);
        Map<String, List<String>> skusByCategory = ProductSeeder.buildSkusByCategory(products);

        // 2. Partners with dual-level location assignments
        Map<UUID, String> partnerRegionMap = new HashMap<>();
        Map<UUID, PartnerLocationRef> partnerLocationMap = new HashMap<>();
        List<FiscalQuarter> allQuarters = FiscalQuarterCalculator.buildFiscalQuarters();

        PartnerSets partners = partnerSeeder.createPartners(
                clientId, allQuarters, random, partnerRegionMap, partnerLocationMap);

        // 3. Users for enrolled partners
        Set<UUID> enrolledPartnerSet = new HashSet<>(partners.enrolledIds());
        UserCreationResult userResult = userSeeder.createUsers(
                clientId, partners.enrolledIds(), partnerAdminRoleId, partnerSellerRoleId,
                partners.creationDates(), random);
        List<SellerRef> sellers = new ArrayList<>(userResult.sellers());
        Map<UUID, Timestamp> userCreationDates = userResult.userCreationDates();

        // Include Flyway-seeded partners
        loadExistingPartners(clientId, partners, enrolledPartnerSet, partnerRegionMap, sellers);

        List<UUID> allPartnerIds = new ArrayList<>(partners.allIds());
        log.info("Total partners: {} ({} enrolled), {} sellers",
                allPartnerIds.size(), enrolledPartnerSet.size(), sellers.size());

        // 4. Fiscal quarters (dynamic — through today)
        log.info("Generated {} fiscal quarters from {} to {}",
                allQuarters.size(), allQuarters.get(0).displayName(),
                allQuarters.get(allQuarters.size() - 1).displayName());

        // 5. Pre-plan SALES incentives in memory
        List<IncentivePlan> salesPlans = incentiveSeeder.prePlanIncentives(allQuarters, skusByCategory, random);
        Map<String, List<IncentivePlan>> incentivePlansByQR = buildIncentiveLookup(salesPlans);

        // 6. Create incentive DB records
        List<IncentiveRef> incentiveRefs = incentiveSeeder.createSalesIncentives(
                clientId, adminUserId, salesPlans, salesFieldIds, regionLevelId, regionValueIds,
                countryLevelId, countryValueIds, regionToCountries, random);
        List<NonSalesIncentiveRef> trainingRefs = incentiveSeeder.createTrainingIncentives(
                clientId, adminUserId, allQuarters, regionLevelId, regionValueIds,
                countryLevelId, countryValueIds, regionToCountries, random);
        List<NonSalesIncentiveRef> activityRefs = incentiveSeeder.createActivityIncentives(
                clientId, adminUserId, allQuarters, regionLevelId, regionValueIds,
                countryLevelId, countryValueIds, regionToCountries, random);
        List<NonSalesIncentiveRef> allNonSalesRefs = new ArrayList<>(trainingRefs);
        allNonSalesRefs.addAll(activityRefs);
        JourneyCompletionExclusions journeyExclusions = incentiveSeeder.createJourneyIncentives(
                clientId, adminUserId, allQuarters, allNonSalesRefs, regionLevelId, regionValueIds,
                countryLevelId, countryValueIds, regionToCountries, random);

        // Note: lmsCourseSeeder and forecastCorrelationSeeder run unconditionally
        // in run() above, before we pick a seed mode. That way CourseCompletionSeeder
        // (step 7) always sees a populated lms_courses catalog.

        // 7. Pre-compute course completions
        List<CourseCompletionRecord> courseSchedule = courseCompletionSeeder.preComputeCourseCompletions(
                clientId, sellers, userCreationDates, random);
        Map<UUID, Map<String, Double>> trainingBoosts = courseCompletionSeeder.computeTrainingBoosts(
                courseSchedule, sellers);

        // 8. Purchase orders with incentive-aware product selection
        Map<String, List<UUID>> posByQuarterRegion = purchaseOrderSeeder.createPurchaseOrdersAndLines(
                clientId, allPartnerIds, partnerRegionMap, products, allQuarters,
                incentivePlansByQR, enrolledPartnerSet, trainingBoosts, random);

        // 9. Persist course completions
        courseCompletionSeeder.persistCourseCompletions(clientId, courseSchedule);

        // 10. Eligibility computation
        UUID taggingJobId = eligibilitySeeder.computeEligibility(
                clientId, incentiveRefs, posByQuarterRegion, partnerRegionMap);

        // 11. Claims with seasonal patterns
        claimSeeder.createClaims(clientId, taggingJobId, sellers, partnerRegionMap,
                userCreationDates, partnerLocationMap, regionValueIds, random);

        // 12. User completions for non-SALES incentives. Skip-list preserves BUG-019
        // verification invariants for the two current-quarter AMERICAS Journeys.
        completionSeeder.seedUserCompletions(clientId, sellers, random, journeyExclusions);

        // 13. Pre-compute recommendation scores so /home's tenX Suggestions widget renders
        // populated for Partner Admin / Partner Seller on the first page load after a fresh
        // seed. Without this, recommendation_scores stays empty until the nightly cron
        // (RecommendationRefreshScheduler, 02:30) fires, and the widget shows the
        // "Recommendations Coming Soon" empty state in the meantime. Runs after all other
        // data is in place — scoring reads users, claims, course completions, incentives,
        // and budget utilizations, so it has to be last.
        log.info("Computing recommendation scores for seeded tenant...");
        recommendationScoringService.scoreTrainingForClient(clientId);
        recommendationScoringService.scoreIncentivesForClient(clientId);

        // Record seed state
        stateTracker.recordSeedCompletion(clientId, seedEnd, "FULL", allPartnerIds.size());

        long elapsed = System.currentTimeMillis() - start;
        log.info("FULL seed completed in {} ms", elapsed);
    }

    private void incrementalSeed(UUID clientId, LocalDate fromDate, LocalDate toDate) {
        long start = System.currentTimeMillis();
        Random random = new Random(42 + fromDate.toEpochDay());

        // Note: lmsCourseSeeder and forecastCorrelationSeeder run unconditionally
        // in run() above, before we pick a seed mode.

        // 1. Load existing state
        List<ProductRow> products = productSeeder.ensureProducts(clientId);
        Map<String, List<String>> skusByCategory = ProductSeeder.buildSkusByCategory(products);

        UUID partnerAdminRoleId = userSeeder.resolvePartnerAdminRoleId(clientId);
        UUID partnerSellerRoleId = userSeeder.resolvePartnerSellerRoleId(clientId);
        UUID adminUserId = userSeeder.resolveAdminUserId(clientId);
        UUID regionLevelId = partnerSeeder.resolveRegionLevelId(clientId);
        UUID countryLevelId = partnerSeeder.resolveCountryLevelId(clientId);
        Map<String, UUID> regionValueIds = partnerSeeder.resolveLocationValues(clientId, 0);
        Map<String, UUID> countryValueIds = partnerSeeder.resolveLocationValues(clientId, 1);
        // Drives the country-level audience-rule writes in the incentive seeder.
        Map<String, List<String>> regionToCountries =
                partnerSeeder.resolveRegionToCountries(clientId);
        Map<String, UUID> salesFieldIds = incentiveSeeder.loadSalesDataFieldIds(clientId);

        // Load existing partners and their regions
        Map<UUID, String> partnerRegionMap = new HashMap<>();
        Map<UUID, PartnerLocationRef> partnerLocationMap = new HashMap<>();
        List<UUID> allPartnerIds = loadExistingPartnerData(clientId, partnerRegionMap, partnerLocationMap);
        Set<UUID> enrolledPartnerSet = loadEnrolledPartnerIds(clientId);
        List<SellerRef> sellers = loadExistingSellers(clientId);
        Map<UUID, Timestamp> userCreationDates = loadUserCreationDates(clientId);

        // 2. Check if we crossed into a new quarter
        FiscalQuarter currentQuarter = FiscalQuarterCalculator.quarterContaining(toDate);
        FiscalQuarter lastSeededQuarter = FiscalQuarterCalculator.quarterContaining(fromDate.minusDays(1));
        boolean newQuarter = !currentQuarter.displayName().equals(lastSeededQuarter.displayName());

        if (newQuarter) {
            log.info("New quarter detected: {} → {}", lastSeededQuarter.displayName(), currentQuarter.displayName());

            // Create new incentives for the new quarter
            List<FiscalQuarter> newQuarters = List.of(currentQuarter);
            List<IncentivePlan> newSalesPlans = incentiveSeeder.prePlanIncentives(newQuarters, skusByCategory, random);
            incentiveSeeder.createSalesIncentives(clientId, adminUserId, newSalesPlans,
                    salesFieldIds, regionLevelId, regionValueIds,
                    countryLevelId, countryValueIds, regionToCountries, random);
            List<NonSalesIncentiveRef> trainingRefs = incentiveSeeder.createTrainingIncentives(
                    clientId, adminUserId, newQuarters, regionLevelId, regionValueIds,
                    countryLevelId, countryValueIds, regionToCountries, random);
            List<NonSalesIncentiveRef> activityRefs = incentiveSeeder.createActivityIncentives(
                    clientId, adminUserId, newQuarters, regionLevelId, regionValueIds,
                    countryLevelId, countryValueIds, regionToCountries, random);
            List<NonSalesIncentiveRef> allNonSales = new ArrayList<>(trainingRefs);
            allNonSales.addAll(activityRefs);
            // INCREMENTAL mode doesn't run CompletionSeeder, so the exclusions return
            // value is intentionally discarded here.
            incentiveSeeder.createJourneyIncentives(clientId, adminUserId, newQuarters,
                    allNonSales, regionLevelId, regionValueIds,
                    countryLevelId, countryValueIds, regionToCountries, random);
            log.info("Created incentives for new quarter {}", currentQuarter.displayName());

            // Add new partners + users for the quarter
            int newEnrolled = SeedConstants.NEW_ENROLLED_PER_QUARTER;
            int newNonEnrolled = SeedConstants.NEW_NON_ENROLLED_PER_QUARTER;
            log.info("Adding {} enrolled + {} non-enrolled partners for new quarter", newEnrolled, newNonEnrolled);
            addIncrementalPartners(clientId, newEnrolled, newNonEnrolled, random,
                    partnerAdminRoleId, partnerSellerRoleId, regionValueIds, countryValueIds,
                    allPartnerIds, enrolledPartnerSet, partnerRegionMap, partnerLocationMap,
                    sellers, userCreationDates);
        }

        // 3. Generate POs only for the missing date range (lightweight incremental)
        Map<String, List<UUID>> posByQuarterRegion = purchaseOrderSeeder.createIncrementalPOs(
                clientId, allPartnerIds, partnerRegionMap, products,
                fromDate, toDate, enrolledPartnerSet, random);

        // 4. Compute eligibility for new POs
        List<IncentiveRef> incentiveRefs = loadExistingIncentiveRefs(clientId, salesFieldIds);
        if (!incentiveRefs.isEmpty() && !posByQuarterRegion.isEmpty()) {
            UUID taggingJobId = eligibilitySeeder.computeEligibility(
                    clientId, incentiveRefs, posByQuarterRegion, partnerRegionMap);

            // 5. Create claims for new eligible POs
            claimSeeder.createClaims(clientId, taggingJobId, sellers, partnerRegionMap,
                    userCreationDates, partnerLocationMap, regionValueIds, random);
        }

        // 6. Refresh recommendation scores so tenX Suggestions picks up the newly-seeded
        // incremental data (new POs, new claims, maybe a new quarter's incentives). The
        // upsert in upsertScores is ON CONFLICT DO UPDATE, so re-running over existing
        // scores is idempotent.
        log.info("Refreshing recommendation scores after incremental seed...");
        recommendationScoringService.scoreTrainingForClient(clientId);
        recommendationScoringService.scoreIncentivesForClient(clientId);

        // 7. Record completion
        stateTracker.recordSeedCompletion(clientId, toDate, "INCREMENTAL", allPartnerIds.size());

        long elapsed = System.currentTimeMillis() - start;
        log.info("INCREMENTAL seed completed in {} ms ({} to {})", elapsed, fromDate, toDate);
    }

    /**
     * Re-run recommendation scoring if the TRAINING side is empty. Guards the
     * SKIP path: if lmsCourseSeeder just populated the catalog for the first
     * time, there are users and claims but no TRAINING scores yet. Without
     * this, the widget's Recommended Training section stays hidden until the
     * next nightly cron (RecommendationRefreshScheduler, 02:30).
     */
    private void refreshRecommendationScoresIfTrainingEmpty(UUID clientId) {
        Integer trainingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recommendation_scores " +
                "WHERE client_id = ? AND recommendation_type = 'TRAINING'",
                Integer.class, clientId);
        if (trainingCount != null && trainingCount > 0) return;

        Integer userCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE client_id = ? AND status = 'ACTIVE'",
                Integer.class, clientId);
        if (userCount == null || userCount == 0) return;

        log.info("Training scores empty but {} users exist — triggering recommendation scoring", userCount);
        recommendationScoringService.scoreTrainingForClient(clientId);
        recommendationScoringService.scoreIncentivesForClient(clientId);
    }

    /** Add a small batch of new partners for incremental growth. */
    private void addIncrementalPartners(UUID clientId, int enrolled, int nonEnrolled, Random random,
                                        UUID adminRoleId, UUID sellerRoleId,
                                        Map<String, UUID> regionValueIds, Map<String, UUID> countryValueIds,
                                        List<UUID> allPartnerIds, Set<UUID> enrolledPartnerSet,
                                        Map<UUID, String> partnerRegionMap,
                                        Map<UUID, PartnerLocationRef> partnerLocationMap,
                                        List<SellerRef> sellers, Map<UUID, Timestamp> userCreationDates) {
        int startIdx = allPartnerIds.size();
        Timestamp now = Timestamp.from(java.time.Instant.now());
        List<Object[]> partnerBatch = new ArrayList<>();
        List<Object[]> locationBatch = new ArrayList<>();
        List<UUID> newEnrolledIds = new ArrayList<>();

        for (int i = 0; i < enrolled + nonEnrolled; i++) {
            UUID id = UUID.randomUUID();
            int idx = startIdx + i;
            String regionName = SeedConstants.REGIONS[idx % SeedConstants.REGIONS.length];
            boolean isEnrolled = i < enrolled;

            // Build partner row (same logic as PartnerSeeder.buildPartnerRow but inline)
            String name = SeedConstants.PARTNER_PREFIXES[idx % SeedConstants.PARTNER_PREFIXES.length] + " "
                    + SeedConstants.PARTNER_SUFFIXES[idx / SeedConstants.PARTNER_PREFIXES.length % SeedConstants.PARTNER_SUFFIXES.length];
            if (idx >= SeedConstants.PARTNER_PREFIXES.length * SeedConstants.PARTNER_SUFFIXES.length) {
                name = name + " " + (idx + 1);
            }
            String slug = name.toLowerCase().replaceAll("[^a-z0-9]", "");
            String email = "info@" + slug + ".example.com";
            String partnerType = SeedConstants.PARTNER_TYPES[idx % SeedConstants.PARTNER_TYPES.length];
            String extId = String.format("EXT-%s-%03d", partnerType.substring(0, 3).toUpperCase(), idx + 1);
            String website = "https://www." + slug + SeedConstants.WEBSITES_TLD[idx % SeedConstants.WEBSITES_TLD.length];
            String phone = "+1-" + SeedConstants.AREA_CODES[idx % SeedConstants.AREA_CODES.length] + "-"
                    + String.format("%03d", 100 + (idx * 7) % 900) + "-"
                    + String.format("%04d", 1000 + (idx * 13) % 9000);
            String metadata = String.format("{\"Partner Type\":\"%s\",\"Contact Email\":\"%s\"}", partnerType, email);

            partnerBatch.add(new Object[]{id, name, clientId, "ACTIVE", phone, website, extId, metadata, now, now});
            allPartnerIds.add(id);
            partnerRegionMap.put(id, regionName);

            // Location assignments
            UUID regionValueId = regionValueIds.get(regionName);
            if (regionValueId != null) {
                locationBatch.add(new Object[]{UUID.randomUUID(), clientId, id, regionValueId, now, now});
            }
            // Pick a country in the region
            String[] countries = SeedConstants.COUNTRIES_BY_REGION.get(regionName);
            if (countries != null && countries.length > 0) {
                String country = countries[random.nextInt(countries.length)];
                Map<String, UUID> cids = partnerSeeder.resolveLocationValues(clientId, 1);
                UUID countryValueId = cids.get(country);
                if (countryValueId != null) {
                    locationBatch.add(new Object[]{UUID.randomUUID(), clientId, id, countryValueId, now, now});
                    partnerLocationMap.put(id, new PartnerLocationRef(regionValueId, regionName, countryValueId, country));
                }
            }

            if (isEnrolled) {
                enrolledPartnerSet.add(id);
                newEnrolledIds.add(id);
            }
        }

        jdbc.batchUpdate("INSERT INTO partner_companies " +
                "(id, name, client_id, status, contact_phone, website, external_partner_id, metadata, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)", partnerBatch);
        jdbc.batchUpdate("INSERT INTO partner_company_locations " +
                "(id, client_id, partner_company_id, location_value_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)", locationBatch);

        // Create users for new enrolled partners
        if (!newEnrolledIds.isEmpty()) {
            Map<UUID, Timestamp> partnerDates = new HashMap<>();
            for (UUID pid : newEnrolledIds) partnerDates.put(pid, now);
            UserCreationResult userResult = userSeeder.createUsers(
                    clientId, newEnrolledIds, adminRoleId, sellerRoleId, partnerDates, random);
            sellers.addAll(userResult.sellers());
            userCreationDates.putAll(userResult.userCreationDates());
        }

        log.info("Added {} new partners ({} enrolled with users)", enrolled + nonEnrolled, enrolled);
    }

    /** Load all partner IDs with their regions from the database. */
    private List<UUID> loadExistingPartnerData(UUID clientId, Map<UUID, String> partnerRegionMap,
                                               Map<UUID, PartnerLocationRef> partnerLocationMap) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DISTINCT pc.id, lv.name AS region, lv.id AS region_value_id " +
                "FROM partner_companies pc " +
                "LEFT JOIN partner_company_locations pcl ON pcl.partner_company_id = pc.id " +
                "LEFT JOIN location_values lv ON lv.id = pcl.location_value_id " +
                "LEFT JOIN location_levels ll ON ll.id = lv.level_id " +
                "WHERE pc.client_id = ? AND (ll.depth = 0 OR ll.depth IS NULL)", clientId);
        List<UUID> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            String region = (String) row.get("region");
            UUID regionValueId = (UUID) row.get("region_value_id");
            if (!ids.contains(id)) ids.add(id);
            if (region != null) {
                partnerRegionMap.put(id, region);
                partnerLocationMap.put(id, new PartnerLocationRef(regionValueId, region, null, null));
            }
        }
        return ids;
    }

    private Set<UUID> loadEnrolledPartnerIds(UUID clientId) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT DISTINCT partner_company_id FROM users WHERE client_id = ? AND partner_company_id IS NOT NULL",
                UUID.class, clientId);
        return new HashSet<>(ids);
    }

    private List<SellerRef> loadExistingSellers(UUID clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, partner_company_id FROM users " +
                "WHERE client_id = ? AND partner_company_id IS NOT NULL AND status = 'ACTIVE'", clientId);
        List<SellerRef> sellers = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            sellers.add(new SellerRef((UUID) row.get("id"), (UUID) row.get("partner_company_id")));
        }
        return sellers;
    }

    private Map<UUID, Timestamp> loadUserCreationDates(UUID clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, created_at FROM users WHERE client_id = ?", clientId);
        Map<UUID, Timestamp> dates = new HashMap<>();
        for (Map<String, Object> row : rows) {
            dates.put((UUID) row.get("id"), (Timestamp) row.get("created_at"));
        }
        return dates;
    }

    /** Load existing SALES incentive refs for eligibility computation. */
    /**
     * Loads full IncentiveRef data from the database for eligibility computation.
     * Includes eligible SKUs, booking amount thresholds, customer segments, and
     * payout configs with band structures — all required by EligibilitySeeder.
     */
    private List<IncentiveRef> loadExistingIncentiveRefs(UUID clientId, Map<String, UUID> salesFieldIds) {
        // 1. Load incentive + requirement base data
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT i.id, sr.id AS req_id, i.start_date " +
                "FROM incentives i JOIN sales_requirements sr ON sr.incentive_id = i.id " +
                "WHERE i.client_id = ? AND i.incentive_type = 'SALES'", clientId);

        // 2. Load all eligibility rules indexed by requirement ID
        Map<UUID, List<Map<String, Object>>> rulesByReqId = new HashMap<>();
        List<Map<String, Object>> allRules = jdbc.queryForList(
                "SELECT erg.requirement_id, er.rule_type, er.operator, er.value, er.selected_products " +
                "FROM eligibility_rules er " +
                "JOIN eligibility_rule_groups erg ON erg.id = er.rule_group_id " +
                "JOIN sales_requirements sr ON sr.id = erg.requirement_id " +
                "JOIN incentives i ON i.id = sr.incentive_id " +
                "WHERE i.client_id = ? AND i.incentive_type = 'SALES'", clientId);
        for (Map<String, Object> rule : allRules) {
            UUID reqId = (UUID) rule.get("requirement_id");
            rulesByReqId.computeIfAbsent(reqId, k -> new ArrayList<>()).add(rule);
        }

        // 3. Load payout configs + bands indexed by requirement ID
        Map<UUID, List<SeedRecords.PayoutConfigRef>> payoutsByReqId = new HashMap<>();
        List<Map<String, Object>> allConfigs = jdbc.queryForList(
                "SELECT pc.id AS config_id, pc.requirement_id, pc.currency_id, " +
                "pc.payout_type, pc.against, pc.max_per_deal " +
                "FROM payout_configs pc " +
                "JOIN sales_requirements sr ON sr.id = pc.requirement_id " +
                "JOIN incentives i ON i.id = sr.incentive_id " +
                "WHERE i.client_id = ? AND i.incentive_type = 'SALES'", clientId);
        Map<UUID, List<SeedRecords.BandPlan>> bandsByConfigId = new HashMap<>();
        List<Map<String, Object>> allBands = jdbc.queryForList(
                "SELECT pb.payout_config_id, pb.min_amount, pb.max_amount, pb.payout_value " +
                "FROM payout_bands pb " +
                "JOIN payout_configs pc ON pc.id = pb.payout_config_id " +
                "JOIN sales_requirements sr ON sr.id = pc.requirement_id " +
                "JOIN incentives i ON i.id = sr.incentive_id " +
                "WHERE i.client_id = ? AND i.incentive_type = 'SALES' " +
                "ORDER BY pb.payout_config_id, pb.sort_order", clientId);
        for (Map<String, Object> band : allBands) {
            UUID configId = (UUID) band.get("payout_config_id");
            bandsByConfigId.computeIfAbsent(configId, k -> new ArrayList<>()).add(
                    new SeedRecords.BandPlan(
                            (BigDecimal) band.get("min_amount"),
                            (BigDecimal) band.get("max_amount"),
                            (BigDecimal) band.get("payout_value")));
        }
        for (Map<String, Object> config : allConfigs) {
            UUID configId = (UUID) config.get("config_id");
            UUID reqId = (UUID) config.get("requirement_id");
            payoutsByReqId.computeIfAbsent(reqId, k -> new ArrayList<>()).add(
                    new SeedRecords.PayoutConfigRef(
                            configId,
                            (String) config.get("currency_id"),
                            (String) config.get("payout_type"),
                            (String) config.get("against"),
                            (BigDecimal) config.get("max_per_deal"),
                            bandsByConfigId.getOrDefault(configId, List.of())));
        }

        // 4. Load audience location rules for target region
        Map<UUID, String> targetRegionByIncentive = new HashMap<>();
        List<Map<String, Object>> locationRules = jdbc.queryForList(
                "SELECT iar.incentive_id, lv.name AS region " +
                "FROM incentive_audience_rules iar " +
                "JOIN location_values lv ON lv.id::text = iar.rule_value " +
                "JOIN location_levels ll ON ll.id = lv.level_id AND ll.depth = 0 " +
                "WHERE iar.rule_type = 'LOCATION' " +
                "AND iar.incentive_id IN (SELECT id FROM incentives WHERE client_id = ? AND incentive_type = 'SALES') " +
                "LIMIT 1000", clientId);
        for (Map<String, Object> lr : locationRules) {
            UUID incentiveId = (UUID) lr.get("incentive_id");
            // Take first region (simplification — full seed pre-plans per region)
            targetRegionByIncentive.putIfAbsent(incentiveId, (String) lr.get("region"));
        }

        // 5. Build full IncentiveRef objects
        List<IncentiveRef> refs = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID incentiveId = (UUID) row.get("id");
            UUID reqId = (UUID) row.get("req_id");

            // Derive quarter from start_date (use UTC to avoid timezone shift)
            Object startDateObj = row.get("start_date");
            LocalDate startDate;
            if (startDateObj instanceof java.sql.Timestamp ts) {
                startDate = ts.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            } else if (startDateObj instanceof java.sql.Date d) {
                startDate = d.toLocalDate();
            } else {
                startDate = LocalDate.parse(startDateObj.toString().substring(0, 10));
            }
            FiscalQuarter fq = FiscalQuarterCalculator.quarterContaining(startDate);

            // Extract eligibility criteria from rules
            List<String> eligibleSkus = new ArrayList<>();
            BigDecimal minBookingAmount = BigDecimal.ZERO;
            List<String> eligibleSegments = null;

            for (Map<String, Object> rule : rulesByReqId.getOrDefault(reqId, List.of())) {
                String ruleType = (String) rule.get("rule_type");
                switch (ruleType) {
                    case "PRODUCTS" -> {
                        String skus = (String) rule.get("selected_products");
                        if (skus != null && !skus.isEmpty()) {
                            eligibleSkus.addAll(List.of(skus.split(",")));
                        }
                    }
                    case "BOOKING_AMOUNT" -> {
                        String val = (String) rule.get("value");
                        if (val != null) {
                            minBookingAmount = new BigDecimal(val);
                        }
                    }
                    case "CUSTOMER_TYPE" -> {
                        String val = (String) rule.get("value");
                        if (val != null) {
                            eligibleSegments = List.of(val.split(","));
                        }
                    }
                }
            }

            String targetRegion = targetRegionByIncentive.get(incentiveId);
            List<SeedRecords.PayoutConfigRef> payoutConfigs = payoutsByReqId.getOrDefault(reqId, List.of());

            refs.add(new IncentiveRef(incentiveId, reqId, fq, targetRegion, eligibleSkus,
                    minBookingAmount, eligibleSegments, payoutConfigs));
        }
        log.info("Loaded {} full IncentiveRefs with rules and payouts for incremental eligibility", refs.size());
        return refs;
    }

    /** Load Flyway-seeded partners that aren't from SeedDataRunner. */
    private void loadExistingPartners(UUID clientId, PartnerSets partners,
                                      Set<UUID> enrolledPartnerSet,
                                      Map<UUID, String> partnerRegionMap,
                                      List<SellerRef> sellers) {
        List<Map<String, Object>> existingPartners = jdbc.queryForList(
                "SELECT pc.id, lv.name AS region FROM partner_companies pc " +
                "LEFT JOIN partner_company_locations pcl ON pcl.partner_company_id = pc.id " +
                "LEFT JOIN location_values lv ON lv.id = pcl.location_value_id " +
                "LEFT JOIN location_levels ll ON ll.id = lv.level_id AND ll.depth = 0 " +
                "WHERE pc.client_id = ? AND pc.metadata->>'Contact Email' NOT LIKE '%.example.com'",
                clientId);

        for (Map<String, Object> ep : existingPartners) {
            UUID existingPartnerId = (UUID) ep.get("id");
            if (!partners.allIds().contains(existingPartnerId)) {
                partners.allIds().add(existingPartnerId);
                enrolledPartnerSet.add(existingPartnerId);
                String region = (String) ep.get("region");
                partnerRegionMap.put(existingPartnerId, region != null ? region : "AMERICAS");
                List<UUID> existingUsers = jdbc.queryForList(
                        "SELECT id FROM users WHERE partner_company_id = ? AND client_id = ?",
                        UUID.class, existingPartnerId, clientId);
                for (UUID uid : existingUsers) {
                    sellers.add(new SellerRef(uid, existingPartnerId));
                }
            }
        }
    }

    /** Build lookup: "FY2025 Q2|AMERICAS" → IncentivePlan list */
    private Map<String, List<IncentivePlan>> buildIncentiveLookup(List<IncentivePlan> salesPlans) {
        Map<String, List<IncentivePlan>> map = new HashMap<>();
        for (IncentivePlan plan : salesPlans) {
            List<String> effectiveRegions = plan.audienceRegions().isEmpty()
                    ? List.of("AMERICAS", "LATAM", "EMEAR", "APJ")
                    : plan.audienceRegions();
            for (String r : effectiveRegions) {
                String key = plan.quarter().displayName() + "|" + r;
                map.computeIfAbsent(key, k -> new ArrayList<>()).add(plan);
            }
        }
        return map;
    }
}
