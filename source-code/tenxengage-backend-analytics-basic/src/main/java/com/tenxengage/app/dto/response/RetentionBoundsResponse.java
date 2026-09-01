package com.tenxengage.app.dto.response;

import java.util.Map;

public record RetentionBoundsResponse(
    Map<String, int[]> bounds
) {

    public static RetentionBoundsResponse from(Map<String, int[]> bounds) {
        return new RetentionBoundsResponse(bounds);
    }
}
