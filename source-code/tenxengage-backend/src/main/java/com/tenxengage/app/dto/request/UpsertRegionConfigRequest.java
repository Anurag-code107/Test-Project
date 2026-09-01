package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpsertRegionConfigRequest(
        @NotNull Boolean enabled
) {}
