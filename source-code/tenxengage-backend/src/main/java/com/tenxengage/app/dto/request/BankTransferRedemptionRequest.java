package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bank-transfer redemption: pay the entered amount into the user's linked bank. The catalog item
 * (the reserved per-client bank-transfer card) and the currency ({@code cash}) are resolved
 * server-side — the caller sends only the funding wallet (their cash reward wallet) + the amount.
 */
public record BankTransferRedemptionRequest(
        @NotNull UUID walletId,
        @NotNull @Positive @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,
        // Which linked bank to pay (our bank id from GET /banks). Optional — null pays the user's default bank.
        UUID bankId,
        @Size(max = 255) String clientIdempotencyKey
) {}
