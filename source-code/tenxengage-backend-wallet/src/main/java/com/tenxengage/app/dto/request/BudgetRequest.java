package com.tenxengage.app.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Per-currency budget entry. When {@code budgetMode == "PER_LOCATION"},
 * {@code locationAllocations} should carry the per-LocationValue amounts
 * (any depth in the location hierarchy). The client is responsible for
 * the children-sum-to-parent invariant and auto-fills blank residuals
 * before sending.
 */
public record BudgetRequest(
    @NotNull String totalBudget,
    @NotBlank @Size(max = 50) String currencyId,
    @NotBlank String allocationMethod,
    String budgetMode,
    UUID budgetLocationLevelId,
    @Valid List<LocationAllocationRequest> locationAllocations
) {
}
