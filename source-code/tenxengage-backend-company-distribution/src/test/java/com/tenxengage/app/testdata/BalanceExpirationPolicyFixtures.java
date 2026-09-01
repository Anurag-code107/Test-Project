package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.BalanceExpirationPolicy;
import com.tenxengage.app.entity.enums.ExpirationMode;

import java.time.LocalDate;
import java.util.UUID;

public final class BalanceExpirationPolicyFixtures {

    private BalanceExpirationPolicyFixtures() {
    }

    public static BalanceExpirationPolicy.BalanceExpirationPolicyBuilder inactivityPolicy(UUID clientId, String currencyId) {
        return BalanceExpirationPolicy.builder()
                .clientId(clientId)
                .currencyId(currencyId)
                .enabled(true)
                .expirationMode(ExpirationMode.INACTIVITY)
                .inactivityDays(365)
                .leadTimeDays(30)
                .deleted(false);
    }

    public static BalanceExpirationPolicy.BalanceExpirationPolicyBuilder fixedDatePolicy(UUID clientId, String currencyId) {
        return BalanceExpirationPolicy.builder()
                .clientId(clientId)
                .currencyId(currencyId)
                .enabled(true)
                .expirationMode(ExpirationMode.FIXED_DATE)
                .fixedExpiryDate(LocalDate.now().plusYears(1))
                .leadTimeDays(30)
                .deleted(false);
    }

    public static BalanceExpirationPolicy.BalanceExpirationPolicyBuilder disabledPolicy(UUID clientId, String currencyId) {
        return BalanceExpirationPolicy.builder()
                .clientId(clientId)
                .currencyId(currencyId)
                .enabled(false)
                .expirationMode(ExpirationMode.INACTIVITY)
                .inactivityDays(365)
                .leadTimeDays(30)
                .deleted(false);
    }
}
