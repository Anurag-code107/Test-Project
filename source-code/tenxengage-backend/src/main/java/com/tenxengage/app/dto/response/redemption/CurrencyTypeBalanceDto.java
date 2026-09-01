package com.tenxengage.app.dto.response.redemption;

public record CurrencyTypeBalanceDto(
        String currencyId,
        long availableBalance,
        long reservedBalance,
        long totalOutstanding
) {

    /**
     * Builds a balance DTO.
     *
     * @param currencyId       platform currency identifier
     * @param availableBalance sum of availableBalance across all wallets (in minor units)
     * @param reservedBalance  sum of reservedBalance across all wallets (in minor units)
     */
    public static CurrencyTypeBalanceDto of(String currencyId, long availableBalance, long reservedBalance) {
        return new CurrencyTypeBalanceDto(
                currencyId,
                availableBalance,
                reservedBalance,
                availableBalance + reservedBalance
        );
    }
}
