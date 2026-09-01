package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XoxodayVendorServiceTest {

    private final XoxodayVendorService service = new XoxodayVendorService();

    @Test
    void dispatch_alwaysThrowsUnsupportedOperationException() {
        RedemptionRequest request = RedemptionRequest.builder()
                .userId(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .catalogItemId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.PROCESSING)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.NON_CASH)
                .submittedAt(Instant.now())
                .deleted(false)
                .build();

        assertThatThrownBy(() -> service.dispatch(request))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("US-06 BE-1");
    }
}
