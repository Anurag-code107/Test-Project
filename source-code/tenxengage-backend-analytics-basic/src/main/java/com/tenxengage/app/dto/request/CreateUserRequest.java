package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    String phone,

    @Size(min = 8, message = "Password must be at least 8 characters")
    String password,

    UUID partnerCompanyId,

    @NotNull(message = "Client role ID is required")
    UUID clientRoleId,

    String metadata
) {}
