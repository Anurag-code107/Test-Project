package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ValidateOnboardingTokenRequest(
    @NotBlank String token
) {
}
