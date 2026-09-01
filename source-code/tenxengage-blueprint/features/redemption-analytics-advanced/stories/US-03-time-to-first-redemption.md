---
id: US-03
title: "View time-to-first-redemption"
seed_id: "F-08.S-03"
layers: ["BE", "FE"]
touches_entities: ["mv_time_to_first_redemption"]
depends_on_stories: ["US-05"]
---

# US-03: View time-to-first-redemption

## Description

**Actor:** CLIENT_ADMIN

**Trigger:** CLIENT_ADMIN opens the Advanced tab.

**Steps:**
1. Advanced tab renders; Time-to-First-Redemption section is visible.
2. `useTimeToFirstRedemption(filters)` fires `GET /advanced/time-to-first-redemption`.
3. Section renders a table with one row per region showing average and median hours to first redemption.
4. Rows where `sampleCount=0` show "N/A" in the average and median columns.

**Expected outcome:** TTFR table shows per-region latency metrics; regions with no data display "N/A".

> **Note:** Originally specified per partner tier; regrouped to **region** because no per-partner tier exists in the data model (see spec FR-08.3). Region is the partner company's top-level location name; a partner with no location resolves to a `null` region (rendered "—").

**Negative paths:**
- Span > 365 days: 422.
- Missing permission: 403.
- No data: "No data for the selected period".

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/analytics/advanced/time-to-first-redemption` returns 200 `TimeToFirstRedemptionResponse` with `regions` array and `lastRefreshedAt`; `?region=APAC` filters to the APAC region's rows only.
- **AC-2:** A region row with `sampleCount=0` returns `avgHoursToFirstRedemption=null` and `medianHoursToFirstRedemption=null` from the BE; the FE renders "N/A" in those cells.
- **AC-3:** Span > 365 days → 422 "Date range must not exceed 365 days"; missing permission → 403; response cached 60s in Redis.
- **AC-4:** FE `TimeToFirstRedemptionTable` renders columns: Region, Avg Hours, Median Hours, Sample Count; "N/A" for null avg/median; "—" for null region; "Data as of {timestamp} UTC" caption; loading skeleton; "No data for the selected period" empty state; inline error + Retry button. _(⊕-2)_

---

## Out of Scope

- Segment breakdown by region/role (US-02) — TTFR only shows per-region latency, not redemption rates.
- Redemption rate trend (US-04).

---

## UI States

- [ ] **Loading:** Skeleton rows (3) while query in-flight.
- [ ] **Empty:** "No data for the selected period".
- [ ] **Error:** "Unable to load time-to-first-redemption data" + Retry button.
- [ ] **Partial:** Rows with `sampleCount=0` render "N/A" in Avg Hours and Median Hours cells — not hidden.

### Verbatim microcopy

- Section heading: "Time to First Redemption"
- Caption: "Data as of {date} at {time} UTC"
- Empty state: "No data for the selected period"
- Error message: "Unable to load time-to-first-redemption data"
- Null cell value: "N/A" (avg/median); "—" (region)
- Column headers: "Region", "Avg Hours", "Median Hours", "Sample Count"

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-05 (controller class must exist)

---

## Spec references

- `## Functional Requirements` — FR-08.3
- `## Data Model` — `mv_time_to_first_redemption` columns
- `## API Endpoints [BE + FE]` — `GET /api/v1/redemption/analytics/advanced/time-to-first-redemption`
- `## DTOs [BE]` — `RegionTimeToRedemptionDto`, `TimeToFirstRedemptionResponse`

---

## BE tasks [BE]

### BE-1: DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/response/redemption/RegionTimeToRedemptionDto.java`
- `src/main/java/com/tenxengage/app/dto/response/redemption/TimeToFirstRedemptionResponse.java`

`RegionTimeToRedemptionDto`: record — `String region` (nullable), `BigDecimal avgHoursToFirstRedemption` (nullable), `BigDecimal medianHoursToFirstRedemption` (nullable), `long sampleCount`.
`TimeToFirstRedemptionResponse`: record — `List<RegionTimeToRedemptionDto> regions`, `Instant lastRefreshedAt`.

Use `@Nullable` on the BigDecimal fields — Jackson serializes as JSON `null` (not 0) when sampleCount=0.

### BE-2: Service method + unit test

Add `getTimeToFirstRedemption(AdvancedAnalyticsFilter filter)` to `RedemptionAdvancedAnalyticsService`:
- Validates span ≤ 365 days
- Queries `mv_time_to_first_redemption` via `NamedParameterJdbcTemplate` (grouped by region)
- `@Cacheable(value="advanced-analytics-ttfr", key="#root.target.buildAdvancedCacheKey(#filter)")`

