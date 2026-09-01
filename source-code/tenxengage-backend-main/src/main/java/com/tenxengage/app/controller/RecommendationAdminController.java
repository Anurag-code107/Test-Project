package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.SaveRecommendationConfigRequest;
import com.tenxengage.app.dto.response.RecommendationConfigResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.recommendation.RecommendationConfigService;
import com.tenxengage.app.service.recommendation.RecommendationScoringService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/recommendations")
public class RecommendationAdminController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationAdminController.class);

    private final RecommendationConfigService configService;
    private final RecommendationScoringService scoringService;
    private final TenantValidator tenantValidator;

    public RecommendationAdminController(RecommendationConfigService configService,
                                          RecommendationScoringService scoringService,
                                          TenantValidator tenantValidator) {
        this.configService = configService;
        this.scoringService = scoringService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping("/config")
    @RequiresPermission("action.recommendations.config")
    public ResponseEntity<RecommendationConfigResponse> getConfig() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return ResponseEntity.ok(configService.getConfig(clientId));
    }

    @PutMapping("/config")
    @RequiresPermission("action.recommendations.config")
    public ResponseEntity<RecommendationConfigResponse> saveConfig(
            @Valid @RequestBody SaveRecommendationConfigRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return ResponseEntity.ok(configService.saveConfig(clientId, request));
    }

    @PostMapping("/refresh")
    @RequiresPermission("action.recommendations.config")
    public ResponseEntity<Map<String, Object>> triggerRefresh() {
        UUID clientId = tenantValidator.getCurrentClientId();
        log.info("Manual recommendation refresh triggered for client {}", clientId);

        long start = System.currentTimeMillis();
        scoringService.scoreTrainingForClient(clientId);
        scoringService.scoreIncentivesForClient(clientId);
        long elapsed = System.currentTimeMillis() - start;

        log.info("Manual recommendation refresh completed for client {} in {}ms", clientId, elapsed);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "clientId", clientId.toString(),
                "elapsedMs", elapsed
        ));
    }
}
