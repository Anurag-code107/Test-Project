package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record HomeRewardBreakdownData(
    Map<String, CurrencyAmount> monetary,
    Map<String, CurrencyCount> nonMonetary
) {

    public record CurrencyAmount(BigDecimal amount, BigDecimal percent) {}

    public record CurrencyCount(long count) {}
}
