package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.PayoutConfig;

import java.util.List;
import java.util.UUID;

public record PayoutConfigResponse(
    UUID id,
    String currencyId,
    String payoutType,
    String against,
    String maxPerDeal,
    List<PayoutBandResponse> bands
) {

    public static PayoutConfigResponse from(PayoutConfig config) {
        return new PayoutConfigResponse(
            config.getId(),
            config.getCurrencyId(),
            config.getPayoutType().name(),
            config.getAgainst(),
            config.getMaxPerDeal() != null ? config.getMaxPerDeal().toPlainString() : null,
            config.getBands().stream()
                .map(PayoutBandResponse::from)
                .toList()
        );
    }
}
