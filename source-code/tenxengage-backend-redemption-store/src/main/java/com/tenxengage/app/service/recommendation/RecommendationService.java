package com.tenxengage.app.service.recommendation;

import com.tenxengage.app.dto.response.IncentiveRecommendationResponse;
import com.tenxengage.app.dto.response.RecommendationCompletionResponse;
import com.tenxengage.app.dto.response.TrainingRecommendationResponse;
import com.tenxengage.app.entity.RecommendationConfig;
import com.tenxengage.app.entity.RecommendationInteraction;
import com.tenxengage.app.entity.RecommendationScore;
import com.tenxengage.app.repository.RecommendationConfigRepository;
import com.tenxengage.app.repository.RecommendationInteractionRepository;
import com.tenxengage.app.repository.RecommendationScoreRepository;
import com.tenxengage.app.service.RewardBalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationScoreRepository scoreRepo;
    private final RecommendationInteractionRepository interactionRepo;
    private final RecommendationConfigRepository configRepo;
    private final RewardBalanceService rewardBalanceService;
    private final JdbcTemplate jdbcTemplate;

    public RecommendationService(RecommendationScoreRepository scoreRepo,
                                  RecommendationInteractionRepository interactionRepo,
                                  RecommendationConfigRepository configRepo,
                                  RewardBalanceService rewardBalanceService,
                                  JdbcTemplate jdbcTemplate) {
        this.scoreRepo = scoreRepo;
        this.interactionRepo = interactionRepo;
        this.configRepo = configRepo;
        this.rewardBalanceService = rewardBalanceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TrainingRecommendationResponse> getTrainingRecommendations(UUID clientId, UUID userId) {
        RecommendationConfig config = configRepo.findByClientId(clientId).orElse(null);
        if (config != null && !config.isTrainingEnabled()) {
            return List.of();
        }

        int maxRank = config != null ? config.getMaxTrainingRecommendations() : 5;
        List<RecommendationScore> scores = scoreRepo.findTopRecommendations(
                clientId, userId, "TRAINING", maxRank);

        BigDecimal rewardAmount = config != null ? config.getTrainingCompletionReward() : BigDecimal.ZERO;
        String rewardCurrencyId = config != null ? config.getRewardCurrencyId() : null;
        int daysUntilQuarterEnd = computeDaysUntilQuarterEnd(clientId);

        List<TrainingRecommendationResponse> responses = new ArrayList<>();
        for (RecommendationScore score : scores) {
            Map<String, Object> course = loadCourseDetails(score.getTargetId());
            if (course == null) continue;

            responses.add(new TrainingRecommendationResponse(
                    score.getTargetId(),
                    (String) course.get("name"),
                    (String) course.get("description"),
                    (String) course.get("category"),
                    (String) course.get("level"),
                    (String) course.get("duration"),
                    (String) course.get("provider"),
                    (String) course.get("product_category"),
                    (String) course.get("course_url"),
                    score.getScore(),
                    score.getRank(),
                    score.getReasonCode(),
                    generateReasonSummary(score.getReasonCode(), "TRAINING", course),
                    rewardAmount,
                    rewardCurrencyId,
                    daysUntilQuarterEnd
            ));
        }
        return responses;
    }

    public List<IncentiveRecommendationResponse> getIncentiveRecommendations(UUID clientId, UUID userId) {
        RecommendationConfig config = configRepo.findByClientId(clientId).orElse(null);
        if (config != null && !config.isIncentiveEnabled()) {
            return List.of();
        }

        int maxRank = config != null ? config.getMaxIncentiveRecommendations() : 5;
        List<RecommendationScore> scores = scoreRepo.findTopRecommendations(
                clientId, userId, "INCENTIVE", maxRank);

        BigDecimal rewardAmount = config != null ? config.getIncentiveCompletionReward() : BigDecimal.ZERO;
        String rewardCurrencyId = config != null ? config.getRewardCurrencyId() : null;

        List<IncentiveRecommendationResponse> responses = new ArrayList<>();
        for (RecommendationScore score : scores) {
            Map<String, Object> incentive = loadIncentiveDetails(score.getTargetId());
            if (incentive == null) continue;

            BigDecimal budgetRemainingPct = loadBudgetRemainingPct(score.getTargetId());

            responses.add(new IncentiveRecommendationResponse(
                    score.getTargetId(),
                    (String) incentive.get("name"),
                    (String) incentive.get("incentive_type"),
                    (String) incentive.get("description"),
                    incentive.get("start_date") != null
                            ? ((java.sql.Timestamp) incentive.get("start_date")).toInstant() : null,
                    incentive.get("end_date") != null
                            ? ((java.sql.Timestamp) incentive.get("end_date")).toInstant() : null,
                    score.getScore(),
                    score.getRank(),
                    score.getReasonCode(),
                    generateReasonSummary(score.getReasonCode(), "INCENTIVE", incentive),
                    budgetRemainingPct,
                    rewardCurrencyId,
                    rewardAmount
            ));
        }
        return responses;
    }

    @Transactional
    public void recordInteraction(UUID clientId, UUID userId, UUID targetId,
                                   String recommendationType, String interactionType) {
        RecommendationInteraction interaction = RecommendationInteraction.builder()
                .clientId(clientId)
                .userId(userId)
                .targetId(targetId)
                .recommendationType(recommendationType)
                .interactionType(interactionType)
                .build();
        interactionRepo.save(interaction);
    }

    @Transactional
    public RecommendationCompletionResponse recordCompletion(UUID clientId, UUID userId,
                                                              UUID targetId,
                                                              String recommendationType) {
        // Check idempotency
        boolean alreadyCompleted = interactionRepo.existsByClientIdAndUserIdAndTargetIdAndInteractionType(
                clientId, userId, targetId, "COMPLETED");
        if (alreadyCompleted) {
            return new RecommendationCompletionResponse(false, BigDecimal.ZERO, null);
        }

        // Record completion
        recordInteraction(clientId, userId, targetId, recommendationType, "COMPLETED");

        // Grant reward if configured
        RecommendationConfig config = configRepo.findByClientId(clientId).orElse(null);
        if (config == null || config.getRewardCurrencyId() == null) {
            return new RecommendationCompletionResponse(false, BigDecimal.ZERO, null);
        }

        BigDecimal rewardAmount = "TRAINING".equals(recommendationType)
                ? config.getTrainingCompletionReward()
                : config.getIncentiveCompletionReward();

        if (rewardAmount != null && rewardAmount.compareTo(BigDecimal.ZERO) > 0) {
            rewardBalanceService.credit(clientId, userId, config.getRewardCurrencyId(), rewardAmount,
                    "RECOMMENDATION", targetId);
            log.info("Granted {} {} reward to user {} for {} recommendation completion",
                    rewardAmount, config.getRewardCurrencyId(), userId, recommendationType);
            return new RecommendationCompletionResponse(true, rewardAmount, config.getRewardCurrencyId());
        }

        return new RecommendationCompletionResponse(false, BigDecimal.ZERO, null);
    }

    // ─── Data Loading ───────────────────────────────────────────────

    // BUG-022 follow-up: level / duration / provider / product_category / course_url are no
    // longer columns on lms_courses; pull them out of the metadata JSONB, nulling out anything
    // the LMS sync hasn't populated. Downstream response DTO treats all five as nullable.
    private Map<String, Object> loadCourseDetails(UUID courseId) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT name, description, category, " +
                "metadata->>'level' AS level, " +
                "metadata->>'duration' AS duration, " +
                "metadata->>'provider' AS provider, " +
                "metadata->>'product_category' AS product_category, " +
                "metadata->>'course_url' AS course_url " +
                "FROM lms_courses WHERE id = ?::uuid", courseId);
        return results.isEmpty() ? null : results.get(0);
    }

    private int computeDaysUntilQuarterEnd(UUID clientId) {
        // Try fiscal year config first
        List<Map<String, Object>> configs = jdbcTemplate.queryForList(
                "SELECT q1_end_date, q2_end_date, q3_end_date, q4_end_date " +
                "FROM fiscal_year_configs WHERE client_id = ?::uuid " +
                "ORDER BY end_date DESC LIMIT 1", clientId);

        java.time.LocalDate today = java.time.LocalDate.now();

        if (!configs.isEmpty()) {
            Map<String, Object> cfg = configs.get(0);
            java.time.LocalDate[] qEnds = {
                    toLocalDate(cfg.get("q1_end_date")),
                    toLocalDate(cfg.get("q2_end_date")),
                    toLocalDate(cfg.get("q3_end_date")),
                    toLocalDate(cfg.get("q4_end_date"))
            };
            for (java.time.LocalDate qEnd : qEnds) {
                if (qEnd != null && !today.isAfter(qEnd)) {
                    return (int) java.time.temporal.ChronoUnit.DAYS.between(today, qEnd);
                }
            }
        }

        // Fallback: calendar quarter end
        int month = today.getMonthValue();
        int qEndMonth = ((month - 1) / 3 + 1) * 3;
        java.time.LocalDate calendarQEnd = java.time.LocalDate.of(today.getYear(), qEndMonth, 1)
                .plusMonths(1).minusDays(1);
        return (int) java.time.temporal.ChronoUnit.DAYS.between(today, calendarQEnd);
    }

    private java.time.LocalDate toLocalDate(Object value) {
        if (value instanceof java.time.LocalDate ld) return ld;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return null;
    }

    private Map<String, Object> loadIncentiveDetails(UUID incentiveId) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT name, description, incentive_type, start_date, end_date " +
                "FROM incentives WHERE id = ?::uuid", incentiveId);
        return results.isEmpty() ? null : results.get(0);
    }

    private BigDecimal loadBudgetRemainingPct(UUID incentiveId) {
        List<BigDecimal> results = jdbcTemplate.queryForList(
                "SELECT CASE WHEN SUM(ib.total_budget) > 0 " +
                "  THEN 100.0 - (COALESCE(SUM(bu.utilized), 0) / SUM(ib.total_budget) * 100) " +
                "  ELSE 100 END " +
                "FROM incentive_budgets ib " +
                "LEFT JOIN budget_utilizations bu ON bu.incentive_id = ib.incentive_id " +
                "  AND bu.currency_id = ib.currency_id " +
                "WHERE ib.incentive_id = ?::uuid",
                BigDecimal.class, incentiveId);
        return results.isEmpty() || results.get(0) == null ? new BigDecimal("100") : results.get(0);
    }

    // ─── Reason Summary ─────────────────────────────────────────────

    private String generateReasonSummary(String reasonCode, String recType,
                                          Map<String, Object> targetDetails) {
        if (reasonCode == null) return "Recommended based on your profile";

        String category = targetDetails != null
                ? (String) targetDetails.getOrDefault("product_category",
                    targetDetails.getOrDefault("category", ""))
                : "";

        return switch (reasonCode) {
            case "SALES_ALIGNMENT" -> "Aligns with your " + category + " sales";
            case "TRAINING_LIFT" -> "Trained sellers see higher deal sizes in " + category;
            case "INCENTIVE_REQUIRED" -> "Required for an active incentive";
            case "SKILL_GAP" -> "Build skills in " + category;
            case "SALES_PATTERN_MATCH" -> "Matches your sales pattern";
            case "TRAINING_READINESS" -> "You're well-prepared for this incentive";
            case "BUDGET_ATTRACTIVE" -> "Good budget remaining — participate now";
            case "PAYOUT_ATTRACTIVE" -> "Strong reward potential for your deal sizes";
            default -> "Recommended based on your profile";
        };
    }
}
