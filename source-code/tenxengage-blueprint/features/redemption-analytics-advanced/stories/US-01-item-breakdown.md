---
id: US-01
title: "View item breakdown"
seed_id: "F-08.S-01"
layers: ["BE", "FE"]
touches_entities: ["mv_item_redemption_breakdown"]
depends_on_stories: ["US-05"]
---

# US-01: View item breakdown

## Description

**Actor:** CLIENT_ADMIN

**Trigger:** CLIENT_ADMIN opens the Advanced tab in `/redemption/admin/analytics` with the default filter (Last 30 days, no region).

**Steps:**
1. Advanced tab renders; Item Breakdown section is visible.
2. `useItemBreakdown(filters)` fires `GET /advanced/item-breakdown?dateFrom=X&dateTo=Y`.
3. Section renders a table sorted by `totalRedeemedCount` descending with a "Data as of {timestamp} UTC" caption.
4. CLIENT_ADMIN changes region filter to "APAC" and clicks Apply → table re-fetches with `?region=APAC`.

**Expected outcome:** Item Breakdown table shows catalog items ranked by redemption count with all specified columns.

**Negative paths:**
- Custom date range > 365 days: FE shows inline error before sending the request; BE returns 422 if the request reaches the server.
- No redemptions for the selected period: "No data for the selected period" empty state.
- Missing permission: 403 from BE; FE cannot reach this endpoint.

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/analytics/advanced/item-breakdown?dateFrom=X&dateTo=Y` returns 200 `ItemBreakdownResponse` with `items` sorted by `totalRedeemedCount` descending and `lastRefreshedAt` timestamp.
- **AC-2:** Each item row carries: `catalogItemId`, `catalogItemName`, `currencyType`, `totalRedeemedCount`, `totalRedeemedAmount`, `redemptionRate` (as a decimal 0.0–1.0).
- **AC-3:** `?region=APAC` constrains results to redemptions by partners in the APAC region only; span > 365 days → 422 "Date range must not exceed 365 days"; missing permission → 403.
- **AC-4:** Response is Redis-cached 60 seconds keyed `{clientId}:item-breakdown:{dateFrom}:{dateTo}:{region}`. A second identical request within 60s must not trigger a DB query.
- **AC-5:** FE `ItemBreakdownTable` renders columns: Item Name, Currency, Redeemed Count, Amount, Rate (%); table is sorted by Redeemed Count desc; a "Data as of {timestamp} UTC" caption appears below the table.
- **AC-6:** Loading → skeleton rows; empty `items` → "No data for the selected period"; query error → inline error message with a Retry button that re-triggers the query. _(⊕-2)_

---

## Out of Scope

- Failure breakdown by item — covered by US-07.
- Tab shell and filter bar — covered by US-05.
- Pagination (item breakdown returns all results for the client within the date window — no pagination per spec).

---

## UI States

- [ ] **Loading:** Skeleton rows (5 rows) while `useItemBreakdown` is in-flight.
- [ ] **Empty:** "No data for the selected period" — center-aligned, no primary CTA.
- [ ] **Error:** Inline error message "Unable to load item breakdown" with a Retry button that calls `refetch()` on the query.

### Verbatim microcopy

- Section heading: "Item Breakdown"
- Caption: "Data as of {date} at {time} UTC"
- Empty state: "No data for the selected period"
- Error message: "Unable to load item breakdown"
- Retry button: "Retry"
- Column headers: "Item Name", "Currency", "Redeemed Count", "Amount", "Rate (%)"

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-05 (controller class must exist before this story adds methods to it)

---

## Spec references

- `## Functional Requirements` — FR-08.1
- `## Data Model` — `mv_item_redemption_breakdown` columns
- `## API Endpoints [BE + FE]` — `GET /api/v1/redemption/analytics/advanced/item-breakdown`
- `## DTOs [BE]` — `ItemRedemptionDto`, `ItemBreakdownResponse`
- `## Service Layer [BE]` — `getItemBreakdown(filter)` — queries `mv_item_redemption_breakdown`
- `## Permissions & Feature Flags [BE + FE]` — `action.redemption.analytics.advanced`
- `## Security Design [BE]` — Redis cache key pattern; `RateLimitFilter` (10 req/min)
- `## Caching Strategy` — 60s TTL; NOT cached: export endpoint (not applicable here)

