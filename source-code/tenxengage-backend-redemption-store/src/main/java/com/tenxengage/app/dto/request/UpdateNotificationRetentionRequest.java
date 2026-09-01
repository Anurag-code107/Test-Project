package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationRetentionRequest(
    @NotNull(message = "Retention days is required")
    @Min(value = 7, message = "Minimum retention is 7 days")
    @Max(value = 365, message = "Maximum retention is 365 days")
    Integer retentionDays
) {}
