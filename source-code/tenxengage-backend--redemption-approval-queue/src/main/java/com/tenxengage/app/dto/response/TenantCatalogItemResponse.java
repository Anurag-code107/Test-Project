package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantCatalogItemResponse(
        UUID id,
        String name,
        String description,
        RedemptionCategory category,
        String currencyId,
        BigDecimal defaultMinRedemptionAmount,
        RedemptionProcessingMode defaultProcessingMode,
        String[] geographicScope,
        String providerItemId,
        boolean isReturnable,
        int defaultReturnWindowDays,
        boolean isGloballyActive,
        // tenant overlay fields
        UUID configId,
        boolean enabled,
        RedemptionProcessingMode processingModeOverride,
        BigDecimal minTransactionAmountOverride,
        BigDecimal minWalletBalanceOverride,
        Integer returnWindowDaysOverride,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantCatalogItemResponse from(RedemptionCatalogItem item, ClientCatalogItemConfig config) {
        return new TenantCatalogItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getCategory(),
                item.getCurrencyId(),
                item.getDefaultMinRedemptionAmount(),
                item.getDefaultProcessingMode(),
                item.getGeographicScope(),
                item.getProviderItemId(),
                item.isReturnable(),
                item.getDefaultReturnWindowDays(),
                item.isActive(),
                config != null ? config.getId() : null,
                config != null && config.isEnabled(),
                config != null ? config.getProcessingModeOverride() : null,
                config != null ? config.getMinTransactionAmountOverride() : null,
                config != null ? config.getMinWalletBalanceOverride() : null,
                config != null ? config.getReturnWindowDaysOverride() : null,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
