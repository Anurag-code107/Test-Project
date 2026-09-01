package com.tenxengage.app.dto.response.redemption;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

/**
 * Per-region time-to-first-redemption summary for the TTFR endpoint (FR-08.3).
 *
 * <p>Each row represents one region (the top-level location of the partner company).
 * {@code region} is nullable — a partner with no location resolves to {@code null};
 * the FE renders "—" for null region cells (AC-4).
 *
 * <p>{@code avgHoursToFirstRedemption} and {@code medianHoursToFirstRedemption} are
 * nullable: both are {@code null} when {@code sampleCount = 0} (no completed redemptions
 * for that region in the cohort window).  The FE renders "N/A" for null avg/median
 * cells (AC-2).
 *
 * <p>{@code @JsonInclude(ALWAYS)} overrides the global {@code NON_NULL} policy so that
 * null avg/median fields are serialized as JSON {@code null} rather than being omitted
 * entirely — typed clients must see stable field presence.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RegionTimeToRedemptionDto(
        @Nullable String region,
        @Nullable BigDecimal avgHoursToFirstRedemption,
        @Nullable BigDecimal medianHoursToFirstRedemption,
        long sampleCount
) {
}
