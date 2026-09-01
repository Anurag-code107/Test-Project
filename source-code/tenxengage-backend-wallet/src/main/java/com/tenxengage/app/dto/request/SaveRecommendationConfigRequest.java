package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaveRecommendationConfigRequest(
    @NotNull Boolean trainingEnabled,
    @NotNull Boolean incentiveEnabled,
    @Min(1) @Max(20) Integer maxTrainingRecommendations,
    @Min(1) @Max(20) Integer maxIncentiveRecommendations,
    String rewardCurrencyId,
    @Min(0) BigDecimal trainingCompletionReward,
    @Min(0) BigDecimal incentiveCompletionReward
) {}
