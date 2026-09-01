package com.tenxengage.app.dto.request;

public record PayoutBandRequest(
    String minAmount,
    String maxAmount,
    String payoutValue
) {
}
