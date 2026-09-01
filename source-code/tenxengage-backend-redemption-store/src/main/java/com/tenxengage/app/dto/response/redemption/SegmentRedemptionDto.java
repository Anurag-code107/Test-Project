package com.tenxengage.app.dto.response.redemption;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Per-segment redemption metrics for the segment breakdown endpoint (FR-08.2).
 *
 * <p>Each row represents one unique (region × role × currency) combination.
 * Both {@code region} and {@code role} are nullable: {@code null} when a partner
 * has no top-level location ({@code region}) or the user has no client role ({@code role}).
 * The FE renders "—" for null cells (AC-4).
 *
 * <p>Tier was dropped per FR-08.2: no per-partner tier exists in the data model
 * (the only tier is {@code clients.subscription_tier}, constant within a tenant).
 *
 * <ul>
 *   <li>{@code currencyId} — platform currency identifier (e.g. "CASH", "POINTS")</li>
 *   <li>{@code totalRedeemedCount} — maps to {@code total_redeemed_count} in the MV</li>
 *   <li>{@code redemptionRate} — decimal 0.0–1.0 (AC-3); MV column stores 0–100 percentage
 *       which is divided by 100 in the row-mapper</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SegmentRedemptionDto(
        String region,
        String role,
        String currencyId,
        long totalRedeemedCount,
        BigDecimal redemptionRate
) {
}
