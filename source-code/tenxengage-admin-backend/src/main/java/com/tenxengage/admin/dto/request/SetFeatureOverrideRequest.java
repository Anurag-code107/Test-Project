package com.tenxengage.admin.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SetFeatureOverrideRequest(
    @NotNull UUID featureFlagId,
    @NotNull Boolean enabled
) {}
