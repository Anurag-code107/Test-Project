package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.RecordInteractionRequest;
import com.tenxengage.app.dto.response.IncentiveRecommendationResponse;
import com.tenxengage.app.dto.response.RecommendationCompletionResponse;
import com.tenxengage.app.dto.response.TrainingRecommendationResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.recommendation.RecommendationInsightService;
import com.tenxengage.app.service.recommendation.RecommendationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationService recommendationService;
    private final RecommendationInsightService insightService;
    private final TenantValidator tenantValidator;

    public RecommendationController(RecommendationService recommendationService,
                                     RecommendationInsightService insightService,
                                     TenantValidator tenantValidator) {
        this.recommendationService = recommendationService;
        this.insightService = insightService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping("/training")
    @RequiresPermission("action.recommendations.view")
    public ResponseEntity<List<TrainingRecommendationResponse>> getTrainingRecommendations() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(recommendationService.getTrainingRecommendations(clientId, userId));
    }

    @GetMapping("/incentives")
    @RequiresPermission("action.recommendations.view")
    public ResponseEntity<List<IncentiveRecommendationResponse>> getIncentiveRecommendations() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(recommendationService.getIncentiveRecommendations(clientId, userId));
    }

    @PostMapping("/{type}/{targetId}/interactions")
    @RequiresPermission("action.recommendations.interact")
    public ResponseEntity<Void> recordInteraction(
            @PathVariable String type,
            @PathVariable UUID targetId,
            @Valid @RequestBody RecordInteractionRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        recommendationService.recordInteraction(clientId, userId, targetId,
                type.toUpperCase(), request.interactionType().toUpperCase());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{type}/{targetId}/complete")
    @RequiresPermission("action.recommendations.interact")
    public ResponseEntity<RecommendationCompletionResponse> completeRecommendation(
            @PathVariable String type,
            @PathVariable UUID targetId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        RecommendationCompletionResponse response =
                recommendationService.recordCompletion(clientId, userId, targetId, type.toUpperCase());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{type}/{targetId}/insight", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequiresPermission("action.recommendations.interact")
    public SseEmitter streamInsight(@PathVariable String type, @PathVariable UUID targetId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        SseEmitter emitter = new SseEmitter(60_000L);
        insightService.streamInsight(emitter, clientId, userId, targetId, type.toUpperCase());
        return emitter;
    }
}
