---
id: US-06
title: "Liability trend chart and CSV export"
seed_id: null
layers: ["BE", "FE"]
touches_entities: ["mv_liability_trend"]
depends_on_stories: ["US-05"]
---

# US-06: Liability trend chart and CSV export

## Description

**Actor:** CLIENT_ADMIN

**Trigger:** CLIENT_ADMIN opens the Advanced tab.

**Steps:**
1. Advanced tab renders; Liability Trend section is visible.
2. `useLiabilityTrend(dateFrom, dateTo)` fires `GET /advanced/liability-trend`.
3. Section renders a recharts `LineChart` with one line per currency type showing unredeemed balance over time.
4. CLIENT_ADMIN clicks "Export CSV" → browser downloads `redemption-liability-trend.csv`.
5. After export, an audit log entry is written with `DATA_EXPORTED / REDEMPTION_ADVANCED_ANALYTICS_EXPORT`.

**Expected outcome:** Liability trend chart and export button functional; audit logged on success; rate limit enforced on repeated exports.

**Negative paths:**
- 4th export within 60 seconds: 429 with `Retry-After`; Export button disabled with countdown.
- Span > 365 days: 422 on both JSON and export endpoints.
- Missing permission: 403.

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/analytics/advanced/liability-trend` returns 200 `LiabilityTrendResponse` with `dataPoints` ordered by `periodDate ASC`; each point has `periodDate`, `currencyType`, `totalUnredeemedBalance`.
- **AC-2:** `GET /api/v1/redemption/analytics/advanced/liability-trend/export` returns 200 with header `Content-Disposition: attachment; filename="redemption-liability-trend.csv"` and UTF-8 CSV body with header `period_date,currency_type,total_unredeemed_balance` followed by one data row per `mv_liability_trend` row for the requesting tenant.
- **AC-3:** Every successful export (200 response) writes an audit log entry: `action=DATA_EXPORTED`, `resourceType=REDEMPTION_ADVANCED_ANALYTICS_EXPORT`, metadata includes `{tenantId, userId, dateFrom, dateTo, rowCount}`; no audit entry is written for 422, 429, or 403 responses.
- **AC-4:** 4th export request within 60 seconds → 429 with `Retry-After` header; FE Export button is disabled for the duration of the countdown timer.
- **AC-5:** Span > 365 days → 422 "Date range must not exceed 365 days" on both `/liability-trend` (JSON) and `/liability-trend/export`; missing permission → 403 on both.
- **AC-6:** FE `LiabilityTrendChart` renders a recharts `LineChart` with one line per currency type; Export CSV button triggers file download; 429 response disables the button and shows a countdown "Retry in {N}s"; "Data as of {timestamp} UTC" caption; loading skeleton; "No data for the selected period" empty state; inline error + Retry button for the chart (distinct from Export button). _(⊕-2)_

---

## Out of Scope

- Redemption rate trend (US-04) — different MV and different data semantics.
- Item/segment/failure breakdowns (US-01, US-02, US-07).
- Real-time streaming of balance changes (spec explicitly excludes real-time updates for this chart).

---

## Non-Functional Notes

- **Audit:** `exportLiabilityTrend()` calls `auditLogService.logAsync()` AFTER the CSV byte array is built — never before. A failed export (exception thrown during CSV build) must not produce an audit entry.
- **Cache:** `/liability-trend` JSON endpoint IS cached 60s. `/liability-trend/export` is NOT cached — always serves a live snapshot.
- **Rate limit:** `AnalyticsExportRateLimiter` (3 req/min per tenant) is applied to the export endpoint only; the JSON chart endpoint uses `RateLimitFilter` (10 req/min).

---

## UI States

- [ ] **Loading:** Rectangular skeleton at chart height while `useLiabilityTrend` in-flight.
- [ ] **Empty:** "No data for the selected period".
- [ ] **Error (chart):** "Unable to load liability trend" + Retry button (re-triggers `useLiabilityTrend`).
- [ ] **Export 429:** Export button disabled; countdown text "Retry in {N}s" next to button; button re-enables when countdown reaches 0.
- [ ] **Export loading:** Export button shows spinner/disabled state while download in-flight.

### Verbatim microcopy

- Section heading: "Liability Trend"
- Caption: "Data as of {date} at {time} UTC"
- Empty state: "No data for the selected period"
- Error message: "Unable to load liability trend"
- Export button (idle): "Export CSV"
- Export button (rate-limited): "Retry in {N}s"
- Y-axis label: "Unredeemed Balance"

### Conditional rendering

**Input: export request result**
- 200 (success): Export button re-enables immediately; file download initiated.
- 429 (rate limited): Export button disabled; countdown from `Retry-After` value in seconds.
- Error (non-429): Export button re-enables; toast "Export failed — please try again" (brief, auto-dismiss).

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-05 (controller class must exist)

---

## Spec references

- `## Functional Requirements` — FR-08.5, FR-08.9, FR-08.10
- `## Data Model` — `mv_liability_trend` columns
- `## API Endpoints [BE + FE]` — `GET /advanced/liability-trend`, `GET /advanced/liability-trend/export`
- `## DTOs [BE]` — `LiabilityDataPointDto`, `LiabilityTrendResponse`
- `## Service Layer [BE]` — `getLiabilityTrend()`, `exportLiabilityTrend()` + audit call
- `## Audit Trail [BE]` — `DATA_EXPORTED / REDEMPTION_ADVANCED_ANALYTICS_EXPORT`
- `## Security Design [BE]` — `AnalyticsExportRateLimiter` (3 req/min); `/liability-trend/export` NOT cached; `escapeCsv()` for formula injection prevention

