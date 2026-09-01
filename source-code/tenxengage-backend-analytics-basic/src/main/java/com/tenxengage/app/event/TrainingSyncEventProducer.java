package com.tenxengage.app.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TrainingSyncEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TrainingSyncEventProducer.class);
    private static final String TOPIC = "training-sync-events";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final KafkaTemplate<String, String> kafkaTemplate;

    public TrainingSyncEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(TrainingSyncEvent event) {
        try {
            String payload = MAPPER.writeValueAsString(event);
            String key = event.clientId().toString();
            kafkaTemplate.send(TOPIC, key, payload);
            log.info("Published training sync event: client={}, records={}",
                event.clientId(), event.records() != null ? event.records().size() : 0);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize training sync event: {}", e.getMessage());
        }
    }
}
