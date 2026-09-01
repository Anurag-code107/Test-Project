package com.tenxengage.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.KafkaConfig;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.event.ReturnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Component
public class ReturnEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ReturnEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ReturnEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishReturnRequested(RedemptionReturn ret) {
        publish(buildEvent("RETURN_REQUESTED", ret, null, null));
    }

    public void publishReturnApproved(RedemptionReturn ret) {
        publish(buildEvent("RETURN_APPROVED", ret, ret.getReviewedBy(), null));
    }

    public void publishReturnConfirmed(RedemptionReturn ret) {
        publish(buildEvent("RETURN_CONFIRMED", ret, null, ret.getVendorReturnReference()));
    }

    public void publishReturnRejected(RedemptionReturn ret) {
        publish(buildEvent("RETURN_REJECTED", ret, ret.getReviewedBy(), null));
    }

    public void publishReturnCancelled(RedemptionReturn ret) {
        publish(buildEvent("RETURN_CANCELLED", ret, null, null));
    }

    public void publishReturnTimedOut(RedemptionReturn ret) {
        publish(buildEvent("RETURN_TIMED_OUT", ret, null, null));
    }

    private ReturnEvent buildEvent(String eventType, RedemptionReturn ret,
                                   UUID reviewedBy, String vendorReturnReference) {
        return new ReturnEvent(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                ret.getClientId(),
                ret.getId(),
                ret.getRedemptionId(),
                ret.getAmount(),
                ret.getCurrencyId(),
                ret.getStatus(),
                reviewedBy,
                vendorReturnReference
        );
    }

    // Sends after the surrounding transaction commits so Kafka is never notified
    // for a rollback. Falls back to immediate send when called outside a transaction
    // (e.g. ReturnTimeoutScheduler running its own per-page mini-transactions).
    private void publish(ReturnEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(event);
                }
            });
        } else {
            doPublish(event);
        }
    }

    private void doPublish(ReturnEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaConfig.RETURN_EVENTS_TOPIC, event.clientId().toString(), payload);
            log.info("Published return event: type={}, returnId={}", event.eventType(), event.returnId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize return event: type={}, returnId={}", event.eventType(), event.returnId(), e);
        }
    }
}
