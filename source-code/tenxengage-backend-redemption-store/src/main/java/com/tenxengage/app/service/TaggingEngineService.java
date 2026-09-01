package com.tenxengage.app.service;

import com.tenxengage.app.entity.BuilderFieldConfig;
import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.EligibilityRule;
import com.tenxengage.app.entity.EligibilityRuleGroup;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.PayoutBand;
import com.tenxengage.app.entity.PayoutConfig;
import com.tenxengage.app.entity.SalesRequirement;
import com.tenxengage.app.entity.enums.EligibilityRuleType;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.entity.enums.PayoutType;
import com.tenxengage.app.entity.enums.RuleOperator;
import com.tenxengage.app.repository.BuilderFieldConfigRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tagging engine that evaluates purchase orders against active SALES incentive eligibility rules.
 *
 * Only processes POs where needs_retagging = TRUE. For each PO x incentive combination:
 * 1. Participant eligibility checks (date range, region, partner type, specific partners)
 * 2. Section 5 requirement rule evaluation (OR between groups, AND within groups)
 * 3. Payout calculation per requirement x currency
 * 4. Creates po_eligibility_mapping records with eligible/ineligible status and reasons
 */
@Service
public class TaggingEngineService {

    private static final Logger log = LoggerFactory.getLogger(TaggingEngineService.class);

    private static final int BATCH_SIZE = 5000;

    private final IncentiveRepository incentiveRepository;
    private final BuilderFieldConfigRepository builderFieldConfigRepository;
    private final JdbcTemplate jdbc;

    public TaggingEngineService(IncentiveRepository incentiveRepository,
                                BuilderFieldConfigRepository builderFieldConfigRepository,
                                JdbcTemplate jdbc) {
        this.incentiveRepository = incentiveRepository;
        this.builderFieldConfigRepository = builderFieldConfigRepository;
        this.jdbc = jdbc;
    }

    /**
     * Result of a tagging run.
     */
    public record TaggingResult(
            int posAnalyzed, int eligibleDeals, int incentivesMatched, int dealsInserted) {}

