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

import static org.assertj.core.api.Assertions.assertThat;

class XoxodayVendorServiceTest {

    private final XoxodayVendorService service = new XoxodayVendorService();

    /**
     * The Xoxoday integration is a dev stub (see {@link XoxodayVendorService}): until US-06 BE-1
     * provides real credentials, dispatch simulates success by stamping a fake STUB- vendor
     * reference so the redemption flow can reach COMPLETED in local/dev. This test pins that
     * stub contract; replace it when the real Xoxoday API call lands.
     */
    @Test
    void dispatch_devStub_stampsFakeVendorReferenceId() {
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

        service.dispatch(request);

        assertThat(request.getVendorReferenceId()).startsWith("STUB-");
    }
}
