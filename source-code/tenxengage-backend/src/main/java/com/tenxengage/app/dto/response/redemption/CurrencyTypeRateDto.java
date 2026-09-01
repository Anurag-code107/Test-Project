package com.tenxengage.app.dto.response.redemption;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CurrencyTypeRateDto(
        String currencyId,
        long numerator,
        long denominator,
        String ratePercentage,
        boolean hasActivity
) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Builds a rate DTO. {@code hasActivity} is {@code false} when {@code denominator == 0}
     * (avoids divide-by-zero; FR-07.8 empty state).
     *
     * @param currencyId  platform currency identifier
     * @param numerator   absolute numerator (total redeemed amount or failed+cancelled count)
     * @param denominator absolute denominator (total earned amount or total requests in window)
     */
    public static CurrencyTypeRateDto of(String currencyId, long numerator, long denominator) {
        boolean hasActivity = denominator > 0;
        String rate = hasActivity
                ? BigDecimal.valueOf(numerator)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                        .toPlainString()
                : "0.00";
        return new CurrencyTypeRateDto(currencyId, numerator, denominator, rate, hasActivity);
    }
}
