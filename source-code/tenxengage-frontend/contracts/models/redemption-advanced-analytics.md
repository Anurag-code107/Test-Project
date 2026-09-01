# Redemption Advanced Analytics DTOs

Read-model DTOs returned by the `RedemptionAdvancedAnalyticsController` at
`/api/v1/redemption/analytics/advanced/**` (F-08, Phase 2). All responses are pure aggregate
projections over PostgreSQL materialized views plus the `analytics_mv_refresh_log` tracking
table — **no new JPA entities are introduced**. Dimensional JSON responses are Redis-cached
(TTL 60 s, keyed `{clientId}:{endpoint}:{dateFrom}:{dateTo}:{region}:{role}`). Per NFR-2, no
individual user identifiers appear in any field — every metric is grouped by dimension.

`clientId` is never accepted or returned; tenant isolation is resolved server-side from the JWT
and bound as a named parameter in every native MV query.

---

## Shared

### DateWindowDto

Echoes the effective date window applied to the query. Reuses the F-07 shape (see
[redemption-analytics-summary](redemption-analytics-summary.md)).

| Field | Type | Notes |
|---|---|---|
| `from` | LocalDate | Start of the selected window (inclusive) |
| `to` | LocalDate | End of the selected window (inclusive) |

Every dimensional response also carries `lastRefreshedAt` (`Instant`) — the timestamp of the
most recent MV refresh, surfaced for the "Data as of {timestamp} UTC" caption (FR-08.8).

---

## ItemBreakdownResponse (FR-08.1)

`GET /item-breakdown`. Catalog-item redemption breakdown, ranked by `totalRedeemedCount`
descending. Empty `items` indicates no redemption data for the filters.

| Field | Type | Notes |
|---|---|---|
| `dateWindow` | DateWindowDto | Effective date window |
| `items` | List\<ItemRedemptionDto\> | One entry per catalog item with redemptions in the window |
| `lastRefreshedAt` | Instant | Most recent MV refresh timestamp |

### ItemRedemptionDto

| Field | Type | Notes |
|---|---|---|
| `catalogItemId` | UUID | Catalog item identifier |
| `catalogItemName` | String | Catalog item display name |
| `currencyId` | String | Platform currency identifier (`CASH`, `POINTS`, `CREDITS`, `TICKETS`) |
| `totalRedeemedCount` | Long | Total completed redemptions for this item in the window |
| `totalRedeemedAmount` | BigDecimal | Total redeemed amount (string representation in JSON) |
| `redemptionRate` | Double | Redemption rate as a percentage (0–100) |

---

## SegmentBreakdownResponse (FR-08.2)

`GET /segment-breakdown`. One row per unique (region × role × currency) segment with at least
one redemption. Zero-count segments are omitted. Partner-tier segmentation was dropped — no
per-partner tier exists in the data model (FR-08.2 note).

| Field | Type | Notes |
|---|---|---|
| `dateWindow` | DateWindowDto | Effective date window |
| `segments` | List\<SegmentRedemptionDto\> | One entry per segment |
| `lastRefreshedAt` | Instant | Most recent MV refresh timestamp |

### SegmentRedemptionDto

| Field | Type | Notes |
|---|---|---|
| `region` | String | Partner company's top-level location. **Null** when partner has no location (FE renders "—") |
| `role` | String | `client_roles.base_role_name`. **Null** when user has no client role (FE renders "—") |
| `currencyId` | String | Platform currency identifier |
| `totalRedeemedCount` | Long | Total completed redemptions for this segment |
| `totalRedeemedAmount` | BigDecimal | Total redeemed amount (string representation in JSON) |
| `redemptionRate` | Double | Redemption rate as a percentage (0–100) |

---

## TimeToFirstRedemptionResponse (FR-08.3)

`GET /time-to-first-redemption`. Mean/median hours from partner account creation to first
COMPLETED `RedemptionRequest`, segmented by region. Uses current profile attributes at time of
analysis (not point-in-time). `dateFrom`/`dateTo` scope which partner cohort is included by join
date. Originally segmented by partner tier; regrouped to region — no per-partner tier exists in
the data model (FR-08.3 note).

| Field | Type | Notes |
|---|---|---|
| `filters` | Map\<String, Object\> | Active region filter values applied to the query |
| `regions` | List\<RegionTimeToRedemptionDto\> | One entry per region |
| `lastRefreshedAt` | Instant | Most recent MV refresh timestamp |

### RegionTimeToRedemptionDto

| Field | Type | Notes |
|---|---|---|
| `region` | String | Partner company's top-level location. **Null** when partner has no location |
| `avgHoursToFirstRedemption` | Double | Mean hours to first redemption. **Null** when `sampleCount = 0` (FE renders "N/A") |
| `medianHoursToFirstRedemption` | Double | Median hours to first redemption. **Null** when `sampleCount = 0` |
| `sampleCount` | Long | Partners in this region with at least one completed redemption |

---

## RedemptionTrendResponse (FR-08.4)

`GET /trend`. Tenant-wide redemption rate time series, one data point per calendar day per
currency type. No tier/region filter.

