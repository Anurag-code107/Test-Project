package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SetConsentRequest(
    @NotBlank String token,
    @NotNull Map<String, Boolean> consents
) {
}
