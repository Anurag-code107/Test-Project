package com.tenxengage.app.dto.response;

import java.util.UUID;

public record ClientFeatureOverrideResponse(
    UUID featureFlagId,
    String featureKey,
    boolean enabled
) {}
