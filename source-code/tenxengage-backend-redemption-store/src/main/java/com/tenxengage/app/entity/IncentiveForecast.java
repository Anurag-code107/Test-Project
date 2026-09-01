package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "incentive_forecasts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IncentiveForecast extends BaseEntity {

    @Column(name = "incentive_id", nullable = false)
    private UUID incentiveId;

    @Column(name = "estimated_roi", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedRoi;

    @Column(name = "estimated_participation", nullable = false)
    private Integer estimatedParticipation;

    @Column(name = "estimated_participation_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal estimatedParticipationRate;

    @Column(name = "estimated_total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedTotalCost;

    @Column(name = "estimated_revenue", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedRevenue;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "monthly_projections", columnDefinition = "jsonb")
    private List<Map<String, Object>> monthlyProjections;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "estimated_net_new_deals")
    private Integer estimatedNetNewDeals;

    @Column(name = "estimated_net_new_bookings", precision = 15, scale = 2)
    private BigDecimal estimatedNetNewBookings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "location_breakdown", columnDefinition = "jsonb")
    private List<Map<String, Object>> locationBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "similar_incentive_ids", columnDefinition = "jsonb")
    private List<String> similarIncentiveIds;

    @Column(name = "ai_insights", columnDefinition = "TEXT")
    private String aiInsights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_level_insights", columnDefinition = "jsonb")
    private Map<String, Object> topLevelInsights;

    @Column(name = "model_version", length = 20)
    @Builder.Default
    private String modelVersion = "v1";

    @Column(name = "data_quality_score", precision = 5, scale = 2)
    private BigDecimal dataQualityScore;
}
