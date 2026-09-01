package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RewardWallet;

import java.util.UUID;

public record RewardWalletResponse(
    UUID id,
    String walletType,
    String currencyId,
    String availableBalance,
    String reservedBalance
) {
    public static RewardWalletResponse from(RewardWallet w) {
        return new RewardWalletResponse(
            w.getId(),
            w.getWalletType().name(),
            w.getCurrencyId(),
            w.getAvailableBalance().toPlainString(),
            w.getReservedBalance().toPlainString()
        );
    }
}
