package com.tenxengage.app.service.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class RecommendationScoringService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationScoringService.class);
    private static final BigDecimal DEFAULT_LIFT_PCT = new BigDecimal("15.0");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationScoringService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // ─── Training Scoring ───────────────────────────────────────────

    public void scoreTrainingForClient(UUID clientId) {
        log.info("Scoring training recommendations for client {}", clientId);

        // Load shared data once per client
        Map<String, BigDecimal> trainingLiftByCategory = loadTrainingLiftByCategory(clientId);
        Map<UUID, Set<String>> courseProductCategories = loadCourseProductCategories();
        Set<String> activeRequiredCategories = loadActiveIncentiveRequiredCourseCategories(clientId);
        List<UUID> partnerUsers = loadPartnerUserIds(clientId);
        List<Map<String, Object>> allCourses = loadAllCourses();

        int scored = 0;
        for (UUID userId : partnerUsers) {
            try {
                scoreTrainingForUser(clientId, userId, allCourses, courseProductCategories,
                        trainingLiftByCategory, activeRequiredCategories);
                scored++;
            } catch (Exception e) {
                log.warn("Failed to score training for user {}: {}", userId, e.getMessage());
            }
        }
        log.info("Training scoring complete for client {}: {} users scored", clientId, scored);
    }

    private void scoreTrainingForUser(UUID clientId, UUID userId,
                                       List<Map<String, Object>> allCourses,
                                       Map<UUID, Set<String>> courseProductCategories,
                                       Map<String, BigDecimal> trainingLiftByCategory,
                                       Set<String> activeRequiredCategories) {
        // User's sales profile: category -> revenue
        Map<String, BigDecimal> salesByCategory = loadUserSalesProfile(clientId, userId);
        BigDecimal totalRevenue = salesByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // User's completed courses
        Set<UUID> completedCourseIds = loadUserCompletedCourses(clientId, userId);

        // User's dismissed recommendations
        Set<UUID> dismissedTargets = loadDismissedTargets(clientId, userId, "TRAINING");

        // Categories where user sells but has no training
        Set<String> trainedCategories = loadUserTrainedCategories(clientId, userId);
        Set<String> gapCategories = new HashSet<>(salesByCategory.keySet());
        gapCategories.removeAll(trainedCategories);

        // Score each candidate course
        List<ScoredItem> scores = new ArrayList<>();
        for (Map<String, Object> course : allCourses) {
            UUID courseId = toUUID(course.get("id"));

            // Exclusion filters
            if (completedCourseIds.contains(courseId) || dismissedTargets.contains(courseId)) {
                continue;
            }

            Set<String> courseCategories = courseProductCategories.getOrDefault(courseId, Set.of());
            String courseLevel = (String) course.get("level");
            String primaryCategory = (String) course.get("product_category");

            // Signal 1: Sales Alignment (weight 0.30)
            double salesAlignment = 0.0;
            if (!salesByCategory.isEmpty() && totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                for (String cat : courseCategories) {
                    BigDecimal catRevenue = salesByCategory.getOrDefault(cat, BigDecimal.ZERO);
                    double weight = catRevenue.doubleValue() / totalRevenue.doubleValue();
                    salesAlignment += weight;
                }
                salesAlignment = Math.min(1.0, salesAlignment);
            }

            // Signal 2: Training Lift (weight 0.25)
            double trainingLift = 0.0;
            int liftCount = 0;
            for (String cat : courseCategories) {
                BigDecimal lift = trainingLiftByCategory.getOrDefault(cat, DEFAULT_LIFT_PCT);
                trainingLift += lift.doubleValue() / 100.0;
                liftCount++;
            }
            if (liftCount > 0) {
                trainingLift = Math.min(1.0, trainingLift / liftCount);
            }

            // Signal 3: Incentive Alignment (weight 0.25)
            // Check if course's category matches any active incentive required categories
            double incentiveAlignment = 0.0;
            String courseCategory = (String) course.get("category");
            if (courseCategory != null && activeRequiredCategories.contains(courseCategory)) {
                incentiveAlignment = 1.0;
            } else {
                for (String cat : courseCategories) {
                    if (activeRequiredCategories.contains(cat)) {
                        incentiveAlignment = 0.7;
                        break;
                    }
                }
            }

            // Signal 4: Skill Gap (weight 0.15)
            double skillGap = 0.0;
            for (String cat : courseCategories) {
                if (gapCategories.contains(cat)) {
                    skillGap = 1.0;
                    break;
                }
            }

            // Signal 5: Difficulty Match (weight 0.05)
            double difficultyMatch = computeDifficultyMatch(courseLevel, primaryCategory,
                    completedCourseIds.size());

            // Composite score
            double compositeScore = (0.30 * salesAlignment)
                    + (0.25 * trainingLift)
                    + (0.25 * incentiveAlignment)
                    + (0.15 * skillGap)
                    + (0.05 * difficultyMatch);

            // Determine reason code (highest weighted signal)
            String reasonCode = determineTrainingReasonCode(
                    salesAlignment, trainingLift, incentiveAlignment, skillGap);

            Map<String, Object> breakdown = new LinkedHashMap<>();
            breakdown.put("salesAlignment", round4(salesAlignment));
            breakdown.put("trainingLift", round4(trainingLift));
            breakdown.put("incentiveAlignment", round4(incentiveAlignment));
            breakdown.put("skillGap", round4(skillGap));
            breakdown.put("difficultyMatch", round4(difficultyMatch));

            scores.add(new ScoredItem(courseId, compositeScore, reasonCode, breakdown));
        }

        // Sort and rank
        scores.sort((a, b) -> Double.compare(b.score, a.score));
        AtomicInteger rank = new AtomicInteger(1);
        for (ScoredItem item : scores) {
            item.rank = rank.getAndIncrement();
        }

        // Upsert into recommendation_scores
        upsertScores(clientId, userId, "TRAINING", scores);
    }

    // ─── Incentive Scoring ──────────────────────────────────────────

    public void scoreIncentivesForClient(UUID clientId) {
        log.info("Scoring incentive recommendations for client {}", clientId);

        List<Map<String, Object>> activeIncentives = loadActiveIncentives(clientId);
        Map<UUID, BigDecimal> budgetUtilization = loadBudgetUtilization(clientId);
        List<UUID> partnerUsers = loadPartnerUserIds(clientId);

        int scored = 0;
        for (UUID userId : partnerUsers) {
            try {
                scoreIncentivesForUser(clientId, userId, activeIncentives, budgetUtilization);
                scored++;
            } catch (Exception e) {
                log.warn("Failed to score incentives for user {}: {}", userId, e.getMessage());
            }
        }
        log.info("Incentive scoring complete for client {}: {} users scored", clientId, scored);
    }

    private void scoreIncentivesForUser(UUID clientId, UUID userId,
                                         List<Map<String, Object>> activeIncentives,
                                         Map<UUID, BigDecimal> budgetUtilization) {
        Map<String, BigDecimal> salesByCategory = loadUserSalesProfile(clientId, userId);
        Set<UUID> completedIncentives = loadUserCompletedIncentives(clientId, userId);
        Set<UUID> dismissedTargets = loadDismissedTargets(clientId, userId, "INCENTIVE");
        Set<UUID> userLocationIds = loadUserLocationValueIds(clientId, userId);
        BigDecimal userAvgDealSize = loadUserAvgDealSize(clientId, userId);

        List<ScoredItem> scores = new ArrayList<>();
        for (Map<String, Object> incentive : activeIncentives) {
            UUID incentiveId = toUUID(incentive.get("id"));

            if (completedIncentives.contains(incentiveId) || dismissedTargets.contains(incentiveId)) {
                continue;
            }

            // Signal 1: Sales Pattern Match (weight 0.30)
            double salesPatternMatch = computeSalesPatternMatch(clientId, incentiveId, salesByCategory);

            // Signal 2: Training Readiness (weight 0.20)
            double trainingReadiness = computeTrainingReadiness(clientId, userId, incentiveId);

            // Signal 3: Budget Health (weight 0.20)
            BigDecimal utilPct = budgetUtilization.getOrDefault(incentiveId, BigDecimal.ZERO);
            double budgetHealth = computeBudgetHealthSignal(utilPct);

            // Signal 4: Payout Attractiveness (weight 0.15)
            double payoutAttractiveness = computePayoutAttractiveness(clientId, incentiveId,
                    userAvgDealSize);

            // Signal 5: Region Match (weight 0.10)
            double regionMatch = computeLocationMatch(clientId, incentiveId, userLocationIds);

            // Signal 6: Time Remaining (weight 0.05)
            Object endDateObj = incentive.get("end_date");
            Instant endDate = endDateObj instanceof java.sql.Timestamp ts ? ts.toInstant()
                    : endDateObj instanceof Instant inst ? inst : null;
            double timeRemaining = computeTimeRemainingSignal(endDate);

            double compositeScore = (0.30 * salesPatternMatch)
                    + (0.20 * trainingReadiness)
                    + (0.20 * budgetHealth)
                    + (0.15 * payoutAttractiveness)
                    + (0.10 * regionMatch)
                    + (0.05 * timeRemaining);

            // Skip exhausted incentives
            if (utilPct.compareTo(new BigDecimal("95")) >= 0) {
                continue;
            }

            String reasonCode = determineIncentiveReasonCode(
                    salesPatternMatch, trainingReadiness, budgetHealth, payoutAttractiveness);

            Map<String, Object> breakdown = new LinkedHashMap<>();
            breakdown.put("salesPatternMatch", round4(salesPatternMatch));
            breakdown.put("trainingReadiness", round4(trainingReadiness));
            breakdown.put("budgetHealth", round4(budgetHealth));
            breakdown.put("payoutAttractiveness", round4(payoutAttractiveness));
            breakdown.put("regionMatch", round4(regionMatch));
            breakdown.put("timeRemaining", round4(timeRemaining));

            scores.add(new ScoredItem(incentiveId, compositeScore, reasonCode, breakdown));
        }

        scores.sort((a, b) -> Double.compare(b.score, a.score));
        AtomicInteger rank = new AtomicInteger(1);
        for (ScoredItem item : scores) {
            item.rank = rank.getAndIncrement();
        }

        upsertScores(clientId, userId, "INCENTIVE", scores);
    }

    // ─── Data Loading Methods ───────────────────────────────────────

    // BUG-022: the legacy user_roles/roles tables were dropped during the role-system
    // consolidation; the current shape is a direct users.client_role_id FK into
    // client_roles. Filter on base_role_name (the stable internal token), matching
    // UserSeeder.resolvePartnerAdminRoleId/resolvePartnerSellerRoleId.
    List<UUID> loadPartnerUserIds(UUID clientId) {
        return jdbcTemplate.queryForList(
                "SELECT u.id::text FROM users u " +
                "JOIN client_roles cr ON cr.id = u.client_role_id " +
                "WHERE u.client_id = ?::uuid " +
                "  AND cr.base_role_name IN ('PARTNER_ADMIN', 'PARTNER_SELLER') " +
                "  AND u.status = 'ACTIVE'",
                String.class, clientId)
                .stream().map(UUID::fromString).toList();
    }

    private Map<String, BigDecimal> loadUserSalesProfile(UUID clientId, UUID userId) {
        Map<String, BigDecimal> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT p.category AS product_category, COALESCE(SUM(pol.line_total), 0) AS total_revenue " +
                "FROM claim_actions ca " +
                "JOIN purchase_orders po ON po.id = ca.purchase_order_id " +
                "JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id " +
                "JOIN products p ON p.id = pol.product_id " +
                "WHERE ca.user_id = ?::uuid AND ca.client_id = ?::uuid " +
                "GROUP BY p.category",
                rs -> {
                    String cat = rs.getString("product_category");
                    BigDecimal rev = rs.getBigDecimal("total_revenue");
                    if (cat != null) result.put(cat, rev);
                },
                userId, clientId);
        return result;
    }

    private Set<UUID> loadUserCompletedCourses(UUID clientId, UUID userId) {
        return jdbcTemplate.queryForList(
                "SELECT course_id::text FROM user_course_completions " +
                "WHERE user_id = ?::uuid AND client_id = ?::uuid",
                String.class, userId, clientId)
                .stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    private Set<UUID> loadDismissedTargets(UUID clientId, UUID userId, String recType) {
        return jdbcTemplate.queryForList(
                "SELECT target_id::text FROM recommendation_interactions " +
                "WHERE client_id = ?::uuid AND user_id = ?::uuid " +
                "AND recommendation_type = ? AND interaction_type = 'DISMISSED'",
                String.class, clientId, userId, recType)
                .stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    private Set<String> loadUserTrainedCategories(UUID clientId, UUID userId) {
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT cpm.product_category FROM user_course_completions ucc " +
                "JOIN course_product_mappings cpm ON cpm.course_id = ucc.course_id " +
                "WHERE ucc.user_id = ?::uuid AND ucc.client_id = ?::uuid " +
                "AND cpm.relevance_score >= 0.3",
                String.class, userId, clientId));
    }

    private Map<String, BigDecimal> loadTrainingLiftByCategory(UUID clientId) {
        Map<String, BigDecimal> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT product_category, data_driven_lift_pct FROM forecast_training_correlations " +
                "WHERE client_id = ?::uuid AND sample_size >= 5",
                rs -> {
                    result.put(rs.getString("product_category"),
                            rs.getBigDecimal("data_driven_lift_pct"));
                },
                clientId);
        return result;
    }

    private Map<UUID, Set<String>> loadCourseProductCategories() {
        Map<UUID, Set<String>> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT course_id, product_category FROM course_product_mappings " +
                "WHERE relevance_score >= 0.3",
                rs -> {
                    UUID courseId = UUID.fromString(rs.getString("course_id"));
                    String cat = rs.getString("product_category");
                    result.computeIfAbsent(courseId, k -> new HashSet<>()).add(cat);
                });
        return result;
    }

    private Set<String> loadActiveIncentiveRequiredCourseCategories(UUID clientId) {
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT tca.course_category FROM training_course_assignments tca " +
                "JOIN incentives i ON i.id = tca.incentive_id " +
                "WHERE i.client_id = ?::uuid AND i.status = 'ACTIVE' AND i.deleted = false " +
                "AND tca.required = true AND tca.course_category IS NOT NULL",
                String.class, clientId));
    }

    // BUG-022 follow-up: lms_courses lost its level / duration / provider / product_category /
    // course_url columns during the same consolidation that dropped user_roles/roles. Those
    // fields now live (optionally) inside the metadata JSONB and are populated by LMS sync —
    // unpopulated courses get nulls, which downstream nullable fields already handle.
    private List<Map<String, Object>> loadAllCourses() {
        return jdbcTemplate.queryForList(
                "SELECT id, name, description, category, " +
                "metadata->>'level' AS level, " +
                "metadata->>'duration' AS duration, " +
                "metadata->>'provider' AS provider, " +
                "metadata->>'product_category' AS product_category " +
                "FROM lms_courses");
    }

    private List<Map<String, Object>> loadActiveIncentives(UUID clientId) {
        return jdbcTemplate.queryForList(
                "SELECT id, name, description, incentive_type, start_date, end_date " +
                "FROM incentives WHERE client_id = ?::uuid AND status = 'ACTIVE' " +
                "AND deleted = false AND incentive_type = 'SALES' " +
                "AND (end_date IS NULL OR end_date > now())",
                clientId);
    }

    private Map<UUID, BigDecimal> loadBudgetUtilization(UUID clientId) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT ib.incentive_id, " +
                "  CASE WHEN SUM(ib.total_budget) > 0 " +
                "    THEN COALESCE(SUM(bu.utilized), 0) / SUM(ib.total_budget) * 100 " +
                "    ELSE 0 END AS util_pct " +
                "FROM incentive_budgets ib " +
                "LEFT JOIN budget_utilizations bu ON bu.incentive_id = ib.incentive_id " +
                "  AND bu.currency_id = ib.currency_id " +
                "JOIN incentives i ON i.id = ib.incentive_id " +
                "WHERE i.client_id = ?::uuid AND i.status = 'ACTIVE' AND i.deleted = false " +
                "GROUP BY ib.incentive_id",
                rs -> {
                    UUID id = UUID.fromString(rs.getString("incentive_id"));
                    result.put(id, rs.getBigDecimal("util_pct"));
                },
                clientId);
        return result;
    }

    private Set<UUID> loadUserCompletedIncentives(UUID clientId, UUID userId) {
        return jdbcTemplate.queryForList(
                "SELECT incentive_id::text FROM user_incentive_completions " +
                "WHERE user_id = ?::uuid AND client_id = ?::uuid",
                String.class, userId, clientId)
                .stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    private Set<UUID> loadUserLocationValueIds(UUID clientId, UUID userId) {
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT pcl.location_value_id FROM users u " +
                "JOIN partner_company_locations pcl ON pcl.partner_company_id = u.partner_company_id " +
                "WHERE u.id = ?::uuid AND u.client_id = ?::uuid",
                UUID.class, userId, clientId);
        if (ids.isEmpty()) {
            // Collapses the location-match signal to 0.5 for every incentive for this
            // user. Surface it in logs so seeding gaps (e.g. BUG-018) don't hide silently.
            log.warn("User {} (client {}) has no partner_company_locations rows — location signal " +
                    "will default to 0.5 for all incentive scoring", userId, clientId);
        }
        return new HashSet<>(ids);
    }

    private BigDecimal loadUserAvgDealSize(UUID clientId, UUID userId) {
        List<BigDecimal> result = jdbcTemplate.queryForList(
                "SELECT AVG(po.total_amount) FROM claim_actions ca " +
                "JOIN purchase_orders po ON po.id = ca.purchase_order_id " +
                "WHERE ca.user_id = ?::uuid AND ca.client_id = ?::uuid",
                BigDecimal.class, userId, clientId);
        if (result.isEmpty() || result.get(0) == null) {
            return BigDecimal.ZERO;
        }
        return result.get(0);
    }

    // ─── Signal Computation Methods ─────────────────────────────────

    private double computeSalesPatternMatch(UUID clientId, UUID incentiveId,
                                             Map<String, BigDecimal> userSalesProfile) {
        if (userSalesProfile.isEmpty()) return 0.0;

        // Get incentive's target product categories
        Set<String> incentiveCategories = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT p.category FROM sales_requirements sr " +
                "JOIN eligibility_rule_groups erg ON erg.requirement_id = sr.id " +
                "JOIN eligibility_rules er ON er.rule_group_id = erg.id " +
                "CROSS JOIN LATERAL unnest(string_to_array(er.selected_products, ',')) AS product_sku " +
                "JOIN products p ON p.sku = trim(product_sku) " +
                "WHERE sr.incentive_id = ?::uuid",
                String.class, incentiveId));

        if (incentiveCategories.isEmpty()) return 0.5; // No product restrictions = moderate match

        Set<String> userCategories = userSalesProfile.keySet();
        Set<String> intersection = new HashSet<>(userCategories);
        intersection.retainAll(incentiveCategories);

        Set<String> union = new HashSet<>(userCategories);
        union.addAll(incentiveCategories);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double computeTrainingReadiness(UUID clientId, UUID userId, UUID incentiveId) {
        // Get required course categories for this incentive
        List<String> requiredCategories = jdbcTemplate.queryForList(
                "SELECT DISTINCT tca.course_category FROM training_course_assignments tca " +
                "WHERE tca.incentive_id = ?::uuid AND tca.required = true " +
                "AND tca.course_category IS NOT NULL",
                String.class, incentiveId);

        if (requiredCategories.isEmpty()) return 0.5; // No training requirements

        // Check how many of these categories the user has completed courses in
        long completedCategories = requiredCategories.stream()
                .filter(cat -> {
                    List<Integer> count = jdbcTemplate.queryForList(
                            "SELECT 1 FROM user_course_completions ucc " +
                            "JOIN lms_courses lc ON lc.id = ucc.course_id " +
                            "WHERE ucc.user_id = ?::uuid AND ucc.client_id = ?::uuid " +
                            "AND lc.category = ?",
                            Integer.class, userId, clientId, cat);
                    return !count.isEmpty();
                })
                .count();

        return (double) completedCategories / requiredCategories.size();
    }

    private double computeBudgetHealthSignal(BigDecimal utilPct) {
        if (utilPct == null) return 1.0;
        double pct = utilPct.doubleValue();
        if (pct >= 95) return 0.0;
        if (pct >= 80) return 0.3;
        if (pct >= 50) return 0.7;
        return 1.0;
    }

    private double computePayoutAttractiveness(UUID clientId, UUID incentiveId,
                                                BigDecimal userAvgDealSize) {
        List<BigDecimal> payouts = jdbcTemplate.queryForList(
                "SELECT AVG(pb.max_amount) FROM payout_bands pb " +
                "JOIN payout_configs pc ON pc.id = pb.payout_config_id " +
                "JOIN sales_requirements sr ON sr.id = pc.requirement_id " +
                "WHERE sr.incentive_id = ?::uuid AND pb.max_amount IS NOT NULL",
                BigDecimal.class, incentiveId);

        if (payouts.isEmpty() || payouts.get(0) == null) return 0.5;
        if (userAvgDealSize.compareTo(BigDecimal.ZERO) == 0) return 0.5;

        BigDecimal avgPayout = payouts.get(0);
        BigDecimal threshold = userAvgDealSize.multiply(new BigDecimal("0.10"));
        double ratio = avgPayout.doubleValue() / threshold.doubleValue();
        return Math.min(1.0, ratio);
    }

    private double computeLocationMatch(UUID clientId, UUID incentiveId, Set<UUID> userLocationIds) {
        if (userLocationIds.isEmpty()) return 0.5;

        // Pull rule_value + location_level_id so malformed rows can be surfaced with
        // enough context to identify stale seed data.
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                "SELECT rule_value, location_level_id FROM incentive_audience_rules " +
                "WHERE incentive_id = ?::uuid AND rule_type = 'LOCATION'",
                incentiveId);

        if (rules.isEmpty()) return 0.5; // No location restriction

        boolean anyMatch = false;
        for (Map<String, Object> row : rules) {
            String rv = (String) row.get("rule_value");
            if (rv == null) continue;
            try {
                if (userLocationIds.contains(UUID.fromString(rv))) {
                    anyMatch = true;
                    break;
                }
            } catch (IllegalArgumentException e) {
                // LOCATION rule_value must be a location_values UUID. A non-UUID here
                // means the row is stale (legacy region-name shape) and needs reseeding.
                log.warn("Malformed LOCATION rule_value '{}' on incentive {} (level {}): " +
                        "expected location_values UUID — scoring this rule as no-match",
                        rv, incentiveId, row.get("location_level_id"));
            }
        }
        return anyMatch ? 1.0 : 0.0;
    }

    private double computeTimeRemainingSignal(Instant endDate) {
        if (endDate == null) return 0.5;
        long daysRemaining = ChronoUnit.DAYS.between(Instant.now(), endDate);
        if (daysRemaining < 3) return 0.2;
        if (daysRemaining < 14) return 0.9;
        if (daysRemaining < 60) return 0.7;
        return 0.5;
    }

    private double computeDifficultyMatch(String courseLevel, String primaryCategory,
                                           int totalCompletedCourses) {
        // Simple heuristic based on total completions
        String userLevel;
        if (totalCompletedCourses <= 2) userLevel = "BEGINNER";
        else if (totalCompletedCourses <= 8) userLevel = "INTERMEDIATE";
        else userLevel = "ADVANCED";

        if (courseLevel == null) return 0.7;

        if (courseLevel.equalsIgnoreCase(userLevel)) return 1.0;

        int courseLevelIdx = levelIndex(courseLevel);
        int userLevelIdx = levelIndex(userLevel);
        int diff = Math.abs(courseLevelIdx - userLevelIdx);

        return diff == 1 ? 0.7 : 0.3;
    }

    private int levelIndex(String level) {
        if (level == null) return 1;
        return switch (level.toUpperCase()) {
            case "BEGINNER" -> 0;
            case "INTERMEDIATE" -> 1;
            case "ADVANCED" -> 2;
            default -> 1;
        };
    }

    // ─── Reason Code Determination ──────────────────────────────────

    private String determineTrainingReasonCode(double salesAlignment, double trainingLift,
                                                double incentiveAlignment, double skillGap) {
        double maxSignal = Math.max(Math.max(salesAlignment * 0.30, trainingLift * 0.25),
                Math.max(incentiveAlignment * 0.25, skillGap * 0.15));

        if (maxSignal == incentiveAlignment * 0.25 && incentiveAlignment > 0)
            return "INCENTIVE_REQUIRED";
        if (maxSignal == salesAlignment * 0.30) return "SALES_ALIGNMENT";
        if (maxSignal == trainingLift * 0.25) return "TRAINING_LIFT";
        return "SKILL_GAP";
    }

    private String determineIncentiveReasonCode(double salesPatternMatch, double trainingReadiness,
                                                 double budgetHealth, double payoutAttractiveness) {
        double maxSignal = Math.max(Math.max(salesPatternMatch * 0.30, trainingReadiness * 0.20),
                Math.max(budgetHealth * 0.20, payoutAttractiveness * 0.15));

        if (maxSignal == salesPatternMatch * 0.30) return "SALES_PATTERN_MATCH";
        if (maxSignal == trainingReadiness * 0.20) return "TRAINING_READINESS";
        if (maxSignal == budgetHealth * 0.20) return "BUDGET_ATTRACTIVE";
        return "PAYOUT_ATTRACTIVE";
    }

    // ─── Persistence ────────────────────────────────────────────────

    private void upsertScores(UUID clientId, UUID userId, String recType, List<ScoredItem> scores) {
        String sql = "INSERT INTO recommendation_scores " +
                "(id, client_id, user_id, recommendation_type, target_id, score, " +
                "score_breakdown, rank, reason_code, computed_at, created_at, updated_at) " +
                "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?, ?::uuid, ?, ?::jsonb, ?, ?, now(), now(), now()) " +
                "ON CONFLICT (client_id, user_id, recommendation_type, target_id) " +
                "DO UPDATE SET score = EXCLUDED.score, score_breakdown = EXCLUDED.score_breakdown, " +
                "rank = EXCLUDED.rank, reason_code = EXCLUDED.reason_code, " +
                "computed_at = EXCLUDED.computed_at, updated_at = now()";

        for (ScoredItem item : scores) {
            String breakdownJson;
            try {
                breakdownJson = objectMapper.writeValueAsString(item.breakdown);
            } catch (JsonProcessingException e) {
                breakdownJson = "{}";
            }

            jdbcTemplate.update(sql, clientId, userId, recType, item.targetId,
                    BigDecimal.valueOf(item.score).setScale(4, RoundingMode.HALF_UP),
                    breakdownJson, item.rank, item.reasonCode);
        }
    }

    private UUID toUUID(Object value) {
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(value.toString());
    }

    private double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    // ─── Internal Data Class ────────────────────────────────────────

    private static class ScoredItem {
        final UUID targetId;
        final double score;
        final String reasonCode;
        final Map<String, Object> breakdown;
        int rank;

        ScoredItem(UUID targetId, double score, String reasonCode, Map<String, Object> breakdown) {
            this.targetId = targetId;
            this.score = score;
            this.reasonCode = reasonCode;
            this.breakdown = breakdown;
        }
    }
}
