package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.FiscalQuarter;
import com.tenxengage.app.batch.seed.SeedRecords.IncentivePlan;
import com.tenxengage.app.batch.seed.SeedRecords.ProductRow;
import com.tenxengage.app.batch.seed.SeedRecords.SellerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.tenxengage.app.batch.seed.SeedConstants.BATCH_SIZE;
import static com.tenxengage.app.batch.seed.SeedConstants.CUSTOMER_FIRST;
import static com.tenxengage.app.batch.seed.SeedConstants.CUSTOMER_LAST;
import static com.tenxengage.app.batch.seed.SeedConstants.CUSTOMER_SEGMENTS;
import static com.tenxengage.app.batch.seed.SeedConstants.MAX_LINES_PER_PO;
import static com.tenxengage.app.batch.seed.SeedConstants.MIN_LINES_PER_PO;
import static com.tenxengage.app.batch.seed.SeedConstants.POS_PER_PARTNER;
import static com.tenxengage.app.batch.seed.SeedConstants.PO_STATUSES;
import static com.tenxengage.app.batch.seed.SeedConstants.PRICE_RANGES;
import static com.tenxengage.app.batch.seed.SeedConstants.QUARTERLY_VOLUME;
import static com.tenxengage.app.batch.seed.SeedConstants.REGIONS;

/**
 * Creates ~700K purchase orders with incentive-aware product selection across 36 months.
 * Enrolled partners get incentive + training boosts; non-enrolled get natural distribution.
 */
