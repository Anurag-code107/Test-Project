> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `tenxengage-backend/src/main/resources/db/migration/`_

---

### V28__create_advanced_analytics_materialized_views.sql

> **Migration numbering:** V10–V27 are already taken on this branch; the next free
> slot is **V28** (permissions seed below is **V29**). Confirm with
> `ls src/main/resources/db/migration/ | sort -V | tail` before applying.
>
> **Schema reconciliation (F2 pre-flight):** the original draft referenced columns
> that do not exist. Corrected against the live schema:
> - `catalog_items` → **`redemption_catalog_items`**; `ci.deleted = false` → **`ci.is_active = true`**
> - `users` has **no `deleted`** column → predicate removed
> - `rr.currency_type` → source column is **`rr.currency_id`** (aliased `AS currency_type` to keep MV/DTO names stable)
> - role: `u.role_name` does not exist → **`client_roles.base_role_name`** via `u.client_role_id`
> - **`partner_tier` dropped entirely** — no per-partner/per-user tier exists; the only tier is
>   `clients.subscription_tier`, constant within a tenant and useless as a segment axis.
> - **`region`** is not a flat column → resolved via the partner company's top-level
>   (`location_levels.depth = 0`) location, matching `ClaimService`. Centralised in the
>   `v_user_region` helper view below so every MV resolves region identically.

