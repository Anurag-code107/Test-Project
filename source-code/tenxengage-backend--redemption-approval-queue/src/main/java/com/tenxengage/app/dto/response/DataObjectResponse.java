package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.DataObject;

import java.time.Instant;
import java.util.UUID;

public record DataObjectResponse(
    UUID id,
    String name,
    String description,
    boolean isDefault,
    int fieldCount,
    String connectorName,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt
) {
    public static DataObjectResponse from(DataObject obj, String connectorName) {
        return new DataObjectResponse(
            obj.getId(),
            obj.getName(),
            obj.getDescription(),
            obj.isDefault(),
            obj.getFields().size(),
            connectorName,
            obj.getSortOrder(),
            obj.getCreatedAt(),
            obj.getUpdatedAt()
        );
    }
}
