package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class RedemptionReturnFixtures {

    private RedemptionReturnFixtures() {
    }

    public static RedemptionReturn.RedemptionReturnBuilder defaultReturn(
            UUID clientId, UUID redemptionId, UUID partnerUserId) {
        return RedemptionReturn.builder()
                .clientId(clientId)
                .redemptionId(redemptionId)
                .partnerUserId(partnerUserId)
                .amount(new BigDecimal("50.0000"))
                .currencyId("cash")
                .status(ReturnStatus.PENDING_APPROVAL)
                .deleted(false);
    }

    public static RedemptionReturn.RedemptionReturnBuilder aSubmittedReturn(
            UUID clientId, UUID redemptionId, UUID partnerUserId) {
        return defaultReturn(clientId, redemptionId, partnerUserId)
                .status(ReturnStatus.PENDING_APPROVAL);
    }

    public static RedemptionReturn.RedemptionReturnBuilder anApprovedReturn(
            UUID clientId, UUID redemptionId, UUID partnerUserId) {
        return defaultReturn(clientId, redemptionId, partnerUserId)
                .status(ReturnStatus.APPROVED)
                .approvedAt(Instant.now());
    }

    public static RedemptionReturn.RedemptionReturnBuilder aConfirmedReturn(
            UUID clientId, UUID redemptionId, UUID partnerUserId) {
        return defaultReturn(clientId, redemptionId, partnerUserId)
                .status(ReturnStatus.RETURN_CONFIRMED)
                .approvedAt(Instant.now().minusSeconds(3600))
                .confirmedAt(Instant.now());
    }

    public static RedemptionReturn.RedemptionReturnBuilder aRejectedReturn(
            UUID clientId, UUID redemptionId, UUID partnerUserId) {
        return defaultReturn(clientId, redemptionId, partnerUserId)
                .status(ReturnStatus.RETURN_REJECTED)
                .rejectedAt(Instant.now());
    }

    public static RedemptionReturn.RedemptionReturnBuilder aCancelledReturn(
            UUID clientId, UUID redemptionId, UUID partnerUserId) {
        return defaultReturn(clientId, redemptionId, partnerUserId)
                .status(ReturnStatus.CANCELLED)
                .cancelledAt(Instant.now());
    }

    public static RedemptionReturn.RedemptionReturnBuilder aTimedOutReturn(
            UUID clientId, UUID redemptionId, UUID partnerUserId) {
        return defaultReturn(clientId, redemptionId, partnerUserId)
                .status(ReturnStatus.RETURN_TIMED_OUT)
                .approvedAt(Instant.now().minusSeconds(86400L * 8))
                .timedOutAt(Instant.now());
    }
}
