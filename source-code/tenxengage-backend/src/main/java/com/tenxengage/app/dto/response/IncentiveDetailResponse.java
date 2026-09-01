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

public record IncentiveDetailResponse(
    UUID id,
    String name,
    String description,
    IncentiveType incentiveType,
    IncentiveStatus status,
    Instant startDate,
    Instant endDate,
    String budgetTotal,
    String budgetCurrency,
    String createdByName,
    Instant createdAt,
    Instant updatedAt,
    String timezone,
    List<BudgetResponse> budgets,
    String maxPerPartner,
    String maxPerUser,
    Integer maxClaimersPerDeal,
    List<String> rewardCurrencies,
    String rewardMessage,
    Map<String, String> rewardAmounts,
    List<AudienceRuleResponse> audienceRules,
    List<SalesRequirementResponse> salesRequirements,
    List<TrainingCourseResponse> trainingCourses,
    List<ActivityDefinitionResponse> activityDefinitions,
    List<JourneyStageResponse> journeyStages,
    Boolean journeySequential,
    ForecastResponse forecast,
    List<DocumentSummaryResponse> documents,
    List<String> fiscalYears,
    List<String> fiscalQuarters,
    Integer trainingRequiredCount,
    String countriesText,
    String specificPartners,
    String customFieldValues,
    Boolean requiresApproval,
    List<ApproverResponse> approvers,
    Integer requiredApprovals,
    ApprovalStatusResponse approvalStatus,
    Instant statusChangedAt
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static IncentiveDetailResponse from(Incentive incentive,
                                                String createdByName,
                                                ForecastResponse forecast,
                                                ApprovalStatusResponse approvalStatus) {
        return from(incentive, createdByName, forecast, approvalStatus, Map.of());
    }

    public static IncentiveDetailResponse from(Incentive incentive,
                                                String createdByName,
                                                ForecastResponse forecast,
                                                ApprovalStatusResponse approvalStatus,
                                                Map<String, String> locationValueNames) {
        String budgetTotal = null;
        String budgetCurrency = null;
        var primaryBudget = (incentive.getBudgets() != null && !incentive.getBudgets().isEmpty())
            ? incentive.getBudgets().get(0) : null;
        if (primaryBudget != null) {
            budgetTotal = primaryBudget.getTotalBudget().toPlainString();
            budgetCurrency = primaryBudget.getCurrencyId();
        }

        List<BudgetResponse> budgetsList = (incentive.getBudgets() != null)
            ? incentive.getBudgets().stream().map(BudgetResponse::from).toList()
            : Collections.emptyList();

        return new IncentiveDetailResponse(
            incentive.getId(),
            incentive.getName(),
            incentive.getDescription(),
            incentive.getIncentiveType(),
            incentive.getStatus(),
            incentive.getStartDate(),
            incentive.getEndDate(),
            budgetTotal,
            budgetCurrency,
            createdByName,
            incentive.getCreatedAt(),
            incentive.getUpdatedAt(),
            incentive.getTimezone(),
            budgetsList,
            incentive.getMaxPerPartner() != null ? incentive.getMaxPerPartner().toPlainString() : null,
            incentive.getMaxPerUser() != null ? incentive.getMaxPerUser().toPlainString() : null,
            incentive.getMaxClaimersPerDeal(),
            parseJsonList(incentive.getRewardCurrencies()),
            incentive.getRewardMessage(),
            parseJsonMap(incentive.getRewardAmounts()),
            incentive.getAudienceRules().stream()
                .map(rule -> AudienceRuleResponse.from(rule,
                    locationValueNames.getOrDefault(rule.getRuleValue(), null)))
                .toList(),
            incentive.getSalesRequirements().stream()
                .map(SalesRequirementResponse::from)
                .toList(),
            incentive.getTrainingCourses().stream()
                .map(TrainingCourseResponse::from)
                .toList(),
            incentive.getActivityDefinitions().stream()
                .map(ActivityDefinitionResponse::from)
                .toList(),
            incentive.getJourneyStages().stream()
                .map(JourneyStageResponse::from)
                .toList(),
            incentive.getJourneySequential(),
            forecast,
            incentive.getDocuments() != null
                ? incentive.getDocuments().stream().map(DocumentSummaryResponse::from).toList()
                : Collections.emptyList(),
            parseJsonList(incentive.getFiscalYears()),
            parseJsonList(incentive.getFiscalQuarters()),
            incentive.getTrainingRequiredCount(),
            incentive.getCountriesText(),
            incentive.getSpecificPartners(),
            incentive.getCustomFieldValues(),
            incentive.getRequiresApproval(),
            incentive.getApprovers() != null
                ? incentive.getApprovers().stream().map(ApproverResponse::from).toList()
                : Collections.emptyList(),
            incentive.getRequiredApprovals(),
            approvalStatus,
            incentive.getStatusChangedAt()
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
