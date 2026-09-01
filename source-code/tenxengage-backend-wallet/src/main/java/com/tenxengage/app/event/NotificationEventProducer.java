package com.tenxengage.app.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventProducer.class);
    private static final String TOPIC = "notification-events";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final KafkaTemplate<String, String> kafkaTemplate;

    public NotificationEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(NotificationEvent event) {
        try {
            String payload = MAPPER.writeValueAsString(event);
            String key = event.clientId() != null ? event.clientId().toString() : event.notificationTypeKey();
            kafkaTemplate.send(TOPIC, key, payload);
            log.info("Published notification event: type={}, client={}", event.notificationTypeKey(), event.clientId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification event: {}", e.getMessage());
        }
    }
}
