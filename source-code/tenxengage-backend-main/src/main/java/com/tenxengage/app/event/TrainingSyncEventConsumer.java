package com.tenxengage.app.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.TrainingCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class TrainingSyncEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrainingSyncEventConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final TrainingCompletionService trainingCompletionService;

    public TrainingSyncEventConsumer(TrainingCompletionService trainingCompletionService) {
        this.trainingCompletionService = trainingCompletionService;
    }

    @KafkaListener(topics = "training-sync-events", groupId = "tenxengage-training")
    public void handleTrainingSyncEvent(String message) {
        TrainingSyncEvent event;
        try {
            event = MAPPER.readValue(message, TrainingSyncEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize training sync event: {}", e.getMessage());
            return;
        }

        UUID clientId = event.clientId();
        if (clientId == null) {
            log.error("Training sync event missing clientId, skipping");
            return;
        }

        log.info("Processing training sync event: client={}, dataUpload={}, records={}",
            clientId, event.dataUploadId(),
            event.records() != null ? event.records().size() : 0);

        try {
            TenantContext.setClientId(clientId);

            // Extract unique user IDs from the sync batch
            // Note: externalUserId in TrainingSyncEvent is a String; the mapping to internal
            // UUID userId happens upstream before publishing the event, or will be resolved
            // by the completion service once user lookup by external ID is available.
            // For now, we attempt to parse as UUID directly.
            Set<UUID> userIds = extractUserIds(event);

            if (userIds.isEmpty()) {
                log.warn("No valid user IDs found in training sync event: client={}, dataUpload={}",
                    clientId, event.dataUploadId());
                return;
            }

            log.info("Evaluating training completions for {} unique users: client={}",
                userIds.size(), clientId);

            for (UUID userId : userIds) {
                try {
                    trainingCompletionService.evaluateTrainingCompletions(clientId, userId);
                } catch (Exception e) {
                    log.error("Failed to evaluate training completions: client={}, user={}, error={}",
                        clientId, userId, e.getMessage(), e);
                }
            }

            log.info("Training sync event processing complete: client={}, users={}",
                clientId, userIds.size());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Extracts unique user IDs from the training sync event records.
     * Skips records with null or unparseable external user IDs.
     */
    private Set<UUID> extractUserIds(TrainingSyncEvent event) {
        if (event.records() == null || event.records().isEmpty()) {
            return Set.of();
        }
        return event.records().stream()
            .map(TrainingSyncEvent.TrainingCompletionRecord::externalUserId)
            .filter(id -> id != null && !id.isBlank())
            .map(id -> {
                try {
                    return UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping non-UUID external user ID: {}", id);
                    return null;
                }
            })
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    }
}
