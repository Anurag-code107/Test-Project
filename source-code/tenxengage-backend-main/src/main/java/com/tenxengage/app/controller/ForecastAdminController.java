package com.tenxengage.app.controller;

import com.tenxengage.app.service.DataOperationsService;
import com.tenxengage.app.service.forecast.ForecastAccuracyService;
import com.tenxengage.app.service.forecast.ForecastAggregationService;
import com.tenxengage.app.service.recommendation.RecommendationScoringService;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/forecast-aggregation")
public class ForecastAdminController {

    private static final Logger log = LoggerFactory.getLogger(ForecastAdminController.class);

    private final ForecastAggregationService aggregationService;
    private final ForecastAccuracyService accuracyService;
    private final RecommendationScoringService recommendationScoringService;
    private final DataOperationsService dataOperationsService;
    private final TenantValidator tenantValidator;

    public ForecastAdminController(ForecastAggregationService aggregationService,
                                    ForecastAccuracyService accuracyService,
                                    RecommendationScoringService recommendationScoringService,
                                    DataOperationsService dataOperationsService,
                                    TenantValidator tenantValidator) {
        this.aggregationService = aggregationService;
        this.accuracyService = accuracyService;
        this.recommendationScoringService = recommendationScoringService;
        this.dataOperationsService = dataOperationsService;
        this.tenantValidator = tenantValidator;
    }

    @PostMapping("/trigger")
    @RequiresPermission("action.incentive.forecast")
    public ResponseEntity<Map<String, Object>> triggerAggregation() {
        UUID clientId = tenantValidator.getCurrentClientId();
        log.info("Manual forecast aggregation triggered for client {}", clientId);

        long start = System.currentTimeMillis();
        aggregationService.aggregateForClient(clientId);
        accuracyService.evaluateForClient(clientId);

        // Refresh recommendation scores using freshly aggregated data
        try {
            recommendationScoringService.scoreTrainingForClient(clientId);
            recommendationScoringService.scoreIncentivesForClient(clientId);
            log.info("Recommendation scoring completed for client {}", clientId);
        } catch (Exception e) {
            log.warn("Recommendation scoring failed for client {}: {}", clientId, e.getMessage());
        }

        // Run tagging engine to evaluate PO eligibility against active incentives
        try {
            dataOperationsService.triggerTaggingJob();
            log.info("Tagging job completed for client {}", clientId);
        } catch (Exception e) {
            log.warn("Tagging job failed for client {}: {}", clientId, e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Manual aggregation completed for client {} in {}ms", clientId, elapsed);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "clientId", clientId.toString(),
                "elapsedMs", elapsed
        ));
    }
}
