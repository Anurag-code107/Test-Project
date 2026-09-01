package com.tenxengage.app.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BreakageRowProjection {
    LocalDate getPeriodStart();
    String getCurrencyId();
    long getExpiredCount();
    BigDecimal getTotalExpiredAmount();
}
