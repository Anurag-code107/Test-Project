package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.DistributionRail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One reward a partner seller received from a company admin — the seller-side view of a distribution item.
 *
 * <p>Distributions are deliberately absent from the seller's own Transaction History (that screen means
 * "redemptions I made from my wallet"), so this is the only place a seller sees them. A wallet transfer
 * additionally shows up as a CREDIT in their balance and ledger, which is correct: that money genuinely
 * arrived.</p>
 */
public record CompanyAwardResponse(
        UUID awardId,
        Instant receivedAt,
        DistributionRail rail,
        String railDisplayName,
        /** Gift-card name, or the rail's label for bank/wallet transfers. */
        String rewardName,
        BigDecimal amount,
        String currencyId,
        String status,
        /** Where it went: the gift-card email, a masked bank label, or {@code Cash wallet}. */
        String destination,
        /** The admin who sent it. */
        String awardedByName,
        String companyName,
        /** The admin's message, if they left one. */
        String note,
        String failureReason,
        /** XTRM payment transaction id — payout rails only, once completed. */
        String paymentTransactionId
) {
}
