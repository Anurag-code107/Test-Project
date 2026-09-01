package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.DistributionRail;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A partner admin distributing the company wallet to their sellers.
 *
 * <p><b>One amount, many recipients.</b> Every selected seller receives the same {@code amount} — the admin
 * types 50 once and each recipient gets 50. Per-recipient amounts are deliberately not supported (OQ-5);
 * the per-item column exists because each recipient still needs their own ledger leg.</p>
 *
 * <p>No {@code currencyId}: cash only in v1 (OQ-10), and the server takes it from the source wallet rather
 * than trusting the client. No recipient count cap (OQ-8) — see the bounded fan-out in the service.</p>
 */
public record CreateCompanyDistributionRequest(

        @NotNull DistributionRail rail,

        /** The COMPANY wallet to spend. Validated against the caller's own company — never trusted as given. */
        @NotNull UUID sourceWalletId,

        /**
         * A curated catalog item to distribute. Legacy path, kept for older clients — prefer
         * {@code providerSku}. Must be null for the non-gift-card rails.
         */
        UUID catalogItemId,
        /**
         * The XTRM gift-card SKU to distribute, as listed by {@code GET /redemption/distribution/gift-cards}.
         *
         * <p>The preferred way to pick a gift card: a partner admin chooses from the provider's whole
         * catalogue rather than only what a client admin has curated. The server backs it with a hidden
         * catalog row (see {@code DistributionGiftCardService}) so the payout path is unchanged.</p>
         *
         * <p>Exactly one of this and {@code catalogItemId} is needed for {@code GIFT_CARD}; both must be null
         * for the other rails.</p>
         */
        @Size(max = 100) String providerSku,

        /** Applied to every recipient. Bounds are re-checked server-side against the SKU / catalog item. */
        @NotNull @Positive @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,

        /**
         * The recipients. Must be active PARTNER_SELLERs of the caller's company (OQ-14) and must not
         * contain the caller (OQ-7) — an admin cannot distribute to themself.
         */
        @NotEmpty List<UUID> userIds,

        /** Shown to recipients on their award. */
        @Size(max = 500) String note,

        @Size(max = 255) String clientIdempotencyKey
) {
}
