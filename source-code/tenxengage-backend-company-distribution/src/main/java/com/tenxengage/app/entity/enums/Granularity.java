package com.tenxengage.app.entity.enums;

/**
 * Granularity for period-bucketing in the balance expiration breakage report (FR-09.6).
 *
 * <ul>
 *   <li>{@code MONTH} — one row per calendar month per currency type</li>
 *   <li>{@code QUARTER} — one row per calendar quarter per currency type</li>
 * </ul>
 *
 * Maps to the {@code granularity} query parameter on
 * {@code GET /api/v1/redemption/expiration/breakage} and
 * {@code GET /api/v1/redemption/expiration/breakage/export}.
 */
public enum Granularity {
    MONTH,
    QUARTER;

    /**
     * Returns the PostgreSQL {@code date_trunc} bucket name for this granularity.
     * Used in the native {@code aggregateExpiryBreakage} query.
     */
    public String toDateTruncBucket() {
        return name().toLowerCase();
    }
}
