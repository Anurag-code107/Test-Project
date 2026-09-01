package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "redemption_catalog_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RedemptionCatalogItem extends BaseEntity {

    /**
     * Owning client (Model 2, client-owned catalog). Every item belongs to exactly one client;
     * a client only ever sees/manages its own items. Stamped from the caller's tenant at create.
     */
    @Column(name = "owner_client_id", nullable = false)
    private UUID ownerClientId;

    /**
     * Soft-delete flag. Deleted items are excluded from the catalog admin list and seller browse,
     * but the row is kept so historical redemptions (which reference catalog_item_id) still resolve
     * the item name. Never hard-deleted.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Reserved per-client "Bank Transfer" card. When true this item is the hidden vehicle for the
     * bank-transfer payout rail: excluded from the seller browse + client-admin list, redeemable
     * only via the dedicated bank-transfer endpoint, and dispatched on the BANK rail. At most one
     * live row per client (partial unique index). Normal items and gift cards are false.
     */
    @Column(name = "is_bank_transfer", nullable = false)
    @Builder.Default
    private boolean isBankTransfer = false;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RedemptionCategory category;

    @Column(name = "currency_id", nullable = false, length = 50)
    private String currencyId;

    @Column(name = "default_min_redemption_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal defaultMinRedemptionAmount;

    /**
     * VARIABLE (open-value within [min,max]) or FIXED (single denomination; min == max == faceValue),
     * derived from the XTRM gift-card SKU at create time. Null on legacy items and the bank-transfer card
     * (treated as open-value with the min floor, no ceiling).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", length = 20)
    private RedemptionValueType valueType;

    /** Upper redemption bound for VARIABLE gift cards (== faceValue for FIXED). Null = no ceiling. */
    @Column(name = "default_max_redemption_amount", precision = 18, scale = 2)
    private BigDecimal defaultMaxRedemptionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_processing_mode", nullable = false, length = 30)
    @Builder.Default
    private RedemptionProcessingMode defaultProcessingMode = RedemptionProcessingMode.INSTANT;

    @Column(name = "geographic_scope", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private String[] geographicScope = new String[0];

    @Column(name = "provider_item_id", length = 255)
    private String providerItemId;

    @Column(name = "is_returnable", nullable = false)
    @Builder.Default
    private boolean isReturnable = false;

    @Column(name = "default_return_window_days", nullable = false)
    @Builder.Default
    private int defaultReturnWindowDays = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "xoxoday_last_synced_at")
    private Instant xoxodayLastSyncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String syncMetadata;

    @Column(name = "image_url", length = 2000)
    private String imageUrl;

    /**
     * Brand image from the vendor gift-card SKU, stamped at create time (and re-stamped when the SKU
     * changes). Shown on the card when the client admin uploaded no image of their own; null for
     * NON_CASH, unknown SKUs and legacy items, which fall back to the inline SVG illustration.
     */
    @Column(name = "provider_image_url", length = 2000)
    private String providerImageUrl;
}
