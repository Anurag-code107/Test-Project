package com.tenxengage.app.dto.response.redemption;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code GET /api/v1/redemption/analytics/advanced/failure-breakdown} (FR-08.7).
 *
 * <p>Fields match the {@code redemption-advanced-analytics.yaml} contract exactly:
 * <ul>
 *   <li>{@code dateWindow} — effective date range applied to the query</li>
 *   <li>{@code failureModes} — rows ordered by {@code failureRate} descending (DB-ordered)</li>
 *   <li>{@code lastRefreshedAt} — nullable; null when no MV refresh has run yet (first deploy)</li>
 * </ul>
 */
public record FailureBreakdownResponse(
        DateWindowDto dateWindow,
        List<FailureModeDto> failureModes,
        Instant lastRefreshedAt
) {
}
