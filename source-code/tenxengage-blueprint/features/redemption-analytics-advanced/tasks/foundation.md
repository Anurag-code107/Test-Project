# Foundation Tasks: redemption-analytics-advanced

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts redemption-analytics-advanced
> ```
> This enables FE story sessions to start immediately in parallel with BE foundation work.

---

## Task Summary

| # | Task | Layer | Deps | Parallel With | Size | Done When |
|---|---|---|---|---|---|---|
| F1 | Enums | BE | None | — | S | `AuditResourceType` includes `REDEMPTION_ADVANCED_ANALYTICS_EXPORT`; `./gradlew compileJava` clean |
| F2 | Flyway V28 — MV DDL + snapshot + tracking tables | BE | F1 | — | M | `./gradlew flywayMigrate` applies cleanly; 5 MVs, `v_user_region` view, `mv_liability_trend` table, and `analytics_mv_refresh_log` table exist with correct columns and indexes |
| F3 | Test fixtures | BE | F2 | — | M | `./gradlew compileTestJava` passes; `AdvancedAnalyticsFixtures` helper methods insert rows into all 6 data tables |
| F4 | Flyway V29 — permissions + feature flags seed | BE | F2 | — | S | Seed migration applies; `action.redemption.analytics.advanced` row visible in DB; `redemption_analytics_advanced` flag: starter=false, professional=true, enterprise=true |
| F5 | MV refresh scheduler | BE | F3, F4 | — | M | Scheduler compiles; unit test verifies all 5 MVs refreshed CONCURRENTLY; `mv_liability_trend` INSERT ON CONFLICT issued; `analytics_mv_refresh_log` upserted per MV |

---

## Task F1: Enums [BE] — Size: S

_Dependencies: None_
_Parallel with: None_
_Done when: `./gradlew compileJava` passes — `REDEMPTION_ADVANCED_ANALYTICS_EXPORT` resolves in `AuditResourceType`_

**Files:**
- `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` — add `REDEMPTION_ADVANCED_ANALYTICS_EXPORT`

No new domain enums. No new `AuditAction` values (reuses `DATA_EXPORTED`). Just the new resource type value.

Refer to `spec.md → ## New Enums [BE]`.

---

## Task F2: Flyway V28 — MV DDL + Snapshot + Tracking Tables [BE] — Size: M

_Dependencies: F1_
_Parallel with: None_
_Done when: `./gradlew flywayMigrate` applies cleanly; verify via DB inspection that all tables and indexes exist_

> **Authoritative SQL is `technical.md → V28__...`.** It was reconciled against the live schema
> (V28 numbering, `redemption_catalog_items`/`is_active`, `currency_id`, role via `client_roles`,
> partner_tier dropped, region via the `v_user_region` helper view). The field lists below are a
> summary — copy the exact DDL from technical.md.

**Files:**
- `src/main/resources/db/migration/V28__create_advanced_analytics_materialized_views.sql`

This single migration creates (plus a `v_user_region` helper VIEW resolving each user's
top-level partner location as `region`):

1. **`analytics_mv_refresh_log`** (tracking table — regular TABLE)
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `mv_name VARCHAR(100) NOT NULL`
   - `last_refreshed_at TIMESTAMPTZ`
   - `last_refresh_duration_ms BIGINT`
   - `refresh_status VARCHAR(20) NOT NULL DEFAULT 'pending'`
   - `CONSTRAINT uq_analytics_mv_refresh_log_mv_name UNIQUE (mv_name)`

2. **`mv_item_redemption_breakdown`** (MATERIALIZED VIEW)
   - Fields: `client_id`, `catalog_item_id`, `catalog_item_name`, `currency_type`, `total_redeemed_count`, `total_redeemed_amount`, `redemption_rate`, `period_date_from`, `period_date_to`
   - UNIQUE index on `(client_id, catalog_item_id, currency_type)` for `REFRESH CONCURRENTLY`

