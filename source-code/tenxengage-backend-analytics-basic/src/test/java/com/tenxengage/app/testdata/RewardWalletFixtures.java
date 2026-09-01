package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.WalletType;

import java.math.BigDecimal;
import java.util.UUID;

public final class RewardWalletFixtures {

    private RewardWalletFixtures() {
    }

    public static RewardWallet.RewardWalletBuilder individualWallet(UUID clientId, UUID userId) {
        return RewardWallet.builder()
                .clientId(clientId)
                .walletType(WalletType.INDIVIDUAL)
                .userId(userId)
                .currencyId("cash")
                .availableBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO);
    }

    public static RewardWallet.RewardWalletBuilder individualWalletWithBalance(
            UUID clientId, UUID userId, BigDecimal availableBalance) {
        return individualWallet(clientId, userId)
                .availableBalance(availableBalance);
    }

    public static RewardWallet.RewardWalletBuilder companyWallet(UUID clientId, UUID partnerCompanyId) {
        return RewardWallet.builder()
                .clientId(clientId)
                .walletType(WalletType.COMPANY)
                .partnerCompanyId(partnerCompanyId)
                .currencyId("cash")
                .availableBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO);
    }

    public static RewardWallet.RewardWalletBuilder companyWalletWithBalance(
            UUID clientId, UUID partnerCompanyId, BigDecimal availableBalance) {
        return companyWallet(clientId, partnerCompanyId)
                .availableBalance(availableBalance);
    }
}
