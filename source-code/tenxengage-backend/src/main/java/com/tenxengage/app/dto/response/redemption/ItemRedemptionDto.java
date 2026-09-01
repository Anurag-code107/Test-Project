package com.tenxengage.app.dto.response.redemption;

import java.math.BigDecimal;

/**
 * Per-item redemption metrics for the item breakdown endpoint (FR-08.1).
 *
 * <p>Field names match the {@code redemption-advanced-analytics.yaml} contract exactly:
 * <ul>
 *   <li>{@code catalogItemId} — UUID as string (contract: {@code format: uuid})</li>
 *   <li>{@code currencyId} — platform currency identifier (not {@code currencyType})</li>
 *   <li>{@code totalRedeemedAmount} — BigDecimal string representation</li>
 *   <li>{@code redemptionRate} — percentage 0–100 (double)</li>
 * </ul>
 */
public record ItemRedemptionDto(
        String catalogItemId,
        String catalogItemName,
        String currencyId,
        long totalRedeemedCount,
        BigDecimal totalRedeemedAmount,
        double redemptionRate
) {
}
