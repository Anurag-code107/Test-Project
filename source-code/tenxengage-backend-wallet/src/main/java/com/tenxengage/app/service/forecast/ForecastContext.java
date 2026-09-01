package com.tenxengage.app.service.forecast;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * All pre-computed data assembled for a single forecast request.
 * This is serialized to JSON and sent to Claude as the user message.
 */
public record ForecastContext(
    NewIncentiveSummary newIncentive,
    List<SimilarIncentive> similarPastIncentives,
    Map<String, RegionSalesBaseline> baselineSalesByRegion,
    Map<String, ProductCategoryBaseline> baselineSalesByProductCategory,
    Map<String, Map<String, ProductRegionBaseline>> productRegionBaselines,
    Map<String, RegionDistribution> regionalDistribution,
    Map<String, TrainingCorrelation> trainingCorrelation,
    Map<String, SeasonalContext> seasonalData,
    ForecastAccuracySummary forecastAccuracy,
    int totalHistoricalIncentives,
    int totalHistoricalPurchaseOrders
) {

    public record NewIncentiveSummary(
        String type,
        String name,
        List<String> regions,
        Map<String, List<String>> targetLocations,
        List<String> productCategories,
        BudgetSummary budget,
        DurationSummary duration,
        PayoutSummary payoutStructure,
        AudienceSummary audienceSize,
        BigDecimal maxPerPartner,
        BigDecimal maxPerUser,
        List<String> rewardCurrencies,
        BigDecimal payoutAsPercentOfDealSize,
        String fiscalQuarter,
        boolean isNearQuarterEnd
    ) {}

    public record BudgetSummary(
        BigDecimal totalBudget,
        String currency,
        String mode,
        Map<String, BigDecimal> regionBudgets
    ) {}

    public record DurationSummary(
        String startDate,
        String endDate,
        int days
    ) {}

    public record PayoutSummary(
        String type,
        String against,
        int bandCount,
        List<BandSummary> bands
    ) {}

    public record BandSummary(
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal payoutValue
    ) {}

    public record AudienceSummary(
        int eligiblePartners,
        int eligibleUsers
    ) {}

    public record SimilarIncentive(
        String id,
        String name,
        double similarityScore,
        String type,
        BigDecimal budget,
        int durationDays,
        BigDecimal actualUtilizationPct,
        int actualParticipation,
        BigDecimal actualParticipationRate,
        BigDecimal actualRevenue,
        BigDecimal actualCost,
        BigDecimal actualRoi,
        BigDecimal actualLiftPct,
        BigDecimal claimRate,
        Integer avgDaysToClaim,
        BigDecimal budgetExhaustionPctAtMidpoint,
        List<String> productCategories,
        List<String> regions,
        String endDate
    ) {}

    public record RegionSalesBaseline(
        String locationValueId,
        String level,
        int depth,
        String parentId,
        String parentName,
        int avgMonthlyDeals,
        BigDecimal avgMonthlyRevenue,
        BigDecimal avgDealSize
    ) {}

    public record ProductCategoryBaseline(
        int avgMonthlyDeals,
        BigDecimal avgMonthlyRevenue
    ) {}

    public record RegionDistribution(
        String locationValueId,
        String level,
        int depth,
        String parentId,
        String parentName,
        int activePartners,
        BigDecimal trailing12mRevenue,
        BigDecimal revenueWeight
    ) {}

    public record TrainingCorrelation(
        BigDecimal trainedPartnerLiftPct,
        BigDecimal penetrationRate,
        int sampleSize
    ) {}

    public record ProductRegionBaseline(
        int avgMonthlyDeals,
        BigDecimal avgMonthlyRevenue
    ) {}

    public record SeasonalContext(
        Map<String, BigDecimal> monthlyIndices,
        BigDecimal yoyGrowthRate
    ) {}

    public record ForecastAccuracySummary(
        BigDecimal avgBookingsErrorPct,
        BigDecimal avgOverallAccuracy,
        int sampleSize
    ) {}
}
