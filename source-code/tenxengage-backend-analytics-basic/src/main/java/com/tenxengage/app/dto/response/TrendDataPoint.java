package com.tenxengage.app.dto.response;

import java.math.BigDecimal;

public record TrendDataPoint(
    String label,
    BigDecimal value
) {}
