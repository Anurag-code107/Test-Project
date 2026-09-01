package com.tenxengage.app.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CompletionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(CompletionEventProducer.class);
    private static final String TOPIC = "completion-events";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletionEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(CompletionEvent event) {
        try {
            String payload = MAPPER.writeValueAsString(event);
            String key = event.clientId().toString();
            kafkaTemplate.send(TOPIC, key, payload);
            log.info("Published completion event: type={}, incentive={}, user={}",
                event.incentiveType(), event.incentiveId(), event.userId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize completion event: {}", e.getMessage());
        }
    }
}
