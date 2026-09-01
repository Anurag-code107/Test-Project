package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.PartnerLocationRef;
import com.tenxengage.app.batch.seed.SeedRecords.SellerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tenxengage.app.batch.seed.SeedConstants.BATCH_SIZE;
import static com.tenxengage.app.batch.seed.SeedConstants.MONTHLY_CLAIM_RATES;

/**
 * Creates claim actions, reward transactions, reward balances, and budget utilizations
 * from eligible purchase orders. Applies seasonal claim rates and per-region budget capping.
 */
@Component
public class ClaimSeeder {

    private static final Logger log = LoggerFactory.getLogger(ClaimSeeder.class);

    private static final LocalDate SEED_END_DATE = LocalDate.now();

    private final JdbcTemplate jdbc;

    public ClaimSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Creates claims with seasonal patterns, per-region budget capping, and reward balances.
     *
     * @param clientId           the client UUID
     * @param taggingJobId       the tagging job UUID from eligibility computation
     * @param sellers            list of seller references (userId + partnerId)
     * @param partnerRegion      map of partnerId to region name
     * @param userCreationDates  map of userId to creation timestamp (for enrollment-before-claim check)
     * @param partnerLocationMap map of partnerId to PartnerLocationRef (for resolving region to
     *                           location_value_id in budget_utilizations)
     * @param regionValueIds     map of region name to location_value UUID (for budget_utilizations
     *                           when partner-level location is unavailable)
     * @param random             shared Random instance for deterministic seeding
     */
    public void createClaims(UUID clientId, UUID taggingJobId, List<SellerRef> sellers,
                             Map<UUID, String> partnerRegion,
                             Map<UUID, Timestamp> userCreationDates,
                             Map<UUID, PartnerLocationRef> partnerLocationMap,
                             Map<String, UUID> regionValueIds,
                             java.util.Random random) {

        Map<UUID, List<UUID>> sellersByPartner = new HashMap<>();
        for (SellerRef s : sellers) {
            sellersByPartner.computeIfAbsent(s.partnerId(), k -> new ArrayList<>())
                    .add(s.userId());
        }

        // Load eligible POs with order dates for seasonal claim rates
        List<Map<String, Object>> eligiblePOs = jdbc.queryForList(
                "SELECT DISTINCT pem.purchase_order_id, po.partner_company_id, po.order_date " +
                        "FROM po_eligibility_mappings pem " +
                        "JOIN purchase_orders po ON po.id = pem.purchase_order_id " +
                        "WHERE pem.client_id = ? AND pem.eligible = true", clientId);

        // Pre-load payouts by PO
        Map<UUID, List<Map<String, Object>>> payoutsByPO = new HashMap<>();
        List<Map<String, Object>> allPayouts = jdbc.queryForList(
                "SELECT pem.purchase_order_id, pem.incentive_id, ep.currency_id, ep.payout_amount " +
                        "FROM eligibility_payouts ep " +
                        "JOIN po_eligibility_mappings pem ON pem.id = ep.eligibility_mapping_id " +
                        "WHERE pem.client_id = ? AND pem.eligible = true", clientId);
        for (Map<String, Object> p : allPayouts) {
            UUID poId = (UUID) p.get("purchase_order_id");
            payoutsByPO.computeIfAbsent(poId, k -> new ArrayList<>()).add(p);
        }

        // Load incentive budgets for per-region budget capping
        Map<String, BigDecimal> budgetRemaining = new HashMap<>();
        List<Map<String, Object>> budgets = jdbc.queryForList(
                "SELECT ib.id, ib.incentive_id, ib.currency_id, ib.total_budget, ib.budget_mode " +
                        "FROM incentive_budgets ib WHERE ib.incentive_id IN " +
                        "(SELECT id FROM incentives WHERE client_id = ? AND incentive_type = 'SALES')",
                clientId);
        for (Map<String, Object> b : budgets) {
            UUID budgetId = (UUID) b.get("id");
            UUID incentiveId = (UUID) b.get("incentive_id");
            String currencyId = (String) b.get("currency_id");
            String mode = (String) b.get("budget_mode");

            double ceiling = computeUtilizationCeiling(incentiveId, random);

            if ("PER_LOCATION".equals(mode)) {
                List<Map<String, Object>> allocs = jdbc.queryForList(
                        "SELECT lba.location_value_id, lba.amount, lv.name " +
                                "FROM location_budget_allocations lba " +
                                "JOIN location_values lv ON lv.id = lba.location_value_id " +
                                "WHERE lba.budget_id = ?", budgetId);
                for (Map<String, Object> a : allocs) {
                    String locName = (String) a.get("name");
                    BigDecimal cappedAmount = ((BigDecimal) a.get("amount"))
                            .multiply(BigDecimal.valueOf(ceiling))
                            .setScale(0, RoundingMode.HALF_UP);
                    budgetRemaining.put(incentiveId + ":" + currencyId + ":" + locName, cappedAmount);
                }
            } else {
                // Fallback: GLOBAL mode
                BigDecimal cappedAmount = ((BigDecimal) b.get("total_budget"))
                        .multiply(BigDecimal.valueOf(ceiling))
                        .setScale(0, RoundingMode.HALF_UP);
                budgetRemaining.put(incentiveId + ":" + currencyId + ":GLOBAL", cappedAmount);
            }
        }
        log.info("Loaded {} budget entries for per-region budget capping",
                budgetRemaining.size());

        // Track actual budget utilization
        Map<String, BigDecimal> budgetUtilized = new HashMap<>();

        int claimedCount = 0;
        int cappedCount = 0;
        int skippedCount = 0;
        int skippedNotEnrolled = 0;
        List<Object[]> actionBatch = new ArrayList<>();
        List<Object[]> txBatch = new ArrayList<>();
        Map<String, BigDecimal> balanceAccumulator = new HashMap<>();

        for (Map<String, Object> row : eligiblePOs) {
            UUID poId = (UUID) row.get("purchase_order_id");
            UUID partnerId = (UUID) row.get("partner_company_id");

            Object orderDateObj = row.get("order_date");
            int month = getMonthFromDate(orderDateObj);
            double claimRate = MONTHLY_CLAIM_RATES[month - 1];

            int year = getYearFromDate(orderDateObj);
            claimRate += 0.02 * (year - 2023);
            claimRate = Math.min(claimRate, 0.60);

            if (random.nextDouble() > claimRate) continue;

            List<Map<String, Object>> payouts = payoutsByPO.getOrDefault(poId, List.of());
            if (payouts.isEmpty()) continue;

            List<UUID> partnerSellers = sellersByPartner.getOrDefault(partnerId, List.of());
            if (partnerSellers.isEmpty()) continue;
            UUID primaryClaimerId = partnerSellers.get(
                    random.nextInt(partnerSellers.size()));

            List<UUID> claimerIds = new ArrayList<>();
            claimerIds.add(primaryClaimerId);

            int extraClaimers = random.nextDouble() < 0.40
                    ? (random.nextDouble() < 0.35 ? 2 : 1) : 0;
            List<UUID> otherSellers = new ArrayList<>(partnerSellers.stream()
                    .filter(id -> !id.equals(primaryClaimerId)).toList());
            Collections.shuffle(otherSellers, random);
            for (int e = 0; e < Math.min(extraClaimers, otherSellers.size()); e++) {
                claimerIds.add(otherSellers.get(e));
            }

            boolean poHadReward = false;

            for (UUID claimerId : claimerIds) {
                UUID actionId = UUID.randomUUID();
                LocalDate orderDate = localDateFromObj(orderDateObj);
                LocalDate claimDate = orderDate.plusDays(1 + random.nextInt(30));
                Timestamp claimedAt = Timestamp.from(
                        claimDate.atStartOfDay(ZoneOffset.UTC).toInstant());

                // Enrollment-before-claim: user must exist before the claim date
                Timestamp userCreated = userCreationDates.get(claimerId);
                if (userCreated != null && claimedAt.before(userCreated)) {
                    skippedNotEnrolled++;
                    continue; // User wasn't enrolled yet at claim time
                }

                boolean hasAnyReward = false;
                List<Object[]> claimTxs = new ArrayList<>();

                // Get partner's region for per-region budget lookup
                String partnerReg = partnerRegion.getOrDefault(partnerId, "AMERICAS");

                for (Map<String, Object> payout : payouts) {
                    UUID incentiveId = (UUID) payout.get("incentive_id");
                    String currencyId = (String) payout.get("currency_id");
                    BigDecimal reward = (BigDecimal) payout.get("payout_amount");

                    // Per-region budget capping
                    String regionBudgetKey = incentiveId + ":" + currencyId + ":" + partnerReg;
                    String globalFallbackKey = incentiveId + ":" + currencyId + ":GLOBAL";
                    String activeKey = budgetRemaining.containsKey(regionBudgetKey)
                            ? regionBudgetKey : globalFallbackKey;
                    BigDecimal remaining = budgetRemaining.getOrDefault(
                            activeKey, BigDecimal.ZERO);

                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        skippedCount++;
                        continue; // Budget exhausted for this incentive+currency+region
                    }

                    BigDecimal awarded = reward;
                    boolean capped = false;
                    if (remaining.compareTo(reward) < 0) {
                        awarded = remaining.setScale(0, RoundingMode.HALF_UP);
                        capped = true;
                        cappedCount++;
                    }

                    budgetRemaining.put(activeKey, remaining.subtract(awarded));
                    budgetUtilized.merge(activeKey, awarded, BigDecimal::add);

                    // Use claimedAt for created_at (not now) so rewards are historically distributed
                    claimTxs.add(new Object[]{
                            UUID.randomUUID(), clientId, actionId, claimerId,
                            incentiveId, currencyId, reward, awarded, capped, claimedAt, claimedAt
                    });

                    String balKey = claimerId + ":" + currencyId;
                    balanceAccumulator.merge(balKey, awarded, BigDecimal::add);
                    hasAnyReward = true;
                }

                // Only create claim_action if at least one reward was awarded
                if (hasAnyReward) {
                    actionBatch.add(new Object[]{
                            actionId, clientId, poId, claimerId, claimedAt, claimedAt, claimedAt
                    });
                    txBatch.addAll(claimTxs);
                    poHadReward = true;
                }
            }

            if (poHadReward) claimedCount++;

            if (actionBatch.size() >= BATCH_SIZE || txBatch.size() >= BATCH_SIZE) {
                flushClaimActions(actionBatch);
                actionBatch.clear();
                flushRewardTransactions(txBatch);
                txBatch.clear();
            }
        }

