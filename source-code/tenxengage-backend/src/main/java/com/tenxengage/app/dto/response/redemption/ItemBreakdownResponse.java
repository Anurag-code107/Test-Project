package com.tenxengage.app.dto.response.redemption;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Top-level response for GET /api/v1/redemption/analytics/advanced/item-breakdown (FR-08.1).
 *
 * <p>Items are sorted by {@code totalRedeemedCount} descending by the service layer;
 * the FE must not re-sort them. {@code lastRefreshedAt} is sourced from the most recent
 * successful refresh in {@code analytics_mv_refresh_log}.
 *
 * <p>{@code lastRefreshedAt} is intentionally nullable (null when no MV refresh has
 * occurred yet on first deploy).  The class-level {@code @JsonInclude(ALWAYS)} overrides
 * the global {@code NON_NULL} policy so that all fields — including the nullable
 * {@code lastRefreshedAt} — are always present in the JSON response. Typed clients
 * rely on stable field presence; a missing key causes a deserialization failure on
 * strictly-typed clients.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ItemBreakdownResponse(
        DateWindowDto dateWindow,
        List<ItemRedemptionDto> items,
        Instant lastRefreshedAt
) {
}