    /**
     * Run the tagging engine for a client. Evaluates POs with needs_retagging = TRUE
     * against all active SALES incentives.
     *
     * @param clientId     the client to run tagging for
     * @param taggingJobId the tagging job identifier
     * @return TaggingResult with accurate counts
     */
    public TaggingResult runTagging(UUID clientId, UUID taggingJobId) {
        // 1. Load POs needing retagging
        List<Map<String, Object>> posToRetag = loadPosNeedingRetagging(clientId);
        log.info("Tagging engine started for client {} — {} POs need retagging",
                clientId, posToRetag.size());

        if (posToRetag.isEmpty()) {
            return new TaggingResult(0, 0, 0, 0);
        }

        // 2. Load active SALES incentives with full rule graph
        List<Incentive> activeIncentives = incentiveRepository
                .searchByClientId(
                        clientId, IncentiveType.SALES, IncentiveStatus.ACTIVE, null,
                        Pageable.unpaged())
                .getContent();

        if (activeIncentives.isEmpty()) {
            log.info("No active SALES incentives for client {} — clearing retagging flags",
                    clientId);
            clearRetaggingFlags(posToRetag);
            return new TaggingResult(posToRetag.size(), 0, 0, 0);
        }

        log.info("Found {} active SALES incentives to evaluate", activeIncentives.size());

        // 3. For each PO x incentive: evaluate and write mappings
        int totalEligibleDeals = 0;
        int totalDealsInserted = 0;
        Set<UUID> incentivesWithMatches = new HashSet<>();

        List<Object[]> mappingBatch = new ArrayList<>();
        List<Object[]> payoutBatch = new ArrayList<>();

        for (Map<String, Object> poData : posToRetag) {
            UUID poId = (UUID) poData.get("id");

            // Delete existing eligibility mappings for this PO (they'll be recreated)
            deleteExistingMappings(poId);

            // Load PO product SKUs once per PO (lazy, only if needed)
            Set<String> poSkus = null;

            for (Incentive incentive : activeIncentives) {
                // 3a. Participant eligibility checks
                String ineligibleReason = checkParticipantEligibility(poData, incentive);
                if (ineligibleReason != null) {
                    mappingBatch.add(buildMappingRow(
                            clientId, poId, incentive.getId(), taggingJobId, false, ineligibleReason));
                    totalDealsInserted++;
                    continue;
                }

                // 3a-ii. Dynamic eligibility field checks (custom fields from builder config)
                String dynamicIneligibleReason = checkDynamicEligibility(
                        clientId, incentive, poData);
                if (dynamicIneligibleReason != null) {
                    mappingBatch.add(buildMappingRow(
                            clientId, poId, incentive.getId(), taggingJobId, false,
                            dynamicIneligibleReason));
                    totalDealsInserted++;
                    continue;
                }

                // Lazy-load PO SKUs for requirement evaluation
                if (poSkus == null) {
                    poSkus = loadPoSkus(poId);
                }

                // 3b. Evaluate Section 5 requirements
                BigDecimal poTotal = toBigDecimal(poData.get("total_amount"));
                String customerSegment = (String) poData.get("customer_segment");
                List<SalesRequirement> requirements = incentive.getSalesRequirements();

                String reqFailReason = evaluateRequirements(
                        poId, poTotal, customerSegment, poSkus, requirements);

                if (reqFailReason != null) {
                    mappingBatch.add(buildMappingRow(
                            clientId, poId, incentive.getId(), taggingJobId, false, reqFailReason));
                    totalDealsInserted++;
                    continue;
                }

                // 3c. PO is eligible — calculate payouts per requirement x currency
                UUID mappingId = UUID.randomUUID();
                mappingBatch.add(buildMappingRowWithId(
                        mappingId, clientId, poId, incentive.getId(), taggingJobId, true, null));
                totalEligibleDeals++;
                totalDealsInserted++;
                incentivesWithMatches.add(incentive.getId());

                for (SalesRequirement req : requirements) {
                    List<Object[]> reqPayouts = calculatePayouts(
                            mappingId, poId, req, poTotal, poSkus);
                    payoutBatch.addAll(reqPayouts);
                }
            }
        }

        // Flush mapping and payout batches
        flushMappingBatch(mappingBatch);
        flushPayoutBatch(payoutBatch);

        // 4. Clear retagging flags
        clearRetaggingFlags(posToRetag);

        log.info("Tagging complete: {} POs analyzed, {} eligible deals across {} incentives, "
                        + "{} total mappings inserted",
                posToRetag.size(), totalEligibleDeals, incentivesWithMatches.size(),
                totalDealsInserted);

        return new TaggingResult(
                posToRetag.size(), totalEligibleDeals,
                incentivesWithMatches.size(), totalDealsInserted);
    }

    // ---- Data loading ----

    private List<Map<String, Object>> loadPosNeedingRetagging(UUID clientId) {
        String sql = """
                SELECT po.id, po.order_number, po.order_date, po.partner_company_id,
                       po.metadata->>'Customer Segment' AS customer_segment, po.total_amount,
                       pc.metadata->>'Partner Type' AS partner_type, pc.external_partner_id,
                       (SELECT json_agg(json_build_object('level_id', lv.level_id::text, 'value_id', lv.id::text))
                        FROM partner_company_locations pcl
                        JOIN location_values lv ON lv.id = pcl.location_value_id
                        WHERE pcl.partner_company_id = pc.id) AS location_data
                FROM purchase_orders po
                JOIN partner_companies pc ON pc.id = po.partner_company_id
                WHERE po.client_id = ? AND po.needs_retagging = TRUE""";
        return jdbc.queryForList(sql, clientId);
    }

    private Set<String> loadPoSkus(UUID poId) {
        String sql = """
                SELECT DISTINCT p.sku FROM purchase_order_lines pol
                JOIN products p ON p.id = pol.product_id
                WHERE pol.purchase_order_id = ?""";
        List<String> skus = jdbc.queryForList(sql, String.class, poId);
        return new HashSet<>(skus);
    }

