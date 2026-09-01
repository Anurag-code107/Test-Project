package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * Request body for the forecast-preview endpoint.
 * Accepts incentive configuration from the builder state
 * without requiring a saved incentive in the database.
 *
 * `regions` is the depth-0 (Region) name list and is kept for backward
 * compatibility. `locationSelections` carries the full level-keyed eligibility
 * scope (Region → Country → State → ...) — when present it takes precedence and
 * the resolver narrows the forecast population to its ancestors. Keys are
 * LocationLevel UUIDs (strings); values are LocationValue names at that level.
 */
public record ForecastPreviewRequest(
    @NotBlank String incentiveType,
    @NotEmpty List<String> regions,
    Map<String, List<String>> locationSelections,
    String startDate,
    String endDate,
    String totalBudget,
    String budgetCurrency,
    String budgetMode,
    List<String> selectedCurrencies,
    List<String> partnerTypes,
    String maxPerPartner,
    String maxPerUser,
    List<String> productSkus,
    String payoutType,
    String payoutAgainst,
    List<PayoutBandPreview> payoutBands,
    String maxPerDeal,
    Map<String, Map<String, String>> regionBudgets
) {
    public record PayoutBandPreview(
        String minAmount,
        String maxAmount,
        String payoutValue
    ) {}
}
