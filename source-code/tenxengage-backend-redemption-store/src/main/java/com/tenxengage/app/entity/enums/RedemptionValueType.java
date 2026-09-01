package com.tenxengage.app.entity.enums;

/**
 * How a catalog item's redemption amount is chosen, mirroring the XTRM gift-card {@code valueType}:
 * <ul>
 *   <li>{@link #FIXED} — a single fixed denomination (the SKU <b>is</b> the amount). min == max == faceValue.</li>
 *   <li>{@link #VARIABLE} — any amount within [min, max] (open-value).</li>
 * </ul>
 * Null on legacy items and the reserved bank-transfer card (treated as open-value with the min floor).
 */
public enum RedemptionValueType {
    FIXED,
    VARIABLE
}
