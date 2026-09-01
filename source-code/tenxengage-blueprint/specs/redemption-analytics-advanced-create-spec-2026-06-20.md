---
slug: redemption-analytics-advanced
stepsCompleted: [parse-input, load-brd-context, load-project-context, resolve-open-questions, detect-feature-shape, load-shape-references, scope-decomposition, security-analysis, events-analysis, test-strategy, permissions-analysis, derive-slug, generate-spec-content, generate-technical-content, write-plan-file]
filesWritten: ["spec.md", "technical.md"]
---

# Spec Plan: redemption-analytics-advanced

## Feature
- **Slug**: `redemption-analytics-advanced`
- **Folder**: `features/redemption-analytics-advanced/`
- **Branch**: `roadmaps/redemption-store` (current working branch)

## Context

F-08 Advanced Redemption Analytics is the Phase 2 extension of F-07 (Basic Redemption Analytics Dashboard, now merged). Where F-07 exposed four aggregate metric cards (redemption rate, unredeemed balance, failed/cancelled rate, total count), F-08 gives Client Admins dimensional drill-down: by catalog item, partner tier × region × role, processing mode, and time. It also introduces time-series charts for redemption trends and liability accumulation.

The feature extends the existing analytics page at `/redemption/admin/analytics` by adding an **Advanced** tab alongside the existing Overview tab — no new route. Data is served from PostgreSQL materialized views refreshed on a 15-minute scheduler cycle, keeping the primary DB free of analytical load. Vijay confirmed PostgreSQL with MVs for now; Snowflake or an external warehouse is explicitly deferred pending client scale requirements.

