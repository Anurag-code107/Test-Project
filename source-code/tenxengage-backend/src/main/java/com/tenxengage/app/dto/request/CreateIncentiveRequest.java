package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.IncentiveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateIncentiveRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2000) String description,
    @NotNull IncentiveType incentiveType,
    String startDate,
    String endDate,
    @Size(max = 50) String timezone,
    List<BudgetRequest> budgets,
    String maxPerPartner,
    String maxPerUser,
    Integer maxClaimersPerDeal,
    List<AudienceRuleRequest> audienceRules,
    List<String> rewardCurrencies,
    @Size(max = 500) String rewardMessage,
    Map<String, String> rewardAmounts,
    List<SalesRequirementRequest> salesRequirements,
    List<TrainingCourseRequest> trainingCourses,
    List<ActivityDefinitionRequest> activityDefinitions,
    List<JourneyStageRequest> journeyStages,
    Boolean journeySequential,
    List<DocumentRequest> documents,
    List<String> fiscalYears,
    List<String> fiscalQuarters,
    Integer trainingRequiredCount,
    String countriesText,
    String specificPartners,
    String customFieldValues,
    Boolean requiresApproval,
    List<ApproverRequest> approvers,
    Integer requiredApprovals
) {
}
