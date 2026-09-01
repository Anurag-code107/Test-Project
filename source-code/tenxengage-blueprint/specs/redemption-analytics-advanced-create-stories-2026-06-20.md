# Stories Plan: redemption-analytics-advanced

## Feature
- **Spec**: `features/redemption-analytics-advanced/spec.md` (status: reviewed)
- **Stories folder**: `features/redemption-analytics-advanced/stories/`

## Flow-level Completeness Audit (Phase 1.5)

| # | Gap | Resolution | Story / Note |
|---|---|---|---|
| 1 | Tab default state not specified (which tab is active on page load) | AC added | AC-2 added to US-05 |
| 2 | Section loading and error states not defined per section | AC added | AC-6/AC-4/AC-4/AC-4/AC-6/AC-4 added to US-01 through US-07 |
| 3 | Tier/region filter dropdowns empty when tenant has no redemption data | AC added | AC-4 added to US-05 |

---

## Foundation tasks

| ID | Title | Deps | Key files (2–3 paths) | Done-when |
|----|-------|------|-----------------------|-----------|
| F0 | Contracts (FIRST) | — | `../tenxengage-contracts/` | `/generate-contracts redemption-analytics-advanced` completes |
| F1 | Enums | F0 | `entity/enums/AuditResourceType.java` | `./gradlew compileJava` — `REDEMPTION_ADVANCED_ANALYTICS_EXPORT` resolves |
| F2 | Flyway migrations | F1 | `db/migration/V10__create_advanced_analytics_materialized_views.sql`, `db/migration/V11__seed_advanced_analytics_permissions.sql` | `./gradlew flywayMigrate` applies cleanly; 5 MVs, `mv_liability_trend` table, `analytics_mv_refresh_log` table, and permission/flag rows exist |
| F3 | Test fixtures | F2 | `test/.../testdata/AdvancedAnalyticsFixtures.java` | `./gradlew compileTestJava` — fixture helpers insert rows into `mv_item_redemption_breakdown`, `mv_segment_redemption_breakdown`, `mv_time_to_first_redemption`, `mv_redemption_rate_trend`, `mv_liability_trend`, `mv_failure_mode_breakdown`, `analytics_mv_refresh_log` |
| F4 | Permissions + feature flags seed | F2 | `db/migration/V11__seed_advanced_analytics_permissions.sql` (included in F2 above) | `action.redemption.analytics.advanced` row visible in DB; `redemption_analytics_advanced` flag row: starter=false, professional=true, enterprise=true |
| F5 | MV refresh scheduler | F3, F4 | `service/redemption/AnalyticsMvRefreshScheduler.java`, `test/.../service/redemption/AnalyticsMvRefreshSchedulerTest.java` | Scheduler compiles; unit test verifies `REFRESH MATERIALIZED VIEW CONCURRENTLY` is called for all 5 MVs; liability trend INSERT ON CONFLICT issued; `analytics_mv_refresh_log` upserted per MV; `mv_refresh_failed` log on exception |

---

## Story index

| ID | Title | Layers | Seed | Touches | Depends on | Parallel with (FE) | Story File |
|----|-------|--------|------|---------|------------|-------------------|-----------|
| US-01 | View item breakdown | BE + FE | F-08.S-01 | `mv_item_redemption_breakdown` | Foundation, US-05 | US-02, US-03, US-04, US-06, US-07 | [stories/US-01-item-breakdown.md](stories/US-01-item-breakdown.md) |
| US-02 | View segment breakdown | BE + FE | F-08.S-02 | `mv_segment_redemption_breakdown` | Foundation, US-05 | US-01, US-03, US-04, US-06, US-07 | [stories/US-02-segment-breakdown.md](stories/US-02-segment-breakdown.md) |
| US-03 | View time-to-first-redemption | BE + FE | F-08.S-03 | `mv_time_to_first_redemption` | Foundation, US-05 | US-01, US-02, US-04, US-06, US-07 | [stories/US-03-time-to-first-redemption.md](stories/US-03-time-to-first-redemption.md) |
| US-04 | View redemption rate trend | BE + FE | F-08.S-04 | `mv_redemption_rate_trend` | Foundation, US-05 | US-01, US-02, US-03, US-06, US-07 | [stories/US-04-redemption-trend.md](stories/US-04-redemption-trend.md) |
| US-05 | Advanced tab shell, filter bar, refresh status | BE + FE | F-08.S-05 | `analytics_mv_refresh_log` | Foundation, F5 | — | [stories/US-05-tab-shell-filter-refresh.md](stories/US-05-tab-shell-filter-refresh.md) |
| US-06 | Liability trend chart and CSV export | BE + FE | null | `mv_liability_trend` | Foundation, US-05 | US-01, US-02, US-03, US-04, US-07 | [stories/US-06-liability-trend-export.md](stories/US-06-liability-trend-export.md) |
| US-07 | Failure breakdown | BE + FE | null | `mv_failure_mode_breakdown` | Foundation, US-05 | US-01, US-02, US-03, US-04, US-06 | [stories/US-07-failure-breakdown.md](stories/US-07-failure-breakdown.md) |

