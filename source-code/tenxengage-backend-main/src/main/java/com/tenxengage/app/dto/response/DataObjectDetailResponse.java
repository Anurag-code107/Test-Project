package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.DataObject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataObjectDetailResponse(
    UUID id,
    String name,
    String description,
    boolean isDefault,
    int sortOrder,
    List<DataObjectFieldResponse> fields,
    ConnectorMappingDetailResponse connectorMapping,
    Instant createdAt,
    Instant updatedAt
) {
    public static DataObjectDetailResponse from(DataObject obj,
                                                 List<DataObjectFieldResponse> fields,
                                                 ConnectorMappingDetailResponse mapping) {
        return new DataObjectDetailResponse(
            obj.getId(),
            obj.getName(),
            obj.getDescription(),
            obj.isDefault(),
            obj.getSortOrder(),
            fields,
            mapping,
            obj.getCreatedAt(),
            obj.getUpdatedAt()
        );
    }
}
