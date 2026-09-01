---
id: US-04
title: "View redemption rate trend"
seed_id: "F-08.S-04"
layers: ["BE", "FE"]
touches_entities: ["mv_redemption_rate_trend"]
depends_on_stories: ["US-05"]
---

# US-04: View redemption rate trend

## Description

**Actor:** CLIENT_ADMIN

**Trigger:** CLIENT_ADMIN opens the Advanced tab.

**Steps:**
1. Advanced tab renders; Redemption Rate Trend section is visible.
2. `useRedemptionTrend(dateFrom, dateTo)` fires `GET /advanced/trend?dateFrom=X&dateTo=Y`.
3. Section renders a recharts `LineChart` with one line per currency type, X-axis = date, Y-axis = redemption rate %.
4. Clicking a preset (e.g., "Last 7 days") updates `dateFrom`/`dateTo` and re-fetches.
5. Hovering a data point shows a tooltip with `redeemedCount` and `redemptionRate`.

**Expected outcome:** Trend chart shows redemption rate over time per currency with correct date axis.

**Negative paths:**
- Custom range > 365 days: FE inline error before request sent; BE 422 if reached.
- Empty data: "No data for the selected period".
- Missing permission: 403.

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/analytics/advanced/trend?dateFrom=X&dateTo=Y` returns 200 `RedemptionTrendResponse` with `dataPoints` array ordered by `periodDate ASC`; each point has `periodDate`, `currencyType`, `redeemedCount`, `totalIssued`, `redemptionRate`.
- **AC-2:** Preset buttons (Last 7 days, Last 30 days, Last 90 days) set `dateFrom` = today minus N days and `dateTo` = today; custom range > 365 days → FE inline error "Date range cannot exceed 365 days" shown before the request is sent; same invalid span reaching BE → 422.
- **AC-3:** Missing permission → 403; response cached 60s in Redis (trend endpoint — not the export).
- **AC-4:** FE `RedemptionTrendChart` renders a recharts `LineChart`; X-axis = formatted date; Y-axis = redemption rate %; one line per distinct `currencyType`; tooltip on hover shows `redeemedCount` and `redemptionRate`; "Data as of {timestamp} UTC" caption; loading skeleton; "No data for the selected period" empty state; inline error + Retry button. _(⊕-2)_

---

## Out of Scope

- Liability trend chart (US-06) — different MV and separate section.
- Region/role filtering (trend query is tenant-wide with no region/role segment per spec FR-08.4).
- Chart export to PNG/SVG (not in spec).

---

## UI States

- [ ] **Loading:** Skeleton placeholder (rectangle) at chart height while query in-flight.
- [ ] **Empty:** "No data for the selected period".
- [ ] **Error:** "Unable to load redemption rate trend" + Retry button.

### Verbatim microcopy

- Section heading: "Redemption Rate Trend"
- Caption: "Data as of {date} at {time} UTC"
- Empty state: "No data for the selected period"
- Error message: "Unable to load redemption rate trend"
- Tooltip labels: "Redeemed: {count}", "Rate: {rate}%"
- Y-axis label: "Rate (%)"

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-05

---

## Spec references

- `## Functional Requirements` — FR-08.4
- `## Data Model` — `mv_redemption_rate_trend` columns
- `## API Endpoints [BE + FE]` — `GET /api/v1/redemption/analytics/advanced/trend`
- `## DTOs [BE]` — `TrendDataPointDto`, `RedemptionTrendResponse`

---

## BE tasks [BE]

### BE-1: DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/response/redemption/TrendDataPointDto.java`
- `src/main/java/com/tenxengage/app/dto/response/redemption/RedemptionTrendResponse.java`

`TrendDataPointDto`: record — `LocalDate periodDate`, `String currencyType`, `long redeemedCount`, `long totalIssued`, `BigDecimal redemptionRate`.
`RedemptionTrendResponse`: record — `List<TrendDataPointDto> dataPoints`, `Instant lastRefreshedAt`.

### BE-2: Service method + unit test

Add `getRedemptionTrend(LocalDate dateFrom, LocalDate dateTo)` to `RedemptionAdvancedAnalyticsService`:
- Validates span ≤ 365 days → `BusinessRuleException`
- Queries `mv_redemption_rate_trend` via `NamedParameterJdbcTemplate` with `client_id` + date range; orders by `period_date ASC, currency_type ASC`
- `@Cacheable(value="advanced-analytics-trend", key="#root.target.buildTrendCacheKey(#dateFrom, #dateTo)")`

Unit tests: happy path ordered correctly; span > 365 → exception; empty result.

### BE-3: Controller endpoint + @WebMvcTest

Add `GET /api/v1/redemption/analytics/advanced/trend` to `RedemptionAdvancedAnalyticsController`.
Params: `@RequestParam LocalDate dateFrom`, `@RequestParam LocalDate dateTo`.
@WebMvcTest: 200 with data points in order; 422 span; 403 permission.

