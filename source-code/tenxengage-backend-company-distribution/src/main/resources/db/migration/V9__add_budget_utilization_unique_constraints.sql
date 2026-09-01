-- Deduplicate global budget_utilization rows (location_value_id IS NULL).
-- Keeps the MIN(id::text)::uuid row, merges utilized amounts onto it, deletes extras.
-- Required before CREATE UNIQUE INDEX can succeed on existing data.
UPDATE budget_utilizations bu
SET    utilized   = agg.total_utilized,
       updated_at = now()
FROM (
    SELECT incentive_id, currency_id, MIN(id::text)::uuid AS keeper_id, SUM(utilized) AS total_utilized
    FROM   budget_utilizations
    WHERE  location_value_id IS NULL
    GROUP  BY incentive_id, currency_id
    HAVING COUNT(*) > 1
) agg
WHERE bu.id = agg.keeper_id;

DELETE FROM budget_utilizations bu
USING (
    SELECT incentive_id, currency_id, MIN(id::text)::uuid AS keeper_id
    FROM   budget_utilizations
    WHERE  location_value_id IS NULL
    GROUP  BY incentive_id, currency_id
    HAVING COUNT(*) > 1
) agg
WHERE  bu.incentive_id      = agg.incentive_id
  AND  bu.currency_id       = agg.currency_id
  AND  bu.location_value_id IS NULL
  AND  bu.id               <> agg.keeper_id;

-- Deduplicate location-scoped budget_utilization rows (location_value_id IS NOT NULL).
UPDATE budget_utilizations bu
SET    utilized   = agg.total_utilized,
       updated_at = now()
FROM (
    SELECT incentive_id, currency_id, location_value_id, MIN(id::text)::uuid AS keeper_id, SUM(utilized) AS total_utilized
    FROM   budget_utilizations
    WHERE  location_value_id IS NOT NULL
    GROUP  BY incentive_id, currency_id, location_value_id
    HAVING COUNT(*) > 1
) agg
WHERE bu.id = agg.keeper_id;

DELETE FROM budget_utilizations bu
USING (
    SELECT incentive_id, currency_id, location_value_id, MIN(id::text)::uuid AS keeper_id
    FROM   budget_utilizations
    WHERE  location_value_id IS NOT NULL
    GROUP  BY incentive_id, currency_id, location_value_id
    HAVING COUNT(*) > 1
) agg
WHERE  bu.incentive_id      = agg.incentive_id
  AND  bu.currency_id       = agg.currency_id
  AND  bu.location_value_id = agg.location_value_id
  AND  bu.id               <> agg.keeper_id;

-- Enforce one BudgetUtilization row per (incentive, currency) for global budgets
CREATE UNIQUE INDEX uq_budget_util_global
    ON budget_utilizations (incentive_id, currency_id)
    WHERE location_value_id IS NULL;

-- Enforce one BudgetUtilization row per (incentive, currency, location) for location-scoped budgets
CREATE UNIQUE INDEX uq_budget_util_location
    ON budget_utilizations (incentive_id, currency_id, location_value_id)
    WHERE location_value_id IS NOT NULL;