```sql
-- ============================================================
-- Advanced Redemption Analytics: Tracking table
-- ============================================================
CREATE TABLE analytics_mv_refresh_log (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    mv_name           VARCHAR(100) NOT NULL,
    last_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    duration_ms       BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_analytics_mv_refresh_log_mv_name UNIQUE (mv_name)
);

-- ============================================================
-- Helper view: resolve each user's region (top-level location).
-- A user's region = the name of their partner company's depth-0 location
-- value (LIMIT 1 when a company spans several). Mirrors the lateral join in
-- ClaimService. Users with no partner company / no location resolve to NULL.
-- ============================================================
CREATE VIEW v_user_region AS
SELECT
    u.id        AS user_id,
    u.client_id,
    region.name AS region
FROM users u
LEFT JOIN LATERAL (
    SELECT lv.name
    FROM   partner_company_locations pcl
    JOIN   location_values lv ON lv.id = pcl.location_value_id
    JOIN   location_levels ll ON ll.id = lv.level_id
    WHERE  pcl.partner_company_id = u.partner_company_id
      AND  ll.depth = 0
    LIMIT  1
) region ON true;

-- ============================================================
-- MV 1: Item-level redemption breakdown (FR-08.1, FR-08.7)
-- ============================================================
CREATE MATERIALIZED VIEW mv_item_redemption_breakdown AS
SELECT
    rr.client_id,
    rr.catalog_item_id,
    ci.name                                                                   AS catalog_item_name,
    rr.currency_id                                                            AS currency_type,
    ur.region,
    rr.processing_mode,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    COUNT(*)                                                                   AS total_redeemed_count,
    COALESCE(SUM(rr.amount), 0)                                               AS total_redeemed_amount,
    CASE
        WHEN COUNT(*) = 0 THEN 0
        ELSE ROUND(
            COUNT(*) FILTER (WHERE rr.status = 'COMPLETED') * 100.0
            / NULLIF(COUNT(*), 0),
            2
        )
    END                                                                        AS redemption_rate,
    COUNT(*) FILTER (WHERE rr.status = 'FAILED')                              AS failed_count,
    COUNT(*) FILTER (WHERE rr.status = 'CANCELLED')                           AS cancelled_count
FROM redemption_requests rr
JOIN redemption_catalog_items ci ON ci.id = rr.catalog_item_id AND ci.is_active = true
JOIN users u                     ON u.id  = rr.user_id          AND u.client_id = rr.client_id
LEFT JOIN v_user_region ur       ON ur.user_id = rr.user_id
WHERE rr.deleted = false
GROUP BY
    rr.client_id, rr.catalog_item_id, ci.name, rr.currency_id,
    ur.region, rr.processing_mode,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE;

-- Unique index required for REFRESH MATERIALIZED VIEW CONCURRENTLY.
-- COALESCE(region,'') keeps the index total even when region is NULL.
CREATE UNIQUE INDEX uq_mv_item_redemption_breakdown
    ON mv_item_redemption_breakdown
    (client_id, catalog_item_id, currency_type, COALESCE(region, ''), processing_mode, period_date);

CREATE INDEX idx_mv_item_redemption_breakdown_client_date
    ON mv_item_redemption_breakdown (client_id, period_date);

-- ============================================================
-- MV 2: Segment breakdown by region × role × currency (FR-08.2)
-- (tier dropped — no backing data; see header note)
-- ============================================================
CREATE MATERIALIZED VIEW mv_segment_redemption_breakdown AS
SELECT
    rr.client_id,
    ur.region,
    cr.base_role_name                                                         AS role,
    rr.currency_id                                                            AS currency_type,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    COUNT(*)                                                                   AS total_redeemed_count,
    COALESCE(SUM(rr.amount), 0)                                               AS total_redeemed_amount,
    CASE
        WHEN COUNT(*) = 0 THEN 0
        ELSE ROUND(
            COUNT(*) FILTER (WHERE rr.status = 'COMPLETED') * 100.0
            / NULLIF(COUNT(*), 0),
            2
        )
    END                                                                        AS redemption_rate
FROM redemption_requests rr
JOIN users u              ON u.id = rr.user_id AND u.client_id = rr.client_id
LEFT JOIN client_roles cr ON cr.id = u.client_role_id
LEFT JOIN v_user_region ur ON ur.user_id = rr.user_id
WHERE rr.deleted = false
GROUP BY
    rr.client_id, ur.region, cr.base_role_name, rr.currency_id,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE;

CREATE UNIQUE INDEX uq_mv_segment_redemption_breakdown
    ON mv_segment_redemption_breakdown
    (client_id, COALESCE(region, ''), COALESCE(role, ''), currency_type, period_date);

CREATE INDEX idx_mv_segment_redemption_breakdown_client_date
    ON mv_segment_redemption_breakdown (client_id, period_date);

-- ============================================================
-- MV 3: Time-to-first-redemption by region (FR-08.3)
-- (regrouped from tier → region — see header note)
-- ============================================================
CREATE MATERIALIZED VIEW mv_time_to_first_redemption AS
SELECT
    u.client_id,
    ur.region,
    DATE_TRUNC('day', first_rr.first_submitted_at AT TIME ZONE 'UTC')::DATE   AS first_redemption_date,
    AVG(
        EXTRACT(EPOCH FROM (first_rr.first_submitted_at - u.created_at)) / 3600.0
    )                                                                          AS avg_hours_to_first_redemption,
    PERCENTILE_CONT(0.5) WITHIN GROUP (
        ORDER BY EXTRACT(EPOCH FROM (first_rr.first_submitted_at - u.created_at)) / 3600.0
    )                                                                          AS median_hours_to_first_redemption,
    SUM(
        EXTRACT(EPOCH FROM (first_rr.first_submitted_at - u.created_at)) / 3600.0
    )                                                                          AS sum_hours_to_first_redemption,
    COUNT(*)                                                                   AS sample_count
FROM users u
JOIN LATERAL (
    SELECT rr.user_id, MIN(rr.submitted_at) AS first_submitted_at
    FROM   redemption_requests rr
    WHERE  rr.user_id   = u.id
      AND  rr.client_id = u.client_id
      AND  rr.status    = 'COMPLETED'
      AND  rr.deleted   = false
    GROUP  BY rr.user_id
) first_rr ON true
LEFT JOIN v_user_region ur ON ur.user_id = u.id
GROUP BY
    u.client_id, ur.region,
    DATE_TRUNC('day', first_rr.first_submitted_at AT TIME ZONE 'UTC')::DATE;

CREATE UNIQUE INDEX uq_mv_time_to_first_redemption
    ON mv_time_to_first_redemption (client_id, COALESCE(region, ''), first_redemption_date);

CREATE INDEX idx_mv_time_to_first_redemption_client
    ON mv_time_to_first_redemption (client_id);

-- ============================================================
-- MV 4: Redemption rate daily trend (FR-08.4)
-- ============================================================
CREATE MATERIALIZED VIEW mv_redemption_rate_trend AS
SELECT
    rr.client_id,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    rr.currency_id                                                            AS currency_type,
    COUNT(*)                                                                   AS redeemed_count,
    CASE
        WHEN COUNT(*) = 0 THEN 0
        ELSE ROUND(
            COUNT(*) FILTER (WHERE rr.status = 'COMPLETED') * 100.0
            / NULLIF(COUNT(*), 0),
            2
        )
    END                                                                        AS redemption_rate
FROM redemption_requests rr
WHERE rr.deleted = false
GROUP BY
    rr.client_id,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE,
    rr.currency_id;

CREATE UNIQUE INDEX uq_mv_redemption_rate_trend
    ON mv_redemption_rate_trend (client_id, period_date, currency_type);

CREATE INDEX idx_mv_redemption_rate_trend_client_date
    ON mv_redemption_rate_trend (client_id, period_date);

-- ============================================================
-- T5: Liability trend snapshot table (FR-08.5)
-- NOTE: This is a regular TABLE, not a MATERIALIZED VIEW.
-- PostgreSQL MVs are read-only; the scheduler appends snapshot rows
-- on each cycle via INSERT ON CONFLICT DO UPDATE, accumulating history.
-- ============================================================
CREATE TABLE mv_liability_trend (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                UUID          NOT NULL,
    period_date              DATE          NOT NULL,
    currency_type            VARCHAR(50)   NOT NULL,
    total_unredeemed_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    captured_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_mv_liability_trend_key UNIQUE (client_id, period_date, currency_type)
);

CREATE INDEX idx_mv_liability_trend_client_date
    ON mv_liability_trend (client_id, period_date);

-- ============================================================
-- MV 6: Failure mode breakdown (FR-08.7)
-- ============================================================
CREATE MATERIALIZED VIEW mv_failure_mode_breakdown AS
SELECT
    rr.client_id,
    rr.processing_mode,
    rr.catalog_item_id,
    ci.name                                                                   AS catalog_item_name,
    rr.currency_id                                                            AS currency_type,
    ur.region,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE               AS period_date,
    COUNT(*) FILTER (WHERE rr.status = 'FAILED')                              AS failed_count,
    COUNT(*) FILTER (WHERE rr.status = 'CANCELLED')                           AS cancelled_count,
    COUNT(*)                                                                   AS total_count,
    CASE
        WHEN COUNT(*) = 0 THEN 0
        ELSE ROUND(
            (COUNT(*) FILTER (WHERE rr.status IN ('FAILED','CANCELLED'))) * 100.0
            / NULLIF(COUNT(*), 0),
            2
        )
    END                                                                        AS failure_rate
FROM redemption_requests rr
JOIN redemption_catalog_items ci ON ci.id = rr.catalog_item_id AND ci.is_active = true
JOIN users u                     ON u.id  = rr.user_id          AND u.client_id = rr.client_id
LEFT JOIN v_user_region ur       ON ur.user_id = rr.user_id
WHERE rr.deleted = false
GROUP BY
    rr.client_id, rr.processing_mode, rr.catalog_item_id, ci.name,
    rr.currency_id, ur.region,
    DATE_TRUNC('day', rr.submitted_at AT TIME ZONE 'UTC')::DATE;

CREATE UNIQUE INDEX uq_mv_failure_mode_breakdown
    ON mv_failure_mode_breakdown
    (client_id, processing_mode, catalog_item_id, currency_type, COALESCE(region, ''), period_date);

CREATE INDEX idx_mv_failure_mode_breakdown_client_date
    ON mv_failure_mode_breakdown (client_id, period_date);
```

