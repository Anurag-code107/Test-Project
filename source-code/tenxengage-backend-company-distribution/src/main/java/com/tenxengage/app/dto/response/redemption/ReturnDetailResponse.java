package com.tenxengage.app.dto.response.redemption;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full detail view for a return request.
 * reviewNotes and vendorReturnReference are admin-only fields —
 * pass null for partner calls so they are omitted from partner-facing JSON responses.
 * Never includes clientId, deleted, version, or raw reviewed_by UUID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReturnDetailResponse(
        UUID id,
        UUID redemptionId,
        String catalogItemName,
        String partnerDisplayName,
        BigDecimal amount,
        String currencyId,
        ReturnStatus status,
        String reason,
        Instant reviewedAt,
        String reviewNotes,
        String vendorReturnReference,
        Instant approvedAt,
        Instant timedOutAt,
        Instant confirmedAt,
        Instant rejectedAt,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Factory for both partner and admin calls.
     * For partner calls: pass null for reviewNotes and vendorReturnReference.
     * For admin calls: pass the actual values.
     */
    public static ReturnDetailResponse from(
            RedemptionReturn ret,
            String catalogItemName,
            String partnerDisplayName) {
        return new ReturnDetailResponse(
                ret.getId(),
                ret.getRedemptionId(),
                catalogItemName,
                partnerDisplayName,
                ret.getAmount(),
                ret.getCurrencyId(),
                ret.getStatus(),
                ret.getReason(),
                ret.getReviewedAt(),
                ret.getReviewNotes(),
                ret.getVendorReturnReference(),
                ret.getApprovedAt(),
                ret.getTimedOutAt(),
                ret.getConfirmedAt(),
                ret.getRejectedAt(),
                ret.getCancelledAt(),
                ret.getCreatedAt(),
                ret.getUpdatedAt()
        );
    }
}
