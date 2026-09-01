package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.LocationValue;

import java.util.List;
import java.util.UUID;

public record LocationValueResponse(
    UUID id,
    String name,
    String code,
    String levelName,
    UUID levelId,
    UUID parentId,
    List<LocationValueResponse> children
) {
    public static LocationValueResponse from(LocationValue value) {
        List<LocationValueResponse> childResponses = value.getChildren() != null
            ? value.getChildren().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(LocationValueResponse::from)
                .toList()
            : List.of();

        return new LocationValueResponse(
            value.getId(),
            value.getName(),
            value.getCode(),
            value.getLevel() != null ? value.getLevel().getName() : null,
            value.getLevel() != null ? value.getLevel().getId() : null,
            value.getParent() != null ? value.getParent().getId() : null,
            childResponses
        );
    }
}
