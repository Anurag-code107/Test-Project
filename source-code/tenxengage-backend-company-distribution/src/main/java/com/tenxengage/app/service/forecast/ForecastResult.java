package com.tenxengage.app.service.forecast;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Parsed result from Claude's forecast response.
 */
public record ForecastResult(
    BigDecimal budgetUtilizationPct,
    int netNewDeals,
    BigDecimal netNewBookings,
    int estimatedParticipation,
    BigDecimal participationRate,
    BigDecimal estimatedTotalCost,
    BigDecimal roi,
    BigDecimal confidenceScore,
    List<LocationBreakdown> locationBreakdown,
    List<MonthlyProjection> monthlyProjections,
    List<Insight> insights,
    Map<String, List<Insight>> topLevelInsights,
    String reasoning,
    String modelVersion,
    BigDecimal dataQualityScore,
    List<String> similarIncentiveIds
) {

    public record LocationBreakdown(
        String locationValueId,
        String name,
        String parentId,
        BigDecimal budgetUtilizedPct,
        int netNewDeals,
        BigDecimal netNewBookings,
        BigDecimal roi,
        BigDecimal participationRate,
        BigDecimal budgetAllocated,
        BigDecimal budgetPredictedSpend
    ) {}

    public record MonthlyProjection(
        String month,
        BigDecimal revenue,
        BigDecimal cost,
        int participants
    ) {}

    public record Insight(
        String type,
        String title,
        String detail,
        int confidence
    ) {}
}
