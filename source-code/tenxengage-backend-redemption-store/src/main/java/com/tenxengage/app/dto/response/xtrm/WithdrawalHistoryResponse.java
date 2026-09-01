package com.tenxengage.app.dto.response.xtrm;

import com.tenxengage.app.entity.xtrm.PartnerWithdrawal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row in the user's withdrawal history (newest first). {@code destinationRef} (our bank/card PK) and the
 * XTRM transaction id are intentionally omitted — the list shows only the masked label + amounts + status.
 */
public record WithdrawalHistoryResponse(
        UUID id,
        BigDecimal amountGross,
        BigDecimal fee,
        BigDecimal amountNet,
        String currency,
        String destinationType,
        String destinationLabel,
        String status,
        Instant createdAt) {

    public static WithdrawalHistoryResponse of(PartnerWithdrawal w) {
        return new WithdrawalHistoryResponse(
                w.getId(),
                w.getAmountGross(),
                w.getFee(),
                w.getAmountNet(),
                w.getCurrency(),
                w.getDestinationType(),
                w.getDestinationLabel(),
                w.getStatus(),
                w.getCreatedAt());
    }
}
