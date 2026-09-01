package com.tenxengage.app.dto.response.redemption;

import java.time.LocalDate;

/**
 * One calendar-day data point in the redemption rate trend (FR-08.4).
 *
 * <p>Field names match the contract schema exactly:
 * periodDate, currencyId, redeemedCount, redemptionRate.
 * Note: totalIssued is absent from mv_redemption_rate_trend DDL (V28) -- omitted.
 */
public record TrendDataPointDto(
        LocalDate periodDate,
        String currencyId,
        long redeemedCount,
        double redemptionRate
) {
}
