package com.tenxengage.app.service.recommendation;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecommendationInsightService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationInsightService.class);

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final String model;
    private final String systemPrompt;

    public RecommendationInsightService(@Autowired(required = false) @Nullable AnthropicClient client,
                                         ObjectMapper objectMapper,
                                         JdbcTemplate jdbcTemplate,
                                         @Value("${app.ai.model}") String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.model = model;
        this.systemPrompt = loadSystemPrompt();
    }

    public boolean isAvailable() {
        return client != null;
    }

    public void streamInsight(SseEmitter emitter, UUID clientId, UUID userId,
                               UUID targetId, String recommendationType) {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Thread.startVirtualThread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                String insightText = generateInsight(clientId, userId, targetId, recommendationType);

                emitter.send(SseEmitter.event()
                        .name("insight")
                        .data(insightText));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("Error streaming insight: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Failed to generate insight"));
                    emitter.complete();
                } catch (IOException ioe) {
                    emitter.completeWithError(ioe);
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private String generateInsight(UUID clientId, UUID userId, UUID targetId,
                                    String recommendationType) {
        Map<String, Object> context = buildInsightContext(clientId, userId, targetId,
                recommendationType);

        if (!isAvailable()) {
            log.warn("Claude API unavailable — using fallback insight");
            return buildFallbackInsight(context, recommendationType);
        }

        try {
            String contextJson = objectMapper.writeValueAsString(context);

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(512L)
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

            return responseText.isEmpty()
                    ? buildFallbackInsight(context, recommendationType)
                    : responseText;

        } catch (Exception e) {
            log.warn("Claude insight generation failed, using fallback: {}", e.getMessage());
            return buildFallbackInsight(context, recommendationType);
        }
    }

    private Map<String, Object> buildInsightContext(UUID clientId, UUID userId,
                                                     UUID targetId, String recType) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("recommendationType", recType);

        // User's sales profile
        Map<String, BigDecimal> salesByCategory = new HashMap<>();
        List<Map<String, Object>> salesRows = jdbcTemplate.queryForList(
                "SELECT p.category, COALESCE(SUM(pol.line_total), 0) AS revenue " +
                "FROM claim_actions ca " +
                "JOIN purchase_orders po ON po.id = ca.purchase_order_id " +
                "JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id " +
                "JOIN products p ON p.id = pol.product_id " +
                "WHERE ca.user_id = ?::uuid AND ca.client_id = ?::uuid " +
                "GROUP BY p.category ORDER BY revenue DESC",
                userId, clientId);
        for (Map<String, Object> row : salesRows) {
            String cat = (String) row.get("category");
            BigDecimal rev = (BigDecimal) row.get("revenue");
            if (cat != null) salesByCategory.put(cat, rev);
        }
        ctx.put("userSalesProfile", salesByCategory);

        // User's completed courses
        List<String> completedCourses = jdbcTemplate.queryForList(
                "SELECT lc.name FROM user_course_completions ucc " +
                "JOIN lms_courses lc ON lc.id = ucc.course_id " +
                "WHERE ucc.user_id = ?::uuid AND ucc.client_id = ?::uuid",
                String.class, userId, clientId);
        ctx.put("completedCourses", completedCourses);

        // Score breakdown
        List<Map<String, Object>> scoreData = jdbcTemplate.queryForList(
                "SELECT score_breakdown, reason_code, score FROM recommendation_scores " +
                "WHERE client_id = ?::uuid AND user_id = ?::uuid AND target_id = ?::uuid " +
                "AND recommendation_type = ?",
                clientId, userId, targetId, recType);
        if (!scoreData.isEmpty()) {
            ctx.put("scoreBreakdown", scoreData.get(0).get("score_breakdown"));
            ctx.put("reasonCode", scoreData.get(0).get("reason_code"));
        }

        // Target details + type-specific context
        if ("TRAINING".equals(recType)) {
            // BUG-022 follow-up: level / duration / product_category are metadata JSONB keys
            // now, not columns.
            List<Map<String, Object>> course = jdbcTemplate.queryForList(
                    "SELECT name, description, category, " +
                    "metadata->>'level' AS level, " +
                    "metadata->>'duration' AS duration, " +
                    "metadata->>'product_category' AS product_category " +
                    "FROM lms_courses WHERE id = ?::uuid", targetId);
            if (!course.isEmpty()) ctx.put("target", course.get(0));

            // Full list of product categories this course covers (relevance >= 0.3).
            // Authoritative via course_product_mappings, which supports multi-category
            // courses (e.g. Cloud -> [Cloud Services, Software & Licensing]).
            List<String> courseCoversCategories = jdbcTemplate.queryForList(
                    "SELECT product_category FROM course_product_mappings " +
                    "WHERE course_id = ?::uuid AND relevance_score >= 0.3 " +
                    "ORDER BY relevance_score DESC",
                    String.class, targetId);
            ctx.put("courseCoversCategories", courseCoversCategories);

            // Training lift data for this course's categories
            List<Map<String, Object>> lift = jdbcTemplate.queryForList(
                    "SELECT product_category, data_driven_lift_pct, sample_size " +
                    "FROM forecast_training_correlations WHERE client_id = ?::uuid",
                    clientId);
            ctx.put("trainingLiftData", lift);

            // Active SALES incentives whose product categories overlap with this course,
            // now with start_date, end_date, and max payout band amount so the prompt
            // can cite concrete upcoming earning opportunities.
            if (!courseCoversCategories.isEmpty()) {
                String placeholders = courseCoversCategories.stream()
                        .map(c -> "?").reduce((a, b) -> a + "," + b).orElse("''");
                List<Object> params = new ArrayList<>();
                params.add(clientId);
                params.addAll(courseCoversCategories);
                List<Map<String, Object>> upcoming = jdbcTemplate.queryForList(
                        "SELECT DISTINCT i.id, i.name, i.incentive_type, i.start_date, i.end_date, " +
                        "(SELECT MAX(pb.payout_value) FROM payout_bands pb " +
                        " JOIN payout_configs pc ON pc.id = pb.payout_config_id " +
                        " JOIN sales_requirements sr2 ON sr2.id = pc.requirement_id " +
                        " WHERE sr2.incentive_id = i.id) AS max_payout " +
                        "FROM incentives i " +
                        "JOIN sales_requirements sr ON sr.incentive_id = i.id " +
                        "JOIN eligibility_rule_groups erg ON erg.requirement_id = sr.id " +
                        "JOIN eligibility_rules er ON er.rule_group_id = erg.id " +
                        "CROSS JOIN LATERAL unnest(string_to_array(er.selected_products, ',')) AS ps " +
                        "JOIN products p ON p.sku = trim(ps) " +
                        "WHERE i.client_id = ?::uuid AND i.status = 'ACTIVE' AND i.deleted = false " +
                        "AND i.incentive_type = 'SALES' AND p.category IN (" + placeholders + ") " +
                        "LIMIT 5",
                        params.toArray());
                ctx.put("upcomingIncentivesForCategories", upcoming);
                // Kept for prompt backward compatibility.
                ctx.put("relatedActiveIncentives", upcoming);
            }

            // User's top earning incentives — grounds "you already earned $X on Y".
            // NOTE: use string_agg (not array_agg) because Jackson can't serialize
            // PostgreSQL PgArray objects through the insight-context -> JSON flow.
            List<Map<String, Object>> earningsByIncentive = jdbcTemplate.queryForList(
                    "SELECT i.name AS incentive_name, " +
                    "       SUM(rt.amount_awarded) AS total_earned, " +
                    "       rt.currency_id, " +
                    "       string_agg(DISTINCT p.category, ',') AS product_categories " +
                    "FROM reward_transactions rt " +
                    "JOIN incentives i ON i.id = rt.incentive_id " +
                    "LEFT JOIN claim_actions ca ON ca.id = rt.claim_action_id " +
                    "LEFT JOIN purchase_orders po ON po.id = ca.purchase_order_id " +
                    "LEFT JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id " +
                    "LEFT JOIN products p ON p.id = pol.product_id " +
                    "WHERE rt.user_id = ?::uuid AND rt.client_id = ?::uuid " +
                    "  AND rt.amount_awarded > 0 " +
                    "GROUP BY i.id, i.name, rt.currency_id " +
                    "ORDER BY total_earned DESC LIMIT 3",
                    userId, clientId);
            ctx.put("userEarningsByIncentive", earningsByIncentive);

            // Skill gaps: categories where user sells but has no training
            List<Map<String, Object>> gaps = jdbcTemplate.queryForList(
                    "SELECT p.category, COALESCE(SUM(pol.line_total), 0) AS revenue " +
                    "FROM claim_actions ca " +
                    "JOIN purchase_orders po ON po.id = ca.purchase_order_id " +
                    "JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id " +
                    "JOIN products p ON p.id = pol.product_id " +
                    "WHERE ca.user_id = ?::uuid AND ca.client_id = ?::uuid " +
                    "AND p.category NOT IN (" +
                    "  SELECT DISTINCT cpm.product_category FROM user_course_completions ucc " +
                    "  JOIN course_product_mappings cpm ON cpm.course_id = ucc.course_id " +
                    "  WHERE ucc.user_id = ?::uuid AND ucc.client_id = ?::uuid " +
                    "  AND cpm.relevance_score >= 0.3" +
                    ") GROUP BY p.category ORDER BY revenue DESC",
                    userId, clientId, userId, clientId);
            ctx.put("userSkillGaps", gaps);

        } else {
            // INCENTIVE context
            List<Map<String, Object>> incentive = jdbcTemplate.queryForList(
                    "SELECT name, description, incentive_type, start_date, end_date " +
                    "FROM incentives WHERE id = ?::uuid", targetId);
            if (!incentive.isEmpty()) ctx.put("target", incentive.get(0));

            // Product categories this incentive targets
            List<String> incentiveCategories = jdbcTemplate.queryForList(
                    "SELECT DISTINCT p.category FROM sales_requirements sr " +
                    "JOIN eligibility_rule_groups erg ON erg.requirement_id = sr.id " +
                    "JOIN eligibility_rules er ON er.rule_group_id = erg.id " +
                    "CROSS JOIN LATERAL unnest(string_to_array(er.selected_products, ',')) AS ps " +
                    "JOIN products p ON p.sku = trim(ps) " +
                    "WHERE sr.incentive_id = ?::uuid",
                    String.class, targetId);
            ctx.put("incentiveProductCategories", incentiveCategories);

            // User's revenue in those specific categories
            if (!incentiveCategories.isEmpty()) {
                Map<String, BigDecimal> matchingRevenue = new HashMap<>();
                for (Map.Entry<String, BigDecimal> entry : salesByCategory.entrySet()) {
                    if (incentiveCategories.contains(entry.getKey())) {
                        matchingRevenue.put(entry.getKey(), entry.getValue());
                    }
                }
                ctx.put("userMatchingRevenue", matchingRevenue);
            }

            // Training courses user completed that relate to this incentive's categories
            if (!incentiveCategories.isEmpty()) {
                String placeholders = incentiveCategories.stream()
                        .map(c -> "?").reduce((a, b) -> a + "," + b).orElse("''");
                List<Object> params = new ArrayList<>();
                params.add(userId);
                params.add(clientId);
                params.addAll(incentiveCategories);
                List<String> relevantTraining = jdbcTemplate.queryForList(
                        "SELECT DISTINCT lc.name FROM user_course_completions ucc " +
                        "JOIN lms_courses lc ON lc.id = ucc.course_id " +
                        "JOIN course_product_mappings cpm ON cpm.course_id = ucc.course_id " +
                        "WHERE ucc.user_id = ?::uuid AND ucc.client_id = ?::uuid " +
                        "AND cpm.product_category IN (" + placeholders + ") " +
                        "AND cpm.relevance_score >= 0.3",
                        String.class, params.toArray());
                ctx.put("userRelevantTraining", relevantTraining);

                // Categories among the incentive's targets where the user has not
                // completed any relevant training — drives the "Completing a course
                // in X could help" prompt angle when userRelevantTraining is thin.
                List<Object> gapParams = new ArrayList<>();
                gapParams.addAll(incentiveCategories);
                gapParams.add(userId);
                gapParams.add(clientId);
                List<String> missingTraining = jdbcTemplate.queryForList(
                        "SELECT cat FROM (VALUES " +
                        incentiveCategories.stream().map(c -> "(?)")
                                .reduce((a, b) -> a + "," + b).orElse("('')") +
                        ") AS t(cat) WHERE cat NOT IN (" +
                        "  SELECT DISTINCT cpm.product_category FROM user_course_completions ucc " +
                        "  JOIN course_product_mappings cpm ON cpm.course_id = ucc.course_id " +
                        "  WHERE ucc.user_id = ?::uuid AND ucc.client_id = ?::uuid " +
                        "  AND cpm.relevance_score >= 0.3" +
                        ")",
                        String.class, gapParams.toArray());
                ctx.put("missingTrainingForCategories", missingTraining);

                // User's lifetime earnings bucketed by the incentive's target categories.
                // Grounds the "you already earned $X selling Security" prompt angle.
                List<Object> earnParams = new ArrayList<>();
                earnParams.add(userId);
                earnParams.add(clientId);
                earnParams.addAll(incentiveCategories);
                List<Map<String, Object>> historicalEarnings = jdbcTemplate.queryForList(
                        "SELECT p.category, SUM(rt.amount_awarded) AS earned, rt.currency_id " +
                        "FROM reward_transactions rt " +
                        "JOIN claim_actions ca ON ca.id = rt.claim_action_id " +
                        "JOIN purchase_orders po ON po.id = ca.purchase_order_id " +
                        "JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id " +
                        "JOIN products p ON p.id = pol.product_id " +
                        "WHERE rt.user_id = ?::uuid AND rt.client_id = ?::uuid " +
                        "  AND p.category IN (" + placeholders + ") " +
                        "  AND rt.amount_awarded > 0 " +
                        "GROUP BY p.category, rt.currency_id " +
                        "ORDER BY earned DESC",
                        earnParams.toArray());
                ctx.put("userHistoricalEarningsInCategory", historicalEarnings);
            }

            // Top 5 most recent claims with incentive + category + reward so the prompt
            // can reference concrete past wins.
            List<Map<String, Object>> recentClaims = jdbcTemplate.queryForList(
                    "SELECT i.name AS incentive_name, p.category, " +
                    "       rt.amount_awarded, rt.currency_id, rt.created_at " +
                    "FROM reward_transactions rt " +
                    "JOIN incentives i ON i.id = rt.incentive_id " +
                    "JOIN claim_actions ca ON ca.id = rt.claim_action_id " +
                    "JOIN purchase_orders po ON po.id = ca.purchase_order_id " +
                    "JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id " +
                    "JOIN products p ON p.id = pol.product_id " +
                    "WHERE rt.user_id = ?::uuid AND rt.client_id = ?::uuid " +
                    "  AND rt.amount_awarded > 0 " +
                    "ORDER BY rt.created_at DESC LIMIT 5",
                    userId, clientId);
            ctx.put("recentClaimsSummary", recentClaims);

            // Training lift for incentive's target categories
            if (!incentiveCategories.isEmpty()) {
                List<Map<String, Object>> lift = jdbcTemplate.queryForList(
                        "SELECT product_category, data_driven_lift_pct, sample_size " +
                        "FROM forecast_training_correlations WHERE client_id = ?::uuid",
                        clientId);
                ctx.put("trainingLiftForCategories", lift);
            }
        }

        return ctx;
    }

    private String buildFallbackInsight(Map<String, Object> context, String recType) {
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) context.get("target");
        String name = target != null ? (String) target.getOrDefault("name", "this item") : "this item";

        if ("TRAINING".equals(recType)) {
            return "This training course, \"" + name + "\", is recommended based on your sales activity " +
                   "and the product categories you work with. Completing it can help improve your " +
                   "effectiveness and qualify you for additional incentive programs.";
        } else {
            return "The incentive \"" + name + "\" matches your selling patterns and has budget " +
                   "available for participation. Based on your sales history, you're well-positioned " +
                   "to earn rewards by participating in this program.";
        }
    }

    private String loadSystemPrompt() {
        try {
            return new ClassPathResource("prompts/recommendation-insight-system.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load recommendation insight system prompt", e);
            return "You are a sales advisor. Explain why a recommendation is relevant to this user.";
        }
    }
}