Unit tests: happy path with mixed null/non-null avg; sampleCount=0 → avgHours=null; region filter applied; span > 365 → exception.

### BE-3: Controller endpoint + @WebMvcTest

Add `GET /api/v1/redemption/analytics/advanced/time-to-first-redemption` to `RedemptionAdvancedAnalyticsController`.
@WebMvcTest: 200 with null avgHours; 422; 403.

---

## FE tasks [FE]

### FE-1: Types + service call

Add `RegionTimeToRedemptionDto`, `TimeToFirstRedemptionResponse` to `redemption-analytics-advanced.types.ts`.
Add `getTimeToFirstRedemption(filters)` to `redemption-analytics-advanced.service.ts`.

### FE-2: Hook

**File:** `src/hooks/redemption/useTimeToFirstRedemption.ts`

`staleTime: 60_000`. Query key: `['redemption-analytics-advanced', 'time-to-first-redemption', filters]`.

### FE-3: Component + Vitest test

**Files:**
- `src/components/analytics/advanced/TimeToFirstRedemptionTable.tsx`
- `src/components/analytics/advanced/__tests__/TimeToFirstRedemptionTable.test.tsx`

Renders columns per AC-4. When `avgHoursToFirstRedemption === null`, cell shows "N/A" (not "0" or blank); when `region === null`, cell shows "—".
Caption, loading skeleton, empty state, error + Retry.

Vitest: renders "N/A" for null avg/median; renders numeric values when non-null; shows empty state; shows error + Retry.

---

## E2E test [FE]

---

**Scenario 1:** `'TTFR table renders N/A for regions with zero sample count'` _(covers AC-1, AC-2, AC-4)_

**File:** `e2e/redemption-analytics-advanced/time-to-first-redemption.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for TTFR section → verify rows render; one EMEA region row has `sampleCount=0` |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/time-to-first-redemption` → 200 + `{"regions":[{"region":"APAC","avgHoursToFirstRedemption":24.5,"medianHoursToFirstRedemption":18.0,"sampleCount":120},{"region":"EMEA","avgHoursToFirstRedemption":null,"medianHoursToFirstRedemption":null,"sampleCount":0}],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | APAC row: avg shows "24.5"; EMEA row: avg and median cells show "N/A" |
| **Negative case** | — |

---

**Scenario 2:** `'TTFR empty state renders when no regions returned'` _(covers AC-4)_

**File:** `e2e/redemption-analytics-advanced/time-to-first-redemption.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for TTFR section |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/time-to-first-redemption` → 200 + `{"regions":[],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByText('No data for the selected period')).toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `RegionTimeToRedemptionDto.java` record created with nullable region + BigDecimal fields _(AC-2)_
- [ ] `TimeToFirstRedemptionResponse.java` record created (`regions` list) _(AC-1)_
- [ ] `getTimeToFirstRedemption(filter)` service method: span validation, `NamedParameterJdbcTemplate` query grouped by region, `@Cacheable` _(AC-1, AC-3)_
- [ ] `RedemptionAdvancedAnalyticsServiceTest`: sampleCount=0 → null avg/median; region filter; span > 365 _(AC-2, AC-3)_
- [ ] `GET /advanced/time-to-first-redemption` controller method added _(AC-1)_
- [ ] `RedemptionAdvancedAnalyticsControllerTest`: 200 with null; 422; 403 _(AC-3)_
- [ ] `./gradlew test` passes for new cases

**FE session:**
- [ ] `RegionTimeToRedemptionDto`, `TimeToFirstRedemptionResponse` types added _(AC-1, AC-2)_
- [ ] `getTimeToFirstRedemption(filters)` service call added _(AC-1)_
- [ ] `useTimeToFirstRedemption(filters)` hook: `staleTime:60_000` _(AC-3)_
- [ ] `TimeToFirstRedemptionTable.tsx`: "N/A" for null cells; "—" for null region; all columns; caption; loading; empty; error+Retry _(AC-4)_
- [ ] `TimeToFirstRedemptionTable.test.tsx` Vitest passes _(AC-2, AC-4)_
- [ ] E2E: Scenario 1 (N/A cells) passes _(AC-1, AC-2, AC-4)_
- [ ] E2E: Scenario 2 (empty state) passes _(AC-4)_

---

## Done when

1. **BE:** `./gradlew test` — TTFR service + controller cases green
2. **FE:** `npm run test` passes + E2E Scenarios 1–2 pass against real BE
3. Every AC (AC-1 through AC-4) referenced by at least one passing test
