package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "forecast_region_distributions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ForecastRegionDistribution extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "location_value_id")
    private UUID locationValueId;

    @Column(name = "active_partner_count", nullable = false)
    @Builder.Default
    private Integer activePartnerCount = 0;

    @Column(name = "trailing_12m_revenue", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal trailing12mRevenue = BigDecimal.ZERO;

    @Column(name = "trailing_12m_order_count", nullable = false)
    @Builder.Default
    private Integer trailing12mOrderCount = 0;

    @Column(name = "revenue_weight", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal revenueWeight = BigDecimal.ZERO;
}
