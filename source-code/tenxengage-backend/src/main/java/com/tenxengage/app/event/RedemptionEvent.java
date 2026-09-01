package com.tenxengage.app.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RedemptionEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID clientId,
        UUID redemptionRequestId,
        UUID userId,
        BigDecimal amount,
        String currencyType,
        String processingMode,
        String status
) {}
