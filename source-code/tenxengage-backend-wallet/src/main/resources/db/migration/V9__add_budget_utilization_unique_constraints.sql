-- Enforce one BudgetUtilization row per (incentive, currency) for global budgets
CREATE UNIQUE INDEX uq_budget_util_global
    ON budget_utilizations (incentive_id, currency_id)
    WHERE location_value_id IS NULL;

-- Enforce one BudgetUtilization row per (incentive, currency, location) for location-scoped budgets
CREATE UNIQUE INDEX uq_budget_util_location
    ON budget_utilizations (incentive_id, currency_id, location_value_id)
    WHERE location_value_id IS NOT NULL;
