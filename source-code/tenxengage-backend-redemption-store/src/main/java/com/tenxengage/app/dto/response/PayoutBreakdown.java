package com.tenxengage.app.dto.response;

import java.math.BigDecimal;

public record PayoutBreakdown(
        BigDecimal currentTierMin,
        BigDecimal currentTierMax,
        BigDecimal currentTierPayoutValue,
        String currentTierPayoutType,
        BigDecimal nextTierMin,
        BigDecimal nextTierPayoutValue,
        BigDecimal gapToNextTier,
        BigDecimal maxPerDeal
) {}
