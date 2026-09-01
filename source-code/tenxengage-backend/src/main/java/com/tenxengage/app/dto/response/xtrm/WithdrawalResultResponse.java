package com.tenxengage.app.dto.response.xtrm;

import com.tenxengage.app.entity.xtrm.PartnerWithdrawal;

import java.math.BigDecimal;

/**
 * Result of a withdrawal step.
 *
 * <ul>
 *   <li><b>Initiate</b> → {@code otpRequired=true} and all amounts null: XTRM has sent the OTP; the client
 *       prompts for it and calls confirm.</li>
 *   <li><b>Confirm</b> → {@code otpRequired=false} with the executed amounts: {@code amountGross} debited
 *       from the wallet, {@code fee} taken by XTRM, {@code amountNet} delivered to the destination.</li>
 * </ul>
 */
public record WithdrawalResultResponse(
        boolean otpRequired,
        String transactionId,
        String status,
        BigDecimal amountGross,
        BigDecimal fee,
        BigDecimal amountNet,
        String currency,
        String destinationLabel) {

    /** The OTP-sent result of the initiate step (no transaction yet). */
    public static WithdrawalResultResponse otpSent() {
        return new WithdrawalResultResponse(true, null, null, null, null, null, null, null);
    }

    /** The completed result, built from the persisted withdrawal row. */
    public static WithdrawalResultResponse of(PartnerWithdrawal w) {
        return new WithdrawalResultResponse(
                false,
                w.getXtrmPaymentTransactionId(),
                w.getStatus(),
                w.getAmountGross(),
                w.getFee(),
                w.getAmountNet(),
                w.getCurrency(),
                w.getDestinationLabel());
    }
}