    private BigDecimal loadEligibleProductLineTotal(UUID poId, Set<String> eligibleSkus) {
        if (eligibleSkus.isEmpty()) {
            return BigDecimal.ZERO;
        }
        String placeholders = eligibleSkus.stream().map(s -> "?").collect(Collectors.joining(","));
        String sql = "SELECT COALESCE(SUM(pol.line_total), 0) FROM purchase_order_lines pol "
                + "JOIN products p ON p.id = pol.product_id "
                + "WHERE pol.purchase_order_id = ? AND p.sku IN (" + placeholders + ")";

        List<Object> params = new ArrayList<>();
        params.add(poId);
        params.addAll(eligibleSkus);
        return jdbc.queryForObject(sql, BigDecimal.class, params.toArray());
    }

    // ---- Participant eligibility checks ----

    /**
     * Check participant-level eligibility for a PO against an incentive.
     *
     * @return null if eligible, or a reason string if ineligible
     */
    private String checkParticipantEligibility(
            Map<String, Object> poData, Incentive incentive) {

        // 1. Date range: incentive start_date/end_date vs PO booking date
        Object orderDateObj = poData.get("order_date");
        if (orderDateObj != null) {
            Instant orderDate = toInstant(orderDateObj);
            if (incentive.getStartDate() != null && orderDate.isBefore(incentive.getStartDate())) {
                return "PO booking date is before incentive start date";
            }
            if (incentive.getEndDate() != null && orderDate.isAfter(incentive.getEndDate())) {
                return "PO booking date is after incentive end date";
            }
        }

        // Load audience rules grouped by type
        Map<String, List<IncentiveAudienceRule>> rulesByType = new HashMap<>();
        for (IncentiveAudienceRule rule : incentive.getAudienceRules()) {
            rulesByType
                    .computeIfAbsent(rule.getRuleType(), k -> new ArrayList<>())
                    .add(rule);
        }

        // 2. Location check (dynamic multi-level)
        List<IncentiveAudienceRule> locationRules = rulesByType.getOrDefault("LOCATION", List.of());
        if (!locationRules.isEmpty()) {
            Map<UUID, Set<UUID>> partnerLocations = parseLocationData(poData.get("location_data"));
            // Group location rules by level
            Map<UUID, List<String>> rulesByLevel = new HashMap<>();
            for (IncentiveAudienceRule rule : locationRules) {
                if (rule.getLocationLevel() != null) {
                    rulesByLevel.computeIfAbsent(rule.getLocationLevel().getId(), k -> new ArrayList<>())
                            .add(rule.getRuleValue());
                }
            }
            for (Map.Entry<UUID, List<String>> entry : rulesByLevel.entrySet()) {
                UUID levelId = entry.getKey();
                List<String> requiredValueIds = entry.getValue();
                Set<UUID> partnerValues = partnerLocations.getOrDefault(levelId, Set.of());
                boolean anyMatch = requiredValueIds.stream().anyMatch(rv -> {
                    try { return partnerValues.contains(UUID.fromString(rv)); }
                    catch (IllegalArgumentException e) { return false; }
                });
                if (!anyMatch) {
                    return "Partner location not in incentive audience locations";
                }
            }
        }

        // 3. Partner type check
        String poPartnerType = (String) poData.get("partner_type");
        List<IncentiveAudienceRule> partnerTypeRules = rulesByType.getOrDefault("PARTNER_TYPE", List.of());
        if (!partnerTypeRules.isEmpty() && poPartnerType != null) {
            boolean typeMatch = partnerTypeRules.stream()
                    .anyMatch(r -> r.getRuleValue().equalsIgnoreCase(poPartnerType));
            if (!typeMatch) {
                return "Partner type '" + poPartnerType
                        + "' not in incentive audience partner types";
            }
        }

        // 4. Specific partners check
        String specificPartners = incentive.getSpecificPartners();
        if (specificPartners != null && !specificPartners.isBlank()) {
            List<String> allowedPartnerIds = parseStringList(specificPartners);
            if (!allowedPartnerIds.isEmpty()) {
                String externalPartnerId = (String) poData.get("external_partner_id");
                if (externalPartnerId == null
                        || !allowedPartnerIds.contains(externalPartnerId)) {
                    return "Partner '" + externalPartnerId
                            + "' not in incentive specific partners list";
                }
            }
        }

        return null;
    }

