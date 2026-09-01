package com.tenxengage.app.dto.response;

import java.math.BigDecimal;

public record ProgramPerformanceResponse(
    // Incentive metrics (hero cards)
    MetricResponse totalRewardsEarned,
    MetricResponse budgetUtilized,
    MetricResponse usersParticipating,
    HomeRewardBreakdownData rewardBreakdown,
    BigDecimal totalBudget,
    // Participation metrics (flat cards)
    boolean partnerFiltered,
    MetricResponse partnerCompaniesEnrolled,
    MetricResponse partnerUsersEnrolled,
    MetricResponse companiesEarningRewards,
    MetricResponse partnerEnrolledUsers,
    MetricResponse usersEarningRewards,
    MetricResponse userClaimsMade,
    // Display context
    String currentQuarterLabel
) {}
