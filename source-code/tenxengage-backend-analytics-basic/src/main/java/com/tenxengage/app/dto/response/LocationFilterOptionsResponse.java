package com.tenxengage.app.dto.response;

import java.util.List;
import java.util.UUID;

public record LocationFilterOptionsResponse(
    List<LocationFilterLevel> levels
) {

    public record LocationFilterLevel(
        UUID levelId,
        String levelName,
        int depth,
        List<LocationFilterValue> values
    ) {}

    public record LocationFilterValue(
        UUID id,
        String name,
        String code,
        UUID parentId
    ) {}
}
