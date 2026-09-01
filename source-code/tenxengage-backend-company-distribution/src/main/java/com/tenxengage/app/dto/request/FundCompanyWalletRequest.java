package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Credit a partner company's wallet — the API replacement for inserting into {@code reward_wallets} by hand.
 *
 * <p>{@code reference} is <b>required</b>, not optional. It is the idempotency key: funding is the one operation
 * in the system that creates balance from nothing, so a double-submit must be impossible rather than merely
 * unlikely. Use whatever identifies the funding on your side (a PO number, a transfer id).</p>
 */
public record FundCompanyWalletRequest(

        /** Always {@code cash} in v1 — the only currency distributions support. */
        @NotBlank @Size(max = 50) String currencyId,

        @NotNull @Positive @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,

        /**
         * Your identifier for this funding. Idempotency is enforced on it, so re-submitting the same reference
         * credits the wallet once.
         */
        @NotBlank @Size(max = 255) String reference,

        @Size(max = 500) String note
) {
}