---

### V29__seed_advanced_analytics_permissions.sql

```sql
-- ============================================================
-- Advanced Redemption Analytics: Permission catalog
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES (
    gen_random_uuid(),
    'action.redemption.analytics.advanced',
    'View Advanced Analytics',
    'Grants access to dimensional analytics breakdowns, trend charts, and liability CSV export',
    'REDEMPTION_ACTIONS',
    'ACTION',
    210,
    NOW(), NOW(),
    'CLIENT'
)
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- Advanced Redemption Analytics: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (
    gen_random_uuid(),
    'redemption_analytics_advanced',
    'Advanced Redemption Analytics — dimensional breakdowns, trend charts, and liability trend CSV export',
    false, true, true,
    NOW(), NOW(),
    'REDEMPTION'
)
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- Advanced Redemption Analytics: Role grants (CLIENT_ADMIN only)
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.analytics.advanced')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;
```

---

## Package Layout [BE]

_All paths relative to `tenxengage-backend/src/`._

| Responsibility | File path |
|---|---|
| Controller | `main/java/com/tenxengage/app/controller/redemption/RedemptionAdvancedAnalyticsController.java` |
| Service | `main/java/com/tenxengage/app/service/redemption/RedemptionAdvancedAnalyticsService.java` |
| MV Refresh Scheduler | `main/java/com/tenxengage/app/service/redemption/AnalyticsMvRefreshScheduler.java` |
| Response DTO — item entry | `main/java/com/tenxengage/app/dto/response/redemption/ItemRedemptionDto.java` |
| Response DTO — item breakdown | `main/java/com/tenxengage/app/dto/response/redemption/ItemBreakdownResponse.java` |
| Response DTO — segment entry | `main/java/com/tenxengage/app/dto/response/redemption/SegmentRedemptionDto.java` |
| Response DTO — segment breakdown | `main/java/com/tenxengage/app/dto/response/redemption/SegmentBreakdownResponse.java` |
| Response DTO — TTFR region entry | `main/java/com/tenxengage/app/dto/response/redemption/RegionTimeToRedemptionDto.java` |
| Response DTO — TTFR response | `main/java/com/tenxengage/app/dto/response/redemption/TimeToFirstRedemptionResponse.java` |
| Response DTO — trend data point | `main/java/com/tenxengage/app/dto/response/redemption/TrendDataPointDto.java` |
| Response DTO — redemption trend | `main/java/com/tenxengage/app/dto/response/redemption/RedemptionTrendResponse.java` |
| Response DTO — liability data point | `main/java/com/tenxengage/app/dto/response/redemption/LiabilityDataPointDto.java` |
| Response DTO — liability trend | `main/java/com/tenxengage/app/dto/response/redemption/LiabilityTrendResponse.java` |
| Response DTO — failure mode entry | `main/java/com/tenxengage/app/dto/response/redemption/FailureModeDto.java` |
| Response DTO — failure breakdown | `main/java/com/tenxengage/app/dto/response/redemption/FailureBreakdownResponse.java` |
| Response DTO — refresh status | `main/java/com/tenxengage/app/dto/response/redemption/AnalyticsRefreshStatusResponse.java` |
| Enum update (add value) | `main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` — add `REDEMPTION_ADVANCED_ANALYTICS_EXPORT` |
| Flyway migration (MV DDL) | `main/resources/db/migration/V28__create_advanced_analytics_materialized_views.sql` |
| Flyway migration (permissions) | `main/resources/db/migration/V29__seed_advanced_analytics_permissions.sql` |
| Service test | `test/java/com/tenxengage/app/service/redemption/RedemptionAdvancedAnalyticsServiceTest.java` |
| Controller test | `test/java/com/tenxengage/app/controller/redemption/RedemptionAdvancedAnalyticsControllerTest.java` |
| Scheduler test | `test/java/com/tenxengage/app/service/redemption/AnalyticsMvRefreshSchedulerTest.java` |
| Test fixtures | `test/java/com/tenxengage/app/testdata/AdvancedAnalyticsFixtures.java` |

