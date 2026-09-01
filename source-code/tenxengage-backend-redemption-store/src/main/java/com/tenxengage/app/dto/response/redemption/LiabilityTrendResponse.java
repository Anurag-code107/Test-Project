package com.tenxengage.app.dto.response.redemption;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Unredeemed balance liability trend response (FR-08.5).
 *
 * <p>{@code dataPoints} is ordered by {@code period_date ASC, currency_type ASC}
 * — guaranteed by the DB query (AC-1).
 *
 * <p>{@code lastRefreshedAt} reflects the minimum {@code last_refreshed_at} across all
 * MV rows in {@code analytics_mv_refresh_log}.  Can be {@code null} on the first deploy
 * before the scheduler has run.  The global {@code JacksonConfig} sets
 * {@code JsonInclude.Include.NON_NULL}, which would silently drop this field when null and
 * break typed clients that expect stable field presence (anti-pattern register: US-05
 * security-review finding).  {@code @JsonInclude(ALWAYS)} is applied at the field level
 * (not the class level) so that only {@code lastRefreshedAt} overrides NON_NULL — the other
 * fields ({@code dateWindow}, {@code dataPoints}) remain subject to the global setting.
 */
public record LiabilityTrendResponse(
        DateWindowDto dateWindow,
        List<LiabilityDataPointDto> dataPoints,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Instant lastRefreshedAt
) {}
