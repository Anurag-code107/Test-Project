package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RewardBalance;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RewardBalanceResponse(
    UUID id,
    String walletType,
    String currencyId,
    String availableBalance,
    String reservedBalance,
    String balance  // alias for availableBalance — backwards compat
) {
    @Deprecated
    public static RewardBalanceResponse from(RewardBalance rb) {
        Objects.requireNonNull(rb, "RewardBalance must not be null");
        BigDecimal raw = rb.getBalance() != null ? rb.getBalance() : BigDecimal.ZERO;
        String bal = raw.toPlainString();
        return new RewardBalanceResponse(null, null, rb.getCurrencyId(), bal, "0.00", bal);
    }

    public static RewardBalanceResponse fromWallet(RewardWalletResponse w) {
        return new RewardBalanceResponse(
            w.id(),
            w.walletType(),
            w.currencyId(),
            w.availableBalance(),
            w.reservedBalance(),
            w.availableBalance()
        );
    }
}