    /**
     * Check dynamic eligibility fields configured via the builder config.
     * These are custom fields (is_eligibility=true, is_system=false) that client admins
     * add to the Participant Eligibility / audience section.
     *
     * @param clientId  the client
     * @param incentive the incentive being evaluated
     * @param poData    the PO row joined with partner_companies data
     * @return null if all dynamic fields pass, or a reason string if any field fails
     */
    private String checkDynamicEligibility(UUID clientId, Incentive incentive,
                                           Map<String, Object> poData) {
        List<BuilderFieldConfig> dynamicFields = builderFieldConfigRepository
                .findDynamicEligibilityFields(
                        clientId,
                        incentive.getIncentiveType().name(),
                        "audience");

        if (dynamicFields.isEmpty()) {
            return null;
        }

        // Parse the incentive's custom field values JSON (admin-chosen values per field)
        Map<String, List<String>> incentiveFieldValues = parseCustomFieldValues(
                incentive.getCustomFieldValues());

        for (BuilderFieldConfig field : dynamicFields) {
            String fieldKey = field.getFieldKey();
            List<String> expectedValues = incentiveFieldValues.get(fieldKey);

            // If admin did not set values for this field on this incentive, skip it
            if (expectedValues == null || expectedValues.isEmpty()) {
                continue;
            }

            // TODO: Wire dynamic eligibility field data lookup from partner data object
            // The actual value should be resolved from the partner's data object using
            // field.getDataObjectField().getId() to look up the value in the partner's
            // data record. For now, fall back to checking if poData contains the field key
            // directly (which works for fields that map to partner_companies columns).
            DataObjectField doField = field.getDataObjectField();
            String actualValue = resolvePartnerFieldValue(doField, poData);

            if (actualValue == null) {
                return "Dynamic eligibility field '" + field.getDisplayName()
                        + "' has no value for this partner";
            }

            // Compare based on field type
            boolean matched;
            if ("MULTI_SELECT".equalsIgnoreCase(field.getFieldType())) {
                matched = expectedValues.stream()
                        .anyMatch(v -> v.equalsIgnoreCase(actualValue));
            } else {
                // TEXT_BOX and other types: exact equality against first expected value
                matched = expectedValues.stream()
                        .anyMatch(v -> v.equalsIgnoreCase(actualValue));
            }

            if (!matched) {
                return "Partner value '" + actualValue + "' for field '"
                        + field.getDisplayName() + "' not in expected values "
                        + expectedValues;
            }
        }

        return null;
    }

    /**
     * Resolve the actual partner field value from PO data using the linked data object field.
     */
    private String resolvePartnerFieldValue(DataObjectField doField, Map<String, Object> poData) {
        if (doField == null) {
            return null;
        }
        Object rawValue = poData.get(doField.getName());
        return rawValue != null ? rawValue.toString() : null;
    }

    /**
     * Parse the incentive's custom_field_values JSON column into a map of field key -> values.
     * Expected format: {"fieldKey": ["value1", "value2"], "otherField": ["single"]}
     * Also handles: {"fieldKey": "singleValue"}
     */
    private Map<String, List<String>> parseCustomFieldValues(String json) {
        Map<String, List<String>> result = new HashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }

