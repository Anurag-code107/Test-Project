package com.tenxengage.app.event;

import com.tenxengage.app.entity.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReturnEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID clientId,
        UUID returnId,
        UUID redemptionId,
        BigDecimal amount,
        String currencyId,
        ReturnStatus status,
        UUID reviewedBy,
        String vendorReturnReference
) {}
