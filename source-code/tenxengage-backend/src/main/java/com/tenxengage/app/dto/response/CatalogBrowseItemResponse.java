package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record CatalogBrowseItemResponse(
        UUID id,
        String name,
        String description,
        String imageUrl,
        String providerImageUrl,
        RedemptionCategory category,
        String currencyId,
        BigDecimal effectiveMinTransactionAmount,
        String valueType,
        BigDecimal effectiveMaxTransactionAmount,
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

        // config is an OPTIONAL per-item override extension (may be null under the client-owned
        // model) — fall back to the item's own defaults when absent or unset.
        RedemptionProcessingMode effectiveMode = (config != null && config.getProcessingModeOverride() != null)
                ? config.getProcessingModeOverride()
                : item.getDefaultProcessingMode();

        BigDecimal effectiveMinTxAmount = (config != null && config.getMinTransactionAmountOverride() != null)
                ? config.getMinTransactionAmountOverride()
                : item.getDefaultMinRedemptionAmount();

        // Symmetric with the min: a client override narrows the vendor SKU's ceiling. Null on both
        // sides = open-value/legacy item with no ceiling at all.
        BigDecimal effectiveMaxTxAmount = (config != null && config.getMaxTransactionAmountOverride() != null)
                ? config.getMaxTransactionAmountOverride()
                : item.getDefaultMaxRedemptionAmount();

        BigDecimal effectiveMinWalletBalance = (config != null && config.getMinWalletBalanceOverride() != null)
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
                item.getImageUrl() != null ? "/api/v1/admin/redemption-catalog/" + item.getId() + "/image" : null,
                // Vendor brand image, used by the card when there is no uploaded image (or it fails to load).
                // Sent as-is: it's an absolute vendor CDN URL, not something this API proxies.
                item.getProviderImageUrl(),
                item.getCategory(),
                item.getCurrencyId(),
                effectiveMinTxAmount,
                item.getValueType() == null ? null : item.getValueType().name(),
                effectiveMaxTxAmount,
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
                // Show a friendly relative estimate ("Tomorrow") rather than a raw future date, which reads
                // as a longer wait than it is. DAILY → next day; WEEKLY → ~a week out.
                LocalDate today = LocalDate.now();
                LocalDate next = batchCadence == BatchCadence.DAILY
                        ? today.plusDays(1)
                        : today.plusWeeks(1);
                long days = ChronoUnit.DAYS.between(today, next);
                String when = days <= 0 ? "Today" : days == 1 ? "Tomorrow" : "in " + days + " days";
                yield "Processed in next batch — " + when;
            }
            case APPROVAL_REQUIRED -> "After admin approval + Available in minutes";
        };
    }
}
