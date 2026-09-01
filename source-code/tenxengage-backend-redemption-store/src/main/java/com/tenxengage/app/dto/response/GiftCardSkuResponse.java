package com.tenxengage.app.dto.response;

import java.math.BigDecimal;

/**
 * One selectable XTRM digital gift-card SKU, surfaced to the client-admin catalog-creation picker.
 * A lean projection of the XTRM catalog (the huge HTML description/terms are dropped).
 *
 * <p>{@code valueType} is normalized to {@code FIXED} / {@code VARIABLE}. For FIXED the amount is
 * {@code faceValue}; for VARIABLE it is any value within [{@code minValue}, {@code maxValue}].</p>
 */
public record GiftCardSkuResponse(
        String sku,
        String rewardName,
        String brandName,
        String brandImageUrl,
        String currencyCode,
        String valueType,
        BigDecimal faceValue,
        BigDecimal minValue,
        BigDecimal maxValue
) {
}