> **BE sessions for US-01 through US-04, US-06, US-07 are sequential** — all add methods to the same `RedemptionAdvancedAnalyticsController` and `RedemptionAdvancedAnalyticsService`. FE sessions are independently parallelizable (different hooks/components).

---

## Dependency graph

```
F0 (contracts)
└── F1 (enums)
    └── F2 (migrations: V10 + V11)
        ├── F3 (test fixtures)        ← no JPA entities; fixtures insert rows directly
        │   └── F5 (scheduler)
        └── F4 (permissions seed)    ← V11 included in F2; F4 = verification step
            └── US-05 (tab shell + filter bar + refresh status)
                ├── US-01 (item breakdown)
                ├── US-02 (segment breakdown)
                ├── US-03 (TTFR)
                ├── US-04 (trend chart)
                ├── US-06 (liability trend + export)
                └── US-07 (failure breakdown)
```

---

## Per-story capsules

### US-05 — Advanced tab shell, filter bar, refresh status
- **Layers:** BE + FE
- **Seed:** F-08.S-05
- **Trigger:** CLIENT_ADMIN navigates to `/redemption/admin/analytics`
- **Steps:** Page renders Overview tab by default → Advanced tab visible when permission + flag → CLIENT_ADMIN clicks Advanced → filter bar, staleness banner (if stale), and section placeholders render
- **Acceptance Criteria:**
  - AC-1: Advanced tab is rendered only when user holds `action.redemption.analytics.advanced` AND `redemption_analytics_advanced=true`; otherwise tab is absent from DOM
  - AC-2: On initial page load, Overview tab is active by default _(⊕-1)_
  - AC-3: Filter bar: date range presets (Last 7d/30d/90d) + custom picker (365-day max); custom range > 365 days → inline error "Date range cannot exceed 365 days" + Apply disabled
  - AC-4: When segment breakdown returns empty rows, tier and region multi-selects render disabled with placeholder "No data available" _(⊕-3)_
  - AC-5: `GET /api/v1/redemption/analytics/advanced/refresh-status` returns 200 `AnalyticsRefreshStatusResponse`; 403 when permission missing
  - AC-6: `isStale=true` OR `lastRefreshedAt=null` → yellow banner "Analytics data may be outdated. Last refreshed: {timestamp} UTC." with ✕ dismiss (session-only, not localStorage)
  - AC-7: Banner auto-disappears when `isStale` transitions to `false` on next poll
