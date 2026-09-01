package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.WalletType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
    UUID id,
    UUID clientId,
    WalletType walletType,
    UUID userId,
    UUID partnerCompanyId,
    String currencyId,
    BigDecimal availableBalance,
    BigDecimal reservedBalance,
    Instant createdAt,
    Instant updatedAt
) {
    public static WalletResponse from(RewardWallet wallet) {
        return new WalletResponse(
            wallet.getId(),
            wallet.getClientId(),
            wallet.getWalletType(),
            wallet.getUserId(),
            wallet.getPartnerCompanyId(),
            wallet.getCurrencyId(),
            wallet.getAvailableBalance(),
            wallet.getReservedBalance(),
            wallet.getCreatedAt(),
            wallet.getUpdatedAt()
        );
    }
}