        if (!actionBatch.isEmpty()) flushClaimActions(actionBatch);
        if (!txBatch.isEmpty()) flushRewardTransactions(txBatch);

        // Reward balances (cumulative snapshot -- use seed end date)
        Timestamp snapshotTs = Timestamp.from(
                SEED_END_DATE.atStartOfDay(ZoneOffset.UTC).toInstant());
        List<Object[]> balanceBatch = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> balEntry : balanceAccumulator.entrySet()) {
            String[] parts = balEntry.getKey().split(":");
            UUID userId = UUID.fromString(parts[0]);
            String currencyId = parts[1];
            balanceBatch.add(new Object[]{
                    UUID.randomUUID(), clientId, userId, currencyId,
                    balEntry.getValue(), snapshotTs, snapshotTs
            });
        }
        if (!balanceBatch.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO reward_wallets (id, client_id, user_id, currency_id, " +
                    "available_balance, created_at, updated_at) VALUES (?,?,?,?,?,?,?) " +
                    "ON CONFLICT (client_id, user_id, currency_id) " +
                    "WHERE user_id IS NOT NULL AND partner_company_id IS NULL " +
                    "DO UPDATE SET available_balance = reward_wallets.available_balance + EXCLUDED.available_balance, " +
                    "updated_at = EXCLUDED.updated_at", balanceBatch);
        }

        // Write actual budget utilizations from claim data (per-region)
        // Uses location_value_id (UUID FK to location_values) instead of the old region VARCHAR column
        List<Object[]> utilBatch = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : budgetUtilized.entrySet()) {
            String[] parts = entry.getKey().split(":");
            UUID incentiveId = UUID.fromString(parts[0]);
            String currencyId = parts[1];
            String regionName = parts[2];

            // Resolve region name to location_value UUID
            UUID locationValueId = null;
            if (!"GLOBAL".equals(regionName)) {
                locationValueId = regionValueIds.get(regionName);
            }

            utilBatch.add(new Object[]{
                    UUID.randomUUID(), incentiveId, currencyId, locationValueId,
                    entry.getValue(), snapshotTs, snapshotTs
            });
        }
        if (!utilBatch.isEmpty()) {
            for (Object[] row : utilBatch) {
                UUID incentiveId2 = (UUID) row[1];
                String currencyId2 = (String) row[2];
                UUID locationValueId2 = (UUID) row[3];
                BigDecimal utilized2 = (BigDecimal) row[4];
                // Upsert: update if exists, insert if not
                int updated;
                if (locationValueId2 != null) {
                    updated = jdbc.update("UPDATE budget_utilizations SET utilized = utilized + ?, updated_at = ? " +
                            "WHERE incentive_id = ? AND currency_id = ? AND location_value_id = ?",
                            utilized2, row[6], incentiveId2, currencyId2, locationValueId2);
                } else {
                    updated = jdbc.update("UPDATE budget_utilizations SET utilized = utilized + ?, updated_at = ? " +
                            "WHERE incentive_id = ? AND currency_id = ? AND location_value_id IS NULL",
                            utilized2, row[6], incentiveId2, currencyId2);
                }
                if (updated == 0) {
                    jdbc.update("INSERT INTO budget_utilizations (id, incentive_id, currency_id, " +
                            "location_value_id, utilized, created_at, updated_at) VALUES (?,?,?,?,?,?,?)", row);
                }
            }
        }

        log.info("Created {} claimed deals, {} reward balances, {} budget-capped txns, " +
                        "{} skipped (budget exhausted), {} skipped (user not yet enrolled)",
                claimedCount, balanceAccumulator.size(), cappedCount, skippedCount,
                skippedNotEnrolled);
    }

    // ── Budget utilization ceiling ──────────────────────────────────────────────

    /**
     * Returns a utilization ceiling (0.15-1.0) that caps how much of an incentive's
     * budget gets consumed by seeded claims. Mimics real-world patterns where most
     * incentives don't exhaust their budget.
     *
     * Distribution:
     *   ~25% of incentives: high utilization (85-100%) — popular, broad eligibility
     *   ~45% of incentives: moderate utilization (45-70%) — typical programs
     *   ~30% of incentives: low utilization (15-40%) — niche or newly launched
     */
    private double computeUtilizationCeiling(UUID incentiveId, java.util.Random random) {
        double roll = Math.abs(incentiveId.hashCode() % 100) / 100.0;
        if (roll < 0.25) {
            return 0.85 + random.nextDouble() * 0.15;
        } else if (roll < 0.70) {
            return 0.45 + random.nextDouble() * 0.25;
        } else {
            return 0.15 + random.nextDouble() * 0.25;
        }
    }

    // ── Batch flush helpers ────────────────────────────────────────────────────

    private void flushClaimActions(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO claim_actions (id, client_id, purchase_order_id, user_id, " +
                "claimed_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?) " +
                "ON CONFLICT (client_id, purchase_order_id, user_id) DO NOTHING", batch);
    }

    private void flushRewardTransactions(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO reward_transactions (id, client_id, claim_action_id, " +
                "user_id, incentive_id, currency_id, amount_potential, amount_awarded, " +
                "budget_capped, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", batch);
    }

    // ── Date utilities ─────────────────────────────────────────────────────────

    private int getMonthFromDate(Object dateObj) {
        LocalDate ld = localDateFromObj(dateObj);
        return ld.getMonthValue();
    }

    private int getYearFromDate(Object dateObj) {
        LocalDate ld = localDateFromObj(dateObj);
        return ld.getYear();
    }

    private LocalDate localDateFromObj(Object dateObj) {
        if (dateObj instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (dateObj instanceof LocalDate ld) {
            return ld;
        }
        if (dateObj instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(dateObj.toString());
    }
}
