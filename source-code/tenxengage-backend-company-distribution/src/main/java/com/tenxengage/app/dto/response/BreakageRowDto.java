package com.tenxengage.app.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One (period × currency type) breakage row in {@link BalanceBreakageReportResponse}.
 *
 * <p>Aggregate-only — no per-user identity or PII. {@code totalExpiredAmount} is typed
 * {@code BigDecimal} but serialised as a JSON {@code string} per the contract
 * ({@code type: string}) via {@link ToStringSerializer} (same pattern as
 * {@link ExpiringBalancePreviewResponse#totalAmountAtRisk()}).</p>
 */
public record BreakageRowDto(
        LocalDate periodStart,
        LocalDate periodEnd,
        String currencyId,
        String currencyDisplayName,
        long expiredCount,
        // Contract mandates type:string — ToStringSerializer ensures JSON string output, not number
        @JsonSerialize(using = ToStringSerializer.class)
        BigDecimal totalExpiredAmount
) {
}
