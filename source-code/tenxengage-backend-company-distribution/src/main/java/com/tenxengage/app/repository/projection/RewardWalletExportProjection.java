package com.tenxengage.app.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface RewardWalletExportProjection {
    UUID getUserId();
    String getUserName();
    UUID getCompanyId();
    String getCompanyName();
    String getCurrencyType();
    BigDecimal getAvailableBalance();
    BigDecimal getReservedBalance();
}
