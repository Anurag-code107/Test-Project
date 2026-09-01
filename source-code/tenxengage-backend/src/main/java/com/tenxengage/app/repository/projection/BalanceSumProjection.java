package com.tenxengage.app.repository.projection;

import java.math.BigDecimal;

public interface BalanceSumProjection {
    BigDecimal getAvailable();
    BigDecimal getReserved();
}
