package com.tenxengage.app.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExpiringBalancePreviewProjection {
    String getCurrencyId();
    LocalDate getScheduledExpiryDate();
    long getAffectedWalletCount();
    BigDecimal getTotalAmountAtRisk();
}
