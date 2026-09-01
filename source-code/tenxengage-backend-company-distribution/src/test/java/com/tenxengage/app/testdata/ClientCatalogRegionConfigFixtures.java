package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.ClientCatalogRegionConfig;

import java.util.UUID;

public final class ClientCatalogRegionConfigFixtures {

    private ClientCatalogRegionConfigFixtures() {
    }

    public static ClientCatalogRegionConfig.ClientCatalogRegionConfigBuilder enabledRegion(
            UUID clientId, UUID catalogItemId, String regionCode) {
        return ClientCatalogRegionConfig.builder()
                .clientId(clientId)
                .redemptionCatalogItemId(catalogItemId)
                .regionCode(regionCode)
                .enabled(true);
    }

    public static ClientCatalogRegionConfig.ClientCatalogRegionConfigBuilder disabledRegion(
            UUID clientId, UUID catalogItemId, String regionCode) {
        return ClientCatalogRegionConfig.builder()
                .clientId(clientId)
                .redemptionCatalogItemId(catalogItemId)
                .regionCode(regionCode)
                .enabled(false);
    }

    public static ClientCatalogRegionConfig.ClientCatalogRegionConfigBuilder usRegion(UUID clientId, UUID catalogItemId) {
        return enabledRegion(clientId, catalogItemId, "US");
    }

    public static ClientCatalogRegionConfig.ClientCatalogRegionConfigBuilder inRegion(UUID clientId, UUID catalogItemId) {
        return enabledRegion(clientId, catalogItemId, "IN");
    }
}
