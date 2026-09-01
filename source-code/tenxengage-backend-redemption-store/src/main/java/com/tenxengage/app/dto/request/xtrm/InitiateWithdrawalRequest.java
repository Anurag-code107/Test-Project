package com.tenxengage.app.dto.request.xtrm;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Step 1 of a wallet withdrawal ({@code UserWithdrawFund} without OTP). Withdraws {@code amount} from the
 * user's XTRM wallet to the linked {@code destinationType} ({@code BANK} or {@code CARD}) identified by
 * {@code destinationId} (OUR row PK — never the raw beneficiary id / card token). The XTRM response is
 * "OTP sent"; the user then submits {@link ConfirmWithdrawalRequest} with the same values + the OTP.
 */
public record InitiateWithdrawalRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "BANK|CARD", message = "destinationType must be BANK or CARD")
        String destinationType,

        @NotNull
        UUID destinationId
) {
}
