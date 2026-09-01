package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.BandPlan;
import com.tenxengage.app.batch.seed.SeedRecords.FiscalQuarter;
import com.tenxengage.app.batch.seed.SeedRecords.IncentivePlan;
import com.tenxengage.app.batch.seed.SeedRecords.IncentiveRef;
import com.tenxengage.app.batch.seed.SeedRecords.JourneyCompletionExclusions;
import com.tenxengage.app.batch.seed.SeedRecords.NonSalesIncentiveRef;
import com.tenxengage.app.batch.seed.SeedRecords.PayoutConfigRef;
import com.tenxengage.app.batch.seed.SeedRecords.PayoutPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.tenxengage.app.batch.seed.SeedConstants.ACTIVITY_CATEGORY_NAMES;
import static com.tenxengage.app.batch.seed.SeedConstants.ACTIVITY_TEMPLATES;
import static com.tenxengage.app.batch.seed.SeedConstants.BAND_PATTERNS_2;
import static com.tenxengage.app.batch.seed.SeedConstants.BAND_PATTERNS_3;
import static com.tenxengage.app.batch.seed.SeedConstants.BAND_PATTERNS_4;
import static com.tenxengage.app.batch.seed.SeedConstants.CUSTOMER_SEGMENTS;
import static com.tenxengage.app.batch.seed.SeedConstants.DOC_REQUIREMENTS;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_BOOKING_THRESHOLDS;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_CASH_FLAT;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_CREDITS;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_ENABLEMENT_CASH;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_ENABLEMENT_POINTS;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_MAX_PER_DEAL;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_PERCENTAGES;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_POINTS_FLAT;
import static com.tenxengage.app.batch.seed.SeedConstants.NICE_TICKETS;
import static com.tenxengage.app.batch.seed.SeedConstants.PRODUCT_FOCUS;
import static com.tenxengage.app.batch.seed.SeedConstants.PROGRAM_TYPES;
import static com.tenxengage.app.batch.seed.SeedConstants.REGIONS;
import static com.tenxengage.app.batch.seed.SeedConstants.ROLE_COMPANY_ADMIN;
import static com.tenxengage.app.batch.seed.SeedConstants.ROLE_PARTNER_SELLER;
import static com.tenxengage.app.batch.seed.SeedConstants.TRAINING_COURSES;

/**
 * Creates SALES, TRAINING, ACTIVITY, and JOURNEY incentives with full data:
 * multi-tier payouts, per-location budgets, audience rules, eligibility rules,
 * training course assignments, activity definitions with document requirements,
 * and journey stages.
 *
 * <p>Location model conventions:
 * <ul>
 *   <li>Audience rules use rule_type=LOCATION with location_level_id (not REGION + name string)</li>
 *   <li>Budget mode uses PER_LOCATION with budget_location_level_id and location_budget_allocations rows</li>
 *   <li>Budget utilizations reference location_value_id (UUID), not a region VARCHAR</li>
 * </ul>
 */
@Component
public class IncentiveSeeder {

    private static final Logger log = LoggerFactory.getLogger(IncentiveSeeder.class);

    private final JdbcTemplate jdbc;

    public IncentiveSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Sales Data Field Lookup ────────────────────────────────────────────────

    /**
     * Loads Sales Data field IDs (e.g. "Product SKU", "Net Bookings", "Customer Segment")
     * from builder_field_configs for use in eligibility rules.
     *
     * @param clientId the client UUID
     * @return map of field name to field UUID
     */
    public Map<String, UUID> loadSalesDataFieldIds(UUID clientId) {
        String sql = "SELECT dof.name, dof.id FROM data_object_fields dof " +
                "JOIN data_objects dobj ON dobj.id = dof.data_object_id " +
                "WHERE dobj.client_id = ? AND dobj.name = 'Sales Data' " +
                "AND dof.name IN ('Product SKU', 'Net Bookings', 'Customer Segment')";
        Map<String, UUID> fieldIds = new HashMap<>();
        jdbc.query(sql, rs -> {
            fieldIds.put(rs.getString("name"), UUID.fromString(rs.getString("id")));
        }, clientId);
        return fieldIds;
    }

    // ── Pre-Plan Incentives (in-memory, before PO generation) ───────────────────

    /**
     * Pre-plans sales incentive data in memory (no DB writes) so that PO generation
     * can correlate product selection with incentive focus categories.
     *
     * @param quarters       all fiscal quarters to seed
     * @param skusByCategory product SKUs grouped by category
     * @param random         seeded Random for deterministic output
     * @return list of planned incentives (not yet persisted)
     */
    public List<IncentivePlan> prePlanIncentives(List<FiscalQuarter> quarters,
                                                  Map<String, List<String>> skusByCategory,
                                                  Random random) {
        List<IncentivePlan> plans = new ArrayList<>();
        List<String> allCategories = new ArrayList<>(skusByCategory.keySet());
        allCategories.sort(String::compareTo);
        FiscalQuarter currentQuarter = quarters.get(quarters.size() - 1);

        for (FiscalQuarter fq : quarters) {
            boolean isCurrent = fq.fyYear() == currentQuarter.fyYear()
                    && fq.qLabel().equals(currentQuarter.qLabel());
            boolean isPast = !isCurrent;

            for (String region : REGIONS) {
                int numSales = 3 + random.nextInt(3); // 3-5

                for (int s = 0; s < numSales; s++) {
                    String name = generateIncentiveName(region, fq, s, random);

                    // Region scoping
                    double scopeRoll = random.nextDouble();
                    List<String> audienceRegions;
                    if (name.contains(region) || scopeRoll < 0.60) {
                        audienceRegions = List.of(region);
                    } else if (scopeRoll < 0.85) {
                        String otherRegion;
                        do {
                            otherRegion = REGIONS[random.nextInt(REGIONS.length)];
                        } while (otherRegion.equals(region));
                        audienceRegions = List.of(region, otherRegion);
                    } else {
                        audienceRegions = List.of();
                    }

                    // Status: current-quarter sales are always ACTIVE so every 3-5
                    // seeded per region surface to partner admins (the partner view
                    // filters to ACTIVE). Past quarters are INACTIVE for history.
                    String status = isPast ? "INACTIVE" : "ACTIVE";

                    // Pick 3 SKUs for eligibility
                    String focusCategory = allCategories.get(
                            random.nextInt(allCategories.size()));
                    List<String> catSkus = skusByCategory.getOrDefault(
                            focusCategory, List.of());
                    List<String> selectedSkus = new ArrayList<>();
                    List<String> shuffled = new ArrayList<>(catSkus);
                    Collections.shuffle(shuffled, random);
                    for (int i = 0; i < Math.min(3, shuffled.size()); i++) {
                        selectedSkus.add(shuffled.get(i));
                    }

                    // Booking amount threshold
                    BigDecimal minBooking = BigDecimal.valueOf(pickOne(NICE_BOOKING_THRESHOLDS, random));

                    // Customer type rule on ~30%
                    List<String> eligibleSegments = null;
                    if (random.nextDouble() < 0.30) {
                        List<String> segList = new ArrayList<>(List.of(CUSTOMER_SEGMENTS));
                        Collections.shuffle(segList, random);
                        eligibleSegments = List.of(segList.get(0), segList.get(1));
                    }

                    // Success score for PO-incentive correlation
                    double successScore;
                    double scoreRoll = random.nextDouble();
                    if (scoreRoll < 0.60) {
                        successScore = 1.8 + random.nextDouble() * 0.7; // 1.8-2.5
                    } else if (scoreRoll < 0.85) {
                        successScore = 1.2 + random.nextDouble() * 0.3; // 1.2-1.5
                    } else {
                        successScore = 0.95 + random.nextDouble() * 0.10; // 0.95-1.05
                    }

                    // Audience roles
                    List<String> audienceRoles;
                    double roleRoll = random.nextDouble();
                    if (roleRoll < 0.60) {
                        audienceRoles = List.of(ROLE_COMPANY_ADMIN, ROLE_PARTNER_SELLER);
                    } else if (roleRoll < 0.90) {
                        audienceRoles = List.of(ROLE_PARTNER_SELLER);
                    } else {
                        audienceRoles = List.of(ROLE_COMPANY_ADMIN);
                    }

                    // Multi-tier payout plan
                    double payoutTypeRoll = random.nextDouble();
                    PayoutPlan cashPayout;
                    PayoutPlan pointsPayout;
                    if (payoutTypeRoll < 0.60) {
                        cashPayout = generatePayoutPlan("PERCENTAGE", "cash", random);
                        pointsPayout = generatePayoutPlan("FLAT", "points", random);
                    } else if (payoutTypeRoll < 0.80) {
                        cashPayout = generatePayoutPlan("FLAT", "cash", random);
                        pointsPayout = generatePayoutPlan("PERCENTAGE", "points", random);
                    } else {
                        cashPayout = generatePayoutPlan("PERCENTAGE", "cash", random);
                        pointsPayout = generatePayoutPlan("PERCENTAGE", "points", random);
                    }

                    // Total budget: $50K-$250K across all regions and currencies
                    int totalBudget = roundedRandom(50000, 250000, 10000, random);

                    // Split: ~60% cash, ~40% points
                    int cashBudgetVal = (int) Math.round(totalBudget * 0.60 / 5000.0) * 5000;
                    int pointsBudgetVal = totalBudget - cashBudgetVal;
                    pointsBudgetVal = (int) Math.round(pointsBudgetVal / 5000.0) * 5000;

                    BigDecimal cashBudget = BigDecimal.valueOf(cashBudgetVal);
                    BigDecimal pointsBudget = BigDecimal.valueOf(pointsBudgetVal);

                    // Per-region breakdown
                    List<String> effectiveRegions = audienceRegions.isEmpty()
                            ? List.of("AMERICAS", "LATAM", "EMEAR", "APJ") : audienceRegions;
                    Map<String, BigDecimal> regionCashBudgets = splitBudgetByRegion(
                            cashBudgetVal, effectiveRegions);
                    Map<String, BigDecimal> regionPointsBudgets = splitBudgetByRegion(
                            pointsBudgetVal, effectiveRegions);

                    // Description
                    String description = String.format(
                            "Earn rewards on qualifying %s deals in %s. " +
                                    "Minimum booking of $%,.0f required.",
                            focusCategory, fq.displayName(), minBooking.doubleValue());

                    plans.add(new IncentivePlan(
                            UUID.randomUUID(), fq, region, focusCategory, selectedSkus,
                            minBooking, eligibleSegments, name, description, status,
                            successScore, audienceRegions, audienceRoles,
                            cashPayout, pointsPayout, cashBudget, pointsBudget,
                            regionCashBudgets, regionPointsBudgets));
                }
            }
        }
        return plans;
    }

