package com.tenxengage.app.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedemptionEventPayload(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID clientId,
        UUID redemptionRequestId,
        UUID userId,
        BigDecimal amount,
        String currencyType,
        String processingMode,
        String status,
        String failureReason
) {}
