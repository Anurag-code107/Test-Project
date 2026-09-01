package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.FeatureFlag;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlagResponse(
    UUID id,
    String featureKey,
    String description,
    String category,
    boolean starterEnabled,
    boolean professionalEnabled,
    boolean enterpriseEnabled,
    Instant createdAt,
    Instant updatedAt
) {

    public static FeatureFlagResponse from(FeatureFlag flag) {
        return new FeatureFlagResponse(
            flag.getId(),
            flag.getFeatureKey(),
            flag.getDescription(),
            flag.getCategory(),
            flag.isStarterEnabled(),
            flag.isProfessionalEnabled(),
            flag.isEnterpriseEnabled(),
            flag.getCreatedAt(),
            flag.getUpdatedAt()
        );
    }
}
