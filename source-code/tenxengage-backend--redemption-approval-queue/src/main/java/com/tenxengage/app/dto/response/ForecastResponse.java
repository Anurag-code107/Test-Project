package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.IncentiveForecast;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ForecastResponse(
    UUID id,
    String incentiveId,
    String estimatedRoi,
    int estimatedParticipation,
    String estimatedParticipationRate,
    String estimatedTotalCost,
    String estimatedRevenue,
    int estimatedNetNewDeals,
    String estimatedNetNewBookings,
    String confidenceScore,
    String dataQualityScore,
    String modelVersion,
    List<LocationBreakdown> locationBreakdown,
    List<Map<String, Object>> monthlyProjections,
    List<String> similarIncentiveIds,
    List<AiInsight> insights,
    Map<String, List<AiInsight>> topLevelInsights,
    String reasoning,
    String generatedAt
) {

    public record LocationBreakdown(
        String locationValueId,
        String name,
        String parentId,
        String budgetUtilizedPct,
        int netNewDeals,
        String netNewBookings,
        String roi,
        String participationRate,
        String budgetAllocated,
        String budgetPredictedSpend
    ) {}

    public record AiInsight(
        String type,
        String title,
        String detail,
        int confidence
    ) {}

    public static ForecastResponse from(IncentiveForecast forecast) {
        if (forecast == null) return null;

        List<LocationBreakdown> locations = List.of();
        if (forecast.getLocationBreakdown() != null) {
            locations = forecast.getLocationBreakdown().stream()
                    .map(m -> new LocationBreakdown(
                            strOrEmpty(m, "locationValueId"),
                            strOrEmpty(m, "name"),
                            strOrEmpty(m, "parentId"),
                            strOrEmpty(m, "budgetUtilizedPct"),
                            intOrZero(m, "netNewDeals"),
                            strOrEmpty(m, "netNewBookings"),
                            strOrEmpty(m, "roi"),
                            strOrEmpty(m, "participationRate"),
                            strOrEmpty(m, "budgetAllocated"),
                            strOrEmpty(m, "budgetPredictedSpend")))
                    .toList();
        }

        List<AiInsight> insights = List.of();
        if (forecast.getAiInsights() != null && !forecast.getAiInsights().isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                insights = mapper.readValue(forecast.getAiInsights(),
                        new TypeReference<List<AiInsight>>() {});
            } catch (Exception e) {
                // If parsing fails, return empty insights
            }
        }

        // top_level_insights is jsonb: Map<TopLevelLocationName, List<AiInsight raw maps>>
        Map<String, List<AiInsight>> topLevelInsights = Map.of();
        if (forecast.getTopLevelInsights() != null && !forecast.getTopLevelInsights().isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                topLevelInsights = mapper.convertValue(forecast.getTopLevelInsights(),
                        new TypeReference<Map<String, List<AiInsight>>>() {});
            } catch (Exception e) {
                topLevelInsights = Map.of();
            }
        }

        // Extract reasoning from the first insight or ai_insights
        String reasoning = null;
        if (forecast.getAiInsights() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                var parsed = mapper.readTree(forecast.getAiInsights());
                // reasoning is stored separately in the insights JSON if present
            } catch (Exception ignored) {}
        }

        return new ForecastResponse(
            forecast.getId(),
            forecast.getIncentiveId().toString(),
            forecast.getEstimatedRoi().toPlainString(),
            forecast.getEstimatedParticipation(),
            forecast.getEstimatedParticipationRate().toPlainString(),
            forecast.getEstimatedTotalCost().toPlainString(),
            forecast.getEstimatedRevenue().toPlainString(),
            forecast.getEstimatedNetNewDeals() != null ? forecast.getEstimatedNetNewDeals() : 0,
            forecast.getEstimatedNetNewBookings() != null ? forecast.getEstimatedNetNewBookings().toPlainString() : "0",
            forecast.getConfidenceScore().toPlainString(),
            forecast.getDataQualityScore() != null ? forecast.getDataQualityScore().toPlainString() : "0",
            forecast.getModelVersion(),
            locations,
            forecast.getMonthlyProjections(),
            forecast.getSimilarIncentiveIds() != null ? forecast.getSimilarIncentiveIds() : List.of(),
            insights,
            topLevelInsights,
            reasoning,
            forecast.getGeneratedAt().toString()
        );
    }

    private static String strOrEmpty(Map<String, Object> m, String key) {
        Object val = m.get(key);
        return val != null ? val.toString() : "";
    }

    private static int intOrZero(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
