package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record MetricResponse(
    BigDecimal value,
    String subValue,
    BigDecimal trendPercent,
    List<TrendDataPoint> trendData
) {}
