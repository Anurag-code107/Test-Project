package com.tenxengage.app.dto.request;

import java.util.List;

public record PayoutConfigRequest(
    String currencyId,
    String payoutType,
    String against,
    String maxPerDeal,
    List<PayoutBandRequest> bands
) {
}
