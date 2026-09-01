package com.tenxengage.app.service.forecast;

import com.tenxengage.app.entity.EligibilityRule;
import com.tenxengage.app.entity.EligibilityRuleGroup;
import com.tenxengage.app.entity.ForecastIncentiveOutcome;
import com.tenxengage.app.entity.ForecastRegionDistribution;
import com.tenxengage.app.entity.ForecastSalesAggregate;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PayoutBand;
import com.tenxengage.app.entity.PayoutConfig;
import com.tenxengage.app.entity.SalesRequirement;
import com.tenxengage.app.entity.ForecastTrainingCorrelation;
import com.tenxengage.app.repository.ForecastIncentiveOutcomeRepository;
import com.tenxengage.app.repository.ForecastRegionDistributionRepository;
import com.tenxengage.app.repository.ForecastSalesAggregateRepository;
import com.tenxengage.app.repository.ForecastTrainingCorrelationRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ForecastDataAssembler {

    private static final Logger log = LoggerFactory.getLogger(ForecastDataAssembler.class);
    private static final int MAX_SIMILAR_INCENTIVES = 5;

    private final ForecastSalesAggregateRepository salesAggregateRepo;
    private final ForecastIncentiveOutcomeRepository incentiveOutcomeRepo;
    private final ForecastRegionDistributionRepository regionDistributionRepo;
    private final ForecastTrainingCorrelationRepository trainingCorrelationRepo;
    private final LocationValueRepository locationValueRepo;
    private final SimilarityScorer similarityScorer;
    private final ForecastAccuracyService accuracyService;
    private final JdbcTemplate jdbcTemplate;

    public ForecastDataAssembler(ForecastSalesAggregateRepository salesAggregateRepo,
                                  ForecastIncentiveOutcomeRepository incentiveOutcomeRepo,
                                  ForecastRegionDistributionRepository regionDistributionRepo,
                                  ForecastTrainingCorrelationRepository trainingCorrelationRepo,
                                  LocationValueRepository locationValueRepo,
                                  SimilarityScorer similarityScorer,
                                  ForecastAccuracyService accuracyService,
                                  JdbcTemplate jdbcTemplate) {
        this.salesAggregateRepo = salesAggregateRepo;
        this.incentiveOutcomeRepo = incentiveOutcomeRepo;
        this.regionDistributionRepo = regionDistributionRepo;
        this.trainingCorrelationRepo = trainingCorrelationRepo;
        this.locationValueRepo = locationValueRepo;
        this.similarityScorer = similarityScorer;
        this.accuracyService = accuracyService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ForecastContext assemble(Incentive incentive) {
        UUID clientId = incentive.getClientId();
        IncentiveFingerprint candidateFingerprint = extractFingerprint(incentive);
        // Resolve the LocationValue IDs the forecast should target — at whatever
        // depth the user actually picked (Region / Country / State / ...). With
        // forecast_sales_aggregates and forecast_region_distributions now keyed
        // by location_value_id at every ancestor depth (see
        // ForecastAggregationService write-time rollup), the downstream queries
        // can scope directly to the selected nodes instead of collapsing to
        // their depth-0 ancestor.
        List<UUID> locationValueIds = resolveTargetLocationIds(incentive);
        List<String> targetRegions = locationValueIds.isEmpty()
                ? List.of()
                : locationValueRepo.findByIdIn(locationValueIds).stream()
                        .map(LocationValue::getName)
                        .distinct()
                        .toList();
        if (targetRegions.isEmpty()) {
            // Fallback: legacy incentives whose rules don't resolve to any
            // depth-0 LocationValue (e.g. orphaned REGION rules whose names
            // don't match the current location_values table).
            targetRegions = extractLocationValueNames(incentive);
        }
        List<String> targetProductCategories = extractProductCategories(incentive);

        // 1. Find similar past incentives
        List<ForecastContext.SimilarIncentive> similarIncentives =
                findSimilarIncentives(clientId, candidateFingerprint);

        // 2. Build baseline sales data by region
        Map<String, ForecastContext.RegionSalesBaseline> baselineByRegion =
                buildRegionBaselines(clientId, targetRegions);

        // 3. Build baseline sales by product category
        Map<String, ForecastContext.ProductCategoryBaseline> baselineByProduct =
                buildProductBaselines(clientId, targetProductCategories);

        // 4. Regional distribution
        Map<String, ForecastContext.RegionDistribution> regionDistribution =
                buildRegionDistribution(clientId, targetRegions);

        // 5. Training correlation
        Map<String, ForecastContext.TrainingCorrelation> trainingCorrelation =
                buildTrainingCorrelation(clientId, targetRegions, targetProductCategories);

        // 6. Product x Region cross-baselines
        Map<String, Map<String, ForecastContext.ProductRegionBaseline>> productRegionBaselines =
                buildProductRegionBaselines(clientId, targetRegions, targetProductCategories);

        // 7. Seasonal context with YoY growth
        Map<String, ForecastContext.SeasonalContext> seasonalData =
                buildSeasonalContext(clientId, targetRegions, targetProductCategories);

        // 8. Forecast accuracy feedback
        ForecastAccuracyService.ForecastAccuracySummary accuracySummary =
                accuracyService.getAccuracySummary(clientId);
        ForecastContext.ForecastAccuracySummary contextAccuracy = accuracySummary != null
                ? new ForecastContext.ForecastAccuracySummary(
                    accuracySummary.avgBookingsErrorPct(),
                    accuracySummary.avgOverallAccuracy(),
                    accuracySummary.sampleSize())
                : null;

        // 9. Totals for data quality assessment
        int totalIncentives = incentiveOutcomeRepo.findByClientId(clientId).size();
        int totalPOs = countPurchaseOrders(clientId);

        return new ForecastContext(
                buildNewIncentiveSummary(incentive, candidateFingerprint, baselineByRegion),
                similarIncentives,
                baselineByRegion,
                baselineByProduct,
                productRegionBaselines,
                regionDistribution,
                trainingCorrelation,
                seasonalData,
                contextAccuracy,
                totalIncentives,
                totalPOs
        );
    }

    // ── Fingerprint Extraction ─────────────────────────────────────────────────

    public IncentiveFingerprint extractFingerprint(Incentive incentive) {
        // Use UUID strings for the location dimension so similarity scoring
        // matches against forecast_incentive_outcomes.target_location_value_ids,
        // which now stores UUIDs at whatever depth the past incentive targeted.
        // Both saved and preview rule shapes are normalized through
        // resolveTargetLocationIds so the fingerprint stays consistent.
        Set<String> regions = resolveTargetLocationIds(incentive).stream()
                .map(UUID::toString)
                .collect(Collectors.toSet());
        Set<String> productCategories = extractProductCategories(incentive).stream().collect(Collectors.toSet());
        Set<String> partnerTypes = extractPartnerTypes(incentive);

        BigDecimal totalBudget = BigDecimal.ZERO;
        if (incentive.getBudgets() != null) {
            totalBudget = incentive.getBudgets().stream()
                    .map(IncentiveBudget::getTotalBudget)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        int durationDays = 90; // default
        if (incentive.getStartDate() != null && incentive.getEndDate() != null) {
            durationDays = (int) ChronoUnit.DAYS.between(
                    incentive.getStartDate().atZone(ZoneOffset.UTC).toLocalDate(),
                    incentive.getEndDate().atZone(ZoneOffset.UTC).toLocalDate());
        }

        String payoutType = null;
        int bandCount = 0;
        if (incentive.getSalesRequirements() != null) {
            for (SalesRequirement req : incentive.getSalesRequirements()) {
                if (req.getPayouts() != null) {
                    for (PayoutConfig pc : req.getPayouts()) {
                        if (payoutType == null && pc.getPayoutType() != null) {
                            payoutType = pc.getPayoutType().name();
                        }
                        if (pc.getBands() != null) {
                            bandCount = Math.max(bandCount, pc.getBands().size());
                        }
                    }
                }
            }
        }

        return new IncentiveFingerprint(
                incentive.getIncentiveType() != null ? incentive.getIncentiveType().name() : null,
                regions, productCategories, totalBudget,
                durationDays, payoutType, bandCount, partnerTypes
        );
    }

    // ── Similar Incentives ─────────────────────────────────────────────────────

    private List<ForecastContext.SimilarIncentive> findSimilarIncentives(
            UUID clientId, IncentiveFingerprint candidate) {

        List<ForecastIncentiveOutcome> outcomes = incentiveOutcomeRepo.findByClientId(clientId);
        if (outcomes.isEmpty()) {
            return List.of();
        }

        record Scored(ForecastIncentiveOutcome outcome, double score) {}

        List<Scored> scored = new ArrayList<>();
        for (ForecastIncentiveOutcome outcome : outcomes) {
            IncentiveFingerprint historical = outcomeToFingerprint(outcome);
            double score = similarityScorer.score(candidate, historical);
            if (score > 0.1) {
                scored.add(new Scored(outcome, score));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(MAX_SIMILAR_INCENTIVES)
                .map(s -> toSimilarIncentive(s.outcome, s.score))
                .toList();
    }

    private IncentiveFingerprint outcomeToFingerprint(ForecastIncentiveOutcome outcome) {
        Set<String> regions = csvToSet(outcome.getTargetLocationValueIds());
        Set<String> products = csvToSet(outcome.getProductCategories());
        Set<String> partnerTypes = csvToSet(outcome.getPartnerTypes());
        int durationDays = outcome.getDurationDays() != null ? outcome.getDurationDays() : 90;

        return new IncentiveFingerprint(
                outcome.getIncentiveType(), regions, products,
                outcome.getTotalBudget() != null ? outcome.getTotalBudget() : BigDecimal.ZERO,
                durationDays, outcome.getPayoutType(), 0, partnerTypes
        );
    }

    private ForecastContext.SimilarIncentive toSimilarIncentive(ForecastIncentiveOutcome o, double score) {
        return new ForecastContext.SimilarIncentive(
                o.getIncentiveId().toString(),
                o.getName(),
                score,
                o.getIncentiveType(),
                o.getTotalBudget(),
                o.getDurationDays() != null ? o.getDurationDays() : 0,
                o.getActualUtilizationRate(),
                o.getActualParticipationCount() != null ? o.getActualParticipationCount() : 0,
                o.getActualParticipationRate(),
                o.getActualRevenue(),
                o.getActualCost(),
                o.getActualRoi(),
                o.getActualLiftPct(),
                o.getClaimRate(),
                o.getAvgDaysToClaim(),
                o.getBudgetExhaustionPctAtMidpoint(),
                csvToList(o.getProductCategories()),
                csvToList(o.getTargetLocationValueIds()),
                o.getEndDate() != null ? o.getEndDate().toString() : null
        );
    }

    // ── Baselines ──────────────────────────────────────────────────────────────

    private Map<String, ForecastContext.RegionSalesBaseline> buildRegionBaselines(
            UUID clientId, List<String> regions) {
        Map<String, ForecastContext.RegionSalesBaseline> result = new LinkedHashMap<>();
        if (regions.isEmpty()) return result;

        List<UUID> locationValueIds = resolveLocationValueIds(clientId, regions);
        List<ForecastSalesAggregate> aggregates =
                salesAggregateRepo.findByClientIdAndLocationValueIdIn(clientId, locationValueIds);

        Map<UUID, LocationNodeInfo> nodeInfo = buildLocationNodeInfo(clientId, locationValueIds);
        Map<UUID, List<ForecastSalesAggregate>> byLocation = aggregates.stream()
                .filter(a -> a.getLocationValueId() != null)
                .collect(Collectors.groupingBy(ForecastSalesAggregate::getLocationValueId));

        for (var entry : byLocation.entrySet()) {
            UUID locationId = entry.getKey();
            LocationNodeInfo info = nodeInfo.get(locationId);
            if (info == null) continue;

            List<ForecastSalesAggregate> aggs = entry.getValue();
            int months = (int) aggs.stream().map(ForecastSalesAggregate::getYearMonth).distinct().count();
            if (months == 0) months = 1;

            int totalDeals = aggs.stream().mapToInt(ForecastSalesAggregate::getDealCount).sum();
            BigDecimal totalRevenue = aggs.stream()
                    .map(ForecastSalesAggregate::getTotalRevenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int avgDeals = totalDeals / months;
            BigDecimal avgRevenue = totalRevenue.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
            BigDecimal avgDealSize = avgDeals > 0
                    ? avgRevenue.divide(BigDecimal.valueOf(avgDeals), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.put(info.name(), new ForecastContext.RegionSalesBaseline(
                    info.locationValueId().toString(),
                    info.level(),
                    info.depth(),
                    info.parentId() != null ? info.parentId().toString() : null,
                    info.parentName(),
                    avgDeals, avgRevenue, avgDealSize));
        }
        return result;
    }

    private Map<String, ForecastContext.ProductCategoryBaseline> buildProductBaselines(
            UUID clientId, List<String> productCategories) {
        Map<String, ForecastContext.ProductCategoryBaseline> result = new LinkedHashMap<>();
        if (productCategories.isEmpty()) return result;

        List<ForecastSalesAggregate> aggregates =
                salesAggregateRepo.findByClientIdAndProductCategoryIn(clientId, productCategories);

        Map<String, List<ForecastSalesAggregate>> byCategory = aggregates.stream()
                .filter(a -> a.getProductCategory() != null)
                .collect(Collectors.groupingBy(ForecastSalesAggregate::getProductCategory));

        for (var entry : byCategory.entrySet()) {
            List<ForecastSalesAggregate> catAggs = entry.getValue();
            int months = (int) catAggs.stream().map(ForecastSalesAggregate::getYearMonth).distinct().count();
            if (months == 0) months = 1;

            int totalDeals = catAggs.stream().mapToInt(ForecastSalesAggregate::getDealCount).sum();
            BigDecimal totalRevenue = catAggs.stream()
                    .map(ForecastSalesAggregate::getTotalRevenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.put(entry.getKey(), new ForecastContext.ProductCategoryBaseline(
                    totalDeals / months,
                    totalRevenue.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP)));
        }
        return result;
    }

    // ── Region Distribution ────────────────────────────────────────────────────

    private Map<String, ForecastContext.RegionDistribution> buildRegionDistribution(
            UUID clientId, List<String> regions) {
        Map<String, ForecastContext.RegionDistribution> result = new LinkedHashMap<>();
        List<UUID> locationValueIds = resolveLocationValueIds(clientId, regions);
        List<ForecastRegionDistribution> distributions = locationValueIds.isEmpty()
                ? regionDistributionRepo.findByClientId(clientId)
                : regionDistributionRepo.findByClientIdAndLocationValueIdIn(clientId, locationValueIds);

        List<UUID> distLocationIds = distributions.stream()
                .map(ForecastRegionDistribution::getLocationValueId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, LocationNodeInfo> nodeInfo = buildLocationNodeInfo(clientId, distLocationIds);

        for (ForecastRegionDistribution dist : distributions) {
            LocationNodeInfo info = nodeInfo.get(dist.getLocationValueId());
            if (info == null) continue;
            result.put(info.name(), new ForecastContext.RegionDistribution(
                    info.locationValueId().toString(),
                    info.level(),
                    info.depth(),
                    info.parentId() != null ? info.parentId().toString() : null,
                    info.parentName(),
                    dist.getActivePartnerCount(),
                    dist.getTrailing12mRevenue(),
                    dist.getRevenueWeight()));
        }
        return result;
    }

    // ── Training Correlation ───────────────────────────────────────────────────

    private Map<String, ForecastContext.TrainingCorrelation> buildTrainingCorrelation(
            UUID clientId, List<String> regions, List<String> productCategories) {
        Map<String, ForecastContext.TrainingCorrelation> result = new LinkedHashMap<>();
        if (productCategories.isEmpty()) return result;

        // Use pre-aggregated training correlation data (data-driven lift)
        List<ForecastTrainingCorrelation> correlations =
                trainingCorrelationRepo.findByClientIdAndProductCategoryIn(clientId, productCategories);

        if (!correlations.isEmpty()) {
            for (ForecastTrainingCorrelation tc : correlations) {
                int totalSellers = (tc.getTrainedSellerCount() != null ? tc.getTrainedSellerCount() : 0)
                        + (tc.getUntrainedSellerCount() != null ? tc.getUntrainedSellerCount() : 0);
                BigDecimal penetration = totalSellers > 0
                        ? BigDecimal.valueOf(tc.getTrainedSellerCount())
                            .divide(BigDecimal.valueOf(totalSellers), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                BigDecimal liftPct = tc.getDataDrivenLiftPct() != null
                        ? tc.getDataDrivenLiftPct() : new BigDecimal("15.0");

                result.put(tc.getProductCategory(), new ForecastContext.TrainingCorrelation(
                        liftPct, penetration,
                        tc.getSampleSize() != null ? tc.getSampleSize() : 0));
            }
            return result;
        }

        // Fallback: use the penetration rate query with conservative 15% estimate
        String sql = """
            SELECT
                cpm.product_category,
                COUNT(DISTINCT ucc.user_id) AS trained_users,
                COUNT(DISTINCT u.id) AS total_users
            FROM course_product_mappings cpm
            JOIN user_course_completions ucc ON ucc.course_id = cpm.course_id
            JOIN users u ON u.id = ucc.user_id
            JOIN partner_companies pc ON pc.id = u.partner_company_id
            WHERE ucc.client_id = ?
              AND cpm.product_category = ANY(?)
              AND cpm.relevance_score >= 0.3
            GROUP BY cpm.product_category
            """;

        try {
            String[] categories = productCategories.toArray(new String[0]);
            jdbcTemplate.query(sql, rs -> {
                String category = rs.getString("product_category");
                int trainedUsers = rs.getInt("trained_users");
                int totalUsers = rs.getInt("total_users");
                BigDecimal penetration = totalUsers > 0
                        ? BigDecimal.valueOf(trainedUsers).divide(BigDecimal.valueOf(totalUsers), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                result.put(category, new ForecastContext.TrainingCorrelation(
                        new BigDecimal("15.0"), penetration, trainedUsers));
            }, clientId, categories);
        } catch (Exception e) {
            log.warn("Failed to compute training correlation for client {}: {}", clientId, e.getMessage());
        }
        return result;
    }

    // ── Seasonal Context (Indices + YoY Growth) ─────────────────────────────

    private Map<String, ForecastContext.SeasonalContext> buildSeasonalContext(
            UUID clientId, List<String> regions, List<String> productCategories) {
        Map<String, ForecastContext.SeasonalContext> result = new LinkedHashMap<>();
        if (regions.isEmpty()) return result;

        List<UUID> locationValueIds = resolveLocationValueIds(clientId, regions);
        List<ForecastSalesAggregate> aggregates =
                salesAggregateRepo.findByClientIdAndLocationValueIdIn(clientId, locationValueIds);

        Map<UUID, String> idToName = buildLocationIdToNameMap(clientId, locationValueIds);
        Map<String, List<ForecastSalesAggregate>> byRegion = aggregates.stream()
                .filter(a -> a.getLocationValueId() != null)
                .collect(Collectors.groupingBy(a -> idToName.getOrDefault(a.getLocationValueId(), "Unknown")));

        for (var entry : byRegion.entrySet()) {
            List<ForecastSalesAggregate> regionAggs = entry.getValue();

            // Monthly seasonal indices
            Map<Integer, BigDecimal> monthTotals = new HashMap<>();
            Map<Integer, Integer> monthCounts = new HashMap<>();

            // YoY growth: group by year
            Map<Integer, BigDecimal> yearTotals = new HashMap<>();

            for (ForecastSalesAggregate agg : regionAggs) {
                int month = agg.getYearMonth().getMonthValue();
                int year = agg.getYearMonth().getYear();
                monthTotals.merge(month, agg.getTotalRevenue(), BigDecimal::add);
                monthCounts.merge(month, 1, Integer::sum);
                yearTotals.merge(year, agg.getTotalRevenue(), BigDecimal::add);
            }

            BigDecimal overallAvg = BigDecimal.ZERO;
            int totalEntries = 0;
            for (var me : monthTotals.entrySet()) {
                int count = monthCounts.getOrDefault(me.getKey(), 1);
                overallAvg = overallAvg.add(me.getValue().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
                totalEntries++;
            }
            if (totalEntries > 0) {
                overallAvg = overallAvg.divide(BigDecimal.valueOf(totalEntries), 2, RoundingMode.HALF_UP);
            }

            Map<String, BigDecimal> monthIndices = new LinkedHashMap<>();
            String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                   "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            for (int m = 1; m <= 12; m++) {
                BigDecimal monthAvg = monthTotals.containsKey(m)
                        ? monthTotals.get(m).divide(
                            BigDecimal.valueOf(monthCounts.getOrDefault(m, 1)), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                BigDecimal index = overallAvg.compareTo(BigDecimal.ZERO) > 0
                        ? monthAvg.divide(overallAvg, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ONE;
                monthIndices.put(monthNames[m - 1], index);
            }

            // Compute YoY growth rate from the two most recent years
            BigDecimal yoyGrowth = BigDecimal.ZERO;
            List<Integer> years = yearTotals.keySet().stream().sorted().toList();
            if (years.size() >= 2) {
                int latestYear = years.get(years.size() - 1);
                int prevYear = years.get(years.size() - 2);
                BigDecimal latest = yearTotals.get(latestYear);
                BigDecimal prev = yearTotals.get(prevYear);
                if (prev.compareTo(BigDecimal.ZERO) > 0) {
                    yoyGrowth = latest.subtract(prev)
                            .divide(prev, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }

            result.put(entry.getKey(), new ForecastContext.SeasonalContext(monthIndices, yoyGrowth));
        }
        return result;
    }

    // ── Product x Region Cross-Baselines ──────────────────────────────────────

    private Map<String, Map<String, ForecastContext.ProductRegionBaseline>> buildProductRegionBaselines(
            UUID clientId, List<String> regions, List<String> productCategories) {
        Map<String, Map<String, ForecastContext.ProductRegionBaseline>> result = new LinkedHashMap<>();
        if (regions.isEmpty() || productCategories.isEmpty()) return result;

        List<UUID> locationValueIds = resolveLocationValueIds(clientId, regions);
        List<ForecastSalesAggregate> aggregates =
                salesAggregateRepo.findByClientIdAndLocationValueIdIn(clientId, locationValueIds);

        // Filter to target product categories only
        Set<String> targetProducts = new HashSet<>(productCategories);
        Map<UUID, String> idToName = buildLocationIdToNameMap(clientId, locationValueIds);

        Map<String, Map<String, List<ForecastSalesAggregate>>> byRegionThenProduct = aggregates.stream()
                .filter(a -> a.getLocationValueId() != null && a.getProductCategory() != null
                        && targetProducts.contains(a.getProductCategory()))
                .collect(Collectors.groupingBy(a -> idToName.getOrDefault(a.getLocationValueId(), "Unknown"),
                        Collectors.groupingBy(ForecastSalesAggregate::getProductCategory)));

        for (var regionEntry : byRegionThenProduct.entrySet()) {
            Map<String, ForecastContext.ProductRegionBaseline> productMap = new LinkedHashMap<>();
            for (var productEntry : regionEntry.getValue().entrySet()) {
                List<ForecastSalesAggregate> aggs = productEntry.getValue();
                int months = (int) aggs.stream().map(ForecastSalesAggregate::getYearMonth).distinct().count();
                if (months == 0) months = 1;

                int totalDeals = aggs.stream().mapToInt(ForecastSalesAggregate::getDealCount).sum();
                BigDecimal totalRevenue = aggs.stream()
                        .map(ForecastSalesAggregate::getTotalRevenue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                productMap.put(productEntry.getKey(), new ForecastContext.ProductRegionBaseline(
                        totalDeals / months,
                        totalRevenue.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP)));
            }
            result.put(regionEntry.getKey(), productMap);
        }
        return result;
    }

    // ── New Incentive Summary ──────────────────────────────────────────────────

    private ForecastContext.NewIncentiveSummary buildNewIncentiveSummary(
            Incentive incentive, IncentiveFingerprint fp,
            Map<String, ForecastContext.RegionSalesBaseline> baselines) {

        ForecastContext.BudgetSummary budgetSummary = null;
        if (incentive.getBudgets() != null && !incentive.getBudgets().isEmpty()) {
            IncentiveBudget primary = incentive.getBudgets().get(0);
            budgetSummary = new ForecastContext.BudgetSummary(
                    fp.totalBudget(),
                    primary.getCurrencyId(),
                    primary.getBudgetMode() != null ? primary.getBudgetMode().name() : "GLOBAL",
                    null // region budgets parsed separately if needed
            );
        }

        ForecastContext.DurationSummary duration = new ForecastContext.DurationSummary(
                incentive.getStartDate() != null
                        ? incentive.getStartDate().atZone(ZoneOffset.UTC).toLocalDate().toString() : null,
                incentive.getEndDate() != null
                        ? incentive.getEndDate().atZone(ZoneOffset.UTC).toLocalDate().toString() : null,
                fp.durationDays()
        );

        ForecastContext.PayoutSummary payoutSummary = buildPayoutSummary(incentive);

        int eligiblePartners = countPartnersByClient(incentive.getClientId());

        List<String> rewardCurrencies = incentive.getRewardCurrencies() != null
                ? csvToList(incentive.getRewardCurrencies()) : List.of();

        // Compute payout as % of avg deal size
        BigDecimal payoutAsPercentOfDealSize = computePayoutAsPercentOfDealSize(payoutSummary, baselines);

        // Fiscal quarter context
        String fiscalQuarter = null;
        boolean isNearQuarterEnd = false;
        if (incentive.getEndDate() != null) {
            LocalDate endDate = incentive.getEndDate().atZone(ZoneOffset.UTC).toLocalDate();
            fiscalQuarter = determineFiscalQuarter(incentive.getClientId(), endDate);
            isNearQuarterEnd = isDaysFromQuarterEnd(incentive.getClientId(), endDate, 14);
        }

        Map<String, List<String>> targetLocations = buildTargetLocationsByLevel(incentive);

        return new ForecastContext.NewIncentiveSummary(
                fp.incentiveType(),
                incentive.getName(),
                new ArrayList<>(fp.regions()),
                targetLocations,
                new ArrayList<>(fp.productCategories()),
                budgetSummary,
                duration,
                payoutSummary,
                new ForecastContext.AudienceSummary(eligiblePartners, eligiblePartners * 4),
                incentive.getMaxPerPartner(),
                incentive.getMaxPerUser(),
                rewardCurrencies,
                payoutAsPercentOfDealSize,
                fiscalQuarter,
                isNearQuarterEnd
        );
    }

    private ForecastContext.PayoutSummary buildPayoutSummary(Incentive incentive) {
        if (incentive.getSalesRequirements() == null || incentive.getSalesRequirements().isEmpty()) {
            return null;
        }

        for (SalesRequirement req : incentive.getSalesRequirements()) {
            if (req.getPayouts() != null) {
                for (PayoutConfig pc : req.getPayouts()) {
                    List<ForecastContext.BandSummary> bands = new ArrayList<>();
                    if (pc.getBands() != null) {
                        for (PayoutBand band : pc.getBands()) {
                            bands.add(new ForecastContext.BandSummary(
                                    band.getMinAmount(), band.getMaxAmount(), band.getPayoutValue()));
                        }
                    }
                    return new ForecastContext.PayoutSummary(
                            pc.getPayoutType() != null ? pc.getPayoutType().name() : null,
                            pc.getAgainst(),
                            bands.size(),
                            bands
                    );
                }
            }
        }
        return null;
    }

    // ── Payout & Fiscal Helpers ──────────────────────────────────────────────

    private BigDecimal computePayoutAsPercentOfDealSize(
            ForecastContext.PayoutSummary payoutSummary,
            Map<String, ForecastContext.RegionSalesBaseline> baselines) {

        if (payoutSummary == null || payoutSummary.bands() == null || payoutSummary.bands().isEmpty()) {
            return null;
        }

        // Compute average payout value across bands
        BigDecimal avgPayout = payoutSummary.bands().stream()
                .map(ForecastContext.BandSummary::payoutValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(payoutSummary.bands().size()), 2, RoundingMode.HALF_UP);

        // If percentage type, we need to compute against avg deal size
        if ("PERCENTAGE".equals(payoutSummary.type())) {
            return avgPayout; // Already a percentage
        }

        // For FLAT payouts, compute payout / avg deal size * 100
        BigDecimal totalDealSize = BigDecimal.ZERO;
        int count = 0;
        for (var entry : baselines.entrySet()) {
            if (entry.getValue().avgDealSize().compareTo(BigDecimal.ZERO) > 0) {
                totalDealSize = totalDealSize.add(entry.getValue().avgDealSize());
                count++;
            }
        }

        if (count == 0) return null;

        BigDecimal avgDealSize = totalDealSize.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        if (avgDealSize.compareTo(BigDecimal.ZERO) == 0) return null;

        return avgPayout.divide(avgDealSize, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String determineFiscalQuarter(UUID clientId, LocalDate date) {
        try {
            String sql = """
                SELECT fyc.fiscal_year || ' ' || fyc.quarter
                FROM fiscal_year_configs fyc
                WHERE fyc.client_id = ?
                  AND fyc.start_date <= ?
                  AND fyc.end_date >= ?
                LIMIT 1
                """;
            return jdbcTemplate.queryForObject(sql, String.class, clientId, date, date);
        } catch (Exception e) {
            // Fallback: compute from calendar quarter
            int q = (date.getMonthValue() - 1) / 3 + 1;
            return "Q" + q + " " + date.getYear();
        }
    }

    private boolean isDaysFromQuarterEnd(UUID clientId, LocalDate date, int withinDays) {
        try {
            String sql = """
                SELECT fyc.end_date
                FROM fiscal_year_configs fyc
                WHERE fyc.client_id = ?
                  AND fyc.start_date <= ?
                  AND fyc.end_date >= ?
                LIMIT 1
                """;
            LocalDate quarterEnd = jdbcTemplate.queryForObject(sql, LocalDate.class, clientId, date, date);
            if (quarterEnd != null) {
                long daysToEnd = ChronoUnit.DAYS.between(date, quarterEnd);
                return daysToEnd >= 0 && daysToEnd <= withinDays;
            }
        } catch (Exception e) {
            // Fallback: calendar quarter end
            int quarterEndMonth = ((date.getMonthValue() - 1) / 3 + 1) * 3;
            LocalDate quarterEnd = LocalDate.of(date.getYear(), quarterEndMonth, 1)
                    .plusMonths(1).minusDays(1);
            long daysToEnd = ChronoUnit.DAYS.between(date, quarterEnd);
            return daysToEnd >= 0 && daysToEnd <= withinDays;
        }
        return false;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Extracts region / location names from audience rules for fingerprint
     * matching and as a fallback for legacy incentives whose rules don't
     * resolve to any depth-0 LocationValue (see `assemble`).
     *
     * Prefers REGION-typed rules (ruleValue is the region name). Falls
     * through to LOCATION-typed rules when a locationLevel is set; those
     * may carry either a LocationValue UUID (saved-incentive shape) or a
     * raw name (preview shape) in ruleValue.
     *
     * Package-private so unit tests can exercise both rule shapes without
     * needing a real Postgres.
     */
    List<String> extractLocationValueNames(Incentive incentive) {
        if (incentive.getAudienceRules() == null) return List.of();

        List<String> regionNamed = incentive.getAudienceRules().stream()
                .filter(r -> "REGION".equals(r.getRuleType())
                        && r.getRuleValue() != null
                        && !r.getRuleValue().isBlank())
                .map(IncentiveAudienceRule::getRuleValue)
                .distinct()
                .toList();
        if (!regionNamed.isEmpty()) return regionNamed;

        return incentive.getAudienceRules().stream()
                .filter(r -> "LOCATION".equals(r.getRuleType())
                        && r.getLocationLevel() != null
                        && r.getRuleValue() != null)
                .map(IncentiveAudienceRule::getRuleValue)
                .distinct()
                .toList();
    }

    /**
     * Resolves the depth-0 LocationValue IDs that should drive the forecast,
     * given an incentive's audience rules.
     *
     * The forecast's downstream queries (region baselines, regional
     * distribution, training correlation, ...) all expect depth-0 region IDs
     * because their underlying aggregate tables are pre-computed at that
     * grain. When a Client Admin narrows their incentive deeper than the
     * region — e.g. picks `Americas → United States → California` in
     * Participant Eligibility — we must NOT silently widen the forecast back
     * out to the full region pool. Instead, every selected LocationValue (at
     * any depth) is resolved to its UUID and walked up parent_id to its
     * depth-0 ancestor; the resulting set is the narrowed region pool the
     * forecast targets.
     *
     * Three rule shapes are accepted:
     *   1. LOCATION rules with UUID `ruleValue` — the saved-incentive shape.
     *   2. LOCATION rules with name `ruleValue` plus `locationLevel` set —
     *      the preview/forecast-preview shape (transient rules built from the
     *      `locationSelections` request field; never persisted).
     *   3. REGION rules with name `ruleValue` — the legacy preview shape,
     *      kept for backward compatibility.
     */
    List<UUID> resolveTargetLocationIds(Incentive incentive) {
        if (incentive.getAudienceRules() == null || incentive.getClientId() == null) return List.of();
        UUID clientId = incentive.getClientId();

        Set<UUID> seedIds = new HashSet<>();

        for (IncentiveAudienceRule rule : incentive.getAudienceRules()) {
            if (!"LOCATION".equals(rule.getRuleType())) continue;
            String value = rule.getRuleValue();
            if (value == null || value.isBlank()) continue;
            try {
                seedIds.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID — handled below as a (level, name) lookup.
            }
        }

        List<NameLevelPair> nameLevelPairs = incentive.getAudienceRules().stream()
                .filter(r -> "LOCATION".equals(r.getRuleType())
                        && r.getRuleValue() != null
                        && !r.getRuleValue().isBlank()
                        && !looksLikeUuid(r.getRuleValue())
                        && r.getLocationLevel() != null
                        && r.getLocationLevel().getId() != null)
                .map(r -> new NameLevelPair(r.getLocationLevel().getId(), r.getRuleValue()))
                .toList();
        if (!nameLevelPairs.isEmpty()) {
            seedIds.addAll(resolveLocationValueIdsByLevelAndName(clientId, nameLevelPairs));
        }

        List<String> regionRuleNames = incentive.getAudienceRules().stream()
                .filter(r -> "REGION".equals(r.getRuleType())
                        && r.getRuleValue() != null
                        && !r.getRuleValue().isBlank())
                .map(IncentiveAudienceRule::getRuleValue)
                .toList();
        if (!regionRuleNames.isEmpty()) {
            seedIds.addAll(resolveLocationValueIds(clientId, regionRuleNames));
        }

        return new ArrayList<>(seedIds);
    }

    private record NameLevelPair(UUID levelId, String name) {}

    private static boolean looksLikeUuid(String s) {
        if (s == null || s.length() != 36) return false;
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Resolves (level, name) pairs to LocationValue UUIDs in a single SQL
     * roundtrip. Match is case-insensitive on `name` and scoped to the given
     * client and the supplied level UUID for each pair.
     */
    private List<UUID> resolveLocationValueIdsByLevelAndName(UUID clientId, List<NameLevelPair> pairs) {
        if (pairs.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder(
                "SELECT lv.id FROM location_values lv "
                        + "WHERE lv.client_id = ? AND (");
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("(lv.level_id = ? AND UPPER(lv.name) = ?)");
            params.add(pairs.get(i).levelId());
            params.add(pairs.get(i).name().toUpperCase());
        }
        sql.append(")");
        return jdbcTemplate.queryForList(sql.toString(), UUID.class, params.toArray());
    }

    /**
     * Builds a level-keyed view of the user's eligibility selections for the
     * AI prompt. Output is `{ "Region": ["Americas"], "Country": ["United
     * States"], "State": ["California"] }` — preserving the depth structure
     * the user actually picked.
     *
     * Downstream forecast queries scope to the selected nodes themselves at
     * any depth (see `resolveTargetLocationIds`); this map is the AI's
     * narrative view of which levels were touched.
     *
     * Bulk-fetches LocationLevel names and (UUID-valued) LocationValue names
     * in two roundtrips at most, so this is cheap to call per assemble().
     */
    Map<String, List<String>> buildTargetLocationsByLevel(Incentive incentive) {
        if (incentive.getAudienceRules() == null || incentive.getClientId() == null) return Map.of();
        UUID clientId = incentive.getClientId();

        Set<UUID> levelIdsToFetch = new HashSet<>();
        Set<UUID> valueIdsToFetch = new HashSet<>();
        for (IncentiveAudienceRule rule : incentive.getAudienceRules()) {
            if (!"LOCATION".equals(rule.getRuleType())) continue;
            if (rule.getLocationLevel() != null
                    && rule.getLocationLevel().getId() != null
                    && rule.getLocationLevel().getName() == null) {
                // Stub LocationLevel from the preview path — name not loaded.
                levelIdsToFetch.add(rule.getLocationLevel().getId());
            }
            if (rule.getRuleValue() != null && looksLikeUuid(rule.getRuleValue())) {
                try {
                    valueIdsToFetch.add(UUID.fromString(rule.getRuleValue()));
                } catch (IllegalArgumentException ignored) { /* unreachable */ }
            }
        }

        Map<UUID, String> levelNamesById = fetchLocationLevelNames(clientId, levelIdsToFetch);
        Map<UUID, String> valueNamesById = valueIdsToFetch.isEmpty()
                ? Map.of()
                : locationValueRepo.findByIdIn(new ArrayList<>(valueIdsToFetch)).stream()
                        .collect(Collectors.toMap(LocationValue::getId, LocationValue::getName, (a, b) -> a));

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (IncentiveAudienceRule rule : incentive.getAudienceRules()) {
            if ("REGION".equals(rule.getRuleType())
                    && rule.getRuleValue() != null
                    && !rule.getRuleValue().isBlank()) {
                result.computeIfAbsent("Region", k -> new ArrayList<>()).add(rule.getRuleValue());
                continue;
            }
            if (!"LOCATION".equals(rule.getRuleType())) continue;
            if (rule.getRuleValue() == null || rule.getRuleValue().isBlank()) continue;

            String levelName = "Location";
            if (rule.getLocationLevel() != null) {
                String pre = rule.getLocationLevel().getName();
                if (pre != null) {
                    levelName = pre;
                } else if (rule.getLocationLevel().getId() != null) {
                    levelName = levelNamesById.getOrDefault(rule.getLocationLevel().getId(), "Location");
                }
            }

            String valueName = rule.getRuleValue();
            if (looksLikeUuid(valueName)) {
                try {
                    UUID id = UUID.fromString(valueName);
                    valueName = valueNamesById.getOrDefault(id, valueName);
                } catch (IllegalArgumentException ignored) { /* unreachable */ }
            }

            result.computeIfAbsent(levelName, k -> new ArrayList<>()).add(valueName);
        }

        // Dedupe within each level while preserving insertion order.
        result.replaceAll((k, v) -> v.stream().distinct().toList());
        return result;
    }

    private Map<UUID, String> fetchLocationLevelNames(UUID clientId, Set<UUID> levelIds) {
        if (levelIds.isEmpty()) return Map.of();
        StringBuilder sql = new StringBuilder(
                "SELECT id, name FROM location_levels WHERE client_id = ? AND id IN (");
        for (int i = 0; i < levelIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.addAll(levelIds);
        List<Map.Entry<UUID, String>> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("name")),
                params.toArray());
        return rows.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
    }

    private List<String> extractProductCategories(Incentive incentive) {
        Set<String> skus = new HashSet<>();
        if (incentive.getSalesRequirements() != null) {
            for (SalesRequirement req : incentive.getSalesRequirements()) {
                if (req.getEligibilityGroups() != null) {
                    for (EligibilityRuleGroup group : req.getEligibilityGroups()) {
                        if (group.getRules() != null) {
                            for (EligibilityRule rule : group.getRules()) {
                                if (rule.getSelectedProducts() != null && !rule.getSelectedProducts().isBlank()) {
                                    for (String sku : rule.getSelectedProducts().split(",")) {
                                        String trimmed = sku.trim();
                                        if (!trimmed.isEmpty()) skus.add(trimmed);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (skus.isEmpty()) return List.of();

        // Resolve SKUs to product categories via DB
        try {
            String sql = "SELECT DISTINCT category FROM products WHERE sku = ANY(?)";
            String[] skuArray = skus.toArray(new String[0]);
            List<String> categories = jdbcTemplate.queryForList(sql, String.class, (Object) skuArray);
            return categories.isEmpty() ? List.of() : categories;
        } catch (Exception e) {
            log.warn("Failed to resolve product categories from SKUs: {}", e.getMessage());
            return List.of();
        }
    }

    private Set<String> extractPartnerTypes(Incentive incentive) {
        if (incentive.getAudienceRules() == null) return Set.of();
        return incentive.getAudienceRules().stream()
                .filter(r -> "PARTNER_TYPE".equals(r.getRuleType()))
                .map(IncentiveAudienceRule::getRuleValue)
                .collect(Collectors.toSet());
    }

    private Set<String> csvToSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private List<String> csvToList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private int countPartnersByClient(UUID clientId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE'",
                Integer.class, clientId);
        return count != null ? count : 0;
    }

    private int countPurchaseOrders(UUID clientId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_orders WHERE client_id = ?",
                Integer.class, clientId);
        return count != null ? count : 0;
    }

    /**
     * Resolves location-name strings to LocationValue UUIDs at any depth in the
     * client's hierarchy. Matches on `name` or `code` (case-insensitive). The
     * forecast aggregates and distributions are now keyed by location_value_id
     * at every ancestor depth, so this no longer needs to scope to depth 0.
     */
    private List<UUID> resolveLocationValueIds(UUID clientId, List<String> locationNames) {
        if (locationNames.isEmpty()) return List.of();
        String sql = "SELECT lv.id FROM location_values lv "
                + "WHERE lv.client_id = ? AND ("
                + "UPPER(lv.name) IN ("
                + locationNames.stream().map(n -> "?").collect(Collectors.joining(","))
                + ") OR UPPER(lv.code) IN ("
                + locationNames.stream().map(n -> "?").collect(Collectors.joining(","))
                + "))";
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        locationNames.forEach(n -> params.add(n.toUpperCase()));
        locationNames.forEach(n -> params.add(n.toUpperCase()));
        return jdbcTemplate.queryForList(sql, UUID.class, params.toArray());
    }

    /**
     * Builds a map from location value ID to location value name.
     */
    private Map<UUID, String> buildLocationIdToNameMap(UUID clientId, List<UUID> locationValueIds) {
        if (locationValueIds.isEmpty()) return Map.of();
        return locationValueRepo.findByIdIn(locationValueIds).stream()
                .collect(Collectors.toMap(LocationValue::getId, LocationValue::getName, (a, b) -> a));
    }

    /**
     * Fetches `(id, name, level_name, depth, parent_id, parent_name)` for a set
     * of LocationValue UUIDs in one roundtrip. Used by the per-baseline builders
     * to populate hierarchy metadata on each context entry — so Claude can echo
     * locationValueId / level / depth / parentId / parentName back in the
     * locationBreakdown output without having to invent them.
     */
    record LocationNodeInfo(UUID locationValueId, String name, String level, int depth,
                            UUID parentId, String parentName) {}

    Map<UUID, LocationNodeInfo> buildLocationNodeInfo(UUID clientId, List<UUID> locationValueIds) {
        if (locationValueIds.isEmpty()) return Map.of();
        String sql = """
                SELECT lv.id,
                       lv.name AS name,
                       ll.name AS level_name,
                       ll.depth AS depth,
                       lv.parent_id AS parent_id,
                       parent.name AS parent_name
                FROM location_values lv
                JOIN location_levels ll ON ll.id = lv.level_id
                LEFT JOIN location_values parent ON parent.id = lv.parent_id
                WHERE lv.client_id = ? AND lv.id = ANY(?)
                """;
        UUID[] idArray = locationValueIds.toArray(new UUID[0]);
        return jdbcTemplate.query(sql, ps -> {
            ps.setObject(1, clientId);
            ps.setArray(2, ps.getConnection().createArrayOf("uuid", idArray));
        }, rs -> {
            Map<UUID, LocationNodeInfo> map = new LinkedHashMap<>();
            while (rs.next()) {
                UUID id = rs.getObject("id", UUID.class);
                map.put(id, new LocationNodeInfo(
                        id,
                        rs.getString("name"),
                        rs.getString("level_name"),
                        rs.getInt("depth"),
                        rs.getObject("parent_id", UUID.class),
                        rs.getString("parent_name")
                ));
            }
            return map;
        });
    }
}
