package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateSelfProfileRequest(
    @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName,

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName,

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    String phone,

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    String avatar,

    Map<String, String> customFields
) {
}
