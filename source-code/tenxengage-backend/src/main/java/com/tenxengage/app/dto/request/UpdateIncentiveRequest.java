package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record UpdateIncentiveRequest(
    @Size(max = 255) String name,
    @Size(max = 2000) String description,
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
