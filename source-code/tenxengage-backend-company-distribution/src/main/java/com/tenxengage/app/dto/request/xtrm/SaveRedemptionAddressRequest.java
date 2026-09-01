package com.tenxengage.app.dto.request.xtrm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Saves the current payee's payout address to {@code partner_redemption} and triggers XTRM enrollment.
 *
 * <p>{@code addressLine1} + {@code countryIso2} are the minimum XTRM {@code CreateUser} requires; the rest
 * are optional but recommended (reused by the Bank rail's {@code LinkBankBeneficiary}). This is bounded PII
 * stored on {@code partner_redemption} — no SSN/DOB.</p>
 */
public record SaveRedemptionAddressRequest(

        @NotBlank
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @Size(max = 120)
        String city,

        @Size(max = 120)
        String region,

        @Size(max = 20)
        String postalCode,

        @NotBlank
        @Pattern(regexp = "[A-Z]{2}", message = "countryIso2 must be a 2-letter uppercase ISO code")
        String countryIso2
) {
}
