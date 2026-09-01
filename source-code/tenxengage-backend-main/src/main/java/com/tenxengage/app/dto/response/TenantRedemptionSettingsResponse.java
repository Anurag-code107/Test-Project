package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.TenantRedemptionSettings;
import com.tenxengage.app.entity.enums.BatchCadence;

import java.time.Instant;
import java.util.UUID;

public record TenantRedemptionSettingsResponse(
        UUID id,
        BatchCadence batchCadence,
        Integer maxInFlightRedemptions,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantRedemptionSettingsResponse from(TenantRedemptionSettings settings) {
        return new TenantRedemptionSettingsResponse(
                settings.getId(),
                settings.getBatchCadence(),
                settings.getMaxInFlightRedemptions(),
                settings.getCreatedAt(),
                settings.getUpdatedAt()
        );
    }
}
