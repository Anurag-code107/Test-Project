package com.tenxengage.app.service.forecast;

import com.tenxengage.app.entity.ForecastAccuracyRecord;
import com.tenxengage.app.entity.ForecastIncentiveOutcome;
import com.tenxengage.app.entity.IncentiveForecast;
import com.tenxengage.app.repository.ForecastAccuracyRepository;
import com.tenxengage.app.repository.ForecastIncentiveOutcomeRepository;
import com.tenxengage.app.repository.IncentiveForecastRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ForecastAccuracyService {

    private static final Logger log = LoggerFactory.getLogger(ForecastAccuracyService.class);

    private final ForecastAccuracyRepository accuracyRepo;
    private final ForecastIncentiveOutcomeRepository outcomeRepo;
    private final IncentiveForecastRepository forecastRepo;

    public ForecastAccuracyService(ForecastAccuracyRepository accuracyRepo,
                                    ForecastIncentiveOutcomeRepository outcomeRepo,
                                    IncentiveForecastRepository forecastRepo) {
        this.accuracyRepo = accuracyRepo;
        this.outcomeRepo = outcomeRepo;
        this.forecastRepo = forecastRepo;
    }

    @Transactional
    public void evaluateForClient(UUID clientId) {
        List<ForecastIncentiveOutcome> outcomes = outcomeRepo.findByClientId(clientId);
        int evaluated = 0;

        for (ForecastIncentiveOutcome outcome : outcomes) {
            // Find the forecast for this incentive
            var forecastOpt = forecastRepo.findTopByIncentiveIdOrderByGeneratedAtDesc(
                    outcome.getIncentiveId());
            if (forecastOpt.isEmpty()) continue;

            IncentiveForecast forecast = forecastOpt.get();

            // Skip if already evaluated
            if (accuracyRepo.existsByIncentiveIdAndForecastId(outcome.getIncentiveId(), forecast.getId())) {
                continue;
            }

            // Skip if no actual data yet
            if (outcome.getActualRevenue() == null || outcome.getActualCost() == null) continue;

            ForecastAccuracyRecord record = buildAccuracyRecord(clientId, outcome, forecast);
            accuracyRepo.save(record);
            evaluated++;
        }

        if (evaluated > 0) {
            log.info("Evaluated forecast accuracy for {} incentives for client {}", evaluated, clientId);
        }
    }

    private ForecastAccuracyRecord buildAccuracyRecord(UUID clientId,
                                                        ForecastIncentiveOutcome outcome,
                                                        IncentiveForecast forecast) {

        BigDecimal predictedBookings = forecast.getEstimatedNetNewBookings() != null
                ? forecast.getEstimatedNetNewBookings() : BigDecimal.ZERO;
        BigDecimal actualBookings = outcome.getActualRevenue() != null
                ? outcome.getActualRevenue() : BigDecimal.ZERO;

        BigDecimal predictedRoi = forecast.getEstimatedRoi() != null
                ? forecast.getEstimatedRoi() : BigDecimal.ZERO;
        BigDecimal actualRoi = outcome.getActualRoi() != null
                ? outcome.getActualRoi() : BigDecimal.ZERO;

        BigDecimal predictedPartRate = forecast.getEstimatedParticipationRate() != null
                ? forecast.getEstimatedParticipationRate() : BigDecimal.ZERO;
        BigDecimal actualPartRate = outcome.getActualParticipationRate() != null
                ? outcome.getActualParticipationRate() : BigDecimal.ZERO;

        // Budget utilization is not stored separately in IncentiveForecast;
        // derive from cost/budget: utilization = estimatedTotalCost / totalBudget * 100
        BigDecimal predictedUtilPct = BigDecimal.ZERO;
        BigDecimal actualUtilPct = outcome.getActualUtilizationRate() != null
                ? outcome.getActualUtilizationRate() : BigDecimal.ZERO;

        // Compute MAPE (Mean Absolute Percentage Error) for each metric
        BigDecimal bookingsError = computeMAPE(predictedBookings, actualBookings);
        BigDecimal roiError = computeMAPE(predictedRoi, actualRoi);
        BigDecimal participationError = computeMAPE(predictedPartRate, actualPartRate);

        // Overall accuracy = 100 - weighted MAPE
        // Weights: bookings 40%, ROI 30%, participation 30%
        BigDecimal weightedMAPE = bookingsError.multiply(new BigDecimal("0.40"))
                .add(roiError.multiply(new BigDecimal("0.30")))
                .add(participationError.multiply(new BigDecimal("0.30")));
        BigDecimal overallAccuracy = BigDecimal.valueOf(100).subtract(weightedMAPE)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return ForecastAccuracyRecord.builder()
                .clientId(clientId)
                .incentiveId(outcome.getIncentiveId())
                .forecastId(forecast.getId())
                .predictedRoi(predictedRoi)
                .actualRoi(actualRoi)
                .predictedNetNewDeals(forecast.getEstimatedNetNewDeals())
                .actualNetNewDeals(null) // computed from actual bookings / avg deal size
                .predictedNetNewBookings(predictedBookings)
                .actualNetNewBookings(actualBookings)
                .predictedParticipationRate(predictedPartRate)
                .actualParticipationRate(actualPartRate)
                .predictedBudgetUtilPct(predictedUtilPct)
                .actualBudgetUtilPct(actualUtilPct)
                .bookingsErrorPct(bookingsError)
                .roiErrorPct(roiError)
                .participationErrorPct(participationError)
                .overallAccuracyScore(overallAccuracy)
                .modelVersion(forecast.getModelVersion())
                .evaluatedAt(Instant.now())
                .build();
    }

    private BigDecimal computeMAPE(BigDecimal predicted, BigDecimal actual) {
        if (actual.compareTo(BigDecimal.ZERO) == 0) {
            return predicted.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return predicted.subtract(actual).abs()
                .divide(actual.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get accuracy summary for a client to include in forecast context.
     */
    public ForecastAccuracySummary getAccuracySummary(UUID clientId) {
        List<ForecastAccuracyRecord> records = accuracyRepo.findByClientId(clientId);
        if (records.size() < 3) {
            // Minimum 3 records for statistically meaningful accuracy feedback
            return null;
        }

        BigDecimal avgBookingsError = records.stream()
                .map(ForecastAccuracyRecord::getBookingsErrorPct)
                .filter(e -> e != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(records.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgOverallAccuracy = records.stream()
                .map(ForecastAccuracyRecord::getOverallAccuracyScore)
                .filter(e -> e != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(records.size()), 2, RoundingMode.HALF_UP);

        return new ForecastAccuracySummary(avgBookingsError, avgOverallAccuracy, records.size());
    }

    public record ForecastAccuracySummary(
        BigDecimal avgBookingsErrorPct,
        BigDecimal avgOverallAccuracy,
        int sampleSize
    ) {}
}