---

## FE tasks [FE]

### FE-1: Types + service call

Add `TrendDataPointDto`, `RedemptionTrendResponse` to `redemption-analytics-advanced.types.ts`.
Add `getRedemptionTrend(dateFrom, dateTo)` to `redemption-analytics-advanced.service.ts`.

### FE-2: Hook

**File:** `src/hooks/redemption/useRedemptionTrend.ts`

`staleTime: 60_000`. Query key: `['redemption-analytics-advanced', 'trend', dateFrom, dateTo]`.

### FE-3: Component + Vitest test

**Files:**
- `src/components/analytics/advanced/RedemptionTrendChart.tsx`
- `src/components/analytics/advanced/__tests__/RedemptionTrendChart.test.tsx`

Recharts `LineChart` with `ResponsiveContainer`. Group `dataPoints` by `currencyType` to build one `<Line>` per currency. X-axis: `periodDate` formatted as "MMM d". Y-axis: redemption rate as percent. Custom `<Tooltip>` renders "Redeemed: {redeemedCount}" and "Rate: {redemptionRate}%".

Loading: rectangular skeleton at chart height. Empty: "No data for the selected period". Error: error message + Retry.

Vitest: renders chart with mocked data (verify Line count = distinct currency count); shows skeleton when loading; shows empty state; mocks recharts to avoid canvas errors in jsdom.

---

## E2E test [FE]

---

**Scenario 1:** `'Trend chart renders with one line per currency type'` _(covers AC-1, AC-4)_

**File:** `e2e/redemption-analytics-advanced/redemption-trend.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for Redemption Rate Trend section → verify chart renders |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/trend` → 200 + `{"dataPoints":[{"periodDate":"2026-05-21","currencyType":"POINTS","redeemedCount":10,"totalIssued":100,"redemptionRate":0.10},{"periodDate":"2026-05-22","currencyType":"POINTS","redeemedCount":15,"totalIssued":100,"redemptionRate":0.15}],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByText('Redemption Rate Trend')).toBeVisible()`; `expect(page.getByText('Data as of')).toBeVisible()`; recharts SVG `<line>` element present |
| **Negative case** | — |

---

**Scenario 2:** `'Last 7 days preset sets correct date params in request'` _(covers AC-2)_

**File:** `e2e/redemption-analytics-advanced/redemption-trend.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → click "Last 7 days" preset in filter bar → intercept outgoing request and verify query params |
| **APIs to mock via `page.route()`** | intercept `GET /api/v1/redemption/analytics/advanced/trend` → capture request URL → return 200 + empty dataPoints |
| **Visible assertion** | Captured request URL contains `dateFrom` = today-7 formatted as YYYY-MM-DD |
| **Negative case** | — |

---

**Scenario 3:** `'Trend empty state renders when no data points'` _(covers AC-4)_

**File:** `e2e/redemption-analytics-advanced/redemption-trend.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for Trend section |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/trend` → 200 + `{"dataPoints":[],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByText('No data for the selected period')).toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `TrendDataPointDto.java` and `RedemptionTrendResponse.java` records created _(AC-1)_
- [ ] `getRedemptionTrend(dateFrom, dateTo)` service method: span validation, `NamedParameterJdbcTemplate` query ordered by date ASC, `@Cacheable` _(AC-1, AC-3)_
- [ ] `RedemptionAdvancedAnalyticsServiceTest`: happy path ordered ASC; span > 365; empty result _(AC-1, AC-3)_
- [ ] `GET /advanced/trend` controller method added _(AC-1)_
- [ ] `RedemptionAdvancedAnalyticsControllerTest`: 200; 422; 403 _(AC-2, AC-3)_
- [ ] `./gradlew test` passes for new cases

**FE session:**
- [ ] `TrendDataPointDto`, `RedemptionTrendResponse` types added _(AC-1)_
- [ ] `getRedemptionTrend(dateFrom, dateTo)` service call added _(AC-1)_
- [ ] `useRedemptionTrend(dateFrom, dateTo)` hook: `staleTime:60_000` _(AC-3)_
- [ ] `RedemptionTrendChart.tsx`: recharts LineChart, one Line per currency, X-axis formatted date, Y-axis rate %, custom tooltip, caption _(AC-4)_
- [ ] `RedemptionTrendChart.test.tsx` Vitest: chart renders; skeleton; empty state; mocked recharts _(AC-4)_
- [ ] E2E: Scenario 1 (chart renders) passes _(AC-1, AC-4)_
- [ ] E2E: Scenario 2 (preset params) passes _(AC-2)_
- [ ] E2E: Scenario 3 (empty state) passes _(AC-4)_

---

## Done when

1. **BE:** `./gradlew test` — trend service + controller cases green
2. **FE:** `npm run test` passes + E2E Scenarios 1–3 pass against real BE
3. Every AC (AC-1 through AC-4) referenced by at least one passing test
