---
slug: redemption-analytics-advanced
name: Advanced Redemption Analytics
status: reviewed
format: story-sliced
roadmap: redemption-store
domain: null
builder_type: null
created: 2026-06-20
contract: null
visual_reference:
  component_path: null
  notes: null
applicable_sections:
  source: null
  sections: []
---

> **Reviewed**: 2026-06-20

# Feature: Advanced Redemption Analytics

> **Format:** story-sliced
> **Stories, tasks, and per-story tests live in sibling files:**
> - [`stories.md`](stories.md) — story index + dependency graph
> - [`stories/`](stories/) — one `US-NN-*.md` per story (self-contained execution unit)
> - [`tasks/foundation.md`](tasks/foundation.md) — horizontal bedrock tasks
> - [`tracker.md`](tracker.md) — session status tracker
> - [`test-plan.md`](test-plan.md) — cross-story integration tests
>
> **This file is the design reference.** Implementers read it alongside their story file.
>
> **Technical artifacts** (Flyway SQL, file paths, query shapes, hook specs): see [`technical.md`](technical.md).

---

## Overview

Client Admins gain dimensional and trend analytics to complement the Phase 1 dashboard (F-07). While F-07 surfaces four aggregate metric cards, this feature enables deeper interrogation: which catalog items and partner segments drive redemptions, how quickly partners make their first redemption, how redemption rates trend over time, where liability is accumulating across periods, and which processing modes and catalog items generate the most failures.

All advanced metrics are surfaced in a new **Advanced** tab added to the existing analytics dashboard at `/redemption/admin/analytics`, keeping the Phase 1 experience intact. Data is served from PostgreSQL materialized views that refresh every 15–30 minutes, with a staleness warning shown when data has not refreshed within 4 hours.

This feature satisfies the Phase 2 analytics requirements per the BRD (§FR-08.1–FR-08.7) and depends on the F-07 (`redemption-analytics-basic`) foundation already merged.

### Naming reconciliation

| BRD / advisory name | Codebase name | Decision |
|---|---|---|
| `RedemptionTransaction` | `RedemptionRequest` | Codebase wins — established by F-03 and F-07. |
| `ClientRedemptionConfig` | `TenantRedemptionSettings` | Codebase wins — established by F-03. |

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-08.1 | CLIENT_ADMIN can view redemption rate broken down by catalog item: items ranked by redemption count (most → least), showing item name, currency type, total redeemed count, total redeemed amount, and redemption rate. Filterable by date range and region (FR-08.6). |
| FR-08.2 | CLIENT_ADMIN can view redemption rate broken down by region and role: one row per unique (region × role × currency) combination, showing total redeemed count and redemption rate. Filterable by region, role, and date range (FR-08.6). _(Partner-tier segmentation was specified originally but dropped — no per-partner tier exists in the data model; the only tier is the tenant's own `subscription_tier`, constant within a tenant. Region is resolved from the partner company's top-level location, role from `client_roles.base_role_name`.)_ |
| FR-08.3 | CLIENT_ADMIN can view average time-to-first-redemption in hours, segmented by region: the mean duration from partner account creation to the partner's first COMPLETED `RedemptionRequest`, using current profile attributes at time of analysis (not point-in-time). _(Originally segmented by partner tier; regrouped to region for the same reason as FR-08.2.)_ |
| FR-08.4 | CLIENT_ADMIN can view redemption rate trend over configurable time windows: 7 days, 30 days, 90 days, and a custom date range (max 365 days). Chart shows one data point per calendar day, plotting total redeemed count and redemption rate as a time series. |
| FR-08.5 | CLIENT_ADMIN can view unredeemed balance liability trend per currency type: total unredeemed balance (available + reserved) at each period-end data point within the selected window. Exportable as CSV with columns: `period_date`, `currency_type`, `total_unredeemed_balance`. |
| FR-08.6 | All dimensional analytics views (FR-08.1, FR-08.2, FR-08.3, FR-08.7) are filterable in combination by region, role, and date range. Filters combine with AND semantics. Date range for all dimensional queries is capped at 365 days. |
| FR-08.7 | CLIENT_ADMIN can view failed and cancelled redemption rate broken down by processing mode (MANUAL / AUTOMATED) and by catalog item: each row shows failed count, cancelled count, total count, and failure rate within the selected date range. Filterable by region and date range (FR-08.6). |
| FR-08.8 | The Advanced tab displays the timestamp of the most recent materialized view refresh alongside each metric section, formatted as "Data as of {date} at {time} UTC". |
| FR-08.9 | Custom date range for dimensional analytics (FR-08.1, FR-08.2, FR-08.3, FR-08.7) is capped at 365 days; CSV export for liability trend (FR-08.5) is also capped at 365 days (12 months). The backend returns HTTP 422 with a descriptive error if either cap is exceeded. |
| FR-08.10 | Every successful liability trend CSV export (FR-08.5) generates an audit log entry: `action=DATA_EXPORTED`, `resourceType=REDEMPTION_ADVANCED_ANALYTICS_EXPORT`, with metadata `{tenantId, userId, dateFrom, dateTo, rowCount}`. |
| FR-08.11 | If the most recent materialized view refresh is older than 4 hours, the Advanced tab displays a non-blocking staleness warning banner: "Analytics data may be outdated. Last refreshed: {timestamp} UTC." The banner is dismissible for the current browser session. |

---

## Functional Completeness Audit

