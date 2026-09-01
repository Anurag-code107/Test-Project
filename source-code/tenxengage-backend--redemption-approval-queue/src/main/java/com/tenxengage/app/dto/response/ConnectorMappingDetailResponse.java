package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.ConnectorType;

import java.util.List;
import java.util.UUID;

public record ConnectorMappingDetailResponse(
    UUID connectorId,
    String connectorName,
    ConnectorType connectorType,
    List<FieldMappingEntry> mappings
) {
    public record FieldMappingEntry(
        UUID fieldId,
        String sourceTable,
        String sourceField
    ) {}
}
