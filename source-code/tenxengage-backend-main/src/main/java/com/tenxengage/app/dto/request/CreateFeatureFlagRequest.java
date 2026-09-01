package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFeatureFlagRequest(
    @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z][a-z0-9_]*$",
        message = "Feature key must be lowercase, start with a letter, and contain only letters, digits, or underscores")
    String featureKey,
    @Size(max = 500)
    String description,
    Boolean starterEnabled,
    Boolean professionalEnabled,
    Boolean enterpriseEnabled
) {}
