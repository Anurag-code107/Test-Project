---
id: US-01
title: "View analytics dashboard"
layers: ["BE", "FE"]
seed_id: ["S-01", "S-02", "S-03", "S-04"]
touches_entities: ["RewardWallet", "LedgerEntry", "RedemptionRequest"]
depends_on_stories: []
---

# US-01: View analytics dashboard

## Description

**Actor:** CLIENT_ADMIN
**Trigger:** CLIENT_ADMIN navigates to `/redemption/admin/analytics`

**Steps:**
1. Page loads with the default date range: Last 30 days (dateFrom = today − 30, dateTo = today)
2. `GET /api/v1/redemption/analytics?dateFrom=&dateTo=` is called; response is cached in Redis for 60s
3. Four metric card groups render — one card per active `currencyId` in the tenant: Redemption Rate, Unredeemed Balance, Failed/Cancelled Rate, and Total Redemption Count
4. CLIENT_ADMIN clicks a preset button ("Last 7 days", "Last 30 days", "Last 90 days", "Last 12 months") or opens the custom calendar picker
5. Selected range updates query key → new API call fires; windowed cards (FR-07.3, FR-07.7) reflect new window; lifetime cards (FR-07.1, FR-07.2) remain unchanged
6. If tenant has no wallet data: all cards show "No program activity yet"
7. If the selected window has no redemption activity: windowed cards show "No redemptions in this period"

**Expected outcome:** All 4 metric card groups render with per-currency data. Date preset changes refresh windowed cards within 60s cache TTL. Skeleton shown while loading.

