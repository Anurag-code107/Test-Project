package com.tenxengage.app.service.forecast;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls Claude API with pre-assembled forecast context and parses the structured response.
 * Falls back to statistical defaults when Claude is unavailable.
 */
@Service
public class ForecastAiService {

    private static final Logger log = LoggerFactory.getLogger(ForecastAiService.class);

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final ForecastBaselineConfig baselineConfig;
    private final String model;
    private final String systemPrompt;

    public ForecastAiService(@Autowired(required = false) @Nullable AnthropicClient client,
                              ObjectMapper objectMapper,
                              ForecastBaselineConfig baselineConfig,
                              @Value("${app.ai.model}") String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.baselineConfig = baselineConfig;
        this.model = model;
        this.systemPrompt = loadSystemPrompt();
    }

    public boolean isAvailable() {
        return client != null;
    }

    public ForecastResult generateForecast(ForecastContext context) {
        if (!isAvailable()) {
            log.warn("Claude API unavailable — using statistical fallback");
            return buildStatisticalFallback(context);
        }

        try {
            String contextJson = objectMapper.writeValueAsString(context);
            log.debug("Forecast context size: {} chars", contextJson.length());
            log.info("Forecast newIncentive context: {}",
                    contextJson.substring(0, Math.min(600, contextJson.length())));

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(8192L)
                    .system(systemPrompt)
                    .addMessage(MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(MessageParam.Content.ofString(contextJson))
                            .build())
                    .build();

            Message response = client.messages().create(params);

            String responseText = response.content().stream()
                    .filter(block -> block.isText())
                    .map(block -> block.asText().text())
                    .reduce("", String::concat);

            log.debug("Claude forecast response length: {} chars", responseText.length());
            log.info("Claude forecast raw response: {}", responseText.substring(0, Math.min(500, responseText.length())));
            return parseResponse(responseText, context);

        } catch (Exception e) {
            log.error("Claude API call failed for forecast — falling back to statistical: {}", e.getMessage(), e);
            return buildStatisticalFallback(context);
        }
    }

    // ── Response Parsing ───────────────────────────────────────────────────────

