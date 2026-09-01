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
@Table(name = "forecast_training_correlations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ForecastTrainingCorrelation extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "product_category", nullable = false, length = 100)
    private String productCategory;

    @Column(name = "trained_seller_count")
    private Integer trainedSellerCount;

    @Column(name = "untrained_seller_count")
    private Integer untrainedSellerCount;

    @Column(name = "trained_avg_deal_size", precision = 15, scale = 2)
    private BigDecimal trainedAvgDealSize;

    @Column(name = "untrained_avg_deal_size", precision = 15, scale = 2)
    private BigDecimal untrainedAvgDealSize;

    @Column(name = "trained_avg_deal_count")
    private Integer trainedAvgDealCount;

    @Column(name = "untrained_avg_deal_count")
    private Integer untrainedAvgDealCount;

    @Column(name = "data_driven_lift_pct", precision = 10, scale = 2)
    private BigDecimal dataDrivenLiftPct;

    @Column(name = "organic_training_lift_pct", precision = 10, scale = 2)
    private BigDecimal organicTrainingLiftPct;

    @Column(name = "incentive_training_lift_pct", precision = 10, scale = 2)
    private BigDecimal incentiveTrainingLiftPct;

    @Column(name = "sample_size")
    private Integer sampleSize;
}
