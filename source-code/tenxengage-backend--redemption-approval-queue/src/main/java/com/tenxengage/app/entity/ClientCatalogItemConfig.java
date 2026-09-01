package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "client_catalog_item_configs")
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientCatalogItemConfig extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "redemption_catalog_item_id", nullable = false)
    private UUID redemptionCatalogItemId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode_override", length = 30)
    private RedemptionProcessingMode processingModeOverride;

    @Column(name = "min_transaction_amount_override", precision = 18, scale = 2)
    private BigDecimal minTransactionAmountOverride;

    @Column(name = "min_wallet_balance_override", precision = 18, scale = 2)
    private BigDecimal minWalletBalanceOverride;

    @Column(name = "return_window_days_override")
    private Integer returnWindowDaysOverride;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