---

## Repository Queries [BE]

No new `JpaRepository` interfaces — F-08 has no JPA entities. All MV queries run via `NamedParameterJdbcTemplate` injected directly into `RedemptionAdvancedAnalyticsService`. Every query binds `clientId` as a named parameter.

### Item breakdown (`mv_item_redemption_breakdown`)

```sql
-- Base query (date filter mandatory; region filter optional, appended when non-null)
SELECT catalog_item_id, catalog_item_name, currency_type,
       SUM(total_redeemed_count)   AS total_redeemed_count,
       SUM(total_redeemed_amount)  AS total_redeemed_amount,
       SUM(failed_count)           AS failed_count,
       SUM(cancelled_count)        AS cancelled_count,
       CASE WHEN SUM(total_redeemed_count) = 0 THEN 0
            ELSE ROUND(SUM(total_redeemed_count - failed_count - cancelled_count) * 100.0
                       / NULLIF(SUM(total_redeemed_count), 0), 2)
       END                         AS redemption_rate
FROM mv_item_redemption_breakdown
WHERE client_id  = :clientId
  AND period_date BETWEEN :dateFrom AND :dateTo
  -- AND region IN (:regions)  -- appended when region filter present
GROUP BY catalog_item_id, catalog_item_name, currency_type
ORDER BY total_redeemed_count DESC;
```

