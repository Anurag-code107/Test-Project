package com.tenxengage.app.service.forecast;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveForecast;
import com.tenxengage.app.repository.IncentiveForecastRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main orchestrator: assembles data context → calls Claude → persists forecast.
 * Streams progress events via SSE.
 */
@Service
public class ForecastEngine {

    private static final Logger log = LoggerFactory.getLogger(ForecastEngine.class);

    private final ForecastDataAssembler dataAssembler;
    private final ForecastAiService aiService;
    private final IncentiveForecastRepository forecastRepository;
    private final ObjectMapper objectMapper;

    public ForecastEngine(ForecastDataAssembler dataAssembler,
                           ForecastAiService aiService,
                           IncentiveForecastRepository forecastRepository,
                           ObjectMapper objectMapper) {
        this.dataAssembler = dataAssembler;
        this.aiService = aiService;
        this.forecastRepository = forecastRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a forecast for an incentive, streaming progress via SSE.
     */
    public ForecastResult generateWithStreaming(Incentive incentive, SseEmitter emitter) {
        try {
            sendStatus(emitter, "Analyzing historical data...");

            ForecastContext context = dataAssembler.assemble(incentive);
            log.info("Assembled forecast context for incentive {}: {} similar incentives, {} total POs",
                    incentive.getId(), context.similarPastIncentives().size(),
                    context.totalHistoricalPurchaseOrders());

            sendStatus(emitter, "Found " + context.similarPastIncentives().size() + " similar programs");

            if (aiService.isAvailable()) {
                sendStatus(emitter, "AI is generating forecast...");
            } else {
                sendStatus(emitter, "Computing statistical forecast...");
            }

            ForecastResult result = aiService.generateForecast(context);

            IncentiveForecast forecast = persistForecast(incentive, result);
            log.info("Forecast generated for incentive {}: ROI={}, confidence={}",
                    incentive.getId(), result.roi(), result.confidenceScore());

            return result;

        } catch (Exception e) {
            log.error("Forecast generation failed for incentive {}: {}", incentive.getId(), e.getMessage(), e);
            sendError(emitter, "Forecast generation failed: " + e.getMessage());
            throw new RuntimeException("Forecast generation failed", e);
        }
    }

    /**
     * Generate a forecast without SSE (for internal use / testing).
     */
    @Transactional
    public ForecastResult generate(Incentive incentive) {
        ForecastContext context = dataAssembler.assemble(incentive);
        ForecastResult result = aiService.generateForecast(context);
        persistForecast(incentive, result);
        return result;
    }

    /**
     * Generate a preview forecast (no persistence) via SSE streaming.
     * Used for creation flows where the incentive hasn't been saved yet.
     */
    public ForecastResult generatePreviewWithStreaming(Incentive transientIncentive, SseEmitter emitter) {
        try {
            sendStatus(emitter, "Analyzing historical data...");

            ForecastContext context = dataAssembler.assemble(transientIncentive);
            log.info("Assembled preview forecast context: {} similar incentives, {} total POs",
                    context.similarPastIncentives().size(), context.totalHistoricalPurchaseOrders());

            sendStatus(emitter, "Found " + context.similarPastIncentives().size() + " similar programs");

            if (aiService.isAvailable()) {
                sendStatus(emitter, "AI is generating forecast...");
            } else {
                sendStatus(emitter, "Computing statistical forecast...");
            }

            ForecastResult result = aiService.generateForecast(context);
            log.info("Preview forecast generated: ROI={}, confidence={}",
                    result.roi(), result.confidenceScore());

            // No persistence — this is a preview
            return result;

        } catch (Exception e) {
            log.error("Preview forecast generation failed: {}", e.getMessage(), e);
            sendError(emitter, "Forecast generation failed: " + e.getMessage());
            throw new RuntimeException("Preview forecast generation failed", e);
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    @Transactional
    public IncentiveForecast persistForecast(Incentive incentive, ForecastResult result) {
        // Build monthly projections as List<Map>
        List<Map<String, Object>> projections = new ArrayList<>();
        for (ForecastResult.MonthlyProjection mp : result.monthlyProjections()) {
            Map<String, Object> m = new HashMap<>();
            m.put("month", mp.month());
            m.put("revenue", mp.revenue().toPlainString());
            m.put("cost", mp.cost().toPlainString());
            m.put("participants", mp.participants());
            projections.add(m);
        }

        // Build per-location breakdown as List<Map>
        List<Map<String, Object>> locationMaps = new ArrayList<>();
        for (ForecastResult.LocationBreakdown lb : result.locationBreakdown()) {
            Map<String, Object> m = new HashMap<>();
            m.put("locationValueId", lb.locationValueId());
            m.put("name", lb.name());
            m.put("parentId", lb.parentId());
            m.put("budgetUtilizedPct", lb.budgetUtilizedPct().toPlainString());
            m.put("netNewDeals", lb.netNewDeals());
            m.put("netNewBookings", lb.netNewBookings().toPlainString());
            m.put("roi", lb.roi().toPlainString());
            m.put("participationRate", lb.participationRate().toPlainString());
            m.put("budgetAllocated", lb.budgetAllocated().toPlainString());
            m.put("budgetPredictedSpend", lb.budgetPredictedSpend().toPlainString());
            locationMaps.add(m);
        }

        // Build ai_insights as JSON string
        String insightsJson = null;
        try {
            insightsJson = objectMapper.writeValueAsString(result.insights());
        } catch (Exception e) {
            log.warn("Failed to serialize insights: {}", e.getMessage());
        }

        // top_level_insights: Map<TopLevelLocationName, List<Insight>> stored as JSONB
        // so the response schema can rehydrate it without a custom converter.
        Map<String, Object> topLevelInsightsMap = new java.util.LinkedHashMap<>();
        if (result.topLevelInsights() != null) {
            for (Map.Entry<String, List<ForecastResult.Insight>> e : result.topLevelInsights().entrySet()) {
                List<Map<String, Object>> insightsForLocation = new ArrayList<>();
                for (ForecastResult.Insight ins : e.getValue()) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("type", ins.type());
                    m.put("title", ins.title());
                    m.put("detail", ins.detail());
                    m.put("confidence", ins.confidence());
                    insightsForLocation.add(m);
                }
                topLevelInsightsMap.put(e.getKey(), insightsForLocation);
            }
        }

        BigDecimal estimatedRevenue = result.netNewBookings() != null ? result.netNewBookings() : BigDecimal.ZERO;
        BigDecimal estimatedCost = result.estimatedTotalCost() != null ? result.estimatedTotalCost() : BigDecimal.ZERO;

        IncentiveForecast forecast = IncentiveForecast.builder()
                .incentiveId(incentive.getId())
                .estimatedRoi(result.roi() != null ? result.roi() : BigDecimal.ZERO)
                .estimatedParticipation(result.estimatedParticipation())
                .estimatedParticipationRate(result.participationRate() != null ? result.participationRate() : BigDecimal.ZERO)
                .estimatedTotalCost(estimatedCost)
                .estimatedRevenue(estimatedRevenue)
                .confidenceScore(result.confidenceScore() != null ? result.confidenceScore() : BigDecimal.ZERO)
                .monthlyProjections(projections)
                .generatedAt(Instant.now())
                .estimatedNetNewDeals(result.netNewDeals())
                .estimatedNetNewBookings(result.netNewBookings())
                .locationBreakdown(locationMaps)
                .similarIncentiveIds(result.similarIncentiveIds())
                .aiInsights(insightsJson)
                .topLevelInsights(topLevelInsightsMap.isEmpty() ? null : topLevelInsightsMap)
                .modelVersion(result.modelVersion())
                .dataQualityScore(result.dataQualityScore())
                .build();

        return forecastRepository.save(forecast);
    }

    // ── SSE Helpers ────────────────────────────────────────────────────────────

    private void sendStatus(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(Map.of("message", message)));
        } catch (IOException e) {
            log.debug("Failed to send SSE status: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", message)));
        } catch (IOException e) {
            log.debug("Failed to send SSE error: {}", e.getMessage());
        }
    }
}
