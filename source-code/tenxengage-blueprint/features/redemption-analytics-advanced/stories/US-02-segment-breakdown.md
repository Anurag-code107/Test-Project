---
id: US-02
title: "View segment breakdown"
seed_id: "F-08.S-02"
layers: ["BE", "FE"]
touches_entities: ["mv_segment_redemption_breakdown"]
depends_on_stories: ["US-05"]
---

# US-02: View segment breakdown

## Description

**Actor:** CLIENT_ADMIN

**Trigger:** CLIENT_ADMIN opens the Advanced tab with default filters.

**Steps:**
1. Advanced tab renders; Segment Breakdown section is visible.
2. `useSegmentBreakdown(filters)` fires `GET /advanced/segment-breakdown`.
3. Section renders a table grouped by region × role × currency type.
4. CLIENT_ADMIN selects region=APAC in the filter bar → table re-fetches with `?region=APAC`.

**Expected outcome:** Segment Breakdown table shows redemption distribution by partner region and role.

> **Note:** Originally specified to include partner tier; tier was dropped because no per-partner tier exists in the data model (see spec FR-08.2). Segments are now region × role × currency. Region = partner company's top-level location name; role = `client_roles.base_role_name`. Either can be `null` (no location / no client role) and renders "—".

**Negative paths:**
- No segments for the period: "No data for the selected period".
- Missing permission: 403.

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/analytics/advanced/segment-breakdown` returns 200 `SegmentBreakdownResponse` with `segments` array and `lastRefreshedAt`.
- **AC-2:** `?region=APAC` constrains all returned rows to `region='APAC'`; `?region=APAC&role=MANAGER` applies AND semantics; span > 365 days → 422; missing permission → 403; cached 60s in Redis.
- **AC-3:** Each segment row carries: `region` (nullable), `role` (nullable), `currencyId`, `totalRedeemedCount`, `redemptionRate` (percentage 0–100, same scale as item-breakdown/trend per the contract).
- **AC-4:** FE `SegmentBreakdownTable` renders columns: Region, Role, Currency, Redeemed Count, Redemption Rate (%); null region/role render "—"; "Data as of {timestamp} UTC" caption; loading skeleton; "No data for the selected period" empty state; inline error + Retry button. _(⊕-2)_

---

## Out of Scope

- Time-to-first-redemption (US-03).
- Filter bar implementation (US-05).
- Segment data is NOT paginated — all rows for the client within the date window are returned.

---

## UI States

- [ ] **Loading:** Skeleton rows (5) while query in-flight.
- [ ] **Empty:** "No data for the selected period".
- [ ] **Error:** "Unable to load segment breakdown" + Retry button.

### Verbatim microcopy

- Section heading: "Segment Breakdown"
- Caption: "Data as of {date} at {time} UTC"
- Empty state: "No data for the selected period"
- Error message: "Unable to load segment breakdown"
- Null region/role cell: "—"
- Column headers: "Region", "Role", "Currency", "Redeemed Count", "Redemption Rate (%)"

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-05 (controller class must exist)

---

## Spec references

- `## Functional Requirements` — FR-08.2
- `## Data Model` — `mv_segment_redemption_breakdown` columns
- `## API Endpoints [BE + FE]` — `GET /api/v1/redemption/analytics/advanced/segment-breakdown`
- `## DTOs [BE]` — `SegmentRedemptionDto`, `SegmentBreakdownResponse`
- `## Service Layer [BE]` — `getSegmentBreakdown(filter)`

---

## BE tasks [BE]

### BE-1: DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/response/redemption/SegmentRedemptionDto.java`
- `src/main/java/com/tenxengage/app/dto/response/redemption/SegmentBreakdownResponse.java`

`SegmentRedemptionDto`: record — `String region` (nullable), `String role` (nullable), `String currencyId`, `long totalRedeemedCount`, `BigDecimal redemptionRate` (percentage 0–100).
`SegmentBreakdownResponse`: record — `List<SegmentRedemptionDto> segments`, `Instant lastRefreshedAt`.

### BE-2: Service method + unit test

**Files:** `RedemptionAdvancedAnalyticsService.java` (add method), `RedemptionAdvancedAnalyticsServiceTest.java` (add cases)

`getSegmentBreakdown(AdvancedAnalyticsFilter filter)`:
- Validates span ≤ 365 days
- Queries `mv_segment_redemption_breakdown` via `NamedParameterJdbcTemplate` with `client_id` + optional region/role predicates
- `@Cacheable(value="advanced-analytics-segment-breakdown", key="#root.target.buildAdvancedCacheKey(#filter)")`

