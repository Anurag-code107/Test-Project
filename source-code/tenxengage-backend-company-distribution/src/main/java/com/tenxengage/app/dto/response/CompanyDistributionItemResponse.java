package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One recipient's line in a distribution.
 *
 * <p>{@code status} is <b>derived</b>, never stored twice: a payout item reports its
 * {@code redemption_requests.status}, a wallet-transfer item reports its own. One owner of the truth per
 * item, so status can never disagree with the money (design §4.4).</p>
 */
public record CompanyDistributionItemResponse(
        UUID itemId,
        UUID recipientUserId,
        String recipientName,
        String recipientEmail,
        BigDecimal amount,
        String status,
        /** Masked destination, e.g. {@code KOTAK ••8943}, the gift-card email, or {@code Cash wallet}. */
        String destination,
        /** XTRM payment transaction id — payout rails only, and only once completed. */
        String paymentTransactionId,
        String failureReason,
        Instant settledAt
) {
}