**Negative paths:**
- `dateFrom` after `dateTo` → 422 surfaced as error toast
- Date range > 24 months → client-side validation inline under picker: "Date range cannot exceed 24 months"
- Non-CLIENT_ADMIN JWT (e.g. PARTNER_SELLER) → 403 → `ProtectedRoute` redirects to home

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/analytics` with valid params returns 200 with `RedemptionAnalyticsSummaryResponse`; `redemptionRates`, `unredeemedBalances`, `failedCancelledRates` each contain one entry per `currencyId` active in the tenant; `totalRedemptionCount` and `dateWindow` are present
- **AC-2:** `redemptionRates[].ratePercentage` equals (SUM of REDEMPTION LedgerEntry amounts ÷ SUM of REWARD LedgerEntry amounts) × 100, lifetime, regardless of dateFrom/dateTo; `unredeemedBalances[].totalOutstanding` equals SUM(availableBalance + reservedBalance) across all tenant wallets, snapshot, not date-filtered
- **AC-3:** `failedCancelledRates[].ratePercentage` and `totalRedemptionCount.byStatus` reflect only `RedemptionRequest` rows whose `submittedAt` falls within the Instant range derived from the requested dateFrom/dateTo window
- **AC-4:** A second identical request within 60s is served from Redis cache (same response object, no DB queries); cache key is `{clientId}:{dateFrom}:{dateTo}`
- **AC-5:** 422 when `dateFrom` is after `dateTo` or span exceeds 730 days; 403 when caller lacks `action.redemption.view_analytics`; 401 when no token is provided
- **AC-6:** FE renders 4 metric card groups per active `currencyId`; each card shows `<Skeleton>` while the query is in flight; "No redemptions in this period" displayed on FR-07.3 and FR-07.7 cards when `hasActivity = false`; "No program activity yet" displayed on all cards when `redemptionRates` is empty (brand-new tenant)
- **AC-7:** Preset buttons (Last 7d / 30d / 90d / 12mo) update the date range state and trigger a refetch; custom calendar picker rejects a selection > 24 months before calling `onChange`, showing inline validation text "Date range cannot exceed 24 months"

---

## Out of Scope

- CSV export flow (US-02)
- Per-catalog-item or tier/region breakdowns (Phase 2 — F-08)
- Cross-tenant analytics aggregation
- Background job or Kafka-based data pipeline (query-on-demand only in Phase 1)

---

## UI States

- [ ] **Loading:** `<Skeleton>` renders inside each metric card while `useRedemptionAnalytics` is in flight
- [ ] **Empty (no wallet data):** All cards display "No program activity yet" — `redemptionRates` array is empty
- [ ] **Empty (windowed — no redemptions):** FR-07.3 and FR-07.7 cards display "No redemptions in this period" when `hasActivity = false`
- [ ] **Error (5xx):** Toast notification: "Could not load analytics. Please refresh." — cards stay in skeleton/error state with retry available via date preset click

### Verbatim microcopy

- Page heading: `"Redemption Analytics"`
- Preset button labels: `"Last 7 days"`, `"Last 30 days"`, `"Last 90 days"`, `"Last 12 months"`, `"Custom range"`
- Date range validation: `"Date range cannot exceed 24 months"` (inline under calendar picker)
- Empty state — no wallets: `"No program activity yet"`
- Empty state — no windowed activity: `"No redemptions in this period"`
- Error toast: `"Could not load analytics. Please refresh."`
- Card label pattern: `"{currencyId} Redemption Rate"`, `"{currencyId} Outstanding Liability"`, `"{currencyId} Failed & Cancelled Rate"`, `"Total Redemptions"`

### Conditional rendering

**Input: `redemptionRates` array length**
- `length === 0`: All 4 card groups render empty state "No program activity yet" — tenant has no wallet data
- `length > 0`: Cards render with per-`currencyId` data

**Input: `CurrencyTypeRateDto.hasActivity`**
- `false`: Card body shows "No redemptions in this period" instead of rate/count
- `true`: Card body shows `ratePercentage` / `total` with metric breakdown

---

## Depends on

- **Foundation tasks:** F1, F2, F3
- **Prior stories:** None

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-07.1, FR-07.2, FR-07.3, FR-07.4, FR-07.5, FR-07.7, FR-07.8
- `spec.md → ## DTOs [BE]` — `RedemptionAnalyticsSummaryResponse`, `DateWindowDto`, `CurrencyTypeRateDto`, `CurrencyTypeBalanceDto`, `RedemptionCountDto`
- `spec.md → ## API Endpoints [BE + FE]` — `GET /api/v1/redemption/analytics`
- `spec.md → ## Service Layer [BE]` — `getAnalyticsSummary()` business rules; lifetime vs windowed distinction
- `spec.md → ## Caching Strategy [BE]` — Redis `@Cacheable`, 60s TTL, cache key pattern
- `spec.md → ## Security Design [BE]` — rate limit 10 req/min/user; input validation; OWASP A01/A03
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.view_analytics`
- `spec.md → ## Edge Cases` — items 1 (no wallets), 2 (no windowed activity), 3 (zero earned), 4 (> 24 months), 5 (dateFrom > dateTo), 7 (analytics rate limit), 8 (partial currency types), 11 (Redis unavailable)
- `spec.md → ## Frontend Specification [FE]` — `RedemptionAnalyticsPage`, `RedemptionRateCard`, `UnredeemedBalanceCard`, `FailedCancelledRateCard`, `TotalCountCard`, `DateRangeFilter`
- `spec.md → ## Observability [BE]` — `analytics_summary_cache_hit`, `analytics_summary_computed` log events
- `technical.md → ## Package Layout [BE]` — concrete file paths
- `technical.md → ## Package Layout [FE]` — concrete file paths
- `technical.md → ## Hook Specs [FE]` — `useRedemptionAnalytics` query key, staleTime, enabled condition
- `technical.md → ## Repository Queries [BE]` — all LedgerEntry + RewardWallet + RedemptionRequest query shapes

---

## BE tasks [BE]

### BE-1: Response DTOs

**Files:** `src/main/java/com/tenxengage/app/dto/response/redemption/`

Create five Java records with `from()` static factories:

- `RedemptionAnalyticsSummaryResponse` — fields: `dateWindow: DateWindowDto`, `redemptionRates: List<CurrencyTypeRateDto>`, `unredeemedBalances: List<CurrencyTypeBalanceDto>`, `failedCancelledRates: List<CurrencyTypeRateDto>`, `totalRedemptionCount: RedemptionCountDto`
- `DateWindowDto` — fields: `from: LocalDate`, `to: LocalDate`
- `CurrencyTypeRateDto` — fields: `currencyId: String`, `numerator: Long`, `denominator: Long`, `ratePercentage: BigDecimal`, `hasActivity: boolean`
- `CurrencyTypeBalanceDto` — fields: `currencyId: String`, `availableBalance: Long`, `reservedBalance: Long`, `totalOutstanding: Long`
- `RedemptionCountDto` — fields: `total: Long`, `byStatus: Map<String, Long>`, `hasActivity: boolean`

