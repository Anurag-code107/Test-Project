package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RedemptionRequestFixtures {

    private RedemptionRequestFixtures() {
    }

    public static RedemptionRequest.RedemptionRequestBuilder defaultPersonal(
            UUID clientId, UUID userId, UUID walletId, UUID catalogItemId) {
        return RedemptionRequest.builder()
                .clientId(clientId)
                .userId(userId)
                .walletId(walletId)
                .catalogItemId(catalogItemId)
                .amount(new BigDecimal("100.0000"))
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.PENDING_APPROVAL)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.NON_CASH)
                .submittedAt(Instant.now())
                .deleted(false);
    }

    public static RedemptionRequest.RedemptionRequestBuilder defaultCompany(
            UUID clientId, UUID userId, UUID walletId, UUID catalogItemId) {
        return defaultPersonal(clientId, userId, walletId, catalogItemId)
                .walletType(WalletType.COMPANY);
    }

    public static RedemptionRequest.RedemptionRequestBuilder withStatus(
            UUID clientId, UUID userId, UUID walletId, UUID catalogItemId, RedemptionStatus status) {
        return defaultPersonal(clientId, userId, walletId, catalogItemId)
                .status(status);
    }

    public static RedemptionRequest.RedemptionRequestBuilder withProcessingMode(
            UUID clientId, UUID userId, UUID walletId, UUID catalogItemId, RedemptionProcessingMode mode) {
        return defaultPersonal(clientId, userId, walletId, catalogItemId)
                .processingMode(mode);
    }

    public static RedemptionRequest.RedemptionRequestBuilder withScheduledBatchDate(
            UUID clientId, UUID userId, UUID walletId, UUID catalogItemId, LocalDate batchDate) {
        return defaultPersonal(clientId, userId, walletId, catalogItemId)
                .processingMode(RedemptionProcessingMode.BATCH)
                .scheduledBatchDate(batchDate)
                .status(RedemptionStatus.RESERVED);
    }

    public static RedemptionRequest.RedemptionRequestBuilder inFlight(
            UUID clientId, UUID userId, UUID walletId, UUID catalogItemId) {
        return defaultPersonal(clientId, userId, walletId, catalogItemId)
                .status(RedemptionStatus.RESERVED);
    }
}
