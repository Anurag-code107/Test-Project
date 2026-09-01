package com.tenxengage.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tenxengage.app.config.KafkaConfig;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.event.RedemptionEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class RedemptionOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionOrchestrationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final NotificationService notificationService;
    // XTRM (CASH) is implemented — AnyPay TransferFund via XtrmVendorService. Xoxoday (NON_CASH) is still a stub.
    private final XtrmVendorService xtrmVendorService;
    private final XoxodayVendorService xoxodayVendorService;

    public RedemptionOrchestrationService(NotificationService notificationService,
                                           XtrmVendorService xtrmVendorService,
                                           XoxodayVendorService xoxodayVendorService) {
        this.notificationService = notificationService;
        this.xtrmVendorService = xtrmVendorService;
        this.xoxodayVendorService = xoxodayVendorService;
    }

    /**
     * Routes the redemption request to the appropriate vendor based on category:
     * CASH → XTRM (AnyPay TransferFund), NON_CASH → Xoxoday (still a stub until US-06 BE-1).
     */
    public void dispatch(RedemptionRequest request) {
        switch (request.getCategory()) {
            case CASH -> xtrmVendorService.dispatch(request);
            case NON_CASH -> xoxodayVendorService.dispatch(request);
        }
    }

    @KafkaListener(topics = KafkaConfig.REDEMPTION_EVENTS_TOPIC, groupId = "redemption-notifications")
    public void handleRedemptionEvent(String message) {
        RedemptionEventPayload event;
        try {
            event = MAPPER.readValue(message, RedemptionEventPayload.class);
        } catch (Exception e) {
            log.error("[step=redemption-event-deserialize-failed] error={}", e.getMessage());
            return;
        }
        dispatchNotification(event);
    }

    void dispatchNotification(RedemptionEventPayload event) {
        if (event.eventType() == null) {
            log.warn("[step=redemption-notification-skipped] unknown eventType=null");
            return;
        }
        switch (event.eventType()) {
            case "REDEMPTION_REQUESTED" -> {
                try {
                    notificationService.sendRedemptionSubmitted(event.userId(), event.redemptionRequestId());
                } catch (Exception e) {
                    log.error("[step=redemption-notification-error] eventType={} error={}", event.eventType(), e.getMessage());
                }
            }
            case "REDEMPTION_COMPLETED" -> {
                try {
                    notificationService.sendRedemptionCompleted(event.userId(), event.redemptionRequestId(), event.amount());
                } catch (Exception e) {
                    log.error("[step=redemption-notification-error] eventType={} error={}", event.eventType(), e.getMessage());
                }
            }
            case "REDEMPTION_FAILED" -> {
                try {
                    notificationService.sendRedemptionFailed(event.userId(), event.redemptionRequestId(), event.failureReason());
                } catch (Exception e) {
                    log.error("[step=redemption-notification-error] eventType={} error={}", event.eventType(), e.getMessage());
                }
            }
            default -> log.warn("[step=redemption-notification-skipped] unknown eventType={}", event.eventType());
        }
    }
}