All fields: types as listed; no additional fields. `ratePercentage` rounded to 2 decimal places (`HALF_UP`).

See `spec.md → ## DTOs [BE]` for full field descriptions.

### BE-2: Service method + unit test

**Files:** `src/main/java/com/tenxengage/app/service/redemption/RedemptionAnalyticsService.java`, `src/test/java/com/tenxengage/app/service/redemption/RedemptionAnalyticsServiceTest.java`

`getAnalyticsSummary(LocalDate dateFrom, LocalDate dateTo): RedemptionAnalyticsSummaryResponse`
- `@Transactional(readOnly = true)`
- Annotate with Spring `@Cacheable(value = "redemption-analytics", key = "#root.target.buildCacheKey(#dateFrom, #dateTo)")` — cache key must include `clientId` resolved from `TenantContext.getCurrentClientId()`
- Validate: if `dateFrom.isAfter(dateTo)` or span > 730 days → throw `BusinessRuleException` (→ 422)
- Convert `LocalDate` → `Instant` range: `from = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant()`, `toExclusive = dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()`
- Discover active currency IDs: `findDistinctCurrencyIdsByClientId(clientId)`
- Per currency ID:
  - Redemption rate: `sumAmountByClientIdAndCurrencyIdAndEntryType(clientId, currencyId, REDEMPTION)` ÷ `...REWARD`; `hasActivity = denominator > 0`
  - Unredeemed balance: `sumBalancesByClientIdAndCurrencyId(clientId, currencyId)`
  - Failed/cancelled rate: `countByClientIdAndCurrencyIdAndStatusInAndSubmittedAtBetween([FAILED, CANCELLED])` ÷ `countByClientIdAndCurrencyIdAndSubmittedAtBetween`
- Total count: `countGroupByStatusByClientIdAndSubmittedAtBetween` → group into `byStatus` map; `total = sum of all values`
- Log on cache hit: `step=analytics_summary_cache_hit`; on cache miss: `step=analytics_summary_computed, durationMs`

**Unit test cases (parameterize where applicable):**
- Happy path: one currency with known ledger amounts → `ratePercentage` = expected value
- `hasActivity = false` when no REWARD entries (denominator = 0) — no divide-by-zero
- Cache hit: second call with same params returns cached result without hitting repos
- Empty tenant (no wallets): returns response with all lists empty
- `dateFrom > dateTo` → `BusinessRuleException`
- Span > 730 days → `BusinessRuleException`
- Failed/cancelled rate: parameterize over `FAILED`, `CANCELLED`, both combined

### BE-3: Controller endpoint + @WebMvcTest

**Files:** `src/main/java/com/tenxengage/app/controller/redemption/RedemptionAnalyticsController.java`, `src/test/java/com/tenxengage/app/controller/redemption/RedemptionAnalyticsControllerTest.java`

```
@Tag(name = "Redemption Analytics")
@RestController
@RequestMapping("/api/v1/redemption/analytics")
@RequiredArgsConstructor
@Validated                         ← required: enables @RequestParam constraint enforcement
public class RedemptionAnalyticsController {

    @GetMapping
    @RequiresPermission("action.redemption.view_analytics")
    public ResponseEntity<RedemptionAnalyticsSummaryResponse> getAnalyticsSummary(
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo) { ... }
}
```

- Default `dateFrom` = today − 30 days; default `dateTo` = today (applied in controller before calling service)
- `@Operation` OpenAPI annotation with summary and parameter descriptions
- Rate limit: 10 req/min/user via existing `RateLimitFilter` mechanism

**@WebMvcTest cases:**
- 200 with valid dateFrom/dateTo
- 200 with no params (defaults applied)
- 400 with non-ISO-8601 date string
- 422 with `dateFrom > dateTo`
- 422 with span > 730 days
- 403 with insufficient permission
- 401 with no token

---

## FE tasks [FE]

### FE-1: TypeScript types + service call

**Files:** `src/types/redemption-analytics.types.ts`, `src/services/redemption-analytics.service.ts`

Copy types from `../tenxengage-contracts/` after `/generate-contracts` runs — do NOT hand-write. Types needed:
- `RedemptionAnalyticsSummaryResponse`
- `DateWindowDto`
- `CurrencyTypeRateDto`
- `CurrencyTypeBalanceDto`
- `RedemptionCountDto`