---

## BE tasks [BE]

### BE-1: DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/response/redemption/ItemRedemptionDto.java`
- `src/main/java/com/tenxengage/app/dto/response/redemption/ItemBreakdownResponse.java`

`ItemRedemptionDto`: Java record — `UUID catalogItemId`, `String catalogItemName`, `String currencyType`, `long totalRedeemedCount`, `BigDecimal totalRedeemedAmount`, `BigDecimal redemptionRate`.
`ItemBreakdownResponse`: Java record — `List<ItemRedemptionDto> items`, `Instant lastRefreshedAt`.

### BE-2: Service method + unit test

**Files:** `src/main/java/com/tenxengage/app/service/redemption/RedemptionAdvancedAnalyticsService.java` (add method), `src/test/java/com/tenxengage/app/service/redemption/RedemptionAdvancedAnalyticsServiceTest.java` (add test cases)

`getItemBreakdown(AdvancedAnalyticsFilter filter)`:
- Validates span ≤ 365 days → `BusinessRuleException` if exceeded
- Queries `mv_item_redemption_breakdown` via `NamedParameterJdbcTemplate` with `client_id = :clientId` + optional `region` predicate
- Orders by `total_redeemed_count DESC`
- Annotated `@Cacheable(value="advanced-analytics-item-breakdown", key="#root.target.buildAdvancedCacheKey(#filter)")`

Unit test scenarios: happy path returns sorted list; span > 365 → `BusinessRuleException`; region filter applied correctly; empty result returns empty list.

### BE-3: Controller endpoint + @WebMvcTest

**Files:** `src/main/java/com/tenxengage/app/controller/redemption/RedemptionAdvancedAnalyticsController.java` (add method), `src/test/java/com/tenxengage/app/controller/redemption/RedemptionAdvancedAnalyticsControllerTest.java` (add test cases)

Add `GET /api/v1/redemption/analytics/advanced/item-breakdown` to the existing controller class (created in US-05).
Params: `@RequestParam LocalDate dateFrom`, `@RequestParam LocalDate dateTo`, `@RequestParam(required=false) String region`.
Returns `ResponseEntity<ItemBreakdownResponse>`.

@WebMvcTest: 200 with items; 422 span exceeded; 403 missing permission.

---

## FE tasks [FE]

### FE-1: TypeScript types + service call

**Files:** `src/types/redemption-analytics-advanced.types.ts` (add types), `src/services/redemption-analytics-advanced.service.ts` (add call)

Add `ItemRedemptionDto` and `ItemBreakdownResponse` types from contracts.
Add `getItemBreakdown(filters: AdvancedAnalyticsFilters): Promise<ItemBreakdownResponse>` service function.

### FE-2: Hook

**File:** `src/hooks/redemption/useItemBreakdown.ts`

`staleTime: 60_000`. Query key: `['redemption-analytics-advanced', 'item-breakdown', filters]`.
Enabled only when `dateFrom` and `dateTo` are set.

### FE-3: Component + Vitest test

**Files:**
- `src/components/analytics/advanced/ItemBreakdownTable.tsx`
- `src/components/analytics/advanced/__tests__/ItemBreakdownTable.test.tsx`

TanStack Table with columns: Item Name, Currency, Redeemed Count, Amount, Rate (%).
Default sort: Redeemed Count desc (server-side sort from BE — do not re-sort in FE).
"Data as of {timestamp} UTC" caption below table.
Loading: 5 skeleton rows. Empty: "No data for the selected period". Error: error message + Retry button calling `refetch()`.

Vitest: renders columns with mock data; shows skeleton when loading; shows empty state; shows error + Retry.

---

## E2E test [FE]

---

**Scenario 1:** `'Item breakdown table renders with correct columns sorted by redeemed count'` _(covers AC-1, AC-2, AC-5)_

