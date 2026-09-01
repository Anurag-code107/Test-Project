package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SubmitPersonalRedemptionRequest(
        @NotNull UUID catalogItemId,
        @NotNull UUID walletId,
        @NotNull @Positive @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 50) String currencyId,
        @Size(max = 255) String clientIdempotencyKey
) {}