Two architectural decisions were confirmed out-of-band with Vijay: (1) 10 concurrent CLIENT_ADMINs per tenant is the correct SLA target (not 50+), with ≤3s P95 response; (2) PostgreSQL with materialized views is the correct data store (no Snowflake). The 365-day cap on dimensional queries (vs. F-07's 730-day cap for aggregate queries) is intentional — dimensional pre-aggregation is more compute-intensive.

F-08 introduces no new JPA entities, no Kafka events, and no new routes. The primary implementation work is: 2 Flyway migrations (V10 MV DDL + V11 permissions), a new controller + service, a scheduler, 13 response DTOs, and a new Advanced tab with 6 data sections and a filter bar in the frontend.

## Phase 0 answers (locked)

| Question | Answer |
|---|---|
| Page vs. tab | Extended tab within `/redemption/admin/analytics` — not a new route |
| Data store | PostgreSQL with materialized views (Vijay confirmed; Snowflake deferred) |
| Segment timing | Current profile attributes at time of analysis (per BRD §business-rules) |
| Data freshness | 15–30 min MV refresh; staleness warning at 4 hours |
| Concurrent admin load | 10 concurrent CLIENT_ADMINs per tenant (Vijay confirmed) |
| Query SLA | ≤3s response at P95 |
| Date range cap | 365 days for F-08 dimensional queries (F-07 aggregate cap = 730 days; intentional difference) |
| Export audit overlap | No overlap — new `AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT` for liability trend export |
| Processing mode values | `MANUAL` and `AUTOMATED` (from `RedemptionRequest.processingMode`) |
| New JPA entities | None — MVs queried via native SQL / JdbcTemplate projections |
| Kafka events | None — read-only feature |
| Feature flag | `redemption_analytics_advanced`: false / true / true (Starter / Professional / Enterprise) |
| Permission | `action.redemption.analytics.advanced` — CLIENT_ADMIN only |
| Flyway start | V10 (V9 is latest: `V9__add_budget_utilization_unique_constraints.sql`) |
| Export rate limiter | Reuses existing `AnalyticsExportRateLimiter` (3 req/min per tenant) |

## Scope summary

0 new JPA entities · 6 materialized views · 1 tracking table · 8 new endpoints · 1 new controller · 1 new service · 1 new scheduler · 13 response DTOs · 7 FE hooks · 8 FE components. Single spec.

## Permissions matrix

| Permission key | Scope | CLIENT_ADMIN | PLATFORM_ADMIN | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|
| `action.redemption.analytics.advanced` | CLIENT | Y | — | — | — |

Feature flag: `redemption_analytics_advanced` — starter: `false`, professional: `true`, enterprise: `true`

## Kafka topics

(None — no events shape. F-08 is read-only.)

## NEEDS_CLARIFICATION

None. All questions resolved during the spec run.

## Registry edits

None.

---

### File: features/redemption-analytics-advanced/spec.md

---
slug: redemption-analytics-advanced
name: Advanced Redemption Analytics
status: draft
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
| FR-08.1 | CLIENT_ADMIN can view redemption rate broken down by catalog item: items ranked by redemption count (most → least), showing item name, currency type, total redeemed count, total redeemed amount, and redemption rate. Filterable by date range and partner segment (FR-08.6). |
| FR-08.2 | CLIENT_ADMIN can view redemption rate broken down by partner tier, region, and role: one row per unique (tier × region × role × currency) combination, showing total redeemed count and redemption rate. Filterable by tier, region, and date range (FR-08.6). |
| FR-08.3 | CLIENT_ADMIN can view average time-to-first-redemption in hours, segmented by partner tier: the mean duration from partner account creation to the partner's first COMPLETED `RedemptionRequest`, using current profile attributes at time of analysis (not point-in-time). |
| FR-08.4 | CLIENT_ADMIN can view redemption rate trend over configurable time windows: 7 days, 30 days, 90 days, and a custom date range (max 365 days). Chart shows one data point per calendar day, plotting total redeemed count and redemption rate as a time series. |
| FR-08.5 | CLIENT_ADMIN can view unredeemed balance liability trend per currency type: total unredeemed balance (available + reserved) at each period-end data point within the selected window. Exportable as CSV with columns: `period_date`, `currency_type`, `total_unredeemed_balance`. |
| FR-08.6 | All dimensional analytics views (FR-08.1, FR-08.2, FR-08.3, FR-08.7) are filterable in combination by partner tier, region, and date range. Filters combine with AND semantics. Date range for all dimensional queries is capped at 365 days. |
| FR-08.7 | CLIENT_ADMIN can view failed and cancelled redemption rate broken down by processing mode (MANUAL / AUTOMATED) and by catalog item: each row shows failed count, cancelled count, total count, and failure rate within the selected date range. Filterable by tier, region, and date range (FR-08.6). |
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
| 8 | Processing mode values for FR-08.7 | ⊕ Approved | `MANUAL` and `AUTOMATED` — values present on `RedemptionRequest.processingMode` in the existing codebase. |

---

## Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | **Performance**: Support 10 concurrent CLIENT_ADMINs per tenant. All advanced analytics queries served from materialized views must respond within ≤3 seconds at P95. Materialized views absorb pre-aggregation cost; live queries are lightweight projections over the MV columns. |
| NFR-2 | **Privacy**: Advanced analytics expose only aggregate metrics (counts, rates, totals) grouped by tier, region, role, item, or processing mode. No individual user identifiers, names, or per-user wallet balances appear in any chart or table. |
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
| `mv_item_redemption_breakdown` | FR-08.1, FR-08.7 | Replaced on each scheduler cycle | `client_id`, `catalog_item_id`, `catalog_item_name`, `currency_type`, `partner_tier`, `region`, `period_date`, `total_redeemed_count`, `total_redeemed_amount`, `redemption_rate` |
| `mv_segment_redemption_breakdown` | FR-08.2, FR-08.6 | Replaced on each scheduler cycle | `client_id`, `partner_tier`, `region`, `role`, `currency_type`, `period_date`, `total_redeemed_count`, `total_redeemed_amount`, `redemption_rate` |
| `mv_time_to_first_redemption` | FR-08.3 | Replaced on each scheduler cycle | `client_id`, `partner_tier`, `avg_hours_to_first_redemption`, `median_hours_to_first_redemption`, `sample_count` |
| `mv_redemption_rate_trend` | FR-08.4 | Replaced on each scheduler cycle | `client_id`, `period_date`, `currency_type`, `redeemed_count`, `redemption_rate` |
| `mv_liability_trend` | FR-08.5 | Append-on-each-cycle (snapshot accumulation) | `client_id`, `period_date`, `currency_type`, `total_unredeemed_balance` |
| `mv_failure_mode_breakdown` | FR-08.7 | Replaced on each scheduler cycle | `client_id`, `processing_mode`, `catalog_item_id`, `catalog_item_name`, `currency_type`, `partner_tier`, `region`, `period_date`, `failed_count`, `cancelled_count`, `total_count`, `failure_rate` |

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

### Permission matrix

| Permission | CLIENT_ADMIN | PLATFORM_ADMIN | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|
| `action.redemption.analytics.advanced` | Y | — | — | — |

### Permission resolution

Follows the platform's standard 5-layer resolution: Global default → Subscription tier → Client override → Role → User override. `CLIENT_ADMIN` inherits `action.redemption.analytics.advanced` via role assignment seeded in V11.

---

## DTOs

### Shared query parameters (not a DTO class — passed as `@RequestParam`)

| Param | Type | Required | Default | Notes |
|---|---|---|---|---|
| `dateFrom` | `LocalDate` (ISO 8601 `YYYY-MM-DD`) | No | Today − 30 days | Inclusive |
| `dateTo` | `LocalDate` (ISO 8601 `YYYY-MM-DD`) | No | Today | Inclusive |
| `tier` | `String` (comma-separated) | No | None | Multi-value partner tier filter |
| `region` | `String` (comma-separated) | No | None | Multi-value region filter |

### Response DTOs (all in `dto/response/redemption/`)

#### `ItemRedemptionDto`
```
catalogItemId       String
catalogItemName     String
currencyType        String
totalRedeemedCount  Long
totalRedeemedAmount BigDecimal
redemptionRate      Double
```

#### `ItemBreakdownResponse`
```
dateWindow      DateWindowDto
items           List<ItemRedemptionDto>
lastRefreshedAt Instant
```

#### `SegmentRedemptionDto`
```
partnerTier         String
region              String
role                String
currencyType        String
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

#### `TierTimeToRedemptionDto`
```
partnerTier                  String
avgHoursToFirstRedemption    Double
medianHoursToFirstRedemption Double
sampleCount                  Long
```

#### `TimeToFirstRedemptionResponse`
```
filters         Map<String, Object>
tiers           List<TierTimeToRedemptionDto>
lastRefreshedAt Instant
```

#### `TrendDataPointDto`
```
periodDate      LocalDate
currencyType    String
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
currencyType           String
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
processingMode  String
catalogItemId   String
catalogItemName String
currencyType    String
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
isStale                  boolean
stalenessThresholdHours  int
```

---

## API Endpoints

**Base path**: `/api/v1/redemption/analytics/advanced`
**Permission on all endpoints**: `@RequiresPermission("action.redemption.analytics.advanced")`

| Method | Path | FR | Response | Cache |
|---|---|---|---|---|
| GET | `/item-breakdown` | 08.1 | `ItemBreakdownResponse` | Redis 60s |
| GET | `/segment-breakdown` | 08.2 | `SegmentBreakdownResponse` | Redis 60s |
| GET | `/time-to-first-redemption` | 08.3 | `TimeToFirstRedemptionResponse` | Redis 60s |
| GET | `/trend` | 08.4 | `RedemptionTrendResponse` | Redis 60s |
| GET | `/liability-trend` | 08.5 | `LiabilityTrendResponse` | Redis 60s |
| GET | `/liability-trend/export` | 08.5 | CSV attachment | Not cached |
| GET | `/failure-breakdown` | 08.7 | `FailureBreakdownResponse` | Redis 60s |
| GET | `/refresh-status` | 08.8 | `AnalyticsRefreshStatusResponse` | Not cached |

All dimensional endpoints validate: `dateFrom ≤ dateTo`, span ≤ 365 days → 422 on violation.
Export endpoint: also rate-limited at 3 req/min per tenant (`AnalyticsExportRateLimiter`).

---

## Service Layer

### `RedemptionAdvancedAnalyticsService`

All methods `@Transactional(readOnly = true)`. Feature flag + date validation at top of each method.

### `AnalyticsMvRefreshScheduler`

`@Scheduled(fixedRateString = "${analytics.mv.refresh-interval-ms:900000}")` — every 15 min default.
Refreshes all 5 MVs via `REFRESH MATERIALIZED VIEW CONCURRENTLY`.
Appends snapshot row to `mv_liability_trend` via INSERT ON CONFLICT DO UPDATE.
Upserts `analytics_mv_refresh_log` after each successful refresh.

### Input validation

```
dateFrom default: today − 30 (UTC)
dateTo   default: today (UTC)
dateFrom > dateTo → BusinessRuleException (422)
span > 365 days  → BusinessRuleException (422)
```

---

## Security Design

| Concern | Implementation |
|---|---|
| Authentication | JWT bearer; `TenantValidator.getCurrentClientId()` |
| Authorization | `@RequiresPermission("action.redemption.analytics.advanced")` |
| Feature flag gate | `featureFlagService.isEnabled(...)` in service; 403 if false |
| Tenant isolation | `client_id` named param in every native query |
| Query rate limiting | `RateLimitFilter` 10 req/min per tenant |
| Export rate limiting | `AnalyticsExportRateLimiter` 3 req/min per tenant |
| PII prevention | MVs aggregate by dimension only — no user-level identifiers |
| CSV injection | `escapeCsv()` from `RedemptionAnalyticsService` reused |

---

## Audit Trail

| Trigger | action | resourceType | Metadata |
|---|---|---|---|
| Successful `GET /liability-trend/export` | `DATA_EXPORTED` | `REDEMPTION_ADVANCED_ANALYTICS_EXPORT` | `{tenantId, userId, dateFrom, dateTo, rowCount}` |

Written after CSV bytes built; 403/422/429 failures produce no audit row.

---

## Observability

Key log step keys: `advanced_analytics_query`, `advanced_analytics_export_downloaded`, `mv_refresh_completed`, `mv_refresh_failed`, `advanced_analytics_rate_limit_exceeded`, `advanced_analytics_feature_disabled`, `advanced_analytics_stale_data_served`.

Metrics: `redemption.advanced_analytics.query.duration` (Timer, tag: `endpoint`), `redemption.advanced_analytics.export.count` (Counter), `redemption.mv_refresh.duration` (Timer, tag: `mvName`).

---

## Frontend Specification

Tab bar added to `/redemption/admin/analytics`: **Overview** (existing) + **Advanced** (new).
Advanced tab hidden when permission or feature flag missing.

Filter bar: date range picker (presets + custom, 365-day max) + tier multi-select + region multi-select.

Sections: Item Breakdown (table) · Segment Breakdown (table) · Time to First Redemption (table) · Redemption Rate Trend (recharts LineChart) · Liability Trend (recharts LineChart + Export CSV button) · Failure Breakdown (table).

Refresh status: each section shows "Data as of {timestamp} UTC". `useRefreshStatus()` polls every 5 min. `isStale=true` → yellow banner at tab top, dismissible per session.

---

## Caching Strategy

| Layer | TTL | Notes |
|---|---|---|
| PostgreSQL MV | 15–30 min | Scheduler-driven |
| Redis `@Cacheable` | 60 s | All dimensional JSON responses |
| TanStack Query | 60 000 ms | FE in-memory |

Not cached: `/liability-trend/export`, `/refresh-status`.

---

## Data Retention

No PII stored. MVs contain aggregate metrics only. Existing entity retention policies unaffected — next MV refresh excludes anonymized/deleted records automatically.

---

## Edge Cases

12 edge cases documented in spec.md (empty tenant, empty tier, null refresh log, concurrent refresh + query, exactly-365-day export, future dateFrom, etc.).

---

## Acceptance Tests

11 acceptance tests (AT-08.1 through AT-08.11) covering: correct response shape, tier filter, TTFR N/A, 365-day cap enforcement, CSV columns + audit, rate limit 429, staleness detection, Starter 403, no audit on JSON query, failure breakdown by mode, null refresh log.

---

## Modified Existing Endpoints

None. F-07 endpoints unchanged.

---

## Out of Scope

Cohort comparisons · individual user drill-down · real-time streaming · Snowflake · ML analytics · internal admin analytics · self-serve MV refresh from UI.

---

## Verification Steps

14 verification steps listed in spec.md covering API shape, filter correctness, date cap, CSV export + audit, rate limit, staleness, flag gating, permission gating, concurrent MV refresh safety, FE tab visibility, staleness banner, export countdown.

---

### File: features/redemption-analytics-advanced/technical.md

> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.

(Full technical.md content is written verbatim to `features/redemption-analytics-advanced/technical.md`. See that file for complete Flyway DDL, package layout, native query shapes, FE package layout, hook specs, and audit annotation table.)
