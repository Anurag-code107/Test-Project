package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.BatchCadence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantRedemptionSettingsRequest(
        @NotNull BatchCadence batchCadence,
        @NotNull @Min(1) @Max(50) Integer maxInFlightRedemptions
) {
}
