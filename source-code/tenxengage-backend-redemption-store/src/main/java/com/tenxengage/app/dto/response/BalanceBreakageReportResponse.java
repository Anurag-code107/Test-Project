package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.Granularity;

import java.time.LocalDate;
import java.util.List;

/**
 * Breakage (expired value) report aggregated from {@code EXPIRY} ledger entries,
 * bucketed by the requested granularity and broken down by currency type (FR-09.6).
 *
 * <p>Aggregate-only — no per-user identity or PII. One {@link BreakageRowDto} per
 * (period × currency type) combination with at least one expiry event.
 *
 * <p>Static factory: {@code from(from, to, granularity, rows)}.
 */
public record BalanceBreakageReportResponse(
        LocalDate from,
        LocalDate to,
        Granularity granularity,
        List<BreakageRowDto> rows
) {

    /**
     * Constructs a report response from its constituent parts.
     *
     * @param from        inclusive start of the reporting window
     * @param to          inclusive end of the reporting window
     * @param granularity period bucketing applied
     * @param rows        per-(period × currency) breakage rows; may be empty (no expiries)
     * @return fully constructed response
     */
    public static BalanceBreakageReportResponse from(
            LocalDate from,
            LocalDate to,
            Granularity granularity,
            List<BreakageRowDto> rows) {
        return new BalanceBreakageReportResponse(from, to, granularity, rows);
    }
}