### Segment breakdown (`mv_segment_redemption_breakdown`)

```sql
SELECT region, role, currency_type,
       SUM(total_redeemed_count)  AS total_redeemed_count,
       SUM(total_redeemed_amount) AS total_redeemed_amount,
       CASE WHEN SUM(total_redeemed_count) = 0 THEN 0
            ELSE ROUND(SUM(total_redeemed_count) * 100.0
                       / NULLIF(SUM(total_redeemed_count), 0), 2)
       END                        AS redemption_rate
FROM mv_segment_redemption_breakdown
WHERE client_id  = :clientId
  AND period_date BETWEEN :dateFrom AND :dateTo
  -- AND region IN (:regions)  -- appended when region filter present
  -- AND role   IN (:roles)    -- appended when role filter present
GROUP BY region, role, currency_type
ORDER BY total_redeemed_count DESC;
```

### Time-to-first-redemption (`mv_time_to_first_redemption`)

```sql
-- Aggregates daily cohort rows into a single per-region summary for the date window
SELECT region,
       CASE WHEN SUM(sample_count) = 0 THEN NULL
            ELSE SUM(sum_hours_to_first_redemption) / NULLIF(SUM(sample_count), 0)
       END  AS avg_hours_to_first_redemption,
       NULL AS median_hours_to_first_redemption,   -- median re-computed at query time if needed
       SUM(sample_count) AS sample_count
FROM mv_time_to_first_redemption
WHERE client_id          = :clientId
  AND first_redemption_date BETWEEN :dateFrom AND :dateTo
  -- AND region IN (:regions)  -- appended when region filter present
GROUP BY region
ORDER BY region;
```

### Redemption rate trend (`mv_redemption_rate_trend`)

```sql
SELECT period_date, currency_type, redeemed_count, redemption_rate
FROM mv_redemption_rate_trend
WHERE client_id  = :clientId
  AND period_date BETWEEN :dateFrom AND :dateTo
ORDER BY period_date ASC, currency_type ASC;
```

### Liability trend (`mv_liability_trend`)

```sql
SELECT period_date, currency_type, total_unredeemed_balance
FROM mv_liability_trend
WHERE client_id  = :clientId
  AND period_date BETWEEN :dateFrom AND :dateTo
ORDER BY period_date ASC, currency_type ASC;
```