3. **`mv_segment_redemption_breakdown`** (MATERIALIZED VIEW)
   - Fields: `client_id`, `region`, `role`, `currency_type`, `period_date`, `total_redeemed_count`, `total_redeemed_amount`, `redemption_rate`
   - UNIQUE index on `(client_id, COALESCE(region,''), COALESCE(role,''), currency_type, period_date)`

4. **`mv_time_to_first_redemption`** (MATERIALIZED VIEW)
   - Fields: `client_id`, `region`, `first_redemption_date`, `avg_hours_to_first_redemption`, `median_hours_to_first_redemption`, `sum_hours_to_first_redemption`, `sample_count`
   - UNIQUE index on `(client_id, COALESCE(region,''), first_redemption_date)`

5. **`mv_redemption_rate_trend`** (MATERIALIZED VIEW)
   - Fields: `client_id`, `period_date`, `currency_type`, `redeemed_count`, `total_issued`, `redemption_rate`
   - UNIQUE index on `(client_id, period_date, currency_type)`

6. **`mv_liability_trend`** (regular TABLE — not a MV; scheduler appends daily snapshots via INSERT ON CONFLICT)
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `client_id UUID NOT NULL`
   - `period_date DATE NOT NULL`
   - `currency_type VARCHAR(50) NOT NULL`
   - `total_unredeemed_balance NUMERIC(19,4) NOT NULL DEFAULT 0`
   - `captured_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
   - `CONSTRAINT uq_mv_liability_trend_key UNIQUE (client_id, period_date, currency_type)`
   - Index on `(client_id, period_date)`

7. **`mv_failure_mode_breakdown`** (MATERIALIZED VIEW)
   - Fields: `client_id`, `processing_mode`, `catalog_item_id`, `catalog_item_name`, `currency_type`, `region`, `period_date`, `failed_count`, `cancelled_count`, `total_count`, `failure_rate`
   - UNIQUE index on `(client_id, processing_mode, catalog_item_id, currency_type, COALESCE(region,''), period_date)`

Refer to `technical.md → ## Flyway Migrations [BE]` for the full SQL DDL.

> **Critical note:** `mv_liability_trend` is a **regular TABLE**, not a MATERIALIZED VIEW. PostgreSQL MVs are read-only. The scheduler accumulates daily balance snapshots into this table via `INSERT ... ON CONFLICT DO UPDATE`. Do not use `CREATE MATERIALIZED VIEW` for this table.

---

## Task F3: Test Fixtures [BE] — Size: M

_Dependencies: F2_
_Parallel with: None_
_Done when: `./gradlew compileTestJava` passes; fixture helper methods insert rows into all 6 data tables; a basic smoke test verifying each helper runs without error passes_

**Files:**
- `src/test/java/com/tenxengage/app/testdata/AdvancedAnalyticsFixtures.java`

No new JPA entities in this feature — all data is served from materialized views and the `mv_liability_trend` regular table. Fixtures insert rows **directly** into these tables via `JdbcTemplate` (not via JPA repositories or service calls).