| # | Dimension | Status | FR / Notes |
|---|---|---|---|
| 1 | Page vs. tab placement | ⊕ Approved | Extended tab within the existing F-07 dashboard at `/redemption/admin/analytics`. F-07 content becomes the "Overview" tab; F-08 adds an "Advanced" tab. No new route needed. |
| 2 | Data store for dimensional analytics | ⊕ Approved | PostgreSQL with materialized views. Snowflake / external data warehouse deferred — depends on client scale. Confirmed by Vijay. |
| 3 | Segment timing (current vs. point-in-time profile) | ⊕ Approved | Current profile attributes at time of analysis, per BRD §business-rules. Not point-in-time. |
| 4 | Data freshness interval | ⊕ Approved | 15–30 minute MV refresh cycle; staleness warning banner triggered at 4 hours. |
| 5 | Concurrent admin load SLA | ⊕ Approved | 10 concurrent CLIENT_ADMINs per tenant, ≤3s query response at P95. Confirmed by Vijay. |
| 6 | Date range cap (F-08 vs. F-07 discrepancy) | ⊕ Approved | F-08 dimensional queries capped at 365 days; F-07 aggregate queries are 730 days. F-08 cap is intentional — dimensional pre-aggregation is more compute-intensive. See FR-08.9. |
| 7 | Export audit overlap with F-07 | ⊕ Approved | F-07 audits the unredeemed balance export. FR-08.10 audits only the new liability trend CSV export. New `AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT` distinguishes the two — no overlap. |
| 8 | Processing mode values for FR-08.7 | ⊕ Approved | F-03 `RedemptionProcessingMode` enum: `INSTANT`, `BATCH`, `APPROVAL_REQUIRED` — present on `RedemptionRequest.processingMode` in the existing codebase. (Corrected from an earlier `MANUAL`/`AUTOMATED` draft — those values exist in neither the BRD nor the codebase.) |

---

## Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | **Performance**: Support 10 concurrent CLIENT_ADMINs per tenant. All advanced analytics queries served from materialized views must respond within ≤3 seconds at P95. Materialized views absorb pre-aggregation cost; live queries are lightweight projections over the MV columns. |
| NFR-2 | **Privacy**: Advanced analytics expose only aggregate metrics (counts, rates, totals) grouped by region, role, item, or processing mode. No individual user identifiers, names, or per-user wallet balances appear in any chart or table. |
| NFR-3 | **Availability**: Internal admin tool. Target 99.5% uptime. Degraded mode acceptable: if an MV refresh fails, the API continues serving stale MV data; the UI shows the staleness warning banner (FR-08.11) rather than an error page. |
| NFR-4 | **Data isolation**: Standard multi-tenant isolation applies. All materialized views and the refresh log table include `client_id`. Service methods bind `client_id` as a named parameter in every native query. No special GDPR requirements beyond the platform baseline. |
| NFR-5 | **Events**: Read-only feature. No Kafka events published. The only write side-effect is the audit log entry generated by the CSV export (FR-08.10). |

---

## Prerequisites

| # | Dependency | Where defined |
|---|---|---|
| 1 | `redemption-analytics-basic` (F-07) merged and deployed | `features/redemption-analytics-basic/tracker.md` |
| 2 | `RedemptionAnalyticsController` at `/api/v1/redemption/analytics` | `tenxengage-backend/.../controller/redemption/RedemptionAnalyticsController.java` |
| 3 | `RedemptionAnalyticsService` with F-07 projections and Redis cache | `tenxengage-backend/.../service/redemption/RedemptionAnalyticsService.java` |
| 4 | `AnalyticsExportRateLimiter` (3 req/min per tenant) | `tenxengage-backend/.../security/AnalyticsExportRateLimiter.java` |
| 5 | `RateLimitFilter` (10 req/min per tenant on `/analytics` path) | `tenxengage-backend/.../security/RateLimitFilter.java` |
| 6 | `AuditLogService` + `AuditAction.DATA_EXPORTED` | Existing audit infrastructure (used by F-07) |
| 7 | `action.redemption.view_analytics` permission seeded (F-07 V-N+1 migration) | `features/redemption-analytics-basic/technical.md` |
| 8 | Flyway latest migration: `V9__add_budget_utilization_unique_constraints.sql` | F-08 DDL starts at V10 |

---

## New Enums

### `AuditResourceType` — add one value

| Value | Description |
|---|---|
| `REDEMPTION_ADVANCED_ANALYTICS_EXPORT` | Audit resource type for liability trend CSV downloads (FR-08.10) |

All other required enum values (`AuditAction.DATA_EXPORTED`, `CurrencyType`, `RedemptionStatus`) already exist in the contracts repo.

---

## Data Model

F-08 introduces **no new JPA entities**. All advanced analytics data is served from PostgreSQL materialized views plus one tracking table. Application code interacts with these via native SQL / `JdbcTemplate` projections — no `@Entity` classes, no Spring Data repositories for MV data.

### Materialized views

