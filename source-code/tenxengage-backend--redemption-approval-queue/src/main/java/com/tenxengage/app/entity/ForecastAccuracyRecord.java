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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forecast_accuracy_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ForecastAccuracyRecord extends BaseEntity {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "incentive_id", nullable = false)
    private UUID incentiveId;

    @Column(name = "forecast_id", nullable = false)
    private UUID forecastId;

    @Column(name = "predicted_roi", precision = 10, scale = 2)
    private BigDecimal predictedRoi;

    @Column(name = "actual_roi", precision = 10, scale = 2)
    private BigDecimal actualRoi;

    @Column(name = "predicted_net_new_deals")
    private Integer predictedNetNewDeals;

    @Column(name = "actual_net_new_deals")
    private Integer actualNetNewDeals;

    @Column(name = "predicted_net_new_bookings", precision = 15, scale = 2)
    private BigDecimal predictedNetNewBookings;

    @Column(name = "actual_net_new_bookings", precision = 15, scale = 2)
    private BigDecimal actualNetNewBookings;

    @Column(name = "predicted_participation_rate", precision = 5, scale = 2)
    private BigDecimal predictedParticipationRate;

    @Column(name = "actual_participation_rate", precision = 5, scale = 2)
    private BigDecimal actualParticipationRate;

    @Column(name = "predicted_budget_util_pct", precision = 5, scale = 2)
    private BigDecimal predictedBudgetUtilPct;

    @Column(name = "actual_budget_util_pct", precision = 5, scale = 2)
    private BigDecimal actualBudgetUtilPct;

    @Column(name = "bookings_error_pct", precision = 10, scale = 2)
    private BigDecimal bookingsErrorPct;

    @Column(name = "roi_error_pct", precision = 10, scale = 2)
    private BigDecimal roiErrorPct;

    @Column(name = "participation_error_pct", precision = 10, scale = 2)
    private BigDecimal participationErrorPct;

    @Column(name = "overall_accuracy_score", precision = 5, scale = 2)
    private BigDecimal overallAccuracyScore;

    @Column(name = "model_version", length = 20)
    private String modelVersion;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;
}
