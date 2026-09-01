package com.tenxengage.app.service.redemption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Component
public class AnalyticsMvRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsMvRefreshScheduler.class);

    static final List<String> MV_NAMES = List.of(
            "mv_item_redemption_breakdown",
            "mv_segment_redemption_breakdown",
            "mv_time_to_first_redemption",
            "mv_redemption_rate_trend",
            "mv_failure_mode_breakdown"
    );

    private static final String REFRESH_LOG_UPSERT =
            "INSERT INTO analytics_mv_refresh_log (id, mv_name, last_refreshed_at, duration_ms) " +
            "VALUES (gen_random_uuid(), ?, NOW(), ?) " +
            "ON CONFLICT (mv_name) DO UPDATE " +
            "  SET last_refreshed_at = EXCLUDED.last_refreshed_at, " +
            "      duration_ms       = EXCLUDED.duration_ms";

    private static final String LIABILITY_SNAPSHOT_UPSERT =
            "INSERT INTO mv_liability_trend (client_id, period_date, currency_type, total_unredeemed_balance) " +
            "SELECT client_id, " +
            "       DATE_TRUNC('day', NOW() AT TIME ZONE 'UTC')::DATE, " +
            "       currency_id, " +
            "       SUM(available_balance + reserved_balance) " +
            "FROM reward_wallets " +
            "GROUP BY client_id, currency_id " +
            "ON CONFLICT (client_id, period_date, currency_type) DO UPDATE " +
            "  SET total_unredeemed_balance = EXCLUDED.total_unredeemed_balance";

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsMvRefreshScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelay = 900_000)
    public void refreshAllMvs() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = MV_NAMES.stream()
                    .map(mvName -> CompletableFuture.runAsync(() -> refreshMv(mvName), executor))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        insertLiabilitySnapshot();
    }

    void refreshMv(String mvName) {
        long start = System.currentTimeMillis();
        try {
            // Plain (non-concurrent) REFRESH. CONCURRENTLY requires a UNIQUE index built
            // from column names only — not partial, not expression. Four of the five MVs key
            // on COALESCE(region,'') / COALESCE(role,'') expression indexes (needed because
            // region/role are nullable), which disqualifies them; CONCURRENTLY would throw
            // "cannot refresh ... concurrently" and be swallowed below, so those MVs would
            // never refresh. A plain REFRESH takes a brief ACCESS EXCLUSIVE lock — acceptable
            // for these small analytics aggregates on a 15-minute background cadence.
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW " + mvName);
            long durationMs = System.currentTimeMillis() - start;
            jdbcTemplate.update(REFRESH_LOG_UPSERT, mvName, durationMs);
        } catch (DataAccessException e) {
            log.warn("step=mv_refresh_failed mv_name={}", mvName, e);
        }
    }

    void insertLiabilitySnapshot() {
        jdbcTemplate.update(LIABILITY_SNAPSHOT_UPSERT);
    }
}
