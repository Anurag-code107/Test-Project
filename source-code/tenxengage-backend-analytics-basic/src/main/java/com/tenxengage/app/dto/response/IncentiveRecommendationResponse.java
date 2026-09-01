package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IncentiveRecommendationResponse(
    UUID incentiveId,
    String incentiveName,
    String incentiveType,
    String description,
    Instant startDate,
    Instant endDate,
    BigDecimal score,
    int rank,
    String reasonCode,
    String reasonSummary,
    BigDecimal budgetRemainingPct,
    String rewardCurrency,
    BigDecimal rewardAmount
) {}
