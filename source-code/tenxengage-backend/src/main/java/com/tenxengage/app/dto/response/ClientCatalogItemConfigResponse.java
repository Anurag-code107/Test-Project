package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClientCatalogItemConfigResponse(
        UUID id,
        UUID clientId,
        UUID redemptionCatalogItemId,
        boolean enabled,
        RedemptionProcessingMode processingModeOverride,
        BigDecimal minTransactionAmountOverride,
        BigDecimal maxTransactionAmountOverride,
        BigDecimal minWalletBalanceOverride,
        Integer returnWindowDaysOverride,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static ClientCatalogItemConfigResponse from(ClientCatalogItemConfig config) {
        return new ClientCatalogItemConfigResponse(
                config.getId(),
                config.getClientId(),
                config.getRedemptionCatalogItemId(),
                config.isEnabled(),
                config.getProcessingModeOverride(),
                config.getMinTransactionAmountOverride(),
                config.getMaxTransactionAmountOverride(),
                config.getMinWalletBalanceOverride(),
                config.getReturnWindowDaysOverride(),
                config.getVersion(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
