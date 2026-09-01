package com.tenxengage.app.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ApprovalEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ApprovalEventProducer.class);
    private static final String TOPIC = "approval-events";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public ApprovalEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ApprovalRequestEvent event) {
        try {
            String payload = MAPPER.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.incentiveId().toString(), payload);
            log.info("Published approval event for incentive {}", event.incentiveId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize approval event: {}", e.getMessage());
        }
    }
}
