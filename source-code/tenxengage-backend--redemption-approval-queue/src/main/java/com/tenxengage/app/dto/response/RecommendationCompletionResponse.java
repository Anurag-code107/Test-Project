package com.tenxengage.app.dto.response;

import java.math.BigDecimal;

public record RecommendationCompletionResponse(
    boolean rewardEarned,
    BigDecimal rewardAmount,
    String rewardCurrencyId
) {}
