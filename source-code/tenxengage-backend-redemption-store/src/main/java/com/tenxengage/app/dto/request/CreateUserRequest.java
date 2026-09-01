package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "First name is required")
    String firstName,

    @NotBlank(message = "Last name is required")
    String lastName,

    // Required: a mobile is needed for payout enrollment/withdrawal at XTRM. National number, digits only.
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "[0-9]{7,20}", message = "phone must be 7–20 digits (national number, no country code)")
    String phone,

    @NotBlank(message = "Phone country is required")
    @Pattern(regexp = "[A-Z]{2}", message = "phoneCountryIso2 must be a 2-letter uppercase ISO code")
    String phoneCountryIso2,

    @Size(min = 8, message = "Password must be at least 8 characters")
    String password,

    UUID partnerCompanyId,

    @NotNull(message = "Client role ID is required")
    UUID clientRoleId,

    String metadata
) {}
