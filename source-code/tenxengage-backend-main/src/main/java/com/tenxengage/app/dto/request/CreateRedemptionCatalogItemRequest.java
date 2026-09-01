package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateRedemptionCatalogItemRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotNull RedemptionCategory category,
        @NotBlank String currencyId,
        @NotNull @DecimalMin("0.01") BigDecimal defaultMinRedemptionAmount,
        RedemptionProcessingMode defaultProcessingMode,
        List<String> geographicScope,
        @Size(max = 255) String providerItemId,
        boolean isReturnable,
        @Min(0) int defaultReturnWindowDays,
        @Size(max = 2000) String imageUrl
) {
}
