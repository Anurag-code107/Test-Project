package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RecommendationConfig;

import java.math.BigDecimal;
import java.util.UUID;

public record RecommendationConfigResponse(
    UUID id,
    boolean trainingEnabled,
    boolean incentiveEnabled,
    int maxTrainingRecommendations,
    int maxIncentiveRecommendations,
    String rewardCurrencyId,
    BigDecimal trainingCompletionReward,
    BigDecimal incentiveCompletionReward
) {
    public static RecommendationConfigResponse from(RecommendationConfig config) {
        return new RecommendationConfigResponse(
                config.getId(),
                config.isTrainingEnabled(),
                config.isIncentiveEnabled(),
                config.getMaxTrainingRecommendations(),
                config.getMaxIncentiveRecommendations(),
                config.getRewardCurrencyId(),
                config.getTrainingCompletionReward(),
                config.getIncentiveCompletionReward()
        );
    }

    public static RecommendationConfigResponse defaults() {
        return new RecommendationConfigResponse(
                null, true, true, 5, 5, null, BigDecimal.ZERO, BigDecimal.ZERO
        );
    }
}
