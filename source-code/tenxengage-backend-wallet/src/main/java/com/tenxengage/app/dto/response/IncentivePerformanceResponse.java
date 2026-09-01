package com.tenxengage.app.dto.response;

import java.math.BigDecimal;

public record IncentivePerformanceResponse(
    MetricResponse totalRewardsEarned,
    MetricResponse budgetUtilized,
    MetricResponse usersParticipating,
    HomeRewardBreakdownData rewardBreakdown,
    BigDecimal totalBudget
) {}
