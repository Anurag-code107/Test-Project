package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientCatalogRegionConfig;

import java.time.Instant;
import java.util.UUID;

public record ClientCatalogRegionConfigResponse(
        UUID id,
        String regionCode,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static ClientCatalogRegionConfigResponse from(ClientCatalogRegionConfig config) {
        return new ClientCatalogRegionConfigResponse(
                config.getId(),
                config.getRegionCode(),
                config.isEnabled(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
