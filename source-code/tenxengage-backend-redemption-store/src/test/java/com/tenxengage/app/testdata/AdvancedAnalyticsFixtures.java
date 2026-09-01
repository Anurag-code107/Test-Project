package com.tenxengage.app.testdata;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Test fixtures for advanced analytics tables created by V28.
 * <p>
 * Five of the six analytics "tables" are PostgreSQL MATERIALIZED VIEWs and are
 * read-only in a live DB.  Integration test suites that need rows in them must
 * either (a) populate the underlying source tables and call
 * {@code REFRESH MATERIALIZED VIEW}, or (b) use a test-profile Flyway overlay
 * that replaces the MVs with regular tables.  The INSERT SQL below is correct
 * for option (b) and is used in unit smoke tests via a mocked JdbcTemplate.
 * <p>
 * {@code mv_liability_trend} and {@code analytics_mv_refresh_log} are regular
 * tables and can be inserted into directly in any context.
 * <p>
 * Schema note: V28 {@code analytics_mv_refresh_log} has no {@code refresh_status}
 * column (spec divergence).  Actual columns: mv_name, last_refreshed_at,
 * duration_ms, created_at.  {@link #upsertRefreshLog} omits the status param
 * until V28 is patched or a follow-up migration adds the column.
 */
@Component
public class AdvancedAnalyticsFixtures {

    private final JdbcTemplate jdbc;

    public AdvancedAnalyticsFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> insertItemBreakdownRow(
            UUID clientId, UUID catalogItemId, String catalogItemName,
            String currencyType, String region, String processingMode,
            LocalDate periodDate, long totalRedeemedCount,
            BigDecimal totalRedeemedAmount, BigDecimal redemptionRate,
            long failedCount, long cancelledCount) {
        jdbc.update("""
                INSERT INTO mv_item_redemption_breakdown
                    (client_id, catalog_item_id, catalog_item_name, currency_type,
                     region, processing_mode, period_date, total_redeemed_count,
                     total_redeemed_amount, redemption_rate, failed_count, cancelled_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                clientId, catalogItemId, catalogItemName, currencyType,
                region, processingMode, periodDate, totalRedeemedCount,
                totalRedeemedAmount, redemptionRate, failedCount, cancelledCount);
        return Map.of(
                "client_id", clientId,
                "catalog_item_id", catalogItemId,
                "currency_type", currencyType,
                "period_date", periodDate);
    }

    public Map<String, Object> insertSegmentBreakdownRow(
            UUID clientId, String region, String role, String currencyType,
            LocalDate periodDate, long totalRedeemedCount,
            BigDecimal totalRedeemedAmount, BigDecimal redemptionRate) {
        jdbc.update("""
                INSERT INTO mv_segment_redemption_breakdown
                    (client_id, region, role, currency_type, period_date,
                     total_redeemed_count, total_redeemed_amount, redemption_rate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                clientId, region, role, currencyType, periodDate,
                totalRedeemedCount, totalRedeemedAmount, redemptionRate);
        return Map.of(
                "client_id", clientId,
                "currency_type", currencyType,
                "period_date", periodDate);
    }

    public Map<String, Object> insertTimeToFirstRedemptionRow(
            UUID clientId, String region, LocalDate firstRedemptionDate,
            Double avgHours, Double medianHours, Double sumHours, long sampleCount) {
        jdbc.update("""
                INSERT INTO mv_time_to_first_redemption
                    (client_id, region, first_redemption_date,
                     avg_hours_to_first_redemption, median_hours_to_first_redemption,
                     sum_hours_to_first_redemption, sample_count)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                clientId, region, firstRedemptionDate,
                avgHours, medianHours, sumHours, sampleCount);
        return Map.of(
                "client_id", clientId,
                "first_redemption_date", firstRedemptionDate,
                "sample_count", sampleCount);
    }

    public Map<String, Object> insertRedemptionTrendRow(
            UUID clientId, LocalDate periodDate, String currencyType,
            long redeemedCount, BigDecimal redemptionRate) {
        jdbc.update("""
                INSERT INTO mv_redemption_rate_trend
                    (client_id, period_date, currency_type, redeemed_count, redemption_rate)
                VALUES (?, ?, ?, ?, ?)
                """,
                clientId, periodDate, currencyType, redeemedCount, redemptionRate);
        return Map.of(
                "client_id", clientId,
                "period_date", periodDate,
                "currency_type", currencyType);
    }

    public UUID insertLiabilityTrendRow(
            UUID clientId, LocalDate periodDate, String currencyType, BigDecimal balance) {
        return jdbc.queryForObject("""
                INSERT INTO mv_liability_trend
                    (client_id, period_date, currency_type, total_unredeemed_balance)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (client_id, period_date, currency_type) DO UPDATE SET
                    total_unredeemed_balance = EXCLUDED.total_unredeemed_balance,
                    captured_at = NOW()
                RETURNING id
                """,
                UUID.class, clientId, periodDate, currencyType, balance);
    }

    public Map<String, Object> insertFailureBreakdownRow(
            UUID clientId, String processingMode, UUID catalogItemId,
            String catalogItemName, String currencyType, String region,
            LocalDate periodDate, long failedCount, long cancelledCount,
            long totalCount, BigDecimal failureRate) {
        jdbc.update("""
                INSERT INTO mv_failure_mode_breakdown
                    (client_id, processing_mode, catalog_item_id, catalog_item_name,
                     currency_type, region, period_date, failed_count, cancelled_count,
                     total_count, failure_rate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                clientId, processingMode, catalogItemId, catalogItemName,
                currencyType, region, periodDate, failedCount, cancelledCount,
                totalCount, failureRate);
        return Map.of(
                "client_id", clientId,
                "processing_mode", processingMode,
                "catalog_item_id", catalogItemId,
                "period_date", periodDate);
    }

    public UUID upsertRefreshLog(String mvName, Instant lastRefreshedAt, long durationMs) {
        return jdbc.queryForObject("""
                INSERT INTO analytics_mv_refresh_log (mv_name, last_refreshed_at, duration_ms)
                VALUES (?, ?, ?)
                ON CONFLICT (mv_name) DO UPDATE SET
                    last_refreshed_at = EXCLUDED.last_refreshed_at,
                    duration_ms = EXCLUDED.duration_ms
                RETURNING id
                """,
                UUID.class, mvName, lastRefreshedAt, durationMs);
    }
}
