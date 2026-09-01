package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A gift card an admin can distribute.
 *
 * <p>Deliberately not the personal store's browse shape. That one computes {@code canAfford} against the
 * caller's <b>own</b> wallet, which is wrong here — the money comes from the company wallet, and affordability
 * depends on the amount and recipient count the admin has not chosen yet. The Distribution Store shows the
 * remaining company balance in its own header instead.</p>
 *
 * <p>{@code minAmount}/{@code maxAmount} are the <b>effective</b> bounds, with any client override already
 * applied, so the field the admin types into is constrained by exactly what submit will enforce. A
 * {@code FIXED} value type means min == max — the denomination is pinned and the amount field should be
 * read-only.</p>
 */
public record DistributionCatalogItemResponse(
        UUID id,
        String name,
        String description,
        /** Uploaded image, served through the API proxy. Null when the admin uploaded none. */
        String imageUrl,
        /** Vendor brand image, used when there is no uploaded one. */
        String providerImageUrl,
        String currencyId,
        /** FIXED (min == max, pinned denomination) or VARIABLE (a window). */
        String valueType,
        BigDecimal minAmount,
        /** Null means open-value: no ceiling on this item. */
        BigDecimal maxAmount
) {
}