### Failure breakdown (`mv_failure_mode_breakdown`)

```sql
SELECT processing_mode, catalog_item_id, catalog_item_name, currency_type,
       SUM(failed_count)    AS failed_count,
       SUM(cancelled_count) AS cancelled_count,
       SUM(total_count)     AS total_count,
       CASE WHEN SUM(total_count) = 0 THEN 0
            ELSE ROUND((SUM(failed_count) + SUM(cancelled_count)) * 100.0
                       / NULLIF(SUM(total_count), 0), 2)
       END                  AS failure_rate
FROM mv_failure_mode_breakdown
WHERE client_id  = :clientId
  AND period_date BETWEEN :dateFrom AND :dateTo
  -- AND region IN (:regions)  -- appended when region filter present
GROUP BY processing_mode, catalog_item_id, catalog_item_name, currency_type
ORDER BY failure_rate DESC;
```

### Refresh status (`analytics_mv_refresh_log`)

```sql
SELECT mv_name, last_refreshed_at, duration_ms
FROM analytics_mv_refresh_log
ORDER BY last_refreshed_at ASC;
-- Service takes the MIN(last_refreshed_at) across all rows as the effective "last refreshed" timestamp.
-- If no rows exist, service returns lastRefreshedAt = null and isStale = true.
```

### MV refresh (called by `AnalyticsMvRefreshScheduler`)

```sql
-- Per MV (except mv_liability_trend — see note in V10 migration):
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_item_redemption_breakdown;
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_segment_redemption_breakdown;
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_time_to_first_redemption;
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_redemption_rate_trend;
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_failure_mode_breakdown;

-- mv_liability_trend — append new period_date snapshot instead of full refresh.
-- NOTE (F5): reward_wallets uses `currency_id` (not currency_type); verify the balance
-- column names (available_balance / reserved_balance) and whether a `deleted` column
-- exists on reward_wallets before wiring the scheduler — same schema-reconciliation rule
-- as the V28 header. The insert target column stays `currency_type`.
INSERT INTO mv_liability_trend (client_id, period_date, currency_type, total_unredeemed_balance)
SELECT client_id,
       DATE_TRUNC('day', NOW() AT TIME ZONE 'UTC')::DATE,
       currency_id,
       SUM(available_balance + reserved_balance)
FROM reward_wallets
GROUP BY client_id, currency_id
ON CONFLICT (client_id, period_date, currency_type) DO UPDATE
  SET total_unredeemed_balance = EXCLUDED.total_unredeemed_balance;

-- Upsert refresh log after each successful MV:
INSERT INTO analytics_mv_refresh_log (id, mv_name, last_refreshed_at, duration_ms)
VALUES (gen_random_uuid(), :mvName, NOW(), :durationMs)
ON CONFLICT (mv_name) DO UPDATE
  SET last_refreshed_at = EXCLUDED.last_refreshed_at,
      duration_ms       = EXCLUDED.duration_ms;
```

---

## Package Layout [FE]

_All paths relative to `tenxengage-frontend/src/`._