---

## BE tasks [BE]

### BE-1: DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/response/redemption/LiabilityDataPointDto.java`
- `src/main/java/com/tenxengage/app/dto/response/redemption/LiabilityTrendResponse.java`

`LiabilityDataPointDto`: record — `LocalDate periodDate`, `String currencyType`, `BigDecimal totalUnredeemedBalance`.
`LiabilityTrendResponse`: record — `List<LiabilityDataPointDto> dataPoints`, `Instant lastRefreshedAt`.

### BE-2: Service methods + unit tests

Add two methods to `RedemptionAdvancedAnalyticsService`:

**`getLiabilityTrend(LocalDate dateFrom, LocalDate dateTo)`:**
- Validates span ≤ 365 days
- Queries `mv_liability_trend` via `NamedParameterJdbcTemplate` with `client_id` + date range; orders by `period_date ASC, currency_type ASC`
- `@Cacheable(value="advanced-analytics-liability-trend", key="#root.target.buildTrendCacheKey(#dateFrom, #dateTo)")`

**`exportLiabilityTrend(LocalDate dateFrom, LocalDate dateTo)`:**
- Validates span ≤ 365 days
- Queries all rows for the client within date range (no cache — fresh snapshot)
- Builds UTF-8 CSV: header `period_date,currency_type,total_unredeemed_balance` + one row per data point
- Applies `escapeCsv()` (reuse pattern from `RedemptionAnalyticsService`) to all string fields
- Calls `auditLogService.logAsync(DATA_EXPORTED, REDEMPTION_ADVANCED_ANALYTICS_EXPORT, ...)` AFTER CSV built
- Returns `byte[]`

Unit tests: `getLiabilityTrend` — happy path ordered; span > 365; empty. `exportLiabilityTrend` — CSV header present; data rows correct; `escapeCsv` applied; audit called after CSV built; audit NOT called when span > 365 throws before reaching audit line.

### BE-3: Controller endpoints + @WebMvcTest

Add two methods to `RedemptionAdvancedAnalyticsController`:

`GET /api/v1/redemption/analytics/advanced/liability-trend` → `ResponseEntity<LiabilityTrendResponse>`.
`GET /api/v1/redemption/analytics/advanced/liability-trend/export` → `ResponseEntity<byte[]>` with `Content-Disposition: attachment; filename="redemption-liability-trend.csv"` and `Content-Type: text/csv; charset=UTF-8`. Apply `@RateLimited("AnalyticsExportRateLimiter")` on the export method.

@WebMvcTest: JSON endpoint: 200; 422; 403. Export endpoint: 200 with CSV content-type; 422; 429 when rate-limited; 403.

### BE-4: Audit annotation

The export controller method DOES NOT use `@Audited` annotation — audit is emitted inside the service after CSV is built (not via annotation) to ensure 422/429/403 paths produce no audit entries.

Verify in `RedemptionAdvancedAnalyticsControllerTest` that the service's `auditLogService.logAsync()` is called with `DATA_EXPORTED` and `REDEMPTION_ADVANCED_ANALYTICS_EXPORT` on 200 path; NOT called when service throws `BusinessRuleException` (422 path).

---

## FE tasks [FE]

### FE-1: Types + service calls

Add `LiabilityDataPointDto`, `LiabilityTrendResponse` to `redemption-analytics-advanced.types.ts`.
Add `getLiabilityTrend(dateFrom, dateTo)` and `exportLiabilityTrendCsv(dateFrom, dateTo): Promise<Blob>` to `redemption-analytics-advanced.service.ts`. Export service call: parse `Retry-After` header from 429 and rethrow with the seconds value.

### FE-2: Hook

**File:** `src/hooks/redemption/useLiabilityTrend.ts`

`staleTime: 60_000`. Query key: `['redemption-analytics-advanced', 'liability-trend', dateFrom, dateTo]`.

### FE-3: Component + Vitest test

**Files:**
- `src/components/analytics/advanced/LiabilityTrendChart.tsx`
- `src/components/analytics/advanced/__tests__/LiabilityTrendChart.test.tsx`

