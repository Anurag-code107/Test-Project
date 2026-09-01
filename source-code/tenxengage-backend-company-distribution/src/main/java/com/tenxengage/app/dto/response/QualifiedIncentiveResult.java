package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QualifiedIncentiveResult(
        UUID incentiveId,
        String incentiveName,
        String incentiveDescription,
        String rewardMessage,
        Instant startDate,
        Instant endDate,
        int matchPercentage,
        BigDecimal estimatedReward,
        String rewardCurrency,
        String payoutType,
        List<CriterionResult> metCriteria,
        List<CriterionResult> unmetCriteria,
        PayoutBreakdown payoutBreakdown
) {}
