package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.enums.WebhookStatus;

import java.time.Instant;
import java.util.UUID;

public final class RedemptionWebhookEventFixtures {

    private RedemptionWebhookEventFixtures() {
    }

    public static RedemptionWebhookEvent.RedemptionWebhookEventBuilder defaultXtrm(
            UUID clientId, UUID redemptionRequestId) {
        return RedemptionWebhookEvent.builder()
                .clientId(clientId)
                .vendor("XTRM")
                .redemptionRequestId(redemptionRequestId)
                .idempotencyKey("xtrm-" + UUID.randomUUID())
                .payload("{}")
                .status(WebhookStatus.RECEIVED)
                .receivedAt(Instant.now())
                .deleted(false);
    }

    public static RedemptionWebhookEvent.RedemptionWebhookEventBuilder defaultXoxoday(
            UUID clientId, UUID redemptionRequestId) {
        return RedemptionWebhookEvent.builder()
                .clientId(clientId)
                .vendor("XOXODAY")
                .redemptionRequestId(redemptionRequestId)
                .idempotencyKey("xoxoday-" + UUID.randomUUID())
                .payload("{}")
                .status(WebhookStatus.RECEIVED)
                .receivedAt(Instant.now())
                .deleted(false);
    }

    public static RedemptionWebhookEvent.RedemptionWebhookEventBuilder withStatus(
            UUID clientId, UUID redemptionRequestId, WebhookStatus status) {
        return defaultXtrm(clientId, redemptionRequestId)
                .status(status);
    }

    public static RedemptionWebhookEvent.RedemptionWebhookEventBuilder withIdempotencyKey(
            UUID clientId, UUID redemptionRequestId, String idempotencyKey) {
        return defaultXtrm(clientId, redemptionRequestId)
                .idempotencyKey(idempotencyKey);
    }
}
