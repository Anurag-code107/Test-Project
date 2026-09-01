package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Step 1 of changing the current user's mobile number. For an XTRM-enrolled payee this triggers a 2-step
 * OTP {@code UpdateUser} (XTRM texts the code to the new number); for a not-yet-enrolled user the phone is
 * saved immediately (it flows to XTRM at enrollment). Mobile is collected as country (ISO2) + national number.
 */
public record InitiatePhoneUpdateRequest(

        @NotBlank
        @Pattern(regexp = "[0-9]{7,20}", message = "phone must be 7–20 digits (national number, no country code)")
        String phone,

        @NotBlank
        @Pattern(regexp = "[A-Z]{2}", message = "phoneCountryIso2 must be a 2-letter uppercase ISO code")
        String phoneCountryIso2
) {
}
