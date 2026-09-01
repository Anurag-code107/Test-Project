package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ConnectorMappingRequest(
    @NotNull UUID connectorId,
    @NotNull List<FieldMappingEntry> mappings
) {
    public record FieldMappingEntry(
        @NotNull UUID fieldId,
        @NotNull String sourceTable,
        @NotNull String sourceField
    ) {}
}
