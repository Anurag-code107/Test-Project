package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.UserStatus;
import jakarta.validation.constraints.Email;

import java.util.UUID;

public record UpdateUserRequest(
    @Email(message = "Email must be valid")
    String email,

    String firstName,

    String lastName,

    String phone,

    String phoneCountryIso2,

    String avatar,

    UserStatus status,

    UUID clientRoleId,

    String metadata
) {}
