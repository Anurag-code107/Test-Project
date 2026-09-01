package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.JourneyCompletionExclusions;
import com.tenxengage.app.batch.seed.SeedRecords.SellerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds user incentive completion records for non-SALES incentives
 * (TRAINING, ACTIVITY, JOURNEY). Completion rates vary by incentive status:
 * INACTIVE incentives get 65% completion, active ones get 35%.
 */
@Component
public class CompletionSeeder {

    private static final Logger log = LoggerFactory.getLogger(CompletionSeeder.class);

    private final JdbcTemplate jdbc;

    public CompletionSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Queries existing non-SALES incentives and seeds user completion records.
     * Eligible users are determined by audience rules (location-based); falls back
     * to all sellers if no audience match is found.
     */
    public void seedUserCompletions(UUID clientId, List<SellerRef> sellers, Random random) {
        seedUserCompletions(clientId, sellers, random, JourneyCompletionExclusions.empty());
    }

    /**
     * Variant that honors a per-user skip-list so callers can guarantee specific
     * (user, incentive) pairs stay at zero random completions. Used by BUG-019
     * verification seeding: the current-quarter AMERICAS Journeys need deterministic
     * completion states for {@code seller@techpartners.com} and
     * {@code partneradmin@techpartners.com} — one Journey with zero completed stages,
     * one with exactly its pre-completed first stage.
     */
    public void seedUserCompletions(UUID clientId, List<SellerRef> sellers, Random random,
                                    JourneyCompletionExclusions exclusions) {
        Timestamp now = Timestamp.from(Instant.now());

        List<Map<String, Object>> nonSalesIncentives = jdbc.queryForList(
            "SELECT id, status, incentive_type FROM incentives " +
            "WHERE client_id = ? AND deleted = false " +
            "AND incentive_type IN ('TRAINING', 'ACTIVITY', 'JOURNEY')",
            clientId);

        int completionCount = 0;
        int skippedCount = 0;
        for (Map<String, Object> inc : nonSalesIncentives) {
            UUID incentiveId = (UUID) inc.get("id");
            String status = (String) inc.get("status");

            double completionRate = "INACTIVE".equals(status) ? 0.65 : 0.35;

            List<UUID> eligibleUserIds = jdbc.queryForList(
                "SELECT u.id FROM users u " +
                "JOIN partner_companies pc ON u.partner_company_id = pc.id " +
                "WHERE u.client_id = ? AND u.status = 'ACTIVE' " +
                "AND EXISTS (SELECT 1 FROM partner_company_locations pcl " +
                "JOIN incentive_audience_rules iar ON iar.rule_value = pcl.location_value_id::text " +
                "WHERE pcl.partner_company_id = pc.id AND iar.incentive_id = ? AND iar.rule_type = 'LOCATION')",
                UUID.class, clientId, incentiveId);

            if (eligibleUserIds.isEmpty()) {
                eligibleUserIds = sellers.stream().map(SellerRef::userId).toList();
            }

            for (UUID userId : eligibleUserIds) {
                if (exclusions.isExcluded(userId, incentiveId)) {
                    skippedCount++;
                    continue;
                }
                if (random.nextDouble() < completionRate) {
                    Timestamp completedAt = Timestamp.from(
                        Instant.now().minusSeconds(random.nextInt(90 * 24 * 3600)));
                    jdbc.update(
                        "INSERT INTO user_incentive_completions " +
                        "(id, client_id, incentive_id, user_id, completed_at, created_at) " +
                        "VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                        UUID.randomUUID(), clientId, incentiveId, userId, completedAt, now);
                    completionCount++;
                }
            }
        }
        log.info("Seeded {} user incentive completions across {} non-SALES incentives ({} skipped by exclusion list)",
            completionCount, nonSalesIncentives.size(), skippedCount);
    }
}