| Responsibility | File path |
|---|---|
| TypeScript types | `types/redemption-analytics-advanced.types.ts` |
| API service | `services/redemption-analytics-advanced.service.ts` |
| Hook — item breakdown | `hooks/useItemBreakdown.ts` |
| Hook — segment breakdown | `hooks/useSegmentBreakdown.ts` |
| Hook — time to first redemption | `hooks/useTimeToFirstRedemption.ts` |
| Hook — redemption trend | `hooks/useRedemptionTrend.ts` |
| Hook — liability trend | `hooks/useLiabilityTrend.ts` |
| Hook — failure breakdown | `hooks/useFailureBreakdown.ts` |
| Hook — refresh status | `hooks/useRefreshStatus.ts` |
| Tab container | `components/analytics/advanced/AdvancedAnalyticsTab.tsx` |
| Filter bar | `components/analytics/advanced/AdvancedFilterBar.tsx` |
| Staleness banner | `components/analytics/advanced/StalenessBanner.tsx` |
| Item breakdown table | `components/analytics/advanced/ItemBreakdownTable.tsx` |
| Segment breakdown table | `components/analytics/advanced/SegmentBreakdownTable.tsx` |
| TTFR table | `components/analytics/advanced/TimeToFirstRedemptionTable.tsx` |
| Redemption trend chart | `components/analytics/advanced/RedemptionTrendChart.tsx` |
| Liability trend chart | `components/analytics/advanced/LiabilityTrendChart.tsx` |
| Failure breakdown table | `components/analytics/advanced/FailureBreakdownTable.tsx` |
| Component tests | `components/analytics/advanced/__tests__/AdvancedAnalyticsTab.test.tsx` |
| Component tests | `components/analytics/advanced/__tests__/StalenessBanner.test.tsx` |
| Component tests | `components/analytics/advanced/__tests__/ItemBreakdownTable.test.tsx` |
| Component tests | `components/analytics/advanced/__tests__/LiabilityTrendChart.test.tsx` |

**No new route** — Advanced Analytics is a tab within the existing analytics page. The `AdvancedAnalyticsTab` component is imported into the existing analytics page component and rendered conditionally when the user holds the required permission and the feature flag is enabled.

---

## Hook Specs [FE]

### `useItemBreakdown(filters: AdvancedAnalyticsFilters)`

```ts
queryKey:  ['advanced-analytics', 'item-breakdown', filters]
staleTime: 60_000
enabled:   !!filters.dateFrom && !!filters.dateTo
```

No mutations — read-only.

---

### `useSegmentBreakdown(filters: AdvancedAnalyticsFilters)`

```ts
queryKey:  ['advanced-analytics', 'segment-breakdown', filters]
staleTime: 60_000
```

---

### `useTimeToFirstRedemption(filters: AdvancedAnalyticsFilters)`

```ts
queryKey:  ['advanced-analytics', 'ttfr', filters]
staleTime: 60_000
```

---

### `useRedemptionTrend(dateFrom: string, dateTo: string)`

```ts
queryKey:  ['advanced-analytics', 'trend', dateFrom, dateTo]
staleTime: 60_000
```

---

### `useLiabilityTrend(dateFrom: string, dateTo: string)`

```ts
queryKey:  ['advanced-analytics', 'liability-trend', dateFrom, dateTo]
staleTime: 60_000
```

---

### `useFailureBreakdown(filters: AdvancedAnalyticsFilters)`

```ts
queryKey:  ['advanced-analytics', 'failure-breakdown', filters]
staleTime: 60_000
```

---

### `useRefreshStatus()`

```ts
queryKey:     ['advanced-analytics', 'refresh-status']
staleTime:    0          // always re-fetch from server
refetchInterval: 300_000 // poll every 5 minutes
```

---

### `AdvancedAnalyticsFilters` type

```ts
interface AdvancedAnalyticsFilters {
  dateFrom: string;   // ISO 8601 YYYY-MM-DD
  dateTo:   string;
  region?:  string;   // comma-separated, omit if empty
  role?:    string;   // comma-separated, omit if empty
}
```

---

## Audit Annotations [BE]

Non-CRUD operations requiring explicit `auditLogService.logAsync()` call in the service:

| Operation | `action` value | `resourceType` value | When called | Metadata |
|---|---|---|---|---|
| Liability trend CSV export | `DATA_EXPORTED` | `REDEMPTION_ADVANCED_ANALYTICS_EXPORT` | After CSV bytes built (before return) | `{tenantId, userId, dateFrom, dateTo, rowCount}` |

### New enum values to add

**`AuditResourceType.java`** — add:
```java
REDEMPTION_ADVANCED_ANALYTICS_EXPORT
```

No new `AuditAction` values needed — `DATA_EXPORTED` already exists.
