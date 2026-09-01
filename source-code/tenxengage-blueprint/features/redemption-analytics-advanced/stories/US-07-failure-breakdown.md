---
id: US-07
title: "Failure breakdown"
seed_id: null
layers: ["BE", "FE"]
touches_entities: ["mv_failure_mode_breakdown"]
depends_on_stories: ["US-05"]
---

# US-07: Failure breakdown

## Description

**Actor:** CLIENT_ADMIN

**Trigger:** CLIENT_ADMIN opens the Advanced tab.

**Steps:**
1. Advanced tab renders; Failure Breakdown section is visible.
2. `useFailureBreakdown(filters)` fires `GET /advanced/failure-breakdown`.
3. Section renders a table with rows sorted by `failureRate` descending.
4. CLIENT_ADMIN applies a region filter → table re-fetches.

**Expected outcome:** Failure Breakdown table shows redemption failure and cancellation rates per catalog item, sorted by failure rate.

**Negative paths:**
- No failures for the period: "No data for the selected period".
- Missing permission: 403.

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/analytics/advanced/failure-breakdown` returns 200 `FailureBreakdownResponse` with `failureModes` sorted by `failureRate` descending and `lastRefreshedAt`.
- **AC-2:** Each row carries: `processingMode` (MANUAL | AUTOMATED), `catalogItemId`, `catalogItemName`, `currencyType`, `failedCount`, `cancelledCount`, `totalCount`, `failureRate` (decimal 0.0–1.0).
- **AC-3:** Region filter constrains results; span > 365 days → 422; missing permission → 403; cached 60s in Redis.
- **AC-4:** FE `FailureBreakdownTable` renders columns: Processing Mode, Item Name, Currency, Failed, Cancelled, Total, Failure Rate (%); table is sorted by Failure Rate (%) desc; "Data as of {timestamp} UTC" caption; loading skeleton; "No data for the selected period" empty state; inline error + Retry button. _(⊕-2)_

---

## Out of Scope

- Item breakdown (US-01) — different data semantics; failure breakdown groups by failure mode, not by redemption count.
- Liability export (US-06).
- Failure breakdown does NOT include audit trail (read-only endpoint per spec).

---

## UI States

- [ ] **Loading:** Skeleton rows (5) while query in-flight.
- [ ] **Empty:** "No data for the selected period".
- [ ] **Error:** "Unable to load failure breakdown" + Retry button.

### Verbatim microcopy

- Section heading: "Failure Breakdown"
- Caption: "Data as of {date} at {time} UTC"
- Empty state: "No data for the selected period"
- Error message: "Unable to load failure breakdown"
- Column headers: "Processing Mode", "Item Name", "Currency", "Failed", "Cancelled", "Total", "Failure Rate (%)"
- `processingMode` display values: "Manual" (for MANUAL), "Automated" (for AUTOMATED)

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-05 (controller class must exist)

---

## Spec references

- `## Functional Requirements` — FR-08.7
- `## Data Model` — `mv_failure_mode_breakdown` columns
- `## API Endpoints [BE + FE]` — `GET /api/v1/redemption/analytics/advanced/failure-breakdown`
- `## DTOs [BE]` — `FailureModeDto`, `FailureBreakdownResponse`
- `## Service Layer [BE]` — `getFailureBreakdown(filter)`

---

## BE tasks [BE]

### BE-1: DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/response/redemption/FailureModeDto.java`
- `src/main/java/com/tenxengage/app/dto/response/redemption/FailureBreakdownResponse.java`

`FailureModeDto`: record — `String processingMode`, `UUID catalogItemId`, `String catalogItemName`, `String currencyType`, `long failedCount`, `long cancelledCount`, `long totalCount`, `BigDecimal failureRate`.
`FailureBreakdownResponse`: record — `List<FailureModeDto> failureModes`, `Instant lastRefreshedAt`.

### BE-2: Service method + unit test

Add `getFailureBreakdown(AdvancedAnalyticsFilter filter)` to `RedemptionAdvancedAnalyticsService`:
- Validates span ≤ 365 days
- Queries `mv_failure_mode_breakdown` via `NamedParameterJdbcTemplate` with `client_id` + optional region; orders by `failure_rate DESC`
- `@Cacheable(value="advanced-analytics-failure-breakdown", key="#root.target.buildAdvancedCacheKey(#filter)")`

Unit tests: happy path sorted by failureRate desc; region filter applied; span > 365 → exception; empty result.

### BE-3: Controller endpoint + @WebMvcTest