        // Minimal JSON parsing — the JSON is a simple object with string or array values
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return result;
        }

        // Remove outer braces
        trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        if (trimmed.isEmpty()) {
            return result;
        }

        // Split on top-level commas (not inside brackets/quotes)
        int depth = 0;
        boolean inQuote = false;
        int start = 0;
        List<String> entries = new ArrayList<>();

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (!inQuote) {
                if (c == '[' || c == '{') {
                    depth++;
                } else if (c == ']' || c == '}') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    entries.add(trimmed.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        entries.add(trimmed.substring(start).trim());

        for (String entry : entries) {
            int colonIdx = entry.indexOf(':');
            if (colonIdx < 0) {
                continue;
            }
            String key = entry.substring(0, colonIdx).trim();
            String value = entry.substring(colonIdx + 1).trim();

            // Remove quotes from key
            key = key.replaceAll("^\"|\"$", "");

            if (value.startsWith("[")) {
                // Array value — parse as list
                result.put(key, parseStringList(value));
            } else {
                // Single value
                String cleaned = value.replaceAll("^\"|\"$", "");
                if (!cleaned.isEmpty()) {
                    result.put(key, List.of(cleaned));
                }
            }
        }

        return result;
    }

    // ---- Requirement evaluation ----

    /**
     * Evaluate all Section 5 requirements for a PO.
     *
     * @return null if all requirements pass, or a reason string if any requirement fails
     */
    private String evaluateRequirements(UUID poId, BigDecimal poTotal, String customerSegment,
                                        Set<String> poSkus,
                                        List<SalesRequirement> requirements) {
        for (SalesRequirement req : requirements) {
            List<EligibilityRuleGroup> groups = req.getEligibilityGroups();
            if (groups.isEmpty()) {
                continue;
            }

            boolean anyGroupMatches = false;
            for (EligibilityRuleGroup group : groups) {
                if (evaluateRuleGroup(poId, poTotal, customerSegment, poSkus, group)) {
                    anyGroupMatches = true;
                    break; // OR between groups — one match is enough
                }
            }

            if (!anyGroupMatches) {
                return "Requirement '" + req.getName() + "' not satisfied: "
                        + "no eligibility rule group matched";
            }
        }
        return null;
    }

    /**
     * Evaluate a single rule group — all rules within must match (AND logic).
     */
    private boolean evaluateRuleGroup(UUID poId, BigDecimal poTotal, String customerSegment,
                                      Set<String> poSkus, EligibilityRuleGroup group) {
        for (EligibilityRule rule : group.getRules()) {
            if (!evaluateRule(poTotal, customerSegment, poSkus, rule)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluate a single eligibility rule against PO data already loaded in memory.
     */
    private boolean evaluateRule(BigDecimal poTotal, String customerSegment,
                                 Set<String> poSkus, EligibilityRule rule) {
        return switch (rule.getRuleType()) {
            case PRODUCTS -> evaluateProductsRule(poSkus, rule);
            case BOOKING_AMOUNT -> evaluateBookingAmountRule(poTotal, rule);
            case CUSTOMER_TYPE -> evaluateCustomerTypeRule(customerSegment, rule);
        };
    }

    private boolean evaluateProductsRule(Set<String> poSkus, EligibilityRule rule) {
        String selectedProducts = rule.getSelectedProducts();
        if (selectedProducts == null || selectedProducts.isBlank()) {
            return false;
        }
        List<String> requiredSkus = parseStringList(selectedProducts);
        if (requiredSkus.isEmpty()) {
            return false;
        }
        // PO must have at least one matching SKU
        for (String sku : requiredSkus) {
            if (poSkus.contains(sku)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateBookingAmountRule(BigDecimal poTotal, EligibilityRule rule) {
        if (rule.getOperator() == null || rule.getValue() == null || poTotal == null) {
            return false;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(rule.getValue().trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid booking amount value: {}", rule.getValue());
            return false;
        }
        BigDecimal valueMax = null;
        if (rule.getValueMax() != null) {
            try {
                valueMax = new BigDecimal(rule.getValueMax().trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid booking amount valueMax: {}", rule.getValueMax());
            }
        }
        return evaluateNumericCondition(poTotal, rule.getOperator(), value, valueMax);
    }

    private boolean evaluateCustomerTypeRule(String customerSegment, EligibilityRule rule) {
        if (rule.getOperator() == null || rule.getValue() == null || customerSegment == null) {
            return false;
        }
        return switch (rule.getOperator()) {
            case EQUALS -> customerSegment.equals(rule.getValue().trim());
            case IN -> {
                List<String> values = parseStringList(rule.getValue());
                yield values.contains(customerSegment);
            }
            case NOT_IN -> {
                List<String> values = parseStringList(rule.getValue());
                yield !values.contains(customerSegment);
            }
            default -> false;
        };
    }

    private boolean evaluateNumericCondition(BigDecimal actual, RuleOperator op,
                                             BigDecimal value, BigDecimal valueMax) {
        return switch (op) {
            case GREATER_THAN -> actual.compareTo(value) > 0;
            case GREATER_THAN_OR_EQUAL -> actual.compareTo(value) >= 0;
            case LESS_THAN -> actual.compareTo(value) < 0;
            case EQUALS -> actual.compareTo(value) == 0;
            case BETWEEN -> {
                if (valueMax == null) {
                    yield false;
                }
                yield actual.compareTo(value) >= 0 && actual.compareTo(valueMax) <= 0;
            }
            default -> false;
        };
    }

    // ---- Payout calculation ----

    /**
     * Calculate payouts for an eligible PO for a given requirement.
     * Returns one row per payout config (i.e., per currency).
     */
    private List<Object[]> calculatePayouts(UUID mappingId, UUID poId,
                                            SalesRequirement req, BigDecimal poTotal,
                                            Set<String> poSkus) {
        List<Object[]> rows = new ArrayList<>();

        for (PayoutConfig config : req.getPayouts()) {
            BigDecimal baseAmount = poTotal;

            // If against='ELIGIBLE_PRODUCTS', sum only eligible product line totals
            if ("ELIGIBLE_PRODUCTS".equals(config.getAgainst())) {
                Set<String> eligibleSkus = collectEligibleSkus(req);
                eligibleSkus.retainAll(poSkus);
                baseAmount = loadEligibleProductLineTotal(poId, eligibleSkus);
            }

            BigDecimal payout = calculatePayoutFromBands(config, baseAmount);
            if (payout == null) {
                continue;
            }

            // Apply max_per_deal cap
            if (config.getMaxPerDeal() != null
                    && payout.compareTo(config.getMaxPerDeal()) > 0) {
                payout = config.getMaxPerDeal();
            }

            java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
            rows.add(new Object[]{
                    UUID.randomUUID(), mappingId, req.getId(),
                    config.getCurrencyId(), payout,
                    now, now
            });
        }

        return rows;
    }

    /**
     * Find the matching band and compute payout based on config type.
     */
    private BigDecimal calculatePayoutFromBands(PayoutConfig config, BigDecimal baseAmount) {
        if (config.getBands().isEmpty() || baseAmount == null) {
            return null;
        }

        // Find the matching band based on baseAmount
        PayoutBand matchingBand = null;
        for (PayoutBand band : config.getBands()) {
            boolean aboveMin = baseAmount.compareTo(band.getMinAmount()) >= 0;
            boolean belowMax = band.getMaxAmount() == null
                    || baseAmount.compareTo(band.getMaxAmount()) <= 0;
            if (aboveMin && belowMax) {
                matchingBand = band;
                break;
            }
        }

        if (matchingBand == null) {
            return null;
        }

        if (config.getPayoutType() == PayoutType.FLAT) {
            return matchingBand.getPayoutValue();
        }

        // PERCENTAGE: payout_value is a percentage of the base amount
        return baseAmount.multiply(matchingBand.getPayoutValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Collect all eligible SKUs from PRODUCTS rules across all rule groups in a requirement.
     */
    private Set<String> collectEligibleSkus(SalesRequirement req) {
        Set<String> skus = new HashSet<>();
        for (EligibilityRuleGroup group : req.getEligibilityGroups()) {
            for (EligibilityRule rule : group.getRules()) {
                if (rule.getRuleType() == EligibilityRuleType.PRODUCTS
                        && rule.getSelectedProducts() != null) {
                    skus.addAll(parseStringList(rule.getSelectedProducts()));
                }
            }
        }
        return skus;
    }

    // ---- Database writes ----

    private void deleteExistingMappings(UUID poId) {
        jdbc.update("DELETE FROM po_eligibility_mappings WHERE purchase_order_id = ?", poId);
    }

    private Object[] buildMappingRow(UUID clientId, UUID poId, UUID incentiveId,
                                     UUID taggingJobId, boolean eligible, String reason) {
        return buildMappingRowWithId(UUID.randomUUID(), clientId, poId, incentiveId,
                taggingJobId, eligible, reason);
    }

    private Object[] buildMappingRowWithId(UUID id, UUID clientId, UUID poId, UUID incentiveId,
                                            UUID taggingJobId, boolean eligible, String reason) {
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        return new Object[]{
                id, clientId, poId, incentiveId, taggingJobId,
                eligible, reason, now, now
        };
    }

    private void flushMappingBatch(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO po_eligibility_mappings "
                + "(id, client_id, purchase_order_id, incentive_id, tagging_job_id, "
                + "eligible, ineligibility_reason, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        batchInsert(sql, batch);
    }

    private void flushPayoutBatch(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO eligibility_payouts "
                + "(id, eligibility_mapping_id, requirement_id, "
                + "currency_id, payout_amount, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        batchInsert(sql, batch);
    }

    private void batchInsert(String sql, List<Object[]> batch) {
        for (int i = 0; i < batch.size(); i += BATCH_SIZE) {
            List<Object[]> chunk = batch.subList(i, Math.min(i + BATCH_SIZE, batch.size()));
            jdbc.batchUpdate(sql, chunk);
        }
    }

    private void clearRetaggingFlags(List<Map<String, Object>> posToRetag) {
        String sql = "UPDATE purchase_orders SET needs_retagging = FALSE WHERE id = ?";
        List<Object[]> batch = posToRetag.stream()
                .map(po -> new Object[]{po.get("id")})
                .toList();
        batchInsert(sql, batch);
    }

    // ---- Utility ----

    /**
     * Parse a string that might be a JSON array or comma-separated list.
     */
    private List<String> parseStringList(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        String cleaned = input.trim();
        // Handle JSON array format: ["srv-001","rtr-001"]
        if (cleaned.startsWith("[")) {
            cleaned = cleaned.substring(1);
            if (cleaned.endsWith("]")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            return Arrays.stream(cleaned.split(","))
                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        // Comma-separated
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses the location_data JSON array from the PO query into a Map of levelId to valueIds.
     * Expected format: [{"level_id":"uuid","value_id":"uuid"}, ...]
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, Set<UUID>> parseLocationData(Object locationDataObj) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        if (locationDataObj == null) return result;
        try {
            String json = locationDataObj.toString();
            if (json.isBlank() || "null".equals(json)) return result;
            List<Map<String, String>> entries = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            for (Map<String, String> entry : entries) {
                UUID levelId = UUID.fromString(entry.get("level_id"));
                UUID valueId = UUID.fromString(entry.get("value_id"));
                result.computeIfAbsent(levelId, k -> new HashSet<>()).add(valueId);
            }
        } catch (Exception e) {
            log.warn("Failed to parse location_data JSON: {}", e.getMessage());
        }
        return result;
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant inst) {
            return inst;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.LocalDate ld) {
            return ld.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        }
        // Handle string values — try LocalDate first, then Instant
        String str = value.toString();
        try {
            return Instant.parse(str);
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return java.time.LocalDate.parse(str)
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            } catch (java.time.format.DateTimeParseException e2) {
                return java.time.LocalDateTime.parse(str)
                        .toInstant(java.time.ZoneOffset.UTC);
            }
        }
    }
}