**Required fixture helpers (each returns the inserted row's primary key or the full inserted row):**

| Method | Target Table | Notes |
|---|---|---|
| `insertItemBreakdownRow(clientId, catalogItemId, ...)` | `mv_item_redemption_breakdown` | All fields parameterized |
| `insertSegmentBreakdownRow(clientId, region, role, ...)` | `mv_segment_redemption_breakdown` | region/role nullable |
| `insertTimeToFirstRedemptionRow(clientId, region, avgHours, ...)` | `mv_time_to_first_redemption` | Allow null avgHours for sampleCount=0 case; region nullable |
| `insertRedemptionTrendRow(clientId, periodDate, currencyType, ...)` | `mv_redemption_rate_trend` | |
| `insertLiabilityTrendRow(clientId, periodDate, currencyType, balance)` | `mv_liability_trend` | Uses ON CONFLICT DO UPDATE |
| `insertFailureBreakdownRow(clientId, processingMode, ...)` | `mv_failure_mode_breakdown` | `processingMode` = MANUAL or AUTOMATED |
| `upsertRefreshLog(mvName, lastRefreshedAt, durationMs, status)` | `analytics_mv_refresh_log` | |

Follow the `UserFixtures.java` builder-return pattern. Wrap `JdbcTemplate` injection via constructor. Annotate `@Component` so Spring test context can inject it.

---

## Task F4: Flyway V29 — Permissions + Feature Flags Seed [BE] — Size: S

_Dependencies: F2_
_Parallel with: None_
_Done when: `./gradlew flywayMigrate` applies V29 cleanly; all 3 rows visible in DB_

**Files:**
- `src/main/resources/db/migration/V29__seed_advanced_analytics_permissions.sql`

This migration inserts (all `ON CONFLICT DO NOTHING` for idempotency):

1. **Permission row:**
   ```sql
   INSERT INTO permissions (id, action, description, created_at)
   VALUES (gen_random_uuid(), 'action.redemption.analytics.advanced',
           'View advanced redemption analytics dashboard', NOW())
   ON CONFLICT (action) DO NOTHING;
   ```

2. **Feature flag row:**
   ```sql
   INSERT INTO feature_flags (id, flag_key, starter, professional, enterprise, created_at)
   VALUES (gen_random_uuid(), 'redemption_analytics_advanced', false, true, true, NOW())
   ON CONFLICT (flag_key) DO NOTHING;
   ```

3. **CLIENT_ADMIN role grant:**
   ```sql
   INSERT INTO role_permissions (role, permission_action, created_at)
   VALUES ('CLIENT_ADMIN', 'action.redemption.analytics.advanced', NOW())
   ON CONFLICT (role, permission_action) DO NOTHING;
   ```

Refer to `spec.md → ## Permissions & Feature Flags [BE + FE] → Flyway Seed Migration` for the authoritative SQL.

---

## Task F5: MV Refresh Scheduler [BE] — Size: M

_Dependencies: F3, F4_
_Parallel with: None_
_Done when: Scheduler compiles; `AnalyticsMvRefreshSchedulerTest` unit test verifies all 5 MVs are refreshed CONCURRENTLY; liability trend INSERT ON CONFLICT issued; `analytics_mv_refresh_log` upserted per MV; `mv_refresh_failed` log on exception_

**Files:**
- `src/main/java/com/tenxengage/app/service/redemption/AnalyticsMvRefreshScheduler.java`
- `src/test/java/com/tenxengage/app/service/redemption/AnalyticsMvRefreshSchedulerTest.java`

**Scheduler responsibilities:**
- Runs every 15 minutes via `@Scheduled(fixedDelay = 900_000)`
- Refreshes all 5 materialized views using `REFRESH MATERIALIZED VIEW CONCURRENTLY {mv_name}` (order: item_breakdown, segment_breakdown, time_to_first_redemption, redemption_rate_trend, failure_mode_breakdown)
- Inserts today's liability snapshot into `mv_liability_trend` per active client per currency type via `INSERT INTO mv_liability_trend ... ON CONFLICT (client_id, period_date, currency_type) DO UPDATE SET total_unredeemed_balance = EXCLUDED.total_unredeemed_balance, captured_at = NOW()`
- Upserts a row in `analytics_mv_refresh_log` per MV with `last_refreshed_at = NOW()`, `last_refresh_duration_ms`, `refresh_status = 'success'`
- On exception: logs `step=mv_refresh_failed mv_name={name}` at WARN level; upserts `refresh_status = 'failed'` in log table; continues to next MV (does not abort the full batch)

**Unit test coverage:**
- `refreshAllMvs()` happy path: each REFRESH MATERIALIZED VIEW SQL is invoked; `analytics_mv_refresh_log` upsert called per MV
- `refreshAllMvs()` partial failure: one MV throws `DataAccessException`; other MVs still refreshed; failed MV log row has `refresh_status = 'failed'`
- `insertLiabilitySnapshot()`: `INSERT INTO mv_liability_trend` ON CONFLICT issued

Refer to `technical.md → ## Repository Queries [BE] → MV Refresh SQL` for the exact SQL patterns.
