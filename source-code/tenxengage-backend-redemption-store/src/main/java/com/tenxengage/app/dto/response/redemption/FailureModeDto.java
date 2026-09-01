package com.tenxengage.app.dto.response.redemption;

/**
 * Per-row failure mode metrics for the failure breakdown endpoint (FR-08.7).
 *
 * <p>Field names match the {@code redemption-advanced-analytics.yaml} contract exactly:
 * <ul>
 *   <li>{@code processingMode} — INSTANT | BATCH | APPROVAL_REQUIRED
 *       (values from {@code RedemptionProcessingMode} enum, F-03)</li>
 *   <li>{@code catalogItemId} — UUID as string (contract: {@code format: uuid})</li>
 *   <li>{@code currencyId} — platform currency identifier (not {@code currencyType})</li>
 *   <li>{@code failureRate} — percentage 0–100 (double); (failed + cancelled) / total</li>
 * </ul>
 */
public record FailureModeDto(
        String processingMode,
        String catalogItemId,
        String catalogItemName,
        String currencyId,
        long failedCount,
        long cancelledCount,
        long totalCount,
        double failureRate
) {
}
