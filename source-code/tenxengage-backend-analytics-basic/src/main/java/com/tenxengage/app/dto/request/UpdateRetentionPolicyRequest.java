package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRetentionPolicyRequest(
    @NotNull(message = "Retention days is required")
    @Min(value = 1, message = "Retention days must be at least 1")
    Integer retentionDays
) {}
