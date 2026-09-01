package com.tenxengage.app.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes notification events to the {@code notification-events} Kafka topic.
 *
 * <p>Hardening (US-02 BE-2):
 * <ul>
 *   <li>Injects the Spring-managed {@link ObjectMapper} bean instead of creating a static instance
 *       — avoids divergence from the app's global Jackson configuration (PROJECT-CONTEXT.md).</li>
 *   <li>Attaches {@code .whenComplete()} to {@code kafkaTemplate.send()} so broker failures are
 *       logged rather than silently discarded (PROJECT-CONTEXT.md anti-pattern register).</li>
 * </ul>
 *
 * <p>Callers must emit from {@code TransactionSynchronizationManager.afterCommit()} — not directly
 * inside a {@code @Transactional} method — to prevent publishing events for rolled-back state.
 */
@Component
public class NotificationEventProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventProducer.class);
    private static final String TOPIC = "notification-events";

    /** Max wait for broker ack in {@link #publishAndConfirm} (off-peak batch callers only). */
    private static final long CONFIRM_TIMEOUT_SECONDS = 15L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public NotificationEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(NotificationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String key = event.clientId() != null ? event.clientId().toString() : event.notificationTypeKey();
            kafkaTemplate.send(TOPIC, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka send failed for notification event type={} client={}: {}",
                                    event.notificationTypeKey(), event.clientId(), ex.getMessage(), ex);
                        } else {
                            // Log after broker ack — not before — to avoid false success signals
                            log.info("Published notification event: type={}, client={}",
                                    event.notificationTypeKey(), event.clientId());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification event type={}: {}",
                    event.notificationTypeKey(), e.getMessage(), e);
        }
    }

    /**
     * Publishes an event and blocks until the broker acknowledges it (or the attempt fails),
     * returning whether delivery was confirmed.
     *
     * <p>Unlike {@link #publish}, callers can act on the outcome — used by the balance-expiry
     * warn phase so a notice is only marked {@code NOTIFIED} once the advance warning is
     * confirmed delivered (FR-09.7: never expire a balance without a delivered prior notice).
     * A {@code false} result means the warning was NOT delivered and the caller must leave the
     * notice re-sendable for the next sweep.
     *
     * <p>Blocking is acceptable here: the only caller is the off-peak scheduled sweep, never a
     * request thread. Bounded by {@link #CONFIRM_TIMEOUT_SECONDS}.
     *
     * @return {@code true} if the broker acknowledged the send, {@code false} on serialization
     *         failure, broker error, timeout, or interruption
     */
    public boolean publishAndConfirm(NotificationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String key = event.clientId() != null ? event.clientId().toString() : event.notificationTypeKey();
            kafkaTemplate.send(TOPIC, key, payload).get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Published+confirmed notification event: type={}, client={}",
                    event.notificationTypeKey(), event.clientId());
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification event type={}: {}",
                    event.notificationTypeKey(), e.getMessage(), e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while confirming notification event type={} client={}",
                    event.notificationTypeKey(), event.clientId());
            return false;
        } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
            log.error("Kafka send not confirmed for notification event type={} client={}: {}",
                    event.notificationTypeKey(), event.clientId(), e.getMessage(), e);
            return false;
        }
    }
}