| Field | Type | Notes |
|---|---|---|
| `dateWindow` | DateWindowDto | Effective date window |
| `dataPoints` | List\<TrendDataPointDto\> | One entry per (calendar day × currency type) |
| `lastRefreshedAt` | Instant | Most recent MV refresh timestamp |

### TrendDataPointDto

| Field | Type | Notes |
|---|---|---|
| `periodDate` | LocalDate | Calendar day for this data point |
| `currencyId` | String | Platform currency identifier |
| `redeemedCount` | Long | Total redeemed count on this day |
| `redemptionRate` | Double | Redemption rate as a percentage (0–100) on this day |

---

## LiabilityTrendResponse (FR-08.5)

`GET /liability-trend` (JSON) and `GET /liability-trend/export` (CSV). Unredeemed balance
liability (available + reserved) at each period-end data point, per currency type. The CSV export
columns are `period_date,currency_type,total_unredeemed_balance` and each successful export emits
an audit record (`DATA_EXPORTED` / `REDEMPTION_ADVANCED_ANALYTICS_EXPORT`, FR-08.10).

| Field | Type | Notes |
|---|---|---|
| `dateWindow` | DateWindowDto | Effective date window |
| `dataPoints` | List\<LiabilityDataPointDto\> | One entry per (period-end day × currency type) |
| `lastRefreshedAt` | Instant | Most recent MV refresh timestamp |

### LiabilityDataPointDto

| Field | Type | Notes |
|---|---|---|
| `periodDate` | LocalDate | Period-end calendar day |
| `currencyId` | String | Platform currency identifier |
| `totalUnredeemedBalance` | BigDecimal | Total unredeemed balance (string representation in JSON) |

---

## FailureBreakdownResponse (FR-08.7)

`GET /failure-breakdown`. Failed/cancelled redemption metrics by processing mode and catalog
item. Default sort is `failureRate` descending.

| Field | Type | Notes |
|---|---|---|
| `dateWindow` | DateWindowDto | Effective date window |
| `failureModes` | List\<FailureModeDto\> | One entry per (processing mode × catalog item) |
| `lastRefreshedAt` | Instant | Most recent MV refresh timestamp |

### FailureModeDto

| Field | Type | Notes |
|---|---|---|
| `processingMode` | String (RedemptionProcessingMode) | `INSTANT`, `BATCH`, or `APPROVAL_REQUIRED` |
| `catalogItemId` | UUID | Catalog item identifier |
| `catalogItemName` | String | Catalog item display name |
| `currencyId` | String | Platform currency identifier |
| `failedCount` | Long | Count of FAILED redemptions in the window |
| `cancelledCount` | Long | Count of CANCELLED redemptions in the window |
| `totalCount` | Long | Total redemptions for this combination in the window |
| `failureRate` | Double | Failure rate as a percentage (0–100) — (failed + cancelled) / total |

> **Spec discrepancy (resolved):** The spec DTO labelled `processingMode` as `MANUAL | AUTOMATED`
> and claimed those values exist on `RedemptionRequest.processingMode`. They do not — neither the
> BRD nor the codebase defines them. The BRD's three processing modes and the F-03
> `RedemptionProcessingMode` enum are the source of truth: `INSTANT`, `BATCH`, `APPROVAL_REQUIRED`.
> The contract uses the canonical values; the spec should be corrected upstream.

---

## AnalyticsRefreshStatusResponse (FR-08.8 / FR-08.11)

`GET /refresh-status`. Live read of `analytics_mv_refresh_log` — not cached.

| Field | Type | Notes |
|---|---|---|
| `lastRefreshedAt` | Instant | Most recent successful MV refresh. **Null** when no refresh has run yet (first deploy) |
| `isStale` | boolean | True when `now − lastRefreshedAt > 4 hours`, or when `lastRefreshedAt` is null |
| `stalenessThresholdHours` | int | Always `4`; included for the client to render a countdown |

---

## Permissions

All endpoints require `action.redemption.analytics.advanced` (CLIENT_ADMIN only) **in addition
to** the F-07 `action.redemption.view_analytics`, and are gated by the
`redemption_analytics_advanced` feature flag (checked in the service layer; 403 if disabled for
the tenant's subscription tier).

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Tenant isolation — resolved server-side from JWT; bound as a named parameter in every native MV query |
| Any per-user identifier | NFR-2 — advanced analytics expose only aggregate metrics grouped by dimension |

## Data Sources

| Source | Usage |
|---|---|
| `mv_item_redemption_breakdown` | ItemBreakdownResponse (FR-08.1) |
| `mv_segment_redemption_breakdown` | SegmentBreakdownResponse (FR-08.2) |
| `mv_time_to_first_redemption` | TimeToFirstRedemptionResponse (FR-08.3) |
| `mv_redemption_rate_trend` | RedemptionTrendResponse (FR-08.4) |
| `mv_liability_trend` | LiabilityTrendResponse + CSV export (FR-08.5) |
| `mv_failure_mode_breakdown` | FailureBreakdownResponse (FR-08.7) |
| `analytics_mv_refresh_log` | AnalyticsRefreshStatusResponse (FR-08.8 / FR-08.11) |
