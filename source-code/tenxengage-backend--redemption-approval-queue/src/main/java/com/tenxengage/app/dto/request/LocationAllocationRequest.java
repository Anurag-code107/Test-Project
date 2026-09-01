package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * One per-LocationValue budget allocation under a {@link BudgetRequest}.
 * <p>
 * The {@code locationValueId} can reference any depth in the location
 * hierarchy (Region, Country, State, City, ...). The schema's only
 * structural guardrail is the {@code (budget_id, location_value_id)}
 * unique constraint on {@code location_budget_allocations}.
 */
public record LocationAllocationRequest(
    @NotNull UUID locationValueId,
    @NotBlank String amount
) {
}