- **Out of Scope:** Section data loading (each section is its own story); export functionality (US-06)
- **UI states:** Loading: skeleton tab bar; Empty filter options: disabled dropdowns per AC-4; Error on `/refresh-status`: silent fail (no banner shown — don't block the tab)
- **Verbatim microcopy:** Staleness banner: "Analytics data may be outdated. Last refreshed: {date} at {time} UTC."; Filter placeholder: "No data available"; Date validation: "Date range cannot exceed 365 days"
- **Conditional rendering:** `redemption_analytics_advanced=false` OR no permission → Advanced tab absent; `isStale=true` → yellow banner visible
- **E2E scenarios:**
  - S1 happy _(covers AC-1, AC-2, AC-5, AC-6)_: Navigate as CLIENT_ADMIN with flag enabled → Overview active → click Advanced → tab renders → mock `/refresh-status` with `isStale=true` → staleness banner visible → ✕ dismiss → banner gone
  - S2 permission gate _(covers AC-1)_: Log in as Starter tenant (flag=false) → Advanced tab absent from DOM
- **BE task intents:** `AnalyticsRefreshStatusResponse.java` record; `RedemptionAdvancedAnalyticsService.getRefreshStatus()` → queries `analytics_mv_refresh_log`, returns min `last_refreshed_at` across all MVs; `RedemptionAdvancedAnalyticsController` class created with `GET /refresh-status` method + `@RequiresPermission("action.redemption.analytics.advanced")`; feature flag check in service; `RedemptionAdvancedAnalyticsServiceTest`, `RedemptionAdvancedAnalyticsControllerTest`
- **FE task intents:** `redemption-analytics-advanced.types.ts` (types from contracts); `redemption-analytics-advanced.service.ts` (service calls); `useRefreshStatus.ts` (poll 5 min); `AdvancedAnalyticsTab.tsx` (tab container with Tabs); `AdvancedFilterBar.tsx` (date + tier + region); `StalenessBanner.tsx` (dismissible)
- **Done when:** `./gradlew test` passes; `npm run test` passes; E2E S1+S2 pass against real BE

---

### US-01 — View item breakdown
- **Layers:** BE + FE
- **Seed:** F-08.S-01
- **Trigger:** CLIENT_ADMIN opens Advanced tab with default filters (Last 30 days, no tier/region filter)
- **Steps:** Advanced tab renders → Item Breakdown section fetches `/item-breakdown` → table populates sorted by `totalRedeemedCount` desc
- **Acceptance Criteria:**
  - AC-1: `GET /item-breakdown?dateFrom=X&dateTo=Y` returns 200 `ItemBreakdownResponse` with items sorted by `totalRedeemedCount` descending
  - AC-2: Each item row has `catalogItemId`, `catalogItemName`, `currencyType`, `totalRedeemedCount`, `totalRedeemedAmount`, `redemptionRate`; response includes `lastRefreshedAt`
  - AC-3: `?tier=GOLD&region=APAC` constrains results to GOLD-tier APAC redemptions; span > 365 days → 422 "Date range must not exceed 365 days"; missing permission → 403
  - AC-4: Response is Redis-cached 60s keyed `{clientId}:item-breakdown:{dateFrom}:{dateTo}:{tier}:{region}`
  - AC-5: FE ItemBreakdownTable renders columns (Item Name, Currency, Redeemed Count, Amount, Rate %), sorted by Redeemed Count desc; section shows "Data as of {timestamp} UTC" caption _(FR-08.8)_
  - AC-6: Loading → skeleton rows; empty results → "No data for the selected period"; query error → inline error message + Retry button _(⊕-2)_
- **Out of Scope:** Failure breakdown by item (US-07); tab shell and filter bar (US-05)
- **E2E scenarios:**
  - S1 happy path _(covers AC-1, AC-2, AC-5)_: Open Advanced tab → Item Breakdown section → mock `/item-breakdown` → table rows render with correct columns sorted by count desc
  - S2 filter + 422 _(covers AC-3)_: Set custom date range > 365 days → Apply → inline error "Date range cannot exceed 365 days" shown; request not sent
  - S3 empty state _(covers AC-6)_: Mock `/item-breakdown` returning empty `items` → "No data for the selected period"
- **BE task intents:** `ItemRedemptionDto.java`, `ItemBreakdownResponse.java` records; `getItemBreakdown(filter)` service method querying `mv_item_redemption_breakdown` via `NamedParameterJdbcTemplate`; `@Cacheable("advanced-analytics-item-breakdown")`; `GET /item-breakdown` controller method; date validation (span ≤ 365); service + controller tests
- **FE task intents:** `useItemBreakdown(filters)` hook (`staleTime: 60_000`); `ItemBreakdownTable.tsx` (TanStack Table, sortable); Vitest test
- **Done when:** `./gradlew test` passes; `npm run test` passes; E2E S1–S3 pass

---

### US-02 — View segment breakdown
- **Layers:** BE + FE
- **Seed:** F-08.S-02
- **Trigger:** CLIENT_ADMIN views segment breakdown table in Advanced tab
- **Steps:** Advanced tab → Segment Breakdown section fetches `/segment-breakdown` → table populates by tier × region × role × currency
- **Acceptance Criteria:**
  - AC-1: `GET /segment-breakdown` returns 200 `SegmentBreakdownResponse` with segment rows
  - AC-2: `?tier=GOLD` constrains all rows to `partnerTier=GOLD`; `?tier=GOLD&region=APAC` applies AND semantics
  - AC-3: Span > 365 days → 422; missing permission → 403; cached 60s in Redis
  - AC-4: FE SegmentBreakdownTable renders Partner Tier, Region, Role, Currency, Redeemed Count, Redemption Rate (%) columns; "Data as of {timestamp} UTC" caption; loading skeleton; "No data for the selected period" empty state; error + Retry _(⊕-2)_
- **Out of Scope:** Filter bar itself (US-05); time-to-first-redemption (US-03)
- **E2E scenarios:**
  - S1 happy + tier filter _(covers AC-1, AC-2, AC-4)_: Open Advanced tab → Segment Breakdown → select tier=GOLD in filter bar → mock `/segment-breakdown?tier=GOLD` → all rows show "GOLD"
  - S2 empty state _(covers AC-4)_: Mock empty `segments` → "No data for the selected period"
- **BE task intents:** `SegmentRedemptionDto.java`, `SegmentBreakdownResponse.java` records; `getSegmentBreakdown(filter)` service method on `mv_segment_redemption_breakdown`; `@Cacheable`; `GET /segment-breakdown` controller method; service + controller tests
- **FE task intents:** `useSegmentBreakdown(filters)` hook; `SegmentBreakdownTable.tsx`; Vitest test
- **Done when:** `./gradlew test` passes; `npm run test` passes; E2E S1–S2 pass

---

### US-03 — View time-to-first-redemption
- **Layers:** BE + FE
- **Seed:** F-08.S-03
- **Trigger:** CLIENT_ADMIN views TTFR table in Advanced tab
- **Acceptance Criteria:**
  - AC-1: `GET /time-to-first-redemption` returns 200 `TimeToFirstRedemptionResponse` with per-tier rows; `?tier=GOLD` filters to GOLD tier only
  - AC-2: A tier with `sampleCount=0` returns `avgHoursToFirstRedemption=null` and `medianHoursToFirstRedemption=null`; FE renders "N/A" in those cells
  - AC-3: Span > 365 days → 422; missing permission → 403; cached 60s
  - AC-4: FE TimeToFirstRedemptionTable renders Partner Tier, Avg Hours, Median Hours, Sample Count; "N/A" for null avg/median; "Data as of" caption; loading skeleton; "No data for the selected period" empty state; error + Retry _(⊕-2)_
- **Out of Scope:** Segment breakdown (US-02); trend chart (US-04)
- **E2E scenarios:**
  - S1 happy _(covers AC-1, AC-2, AC-4)_: Mock `/time-to-first-redemption` with one tier having `sampleCount=0` → that row shows "N/A" in avg/median columns
  - S2 empty state _(covers AC-4)_: Mock empty `tiers` → "No data for the selected period"
- **BE task intents:** `TierTimeToRedemptionDto.java`, `TimeToFirstRedemptionResponse.java` records; `getTimeToFirstRedemption(filter)` service method on `mv_time_to_first_redemption`; `@Cacheable`; `GET /time-to-first-redemption` controller method; service + controller tests
- **FE task intents:** `useTimeToFirstRedemption(filters)` hook; `TimeToFirstRedemptionTable.tsx`; Vitest test
- **Done when:** `./gradlew test` passes; `npm run test` passes; E2E S1–S2 pass

---

### US-04 — View redemption rate trend
- **Layers:** BE + FE
- **Seed:** F-08.S-04
- **Trigger:** CLIENT_ADMIN views trend chart in Advanced tab
- **Acceptance Criteria:**
  - AC-1: `GET /trend?dateFrom=X&dateTo=Y` returns 200 `RedemptionTrendResponse` with one `TrendDataPointDto` per calendar day per currency type, ordered by `periodDate` ASC
  - AC-2: Preset buttons (Last 7d, Last 30d, Last 90d) set correct `dateFrom`/`dateTo`; custom range > 365 days → FE inline error before request is sent; same span sent to BE → 422
  - AC-3: Missing permission → 403; response cached 60s
  - AC-4: FE RedemptionTrendChart renders recharts `LineChart`; X-axis = date, Y-axis = redemption rate %; one line per currency type; tooltip shows `redeemedCount` + `redemptionRate` on hover; "Data as of" caption; loading skeleton; "No data for the selected period" empty state; error + Retry _(⊕-2)_
- **Out of Scope:** Liability trend (US-06); segment filter on trend (trend is tenant-wide, no tier/region)
- **E2E scenarios:**
  - S1 happy _(covers AC-1, AC-4)_: Open Advanced tab → trend chart → mock `/trend` with 30 data points → LineChart renders with correct lines
  - S2 date preset _(covers AC-2)_: Click "Last 7 days" → verify `dateFrom` param = today-7 in intercepted request
  - S3 empty state _(covers AC-4)_: Mock empty `dataPoints` → "No data for the selected period"
- **BE task intents:** `TrendDataPointDto.java`, `RedemptionTrendResponse.java` records; `getRedemptionTrend(dateFrom, dateTo)` service on `mv_redemption_rate_trend`; `@Cacheable`; `GET /trend` controller; service + controller tests
- **FE task intents:** `useRedemptionTrend(dateFrom, dateTo)` hook; `RedemptionTrendChart.tsx` (recharts); Vitest test
- **Done when:** `./gradlew test` passes; `npm run test` passes; E2E S1–S3 pass

---

### US-06 — Liability trend chart and CSV export
- **Layers:** BE + FE
- **Seed:** null (no seed — FR-08.5 was not in planning seeds)
- **Trigger:** CLIENT_ADMIN views liability trend in Advanced tab and optionally exports CSV
- **Acceptance Criteria:**
  - AC-1: `GET /liability-trend` returns 200 `LiabilityTrendResponse` with one `LiabilityDataPointDto` per `period_date` per `currency_type`, ordered by `periodDate` ASC
  - AC-2: `GET /liability-trend/export` returns 200 CSV `Content-Disposition: attachment; filename="redemption-liability-trend.csv"` with header `period_date,currency_type,total_unredeemed_balance`
  - AC-3: Every successful export (200) generates audit log entry `action=DATA_EXPORTED, resourceType=REDEMPTION_ADVANCED_ANALYTICS_EXPORT` with `{tenantId, userId, dateFrom, dateTo, rowCount}`; no entry on 422/429/403
  - AC-4: 4th export within 60s → 429 with `Retry-After` header; no audit entry
  - AC-5: Span > 365 days → 422 on both `/liability-trend` and `/liability-trend/export`; missing permission → 403
  - AC-6: FE LiabilityTrendChart renders recharts `LineChart`; Export CSV button triggers download; 429 → button disabled with countdown; loading skeleton; "No data for the selected period" empty state; error + Retry _(⊕-2)_
- **Out of Scope:** Item/segment/failure breakdowns; trend chart (US-04)
- **Non-Functional Notes:**
  - **Audit:** `exportLiabilityTrend()` calls `auditLogService.logAsync()` AFTER CSV bytes built — never before (failed ops must not leak audit)
  - **Rate limit:** Reuses `AnalyticsExportRateLimiter` (3 req/min per tenant); export endpoint bypasses Redis cache
- **E2E scenarios:**
  - S1 JSON view _(covers AC-1, AC-6)_: Mock `/liability-trend` → chart renders with correct lines; "Data as of" caption present
  - S2 CSV export happy _(covers AC-2, AC-3)_: Click Export CSV → verify download filename + CSV header + audit log row written
  - S3 rate limit _(covers AC-4, AC-6)_: Mock 3 exports then 429 response → Export button disabled with countdown timer visible
  - S4 date cap _(covers AC-5)_: Custom range > 365 days → inline error shown; request not sent
- **BE task intents:** `LiabilityDataPointDto.java`, `LiabilityTrendResponse.java` records; `getLiabilityTrend(dateFrom, dateTo)` + `exportLiabilityTrend(dateFrom, dateTo)` service methods; `escapeCsv()` reused from `RedemptionAnalyticsService`; audit via `auditLogService.logAsync()`; `GET /liability-trend` + `GET /liability-trend/export` controller methods; `AnalyticsExportRateLimiter` applied on export endpoint; service + controller tests
- **FE task intents:** `useLiabilityTrend(dateFrom, dateTo)` hook; `LiabilityTrendChart.tsx` (recharts + export button with rate-limit countdown); Vitest test
- **Done when:** `./gradlew test` passes; `npm run test` passes; E2E S1–S4 pass; audit DB row verified in integration test

---

### US-07 — Failure breakdown
- **Layers:** BE + FE
- **Seed:** null
- **Trigger:** CLIENT_ADMIN views failure breakdown table in Advanced tab
- **Acceptance Criteria:**
  - AC-1: `GET /failure-breakdown` returns 200 `FailureBreakdownResponse` with rows sorted by `failureRate` descending
  - AC-2: Each row has `processingMode` (MANUAL|AUTOMATED), `catalogItemId`, `catalogItemName`, `currencyType`, `failedCount`, `cancelledCount`, `totalCount`, `failureRate`; response includes `lastRefreshedAt`
  - AC-3: Tier/region filters constrain results; span > 365 days → 422; missing permission → 403; cached 60s
  - AC-4: FE FailureBreakdownTable renders Processing Mode, Item Name, Currency, Failed, Cancelled, Total, Failure Rate (%) columns sorted by rate desc; "Data as of" caption; loading skeleton; "No data for the selected period"; error + Retry _(⊕-2)_
- **Out of Scope:** Item breakdown (US-01); liability export (US-06)
- **E2E scenarios:**
  - S1 happy _(covers AC-1, AC-2, AC-4)_: Mock `/failure-breakdown` with MANUAL + AUTOMATED rows → both appear; sorted by failure rate desc
  - S2 empty state _(covers AC-4)_: Mock empty `failureModes` → "No data for the selected period"
- **BE task intents:** `FailureModeDto.java`, `FailureBreakdownResponse.java` records; `getFailureBreakdown(filter)` service on `mv_failure_mode_breakdown`; `@Cacheable`; `GET /failure-breakdown` controller method; service + controller tests
- **FE task intents:** `useFailureBreakdown(filters)` hook; `FailureBreakdownTable.tsx`; Vitest test
- **Done when:** `./gradlew test` passes; `npm run test` passes; E2E S1–S2 pass

---

## Test plan highlights

**Business Rule Enforcement:**
- `AdvancedAnalyticsIntegrationTest`: span=365 days → 200; span=366 days → 422 "Date range must not exceed 365 days" (both JSON and export endpoints)
- `AdvancedAnalyticsIntegrationTest`: Feature flag `redemption_analytics_advanced=false` → 403 on all `/advanced/**` endpoints

**Contract Conformance:**
- `AdvancedAnalyticsContractConformanceTest`: all 8 `GET /advanced/**` response shapes match OpenAPI contract; 422 error shape matches `ErrorResponse` contract; 429 shape matches rate-limit contract

**Tenant Isolation & Security:**
- `AdvancedAnalyticsIntegrationTest`: Unauthenticated `GET /advanced/item-breakdown` → 401
- `AdvancedAnalyticsIntegrationTest`: Tenant A's data not visible to Tenant B (query with Tenant B JWT returns empty list, not Tenant A's rows)
- `AdvancedAnalyticsIntegrationTest`: CLIENT_ADMIN role → 200; PARTNER_ADMIN role → 403 on all `/advanced/**`

**Audit & Events:**
- `AdvancedAnalyticsIntegrationTest`: Successful `/liability-trend/export` → audit row `DATA_EXPORTED / REDEMPTION_ADVANCED_ANALYTICS_EXPORT` written with correct metadata
- `AdvancedAnalyticsIntegrationTest`: Failed export (422 → date too wide) → NO audit row written
- `AdvancedAnalyticsIntegrationTest`: Failed export (429 → rate limit) → NO audit row written

**Query Correctness at Scale:**
- `AdvancedAnalyticsMvQueryIT`: Query `mv_item_redemption_breakdown` with client_id filter against 10 tenants of seeded data → only rows for requesting tenant returned

**E2E Cross-Story (Real Stack):**
- `e2e/redemption-analytics-advanced/full-happy-path.spec.ts`: Open Advanced tab → apply tier filter → all 6 sections update → verify data visible against real BE → export CSV → verify download

---

## Story count summary

| Total | BE + FE | FE-only | BE-only |
|-------|---------|---------|---------|
| 7     | 7       | 0       | 0       |
