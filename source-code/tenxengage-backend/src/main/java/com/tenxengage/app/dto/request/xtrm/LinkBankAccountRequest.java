package com.tenxengage.app.dto.request.xtrm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to link a bank/ACH beneficiary for the current user's XTRM Bank payout rail.
 *
 * <p><b>Pass-through only — NEVER persisted.</b> These fields are forwarded to XTRM
 * {@code LinkBankBeneficiary}; only the returned {@code UserLinkedBankID} reference and a masked
 * display label are stored on {@code partner_redemption}. The raw account/routing numbers are never
 * written to the database or logs.</p>
 *
 * <p>Validation here is <b>structural only</b> ({@code @NotBlank} / {@code @Size} / {@code @Pattern}).
 * Domain rejections (duplicate bank, invalid routing) come from XTRM as a 422 with an errorCode —
 * they are not expressed as bean-validation constraints.</p>
 */
public record LinkBankAccountRequest(

        @NotBlank
        @Size(max = 140)
        String contactName,

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "[0-9]{7,20}", message = "contactPhone must be 7–20 digits, no spaces or symbols (e.g. 14085551234)")
        String contactPhone,

        @NotBlank
        @Size(max = 34)
        String accountNumber,

        @NotBlank
        @Size(max = 34)
        String routingNumber,

        @Size(max = 11)
        String swiftBic,

        @NotBlank
        @Size(max = 140)
        String institutionName,

        @NotBlank
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @NotBlank
        @Size(max = 120)
        String city,

        @NotBlank
        @Size(max = 120)
        String region,

        @NotBlank
        @Size(max = 20)
        String postalCode,

        @NotBlank
        @Pattern(regexp = "[A-Z]{2}", message = "countryIso2 must be a 2-letter uppercase ISO code")
        String countryIso2,

        @NotBlank
        @Size(max = 20)
        String withdrawType
) {
}
