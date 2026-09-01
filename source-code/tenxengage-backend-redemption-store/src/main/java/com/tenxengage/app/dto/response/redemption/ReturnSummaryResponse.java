package com.tenxengage.app.dto.response.redemption;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight summary view for partner's own return list.
 * resolvedAt is the latest of confirmedAt, rejectedAt, cancelledAt, timedOutAt — null while PENDING_APPROVAL or APPROVED.
 * Never includes clientId, deleted, version, reviewNotes, or vendorReturnReference.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReturnSummaryResponse(
        UUID id,
        UUID redemptionId,
        String catalogItemName,
        BigDecimal amount,
        String currencyId,
        ReturnStatus status,
        String reason,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReturnSummaryResponse from(RedemptionReturn ret, String catalogItemName) {
        Instant resolvedAt = resolveTimestamp(ret);
        return new ReturnSummaryResponse(
                ret.getId(),
                ret.getRedemptionId(),
                catalogItemName,
                ret.getAmount(),
                ret.getCurrencyId(),
                ret.getStatus(),
                ret.getReason(),
                resolvedAt,
                ret.getCreatedAt(),
                ret.getUpdatedAt()
        );
    }

    private static Instant resolveTimestamp(RedemptionReturn ret) {
        // resolvedAt = latest of confirmedAt, rejectedAt, cancelledAt (whichever is non-null)
        Instant latest = null;
        if (ret.getConfirmedAt() != null) {
            latest = ret.getConfirmedAt();
        }
        if (ret.getRejectedAt() != null && (latest == null || ret.getRejectedAt().isAfter(latest))) {
            latest = ret.getRejectedAt();
        }
        if (ret.getCancelledAt() != null && (latest == null || ret.getCancelledAt().isAfter(latest))) {
            latest = ret.getCancelledAt();
        }
        if (ret.getTimedOutAt() != null && (latest == null || ret.getTimedOutAt().isAfter(latest))) {
            latest = ret.getTimedOutAt();
        }
        return latest;
    }
}
