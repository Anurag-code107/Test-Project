package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.LocationLevel;

import java.util.UUID;

public record LocationLevelResponse(
    UUID id,
    String name,
    int depth,
    int valueCount,
    boolean useInBuilder,
    boolean useInFilters,
    boolean isRequired
) {
    public static LocationLevelResponse from(LocationLevel level) {
        return new LocationLevelResponse(
            level.getId(),
            level.getName(),
            level.getDepth(),
            level.getValues() != null ? level.getValues().size() : 0,
            level.isUseInBuilder(),
            level.isUseInFilters(),
            level.isRequired()
        );
    }
}
