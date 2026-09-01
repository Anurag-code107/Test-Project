package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.PayoutBand;

import java.util.UUID;

public record PayoutBandResponse(
    UUID id,
    String minAmount,
    String maxAmount,
    String payoutValue
) {

    public static PayoutBandResponse from(PayoutBand band) {
        return new PayoutBandResponse(
            band.getId(),
            band.getMinAmount() != null ? band.getMinAmount().toPlainString() : null,
            band.getMaxAmount() != null ? band.getMaxAmount().toPlainString() : null,
            band.getPayoutValue() != null ? band.getPayoutValue().toPlainString() : null
        );
    }
}
