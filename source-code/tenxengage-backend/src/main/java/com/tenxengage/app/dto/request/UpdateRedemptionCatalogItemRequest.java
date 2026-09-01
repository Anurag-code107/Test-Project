package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record UpdateRedemptionCatalogItemRequest(
        @Size(max = 255) String name,
        @Size(max = 2000) String description,
        RedemptionCategory category,
        String currencyId,
        @DecimalMin("0.01") BigDecimal defaultMinRedemptionAmount,
        RedemptionProcessingMode defaultProcessingMode,
        List<String> geographicScope,
        @Size(max = 255) String providerItemId,
        Boolean isReturnable,
        @Min(0) Integer defaultReturnWindowDays,
        Optional<String> imageUrl
) {
}
