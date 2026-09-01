package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
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
import java.time.Instant;

@Entity
@Table(name = "redemption_catalog_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RedemptionCatalogItem extends BaseEntity {

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
}