**File:** `e2e/redemption-analytics-advanced/item-breakdown.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Navigate to `/redemption/admin/analytics` → click Advanced tab → wait for Item Breakdown section → verify table rows render sorted by Redeemed Count desc |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/item-breakdown` → 200 + `{"items":[{"catalogItemId":"...","catalogItemName":"Gold Ring","currencyType":"POINTS","totalRedeemedCount":150,...},{"catalogItemName":"Silver Coin","totalRedeemedCount":75,...}],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByRole('columnheader',{name:'Item Name'})).toBeVisible()`; first row shows "Gold Ring" (count=150); second row shows "Silver Coin" (count=75); caption "Data as of" visible |
| **Negative case** | — |

---

**Scenario 2:** `'Date range > 365 days shows validation error before request'` _(covers AC-3)_

**File:** `e2e/redemption-analytics-advanced/item-breakdown.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → set custom date range > 365 days in filter bar → click Apply → verify no request fired; inline error visible |
| **APIs to mock via `page.route()`** | intercept `GET /api/v1/redemption/analytics/advanced/item-breakdown` → assert never called |
| **Visible assertion** | `expect(page.getByText('Date range cannot exceed 365 days')).toBeVisible()` |
| **Negative case** | Set range = 365 days → error hidden; Apply enabled; request fires and table loads |

---

**Scenario 3:** `'Empty state renders when no items returned'` _(covers AC-6)_

**File:** `e2e/redemption-analytics-advanced/item-breakdown.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → wait for Item Breakdown section |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/item-breakdown` → 200 + `{"items":[],"lastRefreshedAt":"2026-06-20T06:00:00Z"}` |
| **Visible assertion** | `expect(page.getByText('No data for the selected period')).toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `ItemRedemptionDto.java` and `ItemBreakdownResponse.java` records created _(AC-1, AC-2)_
- [ ] `getItemBreakdown(filter)` service method added; span validation ≤ 365 days; `NamedParameterJdbcTemplate` query on `mv_item_redemption_breakdown` with clientId + optional region; ordered by `total_redeemed_count DESC` _(AC-1, AC-3)_
- [ ] `@Cacheable` annotation with correct key including all filter params _(AC-4)_
- [ ] `RedemptionAdvancedAnalyticsServiceTest`: happy path, span > 365, region filter, empty result _(AC-1, AC-3)_
- [ ] `GET /advanced/item-breakdown` controller method added to existing `RedemptionAdvancedAnalyticsController` _(AC-1, AC-3)_
- [ ] `RedemptionAdvancedAnalyticsControllerTest`: 200 happy; 422 span exceeded; 403 missing permission _(AC-3, AC-5)_
- [ ] `./gradlew test` passes for new service + controller test cases

**FE session:**
- [ ] `ItemRedemptionDto` and `ItemBreakdownResponse` types added to `redemption-analytics-advanced.types.ts` _(AC-1, AC-2)_
- [ ] `getItemBreakdown(filters)` service call added _(AC-1)_
- [ ] `useItemBreakdown(filters)` hook: `staleTime:60_000`, correct query key _(AC-4)_
- [ ] `ItemBreakdownTable.tsx`: columns, server-side desc sort, "Data as of" caption _(AC-2, AC-5)_
- [ ] `ItemBreakdownTable.test.tsx` Vitest: renders columns; skeleton loading; empty state; error + Retry _(AC-5, AC-6)_
- [ ] UI states: loading skeleton, empty state, error + Retry wired to `refetch()` _(AC-6)_
- [ ] E2E: Scenario 1 (happy path) passes _(AC-1, AC-2, AC-5)_
- [ ] E2E: Scenario 2 (date validation) passes _(AC-3)_
- [ ] E2E: Scenario 3 (empty state) passes _(AC-6)_

---

## Done when

1. **BE:** `./gradlew test` passes — new `RedemptionAdvancedAnalyticsServiceTest` + `RedemptionAdvancedAnalyticsControllerTest` cases for item breakdown green
2. **FE:** `npm run test` passes + E2E Scenarios 1–3 pass against real BE
3. Every AC (AC-1 through AC-6) referenced by at least one passing test
