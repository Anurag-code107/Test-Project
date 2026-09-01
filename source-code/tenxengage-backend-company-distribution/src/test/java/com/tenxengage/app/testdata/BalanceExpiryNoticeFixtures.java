package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.BalanceExpiryNotice;
import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class BalanceExpiryNoticeFixtures {

    private BalanceExpiryNoticeFixtures() {
    }

    public static BalanceExpiryNotice.BalanceExpiryNoticeBuilder scheduledNotice(
            UUID clientId, UUID walletId, String currencyId, UUID policyId) {
        return BalanceExpiryNotice.builder()
                .clientId(clientId)
                .walletId(walletId)
                .currencyId(currencyId)
                .policyId(policyId)
                .scheduledExpiryDate(LocalDate.now().plusDays(30))
                .status(ExpiryNoticeStatus.SCHEDULED)
                .deleted(false);
    }

    public static BalanceExpiryNotice.BalanceExpiryNoticeBuilder notifiedNotice(
            UUID clientId, UUID walletId, String currencyId, UUID policyId) {
        return scheduledNotice(clientId, walletId, currencyId, policyId)
                .status(ExpiryNoticeStatus.NOTIFIED)
                .notifiedAt(Instant.now())
                .notifiedAmount(new BigDecimal("100.00"));
    }

    public static BalanceExpiryNotice.BalanceExpiryNoticeBuilder expiredNotice(
            UUID clientId, UUID walletId, String currencyId, UUID policyId) {
        return scheduledNotice(clientId, walletId, currencyId, policyId)
                .scheduledExpiryDate(LocalDate.now().minusDays(1))
                .status(ExpiryNoticeStatus.EXPIRED)
                .notifiedAt(Instant.now().minusSeconds(86400 * 30L))
                .notifiedAmount(new BigDecimal("100.00"))
                .expiredAt(Instant.now())
                .expiredAmount(new BigDecimal("100.00"));
    }

    public static BalanceExpiryNotice.BalanceExpiryNoticeBuilder cancelledNotice(
            UUID clientId, UUID walletId, String currencyId, UUID policyId) {
        return scheduledNotice(clientId, walletId, currencyId, policyId)
                .status(ExpiryNoticeStatus.CANCELLED)
                .cancelledAt(Instant.now());
    }
}
