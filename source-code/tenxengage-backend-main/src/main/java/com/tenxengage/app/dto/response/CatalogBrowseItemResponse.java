package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CatalogBrowseItemResponse(
        UUID id,
        String name,
        String description,
        RedemptionCategory category,
        String currencyId,
        BigDecimal effectiveMinTransactionAmount,
        RedemptionProcessingMode effectiveProcessingMode,
        String estimatedPayoutTimeline,
        boolean canAfford,
        BigDecimal shortfallAmount,
        String[] geographicScope
) {
    public static CatalogBrowseItemResponse from(
            RedemptionCatalogItem item,
            ClientCatalogItemConfig config,
            BigDecimal walletBalance,
            BatchCadence batchCadence) {

        RedemptionProcessingMode effectiveMode = config.getProcessingModeOverride() != null
                ? config.getProcessingModeOverride()
                : item.getDefaultProcessingMode();

        BigDecimal effectiveMinTxAmount = config.getMinTransactionAmountOverride() != null
                ? config.getMinTransactionAmountOverride()
                : item.getDefaultMinRedemptionAmount();

        BigDecimal effectiveMinWalletBalance = config.getMinWalletBalanceOverride() != null
                ? config.getMinWalletBalanceOverride()
                : BigDecimal.ZERO;

        boolean canAfford = walletBalance.compareTo(effectiveMinWalletBalance) >= 0;
        BigDecimal shortfallAmount = canAfford
                ? BigDecimal.ZERO
                : effectiveMinWalletBalance.subtract(walletBalance);

        return new CatalogBrowseItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getCategory(),
                item.getCurrencyId(),
                effectiveMinTxAmount,
                effectiveMode,
                buildPayoutTimeline(effectiveMode, batchCadence),
                canAfford,
                shortfallAmount,
                item.getGeographicScope()
        );
    }

    private static String buildPayoutTimeline(RedemptionProcessingMode mode, BatchCadence batchCadence) {
        return switch (mode) {
            case INSTANT -> "Available in minutes";
            case BATCH -> {
                LocalDate next = batchCadence == BatchCadence.DAILY
                        ? LocalDate.now().plusDays(1)
                        : LocalDate.now().plusWeeks(1);
                yield "Processed in next batch — estimated " + next;
            }
            case APPROVAL_REQUIRED -> "After admin approval + Available in minutes";
        };
    }
}