    // ── Sales Incentive DB Record Creation (from plans) ─────────────────────────

    /**
     * Persists pre-planned sales incentives to the database with full structure:
     * incentive record, audience rules (LOCATION + ROLE), sales requirements,
     * eligibility rules, multi-tier payout configs, and per-location budgets.
     *
     * @param clientId        the client UUID
     * @param adminUserId     admin user who "created" the incentives
     * @param salesPlans      pre-planned incentive data from {@link #prePlanIncentives}
     * @param salesFieldIds   map of field name to field UUID for eligibility rules
     * @param regionLevelId   UUID of the region-level location_level (depth=0)
     * @param regionValueIds  map of region name (e.g. "AMERICAS") to location_value UUID
     * @param random          seeded Random for deterministic output
     * @return list of incentive refs for eligibility computation
     */
    public List<IncentiveRef> createSalesIncentives(UUID clientId, UUID adminUserId,
                                                     List<IncentivePlan> salesPlans,
                                                     Map<String, UUID> salesFieldIds,
                                                     UUID regionLevelId,
                                                     Map<String, UUID> regionValueIds,
                                                     UUID countryLevelId,
                                                     Map<String, UUID> countryValueIds,
                                                     Map<String, List<String>> regionToCountries,
                                                     Random random) {
        List<IncentiveRef> allRefs = new ArrayList<>();

        for (IncentivePlan plan : salesPlans) {
            FiscalQuarter fq = plan.quarter();
            Timestamp qStart = Timestamp.from(
                    fq.startDate().atStartOfDay(ZoneOffset.UTC).toInstant());
            Timestamp qEnd = Timestamp.from(
                    fq.endDate().atTime(23, 59, 59).toInstant(ZoneOffset.UTC));
            // Incentive created 7-30 days before start (admin setup time)
            Timestamp incCreatedAt = Timestamp.from(
                    fq.startDate().minusDays(7 + random.nextInt(24))
                            .atStartOfDay(ZoneOffset.UTC).toInstant());
            // Use incCreatedAt for all sub-records in this incentive
            Timestamp now = incCreatedAt;

            String rewardCurrencies = "[\"cash\",\"points\"]";
            LinkedHashMap<String, Long> rewardMaxes = new LinkedHashMap<>();
            rewardMaxes.put("cash", maxSalesPayoutAmount(plan.cashPayout()));
            rewardMaxes.put("points", maxSalesPayoutAmount(plan.pointsPayout()));
            String rewardMessage = formatRewardMessage(rewardMaxes);
            // Derive representative reward amounts from mid-band payout values
            int midBandIdx = plan.cashPayout().bands().size() / 2;
            int reprCash = plan.cashPayout().bands().get(midBandIdx).payoutValue().intValue();
            int reprPoints = plan.pointsPayout().bands().get(
                    Math.min(midBandIdx, plan.pointsPayout().bands().size() - 1))
                    .payoutValue().intValue();
            String rewardAmounts = String.format(
                    "{\"cash\":\"%d\",\"points\":\"%d\"}", reprCash, reprPoints);

            UUID incId = plan.plannedId();
            jdbc.update("INSERT INTO incentives (id, name, description, incentive_type, " +
                            "status, client_id, created_by, start_date, end_date, deleted, " +
                            "requires_approval, required_approvals, journey_sequential, " +
                            "approval_round, reward_currencies, reward_message, reward_amounts, " +
                            "fiscal_years, fiscal_quarters, max_claimers_per_deal, " +
                            "created_at, updated_at) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,false,false,0,true,1,?,?,?,?,?,?,?,?)",
                    incId, plan.name(), plan.description(), "SALES", plan.status(),
                    clientId, adminUserId, qStart, qEnd,
                    rewardCurrencies, rewardMessage, rewardAmounts,
                    "[\"FY" + fq.fyYear() + "\"]",
                    "[\"" + fq.qLabel() + "\"]",
                    1 + random.nextInt(3),
                    incCreatedAt, incCreatedAt);

            // Audience rules: emit LOCATION rows with location_level_id (canonical shape).
            insertLocationAudienceRules(incId, plan.audienceRegions(), regionLevelId,
                    regionValueIds, now);

            // Also write country-level LOCATION rules so the (required) Country
            // MultiSelect renders pills when a Client Admin clones or edits
            // this seeded incentive. Picker randomizes the country count per
            // incentive so seed data exercises both broad and narrow shapes.
            List<String> pickedCountries = pickCountriesForRegions(
                    plan.audienceRegions(), regionToCountries, random);
            insertCountryAudienceRules(incId, pickedCountries, countryLevelId,
                    countryValueIds, now);

            // ROLE audience rules
            insertRoleAudienceRules(incId, clientId, plan.audienceRoles(), now);

            // Sales requirement with eligibility rules
            UUID reqId = UUID.randomUUID();
            jdbc.update("INSERT INTO sales_requirements " +
                            "(id, incentive_id, name, sort_order, created_at, updated_at) " +
                            "VALUES (?,?,?,?,?,?)",
                    reqId, incId, plan.focusCategory() + " deals", 0, now, now);

            UUID groupId = UUID.randomUUID();
            jdbc.update("INSERT INTO eligibility_rule_groups " +
                            "(id, requirement_id, sort_order, created_at, updated_at) " +
                            "VALUES (?,?,?,?,?)",
                    groupId, reqId, 0, now, now);

            int ruleOrder = 0;

            // PRODUCTS rule
            if (!plan.selectedSkus().isEmpty()) {
                jdbc.update("INSERT INTO eligibility_rules (id, rule_group_id, rule_type, " +
                                "operator, value, selected_products, field_id, sort_order, " +
                                "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), groupId, "PRODUCTS", null, null,
                        String.join(",", plan.selectedSkus()),
                        salesFieldIds.get("Product SKU"),
                        ruleOrder++, now, now);
            }

            // BOOKING_AMOUNT rule
            jdbc.update("INSERT INTO eligibility_rules (id, rule_group_id, rule_type, " +
                            "operator, value, field_id, sort_order, created_at, updated_at) " +
                            "VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), groupId, "BOOKING_AMOUNT",
                    "GREATER_THAN", plan.minBookingAmount().toPlainString(),
                    salesFieldIds.get("Net Bookings"),
                    ruleOrder++, now, now);

            // CUSTOMER_TYPE rule (on ~30%)
            if (plan.eligibleSegments() != null) {
                jdbc.update("INSERT INTO eligibility_rules (id, rule_group_id, rule_type, " +
                                "operator, value, selected_products, field_id, sort_order, " +
                                "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), groupId, "CUSTOMER_TYPE", "IN",
                        String.join(",", plan.eligibleSegments()),
                        String.join(",", plan.eligibleSegments()),
                        salesFieldIds.get("Customer Segment"),
                        ruleOrder, now, now);
            }

            // Multi-tier payout configs
            List<PayoutConfigRef> payoutConfigs = new ArrayList<>();

            // Cash payout
            PayoutPlan cashPlan = plan.cashPayout();
            UUID payoutCashId = UUID.randomUUID();
            jdbc.update("INSERT INTO payout_configs (id, requirement_id, currency_id, " +
                            "payout_type, against, max_per_deal, sort_order, " +
                            "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    payoutCashId, reqId, "cash", cashPlan.payoutType(),
                    cashPlan.against() != null ? cashPlan.against() : "TOTAL_BOOKING",
                    cashPlan.maxPerDeal(), 0, now, now);
            for (int b = 0; b < cashPlan.bands().size(); b++) {
                BandPlan band = cashPlan.bands().get(b);
                jdbc.update("INSERT INTO payout_bands (id, payout_config_id, min_amount, " +
                                "max_amount, payout_value, sort_order, created_at, updated_at) " +
                                "VALUES (?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), payoutCashId, band.minAmount(), band.maxAmount(),
                        band.payoutValue(), b, now, now);
            }
            payoutConfigs.add(new PayoutConfigRef(payoutCashId, "cash", cashPlan.payoutType(),
                    cashPlan.against() != null ? cashPlan.against() : "TOTAL_BOOKING",
                    cashPlan.maxPerDeal(), cashPlan.bands()));

            // Points payout
            PayoutPlan pointsPlan = plan.pointsPayout();
            UUID payoutPtsId = UUID.randomUUID();
            jdbc.update("INSERT INTO payout_configs (id, requirement_id, currency_id, " +
                            "payout_type, against, max_per_deal, sort_order, " +
                            "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    payoutPtsId, reqId, "points", pointsPlan.payoutType(),
                    pointsPlan.against() != null ? pointsPlan.against() : "TOTAL_BOOKING",
                    pointsPlan.maxPerDeal(), 1, now, now);
            for (int b = 0; b < pointsPlan.bands().size(); b++) {
                BandPlan band = pointsPlan.bands().get(b);
                jdbc.update("INSERT INTO payout_bands (id, payout_config_id, min_amount, " +
                                "max_amount, payout_value, sort_order, created_at, updated_at) " +
                                "VALUES (?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), payoutPtsId, band.minAmount(), band.maxAmount(),
                        band.payoutValue(), b, now, now);
            }
            payoutConfigs.add(new PayoutConfigRef(payoutPtsId, "points", pointsPlan.payoutType(),
                    pointsPlan.against() != null ? pointsPlan.against() : "TOTAL_BOOKING",
                    pointsPlan.maxPerDeal(), pointsPlan.bands()));

            // Budget — two rows: cash + points with PER_LOCATION breakdown
            UUID cashBudgetId = UUID.randomUUID();
            jdbc.update("INSERT INTO incentive_budgets (id, incentive_id, total_budget, " +
                            "currency_id, allocation_method, budget_mode, budget_location_level_id, " +
                            "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    cashBudgetId, incId, plan.cashBudget(), "cash", "EQUAL", "PER_LOCATION",
                    regionLevelId, now, now);
            // Insert location budget allocations for cash
            for (Map.Entry<String, BigDecimal> rb : plan.regionCashBudgets().entrySet()) {
                UUID locValId = regionValueIds.get(rb.getKey());
                if (locValId != null) {
                    jdbc.update("INSERT INTO location_budget_allocations " +
                                    "(id, budget_id, location_value_id, amount, created_at, updated_at) " +
                                    "VALUES (?,?,?,?,?,?)",
                            UUID.randomUUID(), cashBudgetId, locValId, rb.getValue(), now, now);
                }
            }

            UUID pointsBudgetId = UUID.randomUUID();
            jdbc.update("INSERT INTO incentive_budgets (id, incentive_id, total_budget, " +
                            "currency_id, allocation_method, budget_mode, budget_location_level_id, " +
                            "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    pointsBudgetId, incId, plan.pointsBudget(), "points", "EQUAL", "PER_LOCATION",
                    regionLevelId, now, now);
            // Insert location budget allocations for points
            for (Map.Entry<String, BigDecimal> rb : plan.regionPointsBudgets().entrySet()) {
                UUID locValId = regionValueIds.get(rb.getKey());
                if (locValId != null) {
                    jdbc.update("INSERT INTO location_budget_allocations " +
                                    "(id, budget_id, location_value_id, amount, created_at, updated_at) " +
                                    "VALUES (?,?,?,?,?,?)",
                            UUID.randomUUID(), pointsBudgetId, locValId, rb.getValue(), now, now);
                }
            }

            // Budget utilization is now computed from actual claims in createClaims()

            // Track ACTIVE and INACTIVE for eligibility (Part 8 change)
            if ("ACTIVE".equals(plan.status()) || "INACTIVE".equals(plan.status())) {
                allRefs.add(new IncentiveRef(incId, reqId, plan.quarter(),
                        plan.audienceRegions().isEmpty() ? null : plan.targetRegion(),
                        plan.selectedSkus(), plan.minBookingAmount(), plan.eligibleSegments(),
                        payoutConfigs));
            }
        }

        log.info("Created {} total SALES incentive refs for eligibility evaluation", allRefs.size());
        return allRefs;
    }

