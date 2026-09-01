package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedemptionRequestResponseTest {

    // BU-10: the history list DTO carries the redemption category (drives the FE Actions column).
    @Test
    void from_carriesCategory() {
        RedemptionRequest req = RedemptionRequest.builder()
                .amount(new BigDecimal("50.00"))
                .currencyId("cash")
                .status(RedemptionStatus.COMPLETED)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.NON_CASH)
                .submittedAt(Instant.now())
                .build();
        req.setId(UUID.randomUUID());

        RedemptionRequestResponse dto = RedemptionRequestResponse.from(req, "Amazon Gift Card");

        assertThat(dto.category()).isEqualTo("NON_CASH");
        assertThat(dto.catalogItemName()).isEqualTo("Amazon Gift Card");
    }

    @Test
    void from_carriesCashCategory() {
        RedemptionRequest req = RedemptionRequest.builder()
                .amount(new BigDecimal("10.00"))
                .currencyId("cash")
                .status(RedemptionStatus.PROCESSING)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.CASH)
                .submittedAt(Instant.now())
                .build();
        req.setId(UUID.randomUUID());

        assertThat(RedemptionRequestResponse.from(req, "Bank Transfer").category()).isEqualTo("CASH");
    }
}
