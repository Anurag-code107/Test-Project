package com.tenxengage.app.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.JourneyCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens for completion events and triggers journey
 * stage evaluation. When a TRAINING or ACTIVITY incentive is completed,
 * this consumer checks if it corresponds to a stage in any journey incentive
 * and processes accordingly.
 */
@Component
public class CompletionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CompletionEventConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final JourneyCompletionService journeyCompletionService;

    public CompletionEventConsumer(JourneyCompletionService journeyCompletionService) {
        this.journeyCompletionService = journeyCompletionService;
    }

    @KafkaListener(topics = "completion-events", groupId = "tenxengage-journey")
    public void handleCompletionEvent(String message) {
        CompletionEvent event;
        try {
            event = MAPPER.readValue(message, CompletionEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize completion event: {}", e.getMessage());
            return;
        }

        log.info("Processing completion event: type={}, incentive={}, user={}",
                event.incentiveType(), event.incentiveId(), event.userId());

        try {
            TenantContext.setClientId(event.clientId());
            journeyCompletionService.onIncentiveCompleted(
                    event.clientId(), event.userId(), event.incentiveId());
        } catch (Exception e) {
            log.error("Error processing completion event for incentive={}, user={}: {}",
                    event.incentiveId(), event.userId(), e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }
}
