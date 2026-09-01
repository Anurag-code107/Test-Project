package com.tenxengage.app.batch.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads/writes the seed_state table to track seeding progress across restarts.
 * Determines whether to perform a FULL reseed, INCREMENTAL update, or SKIP.
 */
@Component
public class SeedStateTracker {

    private static final Logger log = LoggerFactory.getLogger(SeedStateTracker.class);

    /** Bump this when seeding algorithm changes incompatibly to force full reseed. */
    static final String CURRENT_SEED_VERSION = "5";

    private final JdbcTemplate jdbc;

    public SeedStateTracker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public enum SeedMode { FULL, INCREMENTAL, SKIP }

    /** Load all seed_state entries for a client. */
    public Map<String, String> loadState(UUID clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT state_key, state_value FROM seed_state WHERE client_id = ?", clientId);
        Map<String, String> state = new HashMap<>();
        for (Map<String, Object> row : rows) {
            state.put((String) row.get("state_key"), (String) row.get("state_value"));
        }
        return state;
    }

    /** Write or update a single state key. */
    public void saveState(UUID clientId, String key, String value) {
        jdbc.update(
                "INSERT INTO seed_state (client_id, state_key, state_value, updated_at) " +
                "VALUES (?, ?, ?, now()) " +
                "ON CONFLICT (client_id, state_key) DO UPDATE SET state_value = EXCLUDED.state_value, updated_at = now()",
                clientId, key, value);
    }

    /** Determine whether to FULL reseed, INCREMENTAL update, or SKIP entirely. */
    public SeedMode determineSeedMode(UUID clientId) {
        // Check if seed_state table exists (might not on first run before V4)
        try {
            Integer tableExists = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'seed_state'",
                    Integer.class);
            if (tableExists == null || tableExists == 0) {
                return SeedMode.FULL;
            }
        } catch (Exception e) {
            return SeedMode.FULL;
        }

        Map<String, String> state = loadState(clientId);
        if (state.isEmpty()) {
            log.info("No seed state found — full reseed required");
            return SeedMode.FULL;
        }

        String seedVersion = state.get("seed.seed_version");
        if (!CURRENT_SEED_VERSION.equals(seedVersion)) {
            log.info("Seed version changed ({} → {}) — full reseed required", seedVersion, CURRENT_SEED_VERSION);
            return SeedMode.FULL;
        }

        String lastDateStr = state.get("seed.last_seeded_date");
        if (lastDateStr == null) {
            log.info("No last seeded date — full reseed required");
            return SeedMode.FULL;
        }

        LocalDate lastSeeded = LocalDate.parse(lastDateStr);
        LocalDate today = LocalDate.now();

        if (!lastSeeded.isBefore(today)) {
            log.info("Already seeded through {} — skipping", lastSeeded);
            return SeedMode.SKIP;
        }

        log.info("Last seeded through {} — incremental seed to {}", lastSeeded, today);
        return SeedMode.INCREMENTAL;
    }

    public LocalDate getLastSeededDate(UUID clientId) {
        Map<String, String> state = loadState(clientId);
        String dateStr = state.get("seed.last_seeded_date");
        return dateStr != null ? LocalDate.parse(dateStr) : null;
    }

    public LocalDate getIncrementalStartDate(UUID clientId) {
        LocalDate lastSeeded = getLastSeededDate(clientId);
        return lastSeeded != null ? lastSeeded.plusDays(1) : FiscalQuarterCalculator.getSeedStartDate();
    }

    /** Save all state after a successful seed run. */
    public void recordSeedCompletion(UUID clientId, LocalDate endDate, String mode, int partnerCount) {
        saveState(clientId, "seed.last_seeded_date", endDate.toString());
        saveState(clientId, "seed.last_seeded_quarter",
                FiscalQuarterCalculator.quarterContaining(endDate).displayName());
        saveState(clientId, "seed.partner_count", String.valueOf(partnerCount));
        saveState(clientId, "seed.seed_version", CURRENT_SEED_VERSION);
        saveState(clientId, "seed.mode", mode);
    }
}