Unit tests: happy path; region=APAC filter; role filter; empty result; span > 365 → exception.

### BE-3: Controller endpoint + @WebMvcTest

Add `GET /api/v1/redemption/analytics/advanced/segment-breakdown` to `RedemptionAdvancedAnalyticsController`.
Params: `@RequestParam LocalDate dateFrom`, `@RequestParam LocalDate dateTo`, `@RequestParam(required=false) String region`, `@RequestParam(required=false) String role`.
@WebMvcTest: 200 happy; 422 span; 403 permission.

---

## FE tasks [FE]

### FE-1: Types + service call

Add `SegmentRedemptionDto`, `SegmentBreakdownResponse` to `redemption-analytics-advanced.types.ts`.
Add `getSegmentBreakdown(filters)` to `redemption-analytics-advanced.service.ts`.

### FE-2: Hook

**File:** `src/hooks/redemption/useSegmentBreakdown.ts`

`staleTime: 60_000`. Query key: `['redemption-analytics-advanced', 'segment-breakdown', filters]`.

### FE-3: Component + Vitest test

**Files:**
- `src/components/analytics/advanced/SegmentBreakdownTable.tsx`
- `src/components/analytics/advanced/__tests__/SegmentBreakdownTable.test.tsx`

TanStack Table with columns per AC-4 (null region/role → "—"). "Data as of" caption. Loading skeleton. Empty state. Error + Retry.

Vitest: renders all columns with mock data; renders "—" for null region/role; skeleton when loading; empty state text; error + Retry.

---

## E2E test [FE]

---

**Scenario 1:** `'Segment breakdown table renders with region filter applied'` _(covers AC-1, AC-2, AC-3, AC-4)_

**File:** `e2e/redemption-analytics-advanced/segment-breakdown.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → select region=APAC in filter bar → click Apply → wait for Segment Breakdown section |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/segment-breakdown?*region=APAC*` → 200 + `{"segments":[{"region":"APAC","role":"MANAGER","currencyId":"POINTS","totalRedeemedCount":42,"redemptionRate":35.0}],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByRole('cell',{name:'APAC'})).toBeVisible()`; "Data as of" caption visible |
| **Negative case** | — |

---

**Scenario 2:** `'Segment breakdown empty state renders when no segments returned'` _(covers AC-4)_

**File:** `e2e/redemption-analytics-advanced/segment-breakdown.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for Segment Breakdown section |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/segment-breakdown` → 200 + `{"segments":[],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByText('No data for the selected period')).toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `SegmentRedemptionDto.java` and `SegmentBreakdownResponse.java` records created _(AC-1, AC-3)_
- [ ] `getSegmentBreakdown(filter)` service method: span validation, `NamedParameterJdbcTemplate` query (region/role predicates), `@Cacheable` _(AC-2)_
- [ ] `RedemptionAdvancedAnalyticsServiceTest`: happy path, region filter, role filter, span > 365, empty result _(AC-1, AC-2)_
- [ ] `GET /advanced/segment-breakdown` controller method added _(AC-1, AC-2)_
- [ ] `RedemptionAdvancedAnalyticsControllerTest`: 200 happy; 422; 403 _(AC-2)_
- [ ] `./gradlew test` passes for new cases

**FE session:**
- [ ] `SegmentRedemptionDto`, `SegmentBreakdownResponse` types added _(AC-1, AC-3)_
- [ ] `getSegmentBreakdown(filters)` service call added _(AC-1)_
- [ ] `useSegmentBreakdown(filters)` hook: `staleTime:60_000` _(AC-2)_
- [ ] `SegmentBreakdownTable.tsx`: all columns, null→"—", caption, loading, empty, error+Retry _(AC-4)_
- [ ] `SegmentBreakdownTable.test.tsx` Vitest passes _(AC-3, AC-4)_
- [ ] E2E: Scenario 1 (region filter) passes _(AC-1, AC-2, AC-3, AC-4)_
- [ ] E2E: Scenario 2 (empty state) passes _(AC-4)_

---

## Done when

1. **BE:** `./gradlew test` — segment breakdown service + controller cases green
2. **FE:** `npm run test` passes + E2E Scenarios 1–2 pass against real BE
3. Every AC (AC-1 through AC-4) referenced by at least one passing test
