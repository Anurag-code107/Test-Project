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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "forecast_sales_aggregates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ForecastSalesAggregate extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "location_value_id")
    private UUID locationValueId;

    @Column(name = "product_category", length = 100)
    private String productCategory;

    @Column(name = "year_month", nullable = false)
    private LocalDate yearMonth;

    @Column(name = "deal_count", nullable = false)
    @Builder.Default
    private Integer dealCount = 0;

    @Column(name = "total_revenue", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "avg_deal_size", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal avgDealSize = BigDecimal.ZERO;

    @Column(name = "unique_partners", nullable = false)
    @Builder.Default
    private Integer uniquePartners = 0;
}