Add `GET /api/v1/redemption/analytics/advanced/failure-breakdown` to `RedemptionAdvancedAnalyticsController`.
Params: `@RequestParam LocalDate dateFrom`, `@RequestParam LocalDate dateTo`, `@RequestParam(required=false) String region`.
Returns `ResponseEntity<FailureBreakdownResponse>`.

@WebMvcTest: 200 with rows sorted by failureRate desc; 422 span; 403 permission.

---

## FE tasks [FE]

### FE-1: Types + service call

Add `FailureModeDto`, `FailureBreakdownResponse` to `redemption-analytics-advanced.types.ts`.
Add `getFailureBreakdown(filters)` to `redemption-analytics-advanced.service.ts`.

### FE-2: Hook

**File:** `src/hooks/redemption/useFailureBreakdown.ts`

`staleTime: 60_000`. Query key: `['redemption-analytics-advanced', 'failure-breakdown', filters]`.

### FE-3: Component + Vitest test

**Files:**
- `src/components/analytics/advanced/FailureBreakdownTable.tsx`
- `src/components/analytics/advanced/__tests__/FailureBreakdownTable.test.tsx`

TanStack Table with columns per AC-4. Display `processingMode` as "Manual" or "Automated" (not raw enum value). Failure Rate (%) column formatted as percent with 1 decimal (e.g., "23.5%"). Default sort: Failure Rate desc (server-side — do not re-sort in FE).
Caption, loading skeleton, empty state, error + Retry.

Vitest: renders all columns with mock data; "Manual"/"Automated" display values; skeleton when loading; empty state; error + Retry.

---

## E2E test [FE]

---

**Scenario 1:** `'Failure breakdown table renders MANUAL and AUTOMATED rows sorted by failure rate desc'` _(covers AC-1, AC-2, AC-4)_

**File:** `e2e/redemption-analytics-advanced/failure-breakdown.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for Failure Breakdown section → verify rows render sorted by failure rate |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/failure-breakdown` → 200 + `{"failureModes":[{"processingMode":"MANUAL","catalogItemId":"...","catalogItemName":"Gold Ring","currencyType":"POINTS","failedCount":30,"cancelledCount":5,"totalCount":100,"failureRate":0.35},{"processingMode":"AUTOMATED","catalogItemName":"Silver Coin","failedCount":10,"cancelledCount":2,"totalCount":100,"failureRate":0.12}],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | First row shows "Manual" and "35.0%"; second row shows "Automated" and "12.0%"; `expect(page.getByText('Failure Breakdown')).toBeVisible()`; "Data as of" caption visible |
| **Negative case** | — |

---

**Scenario 2:** `'Failure breakdown empty state renders when no failure modes'` _(covers AC-4)_

**File:** `e2e/redemption-analytics-advanced/failure-breakdown.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for Failure Breakdown section |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/failure-breakdown` → 200 + `{"failureModes":[],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByText('No data for the selected period')).toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `FailureModeDto.java` and `FailureBreakdownResponse.java` records created _(AC-1, AC-2)_
- [ ] `getFailureBreakdown(filter)` service method: span validation, `NamedParameterJdbcTemplate` query ordered by `failure_rate DESC`, `@Cacheable` _(AC-1, AC-3)_
- [ ] `RedemptionAdvancedAnalyticsServiceTest`: happy path sorted; region filter; span > 365; empty result _(AC-1, AC-3)_
- [ ] `GET /advanced/failure-breakdown` controller method added _(AC-1)_
- [ ] `RedemptionAdvancedAnalyticsControllerTest`: 200 with sorted rows; 422; 403 _(AC-3)_
- [ ] `./gradlew test` passes for new cases

**FE session:**
- [ ] `FailureModeDto`, `FailureBreakdownResponse` types added _(AC-1, AC-2)_
- [ ] `getFailureBreakdown(filters)` service call added _(AC-1)_
- [ ] `useFailureBreakdown(filters)` hook: `staleTime:60_000` _(AC-3)_
- [ ] `FailureBreakdownTable.tsx`: all columns, "Manual"/"Automated" display, failure rate %, server-side sort, caption, loading, empty, error+Retry _(AC-4)_
- [ ] `FailureBreakdownTable.test.tsx` Vitest: columns; display values; skeleton; empty; error+Retry _(AC-2, AC-4)_
- [ ] E2E: Scenario 1 (happy path + sort) passes _(AC-1, AC-2, AC-4)_
- [ ] E2E: Scenario 2 (empty state) passes _(AC-4)_

---

## Done when

1. **BE:** `./gradlew test` — failure breakdown service + controller cases green
2. **FE:** `npm run test` passes + E2E Scenarios 1–2 pass against real BE
3. Every AC (AC-1 through AC-4) referenced by at least one passing test
