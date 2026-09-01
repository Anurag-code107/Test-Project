package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.enums.LedgerEntryType;

import java.math.BigDecimal;
import java.util.UUID;

public final class LedgerEntryFixtures {

    private LedgerEntryFixtures() {
    }

    public static LedgerEntry.LedgerEntryBuilder creditEntry(UUID clientId, UUID rewardWalletId, BigDecimal amount) {
        return LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(rewardWalletId)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .currencyId("cash")
                .availableBalanceBefore(BigDecimal.ZERO)
                .availableBalanceAfter(amount)
                .reservedBalanceBefore(BigDecimal.ZERO)
                .reservedBalanceAfter(BigDecimal.ZERO);
    }

    public static LedgerEntry.LedgerEntryBuilder reserveEntry(UUID clientId, UUID rewardWalletId,
            BigDecimal amount, BigDecimal availableBalanceBefore) {
        return LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(rewardWalletId)
                .entryType(LedgerEntryType.RESERVE)
                .amount(amount)
                .currencyId("cash")
                .availableBalanceBefore(availableBalanceBefore)
                .availableBalanceAfter(availableBalanceBefore.subtract(amount))
                .reservedBalanceBefore(BigDecimal.ZERO)
                .reservedBalanceAfter(amount);
    }

    public static LedgerEntry.LedgerEntryBuilder debitEntry(UUID clientId, UUID rewardWalletId,
            BigDecimal amount, BigDecimal reservedBalanceBefore) {
        return LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(rewardWalletId)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .currencyId("cash")
                .availableBalanceBefore(BigDecimal.ZERO)
                .availableBalanceAfter(BigDecimal.ZERO)
                .reservedBalanceBefore(reservedBalanceBefore)
                .reservedBalanceAfter(reservedBalanceBefore.subtract(amount));
    }

    public static LedgerEntry.LedgerEntryBuilder releaseEntry(UUID clientId, UUID rewardWalletId,
            BigDecimal amount, BigDecimal availableBalanceBefore, BigDecimal reservedBalanceBefore) {
        return LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(rewardWalletId)
                .entryType(LedgerEntryType.RELEASE)
                .amount(amount)
                .currencyId("cash")
                .availableBalanceBefore(availableBalanceBefore)
                .availableBalanceAfter(availableBalanceBefore.add(amount))
                .reservedBalanceBefore(reservedBalanceBefore)
                .reservedBalanceAfter(reservedBalanceBefore.subtract(amount));
    }

    public static LedgerEntry.LedgerEntryBuilder returnCreditEntry(UUID clientId, UUID rewardWalletId,
            BigDecimal amount, BigDecimal availableBalanceBefore) {
        return LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(rewardWalletId)
                .entryType(LedgerEntryType.RETURN_CREDIT)
                .amount(amount)
                .currencyId("cash")
                .availableBalanceBefore(availableBalanceBefore)
                .availableBalanceAfter(availableBalanceBefore.add(amount))
                .reservedBalanceBefore(BigDecimal.ZERO)
                .reservedBalanceAfter(BigDecimal.ZERO);
    }
}
