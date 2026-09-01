package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JourneyStageRequest(
    @NotBlank String linkedIncentiveId,
    int sortOrder
) {
}