Service call:
```ts
getSummary(dateFrom: string, dateTo: string): Promise<RedemptionAnalyticsSummaryResponse>
// GET /api/v1/redemption/analytics?dateFrom={dateFrom}&dateTo={dateTo}
// dateFrom / dateTo as ISO 8601 strings (YYYY-MM-DD)
```

### FE-2: Hook

**File:** `src/hooks/useRedemptionAnalytics.ts`

```ts
// Query key: ['redemption-analytics', clientId, dateFrom, dateTo]
// staleTime: 60 * 1000        ← matches server Redis TTL
// gcTime: 5 * 60 * 1000
// enabled: !!clientId
// retry: false                 ← read-only dashboard; no silent retry on 404/403
// Default params (applied inside hook when not provided by caller):
//   dateFrom = format(subDays(new Date(), 30), 'yyyy-MM-dd')
//   dateTo   = format(new Date(), 'yyyy-MM-dd')
```

`clientId` resolved from `useAuth()` context — never read from storage directly.

### FE-3a: Rate cards + Vitest tests

**Files:**
- `src/components/redemption-analytics/RedemptionRateCard.tsx`
- `src/components/redemption-analytics/__tests__/RedemptionRateCard.test.tsx`
- `src/components/redemption-analytics/FailedCancelledRateCard.tsx`
- `src/components/redemption-analytics/__tests__/FailedCancelledRateCard.test.tsx`

Both components accept `data: CurrencyTypeRateDto` — same props type, same visual structure (rate % prominent; numerator + denominator as sub-labels via `getCurrency()` formatter; `hasActivity=false` → empty state copy).

`RedemptionRateCard` displays: `"{currencyId} Redemption Rate"` title; `ratePercentage` as large display (`"34.25%"`); sub-labels showing total redeemed amount and total earned amount.

`FailedCancelledRateCard` displays: `"{currencyId} Failed & Cancelled Rate"` title; same layout but numerator = failed+cancelled count, denominator = total requests.

**Vitest tests (both components):** renders with `hasActivity=true` → shows rate; renders with `hasActivity=false` → shows "No redemptions in this period"; renders correct `currencyId` label.

### FE-3b: Unredeemed balance card + Vitest test

**Files:**
- `src/components/redemption-analytics/UnredeemedBalanceCard.tsx`
- `src/components/redemption-analytics/__tests__/UnredeemedBalanceCard.test.tsx`

Accepts `data: CurrencyTypeBalanceDto`. Displays: `"{currencyId} Outstanding Liability"` title; `totalOutstanding` as primary value via `getCurrency()`; sub-labels for `availableBalance` ("Available: X") and `reservedBalance` ("Reserved: X").

**Vitest tests:** renders all balance fields; `totalOutstanding = availableBalance + reservedBalance`.

### FE-3c: Total count card + Vitest test

**Files:**
- `src/components/redemption-analytics/TotalCountCard.tsx`
- `src/components/redemption-analytics/__tests__/TotalCountCard.test.tsx`

Accepts `data: RedemptionCountDto`. Displays: `"Total Redemptions"` title; `total` as large display; status-breakdown list with rows for `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED` — each row: label + count from `byStatus` map; `hasActivity=false` → "No redemptions in this period".

**Vitest tests:** renders all 5 status rows; empty state when `hasActivity=false`; `total` matches sum of `byStatus` values.

### FE-4: DateRangeFilter + Vitest test

**Files:**
- `src/components/redemption-analytics/DateRangeFilter.tsx`
- `src/components/redemption-analytics/__tests__/DateRangeFilter.test.tsx`

Props: `value: DateRange`, `onChange: (range: DateRange) => void`

Structure:
- 5 preset buttons (Last 7d, Last 30d, Last 90d, Last 12mo, Custom range)
- Custom range: shadcn `<Popover>` containing shadcn `<Calendar>` in range mode
- Validation: if selected range > 730 days → call `onChange` is NOT invoked; inline message shown: `"Date range cannot exceed 24 months"` (under the calendar)
- `react-day-picker v8` API (not v9) — verify before use

**Vitest tests:** preset buttons call `onChange` with correct date ranges; custom range > 730 days shows validation message and does not call `onChange`; custom range ≤ 730 days calls `onChange`.

### FE-5: Page wiring

**Files:** `src/pages/redemption/analytics/RedemptionAnalyticsPage.tsx`, `src/App.tsx`

