package com.tenxengage.app.dto.request.redemption;

import java.time.LocalDate;

/**
 * Shared filter parameters for advanced redemption analytics endpoints (FR-08.x).
 *
 * <p>All fields are optional on the wire; defaults are applied in the service layer:
 * <ul>
 *   <li>{@code dateFrom} defaults to today − 30 days (UTC)</li>
 *   <li>{@code dateTo} defaults to today (UTC)</li>
 *   <li>{@code region} — null means "no region filter" (all regions)</li>
 *   <li>{@code role} — null means "no role filter" (all roles); used by segment breakdown (FR-08.2)</li>
 * </ul>
 *
 * <p>Controller populates this from {@code @RequestParam} values before passing to the service.
 */
public record AdvancedAnalyticsFilter(
        LocalDate dateFrom,
        LocalDate dateTo,
        String region,
        String role
) {
    /**
     * Convenience constructor for endpoints that do not accept a {@code role} parameter
     * (e.g. item-breakdown, failure-breakdown). Sets {@code role} to {@code null}.
     */
    public AdvancedAnalyticsFilter(LocalDate dateFrom, LocalDate dateTo, String region) {
        this(dateFrom, dateTo, region, null);
    }
}
