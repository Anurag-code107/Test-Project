package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What a company admin supplies to finish their payout setup.
 *
 * <p>Address only. Their name, email, mobile and country were set when their login was created, and the
 * email in particular cannot be changed here — XTRM refuses to reuse an address, so it is spent once.</p>
 *
 * <p>No address line: XTRM's {@code BeneficiaryCompanyAdminDetails} does not take one.</p>
 */
public record CompleteCompanyAdminProfileRequest(
    @NotBlank(message = "City is required") @Size(max = 100) String adminCity,
    @NotBlank(message = "State/region is required") @Size(max = 100) String adminRegion,
    @NotBlank(message = "Postal code is required") @Size(max = 20) String adminPostalCode
) {}