    // ── Training Incentive Generation ───────────────────────────────────────────

    /**
     * Creates TRAINING incentives with course assignments and budgets.
     *
     * @param clientId       the client UUID
     * @param adminUserId    admin user who "created" the incentives
     * @param quarters       all fiscal quarters to seed
     * @param regionLevelId  UUID of the region-level location_level (depth=0)
     * @param regionValueIds map of region name to location_value UUID
     * @param random         seeded Random for deterministic output
     * @return list of non-sales incentive refs for journey linking
     */
    public List<NonSalesIncentiveRef> createTrainingIncentives(UUID clientId, UUID adminUserId,
                                                                List<FiscalQuarter> quarters,
                                                                UUID regionLevelId,
                                                                Map<String, UUID> regionValueIds,
                                                                UUID countryLevelId,
                                                                Map<String, UUID> countryValueIds,
                                                                Map<String, List<String>> regionToCountries,
                                                                Random random) {
        List<NonSalesIncentiveRef> refs = new ArrayList<>();
        FiscalQuarter currentQuarter = quarters.get(quarters.size() - 1);

        for (FiscalQuarter fq : quarters) {
            Timestamp qStart = Timestamp.from(
                    fq.startDate().atStartOfDay(ZoneOffset.UTC).toInstant());
            // Incentive created 7-30 days before quarter start
            Timestamp now = Timestamp.from(
                    fq.startDate().minusDays(7 + random.nextInt(24))
                            .atStartOfDay(ZoneOffset.UTC).toInstant());
            Timestamp qEnd = Timestamp.from(
                    fq.endDate().atTime(23, 59, 59).toInstant(ZoneOffset.UTC));
            boolean isCurrent = fq.fyYear() == currentQuarter.fyYear()
                    && fq.qLabel().equals(currentQuarter.qLabel());
            boolean isPast = !isCurrent;

            for (String region : REGIONS) {
                // 3-5 training incentives per region per quarter. BUG-019's
                // AMERICAS-current-quarter ≥2 requirement is subsumed by this range.
                int numTraining = 3 + random.nextInt(3); // 3-5

                for (int t = 0; t < numTraining; t++) {
                    String tName = region + " " + fq.qLabel() + " "
                            + PRODUCT_FOCUS[random.nextInt(PRODUCT_FOCUS.length)]
                            + " Training #" + (t + 1);
                    String tStatus = isPast ? "INACTIVE" : "ACTIVE";

                    // Assign 2-5 courses
                    int numCourses = 2 + random.nextInt(4);
                    List<int[]> courseIndices = new ArrayList<>();
                    Set<Integer> usedIndices = new HashSet<>();
                    while (courseIndices.size() < numCourses
                            && usedIndices.size() < TRAINING_COURSES.length) {
                        int idx = random.nextInt(TRAINING_COURSES.length);
                        if (usedIndices.add(idx)) {
                            courseIndices.add(new int[]{idx});
                        }
                    }

                    // Reward amounts: 80% non-monetary, 20% monetary
                    boolean useMonetary = random.nextDouble() < 0.20;
                    String rewardCurrencies;
                    String rewardAmounts;
                    String rewardMessage;
                    String trainingBudgetCurrency;
                    BigDecimal trainingBudgetAmount;
                    LinkedHashMap<String, Long> trainingRewardMaxes = new LinkedHashMap<>();
                    if (useMonetary) {
                        int cash = pickOne(NICE_ENABLEMENT_CASH, random);
                        int pts = pickOne(NICE_ENABLEMENT_POINTS, random);
                        rewardCurrencies = "[\"cash\",\"points\"]";
                        rewardAmounts = String.format(
                                "{\"cash\":\"%d\",\"points\":\"%d\"}", cash, pts);
                        trainingRewardMaxes.put("cash", (long) cash);
                        trainingRewardMaxes.put("points", (long) pts);
                        trainingBudgetCurrency = "cash";
                        trainingBudgetAmount = BigDecimal.valueOf(
                                roundedRandom(10000, 50000, 5000, random));
                    } else {
                        double rewardRoll = random.nextDouble();
                        if (rewardRoll < 0.50) {
                            int credits = pickOne(NICE_CREDITS, random);
                            rewardCurrencies = "[\"credits\"]";
                            rewardAmounts = String.format("{\"credits\":\"%d\"}", credits);
                            trainingRewardMaxes.put("credits", (long) credits);
                            trainingBudgetCurrency = "credits";
                            trainingBudgetAmount = BigDecimal.valueOf(
                                    roundedRandom(50, 500, 50, random));
                        } else if (rewardRoll < 0.80) {
                            int tickets = pickOne(NICE_TICKETS, random);
                            rewardCurrencies = "[\"tickets\"]";
                            rewardAmounts = String.format("{\"tickets\":\"%d\"}", tickets);
                            trainingRewardMaxes.put("tickets", (long) tickets);
                            trainingBudgetCurrency = "tickets";
                            trainingBudgetAmount = BigDecimal.valueOf(
                                    roundedRandom(50, 500, 50, random));
                        } else {
                            int credits = pickOne(NICE_CREDITS, random);
                            int tickets = pickOne(NICE_TICKETS, random);
                            rewardCurrencies = "[\"credits\",\"tickets\"]";
                            rewardAmounts = String.format(
                                    "{\"credits\":\"%d\",\"tickets\":\"%d\"}", credits, tickets);
                            trainingRewardMaxes.put("credits", (long) credits);
                            trainingRewardMaxes.put("tickets", (long) tickets);
                            trainingBudgetCurrency = "credits";
                            trainingBudgetAmount = BigDecimal.valueOf(
                                    roundedRandom(50, 500, 50, random));
                        }
                    }
                    rewardMessage = formatRewardMessage(trainingRewardMaxes);

                    UUID tId = UUID.randomUUID();
                    jdbc.update("INSERT INTO incentives (id, name, description, incentive_type, " +
                                    "status, client_id, created_by, start_date, end_date, deleted, " +
                                    "requires_approval, required_approvals, journey_sequential, " +
                                    "approval_round, max_claimers_per_deal, " +
                                    "reward_currencies, reward_amounts, reward_message, " +
                                    "training_required_count, created_at, updated_at) " +
                                    "VALUES (?,?,?,?,?,?,?,?,?,false,false,0,true,1,1,?,?,?,?,?,?)",
                            tId, tName,
                            "Complete required training courses to earn certification rewards.",
                            "TRAINING", tStatus, clientId, adminUserId, qStart, qEnd,
                            rewardCurrencies, rewardAmounts, rewardMessage,
                            courseIndices.size(), now, now);

                    // Single-region audience: one LOCATION rule per region.
                    insertLocationAudienceRules(tId, List.of(region), regionLevelId,
                            regionValueIds, now);

                    // Also write country-level LOCATION rules (randomized count).
                    List<String> tCountries = pickCountriesForRegions(
                            List.of(region), regionToCountries, random);
                    insertCountryAudienceRules(tId, tCountries, countryLevelId,
                            countryValueIds, now);

                    // ROLE audience rules
                    double roleRoll = random.nextDouble();
                    List<String> roles;
                    if (roleRoll < 0.60) {
                        roles = List.of(ROLE_COMPANY_ADMIN, ROLE_PARTNER_SELLER);
                    } else if (roleRoll < 0.90) {
                        roles = List.of(ROLE_PARTNER_SELLER);
                    } else {
                        roles = List.of(ROLE_COMPANY_ADMIN);
                    }
                    insertRoleAudienceRules(tId, clientId, roles, now);

                    // Training course assignments
                    int sortOrder = 0;
                    for (int[] ci : courseIndices) {
                        String[] course = TRAINING_COURSES[ci[0]];
                        String courseId = "LMS-" + UUID.randomUUID().toString().substring(0, 8);
                        jdbc.update("INSERT INTO training_course_assignments " +
                                        "(id, incentive_id, course_id, course_name, course_category, " +
                                        "course_provider, course_duration, course_level, required, " +
                                        "sort_order, created_at, updated_at) " +
                                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                                UUID.randomUUID(), tId, courseId, course[0], course[1],
                                course[2], course[3], course[4], true, sortOrder++, now, now);
                    }

                    // Budget — currency matches primary reward currency
                    jdbc.update("INSERT INTO incentive_budgets (id, incentive_id, total_budget, " +
                                    "currency_id, allocation_method, budget_mode, " +
                                    "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                            UUID.randomUUID(), tId, trainingBudgetAmount,
                            trainingBudgetCurrency, "EQUAL", "GLOBAL", now, now);

                    refs.add(new NonSalesIncentiveRef(tId, "TRAINING", fq, region));
                }
            }
        }
        return refs;
    }

