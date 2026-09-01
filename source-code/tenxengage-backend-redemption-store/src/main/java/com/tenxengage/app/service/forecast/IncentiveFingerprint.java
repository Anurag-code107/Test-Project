package com.tenxengage.app.service.forecast;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Comparable feature vector extracted from an incentive for similarity scoring.
 */
public record IncentiveFingerprint(
    String incentiveType,
    Set<String> regions,
    Set<String> productCategories,
    BigDecimal totalBudget,
    int durationDays,
    String payoutType,
    int bandCount,
    Set<String> partnerTypes
) {}
