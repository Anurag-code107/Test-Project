package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.Connector;
import com.tenxengage.app.entity.enums.ConnectorStatus;
import com.tenxengage.app.entity.enums.ConnectorType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConnectorResponse(
    UUID id,
    ConnectorType connectorType,
    String name,
    ConnectorStatus status,
    String authType,
    Instant lastSyncAt,
    String lastSyncStatus,
    Map<String, String> configSummary,
    Instant createdAt,
    Instant updatedAt
) {
    public static ConnectorResponse from(Connector connector, Map<String, String> maskedConfig) {
        return new ConnectorResponse(
            connector.getId(),
            connector.getConnectorType(),
            connector.getName(),
            connector.getStatus(),
            connector.getAuthType(),
            connector.getLastSyncAt(),
            connector.getLastSyncStatus(),
            maskedConfig,
            connector.getCreatedAt(),
            connector.getUpdatedAt()
        );
    }
}
