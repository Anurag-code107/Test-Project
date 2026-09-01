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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "forecast_incentive_outcomes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ForecastIncentiveOutcome extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "incentive_id", nullable = false)
    private UUID incentiveId;

    @Column(name = "incentive_type", nullable = false, length = 20)
    private String incentiveType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "total_budget", precision = 15, scale = 2)
    private BigDecimal totalBudget;

    @Column(name = "actual_utilization_rate", precision = 5, scale = 2)
    private BigDecimal actualUtilizationRate;

    @Column(name = "actual_participation_count")
    private Integer actualParticipationCount;

    @Column(name = "actual_participation_rate", precision = 5, scale = 2)
    private BigDecimal actualParticipationRate;

    @Column(name = "actual_revenue", precision = 15, scale = 2)
    private BigDecimal actualRevenue;

    @Column(name = "actual_cost", precision = 15, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "actual_roi", precision = 10, scale = 2)
    private BigDecimal actualRoi;

    @Column(name = "product_categories", columnDefinition = "TEXT")
    private String productCategories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_location_value_ids", columnDefinition = "jsonb")
    private String targetLocationValueIds;

    @Column(name = "payout_type", length = 20)
    private String payoutType;

    @Column(name = "avg_payout_value", precision = 15, scale = 2)
    private BigDecimal avgPayoutValue;

    @Column(name = "partner_types", columnDefinition = "TEXT")
    private String partnerTypes;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "actual_lift_pct", precision = 10, scale = 2)
    private BigDecimal actualLiftPct;

    @Column(name = "claim_rate", precision = 5, scale = 2)
    private BigDecimal claimRate;

    @Column(name = "avg_days_to_claim")
    private Integer avgDaysToClaim;

    @Column(name = "budget_exhaustion_pct_at_midpoint", precision = 5, scale = 2)
    private BigDecimal budgetExhaustionPctAtMidpoint;
}