@Component
public class PurchaseOrderSeeder {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderSeeder.class);

    private static final LocalDate SEED_START_DATE = FiscalQuarterCalculator.getSeedStartDate();

    private final JdbcTemplate jdbc;

    public PurchaseOrderSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Creates purchase orders and line items for all partners across all quarters.
     *
     * @return map of "FYyyyy Qn|REGION" to list of PO UUIDs for eligibility computation
     */
    public Map<String, List<UUID>> createPurchaseOrdersAndLines(
            UUID clientId, List<UUID> partnerIds, Map<UUID, String> partnerRegion,
            List<ProductRow> products, List<FiscalQuarter> quarters,
            Map<String, List<IncentivePlan>> incentivePlansByQR,
            Set<UUID> enrolledPartnerSet,
            Map<UUID, Map<String, Double>> trainingBoostsByPartner,
            Random random) {

        Timestamp now = Timestamp.from(Instant.now());
        Map<String, List<UUID>> posByQuarterRegion = new HashMap<>();

        // Build product lookup by category
        Map<String, List<ProductRow>> productsByCategory = new HashMap<>();
        for (ProductRow pr : products) {
            productsByCategory.computeIfAbsent(pr.category(), k -> new ArrayList<>()).add(pr);
        }
        List<String> catList = new ArrayList<>(productsByCategory.keySet());
        catList.sort(String::compareTo);
        List<String> cloudCategories = List.of("Cloud Services", "Software & Licensing");

        // Pre-compute category weights per quarter/region
        // Enrolled partners: with incentive boost (normal + trailing)
        // Non-enrolled partners: baseline (no boost) -- natural product distribution
        Map<String, double[]> normalWeightsByQR = new HashMap<>();
        Map<String, double[]> trailingWeightsByQR = new HashMap<>();
        Map<String, double[]> baselineWeightsByQR = new HashMap<>();

        for (int q = 0; q < quarters.size(); q++) {
            FiscalQuarter fq = quarters.get(q);
            double cloudProbBase = 0.20 + 0.03 * (fq.fyYear() - 2023)
                    + (fq.calendarQuarter() - 1) * 0.0075;

            for (String region : REGIONS) {
                String qrKey = fq.displayName() + "|" + region;

                // Baseline weights: no incentive boost (for non-enrolled partners)
                baselineWeightsByQR.put(qrKey,
                        computeCategoryWeights(catList, cloudProbBase, cloudCategories, Map.of()));

                // Current quarter boost from incentive plans (for enrolled partners)
                Map<String, Double> currentBoost = new HashMap<>();
                for (IncentivePlan plan : incentivePlansByQR.getOrDefault(qrKey, List.of())) {
                    currentBoost.merge(plan.focusCategory(), plan.successScore(), Math::max);
                }
                normalWeightsByQR.put(qrKey,
                        computeCategoryWeights(catList, cloudProbBase, cloudCategories, currentBoost));

                // With trailing effect from previous quarter
                Map<String, Double> combinedBoost = new HashMap<>(currentBoost);
                if (q > 0) {
                    String prevKey = quarters.get(q - 1).displayName() + "|" + region;
                    for (IncentivePlan plan : incentivePlansByQR.getOrDefault(prevKey, List.of())) {
                        double trailing = 1.0 + 0.30 * (plan.successScore() - 1.0);
                        if (trailing > 1.0) {
                            combinedBoost.merge(plan.focusCategory(), trailing, Math::max);
                        }
                    }
                }
                trailingWeightsByQR.put(qrKey,
                        computeCategoryWeights(catList, cloudProbBase, cloudCategories, combinedBoost));
            }
        }

        // Compute volume weights per quarter
        double[] quarterWeights = new double[quarters.size()];
        double totalWeight = 0;
        for (int q = 0; q < quarters.size(); q++) {
            FiscalQuarter fq = quarters.get(q);
            int calQ = fq.calendarQuarter();
            double seasonal = QUARTERLY_VOLUME[calQ - 1];
            double yearFactor = 1.0 + 0.07 * (fq.fyYear() - 2023);
            double partialFactor = 1.0;
            if (fq.fyYear() == 2023 && "Q1".equals(fq.qLabel())) {
                partialFactor = 1.0 / 3.0;
            }
            quarterWeights[q] = seasonal * yearFactor * partialFactor;
            totalWeight += quarterWeights[q];
        }
        for (int q = 0; q < quarters.size(); q++) {
            quarterWeights[q] /= totalWeight;
        }

        List<Object[]> poBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> lineBatch = new ArrayList<>(BATCH_SIZE);
        int totalPOs = 0;
        int totalLines = 0;

        for (int p = 0; p < partnerIds.size(); p++) {
            UUID partnerId = partnerIds.get(p);
            String region = partnerRegion.getOrDefault(partnerId, "AMERICAS");
            boolean isEnrolled = enrolledPartnerSet.contains(partnerId);

            double regionFactor = regionVolumeFactor(region);
            int[] posPerQuarter = distributeVolume(POS_PER_PARTNER, quarterWeights, regionFactor);

            for (int q = 0; q < quarters.size(); q++) {
                FiscalQuarter fq = quarters.get(q);
                int numPOs = posPerQuarter[q];
                LocalDate qStart = fq.startDate().isBefore(SEED_START_DATE)
                        ? SEED_START_DATE : fq.startDate();
                LocalDate seedEnd = LocalDate.now();
                LocalDate qEnd = fq.endDate().isAfter(seedEnd)
                        ? seedEnd : fq.endDate();
                int dayRange = (int) (qEnd.toEpochDay() - qStart.toEpochDay());
                if (dayRange <= 0) continue;

                String qrKey = fq.displayName() + "|" + region;
                double[] weightsNormal = normalWeightsByQR.getOrDefault(qrKey,
                        new double[catList.size()]);
                double[] weightsTrailing = trailingWeightsByQR.getOrDefault(qrKey,
                        new double[catList.size()]);
                double[] weightsBaseline = baselineWeightsByQR.getOrDefault(qrKey,
                        new double[catList.size()]);

                for (int o = 0; o < numPOs; o++) {
                    UUID poId = UUID.randomUUID();
                    LocalDate orderDate = qStart.plusDays(random.nextInt(dayRange + 1));
                    String segment = CUSTOMER_SEGMENTS[random.nextInt(CUSTOMER_SEGMENTS.length)];
                    String customer = CUSTOMER_FIRST[random.nextInt(CUSTOMER_FIRST.length)] + " "
                            + CUSTOMER_LAST[random.nextInt(CUSTOMER_LAST.length)];
                    String status = PO_STATUSES[random.nextInt(PO_STATUSES.length)];
                    String orderNumber = String.format("PO-%05d-%05d", p + 1, totalPOs + 1);

                    int numLines = MIN_LINES_PER_PO
                            + random.nextInt(MAX_LINES_PER_PO - MIN_LINES_PER_PO + 1);

                    // Select weight array: enrolled get incentive + training boost,
                    // non-enrolled get baseline
                    double[] catWeights;
                    if (isEnrolled) {
                        long daysSinceQStart = orderDate.toEpochDay() - qStart.toEpochDay();
                        catWeights = (daysSinceQStart <= 30 && q > 0)
                                ? weightsTrailing : weightsNormal;
                        // Apply training boost: multiply category weights by training
                        // completion boosts (temporal causation -- only for completions
                        // before the order date)
                        Map<String, Double> partnerBoosts = trainingBoostsByPartner
                                .getOrDefault(partnerId, Map.of());
                        if (!partnerBoosts.isEmpty()) {
                            catWeights = applyTrainingBoost(
                                    catWeights, catList, partnerBoosts, orderDate);
                        }
                    } else {
                        catWeights = weightsBaseline;
                    }

                    BigDecimal orderTotal = BigDecimal.ZERO;
                    for (int l = 0; l < numLines; l++) {
                        // Pick product using incentive-aware category weights
                        ProductRow product;
                        if (catWeights.length > 0) {
                            double roll = random.nextDouble();
                            double cumulative = 0;
                            int selectedIdx = catList.size() - 1;
                            for (int c = 0; c < catWeights.length; c++) {
                                cumulative += catWeights[c];
                                if (roll < cumulative) {
                                    selectedIdx = c;
                                    break;
                                }
                            }
                            String selectedCategory = catList.get(selectedIdx);
                            List<ProductRow> catProducts = productsByCategory.get(selectedCategory);
                            product = catProducts.get(random.nextInt(catProducts.size()));
                        } else {
                            product = products.get(random.nextInt(products.size()));
                        }

                        double[] range = PRICE_RANGES.getOrDefault(product.category(),
                                new double[]{1000, 10000});
                        double unitPrice = range[0] + random.nextDouble() * (range[1] - range[0]);
                        int quantity = 1 + random.nextInt(5);
                        BigDecimal price = BigDecimal.valueOf(unitPrice)
                                .setScale(0, RoundingMode.HALF_UP);
                        BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(quantity));
                        orderTotal = orderTotal.add(lineTotal);

                        String transactionId = String.format("TXN-%05d-%07d-%02d",
                                p + 1, totalPOs + 1, l + 1);
                        lineBatch.add(new Object[]{
                                UUID.randomUUID(), poId, product.id(), quantity,
                                price, lineTotal, transactionId, now, now
                        });
                        totalLines++;
                    }

                    String poMetadata = String.format(
                            "{\"Customer Name\":\"%s\",\"Customer Segment\":\"%s\"}",
                            customer.replace("\"", "\\\""), segment);
                    poBatch.add(new Object[]{
                            poId, clientId, partnerId, orderNumber,
                            Date.valueOf(orderDate), status,
                            orderTotal, poMetadata, now, now
                    });
                    posByQuarterRegion.computeIfAbsent(qrKey, k -> new ArrayList<>()).add(poId);
                    totalPOs++;

                    if (poBatch.size() >= BATCH_SIZE || lineBatch.size() >= BATCH_SIZE) {
                        flushPOs(poBatch);
                        poBatch.clear();
                        flushLines(lineBatch);
                        lineBatch.clear();
                    }
                }
            }

            if ((p + 1) % 10 == 0) {
                log.info("Progress: {}/{} partners, {} POs, {} lines",
                        p + 1, partnerIds.size(), totalPOs, totalLines);
            }
        }

        if (!poBatch.isEmpty()) flushPOs(poBatch);
        if (!lineBatch.isEmpty()) flushLines(lineBatch);
        log.info("Created {} purchase orders with {} line items", totalPOs, totalLines);
        return posByQuarterRegion;
    }

    // ── Helper methods ─────────────────────────────────────────────────────────

    private double regionVolumeFactor(String region) {
        return switch (region) {
            case "AMERICAS" -> 1.0;
            case "LATAM" -> 0.7;
            case "EMEAR" -> 0.95;
            case "APJ" -> 0.85;
            default -> 1.0;
        };
    }

    private int[] distributeVolume(int total, double[] weights, double regionFactor) {
        int[] result = new int[weights.length];
        int assigned = 0;
        for (int i = 0; i < weights.length; i++) {
            result[i] = (int) Math.round(total * weights[i]);
            assigned += result[i];
        }
        int diff = total - assigned;
        if (diff != 0) {
            int maxIdx = 0;
            for (int i = 1; i < result.length; i++) {
                if (result[i] > result[maxIdx]) maxIdx = i;
            }
            result[maxIdx] += diff;
        }
        return result;
    }

    private double[] computeCategoryWeights(List<String> categories, double cloudProbBase,
                                            List<String> cloudCategories,
                                            Map<String, Double> boost) {
        double[] weights = new double[categories.size()];
        int nonCloudCount = categories.size() - (int) categories.stream()
                .filter(cloudCategories::contains).count();
        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            if (cloudCategories.contains(cat)) {
                weights[i] = cloudProbBase / Math.max(1, (int) categories.stream()
                        .filter(cloudCategories::contains).count());
            } else {
                weights[i] = (1.0 - cloudProbBase) / Math.max(1, nonCloudCount);
            }
            Double b = boost.get(cat);
            if (b != null) {
                weights[i] *= b;
            }
        }
        double sum = 0;
        for (double w : weights) sum += w;
        if (sum > 0) {
            for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        }
        return weights;
    }

    private double[] applyTrainingBoost(double[] baseWeights, List<String> catList,
                                        Map<String, Double> partnerBoosts,
                                        LocalDate orderDate) {
        double[] boosted = new double[baseWeights.length];
        double sum = 0;
        for (int i = 0; i < baseWeights.length; i++) {
            String cat = catList.get(i);
            Double boost = partnerBoosts.get(cat);
            if (boost != null) {
                boosted[i] = baseWeights[i] * boost;
            } else {
                boosted[i] = baseWeights[i];
            }
            sum += boosted[i];
        }
        // Re-normalize
        if (sum > 0) {
            for (int i = 0; i < boosted.length; i++) {
                boosted[i] /= sum;
            }
        }
        return boosted;
    }

    // ── Incremental PO Generation ────────────────────────────────────────────

    /**
     * Creates POs only for the specified date range (incremental mode).
     * Generates ~1-2 POs per partner per day within [fromDate, toDate].
     * Returns PO IDs keyed by "FYyyyy Qn|REGION" for eligibility computation.
     */
    public Map<String, List<UUID>> createIncrementalPOs(
            UUID clientId, List<UUID> partnerIds, Map<UUID, String> partnerRegion,
            List<ProductRow> products, LocalDate fromDate, LocalDate toDate,
            Set<UUID> enrolledPartnerSet, Random random) {

        Timestamp now = Timestamp.from(Instant.now());
        Map<String, List<UUID>> posByQuarterRegion = new HashMap<>();
        int dayRange = (int) (toDate.toEpochDay() - fromDate.toEpochDay()) + 1;
        if (dayRange <= 0) return posByQuarterRegion;

        // Build product lookup
        Map<String, List<ProductRow>> productsByCategory = new HashMap<>();
        for (ProductRow pr : products) {
            productsByCategory.computeIfAbsent(pr.category(), k -> new ArrayList<>()).add(pr);
        }
        List<String> catList = new ArrayList<>(productsByCategory.keySet());
        catList.sort(String::compareTo);

        // Get existing PO count for unique order numbers
        Integer existingPOCount = jdbc.queryForObject(
                "SELECT count(*) FROM purchase_orders WHERE client_id = ?", Integer.class, clientId);
        int poCounter = (existingPOCount != null ? existingPOCount : 0) + 1;

        // Compute avg POs per partner per day from the full seed rate
        double avgPOsPerDay = (double) POS_PER_PARTNER / 1140.0; // ~1.54 POs/partner/day

        List<Object[]> poBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> lineBatch = new ArrayList<>(BATCH_SIZE);
        int totalPOs = 0;

        for (UUID partnerId : partnerIds) {
            String region = partnerRegion.getOrDefault(partnerId, "AMERICAS");
            double regionFactor = regionVolumeFactor(region);

            // Number of POs for this partner in the date range
            int numPOs = Math.max(1, (int) Math.round(avgPOsPerDay * dayRange * regionFactor));
            // Cap to prevent excessive POs for large date ranges
            numPOs = Math.min(numPOs, dayRange * 3);

            for (int o = 0; o < numPOs; o++) {
                UUID poId = UUID.randomUUID();
                LocalDate orderDate = fromDate.plusDays(random.nextInt(dayRange));
                FiscalQuarter fq = FiscalQuarterCalculator.quarterContaining(orderDate);
                String qrKey = fq.displayName() + "|" + region;

                String segment = CUSTOMER_SEGMENTS[random.nextInt(CUSTOMER_SEGMENTS.length)];
                String customer = CUSTOMER_FIRST[random.nextInt(CUSTOMER_FIRST.length)] + " "
                        + CUSTOMER_LAST[random.nextInt(CUSTOMER_LAST.length)];
                String status = PO_STATUSES[random.nextInt(PO_STATUSES.length)];
                String orderNumber = String.format("INC-%06d", poCounter++);

                int numLines = MIN_LINES_PER_PO + random.nextInt(MAX_LINES_PER_PO - MIN_LINES_PER_PO + 1);
                BigDecimal orderTotal = BigDecimal.ZERO;

                for (int l = 0; l < numLines; l++) {
                    String category = catList.get(random.nextInt(catList.size()));
                    List<ProductRow> catProducts = productsByCategory.get(category);
                    ProductRow product = catProducts.get(random.nextInt(catProducts.size()));

                    double[] priceRange = PRICE_RANGES.get(category);
                    double price = priceRange[0] + random.nextDouble() * (priceRange[1] - priceRange[0]);
                    int quantity = 1 + random.nextInt(5);
                    BigDecimal unitPrice = BigDecimal.valueOf(price).setScale(0, RoundingMode.HALF_UP);
                    BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
                    orderTotal = orderTotal.add(lineTotal);

                    String txnId = UUID.randomUUID().toString().substring(0, 30);
                    lineBatch.add(new Object[]{
                            UUID.randomUUID(), poId, product.id(), quantity,
                            unitPrice, lineTotal, txnId, now, now
                    });
                }

                String metadata = String.format("{\"Customer Name\":\"%s\",\"Customer Segment\":\"%s\"}",
                        customer, segment);
                poBatch.add(new Object[]{
                        poId, clientId, partnerId, orderNumber,
                        Date.valueOf(orderDate), status, orderTotal, metadata, now, now
                });

                posByQuarterRegion.computeIfAbsent(qrKey, k -> new ArrayList<>()).add(poId);
                totalPOs++;

                if (poBatch.size() >= BATCH_SIZE) {
                    flushPOs(poBatch);
                    poBatch.clear();
                }
                if (lineBatch.size() >= BATCH_SIZE) {
                    flushLines(lineBatch);
                    lineBatch.clear();
                }
            }
        }

        if (!poBatch.isEmpty()) flushPOs(poBatch);
        if (!lineBatch.isEmpty()) flushLines(lineBatch);

        log.info("Created {} incremental POs for {} partners ({} to {})",
                totalPOs, partnerIds.size(), fromDate, toDate);
        return posByQuarterRegion;
    }

    // ── Batch flush helpers ────────────────────────────────────────────────────

    private void flushPOs(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO purchase_orders (id, client_id, partner_company_id, " +
                "order_number, order_date, status, " +
                "total_amount, metadata, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)", batch);
    }

    private void flushLines(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO purchase_order_lines (id, purchase_order_id, product_id, " +
                "quantity, unit_price, line_total, transaction_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch);
    }
}
