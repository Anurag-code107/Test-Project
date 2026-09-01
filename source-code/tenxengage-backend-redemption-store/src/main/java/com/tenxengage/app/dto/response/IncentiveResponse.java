package com.tenxengage.app.dto.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record IncentiveResponse(
    UUID id,
    String name,
    String description,
    IncentiveType incentiveType,
    IncentiveStatus status,
    Instant startDate,
    Instant endDate,
    String budgetTotal,
    String budgetCurrency,
    List<BudgetResponse> budgets,
    String createdByName,
    Instant createdAt,
    Instant updatedAt,
    List<String> rewardCurrencies,
    String rewardMessage,
    Map<String, String> rewardAmounts,
    Integer budgetUtilizationPercent,
    List<DocumentSummaryResponse> documents,
    Integer trainingCourseCount,
    Integer activityDefinitionCount,
    Integer partnerProgressCompleted,
    String partnerProgressLabel,
    List<JourneyStageSummaryResponse> journeyStages,
    Boolean requiresApproval,
    Instant statusChangedAt,
    Boolean userCompleted,
    Instant userCompletedAt
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static IncentiveResponse from(Incentive incentive, String createdByName) {
        return from(incentive, createdByName, Collections.emptyList(), null, null, null, null,
            Collections.emptyList(), null, null, null, null);
    }

    public static IncentiveResponse from(
            Incentive incentive,
            String createdByName,
            List<DocumentSummaryResponse> documents,
            Integer trainingCourseCount,
            Integer activityDefinitionCount,
            Integer partnerProgressCompleted,
            String partnerProgressLabel,
            List<JourneyStageSummaryResponse> journeyStages,
            Boolean requiresApproval,
            Boolean userCompleted,
            Instant userCompletedAt,
            Integer budgetUtilizationPct
    ) {
        String budgetTotal = null;
        String budgetCurrency = null;
        Integer utilizationPercent = budgetUtilizationPct;
        if (incentive.getBudgets() != null && !incentive.getBudgets().isEmpty()) {
            var primaryBudget = incentive.getBudgets().get(0);
            budgetTotal = primaryBudget.getTotalBudget().toPlainString();
            budgetCurrency = primaryBudget.getCurrencyId();
        }

        List<BudgetResponse> budgetsList = (incentive.getBudgets() != null)
            ? incentive.getBudgets().stream().map(BudgetResponse::from).toList()
            : Collections.emptyList();

        // Defense-in-depth: never return ACTIVE for an expired incentive
        IncentiveStatus effectiveStatus = incentive.getStatus();
        if (effectiveStatus == IncentiveStatus.ACTIVE
                && incentive.getEndDate() != null
                && incentive.getEndDate().isBefore(Instant.now())) {
            effectiveStatus = IncentiveStatus.INACTIVE;
        }

        return new IncentiveResponse(
            incentive.getId(),
            incentive.getName(),
            incentive.getDescription(),
            incentive.getIncentiveType(),
            effectiveStatus,
            incentive.getStartDate(),
            incentive.getEndDate(),
            budgetTotal,
            budgetCurrency,
            budgetsList,
            createdByName,
            incentive.getCreatedAt(),
            incentive.getUpdatedAt(),
            parseJsonList(incentive.getRewardCurrencies()),
            incentive.getRewardMessage(),
            parseJsonMap(incentive.getRewardAmounts()),
            utilizationPercent,
            documents,
            trainingCourseCount,
            activityDefinitionCount,
            partnerProgressCompleted,
            partnerProgressLabel,
            journeyStages,
            requiresApproval,
            incentive.getStatusChangedAt(),
            userCompleted,
            userCompletedAt
        );
    }

    private static List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
