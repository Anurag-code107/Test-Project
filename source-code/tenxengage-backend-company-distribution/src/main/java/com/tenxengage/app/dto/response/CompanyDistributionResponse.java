package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.enums.DistributionRail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A distribution and its per-recipient outcomes.
 *
 * <p><b>{@code requestedTotal} and {@code settledTotal} are both present on purpose.</b> After a partial
 * failure they differ, and showing only the requested figure would make a half-failed distribution read as
 * though it paid out in full (design §4.4).</p>
 */
public record CompanyDistributionResponse(
        UUID id,
        DistributionRail rail,
        String railDisplayName,
        UUID catalogItemId,
        String catalogItemName,
        String currencyId,
        BigDecimal amountPerRecipient,
        int recipientCount,
        /** What was submitted: amount × recipientCount. */
        BigDecimal requestedTotal,
        /** What actually left the company wallet — lower than requested when recipients failed. */
        BigDecimal settledTotal,
        /** Rolled up from the items: COMPLETED / PROCESSING / FAILED / PARTIALLY_COMPLETED. */
        String status,
        UUID initiatedByUserId,
        String initiatedByName,
        String note,
        Instant createdAt,
        List<CompanyDistributionItemResponse> items
) {

    public static CompanyDistributionResponse from(
            CompanyDistribution d,
            String catalogItemName,
            String initiatedByName,
            String rollupStatus,
            BigDecimal settledTotal,
            List<CompanyDistributionItemResponse> items) {

        BigDecimal perRecipient = d.getRecipientCount() == 0
                ? BigDecimal.ZERO
                : d.getTotalAmount().divide(BigDecimal.valueOf(d.getRecipientCount()), 4, java.math.RoundingMode.HALF_UP);

        return new CompanyDistributionResponse(
                d.getId(),
                d.getRail(),
                d.getRail().getDisplayName(),
                d.getCatalogItemId(),
                catalogItemName,
                d.getCurrencyId(),
                perRecipient,
                d.getRecipientCount(),
                d.getTotalAmount(),
                settledTotal,
                rollupStatus,
                d.getInitiatedByUserId(),
                initiatedByName,
                d.getNote(),
                d.getCreatedAt(),
                items
        );
    }
}