`RedemptionAnalyticsPage`:
- Holds `dateFrom`/`dateTo` in `useState` (defaults: today − 30 / today)
- Calls `useRedemptionAnalytics(dateFrom, dateTo)`
- While `isLoading`: renders `<Skeleton>` inside each card slot (4 slots per active currency — or 1 slot per card group if currency count unknown at skeleton time; use 3 skeleton cards per group as placeholder)
- While `isError`: shows error toast + empty card shells with retry affordance
- Renders `<DateRangeFilter>` connected to state — `onChange` updates state → query key changes → refetch
- Renders 4 card group sections:
  - `redemptionRates.map(d => <RedemptionRateCard data={d} />)`
  - `unredeemedBalances.map(d => <UnredeemedBalanceCard data={d} />)`
  - `failedCancelledRates.map(d => <FailedCancelledRateCard data={d} />)`
  - `<TotalCountCard data={totalRedemptionCount} />`
- Export section stub: Export button (disabled, placeholder) — wired in US-02
- If `redemptionRates.length === 0`: render "No program activity yet" across all card sections

`App.tsx` — add route inside the existing `AppLayout` pattern:
```tsx
<Route element={<ProtectedRoute permission="action.redemption.view_analytics" />}>
  <Route element={<AppLayout />}>
    <Route path="/redemption/admin/analytics" element={<RedemptionAnalyticsPage />} />
  </Route>
</Route>
```

Also add sidebar entry to the Redemption section navigation config: `{ label: 'Analytics', path: '/redemption/admin/analytics', permissionKey: 'action.redemption.view_analytics' }`

---

## E2E test [FE]

**File:** `e2e/redemption-analytics-basic/analytics-dashboard.spec.ts`

---

**Scenario 1:** `'dashboard loads with all metric card groups'` _(covers AC-1, AC-2, AC-3, AC-6)_

| Field | Value |
|---|---|
| **User flow** | Login as CLIENT_ADMIN → navigate to `/redemption/admin/analytics` → wait for cards to render |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics*` → 200 + `RedemptionAnalyticsSummaryResponse` fixture with 2 currency IDs (`"CASH"`, `"POINTS"`), all `hasActivity: true` |
| **Visible assertion** | `expect(page.getByText('CASH Redemption Rate')).toBeVisible()` + `expect(page.getByText('POINTS Outstanding Liability')).toBeVisible()` + `expect(page.getByText('Total Redemptions')).toBeVisible()` |
| **Negative case** | — |

---

**Scenario 2:** `'date preset filter triggers refetch and updates windowed cards'` _(covers AC-3, AC-7)_

| Field | Value |
|---|---|
| **User flow** | Load page (Last 30 days default) → click "Last 7 days" preset → verify API called with updated dateFrom/dateTo |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics*` → capture request params; respond with modified `failedCancelledRates[].ratePercentage = "5.00"` |
| **Visible assertion** | Updated rate visible in card; `expect(requestUrl).toContain('dateFrom=')` with correct 7-day range |
| **Negative case** | Custom range > 24 months → `page.route` not called again; "Date range cannot exceed 24 months" visible |

---

**Scenario 3:** `'empty state renders when tenant has no activity'` _(covers AC-6)_

