package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Step 2 of changing the current user's mobile — resends the same country + number as
 * {@link InitiatePhoneUpdateRequest} plus the one-time password XTRM sent. On success the number is applied
 * at XTRM and persisted locally.
 */
public record ConfirmPhoneUpdateRequest(

        @NotBlank
        @Pattern(regexp = "[0-9]{7,20}", message = "phone must be 7–20 digits (national number, no country code)")
        String phone,

        @NotBlank
        @Pattern(regexp = "[A-Z]{2}", message = "phoneCountryIso2 must be a 2-letter uppercase ISO code")
        String phoneCountryIso2,

        @NotBlank
        @Size(max = 20)
        String otp
) {
}