    private ForecastResult parseResponse(String responseText, ForecastContext context) {
        try {
            // Claude sometimes precedes the JSON with chain-of-thought preamble or wraps
            // it in markdown fences. Locate the outermost JSON object so the parse works
            // regardless of what surrounds it.
            String json = extractJsonObject(responseText);

            JsonNode root = objectMapper.readTree(json);

            BigDecimal budgetUtilPct = clamp(decimalOrZero(root, "budgetUtilizationPct"), 0, 100);
            int netNewDeals = Math.max(0, intOrZero(root, "netNewDeals"));
            BigDecimal netNewBookings = decimalOrZero(root, "netNewBookings").max(BigDecimal.ZERO);
            int participation = Math.max(0, intOrZero(root, "estimatedParticipation"));
            BigDecimal participationRate = clamp(decimalOrZero(root, "participationRate"), 0, 100);
            BigDecimal totalCost = decimalOrZero(root, "estimatedTotalCost").max(BigDecimal.ZERO);
            BigDecimal roi = decimalOrZero(root, "roi");
            BigDecimal confidence = clamp(decimalOrZero(root, "confidenceScore"), 0, 95);

            List<ForecastResult.LocationBreakdown> locations = parseLocationBreakdown(root);
            List<ForecastResult.MonthlyProjection> projections = parseMonthlyProjections(root);
            List<ForecastResult.Insight> insights = parseInsights(root);
            Map<String, List<ForecastResult.Insight>> topLevelInsights = parseTopLevelInsights(root);
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText("") : "";

            // Fix 4 — Flag targeted locations with no baseline data. Zero out their
            // contribution in locationBreakdown (defense in depth — Claude usually does
            // this on its own, but we don't want to ship a fabricated number if it slips),
            // recompute parent metrics as the sum of their remaining children, and surface
            // a single warning insight that names the missing locations. We also lower the
            // global totals to match the new top-level sum so the UI doesn't show a global
            // figure that double-counts the zeroed-out contribution.
            List<String> unbaselinedTargets = findUnbaselinedTargets(context);
            if (!unbaselinedTargets.isEmpty()) {
                locations = zeroOutUnbaselined(locations, unbaselinedTargets);
                locations = reconcileParentsFromChildren(locations);
                BigDecimal newGlobalBookings = locations.stream()
                        .filter(lb -> lb.parentId() == null)
                        .map(ForecastResult.LocationBreakdown::netNewBookings)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                int newGlobalDeals = locations.stream()
                        .filter(lb -> lb.parentId() == null)
                        .mapToInt(ForecastResult.LocationBreakdown::netNewDeals)
                        .sum();
                if (newGlobalBookings.compareTo(BigDecimal.ZERO) > 0) {
                    netNewBookings = newGlobalBookings;
                }
                if (newGlobalDeals > 0) {
                    netNewDeals = newGlobalDeals;
                }
                insights.add(new ForecastResult.Insight(
                        "warning",
                        "Targeted locations missing baseline data",
                        "No historical sales data exists for: " + String.join(", ", unbaselinedTargets)
                                + ". Their forecast contribution is held at zero — totals reflect only "
                                + "locations with baselines. Either exclude these from eligibility, or "
                                + "treat actual results in those markets as exploratory.",
                        95
                ));
            }

            // Guardrail: cap net new bookings using type-aware max ROI multiplier
            String incentiveType = context.newIncentive() != null ? context.newIncentive().type() : "SALES";
            BigDecimal maxRoiMultiplier = baselineConfig.getDefaultsForType(incentiveType).getMaxRoiCap();
            BigDecimal maxReasonableBookings = totalCost.multiply(maxRoiMultiplier);

            if (maxReasonableBookings.compareTo(BigDecimal.ZERO) > 0
                    && netNewBookings.compareTo(maxReasonableBookings) > 0) {
                BigDecimal originalBookings = netNewBookings;
                int originalDeals = netNewDeals;
                int originalParticipation = participation;
                log.warn("Claude returned netNewBookings={} which exceeds {}x cost {}. Clamping to {}",
                        netNewBookings, maxRoiMultiplier, totalCost, maxReasonableBookings);
                netNewBookings = maxReasonableBookings;

                // Fix 3 — Scale deals AND participation by the same ratio so
                // deals-per-participant stays consistent with what Claude originally
                // projected. Recomputing deals from baseline avg deal size (the prior
                // behavior) produced incoherent ratios like 8 deals across 83 partners.
                BigDecimal clampRatio = originalBookings.compareTo(BigDecimal.ZERO) > 0
                        ? maxReasonableBookings.divide(originalBookings, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ONE;
                netNewDeals = Math.max(0, BigDecimal.valueOf(originalDeals)
                        .multiply(clampRatio).setScale(0, RoundingMode.HALF_UP).intValue());
                participation = Math.max(0, BigDecimal.valueOf(originalParticipation)
                        .multiply(clampRatio).setScale(0, RoundingMode.HALF_UP).intValue());

                // Clamp per-location breakdowns proportionally
                locations = clampRegionalBookings(locations, totalCost, maxRoiMultiplier, context);

                // Fix 1 — Surface the clamp so users know the displayed number is a
                // capped ceiling rather than the AI's actual estimate.
                BigDecimal originalRoi = totalCost.compareTo(BigDecimal.ZERO) > 0
                        ? originalBookings.divide(totalCost, 1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                insights.add(new ForecastResult.Insight(
                        "warning",
                        "AI estimate clamped to ROI cap",
                        "The AI projected " + formatDollars(originalBookings) + " in net new bookings ("
                                + originalRoi.stripTrailingZeros().toPlainString() + "x ROI), which exceeds "
                                + "the " + maxRoiMultiplier.stripTrailingZeros().toPlainString()
                                + "x sanity cap for this incentive type. Displayed values are clamped to "
                                + formatDollars(maxReasonableBookings) + ". Treat the forecast as an upper "
                                + "bound; the underlying calculation likely overstated the addressable cohort.",
                        95
                ));
            }

            // Always recompute ROI from cost and bookings (don't trust Claude's ROI)
            roi = totalCost.compareTo(BigDecimal.ZERO) > 0
                    ? netNewBookings.divide(totalCost, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Per-location consistency validation: ensure top-level breakdowns sum to global totals
            locations = enforceRegionalConsistency(locations, netNewBookings, netNewDeals);

            BigDecimal dataQuality = computeDataQuality(context);

            // Confidence post-validation: prevent overconfident scores with sparse data
            confidence = adjustConfidence(confidence, context, dataQuality);
            List<String> similarIds = context.similarPastIncentives().stream()
                    .map(ForecastContext.SimilarIncentive::id)
                    .toList();

            // Cap at 3 insights per scope (global + each top-level) by Claude's
            // self-rated confidence, falling back to original order on ties.
            insights = trimToTopThree(insights);
            topLevelInsights = trimTopLevelInsights(topLevelInsights);

            return new ForecastResult(
                    budgetUtilPct, netNewDeals, netNewBookings,
                    participation, participationRate, totalCost, roi, confidence,
                    locations, projections, insights, topLevelInsights, reasoning,
                    "v1-claude", dataQuality, similarIds
            );

        } catch (Exception e) {
            log.error("Failed to parse Claude forecast response: {}", e.getMessage(), e);
            return buildStatisticalFallback(context);
        }
    }

    /**
     * Find the outermost JSON object in a response. Claude may emit chain-of-thought
     * preamble or wrap the JSON in ```json fences; this scans for the first '{' and
     * walks brace depth (with string-literal awareness) to the matching '}'.
     */
    private String extractJsonObject(String responseText) {
        if (responseText == null) throw new IllegalArgumentException("Empty response");
        int start = responseText.indexOf('{');
        if (start < 0) throw new IllegalArgumentException("No JSON object found in response");

        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < responseText.length(); i++) {
            char c = responseText.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return responseText.substring(start, i + 1);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON object in response");
    }

    private List<ForecastResult.LocationBreakdown> parseLocationBreakdown(JsonNode root) {
        List<ForecastResult.LocationBreakdown> result = new ArrayList<>();
        JsonNode arr = root.get("locationBreakdown");
        // Back-compat: tolerate the legacy key for in-flight responses while the
        // prompt's new output shape rolls out.
        if (arr == null || !arr.isArray()) arr = root.get("regionalBreakdown");
        if (arr == null || !arr.isArray()) return result;

        for (JsonNode node : arr) {
            String name = node.has("name")
                    ? node.get("name").asText("")
                    : (node.has("region") ? node.get("region").asText("") : "");
            result.add(new ForecastResult.LocationBreakdown(
                    node.has("locationValueId") ? node.get("locationValueId").asText(null) : null,
                    name,
                    node.has("parentId") && !node.get("parentId").isNull() ? node.get("parentId").asText() : null,
                    clamp(decimalOrZero(node, "budgetUtilizedPct"), 0, 100),
                    Math.max(0, intOrZero(node, "netNewDeals")),
                    decimalOrZero(node, "netNewBookings").max(BigDecimal.ZERO),
                    decimalOrZero(node, "roi"),
                    clamp(decimalOrZero(node, "participationRate"), 0, 100),
                    decimalOrZero(node, "budgetAllocated").max(BigDecimal.ZERO),
                    decimalOrZero(node, "budgetPredictedSpend").max(BigDecimal.ZERO)
            ));
        }
        return result;
    }

    private List<ForecastResult.MonthlyProjection> parseMonthlyProjections(JsonNode root) {
        List<ForecastResult.MonthlyProjection> result = new ArrayList<>();
        JsonNode arr = root.get("monthlyProjections");
        if (arr == null || !arr.isArray()) return result;

        for (JsonNode node : arr) {
            result.add(new ForecastResult.MonthlyProjection(
                    node.has("month") ? node.get("month").asText() : "",
                    decimalOrZero(node, "revenue"),
                    decimalOrZero(node, "cost"),
                    Math.max(0, intOrZero(node, "participants"))
            ));
        }
        return result;
    }

    private List<ForecastResult.Insight> parseInsights(JsonNode root) {
        return parseInsightArray(root.get("insights"));
    }

    /**
     * Parse the per-top-level-location insights map. Keys are top-level location
     * names (whatever depth-0 is in the client's hierarchy — Region / Theater /
     * Country / etc.); values are arrays of insights for that location.
     */
    private Map<String, List<ForecastResult.Insight>> parseTopLevelInsights(JsonNode root) {
        Map<String, List<ForecastResult.Insight>> map = new LinkedHashMap<>();
        JsonNode obj = root.get("topLevelInsights");
        if (obj == null || !obj.isObject()) return map;
        java.util.Iterator<String> names = obj.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            List<ForecastResult.Insight> insights = parseInsightArray(obj.get(name));
            if (!insights.isEmpty()) map.put(name, insights);
        }
        return map;
    }

    private List<ForecastResult.Insight> parseInsightArray(JsonNode arr) {
        List<ForecastResult.Insight> result = new ArrayList<>();
        if (arr == null || !arr.isArray()) return result;

        for (JsonNode node : arr) {
            // Confidence defaults to 50 for older responses missing the field — that
            // way trim-by-confidence still produces a stable order without crashing.
            int conf = node.has("confidence") ? node.get("confidence").asInt(50) : 50;
            result.add(new ForecastResult.Insight(
                    node.has("type") ? node.get("type").asText("") : "",
                    node.has("title") ? node.get("title").asText("") : "",
                    node.has("detail") ? node.get("detail").asText("") : "",
                    Math.max(0, Math.min(100, conf))
            ));
        }
        return result;
    }

    /**
     * Cap a list of insights at 3, ranked by self-rated confidence (descending).
     * Stable on ties — original order is preserved among equal-confidence entries.
     */
    private List<ForecastResult.Insight> trimToTopThree(List<ForecastResult.Insight> insights) {
        if (insights == null || insights.size() <= 3) return insights == null ? List.of() : insights;
        List<ForecastResult.Insight> sorted = new ArrayList<>(insights);
        sorted.sort((a, b) -> Integer.compare(b.confidence(), a.confidence()));
        return new ArrayList<>(sorted.subList(0, 3));
    }

    private Map<String, List<ForecastResult.Insight>> trimTopLevelInsights(
            Map<String, List<ForecastResult.Insight>> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, List<ForecastResult.Insight>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<ForecastResult.Insight>> e : source.entrySet()) {
            out.put(e.getKey(), trimToTopThree(e.getValue()));
        }
        return out;
    }

    // ── Statistical Fallback ───────────────────────────────────────────────────

    private ForecastResult buildStatisticalFallback(ForecastContext context) {
        String type = context.newIncentive() != null ? context.newIncentive().type() : "SALES";
        ForecastBaselineConfig.IncentiveTypeDefaults defaults = baselineConfig.getDefaultsForType(type);

        BigDecimal budget = BigDecimal.ZERO;
        if (context.newIncentive() != null && context.newIncentive().budget() != null) {
            budget = context.newIncentive().budget().totalBudget();
            if (budget == null) budget = BigDecimal.ZERO;
        }

        // Use similar incentives if available for better fallback
        if (!context.similarPastIncentives().isEmpty()) {
            return buildFromSimilarIncentives(context, defaults);
        }

        BigDecimal utilPct = defaults.getUtilizationPct();
        BigDecimal totalCost = budget.multiply(utilPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        // netNewBookings = totalCost * roiMultiplier (e.g. cost $55K * 2.5x = $137.5K incremental)
        BigDecimal netNewBookings = totalCost.multiply(defaults.getRoiMultiplier());
        // ROI is an x-multiplier: netNewBookings / totalCost
        BigDecimal roi = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? netNewBookings.divide(totalCost, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int eligiblePartners = context.newIncentive() != null
                ? context.newIncentive().audienceSize().eligiblePartners() : 100;
        int participation = (int) (eligiblePartners * defaults.getParticipationPct().doubleValue() / 100);
        BigDecimal avgDealSize = computeBaselineAvgDealSize(context);
        int netNewDeals = avgDealSize.compareTo(BigDecimal.ZERO) > 0
                ? netNewBookings.divide(avgDealSize, 0, RoundingMode.HALF_UP).intValue()
                : Math.max(1, (int) (budget.doubleValue() / 2500));

        BigDecimal confidence = new BigDecimal("35.00");
        BigDecimal dataQuality = computeDataQuality(context);

        List<ForecastResult.Insight> insights = List.of(
                new ForecastResult.Insight("warning",
                        "Limited Historical Data",
                        "Predictions based on industry benchmarks. Accuracy will improve as more incentive data accumulates.",
                        80)
        );

        return new ForecastResult(
                utilPct, netNewDeals, netNewBookings,
                participation, defaults.getParticipationPct(), totalCost, roi, confidence,
                List.of(), List.of(), insights, Map.of(),
                "Statistical fallback — AI insights unavailable",
                "statistical-fallback", dataQuality, List.of()
        );
    }

    private ForecastResult buildFromSimilarIncentives(ForecastContext context,
                                                       ForecastBaselineConfig.IncentiveTypeDefaults defaults) {
        List<ForecastContext.SimilarIncentive> similar = context.similarPastIncentives();

        // Weighted average of similar incentive outcomes
        double totalWeight = 0;
        double weightedUtil = 0;
        double weightedParticipation = 0;
        double weightedRoi = 0;

        for (ForecastContext.SimilarIncentive s : similar) {
            double w = s.similarityScore();
            totalWeight += w;
            if (s.actualUtilizationPct() != null) weightedUtil += s.actualUtilizationPct().doubleValue() * w;
            if (s.actualParticipationRate() != null) weightedParticipation += s.actualParticipationRate().doubleValue() * w;
            if (s.actualRoi() != null) weightedRoi += s.actualRoi().doubleValue() * w;
        }

        BigDecimal utilPct = totalWeight > 0
                ? BigDecimal.valueOf(weightedUtil / totalWeight).setScale(2, RoundingMode.HALF_UP)
                : defaults.getUtilizationPct();
        BigDecimal partRate = totalWeight > 0
                ? BigDecimal.valueOf(weightedParticipation / totalWeight).setScale(2, RoundingMode.HALF_UP)
                : defaults.getParticipationPct();

        BigDecimal budget = context.newIncentive().budget() != null
                ? context.newIncentive().budget().totalBudget() : BigDecimal.ZERO;
        if (budget == null) budget = BigDecimal.ZERO;

        BigDecimal totalCost = budget.multiply(utilPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // The weighted ROI from outcomes is stored as a percentage in the DB (e.g. 150 = 150%).
        // Convert to x-multiplier: 150% → 1.5x, then add 1 to get total multiplier.
        // Actually, the DB stores (cost*2.5 - cost)/cost*100 = 150, meaning the INCREMENTAL ratio is 1.5x.
        // So netNewBookings = totalCost * (roiPct / 100), giving the incremental revenue.
        double rawRoiPct = totalWeight > 0 ? weightedRoi / totalWeight : 0;
        BigDecimal roiMultiplier = BigDecimal.valueOf(rawRoiPct / 100).setScale(2, RoundingMode.HALF_UP);

        // netNewBookings = totalCost * roiMultiplier (incremental revenue above baseline)
        BigDecimal netNewBookings = totalCost.multiply(roiMultiplier);
        // ROI as x-multiplier
        BigDecimal roiVal = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? netNewBookings.divide(totalCost, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal avgDealSize = computeBaselineAvgDealSize(context);
        int netNewDeals = avgDealSize.compareTo(BigDecimal.ZERO) > 0
                ? Math.max(1, netNewBookings.divide(avgDealSize, 0, RoundingMode.HALF_UP).intValue())
                : 0;

        int eligiblePartners = context.newIncentive().audienceSize().eligiblePartners();
        int participation = (int) (eligiblePartners * partRate.doubleValue() / 100);

        BigDecimal confidence = BigDecimal.valueOf(Math.min(55, 35 + similar.size() * 5))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal dataQuality = computeDataQuality(context);

        List<String> similarIds = similar.stream().map(ForecastContext.SimilarIncentive::id).toList();

        List<ForecastResult.Insight> insights = List.of(
                new ForecastResult.Insight("warning",
                        "Statistical Fallback Active",
                        "AI analysis unavailable. Predictions based on weighted averages of " + similar.size()
                                + " similar past incentives.",
                        80)
        );

        return new ForecastResult(
                utilPct, netNewDeals, netNewBookings,
                participation, partRate, totalCost, roiVal, confidence,
                List.of(), List.of(), insights, Map.of(),
                "Statistical fallback from " + similar.size() + " similar incentives",
                "statistical-fallback", dataQuality, similarIds
        );
    }

    // ── Validation Helpers ──────────────────────────────────────────────────────

    /**
     * Compute the total baseline revenue for the incentive period from the context data.
     * This represents organic sales that would happen WITHOUT the incentive.
     */
    private BigDecimal computeBaselineRevenue(ForecastContext context) {
        BigDecimal totalMonthlyRevenue = BigDecimal.ZERO;

        // Sum monthly revenue across all regions
        for (var entry : context.baselineSalesByRegion().entrySet()) {
            totalMonthlyRevenue = totalMonthlyRevenue.add(entry.getValue().avgMonthlyRevenue());
        }

        // If no region data, try product category data
        if (totalMonthlyRevenue.compareTo(BigDecimal.ZERO) == 0) {
            for (var entry : context.baselineSalesByProductCategory().entrySet()) {
                totalMonthlyRevenue = totalMonthlyRevenue.add(entry.getValue().avgMonthlyRevenue());
            }
        }

        // Multiply by incentive duration in months
        int months = 3; // default
        if (context.newIncentive() != null && context.newIncentive().duration() != null) {
            months = Math.max(1, context.newIncentive().duration().days() / 30);
        }

        return totalMonthlyRevenue.multiply(BigDecimal.valueOf(months));
    }

    /**
     * Find targeted location names that have no entry in baselineSalesByRegion.
     * Returns names sorted alphabetically for stable insight wording across runs.
     */
    private List<String> findUnbaselinedTargets(ForecastContext context) {
        if (context.newIncentive() == null
                || context.newIncentive().targetLocations() == null
                || context.baselineSalesByRegion() == null) {
            return List.of();
        }
        java.util.Set<String> baselineKeys = context.baselineSalesByRegion().keySet();
        java.util.Set<String> missing = new java.util.TreeSet<>();
        for (List<String> names : context.newIncentive().targetLocations().values()) {
            if (names == null) continue;
            for (String name : names) {
                if (name != null && !baselineKeys.contains(name)) {
                    missing.add(name);
                }
            }
        }
        return new ArrayList<>(missing);
    }

    /**
     * Zero out per-location bookings/deals/budget for any row whose name appears in
     * the unbaselined list. Keeps the row in place so the parent's children list is
     * complete; enforceRegionalConsistency adjusts parent totals afterward.
     */
    private List<ForecastResult.LocationBreakdown> zeroOutUnbaselined(
            List<ForecastResult.LocationBreakdown> locations,
            List<String> unbaselined) {
        java.util.Set<String> drop = new java.util.HashSet<>(unbaselined);
        List<ForecastResult.LocationBreakdown> out = new ArrayList<>(locations.size());
        for (ForecastResult.LocationBreakdown lb : locations) {
            if (lb.name() != null && drop.contains(lb.name())) {
                out.add(new ForecastResult.LocationBreakdown(
                        lb.locationValueId(), lb.name(), lb.parentId(),
                        BigDecimal.ZERO,           // budgetUtilizedPct
                        0,                         // netNewDeals
                        BigDecimal.ZERO,           // netNewBookings
                        BigDecimal.ZERO,           // roi
                        BigDecimal.ZERO,           // participationRate
                        BigDecimal.ZERO,           // budgetAllocated
                        BigDecimal.ZERO));         // budgetPredictedSpend
            } else {
                out.add(lb);
            }
        }
        return out;
    }

    /**
     * After zeroing out unbaselined leaves, walk bottom-up and replace each parent's
     * netNewBookings / netNewDeals / budgetAllocated / budgetPredictedSpend with the
     * sum of its children. Without this, a parent like APJ keeps the original sum
     * Claude assigned (which included the zeroed children's contribution) and
     * desegments visually from its children in the UI.
     */
    private List<ForecastResult.LocationBreakdown> reconcileParentsFromChildren(
            List<ForecastResult.LocationBreakdown> locations) {
        java.util.Map<String, List<ForecastResult.LocationBreakdown>> childrenByParentId = new java.util.HashMap<>();
        for (ForecastResult.LocationBreakdown lb : locations) {
            if (lb.parentId() != null) {
                childrenByParentId.computeIfAbsent(lb.parentId(), k -> new ArrayList<>()).add(lb);
            }
        }
        if (childrenByParentId.isEmpty()) return locations;

        // Build a depth map so we can process leaves first, then their parents, etc.
        java.util.Map<String, Integer> depthById = new java.util.HashMap<>();
        for (ForecastResult.LocationBreakdown lb : locations) {
            depthById.put(lb.locationValueId(), computeDepth(lb, locations, new java.util.HashSet<>()));
        }
        List<ForecastResult.LocationBreakdown> ordered = new ArrayList<>(locations);
        ordered.sort((a, b) -> Integer.compare(
                depthById.getOrDefault(b.locationValueId(), 0),
                depthById.getOrDefault(a.locationValueId(), 0)));

        java.util.Map<String, ForecastResult.LocationBreakdown> byId = new java.util.HashMap<>();
        for (ForecastResult.LocationBreakdown lb : locations) byId.put(lb.locationValueId(), lb);

        for (ForecastResult.LocationBreakdown node : ordered) {
            List<ForecastResult.LocationBreakdown> kids = childrenByParentId.get(node.locationValueId());
            if (kids == null || kids.isEmpty()) continue;
            // Always re-read children from byId so prior bottom-up updates are picked up.
            BigDecimal sumBookings = BigDecimal.ZERO;
            int sumDeals = 0;
            BigDecimal sumAllocated = BigDecimal.ZERO;
            BigDecimal sumSpend = BigDecimal.ZERO;
            for (ForecastResult.LocationBreakdown kid : kids) {
                ForecastResult.LocationBreakdown current = byId.get(kid.locationValueId());
                sumBookings = sumBookings.add(current.netNewBookings());
                sumDeals += current.netNewDeals();
                sumAllocated = sumAllocated.add(current.budgetAllocated());
                sumSpend = sumSpend.add(current.budgetPredictedSpend());
            }
            BigDecimal newRoi = sumSpend.compareTo(BigDecimal.ZERO) > 0
                    ? sumBookings.divide(sumSpend, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            ForecastResult.LocationBreakdown updated = new ForecastResult.LocationBreakdown(
                    node.locationValueId(), node.name(), node.parentId(),
                    node.budgetUtilizedPct(), sumDeals, sumBookings, newRoi,
                    node.participationRate(), sumAllocated, sumSpend);
            byId.put(node.locationValueId(), updated);
        }

        // Rebuild list in original order so downstream code's expectations don't break.
        List<ForecastResult.LocationBreakdown> out = new ArrayList<>(locations.size());
        for (ForecastResult.LocationBreakdown lb : locations) {
            out.add(byId.get(lb.locationValueId()));
        }
        return out;
    }

    private int computeDepth(ForecastResult.LocationBreakdown node,
                              List<ForecastResult.LocationBreakdown> all,
                              java.util.Set<String> seen) {
        if (node.parentId() == null) return 0;
        if (!seen.add(node.locationValueId())) return 0; // cycle guard
        for (ForecastResult.LocationBreakdown candidate : all) {
            if (candidate.locationValueId().equals(node.parentId())) {
                return 1 + computeDepth(candidate, all, seen);
            }
        }
        return 0;
    }

    /** Format a BigDecimal as a compact dollar string ($X.XM / $XK). */
    private String formatDollars(BigDecimal value) {
        if (value == null) return "$0";
        double d = value.doubleValue();
        if (d >= 1_000_000) return String.format("$%.1fM", d / 1_000_000);
        if (d >= 1_000) return String.format("$%.0fK", d / 1_000);
        return String.format("$%.0f", d);
    }

    /**
     * Clamp per-location breakdown bookings and recompute deals from baseline avg deal size.
     */
    private List<ForecastResult.LocationBreakdown> clampRegionalBookings(
            List<ForecastResult.LocationBreakdown> locations,
            BigDecimal totalCost,
            BigDecimal maxRoiMultiplier,
            ForecastContext context) {

        BigDecimal avgDealSize = computeBaselineAvgDealSize(context);

        List<ForecastResult.LocationBreakdown> clamped = new ArrayList<>();
        for (ForecastResult.LocationBreakdown lb : locations) {
            BigDecimal nodeSpend = lb.budgetPredictedSpend().max(BigDecimal.ONE);
            BigDecimal maxBookings = nodeSpend.multiply(maxRoiMultiplier);
            BigDecimal clampedBookings = lb.netNewBookings().min(maxBookings);

            BigDecimal clampedRoi = nodeSpend.compareTo(BigDecimal.ZERO) > 0
                    ? clampedBookings.divide(nodeSpend, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            int clampedDeals;
            if (avgDealSize.compareTo(BigDecimal.ZERO) > 0) {
                clampedDeals = Math.max(1,
                        clampedBookings.divide(avgDealSize, 0, RoundingMode.HALF_UP).intValue());
            } else {
                clampedDeals = Math.max(1, lb.netNewDeals());
            }

            clamped.add(new ForecastResult.LocationBreakdown(
                    lb.locationValueId(), lb.name(), lb.parentId(),
                    lb.budgetUtilizedPct(), clampedDeals,
                    clampedBookings, clampedRoi, lb.participationRate(),
                    lb.budgetAllocated(), lb.budgetPredictedSpend()));
        }
        return clamped;
    }

    /**
     * Compute the baseline average deal size from the context data.
     */
    private BigDecimal computeBaselineAvgDealSize(ForecastContext context) {
        BigDecimal totalDealSize = BigDecimal.ZERO;
        int count = 0;
        for (var entry : context.baselineSalesByRegion().entrySet()) {
            if (entry.getValue().avgDealSize().compareTo(BigDecimal.ZERO) > 0) {
                totalDealSize = totalDealSize.add(entry.getValue().avgDealSize());
                count++;
            }
        }
        if (count > 0) {
            return totalDealSize.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
        // Fallback: estimate from revenue and deals
        for (var entry : context.baselineSalesByRegion().entrySet()) {
            if (entry.getValue().avgMonthlyDeals() > 0) {
                return entry.getValue().avgMonthlyRevenue()
                        .divide(BigDecimal.valueOf(entry.getValue().avgMonthlyDeals()), 2, RoundingMode.HALF_UP);
            }
        }
        return new BigDecimal("50000"); // conservative default
    }

    // ── Confidence & Consistency Validation ───────────────────────────────────

    /**
     * Adjust confidence score based on data availability.
     * Prevents Claude from claiming high confidence with sparse data.
     */
    private BigDecimal adjustConfidence(BigDecimal confidence, ForecastContext context, BigDecimal dataQuality) {
        BigDecimal adjusted = confidence;

        // No similar incentives → max 45
        if (context.similarPastIncentives().isEmpty() && adjusted.doubleValue() > 45) {
            log.info("Adjusting confidence from {} to 45 — no similar incentives", adjusted);
            adjusted = new BigDecimal("45.00");
        }

        // Fewer than 3 similar incentives → max 65
        if (context.similarPastIncentives().size() < 3 && adjusted.doubleValue() > 65) {
            log.info("Adjusting confidence from {} to 65 — only {} similar incentives",
                    adjusted, context.similarPastIncentives().size());
            adjusted = new BigDecimal("65.00");
        }

        // Very few historical incentives total → max 50
        if (context.totalHistoricalIncentives() < 3 && adjusted.doubleValue() > 50) {
            log.info("Adjusting confidence from {} to 50 — only {} total historical incentives",
                    adjusted, context.totalHistoricalIncentives());
            adjusted = new BigDecimal("50.00");
        }

        // Cap confidence based on data quality: confidence can't exceed dataQuality + 15
        BigDecimal maxByQuality = dataQuality.add(new BigDecimal("15"));
        if (adjusted.compareTo(maxByQuality) > 0) {
            log.info("Adjusting confidence from {} to {} — data quality cap (quality={})",
                    adjusted, maxByQuality, dataQuality);
            adjusted = maxByQuality.setScale(2, RoundingMode.HALF_UP);
        }

        return clamp(adjusted, 0, 95);
    }

    /**
     * Ensure top-level (depth-0) breakdowns sum to global totals (within 10%).
     * If off, proportionally scale all rows by the same ratio so children still
     * sum to parents (the prompt requires that invariant from Claude up front).
     */
    private List<ForecastResult.LocationBreakdown> enforceRegionalConsistency(
            List<ForecastResult.LocationBreakdown> locations,
            BigDecimal globalNetNewBookings,
            int globalNetNewDeals) {

        if (locations.isEmpty() || globalNetNewBookings.compareTo(BigDecimal.ZERO) == 0) {
            return locations;
        }

        BigDecimal sumBookings = locations.stream()
                .filter(lb -> lb.parentId() == null)
                .map(ForecastResult.LocationBreakdown::netNewBookings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int sumDeals = locations.stream()
                .filter(lb -> lb.parentId() == null)
                .mapToInt(ForecastResult.LocationBreakdown::netNewDeals)
                .sum();

        // If no top-level rows exist (e.g. user only picked countries), fall
        // back to the full set so the global totals still get validated against
        // something rather than silently skipping the check.
        if (sumBookings.compareTo(BigDecimal.ZERO) == 0 && sumDeals == 0) {
            sumBookings = locations.stream()
                    .map(ForecastResult.LocationBreakdown::netNewBookings)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sumDeals = locations.stream()
                    .mapToInt(ForecastResult.LocationBreakdown::netNewDeals)
                    .sum();
        }

        BigDecimal bookingsRatio = sumBookings.compareTo(BigDecimal.ZERO) > 0
                ? globalNetNewBookings.divide(sumBookings, 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        double dealsRatio = sumDeals > 0
                ? (double) globalNetNewDeals / sumDeals
                : 1.0;

        boolean bookingsOff = Math.abs(bookingsRatio.doubleValue() - 1.0) > 0.10;
        boolean dealsOff = Math.abs(dealsRatio - 1.0) > 0.10;

        if (!bookingsOff && !dealsOff) {
            return locations;
        }

        log.info("Scaling per-location breakdowns to match global totals. Bookings ratio={}, Deals ratio={}",
                bookingsRatio, dealsRatio);

        List<ForecastResult.LocationBreakdown> scaled = new ArrayList<>();
        for (ForecastResult.LocationBreakdown lb : locations) {
            BigDecimal scaledBookings = bookingsOff
                    ? lb.netNewBookings().multiply(bookingsRatio).setScale(2, RoundingMode.HALF_UP)
                    : lb.netNewBookings();
            int scaledDeals = dealsOff
                    ? Math.max(1, (int) Math.round(lb.netNewDeals() * dealsRatio))
                    : lb.netNewDeals();
            BigDecimal scaledRoi = lb.budgetPredictedSpend().compareTo(BigDecimal.ZERO) > 0
                    ? scaledBookings.divide(lb.budgetPredictedSpend(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            scaled.add(new ForecastResult.LocationBreakdown(
                    lb.locationValueId(), lb.name(), lb.parentId(),
                    lb.budgetUtilizedPct(), scaledDeals,
                    scaledBookings, scaledRoi, lb.participationRate(),
                    lb.budgetAllocated(), lb.budgetPredictedSpend()));
        }
        return scaled;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private BigDecimal computeDataQuality(ForecastContext context) {
        // Score 0-100 based on data availability
        double score = 0;
        if (context.totalHistoricalPurchaseOrders() > 0) score += 20;
        if (context.totalHistoricalPurchaseOrders() > 1000) score += 15;
        if (context.totalHistoricalIncentives() > 0) score += 20;
        if (context.totalHistoricalIncentives() > 5) score += 10;
        if (!context.similarPastIncentives().isEmpty()) score += 20;
        if (context.similarPastIncentives().size() >= 3) score += 10;
        if (!context.trainingCorrelation().isEmpty()) score += 5;
        return BigDecimal.valueOf(Math.min(score, 100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(BigDecimal value, double min, double max) {
        if (value.doubleValue() < min) return BigDecimal.valueOf(min).setScale(2, RoundingMode.HALF_UP);
        if (value.doubleValue() > max) return BigDecimal.valueOf(max).setScale(2, RoundingMode.HALF_UP);
        return value;
    }

    private BigDecimal decimalOrZero(JsonNode node, String field) {
        if (node.has(field) && node.get(field).isNumber()) {
            return BigDecimal.valueOf(node.get(field).asDouble()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private int intOrZero(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asInt(0) : 0;
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/forecast-system.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load forecast system prompt", e);
            return "You are a sales incentive forecasting analyst. Respond with valid JSON.";
        }
    }
}