Recharts `LineChart` with `ResponsiveContainer`. One `<Line>` per `currencyType`. X-axis = `periodDate` formatted "MMM d". Y-axis = "Unredeemed Balance". Export CSV button with rate-limit countdown state (`retryAfterSeconds` local state). Caption, loading skeleton, empty state, chart error + Retry.

When export returns 429: set `retryAfterSeconds` from `Retry-After` header → countdown timer (decrement per second via `setInterval`) → button re-enables at 0.

Vitest: renders chart; Export button present; 429 disables button and shows countdown; countdown expires and button re-enables (use `vi.useFakeTimers()`); shows empty state.

---

## E2E test [FE]

---

**Scenario 1:** `'Liability trend chart renders with one line per currency'` _(covers AC-1, AC-6)_

**File:** `e2e/redemption-analytics-advanced/liability-trend.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for Liability Trend section → verify chart and caption |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/liability-trend` → 200 + `{"dataPoints":[{"periodDate":"2026-06-01","currencyType":"POINTS","totalUnredeemedBalance":1200.50},{"periodDate":"2026-06-02","currencyType":"POINTS","totalUnredeemedBalance":1150.00}],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByText('Liability Trend')).toBeVisible()`; `expect(page.getByText('Data as of')).toBeVisible()`; Export CSV button visible |
| **Negative case** | — |

---

**Scenario 2:** `'Export CSV triggers download and rate limit disables button'` _(covers AC-2, AC-4, AC-6)_

**File:** `e2e/redemption-analytics-advanced/liability-trend.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → click Export CSV (mock 200 with CSV bytes) → verify download; click Export CSV 3 more times (mock 429 on 4th) → verify button disabled with countdown |
| **APIs to mock via `page.route()`** | First 3 calls to `GET /advanced/liability-trend/export` → 200 + CSV bytes; 4th call → 429 with `Retry-After: 45` |
| **Visible assertion** | After 200: file download initiated (check via `page.waitForEvent('download')`); after 429: `expect(page.getByRole('button',{name:/Retry in/})).toBeDisabled()` |
| **Negative case** | — |

---

**Scenario 3:** `'Liability trend empty state renders when no data points'` _(covers AC-6)_

**File:** `e2e/redemption-analytics-advanced/liability-trend.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for Liability Trend section |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/liability-trend` → 200 + `{"dataPoints":[],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByText('No data for the selected period')).toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `LiabilityDataPointDto.java` and `LiabilityTrendResponse.java` records created _(AC-1)_
- [ ] `getLiabilityTrend(dateFrom, dateTo)` service method: span validation, `NamedParameterJdbcTemplate` query ordered by date ASC, `@Cacheable` _(AC-1, AC-5)_
- [ ] `exportLiabilityTrend(dateFrom, dateTo)` service method: CSV build with header, `escapeCsv()`, `auditLogService.logAsync()` after CSV built _(AC-2, AC-3)_
- [ ] `RedemptionAdvancedAnalyticsServiceTest`: getLiabilityTrend happy + span > 365; exportLiabilityTrend CSV header present, audit called after build, audit NOT called on span > 365 _(AC-2, AC-3, AC-5)_
- [ ] `GET /advanced/liability-trend` controller method + `GET /advanced/liability-trend/export` method with `@RateLimited("AnalyticsExportRateLimiter")` _(AC-1, AC-2, AC-4)_
- [ ] `RedemptionAdvancedAnalyticsControllerTest`: JSON 200; JSON 422; export 200 with CSV content-type; export 422; export 429; export 403 _(AC-4, AC-5)_
- [ ] `./gradlew test` passes for new cases

**FE session:**
- [ ] `LiabilityDataPointDto`, `LiabilityTrendResponse` types added _(AC-1)_
- [ ] `getLiabilityTrend(dateFrom, dateTo)` and `exportLiabilityTrendCsv(dateFrom, dateTo)` service calls added; 429 rethrows with `Retry-After` seconds _(AC-2, AC-4)_
- [ ] `useLiabilityTrend(dateFrom, dateTo)` hook: `staleTime:60_000` _(AC-5)_
- [ ] `LiabilityTrendChart.tsx`: recharts LineChart, one Line per currency, Export button, rate-limit countdown, caption, loading, empty, chart error+Retry _(AC-6)_
- [ ] `LiabilityTrendChart.test.tsx` Vitest: chart renders; Export present; 429 countdown; countdown expires re-enables button; empty state _(AC-4, AC-6)_
- [ ] E2E: Scenario 1 (chart) passes _(AC-1, AC-6)_
- [ ] E2E: Scenario 2 (export + rate limit) passes _(AC-2, AC-4, AC-6)_
- [ ] E2E: Scenario 3 (empty state) passes _(AC-6)_

---

## Done when

1. **BE:** `./gradlew test` — liability trend service + controller cases green; audit integration test verifies no entry on 422 (can be run as part of T1 in test-plan.md)
2. **FE:** `npm run test` passes + E2E Scenarios 1–3 pass against real BE
3. Every AC (AC-1 through AC-6) referenced by at least one passing test