    // ── Activity Incentive Generation ───────────────────────────────────────────

    /**
     * Creates ACTIVITY incentives with activity definitions, document requirements,
     * and budgets.
     *
     * @param clientId       the client UUID
     * @param adminUserId    admin user who "created" the incentives
     * @param quarters       all fiscal quarters to seed
     * @param regionLevelId  UUID of the region-level location_level (depth=0)
     * @param regionValueIds map of region name to location_value UUID
     * @param random         seeded Random for deterministic output
     * @return list of non-sales incentive refs for journey linking
     */
    public List<NonSalesIncentiveRef> createActivityIncentives(UUID clientId, UUID adminUserId,
                                                                List<FiscalQuarter> quarters,
                                                                UUID regionLevelId,
                                                                Map<String, UUID> regionValueIds,
                                                                UUID countryLevelId,
                                                                Map<String, UUID> countryValueIds,
                                                                Map<String, List<String>> regionToCountries,
                                                                Random random) {
        List<NonSalesIncentiveRef> refs = new ArrayList<>();
        FiscalQuarter currentQuarter = quarters.get(quarters.size() - 1);

        for (FiscalQuarter fq : quarters) {
            Timestamp qStart = Timestamp.from(
                    fq.startDate().atStartOfDay(ZoneOffset.UTC).toInstant());
            Timestamp now = Timestamp.from(
                    fq.startDate().minusDays(7 + random.nextInt(24))
                            .atStartOfDay(ZoneOffset.UTC).toInstant());
            Timestamp qEnd = Timestamp.from(
                    fq.endDate().atTime(23, 59, 59).toInstant(ZoneOffset.UTC));
            boolean isCurrent = fq.fyYear() == currentQuarter.fyYear()
                    && fq.qLabel().equals(currentQuarter.qLabel());
            boolean isPast = !isCurrent;

            for (String region : REGIONS) {
                // 3-5 activity incentives per region per quarter. BUG-019's
                // AMERICAS-current-quarter ≥1 requirement is subsumed by this range.
                int numActivity = 3 + random.nextInt(3); // 3-5

                for (int act = 0; act < numActivity; act++) {
                    String aName = region + " " + fq.qLabel() + " Partner Activity "
                            + PROGRAM_TYPES[random.nextInt(PROGRAM_TYPES.length)]
                            + " #" + (act + 1);
                    String aStatus = isPast ? "INACTIVE" : "ACTIVE";

                    // Reward amounts: 80% non-monetary, 20% monetary
                    boolean useMonetary = random.nextDouble() < 0.20;
                    String rewardCurrencies;
                    String rewardAmounts;
                    String activityBudgetCurrency;
                    BigDecimal activityBudgetAmount;
                    LinkedHashMap<String, Long> activityRewardMaxes = new LinkedHashMap<>();
                    if (useMonetary) {
                        int cash = pickOne(NICE_ENABLEMENT_CASH, random);
                        int pts = pickOne(NICE_ENABLEMENT_POINTS, random);
                        rewardCurrencies = "[\"cash\",\"points\"]";
                        rewardAmounts = String.format(
                                "{\"cash\":\"%d\",\"points\":\"%d\"}", cash, pts);
                        activityRewardMaxes.put("cash", (long) cash);
                        activityRewardMaxes.put("points", (long) pts);
                        activityBudgetCurrency = "cash";
                        activityBudgetAmount = BigDecimal.valueOf(
                                roundedRandom(10000, 50000, 5000, random));
                    } else {
                        double rewardRoll = random.nextDouble();
                        if (rewardRoll < 0.50) {
                            int credits = pickOne(NICE_CREDITS, random);
                            rewardCurrencies = "[\"credits\"]";
                            rewardAmounts = String.format("{\"credits\":\"%d\"}", credits);
                            activityRewardMaxes.put("credits", (long) credits);
                            activityBudgetCurrency = "credits";
                            activityBudgetAmount = BigDecimal.valueOf(
                                    roundedRandom(50, 500, 50, random));
                        } else if (rewardRoll < 0.80) {
                            int tickets = pickOne(NICE_TICKETS, random);
                            rewardCurrencies = "[\"tickets\"]";
                            rewardAmounts = String.format("{\"tickets\":\"%d\"}", tickets);
                            activityRewardMaxes.put("tickets", (long) tickets);
                            activityBudgetCurrency = "tickets";
                            activityBudgetAmount = BigDecimal.valueOf(
                                    roundedRandom(50, 500, 50, random));
                        } else {
                            int credits = pickOne(NICE_CREDITS, random);
                            int tickets = pickOne(NICE_TICKETS, random);
                            rewardCurrencies = "[\"credits\",\"tickets\"]";
                            rewardAmounts = String.format(
                                    "{\"credits\":\"%d\",\"tickets\":\"%d\"}", credits, tickets);
                            activityRewardMaxes.put("credits", (long) credits);
                            activityRewardMaxes.put("tickets", (long) tickets);
                            activityBudgetCurrency = "credits";
                            activityBudgetAmount = BigDecimal.valueOf(
                                    roundedRandom(50, 500, 50, random));
                        }
                    }
                    String rewardMessage = formatRewardMessage(activityRewardMaxes);

                    UUID aId = UUID.randomUUID();
                    jdbc.update("INSERT INTO incentives (id, name, description, incentive_type, " +
                                    "status, client_id, created_by, start_date, end_date, deleted, " +
                                    "requires_approval, required_approvals, journey_sequential, " +
                                    "approval_round, max_claimers_per_deal, " +
                                    "reward_currencies, reward_amounts, reward_message, " +
                                    "created_at, updated_at) " +
                                    "VALUES (?,?,?,?,?,?,?,?,?,false,false,0,true,1,1,?,?,?,?,?)",
                            aId, aName,
                            "Complete partner activities to earn bonus rewards.",
                            "ACTIVITY", aStatus, clientId, adminUserId, qStart, qEnd,
                            rewardCurrencies, rewardAmounts, rewardMessage, now, now);

                    // Single-region audience: one LOCATION rule per region.
                    insertLocationAudienceRules(aId, List.of(region), regionLevelId,
                            regionValueIds, now);

                    // Also write country-level LOCATION rules (randomized count).
                    List<String> aCountries = pickCountriesForRegions(
                            List.of(region), regionToCountries, random);
                    insertCountryAudienceRules(aId, aCountries, countryLevelId,
                            countryValueIds, now);

                    // ROLE audience rules
                    double roleRoll = random.nextDouble();
                    List<String> roles;
                    if (roleRoll < 0.60) {
                        roles = List.of(ROLE_COMPANY_ADMIN, ROLE_PARTNER_SELLER);
                    } else if (roleRoll < 0.90) {
                        roles = List.of(ROLE_PARTNER_SELLER);
                    } else {
                        roles = List.of(ROLE_COMPANY_ADMIN);
                    }
                    insertRoleAudienceRules(aId, clientId, roles, now);

                    // Activity definitions (1-3 per incentive)
                    int numActivities = 1 + random.nextInt(3);
                    Set<String> usedCategories = new HashSet<>();

                    for (int a = 0; a < numActivities; a++) {
                        // Pick a category not yet used for this incentive
                        String category;
                        do {
                            category = ACTIVITY_CATEGORY_NAMES[
                                    random.nextInt(ACTIVITY_CATEGORY_NAMES.length)];
                        } while (usedCategories.contains(category)
                                && usedCategories.size() < ACTIVITY_CATEGORY_NAMES.length);
                        usedCategories.add(category);

                        String[][] templates = ACTIVITY_TEMPLATES.get(category);
                        String[] template = templates[random.nextInt(templates.length)];

                        UUID adId = UUID.randomUUID();
                        jdbc.update("INSERT INTO activity_definitions " +
                                        "(id, incentive_id, name, description, category_id, " +
                                        "sort_order, created_at, updated_at) " +
                                        "VALUES (?,?,?,?,?,?,?,?)",
                                adId, aId, template[0], template[1], category, a, now, now);

                        // Document requirements (1-2 per activity definition)
                        int numDocs = 1 + random.nextInt(2);
                        Set<Integer> usedDocs = new HashSet<>();
                        for (int d = 0; d < numDocs; d++) {
                            int docIdx;
                            do {
                                docIdx = random.nextInt(DOC_REQUIREMENTS.length);
                            } while (usedDocs.contains(docIdx)
                                    && usedDocs.size() < DOC_REQUIREMENTS.length);
                            usedDocs.add(docIdx);

                            jdbc.update("INSERT INTO activity_document_requirements " +
                                            "(id, activity_definition_id, name, description, " +
                                            "required, sort_order, created_at, updated_at) " +
                                            "VALUES (?,?,?,?,?,?,?,?)",
                                    UUID.randomUUID(), adId,
                                    DOC_REQUIREMENTS[docIdx][0], DOC_REQUIREMENTS[docIdx][1],
                                    true, d, now, now);
                        }
                    }

                    // Budget — currency matches primary reward currency
                    jdbc.update("INSERT INTO incentive_budgets (id, incentive_id, total_budget, " +
                                    "currency_id, allocation_method, budget_mode, " +
                                    "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                            UUID.randomUUID(), aId, activityBudgetAmount,
                            activityBudgetCurrency, "EQUAL", "GLOBAL", now, now);

                    refs.add(new NonSalesIncentiveRef(aId, "ACTIVITY", fq, region));
                }
            }
        }
        return refs;
    }

