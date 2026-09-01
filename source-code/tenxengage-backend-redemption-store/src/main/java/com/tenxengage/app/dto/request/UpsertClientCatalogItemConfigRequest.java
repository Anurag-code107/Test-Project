package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertClientCatalogItemConfigRequest(
        @NotNull Boolean enabled,
        RedemptionProcessingMode processingModeOverride,
        @DecimalMin("0.01") BigDecimal minTransactionAmountOverride,
        @DecimalMin("0.01") BigDecimal maxTransactionAmountOverride,
        @DecimalMin("0.00") BigDecimal minWalletBalanceOverride,
        @Min(0) Integer returnWindowDaysOverride
) {
}
