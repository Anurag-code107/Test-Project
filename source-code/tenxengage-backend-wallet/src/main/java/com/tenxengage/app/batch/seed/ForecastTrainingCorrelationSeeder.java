package com.tenxengage.app.batch.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds forecast_training_correlations with plausible per-product-category lift
 * figures so the recommendation scoring service has real training-lift data to
 * reference (vs. falling back to a hardcoded 15% default) and the AI Insight
 * service can cite "trained sellers see N% higher deal sizes in [category]".
 *
 * One row per product category in SeedConstants.PRODUCT_CATALOG per client.
 * Deterministic — uses a fixed seed so repeated runs produce the same lift
 * values, keeping recommendation scores stable across reseeds.
 */
@Component
public class ForecastTrainingCorrelationSeeder {

    private static final Logger log = LoggerFactory.getLogger(ForecastTrainingCorrelationSeeder.class);

    private static final long RANDOM_SEED = 4242L;

    private final JdbcTemplate jdbc;

    public ForecastTrainingCorrelationSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One row per product category for the given client. Idempotent via ON CONFLICT. */
    public void seedForecastCorrelations(UUID clientId) {
        Timestamp now = Timestamp.from(Instant.now());
        Random random = new Random(RANDOM_SEED);

        List<Object[]> batch = new ArrayList<>(SeedConstants.PRODUCT_CATALOG.size());

        for (String productCategory : SeedConstants.PRODUCT_CATALOG.keySet()) {
            int trainedCount = 20 + random.nextInt(21);      // 20-40
            int untrainedCount = 30 + random.nextInt(31);    // 30-60

            double[] priceRange = SeedConstants.PRICE_RANGES.get(productCategory);
            double midPrice = priceRange != null ? (priceRange[0] + priceRange[1]) / 2.0 : 15000.0;

            // Trained sellers close 10-25% higher than untrained baseline
            double liftPct = 10.0 + random.nextDouble() * 15.0;
            BigDecimal untrainedAvgDeal = BigDecimal.valueOf(midPrice * (0.9 + random.nextDouble() * 0.2))
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal trainedAvgDeal = untrainedAvgDeal
                    .multiply(BigDecimal.valueOf(1.0 + liftPct / 100.0))
                    .setScale(0, RoundingMode.HALF_UP);
            int untrainedDeals = 6 + random.nextInt(7);      // 6-12
            int trainedDeals = untrainedDeals + 2 + random.nextInt(4); // +2..+5

            BigDecimal dataDrivenLift = BigDecimal.valueOf(liftPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal organicLift = dataDrivenLift.subtract(new BigDecimal("3.00"));
            BigDecimal incentiveLift = dataDrivenLift.add(new BigDecimal("4.00"));
            int sampleSize = trainedCount + untrainedCount;

            batch.add(new Object[]{
                    UUID.randomUUID(),
                    clientId,
                    productCategory,
                    trainedCount,
                    untrainedCount,
                    trainedAvgDeal,
                    untrainedAvgDeal,
                    trainedDeals,
                    untrainedDeals,
                    dataDrivenLift,
                    organicLift,
                    incentiveLift,
                    sampleSize,
                    now,
                    now
            });
        }

        int[] result = jdbc.batchUpdate(
                "INSERT INTO forecast_training_correlations " +
                "(id, client_id, product_category, " +
                "trained_seller_count, untrained_seller_count, " +
                "trained_avg_deal_size, untrained_avg_deal_size, " +
                "trained_avg_deal_count, untrained_avg_deal_count, " +
                "data_driven_lift_pct, organic_training_lift_pct, incentive_training_lift_pct, " +
                "sample_size, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (client_id, product_category) DO NOTHING",
                batch);

        int inserted = 0;
        for (int r : result) if (r > 0) inserted++;
        log.info("Forecast training correlation seed for client {}: {} rows (of {})",
                clientId, inserted, batch.size());
    }
}
