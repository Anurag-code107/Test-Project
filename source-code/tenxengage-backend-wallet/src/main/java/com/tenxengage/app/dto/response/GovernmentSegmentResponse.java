package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.GovernmentSegmentConfig;

import java.util.UUID;

public record GovernmentSegmentResponse(
    UUID id,
    UUID clientId,
    String segmentValue,
    boolean isGovernment
) {

    public static GovernmentSegmentResponse from(GovernmentSegmentConfig config) {
        return new GovernmentSegmentResponse(
            config.getId(),
            config.getClientId(),
            config.getSegmentValue(),
            config.isGovernment()
        );
    }
}
