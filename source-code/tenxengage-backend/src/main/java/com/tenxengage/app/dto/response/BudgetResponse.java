package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.LocationBudgetAllocation;
import com.tenxengage.app.entity.LocationLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BudgetResponse(
    String totalBudget,
    String currencyId,
    String allocationMethod,
    String budgetMode,
    UUID budgetLocationLevelId,
    List<LocationAllocationResponse> locationAllocations
) {

    public static BudgetResponse from(IncentiveBudget budget) {
        if (budget == null) return null;
        List<LocationAllocationResponse> allocations = new ArrayList<>();
        if (budget.getLocationAllocations() != null) {
            for (LocationBudgetAllocation alloc : budget.getLocationAllocations()) {
                LocationAllocationResponse mapped = LocationAllocationResponse.from(alloc);
                if (mapped != null) allocations.add(mapped);
            }
        }
        LocationLevel level = budget.getBudgetLocationLevel();
        return new BudgetResponse(
            budget.getTotalBudget().toPlainString(),
            budget.getCurrencyId(),
            budget.getAllocationMethod().name(),
            budget.getBudgetMode().name(),
            level != null ? level.getId() : null,
            allocations
        );
    }
}