| View name | Feeds | Refresh granularity | Key columns |
|---|---|---|---|
| `mv_item_redemption_breakdown` | FR-08.1 | Replaced on each scheduler cycle | `client_id`, `catalog_item_id`, `catalog_item_name`, `currency_type`, `region`, `processing_mode`, `period_date`, `total_redeemed_count`, `total_redeemed_amount`, `redemption_rate` |
| `mv_segment_redemption_breakdown` | FR-08.2 | Replaced on each scheduler cycle | `client_id`, `region`, `role`, `currency_type`, `period_date`, `total_redeemed_count`, `total_redeemed_amount`, `redemption_rate` |
| `mv_time_to_first_redemption` | FR-08.3 | Replaced on each scheduler cycle | `client_id`, `region`, `avg_hours_to_first_redemption`, `median_hours_to_first_redemption`, `sample_count` |
| `mv_redemption_rate_trend` | FR-08.4 | Replaced on each scheduler cycle | `client_id`, `period_date`, `currency_type`, `redeemed_count`, `redemption_rate` |
| `mv_liability_trend` | FR-08.5 | Regular TABLE (not MV) — scheduler appends daily balance snapshots via INSERT ON CONFLICT DO UPDATE | `client_id`, `period_date`, `currency_type`, `total_unredeemed_balance`, `captured_at` |
| `mv_failure_mode_breakdown` | FR-08.7 | Replaced on each scheduler cycle | `client_id`, `processing_mode`, `catalog_item_id`, `catalog_item_name`, `currency_type`, `region`, `period_date`, `failed_count`, `cancelled_count`, `total_count`, `failure_rate` |

### Tracking table

| Table name | Purpose | Key columns |
|---|---|---|
| `analytics_mv_refresh_log` | Stores the last-successful-refresh timestamp per MV | `id` (UUID PK), `mv_name` (VARCHAR, unique), `last_refreshed_at` (TIMESTAMPTZ), `duration_ms` (BIGINT), `created_at` (TIMESTAMPTZ) |

The `AnalyticsMvRefreshScheduler` upserts a row (on `mv_name`) after each successful MV refresh. The `getRefreshStatus()` service method queries this table to serve FR-08.8 and the FR-08.11 staleness check.

### Entity-shape decisions

No new JPA entities are introduced by this feature. The materialized views and tracking table are created via Flyway DDL only. No `@Entity` annotations, no `JpaRepository` subinterfaces for MV data — queries are issued as native SQL with tenant-scoped named parameters.

### Multi-tenant isolation

All MVs include `client_id`. The `analytics_mv_refresh_log` table does not carry `client_id` (it is a global scheduler log — one row per MV name, not per tenant). Service methods call `tenantValidator.getCurrentClientId()` and supply `client_id` as a named parameter in every native query. Because MVs are not `@Entity` classes, the Hibernate `@Filter` does not apply — explicit `client_id` binding in every query is mandatory.

---

## Permissions & Feature Flags

### New permission

| Permission string | Roles | Description |
|---|---|---|
| `action.redemption.analytics.advanced` | `CLIENT_ADMIN` | Grants access to all advanced analytics endpoints and the liability trend CSV export |

The F-07 permission `action.redemption.view_analytics` is unchanged. Advanced analytics requires the additional `action.redemption.analytics.advanced` — `CLIENT_ADMIN` must hold both permissions.

### Feature flag

| Flag key | Starter | Professional | Enterprise |
|---|---|---|---|
| `redemption_analytics_advanced` | `false` | `true` | `true` |

The feature flag gates the Advanced tab in the frontend and is checked server-side in the service layer before processing. If the flag is disabled for the tenant's subscription tier, the service throws `FeatureNotEnabledException` and the controller returns HTTP 403.

### Permission matrix

| Permission | CLIENT_ADMIN | PLATFORM_ADMIN | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|
| `action.redemption.analytics.advanced` | Y | — | — | — |

### Permission resolution

Follows the platform's standard 5-layer resolution: Global default → Subscription tier → Client override → Role → User override. `CLIENT_ADMIN` inherits `action.redemption.analytics.advanced` via role assignment seeded in V29.

---

## DTOs

### Shared query parameters (not a DTO class — passed as `@RequestParam`)

