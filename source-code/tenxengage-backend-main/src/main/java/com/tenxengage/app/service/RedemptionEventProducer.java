package com.tenxengage.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tenxengage.app.config.KafkaConfig;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.event.RedemptionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RedemptionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RedemptionEventProducer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final KafkaTemplate<String, String> kafkaTemplate;

    public RedemptionEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishRedemptionRequested(RedemptionRequest request) {
        publish(buildEvent("REDEMPTION_REQUESTED", request));
    }

    public void publishRedemptionCompleted(RedemptionRequest request) {
        publish(buildEvent("REDEMPTION_COMPLETED", request));
    }

    public void publishRedemptionFailed(RedemptionRequest request) {
        publish(buildEvent("REDEMPTION_FAILED", request));
    }

    private RedemptionEvent buildEvent(String eventType, RedemptionRequest request) {
        return new RedemptionEvent(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                request.getClientId(),
                request.getId(),
                request.getUserId(),
                request.getAmount(),
                request.getCurrencyId(),
                request.getProcessingMode().name(),
                request.getStatus().name()
        );
    }

    private void publish(RedemptionEvent event) {
        try {
            String payload = MAPPER.writeValueAsString(event);
            kafkaTemplate.send(KafkaConfig.REDEMPTION_EVENTS_TOPIC, event.clientId().toString(), payload);
            log.info("Published redemption event: type={}, requestId={}", event.eventType(), event.redemptionRequestId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize redemption event: {}", e.getMessage());
        }
    }
}