| Field | Value |
|---|---|
| **User flow** | Load page with API returning `redemptionRates: []` (no active wallets) |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics*` → 200 + `{ redemptionRates: [], unredeemedBalances: [], failedCancelledRates: [], totalRedemptionCount: { total: 0, byStatus: {}, hasActivity: false } }` |
| **Visible assertion** | `expect(page.getByText('No program activity yet')).toBeVisible()` |
| **Negative case** | — |

---

**Scenario 4:** `'PARTNER_SELLER redirected from analytics page'` _(covers AC-5)_

| Field | Value |
|---|---|
| **User flow** | Login as PARTNER_SELLER → navigate directly to `/redemption/admin/analytics` |
| **APIs to mock via `page.route()`** | None (ProtectedRoute intercepts before API call) |
| **Visible assertion** | URL does not remain at `/redemption/admin/analytics`; user lands on home/login |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `RedemptionAnalyticsSummaryResponse.java` record created in `dto/response/redemption/` with `from()` factory _(AC-1)_
- [ ] `DateWindowDto.java` record created _(AC-1)_
- [ ] `CurrencyTypeRateDto.java` record created — fields: `currencyId: String`, `numerator: Long`, `denominator: Long`, `ratePercentage: BigDecimal`, `hasActivity: boolean` _(AC-1, AC-2, AC-3)_
- [ ] `CurrencyTypeBalanceDto.java` record created — fields: `currencyId: String`, `availableBalance: Long`, `reservedBalance: Long`, `totalOutstanding: Long` _(AC-1, AC-2)_
- [ ] `RedemptionCountDto.java` record created — fields: `total: Long`, `byStatus: Map<String, Long>`, `hasActivity: boolean` _(AC-1, AC-3)_
- [ ] `RedemptionAnalyticsService.getAnalyticsSummary()` implemented with `@Cacheable` (Redis, 60s TTL, clientId-scoped key); LocalDate → Instant conversion; lifetime vs windowed distinction _(AC-1, AC-2, AC-3, AC-4)_
- [ ] `RedemptionAnalyticsServiceTest` passes: happy path rate calculation; `hasActivity=false` when denominator=0; cache hit; empty tenant; `dateFrom>dateTo` → exception; span>730d → exception; failed/cancelled rate parameterized over status values _(AC-1, AC-2, AC-3, AC-4, AC-5)_
- [ ] `RedemptionAnalyticsController.getAnalyticsSummary()` endpoint added; `@RequiresPermission`; `@Validated` on class; defaults applied for missing params; rate limit 10/min/user _(AC-1, AC-5)_
- [ ] `RedemptionAnalyticsControllerTest` passes: 200 with params; 200 with defaults; 400 bad date format; 422 dateFrom>dateTo; 422 span>730d; 403 wrong permission; 401 no token _(AC-1, AC-5)_

**FE session:**
- [ ] TypeScript types (`RedemptionAnalyticsSummaryResponse`, `CurrencyTypeRateDto`, `CurrencyTypeBalanceDto`, `RedemptionCountDto`, `DateWindowDto`) added to `redemption-analytics.types.ts` from contracts _(AC-1)_
- [ ] `getSummary(dateFrom, dateTo)` service call added to `redemption-analytics.service.ts` _(AC-1)_
- [ ] `useRedemptionAnalytics` hook created — queryKey `['redemption-analytics', clientId, dateFrom, dateTo]`; staleTime 60s; enabled: `!!clientId`; retry: false _(AC-1, AC-4)_
- [ ] `RedemptionRateCard` + `FailedCancelledRateCard` created; both show empty state when `hasActivity=false`; tests pass (FE-3a) _(AC-2, AC-3, AC-6)_
- [ ] `UnredeemedBalanceCard` created; shows all 3 balance fields; test passes (FE-3b) _(AC-2, AC-6)_
- [ ] `TotalCountCard` created; shows `total` + all 5 status rows; empty state when `hasActivity=false`; test passes (FE-3c) _(AC-3, AC-6)_
- [ ] `DateRangeFilter` created; presets work; custom range > 24 months shows "Date range cannot exceed 24 months" and does NOT invoke `onChange`; test passes (FE-4) _(AC-7)_
- [ ] `RedemptionAnalyticsPage` wired: `<Skeleton>` while loading; error toast on 5xx; all 4 card groups rendered; connected to `DateRangeFilter`; export section stub present (FE-5) _(AC-6, AC-7)_
- [ ] Route added to `App.tsx` with `permission="action.redemption.view_analytics"` + `AppLayout` wrapper _(AC-5)_
- [ ] Sidebar entry added to Redemption section nav config _(AC-5)_
- [x] E2E: `'dashboard loads with all metric card groups'` passes _(AC-1, AC-2, AC-3, AC-6)_
- [x] E2E: `'date preset filter triggers refetch and updates windowed cards'` passes _(AC-3, AC-7)_
- [x] E2E: `'empty state renders when tenant has no activity'` passes _(AC-6)_
- [x] E2E: `'PARTNER_SELLER redirected from analytics page'` passes _(AC-5)_

---

## Done when

1. **BE:** `./gradlew test` passes — all `RedemptionAnalyticsServiceTest` + `RedemptionAnalyticsControllerTest` cases green
2. **FE:** `npm run test` passes + all 4 Playwright scenarios in `analytics-dashboard.spec.ts` pass against real BE
3. Every AC-1 through AC-7 is referenced by at least one passing test (unit, @WebMvcTest, Vitest, or E2E)