    // ── Journey Incentive Generation ────────────────────────────────────────────

    /**
     * Creates JOURNEY incentives that link training and activity incentives as stages.
     *
     * @param clientId       the client UUID
     * @param adminUserId    admin user who "created" the incentives
     * @param quarters       all fiscal quarters to seed
     * @param nonSalesRefs   training + activity refs to use as journey stages
     * @param regionLevelId  UUID of the region-level location_level (depth=0), reserved for future use
     * @param regionValueIds map of region name to location_value UUID, reserved for future use
     * @param random         seeded Random for deterministic output
     */
    public JourneyCompletionExclusions createJourneyIncentives(UUID clientId, UUID adminUserId,
                                         List<FiscalQuarter> quarters,
                                         List<NonSalesIncentiveRef> nonSalesRefs,
                                         UUID regionLevelId,
                                         Map<String, UUID> regionValueIds,
                                         UUID countryLevelId,
                                         Map<String, UUID> countryValueIds,
                                         Map<String, List<String>> regionToCountries,
                                         Random random) {
        FiscalQuarter currentQuarter = quarters.get(quarters.size() - 1);

        // Group non-sales refs by quarter display name
        Map<String, List<NonSalesIncentiveRef>> refsByQuarter = new HashMap<>();
        for (NonSalesIncentiveRef ref : nonSalesRefs) {
            refsByQuarter.computeIfAbsent(ref.quarter().displayName(), k -> new ArrayList<>())
                    .add(ref);
        }

        // BUG-019 verification support: identify AMERICAS partner users so that the
        // second current-quarter AMERICAS Journey (seeded below) can pre-complete its
        // first stage for them — guarantees a deterministic "in progress" Journey for
        // /bug-verify. Empty list is fine — the completion insert is simply a no-op.
        List<UUID> americasPartnerUserIds = jdbc.queryForList(
                "SELECT id FROM users WHERE client_id = ? AND email IN (?, ?)",
                UUID.class, clientId,
                "seller@techpartners.com", "partneradmin@techpartners.com");

        // Per-user skip-list for CompletionSeeder so the two current-quarter AMERICAS
        // Journeys stay in their seeded states — variant 0 has zero completions, variant
        // 1 has exactly one (its first stage). Without this, CompletionSeeder's 35%
        // random completion of ACTIVE non-sales incentives would silently complete stage
        // enablements for the seeded AMERICAS partner users and break both invariants.
        Map<UUID, Set<UUID>> exclusionsByUser = new HashMap<>();
        for (UUID partnerUserId : americasPartnerUserIds) {
            exclusionsByUser.put(partnerUserId, new HashSet<>());
        }

        int journeyCount = 0;
        for (FiscalQuarter fq : quarters) {
            boolean isCurrent = fq.fyYear() == currentQuarter.fyYear()
                    && fq.qLabel().equals(currentQuarter.qLabel());
            boolean isPast = !isCurrent;

            Timestamp now = Timestamp.from(
                    fq.startDate().minusDays(7 + random.nextInt(24))
                            .atStartOfDay(ZoneOffset.UTC).toInstant());

            List<NonSalesIncentiveRef> quarterRefs = refsByQuarter.getOrDefault(
                    fq.displayName(), List.of());
            if (quarterRefs.size() < 2) continue; // Need at least 2 stages somewhere

            // BUG-019: Journey stages must share the Journey's region. Group refs by
            // region so each region's journeys draw only from its own stage pool.
            Map<String, List<NonSalesIncentiveRef>> refsByRegion = new HashMap<>();
            for (NonSalesIncentiveRef ref : quarterRefs) {
                refsByRegion.computeIfAbsent(ref.region(), k -> new ArrayList<>())
                        .add(ref);
            }

            for (String region : REGIONS) {
                // Defense-in-depth: dedupe by incentive id and keep only refs whose
                // region actually matches (guards against any upstream shuffle bug).
                LinkedHashMap<UUID, NonSalesIncentiveRef> dedupByRegion = new LinkedHashMap<>();
                for (NonSalesIncentiveRef ref : refsByRegion.getOrDefault(region, List.of())) {
                    if (region.equals(ref.region())) {
                        dedupByRegion.putIfAbsent(ref.id(), ref);
                    }
                }
                List<NonSalesIncentiveRef> regionRefs = new ArrayList<>(dedupByRegion.values());
                if (regionRefs.size() < 2) continue; // Need ≥2 same-region stage candidates

                // 3-5 journeys per (region, quarter). BUG-019: for AMERICAS current
                // quarter, journey 0 is variant 0 (zero completions) and journey 1 is
                // variant 1 (first stage pre-completed for seeded AMERICAS partner
                // users). Journeys 2..n and all other (region, quarter) pairs use the
                // ordinary path (variant == -1).
                int numJourneys = 3 + random.nextInt(3); // 3-5
                boolean twoVariants = isCurrent && "AMERICAS".equals(region);
                int previousProgramTypeIndex = -1;
                // Tracks variant 0's stage incentive ids so variant 1's first stage can
                // be picked disjoint — otherwise the "zero completions" and "first
                // stage completed" invariants would contradict on any shared stage.
                Set<UUID> variantZeroStageIds = new HashSet<>();

                for (int j = 0; j < numJourneys; j++) {
                    int variant = (twoVariants && j < 2) ? j : -1;

                    // Program type: for variant 1, deliberately differ from variant 0
                    // so the two BUG-019 journey names don't collide.
                    int programTypeIndex;
                    if (variant == 1 && previousProgramTypeIndex >= 0 && PROGRAM_TYPES.length > 1) {
                        int offset = 1 + random.nextInt(PROGRAM_TYPES.length - 1);
                        programTypeIndex = (previousProgramTypeIndex + offset) % PROGRAM_TYPES.length;
                    } else {
                        programTypeIndex = random.nextInt(PROGRAM_TYPES.length);
                        if (variant == 0) previousProgramTypeIndex = programTypeIndex;
                    }
                    String jName = fq.displayName() + " " + region + " Partner Journey "
                            + PROGRAM_TYPES[programTypeIndex] + " #" + (j + 1);

                    // For FY2026 Q1 journeys: extend end date and set ACTIVE
                    Timestamp qStart = Timestamp.from(
                            fq.startDate().atStartOfDay(ZoneOffset.UTC).toInstant());
                    Timestamp qEnd;
                    String jStatus;
                    if (fq.fyYear() == 2026 && "Q1".equals(fq.qLabel())) {
                        qEnd = Timestamp.from(LocalDate.of(2026, 6, 30)
                                .atTime(23, 59, 59).toInstant(ZoneOffset.UTC));
                        jStatus = "ACTIVE";
                    } else {
                        qEnd = Timestamp.from(
                                fq.endDate().atTime(23, 59, 59).toInstant(ZoneOffset.UTC));
                        jStatus = isPast ? "INACTIVE" : "ACTIVE";
                    }

                    // Journey sequential: 70% true, 30% false
                    boolean journeySequential = random.nextDouble() < 0.70;

                    // 50% of journeys get own reward
                    boolean hasOwnReward = random.nextDouble() < 0.50;
                    String rewardCurrencies = null;
                    String rewardAmounts = null;
                    String rewardMessage = null;
                    boolean journeyMonetary = false;
                    if (hasOwnReward) {
                        LinkedHashMap<String, Long> journeyRewardMaxes = new LinkedHashMap<>();
                        journeyMonetary = random.nextDouble() < 0.50;
                        if (journeyMonetary) {
                            int cash = pickOne(NICE_ENABLEMENT_CASH, random);
                            int pts = pickOne(NICE_ENABLEMENT_POINTS, random);
                            rewardCurrencies = "[\"cash\",\"points\"]";
                            rewardAmounts = String.format(
                                    "{\"cash\":\"%d\",\"points\":\"%d\"}", cash, pts);
                            journeyRewardMaxes.put("cash", (long) cash);
                            journeyRewardMaxes.put("points", (long) pts);
                        } else {
                            int credits = pickOne(NICE_CREDITS, random);
                            int tickets = pickOne(NICE_TICKETS, random);
                            rewardCurrencies = "[\"credits\",\"tickets\"]";
                            rewardAmounts = String.format(
                                    "{\"credits\":\"%d\",\"tickets\":\"%d\"}", credits, tickets);
                            journeyRewardMaxes.put("credits", (long) credits);
                            journeyRewardMaxes.put("tickets", (long) tickets);
                        }
                        rewardMessage = formatRewardMessage(journeyRewardMaxes);
                    }

                    UUID jId = UUID.randomUUID();
                    jdbc.update("INSERT INTO incentives (id, name, description, incentive_type, " +
                                    "status, client_id, created_by, start_date, end_date, deleted, " +
                                    "requires_approval, required_approvals, journey_sequential, " +
                                    "approval_round, max_claimers_per_deal, " +
                                    "reward_currencies, reward_amounts, reward_message, " +
                                    "created_at, updated_at) " +
                                    "VALUES (?,?,?,?,?,?,?,?,?,false,false,0,?,1,1,?,?,?,?,?)",
                            jId, jName,
                            "Multi-stage partner journey linking training, activities, and sales.",
                            "JOURNEY", jStatus, clientId, adminUserId, qStart, qEnd,
                            journeySequential,
                            rewardCurrencies, rewardAmounts, rewardMessage, now, now);

                    // ROLE audience rules
                    double roleRoll = random.nextDouble();
                    List<String> roles;
                    if (roleRoll < 0.60) {
                        roles = List.of(ROLE_COMPANY_ADMIN, ROLE_PARTNER_SELLER);
                    } else if (roleRoll < 0.90) {
                        roles = List.of(ROLE_PARTNER_SELLER);
                    } else {
                        roles = List.of(ROLE_COMPANY_ADMIN);
                    }
                    insertRoleAudienceRules(jId, clientId, roles, now);

                    // LOCATION audience rule — matches the region the stages are
                    // filtered to, enforcing stages-share-region invariant.
                    insertLocationAudienceRules(jId, List.of(region), regionLevelId,
                            regionValueIds, now);

                    // Also write country-level LOCATION rules (randomized count).
                    List<String> jCountries = pickCountriesForRegions(
                            List.of(region), regionToCountries, random);
                    insertCountryAudienceRules(jId, jCountries, countryLevelId,
                            countryValueIds, now);

                    // Pick 2-4 stages from this region's training/activity incentives.
                    // For BUG-019 variant 0 we reserve at least one ref so variant 1
                    // can place a disjoint first stage at sort_order=0; if the pool is
                    // too small for that (< 3 refs), skip variant 1.
                    if (variant == 1 && regionRefs.size() < 3) {
                        continue;
                    }
                    int maxStages = (variant == 0)
                            ? Math.min(regionRefs.size() - 1, 4)
                            : regionRefs.size();
                    int numStages = Math.min(2 + random.nextInt(3), maxStages);
                    List<NonSalesIncentiveRef> shuffledRefs = new ArrayList<>(regionRefs);
                    Collections.shuffle(shuffledRefs, random);

                    // BUG-019: variant 1's first stage must not be one of variant 0's
                    // stages; otherwise completing it breaks variant 0's invariant.
                    // Swap a disjoint ref into position 0.
                    boolean variantOneFirstStageIsDisjoint = true;
                    if (variant == 1 && !variantZeroStageIds.isEmpty()) {
                        if (variantZeroStageIds.contains(shuffledRefs.get(0).id())) {
                            int firstDisjoint = -1;
                            for (int i = 1; i < shuffledRefs.size(); i++) {
                                if (!variantZeroStageIds.contains(shuffledRefs.get(i).id())) {
                                    firstDisjoint = i;
                                    break;
                                }
                            }
                            if (firstDisjoint > 0) {
                                NonSalesIncentiveRef tmp = shuffledRefs.get(0);
                                shuffledRefs.set(0, shuffledRefs.get(firstDisjoint));
                                shuffledRefs.set(firstDisjoint, tmp);
                            } else {
                                // Defensive: variant 0's reservation should have left
                                // at least one disjoint ref. If not, skip pre-completion
                                // rather than violate variant 0's invariant.
                                variantOneFirstStageIsDisjoint = false;
                            }
                        }
                    }

                    for (int s = 0; s < numStages; s++) {
                        jdbc.update("INSERT INTO journey_stages (id, incentive_id, linked_incentive_id, " +
                                        "sort_order, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                                UUID.randomUUID(), jId, shuffledRefs.get(s).id(), s, now, now);
                    }

                    if (variant == 0) {
                        for (int s = 0; s < numStages; s++) {
                            variantZeroStageIds.add(shuffledRefs.get(s).id());
                        }
                    }

                    // BUG-019: pre-complete variant 1's first stage for seeded AMERICAS
                    // partner users. ON CONFLICT DO NOTHING makes this re-seed-safe.
                    if (variant == 1 && !americasPartnerUserIds.isEmpty() && !shuffledRefs.isEmpty()
                            && variantOneFirstStageIsDisjoint) {
                        UUID firstStageIncentiveId = shuffledRefs.get(0).id();
                        for (UUID targetUserId : americasPartnerUserIds) {
                            jdbc.update(
                                    "INSERT INTO user_incentive_completions " +
                                            "(id, client_id, incentive_id, user_id, completed_at, created_at) " +
                                            "VALUES (?,?,?,?,?,?) " +
                                            "ON CONFLICT (client_id, incentive_id, user_id) DO NOTHING",
                                    UUID.randomUUID(), clientId, firstStageIncentiveId,
                                    targetUserId, now, now);
                        }
                    }

                    // Record CompletionSeeder skip-list entries for BUG-019 variants.
                    // Variant 0 must stay at zero completions; variant 1 must stay at
                    // exactly its pre-completed first stage. Exclude the Journey
                    // incentive itself too so CompletionSeeder can't mark it complete
                    // out of band. Ordinary journeys (variant == -1) are unaffected.
                    if (variant >= 0 && !americasPartnerUserIds.isEmpty()) {
                        for (UUID partnerUserId : americasPartnerUserIds) {
                            Set<UUID> excluded = exclusionsByUser.get(partnerUserId);
                            excluded.add(jId);
                            if (variant == 0) {
                                for (int s = 0; s < numStages; s++) {
                                    excluded.add(shuffledRefs.get(s).id());
                                }
                            } else {
                                // variant == 1: skip every stage except the first
                                // (pre-completed above via INSERT ... ON CONFLICT).
                                for (int s = 1; s < numStages; s++) {
                                    excluded.add(shuffledRefs.get(s).id());
                                }
                            }
                        }
                    }

                    // Budget (if journey has own rewards)
                    if (hasOwnReward) {
                        String journeyBudgetCurrency;
                        BigDecimal journeyBudgetAmount;
                        if (journeyMonetary) {
                            journeyBudgetCurrency = "cash";
                            journeyBudgetAmount = BigDecimal.valueOf(
                                    roundedRandom(25000, 100000, 5000, random));
                        } else {
                            journeyBudgetCurrency = "credits";
                            journeyBudgetAmount = BigDecimal.valueOf(
                                    roundedRandom(100, 1000, 50, random));
                        }
                        jdbc.update("INSERT INTO incentive_budgets (id, incentive_id, total_budget, " +
                                        "currency_id, allocation_method, budget_mode, " +
                                        "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                                UUID.randomUUID(), jId, journeyBudgetAmount,
                                journeyBudgetCurrency, "EQUAL", "GLOBAL", now, now);
                    }

                    journeyCount++;
                } // end journey loop per region
            } // end region loop per quarter
        }
        log.info("Created {} JOURNEY incentives with linked stages", journeyCount);
        return new JourneyCompletionExclusions(exclusionsByUser);
    }

    // ── Audience Rule Helpers ───────────────────────────────────────────────────

    /**
     * Inserts LOCATION audience rules for the given region names.
     *
     * <p>Writes the canonical shape: rule_type=LOCATION, rule_value=location_value UUID,
     * location_level_id populated. This is the only shape the eligibility and deal-qualifier
     * services honor; REGION/COUNTRY rule types are rejected at the API boundary.
     *
     * @param incentiveId    the incentive to attach rules to
     * @param regionNames    list of region names (e.g. "AMERICAS", "EMEAR")
     * @param regionLevelId  UUID of the region-level location_level (depth=0)
     * @param regionValueIds map of region name to location_value UUID
     * @param now            timestamp for created_at/updated_at
     */
    public void insertLocationAudienceRules(UUID incentiveId, List<String> regionNames,
                                             UUID regionLevelId, Map<String, UUID> regionValueIds,
                                             Timestamp now) {
        for (String regionName : regionNames) {
            UUID locationValueId = regionValueIds.get(regionName);
            if (locationValueId != null) {
                jdbc.update("INSERT INTO incentive_audience_rules " +
                                "(id, incentive_id, rule_type, rule_value, location_level_id, " +
                                "created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                        UUID.randomUUID(), incentiveId, "LOCATION",
                        locationValueId.toString(), regionLevelId, now, now);
            }
        }
    }

    /**
     * Picks a randomized subset of countries under the given regions to scope a
     * seeded incentive to. Produces variety across seeded data so the UI sees
     * both "broad" eligibility (most or all countries under a region) and
     * "narrow" eligibility (a handful) shapes.
     * <p>
     * Distribution: 40% of incentives get the full union (broad), the rest get
     * a uniformly-random subset between 1 and N. Countries are deduped and
     * returned in deterministic order for the supplied {@code random} seed.
     *
     * @param regionNames       region names this incentive is scoped to
     * @param regionToCountries map of region name → list of country names
     * @param random            seeded {@link Random} so the seed run stays
     *                          reproducible
     * @return picked country names (possibly empty if no regions or no
     *         children)
     */
    public List<String> pickCountriesForRegions(List<String> regionNames,
                                                 Map<String, List<String>> regionToCountries,
                                                 Random random) {
        if (regionNames == null || regionNames.isEmpty()) return List.of();
        if (regionToCountries == null) return List.of();
        java.util.LinkedHashSet<String> union = new java.util.LinkedHashSet<>();
        for (String region : regionNames) {
            union.addAll(regionToCountries.getOrDefault(region, List.of()));
        }
        if (union.isEmpty()) return List.of();
        List<String> all = new ArrayList<>(union);
        // 40% chance to keep the full set — the broad-eligibility path. Real
        // programs more often span "most countries in a region" than "a few".
        if (random.nextDouble() < 0.40) {
            return all;
        }
        java.util.Collections.shuffle(all, random);
        int count = 1 + random.nextInt(all.size());
        return new ArrayList<>(all.subList(0, count));
    }

    /**
     * Inserts country-level LOCATION audience rules for the supplied country
     * names. The Country level is configured as required in
     * {@code location_levels}, so seeded incentives that omit it would render
     * an empty Country MultiSelect when a Client Admin opens them in the
     * builder. Mirrors the region-level write at
     * {@link #insertLocationAudienceRules}.
     * <p>
     * Country names that aren't present in {@code countryValueIds} are silently
     * skipped — same shape as {@link #insertLocationAudienceRules}. Use
     * {@link #pickCountriesForRegions} to derive the input list from the
     * region selection.
     *
     * @param incentiveId     UUID of the incentive being seeded
     * @param countryNames    countries this incentive is scoped to
     * @param countryLevelId  UUID of the location_levels row for Country
     * @param countryValueIds map of country name → location_value UUID
     * @param now             timestamp for created_at/updated_at
     */
    public void insertCountryAudienceRules(UUID incentiveId,
                                            List<String> countryNames,
                                            UUID countryLevelId,
                                            Map<String, UUID> countryValueIds,
                                            Timestamp now) {
        if (countryNames == null || countryNames.isEmpty()) return;
        if (countryLevelId == null || countryValueIds == null) return;
        for (String countryName : countryNames) {
            UUID countryValueId = countryValueIds.get(countryName);
            if (countryValueId != null) {
                jdbc.update("INSERT INTO incentive_audience_rules " +
                                "(id, incentive_id, rule_type, rule_value, location_level_id, " +
                                "created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                        UUID.randomUUID(), incentiveId, "LOCATION",
                        countryValueId.toString(), countryLevelId, now, now);
            }
        }
    }

    /**
     * Inserts ROLE audience rules for the given role names.
     * <p>
     * BUG-020: rule_value now holds ClientRole.id (UUID) instead of the display name,
     * matching the pattern LOCATION rules already follow. The seeder resolves each
     * display name against client_roles (scoped to the supplied clientId) before the
     * insert. Missing roles fail the seed loudly — unknown names indicate a bug in
     * the seed plan, not something to silently skip.
     */
    public void insertRoleAudienceRules(UUID incentiveId, UUID clientId,
                                        List<String> roleDisplayNames, Timestamp now) {
        Map<String, UUID> roleIdsByName = loadRoleIdsByName(clientId);
        for (String displayName : roleDisplayNames) {
            UUID roleId = roleIdsByName.get(displayName);
            if (roleId == null) {
                throw new IllegalStateException(
                    "BUG-020 seed: no ClientRole with name '" + displayName
                        + "' exists for client " + clientId
                        + ". Known names: " + roleIdsByName.keySet());
            }
            jdbc.update("INSERT INTO incentive_audience_rules " +
                            "(id, incentive_id, rule_type, rule_value, created_at, updated_at) " +
                            "VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(), incentiveId, "ROLE", roleId.toString(), now, now);
        }
    }

    private final Map<UUID, Map<String, UUID>> roleLookupCache = new HashMap<>();

    // Package-private for test stubbing (see JourneyStageRegionIntegrityTest, which
    // uses a Mockito spy to bypass the DB read while still exercising the rest of
    // the seed path).
    Map<String, UUID> loadRoleIdsByName(UUID clientId) {
        Map<String, UUID> cached = roleLookupCache.get(clientId);
        if (cached != null) return cached;
        Map<String, UUID> result = new HashMap<>();
        jdbc.query("SELECT id, name FROM client_roles WHERE client_id = ?",
            rs -> {
                result.put(rs.getString("name"),
                    (UUID) rs.getObject("id"));
            }, clientId);
        roleLookupCache.put(clientId, result);
        return result;
    }

    // ── Payout Plan Generation ─────────────────────────────────────────────────

    /**
     * Generates a multi-tier payout plan with 2-4 bands and realistic values.
     *
     * @param payoutType "PERCENTAGE" or "FLAT"
     * @param currency   "cash" or "points"
     * @param random     seeded Random for deterministic output
     * @return the generated payout plan
     */
    public PayoutPlan generatePayoutPlan(String payoutType, String currency, Random random) {
        String against = "TOTAL_BOOKING";
        if ("PERCENTAGE".equals(payoutType)) {
            against = random.nextDouble() < 0.70 ? "TOTAL_BOOKING" : "ELIGIBLE_PRODUCTS";
        }

        int numBands = 2 + random.nextInt(3); // 2-4 bands
        List<BandPlan> bands = new ArrayList<>();

        // Pick band thresholds from predefined patterns ($10K-$150K range)
        long[][] patterns = switch (numBands) {
            case 2 -> BAND_PATTERNS_2;
            case 3 -> BAND_PATTERNS_3;
            default -> BAND_PATTERNS_4;
        };
        long[] chosen = patterns[random.nextInt(patterns.length)];

        BigDecimal prevMax = BigDecimal.ZERO;
        for (int b = 0; b < numBands; b++) {
            BigDecimal minAmount = prevMax;
            BigDecimal maxAmount;
            if (b == numBands - 1) {
                maxAmount = null; // open-ended top band
            } else {
                maxAmount = BigDecimal.valueOf(chosen[b]);
            }

            BigDecimal payoutValue;
            if ("PERCENTAGE".equals(payoutType)) {
                // Pick from NICE_PERCENTAGES, ascending by band
                int startIdx = Math.min(b * 2, NICE_PERCENTAGES.length - 2);
                int endIdx = Math.min(startIdx + 3, NICE_PERCENTAGES.length);
                payoutValue = BigDecimal.valueOf(
                        NICE_PERCENTAGES[startIdx + random.nextInt(endIdx - startIdx)]);
            } else { // FLAT
                if ("cash".equals(currency)) {
                    // Pick from NICE_CASH_FLAT, ascending by band
                    int startIdx = Math.min(b * 2, NICE_CASH_FLAT.length - 2);
                    int endIdx = Math.min(startIdx + 3, NICE_CASH_FLAT.length);
                    payoutValue = BigDecimal.valueOf(
                            NICE_CASH_FLAT[startIdx + random.nextInt(endIdx - startIdx)]);
                } else { // points (dollar-equivalent)
                    int startIdx = Math.min(b * 2, NICE_POINTS_FLAT.length - 2);
                    int endIdx = Math.min(startIdx + 3, NICE_POINTS_FLAT.length);
                    payoutValue = BigDecimal.valueOf(
                            NICE_POINTS_FLAT[startIdx + random.nextInt(endIdx - startIdx)]);
                }
            }

            bands.add(new BandPlan(minAmount, maxAmount, payoutValue));
            if (maxAmount != null) prevMax = maxAmount;
        }

        BigDecimal maxPerDeal = "cash".equals(currency)
                ? BigDecimal.valueOf(pickOne(NICE_MAX_PER_DEAL, random)) : null;

        return new PayoutPlan(payoutType, against, maxPerDeal, bands);
    }

    // ── Budget Splitting ───────────────────────────────────────────────────────

    /**
     * Splits a total budget across regions using weighted proportions:
     * AMERICAS=35%, EMEAR=30%, APJ=25%, LATAM=10%.
     * Rounds to nearest $1000 and assigns remainder to the last region.
     *
     * @param totalBudget total budget to split
     * @param regions     list of region names to distribute across
     * @return map of region name to allocated budget amount
     */
    public Map<String, BigDecimal> splitBudgetByRegion(int totalBudget, List<String> regions) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (regions.size() == 1) {
            result.put(regions.get(0), BigDecimal.valueOf(totalBudget));
            return result;
        }
        // Region weights: AMERICAS=35%, EMEAR=30%, APJ=25%, LATAM=10%
        double totalWeight = 0;
        double[] weights = new double[regions.size()];
        for (int i = 0; i < regions.size(); i++) {
            weights[i] = switch (regions.get(i)) {
                case "AMERICAS" -> 0.35;
                case "EMEAR" -> 0.30;
                case "APJ" -> 0.25;
                case "LATAM" -> 0.10;
                default -> 0.25;
            };
            totalWeight += weights[i];
        }
        int assigned = 0;
        for (int i = 0; i < regions.size(); i++) {
            int regionBudget;
            if (i == regions.size() - 1) {
                regionBudget = totalBudget - assigned;
            } else {
                regionBudget = (int) Math.round(
                        totalBudget * weights[i] / totalWeight / 1000.0) * 1000;
            }
            result.put(regions.get(i), BigDecimal.valueOf(regionBudget));
            assigned += regionBudget;
        }
        return result;
    }

    // ── Naming & Random Helpers ────────────────────────────────────────────────

    /**
     * Generates a varied incentive name from region, quarter, and index.
     */
    public String generateIncentiveName(String region, FiscalQuarter fq, int index, Random random) {
        String focus = PRODUCT_FOCUS[random.nextInt(PRODUCT_FOCUS.length)];
        String program = PROGRAM_TYPES[random.nextInt(PROGRAM_TYPES.length)];

        return switch (index % 5) {
            case 0 -> region + " " + focus + " " + program;
            case 1 -> focus + " " + program;
            case 2 -> region + " " + fq.qLabel() + " " + focus + " " + program;
            case 3 -> fq.displayName() + " " + focus + " " + program;
            default -> focus + " " + program + " " + fq.qLabel();
        };
    }

    /**
     * Returns a random value in [min, max] rounded to the nearest step.
     */
    public int roundedRandom(int min, int max, int step, Random random) {
        int minSteps = (min + step - 1) / step;
        int maxSteps = max / step;
        return (minSteps + random.nextInt(maxSteps - minSteps + 1)) * step;
    }

    /** Pick a random element from an int array. */
    public int pickOne(int[] opts, Random random) {
        return opts[random.nextInt(opts.length)];
    }

    /** Pick a random element from a long array. */
    public long pickOne(long[] opts, Random random) {
        return opts[random.nextInt(opts.length)];
    }

    /** Pick a random element from a double array. */
    public double pickOne(double[] opts, Random random) {
        return opts[random.nextInt(opts.length)];
    }

    // ── Reward Payout Derivation ───────────────────────────────────────────────

    /**
     * Returns the representative "up to" per-deal payout amount for a sales
     * {@link PayoutPlan}, used to render the "Earn up to ..." banner.
     * <p>FLAT payouts: the max band value, capped by {@code maxPerDeal} when set.
     * PERCENTAGE payouts: {@code maxPerDeal} when set, otherwise approximated as
     * {@code topBand.payoutValue * topBand.minAmount / 100} (the top-band rate
     * applied at its threshold) so points-percentage plans with no cap still
     * report a sensible dollar-equivalent figure.
     */
    static long maxSalesPayoutAmount(PayoutPlan plan) {
        if ("PERCENTAGE".equals(plan.payoutType())) {
            if (plan.maxPerDeal() != null) {
                return plan.maxPerDeal().longValue();
            }
            BandPlan top = plan.bands().get(plan.bands().size() - 1);
            BigDecimal topMin = top.minAmount() != null && top.minAmount().signum() > 0
                    ? top.minAmount() : BigDecimal.valueOf(100_000);
            return top.payoutValue().multiply(topMin).longValue() / 100L;
        }
        long bandMax = plan.bands().stream()
                .map(BandPlan::payoutValue)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                .longValue();
        if (plan.maxPerDeal() != null) {
            return Math.min(bandMax, plan.maxPerDeal().longValue());
        }
        return bandMax;
    }

    // ── Reward Message Formatting ──────────────────────────────────────────────

    /**
     * Formats an "Earn up to ..." reward banner from currency→max-amount pairs.
     * <p>Examples: {@code "Earn up to $1,000 + 500 pts"}, {@code "Earn up to 5 credits"},
     * {@code "Earn up to 5 credits + 3 tickets"}, {@code "Earn up to $500"}.
     * Iteration order of the supplied map is preserved, so callers control currency
     * order (conventionally cash, points, credits, tickets to match reward_currencies JSON).
     */
    static String formatRewardMessage(LinkedHashMap<String, Long> amountsByCurrency) {
        List<String> parts = new ArrayList<>(amountsByCurrency.size());
        for (Map.Entry<String, Long> e : amountsByCurrency.entrySet()) {
            String currency = e.getKey();
            long amount = e.getValue();
            parts.add(switch (currency) {
                case "cash" -> String.format("$%,d", amount);
                case "points" -> String.format("%,d pts", amount);
                case "credits" -> String.format("%,d credits", amount);
                case "tickets" -> String.format("%,d tickets", amount);
                default -> String.format("%,d %s", amount, currency);
            });
        }
        return "Earn up to " + String.join(" + ", parts);
    }

}
