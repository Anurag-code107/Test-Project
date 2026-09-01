package com.tenxengage.app.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tenxengage.app.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final NotificationDispatcher dispatcher;

    public NotificationEventConsumer(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(topics = "notification-events", groupId = "tenxengage-notifications")
    public void handleNotificationEvent(String message) {
        NotificationEvent event;
        try {
            event = MAPPER.readValue(message, NotificationEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize notification event: {}", e.getMessage());
            return;
        }

        log.info("Processing notification event: type={}, client={}", event.notificationTypeKey(), event.clientId());

        try {
            dispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to dispatch notification event type={}: {}",
                event.notificationTypeKey(), e.getMessage(), e);
        }
    }
}
