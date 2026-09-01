package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.math.BigDecimal;
import java.util.UUID;

public final class ClientCatalogItemConfigFixtures {

    private ClientCatalogItemConfigFixtures() {
    }

    public static ClientCatalogItemConfig.ClientCatalogItemConfigBuilder enabledConfig(UUID clientId, UUID catalogItemId) {
        return ClientCatalogItemConfig.builder()
                .clientId(clientId)
                .redemptionCatalogItemId(catalogItemId)
                .enabled(true);
    }

    public static ClientCatalogItemConfig.ClientCatalogItemConfigBuilder disabledConfig(UUID clientId, UUID catalogItemId) {
        return ClientCatalogItemConfig.builder()
                .clientId(clientId)
                .redemptionCatalogItemId(catalogItemId)
                .enabled(false);
    }

    public static ClientCatalogItemConfig.ClientCatalogItemConfigBuilder enabledConfigWithOverrides(UUID clientId, UUID catalogItemId) {
        return ClientCatalogItemConfig.builder()
                .clientId(clientId)
                .redemptionCatalogItemId(catalogItemId)
                .enabled(true)
                .processingModeOverride(RedemptionProcessingMode.APPROVAL_REQUIRED)
                .minTransactionAmountOverride(new BigDecimal("25.00"))
                .minWalletBalanceOverride(new BigDecimal("50.00"))
                .returnWindowDaysOverride(14);
    }

    public static ClientCatalogItemConfig.ClientCatalogItemConfigBuilder enabledConfigWithProcessingOverride(
            UUID clientId, UUID catalogItemId, RedemptionProcessingMode mode) {
        return ClientCatalogItemConfig.builder()
                .clientId(clientId)
                .redemptionCatalogItemId(catalogItemId)
                .enabled(true)
                .processingModeOverride(mode);
    }
}
