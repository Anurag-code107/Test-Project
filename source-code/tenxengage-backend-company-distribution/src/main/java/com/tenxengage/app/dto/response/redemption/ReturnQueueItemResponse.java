package com.tenxengage.app.dto.response.redemption;

import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight list item for the admin return review queue.
 * catalogItemName, partnerDisplayName, and partnerCompanyName are hydrated by the service.
 * Never includes clientId, deleted, or version.
 */
public record ReturnQueueItemResponse(
        UUID id,
        String catalogItemName,
        String partnerDisplayName,
        String partnerCompanyName,
        BigDecimal amount,
        String currencyId,
        ReturnStatus status,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReturnQueueItemResponse from(
            RedemptionReturn ret,
            String catalogItemName,
            String partnerDisplayName,
            String partnerCompanyName) {
        return new ReturnQueueItemResponse(
                ret.getId(),
                catalogItemName,
                partnerDisplayName,
                partnerCompanyName,
                ret.getAmount(),
                ret.getCurrencyId(),
                ret.getStatus(),
                ret.getReason(),
                ret.getCreatedAt(),
                ret.getUpdatedAt()
        );
    }
}
