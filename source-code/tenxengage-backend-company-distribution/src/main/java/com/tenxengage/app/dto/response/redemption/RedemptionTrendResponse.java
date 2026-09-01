package com.tenxengage.app.dto.response.redemption;

import java.time.Instant;
import java.util.List;

/**
 * Redemption rate trend time series for the authenticated tenant (FR-08.4).
 *
 * <p>dateWindow echoes effective window; dataPoints ordered period_date ASC,
 * currency_type ASC; lastRefreshedAt from analytics_mv_refresh_log (nullable).
 */
public record RedemptionTrendResponse(
        DateWindowDto dateWindow,
        List<TrendDataPointDto> dataPoints,
        Instant lastRefreshedAt
) {
}
