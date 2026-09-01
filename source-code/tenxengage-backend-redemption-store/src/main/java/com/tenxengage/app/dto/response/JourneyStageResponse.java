package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.JourneyStage;

import java.util.UUID;

public record JourneyStageResponse(
    UUID id,
    String linkedIncentiveId,
    int sortOrder
) {

    public static JourneyStageResponse from(JourneyStage stage) {
        return new JourneyStageResponse(
            stage.getId(),
            stage.getLinkedIncentiveId().toString(),
            stage.getSortOrder()
        );
    }
}
