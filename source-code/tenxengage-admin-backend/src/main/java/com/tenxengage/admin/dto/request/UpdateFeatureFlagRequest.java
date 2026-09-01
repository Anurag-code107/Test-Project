package com.tenxengage.admin.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateFeatureFlagRequest(
    @Size(max = 500)
    String description,
    Boolean starterEnabled,
    Boolean professionalEnabled,
    Boolean enterpriseEnabled
) {}