| Param | Type | Required | Default | Notes |
|---|---|---|---|---|
| `dateFrom` | `LocalDate` (ISO 8601 `YYYY-MM-DD`) | No | Today − 30 days | Inclusive |
| `dateTo` | `LocalDate` (ISO 8601 `YYYY-MM-DD`) | No | Today | Inclusive |
| `region` | `String` (comma-separated) | No | None | Multi-value region filter (partner company's top-level location name) |
| `role` | `String` (comma-separated) | No | None | Multi-value role filter (`client_roles.base_role_name`) |

### Response DTOs (all in `dto/response/redemption/`)

#### `ItemRedemptionDto`
```
catalogItemId       String
catalogItemName     String
currencyId        String
totalRedeemedCount  Long
totalRedeemedAmount BigDecimal
redemptionRate      Double      // percentage, 0–100
```

#### `ItemBreakdownResponse`
```
dateWindow      DateWindowDto        // reuses F-07 DTO
items           List<ItemRedemptionDto>
lastRefreshedAt Instant
```

#### `SegmentRedemptionDto`
```
region              String      // nullable — null when partner has no location
role                String      // nullable — null when user has no client role
currencyId        String
totalRedeemedCount  Long
totalRedeemedAmount BigDecimal
redemptionRate      Double
```

#### `SegmentBreakdownResponse`
```
dateWindow      DateWindowDto
segments        List<SegmentRedemptionDto>
lastRefreshedAt Instant
```

#### `RegionTimeToRedemptionDto`
```
region                       String    // nullable — null when partner has no location
avgHoursToFirstRedemption    Double    // null when sampleCount = 0
medianHoursToFirstRedemption Double    // null when sampleCount = 0
sampleCount                  Long
```

#### `TimeToFirstRedemptionResponse`
```
filters         Map<String, Object>  // active region filter values
regions         List<RegionTimeToRedemptionDto>
lastRefreshedAt Instant
```

#### `TrendDataPointDto`
```
periodDate      LocalDate
currencyId    String
redeemedCount   Long
redemptionRate  Double
```

#### `RedemptionTrendResponse`
```
dateWindow      DateWindowDto
dataPoints      List<TrendDataPointDto>
lastRefreshedAt Instant
```

#### `LiabilityDataPointDto`
```
periodDate             LocalDate
currencyId           String
totalUnredeemedBalance BigDecimal
```

#### `LiabilityTrendResponse`
```
dateWindow      DateWindowDto
dataPoints      List<LiabilityDataPointDto>
lastRefreshedAt Instant
```

#### `FailureModeDto`
```
processingMode  String    // INSTANT | BATCH | APPROVAL_REQUIRED (RedemptionProcessingMode, F-03)
catalogItemId   String
catalogItemName String
currencyId    String
failedCount     Long
cancelledCount  Long
totalCount      Long
failureRate     Double
```

#### `FailureBreakdownResponse`
```
dateWindow      DateWindowDto
failureModes    List<FailureModeDto>
lastRefreshedAt Instant
```

#### `AnalyticsRefreshStatusResponse`
```
lastRefreshedAt          Instant
isStale                  boolean   // true when now − lastRefreshedAt > 4 hours
stalenessThresholdHours  int       // always 4; included for FE to render countdown
```

---

## API Endpoints

### New controller: `RedemptionAdvancedAnalyticsController`

**Base path**: `/api/v1/redemption/analytics/advanced`
**Permission on all endpoints**: `@RequiresPermission("action.redemption.analytics.advanced")`
**Feature flag**: checked in service layer before processing; throws 403 if disabled

---

#### `GET /api/v1/redemption/analytics/advanced/item-breakdown`

Serves FR-08.1.

| | |
|---|---|
| **Query params** | `dateFrom`, `dateTo`, `region` |
| **Validation** | `dateFrom ≤ dateTo`; span ≤ 365 days |
| **Success** | `200 OK` → `ItemBreakdownResponse` |
| **Errors** | `422` date validation failure · `403` permission/flag · `429` rate limited |
| **Cache** | Redis 60s per tenant + filter combination |

---

#### `GET /api/v1/redemption/analytics/advanced/segment-breakdown`

Serves FR-08.2.

| | |
|---|---|
| **Query params** | `dateFrom`, `dateTo`, `region`, `role` |
| **Validation** | span ≤ 365 days |
| **Success** | `200 OK` → `SegmentBreakdownResponse` |
| **Errors** | `422` · `403` · `429` |
| **Cache** | Redis 60s |

---

#### `GET /api/v1/redemption/analytics/advanced/time-to-first-redemption`

Serves FR-08.3.

| | |
|---|---|
| **Query params** | `dateFrom`, `dateTo`, `region` |
| **Note** | `dateFrom`/`dateTo` scope which partner cohort is included (by join date). MV uses current-profile region values. |
| **Success** | `200 OK` → `TimeToFirstRedemptionResponse` |
| **Errors** | `422` · `403` · `429` |
| **Cache** | Redis 60s |

---

#### `GET /api/v1/redemption/analytics/advanced/trend`

Serves FR-08.4.

| | |
|---|---|
| **Query params** | `dateFrom` (default: today−30), `dateTo` (default: today) |
| **Note** | No region/role filter — trend is tenant-wide |
| **Validation** | span ≤ 365 days |
| **Success** | `200 OK` → `RedemptionTrendResponse` |
| **Errors** | `422` · `403` · `429` |
| **Cache** | Redis 60s |

---

#### `GET /api/v1/redemption/analytics/advanced/liability-trend`

Serves FR-08.5 (JSON view).

| | |
|---|---|
| **Query params** | `dateFrom`, `dateTo` |
| **Validation** | span ≤ 365 days |
| **Success** | `200 OK` → `LiabilityTrendResponse` |
| **Errors** | `422` · `403` · `429` |
| **Cache** | Redis 60s |

---

#### `GET /api/v1/redemption/analytics/advanced/liability-trend/export`

Serves FR-08.5 CSV export. Uses existing `AnalyticsExportRateLimiter` (3 req/min per tenant).

| | |
|---|---|
| **Query params** | `dateFrom`, `dateTo` |
| **Validation** | span ≤ 365 days |
| **Success** | `200 OK` · `Content-Type: text/csv; charset=UTF-8` · `Content-Disposition: attachment; filename="redemption-liability-trend.csv"` |
| **CSV columns** | `period_date,currency_type,total_unredeemed_balance` |
| **Errors** | `422` date validation · `429` export rate limit (includes `Retry-After` header) · `403` |
| **Cache** | **Not cached** — live MV read |
| **Audit** | Every successful `200` emits `DATA_EXPORTED / REDEMPTION_ADVANCED_ANALYTICS_EXPORT` (after CSV bytes built) |

---

#### `GET /api/v1/redemption/analytics/advanced/failure-breakdown`

Serves FR-08.7.

| | |
|---|---|
| **Query params** | `dateFrom`, `dateTo`, `region` |
| **Validation** | span ≤ 365 days |
| **Success** | `200 OK` → `FailureBreakdownResponse` |
| **Errors** | `422` · `403` · `429` |
| **Cache** | Redis 60s |

---

#### `GET /api/v1/redemption/analytics/advanced/refresh-status`

Serves FR-08.8 + staleness data for FR-08.11.

| | |
|---|---|
| **Query params** | None |
| **Success** | `200 OK` → `AnalyticsRefreshStatusResponse` |
| **Errors** | `403` |
| **Cache** | **Not cached** — must reflect live `analytics_mv_refresh_log` state |

---

## Service Layer

### `RedemptionAdvancedAnalyticsService`

All methods are `@Transactional(readOnly = true)`. Each method opens with:

```java
UUID clientId = tenantValidator.getCurrentClientId();
if (!featureFlagService.isEnabled("redemption_analytics_advanced", clientId)) {
    throw new FeatureNotEnabledException("redemption_analytics_advanced");
}
// validate date range: dateFrom <= dateTo, span <= 365 days
```

| Method | MV queried | Redis cached | Cache key pattern |
|---|---|---|---|
| `getItemBreakdown(filter)` | `mv_item_redemption_breakdown` | 60s | `{clientId}:item-breakdown:{dateFrom}:{dateTo}:{region}` |
| `getSegmentBreakdown(filter)` | `mv_segment_redemption_breakdown` | 60s | `{clientId}:segment-breakdown:{dateFrom}:{dateTo}:{region}:{role}` |
| `getTimeToFirstRedemption(filter)` | `mv_time_to_first_redemption` | 60s | `{clientId}:ttfr:{dateFrom}:{dateTo}:{region}` |
| `getRedemptionTrend(dateFrom, dateTo)` | `mv_redemption_rate_trend` | 60s | `{clientId}:trend:{dateFrom}:{dateTo}` |
| `getLiabilityTrend(dateFrom, dateTo)` | `mv_liability_trend` | 60s | `{clientId}:liability-trend:{dateFrom}:{dateTo}` |
| `exportLiabilityTrend(dateFrom, dateTo)` | `mv_liability_trend` | **Not cached** | — |
| `getFailureBreakdown(filter)` | `mv_failure_mode_breakdown` | 60s | `{clientId}:failure-breakdown:{dateFrom}:{dateTo}:{region}` |
| `getRefreshStatus()` | `analytics_mv_refresh_log` | **Not cached** | — |

### `AnalyticsMvRefreshScheduler`

A Spring `@Component` that:

1. Runs on `@Scheduled(fixedRateString = "${analytics.mv.refresh-interval-ms:900000}")` (default 15 min)
2. Calls `REFRESH MATERIALIZED VIEW CONCURRENTLY` for each of the 6 MVs in sequence
3. After each successful refresh, upserts a row in `analytics_mv_refresh_log` on `mv_name`
4. Logs `step=mv_refresh_completed mvName={} durationMs={}` after each MV
5. On failure: logs `step=mv_refresh_failed mvName={} error={}`, does **not** update `analytics_mv_refresh_log` (stale timestamp persists, triggering FR-08.11 banner)

### Input validation (all service methods)

```
- Default dateFrom: today − 30 days (UTC)
- Default dateTo:   today (UTC)
- Guard: dateFrom.isAfter(dateTo)  → BusinessRuleException "dateFrom must not be after dateTo." (HTTP 422)
- Guard: span > 365 days           → BusinessRuleException "Date range must not exceed 365 days." (HTTP 422)
```

---

## Security Design

| Concern | Implementation |
|---|---|
| Authentication | JWT bearer; `TenantValidator.getCurrentClientId()` extracts `client_id` from token claims |
| Authorization | `@RequiresPermission("action.redemption.analytics.advanced")` on all controller methods |
| Feature flag gate | `featureFlagService.isEnabled("redemption_analytics_advanced", clientId)` checked in service; returns 403 if false |
| Tenant isolation | `client_id` bound as named parameter in every native MV query; no cross-tenant data accessible |
| Query rate limiting | `RateLimitFilter` applies 10 req/min **per IP** to the exact path `/api/v1/redemption/analytics` (F-07). ⚠️ **Deviation:** it does **not** throttle the F-08 `/advanced/**` sub-paths — the filter matches by exact equality (not prefix) and keys per IP, not per tenant. F-08's DB load is instead bounded by the export rate limiter (3/min/tenant) + the 60s Redis cache + MV-backed reads. Accepted for Phase 1 (internal admin tool); not a gating acceptance criterion. A literal per-tenant 10/min query cap was rejected because the tab fires 6 queries per load and NFR-1 targets 10 concurrent admins/tenant — it would throttle normal use. Locked in by `AnalyticsExportRateLimiterTest` + `AdvancedAnalyticsCrossCuttingIT`. |
| Export rate limiting | `AnalyticsExportRateLimiter`: 3 req/min per tenant (shared with F-07 export limiter) |
| PII prevention | All MVs aggregate by dimension groups; no individual user identifiers in any chart or CSV |
| CSV injection | `escapeCsv()` helper from `RedemptionAnalyticsService` reused in `exportLiabilityTrend()` |
| HTTPS | All endpoints served over TLS; no plaintext fallback |

---

## Audit Trail

| Trigger | `action` | `resourceType` | Metadata |
|---|---|---|---|
| Successful `GET /liability-trend/export` (HTTP 200) | `DATA_EXPORTED` | `REDEMPTION_ADVANCED_ANALYTICS_EXPORT` | `{tenantId, userId, dateFrom, dateTo, rowCount}` |

The audit entry is written **after** the CSV bytes are built, so 403, 422, and 429 failures never generate an audit row. This matches the pattern in F-07's `RedemptionAnalyticsService.exportUnredeemedBalances()`.

No audit entries are generated for JSON analytics queries (read-only, non-export).

---

## Observability

### Structured log events

All log lines use `key=value` structured format.

| Step key | When emitted | Fields |
|---|---|---|
| `advanced_analytics_query` | Cache miss on any analytics service method | `clientId`, `endpoint`, `dateFrom`, `dateTo`, `region`, `role`, `durationMs` |
| `advanced_analytics_export_downloaded` | After CSV bytes built in `exportLiabilityTrend` | `tenantId`, `userId`, `dateFrom`, `dateTo`, `rowCount`, `durationMs` |
| `mv_refresh_completed` | After each successful MV refresh | `mvName`, `durationMs`, `triggeredAt` |
| `mv_refresh_failed` | On MV refresh failure | `mvName`, `error`, `lastSuccessfulRefreshAt` |
| `advanced_analytics_rate_limit_exceeded` | On 429 from export rate limiter | `tenantId`, `endpoint` |
| `advanced_analytics_feature_disabled` | When feature flag check fails in service | `tenantId`, `featureFlag` |
| `advanced_analytics_stale_data_served` | When `getRefreshStatus()` returns `isStale=true` | `tenantId`, `lastRefreshedAt`, `staleForHours` |

### Metrics (Micrometer)

| Metric | Type | Tags |
|---|---|---|
| `redemption.advanced_analytics.query.duration` | Timer | `endpoint` |
| `redemption.advanced_analytics.export.count` | Counter | — |
| `redemption.mv_refresh.duration` | Timer | `mvName` |

---

## Frontend Specification

### Layout

The existing page at `/redemption/admin/analytics` gains a tab bar:
- **Overview** tab — existing F-07 metric cards (no changes to existing components)
- **Advanced** tab — F-08 content (new)

The Advanced tab is rendered only when `usePermissions()` returns `true` for `action.redemption.analytics.advanced` AND the `redemption_analytics_advanced` feature flag is enabled. If either check fails, the tab is hidden entirely (not greyed out or disabled).

### Filter bar (top of Advanced tab)

A persistent filter bar shared by all sections:

- **Date range**: presets — Last 7 days, Last 30 days (default on tab open), Last 90 days — plus a custom range calendar picker. Custom range enforces a 365-day maximum; if exceeded, shows inline validation error "Date range cannot exceed 365 days" and disables the apply button.
- **Region**: multi-select. Options sourced from distinct region values returned in the segment breakdown response.
- **Role**: multi-select. Options sourced from distinct role values returned in the segment breakdown response.

On any filter change, all six section queries are invalidated and re-fetched simultaneously via TanStack Query.

### Sections (within Advanced tab)

**1. Item Breakdown (FR-08.1)**
- Sortable table. Default sort: `totalRedeemedCount` descending.
- Columns: Item Name, Currency Type, Redeemed Count, Redeemed Amount, Redemption Rate (%)
- Empty state: "No redemption data for the selected filters"

**2. Segment Breakdown (FR-08.2)**
- Sortable table.
- Columns: Region, Role, Currency Type, Redeemed Count, Redemption Rate (%)
- Region / Role cells render "—" when null (partner has no location / user has no client role)

**3. Time to First Redemption (FR-08.3)**
- Summary table by region: Region, Avg Hours, Median Hours, Sample Count
- When `sampleCount = 0`, avg/median columns display "N/A"

**4. Redemption Rate Trend (FR-08.4)**
- `recharts` `LineChart`. X-axis: date. Y-axis: redemption rate (%). One line per currency type.
- Tooltip: `redeemedCount` + `redemptionRate` for hovered date.
- Date range controlled by the filter bar date picker.

**5. Liability Trend (FR-08.5)**
- `recharts` `LineChart`. X-axis: date. Y-axis: total unredeemed balance. One line per currency type.
- **Export CSV button**: calls `GET /liability-trend/export`. On 429, button is disabled and shows a countdown (`Retry-After` seconds). Matches the F-07 export button pattern.

**6. Failure Breakdown (FR-08.7)**
- Sortable table. Default sort: `failureRate` descending.
- Columns: Processing Mode, Item Name, Currency Type, Failed, Cancelled, Total, Failure Rate (%)

### Refresh status & staleness (FR-08.8 + FR-08.11)

- Each section's title row includes a caption: "Data as of {lastRefreshedAt formatted as 'MMM DD, YYYY [at] HH:mm'} UTC" sourced from `lastRefreshedAt` in the section's response body.
- `useRefreshStatus()` polls `GET /refresh-status` every 5 minutes (`refetchInterval: 300_000`).
- When `isStale = true`: a yellow warning banner appears at the top of the Advanced tab: "Analytics data may be outdated. Last refreshed: {timestamp} UTC." Banner has an ✕ dismiss button that hides it for the session (state held in React local state, not `localStorage`).

### Hooks

| Hook | Endpoint | Query key | staleTime |
|---|---|---|---|
| `useItemBreakdown(filters)` | `GET .../item-breakdown` | `['advanced-analytics', 'item-breakdown', filters]` | 60 000 ms |
| `useSegmentBreakdown(filters)` | `GET .../segment-breakdown` | `['advanced-analytics', 'segment-breakdown', filters]` | 60 000 ms |
| `useTimeToFirstRedemption(filters)` | `GET .../time-to-first-redemption` | `['advanced-analytics', 'ttfr', filters]` | 60 000 ms |
| `useRedemptionTrend(dateFrom, dateTo)` | `GET .../trend` | `['advanced-analytics', 'trend', dateFrom, dateTo]` | 60 000 ms |
| `useLiabilityTrend(dateFrom, dateTo)` | `GET .../liability-trend` | `['advanced-analytics', 'liability-trend', dateFrom, dateTo]` | 60 000 ms |
| `useFailureBreakdown(filters)` | `GET .../failure-breakdown` | `['advanced-analytics', 'failure-breakdown', filters]` | 60 000 ms |
| `useRefreshStatus()` | `GET .../refresh-status` | `['advanced-analytics', 'refresh-status']` | 0 ms (always fresh) |

No mutations — this feature is read-only.

---

## Caching Strategy

| Layer | What is cached | TTL | Key pattern | Eviction |
|---|---|---|---|---|
| PostgreSQL MV | Pre-aggregated dimensional data for all tenants | 15–30 min | — (scheduler-driven) | `REFRESH MATERIALIZED VIEW CONCURRENTLY` |
| Redis (`@Cacheable`) | API JSON responses for dimensional queries | 60 s | `{clientId}:{endpoint}:{dateFrom}:{dateTo}:{region}:{role}` | TTL expiry |
| TanStack Query | FE in-memory response cache | 60 000 ms | See Hooks table | Tab change / filter update |

**Not cached:**
- `GET /liability-trend/export` — must provide a consistent snapshot; bypasses Redis
- `GET /refresh-status` — must reflect live `analytics_mv_refresh_log` state; bypasses Redis

**Cache invalidation note**: The Redis 60s TTL is intentionally shorter than the MV refresh interval (15–30 min). Requests within a 60s window return identical materialized data without redundant DB reads; the TTL expires before the next MV cycle without requiring manual eviction.

---

## Data Retention

No new personally identifiable information is stored. Materialized views contain only aggregate metrics keyed by `client_id`, dimension values (region / role / item / processing mode), and period dates. The `analytics_mv_refresh_log` table stores only `mv_name` and refresh timestamps — no user data.

Existing `RedemptionRequest`, `RewardWallet`, and `LedgerEntry` retention policies (governed by F-05 and the compliance module) are unaffected. When those records are anonymized or deleted per policy, the next MV refresh automatically excludes them from the aggregates.

---

## Edge Cases

| # | Scenario | Expected behavior |
|---|---|---|
| 1 | No redemptions for the selected date range | All charts render empty state: "No data for the selected period" |
| 2 | A region × role combination has zero redemptions | Segment breakdown omits that row — zero-count rows are not shown |
| 3 | A region has no completed redemptions for TTFR | Row shown with `sampleCount = 0`; `avgHoursToFirstRedemption = null`; FE renders "N/A" |
| 4 | MV refresh fails mid-execution | `mv_refresh_failed` logged; `analytics_mv_refresh_log` not updated; stale timestamp persists; FE staleness banner shown |
| 5 | MV refresh runs while a query is in flight | `REFRESH MATERIALIZED VIEW CONCURRENTLY` allows concurrent reads — no query blocking |
| 6 | Export span = exactly 365 days | Allowed. Span of 366+ days returns 422. |
| 7 | Export rate limit hit (3 req/min per tenant) | `429 Too Many Requests` with `Retry-After` header; no audit log entry; FE disables button with countdown |
| 8 | Tenant subscription tier = Starter (`flag = false`) | All `GET /advanced/**` endpoints return 403; FE Advanced tab hidden entirely |
| 9 | `dateFrom` is in the future | `dateFrom > dateTo` guard fires (since `dateTo` defaults to today); returns 422 |
| 10 | Tenant has wallets but no RedemptionRequests | Item breakdown, segment breakdown, failure breakdown: empty lists; TTFR: all `sampleCount = 0`; trend / liability trend: empty `dataPoints` |
| 11 | `region` filter value does not match any region in the tenant | Service returns empty result; not an error |
| 12 | `analytics_mv_refresh_log` has no rows yet (first deploy, scheduler hasn't run) | `getRefreshStatus()` returns `lastRefreshedAt = null`; FE treats null as stale and shows the warning banner |

---

## Acceptance Tests

### AT-08.1 — Item breakdown returns correct shape
**Given** CLIENT_ADMIN with `action.redemption.analytics.advanced` and `redemption_analytics_advanced = true`  
**When** `GET /api/v1/redemption/analytics/advanced/item-breakdown?dateFrom=2026-01-01&dateTo=2026-01-31`  
**Then** `200 OK`; `items` sorted by `totalRedeemedCount` descending; each item has `catalogItemId`, `catalogItemName`, `currencyId`, `totalRedeemedCount`, `redemptionRate`; `lastRefreshedAt` is present

### AT-08.2 — Segment breakdown respects region filter
**Given** Tenant has partners in the APAC and EMEA regions with redemptions  
**When** `GET .../segment-breakdown?region=APAC`  
**Then** All rows in `segments` have `region = "APAC"`

### AT-08.3 — TTFR returns N/A for region with no completed redemptions
**Given** A region with no completed `RedemptionRequest` rows  
**When** `GET .../time-to-first-redemption`  
**Then** The row for that region has `sampleCount = 0` and `avgHoursToFirstRedemption = null`

### AT-08.4 — Trend endpoint enforces 365-day cap
**Given** CLIENT_ADMIN  
**When** `GET .../trend?dateFrom=2024-01-01&dateTo=2026-01-01` (span = 731 days)  
**Then** `422 Unprocessable Entity`; error message contains "Date range must not exceed 365 days"

### AT-08.5 — Liability trend CSV export correct shape and audit
**Given** CLIENT_ADMIN within rate limit  
**When** `GET .../liability-trend/export?dateFrom=2026-01-01&dateTo=2026-01-31`  
**Then** `200 OK`; `Content-Type: text/csv; charset=UTF-8`; CSV header `period_date,currency_type,total_unredeemed_balance`; audit log entry created with `action=DATA_EXPORTED`, `resourceType=REDEMPTION_ADVANCED_ANALYTICS_EXPORT`

### AT-08.6 — Export rate limit enforced
**Given** CLIENT_ADMIN has made 3 export requests within the current rate-limit window  
**When** 4th `GET .../liability-trend/export`  
**Then** `429 Too Many Requests` with `Retry-After` header; no audit log entry written

### AT-08.7 — Staleness detection
**Given** `analytics_mv_refresh_log` has `last_refreshed_at` more than 4 hours ago  
**When** `GET .../refresh-status`  
**Then** `200 OK`; `isStale = true`; `lastRefreshedAt` matches the log row

### AT-08.8 — Feature flag blocks Starter tenant
**Given** Starter-tier tenant (`redemption_analytics_advanced = false`)  
**When** `GET .../item-breakdown`  
**Then** `403 Forbidden`

### AT-08.9 — JSON queries generate no audit entries
**Given** CLIENT_ADMIN calls `GET .../segment-breakdown`  
**Then** No audit log entry is created

### AT-08.10 — Failure breakdown by processing mode
**Given** Tenant has FAILED `RedemptionRequest` rows with `processingMode = MANUAL` and `processingMode = AUTOMATED`  
**When** `GET .../failure-breakdown`  
**Then** Response contains rows for both processing modes with correct `failedCount` and `failureRate`

### AT-08.11 — Null refresh log on first deploy
**Given** `analytics_mv_refresh_log` table is empty (no refresh has run yet)  
**When** `GET .../refresh-status`  
**Then** `200 OK`; `lastRefreshedAt = null`; `isStale = true`

---

## Modified Existing Endpoints

| Endpoint | Change | Reason |
|---|---|---|
| `GET /api/v1/redemption/analytics` | **None** | F-07 endpoint unchanged; F-08 adds a sibling controller at `/advanced` |
| `GET /api/v1/redemption/analytics/export` | **None** | F-07 export unchanged; F-08 adds a new export at `/advanced/liability-trend/export` |

No existing endpoints are modified by this feature.

---

## Out of Scope

- Cohort comparisons or year-over-year trend comparisons (deferred to Phase 3)
- Partner-level (individual user) drill-down analytics — aggregate-only per NFR-2
- Real-time streaming analytics — MV refresh is batch (15–30 min)
- Snowflake or external data warehouse integration — depends on future client scale
- Predictive or ML-driven analytics
- Internal admin portal analytics (separate feature, separate domain)
- Analytics on non-redemption entities (wallet earning, training completions)
- Self-serve MV refresh trigger from the UI — scheduler-controlled only
- Customizable MV refresh intervals per tenant — single global interval

---

## Planning Seeds (from feature brief)

Verbatim from `roadmaps/redemption-store/features/F-08-redemption-store.md` → Suggested story seeds:

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | View redemption rate by catalog item | Client Admin identifies most and least redeemed items to optimize catalog curation | reporting | F-07.S-01 |
| S-02 | View redemption by partner segment | Client Admin sees breakdowns by tier, region, and role to identify engagement gaps | reporting | S-01 |
| S-03 | View time-to-first-redemption | Client Admin understands how quickly new partners convert earned rewards | reporting | S-01 |
| S-04 | View redemption rate trends over time | Client Admin tracks engagement trends across configurable time windows | reporting | S-01 |
| S-05 | Filter analytics by tier, region, and date | Client Admin combines multiple filters for targeted program diagnosis | UI | S-02 |

---

## Verification Steps

- [ ] `GET /api/v1/redemption/analytics/advanced/item-breakdown` returns `200` with correct `ItemBreakdownResponse` shape and populated `lastRefreshedAt`
- [ ] `GET .../segment-breakdown?region=APAC` returns only rows where `region = "APAC"`
- [ ] `GET .../trend?dateFrom=X&dateTo=Y` with span = 366 days → `422` with message containing "365 days"
- [ ] `GET .../liability-trend/export` returns CSV with header `period_date,currency_type,total_unredeemed_balance` and triggers audit log entry `REDEMPTION_ADVANCED_ANALYTICS_EXPORT`
- [ ] 4th export request within 60 seconds → `429` with `Retry-After` header; no audit entry
- [ ] `GET .../refresh-status` returns `isStale = true` when `analytics_mv_refresh_log` row is > 4 hours old
- [ ] Starter-tier tenant (flag disabled) → `403` on all `/advanced/**` endpoints
- [ ] CLIENT_ADMIN without `action.redemption.analytics.advanced` → `403` on all `/advanced/**` endpoints
- [ ] `REFRESH MATERIALIZED VIEW CONCURRENTLY` runs without blocking concurrent `SELECT` queries on the same MV (verify via `pg_stat_activity`)
- [ ] Audit log contains exactly one entry per successful export; JSON query endpoints produce no audit entries
- [ ] FE Advanced tab is hidden (not rendered) when `redemption_analytics_advanced` flag is false
- [ ] FE staleness banner appears when `isStale = true`; ✕ dismiss hides it for the session without page reload
- [ ] FE export button shows countdown and is disabled for the duration of the `Retry-After` window on 429
- [ ] All 6 MV sections show "Data as of {timestamp} UTC" caption beneath their title
