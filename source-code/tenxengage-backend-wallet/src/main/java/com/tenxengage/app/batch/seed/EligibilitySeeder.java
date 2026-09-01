package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.BandPlan;
import com.tenxengage.app.batch.seed.SeedRecords.IncentiveRef;
import com.tenxengage.app.batch.seed.SeedRecords.PayoutConfigRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.tenxengage.app.batch.seed.SeedConstants.BATCH_SIZE;
import static com.tenxengage.app.batch.seed.SeedConstants.REGIONS;

/**
 * Computes eligibility mappings and payouts for all purchase orders against all incentives.
 * Creates po_eligibility_mappings and eligibility_payouts records with multi-tier band logic.
 */
@Component
public class EligibilitySeeder {

    private static final Logger log = LoggerFactory.getLogger(EligibilitySeeder.class);

    private final JdbcTemplate jdbc;

    public EligibilitySeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Computes eligibility for all POs against all incentive requirements.
     *
     * @return the tagging job UUID
     */
    public UUID computeEligibility(UUID clientId, List<IncentiveRef> incentiveRefs,
                                   Map<String, List<UUID>> posByQuarterRegion,
                                   Map<UUID, String> partnerRegion) {
        Timestamp now = Timestamp.from(Instant.now());

        UUID jobId = UUID.randomUUID();
        jdbc.update("INSERT INTO tagging_jobs (id, client_id, status, pos_analyzed, eligible_deals, " +
                        "incentives_matched, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                jobId, clientId, "COMPLETED", 0, 0, 0, now, now);

        // Temporary indexes for heavy eligibility queries on large datasets
        log.info("Creating temporary indexes for eligibility computation...");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_tmp_pol_po_id ON purchase_order_lines (purchase_order_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_tmp_pol_product_id ON purchase_order_lines (product_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_tmp_po_client_id ON purchase_orders (client_id)");

        // Collect all PO IDs from the input map for targeted loading
        Set<UUID> targetPoIds = new java.util.HashSet<>();
        for (List<UUID> ids : posByQuarterRegion.values()) {
            targetPoIds.addAll(ids);
        }

        // Pre-load PO data: only load POs we need to evaluate (not the entire table)
        log.info("Loading PO data with SKU aggregation for {} target POs...", targetPoIds.size());
        String poIdFilter = targetPoIds.isEmpty() ? "AND FALSE"
                : "AND po.id = ANY(ARRAY[" + targetPoIds.stream()
                    .map(id -> "'" + id + "'::uuid")
                    .reduce((a, b) -> a + "," + b).orElse("") + "])";
        List<Map<String, Object>> allPOs = jdbc.queryForList(
                "SELECT po.id, po.total_amount, po.metadata->>'Customer Segment' AS customer_segment, " +
                        "po.partner_company_id, " +
                        "STRING_AGG(DISTINCT p.sku, ',') as skus " +
                        "FROM purchase_orders po " +
                        "JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id " +
                        "JOIN products p ON p.id = pol.product_id " +
                        "WHERE po.client_id = ? " + poIdFilter + " " +
                        "GROUP BY po.id, po.total_amount, po.metadata->>'Customer Segment', po.partner_company_id",
                clientId);

        Map<UUID, Map<String, Object>> poLookup = new HashMap<>();
        for (Map<String, Object> po : allPOs) {
            poLookup.put((UUID) po.get("id"), po);
        }

        // Pre-load PO line totals by SKU (for ELIGIBLE_PRODUCTS payout computation)
        Map<UUID, Map<String, BigDecimal>> poLinesBySku = new HashMap<>();
        boolean hasEligibleProductsConfig = incentiveRefs.stream()
                .flatMap(ref -> ref.payoutConfigs().stream())
                .anyMatch(pc -> "ELIGIBLE_PRODUCTS".equals(pc.against()));

        if (hasEligibleProductsConfig) {
            List<Map<String, Object>> allLines = jdbc.queryForList(
                    "SELECT pol.purchase_order_id, p.sku, SUM(pol.line_total) as sku_total " +
                            "FROM purchase_order_lines pol " +
                            "JOIN products p ON p.id = pol.product_id " +
                            "WHERE pol.purchase_order_id IN " +
                            "(SELECT id FROM purchase_orders WHERE client_id = ?) " +
                            "GROUP BY pol.purchase_order_id, p.sku",
                    clientId);
            for (Map<String, Object> line : allLines) {
                UUID poId = (UUID) line.get("purchase_order_id");
                String sku = (String) line.get("sku");
                BigDecimal skuTotal = (BigDecimal) line.get("sku_total");
                poLinesBySku.computeIfAbsent(poId, k -> new HashMap<>()).put(sku, skuTotal);
            }
        }

        // Group incentives by quarter+region for efficient lookup
        Map<String, List<IncentiveRef>> incentivesByQR = new HashMap<>();
        for (IncentiveRef ref : incentiveRefs) {
            for (String region : REGIONS) {
                if (ref.targetRegion() == null || ref.targetRegion().equals(region)) {
                    String key = ref.quarter().displayName() + "|" + region;
                    incentivesByQR.computeIfAbsent(key, k -> new ArrayList<>()).add(ref);
                }
            }
        }

        List<Object[]> mappingBatch = new ArrayList<>();
        List<Object[]> payoutBatch = new ArrayList<>();
        int totalEligible = 0;
        int totalMappings = 0;

        for (Map.Entry<String, List<UUID>> entry : posByQuarterRegion.entrySet()) {
            String qrKey = entry.getKey();
            List<UUID> poIds = entry.getValue();
            List<IncentiveRef> qrIncentives = incentivesByQR.getOrDefault(qrKey, List.of());

            if (qrIncentives.isEmpty()) continue;

            for (UUID poId : poIds) {
                Map<String, Object> poData = poLookup.get(poId);
                if (poData == null) continue;

                BigDecimal total = (BigDecimal) poData.get("total_amount");
                String segment = (String) poData.get("customer_segment");
                String skuStr = (String) poData.get("skus");
                Set<String> poSkus = skuStr != null
                        ? Set.of(skuStr.split(",")) : Set.of();

                boolean anyEligible = false;

                for (IncentiveRef ref : qrIncentives) {
                    boolean skuMatch = false;
                    for (String sku : ref.eligibleSkus()) {
                        if (poSkus.contains(sku)) {
                            skuMatch = true;
                            break;
                        }
                    }

                    boolean amountMatch = total != null
                            && total.compareTo(ref.minBookingAmount()) > 0;

                    boolean segmentMatch = true;
                    String ineligibleReason = null;

                    if (ref.eligibleSegments() != null) {
                        segmentMatch = ref.eligibleSegments().contains(segment);
                    }

                    boolean eligible = skuMatch && amountMatch && segmentMatch;

                    if (!eligible) {
                        if (!skuMatch) {
                            ineligibleReason = "No matching products";
                        } else if (!amountMatch) {
                            ineligibleReason = String.format(
                                    "Booking amount below $%,.0f threshold",
                                    ref.minBookingAmount().doubleValue());
                        } else {
                            ineligibleReason = "Customer segment not eligible";
                        }
                    }

                    UUID mappingId = UUID.randomUUID();
                    mappingBatch.add(new Object[]{
                            mappingId, clientId, jobId, poId,
                            ref.id(), eligible, ineligibleReason, now, now
                    });
                    totalMappings++;

                    if (eligible) {
                        anyEligible = true;

                        // Multi-tier payout computation using actual band data
                        for (PayoutConfigRef config : ref.payoutConfigs()) {
                            BigDecimal baseAmount;
                            if ("ELIGIBLE_PRODUCTS".equals(config.against())) {
                                Map<String, BigDecimal> skuTotals = poLinesBySku.getOrDefault(
                                        poId, Map.of());
                                baseAmount = BigDecimal.ZERO;
                                for (String sku : ref.eligibleSkus()) {
                                    baseAmount = baseAmount.add(
                                            skuTotals.getOrDefault(sku, BigDecimal.ZERO));
                                }
                            } else {
                                baseAmount = total;
                            }

                            BigDecimal payoutAmount = null;
                            for (BandPlan band : config.bands()) {
                                boolean aboveMin = baseAmount.compareTo(band.minAmount()) >= 0;
                                boolean belowMax = band.maxAmount() == null
                                        || baseAmount.compareTo(band.maxAmount()) <= 0;
                                if (aboveMin && belowMax) {
                                    if ("FLAT".equals(config.payoutType())) {
                                        payoutAmount = band.payoutValue();
                                    } else { // PERCENTAGE
                                        payoutAmount = baseAmount
                                                .multiply(band.payoutValue())
                                                .divide(BigDecimal.valueOf(100), 0,
                                                        RoundingMode.HALF_UP);
                                    }
                                    break;
                                }
                            }

                            if (payoutAmount != null) {
                                if (config.maxPerDeal() != null) {
                                    payoutAmount = payoutAmount.min(config.maxPerDeal());
                                }
                                payoutBatch.add(new Object[]{
                                        UUID.randomUUID(), mappingId, ref.requirementId(),
                                        config.currencyId(), payoutAmount, now, now
                                });
                            }
                        }
                    }

                    if (mappingBatch.size() >= BATCH_SIZE) {
                        flushEligibilityMappings(mappingBatch);
                        mappingBatch.clear();
                        flushEligibilityPayouts(payoutBatch);
                        payoutBatch.clear();
                    }
                }

                if (anyEligible) totalEligible++;
            }
        }

        if (!mappingBatch.isEmpty()) flushEligibilityMappings(mappingBatch);
        if (!payoutBatch.isEmpty()) flushEligibilityPayouts(payoutBatch);

        // Update tagging job stats
        jdbc.update("UPDATE tagging_jobs SET pos_analyzed = ?, eligible_deals = ?, " +
                        "incentives_matched = ? WHERE id = ?",
                allPOs.size(), totalEligible, incentiveRefs.size(), jobId);

        double eligibilityRate = allPOs.isEmpty() ? 0
                : (totalEligible * 100.0 / allPOs.size());
        log.info("Eligibility: {} eligible POs out of {} ({}%), {} total mappings",
                totalEligible, allPOs.size(),
                BigDecimal.valueOf(eligibilityRate).setScale(1, RoundingMode.HALF_UP),
                totalMappings);
        return jobId;
    }

    // ── Batch flush helpers ────────────────────────────────────────────────────

    private void flushEligibilityMappings(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO po_eligibility_mappings (id, client_id, tagging_job_id, " +
                "purchase_order_id, incentive_id, eligible, ineligibility_reason, " +
                "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)", batch);
    }

    private void flushEligibilityPayouts(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO eligibility_payouts (id, eligibility_mapping_id, " +
                "requirement_id, currency_id, payout_amount, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?)", batch);
    }
}
