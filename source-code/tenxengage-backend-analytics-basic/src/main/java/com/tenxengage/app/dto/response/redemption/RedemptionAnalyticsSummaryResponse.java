package com.tenxengage.app.dto.response.redemption;

import java.util.List;

public record RedemptionAnalyticsSummaryResponse(
        DateWindowDto dateWindow,
        List<CurrencyTypeRateDto> redemptionRates,
        List<CurrencyTypeBalanceDto> unredeemedBalances,
        List<CurrencyTypeRateDto> failedCancelledRates,
        RedemptionCountDto totalRedemptionCount
) {

    public static RedemptionAnalyticsSummaryResponse of(
            DateWindowDto dateWindow,
            List<CurrencyTypeRateDto> redemptionRates,
            List<CurrencyTypeBalanceDto> unredeemedBalances,
            List<CurrencyTypeRateDto> failedCancelledRates,
            RedemptionCountDto totalRedemptionCount) {

        return new RedemptionAnalyticsSummaryResponse(
                dateWindow,
                List.copyOf(redemptionRates),
                List.copyOf(unredeemedBalances),
                List.copyOf(failedCancelledRates),
                totalRedemptionCount
        );
    }
}
