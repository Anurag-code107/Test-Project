package com.tenxengage.admin.dto.response;

import java.util.UUID;

public record ClientFeatureOverrideResponse(
    UUID featureFlagId,
    String featureKey,
    boolean enabled
) {}
