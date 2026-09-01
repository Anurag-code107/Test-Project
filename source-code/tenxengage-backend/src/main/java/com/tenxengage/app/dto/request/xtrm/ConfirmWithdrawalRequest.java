package com.tenxengage.app.dto.request.xtrm;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Step 2 of a wallet withdrawal ({@code UserWithdrawFund} with OTP). Resends the same {@code amount} +
 * {@code destinationType}/{@code destinationId} as {@link InitiateWithdrawalRequest} plus the one-time
 * password XTRM sent; on success the transfer executes and a {@code partner_withdrawal} row is written.
 */
public record ConfirmWithdrawalRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "BANK|CARD", message = "destinationType must be BANK or CARD")
        String destinationType,

        @NotNull
        UUID destinationId,

        @NotBlank
        @Size(max = 20)
        String otp
) {
}
