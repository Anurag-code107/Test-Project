package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompleteProfileRequest(
    @NotBlank String token,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String phone,
    String countryCode
) {
}
