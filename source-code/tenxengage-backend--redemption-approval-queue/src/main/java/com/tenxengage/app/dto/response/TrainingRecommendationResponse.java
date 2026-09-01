package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TrainingRecommendationResponse(
    UUID courseId,
    String courseName,
    String courseDescription,
    String courseCategory,
    String courseLevel,
    String courseDuration,
    String courseProvider,
    String productCategory,
    String courseUrl,
    BigDecimal score,
    int rank,
    String reasonCode,
    String reasonSummary,
    BigDecimal rewardAmount,
    String rewardCurrencyId,
    int daysUntilQuarterEnd
) {}
