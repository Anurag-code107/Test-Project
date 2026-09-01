package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RedemptionCatalogItemResponse(
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
        boolean isActive,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static RedemptionCatalogItemResponse from(RedemptionCatalogItem item) {
        return new RedemptionCatalogItemResponse(
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
                item.getImageUrl(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
