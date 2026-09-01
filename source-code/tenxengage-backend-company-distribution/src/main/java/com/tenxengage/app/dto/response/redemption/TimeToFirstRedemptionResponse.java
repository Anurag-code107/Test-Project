package com.tenxengage.app.dto.response.redemption;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Top-level response for GET /api/v1/redemption/analytics/advanced/time-to-first-redemption
 * (FR-08.3).
 *
 * <p>Segmented by region — originally specified per partner tier, regrouped to region because
 * no per-partner tier exists in the data model (see spec FR-08.3).
 *
 * <p>{@code lastRefreshedAt} is nullable (null when no MV refresh has occurred yet on first
 * deploy).  {@code @JsonInclude(ALWAYS)} overrides the global {@code NON_NULL} policy so that
 * the nullable {@code lastRefreshedAt} is always serialized as a JSON key (null value, not
 * absent field) — typed clients rely on stable field presence.
 *
 * <p>{@code filters} echoes the active region filter values applied to the query (typed
 * {@code String → String}).  An empty map is returned when no filters are applied.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TimeToFirstRedemptionResponse(
        Map<String, String> filters,
        List<RegionTimeToRedemptionDto> regions,
        @Nullable Instant lastRefreshedAt
) {
}
