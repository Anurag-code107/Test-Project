package com.tenxengage.app.dto.response.redemption;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Response DTO for GET /api/v1/redemption/analytics/advanced/refresh-status.
 *
 * <p>{@code lastRefreshedAt} is {@code null} when no MV refresh has ever run (first deploy);
 * the client treats {@code null} as stale. {@code staleThresholdHours} is always 4 and is
 * included so the frontend can render a countdown without hard-coding the threshold.
 */
public record AnalyticsRefreshStatusResponse(
        boolean isStale,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant lastRefreshedAt,
        int stalenessThresholdHours
) {

    /**
     * Factory method: computes {@code isStale} from the last refresh timestamp and the
     * staleness threshold (in hours).  If {@code lastRefreshedAt} is {@code null} (no
     * refresh has run yet), {@code isStale} is always {@code true}.
     *
     * @param lastRefreshedAt timestamp of the most recent successful MV refresh, or {@code null}
     * @param thresholdHours  the staleness threshold in hours (spec mandates 4)
     */
    public static AnalyticsRefreshStatusResponse of(Instant lastRefreshedAt, int thresholdHours) {
        boolean stale = lastRefreshedAt == null
                || lastRefreshedAt.isBefore(Instant.now().minus(thresholdHours, ChronoUnit.HOURS));
        return new AnalyticsRefreshStatusResponse(stale, lastRefreshedAt, thresholdHours);
    }
}
