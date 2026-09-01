---
id: US-05
title: "Advanced tab shell, filter bar, and refresh status"
seed_id: "F-08.S-05"
layers: ["BE", "FE"]
touches_entities: ["analytics_mv_refresh_log"]
depends_on_stories: []
---

# US-05: Advanced tab shell, filter bar, and refresh status

## Description

**Actor:** CLIENT_ADMIN

**Trigger:** CLIENT_ADMIN navigates to `/redemption/admin/analytics`.

**Steps:**
1. Page renders with the Overview tab active by default.
2. If the user holds `action.redemption.analytics.advanced` AND their tenant's `redemption_analytics_advanced` flag is enabled, an "Advanced" tab is visible in the tab bar.
3. CLIENT_ADMIN clicks the Advanced tab.
4. The Advanced tab renders: a filter bar (date range + region + role), a staleness banner (if stale), and section placeholders for US-01 through US-04, US-06, US-07.
5. The filter bar polls `/refresh-status` every 5 minutes; if `isStale=true` or `lastRefreshedAt=null`, a yellow banner appears.

**Expected outcome:** Advanced tab is accessible, filter bar functional, staleness banner shown/hidden according to MV freshness.

**Negative paths:**
- Starter tenant (flag=false): Advanced tab is absent from the DOM — not just hidden; inspect shows no tab element.
- CLIENT_ADMIN without permission: Advanced tab absent; direct `GET /advanced/refresh-status` → 403.
- `/refresh-status` endpoint fails: banner is silently suppressed — the tab does not show an error state for this secondary call.

---

## Acceptance Criteria

- **AC-1:** Advanced tab is rendered in the tab bar only when the authenticated user holds `action.redemption.analytics.advanced` AND the tenant's `redemption_analytics_advanced` feature flag is `true`; when either condition fails, the tab element is absent from the DOM (not disabled or hidden).
- **AC-2:** On initial page load, the Overview tab is active by default regardless of URL or prior session state. _(⊕-1)_
- **AC-3:** The filter bar provides: date range presets (Last 7 days, Last 30 days, Last 90 days) that set `dateFrom`/`dateTo` correctly; a custom date picker with a 365-day maximum; and region + role multi-select dropdowns. Selecting a custom range > 365 days shows an inline error "Date range cannot exceed 365 days" and disables the Apply button until the range is corrected.
- **AC-4:** When the segment breakdown endpoint returns an empty `segments` array, the region and role multi-select dropdowns render as disabled with placeholder text "No data available". _(⊕-3)_
- **AC-5:** `GET /api/v1/redemption/analytics/advanced/refresh-status` returns 200 with `AnalyticsRefreshStatusResponse` (fields: `isStale: boolean`, `lastRefreshedAt: string | null`, `staleThresholdHours: number`); returns 403 when the caller lacks `action.redemption.analytics.advanced`.
- **AC-6:** When `isStale=true` OR `lastRefreshedAt=null`, a yellow staleness banner is displayed: "Analytics data may be outdated. Last refreshed: {date} at {time} UTC." (or "Analytics data has not been refreshed yet." when `lastRefreshedAt=null`). The banner has a ✕ dismiss button; dismissal is session-only (not persisted in localStorage or cookies).
- **AC-7:** `useRefreshStatus()` polls `/refresh-status` every 5 minutes (`refetchInterval: 300_000`). When `isStale` transitions from `true` to `false` on the next poll, the banner auto-hides without requiring a page reload.

---

## Out of Scope

- Section data loading (item breakdown, segment breakdown, TTFR, trend, liability, failure) — each covered by US-01 through US-04, US-06, US-07.
- Export functionality — covered by US-06.
- Overview tab content — existing F-07 functionality; no changes in this story.
- MV refresh trigger from the UI (no manual refresh button in spec).

---

## Non-Functional Notes

- **Perf:** `/refresh-status` is NOT cached in Redis (spec NFR — staleness check must reflect actual log table state). `staleTime: 0` in the hook ensures every render triggers a network call, but refetchInterval caps polling at every 5 min.
- **a11y:** Staleness banner must have `role="alert"` so screen readers announce it on appearance. Dismiss button must have `aria-label="Dismiss staleness warning"`.

---

## UI States

- [ ] **Loading:** Tab bar skeleton while page-level auth/flag check resolves; once resolved, tab renders immediately (no lazy data fetch at this level).
- [ ] **Empty (no flag / no permission):** Advanced tab element absent from DOM — no empty state UI required.
- [ ] **Error (`/refresh-status` fails):** Banner is silently suppressed; no error indicator shown for this secondary fetch.
- [ ] **Staleness banner visible:** Yellow banner with dismiss button (AC-6).
- [ ] **Staleness banner dismissed:** Banner hidden for the rest of the session; re-appears if user refreshes the page.

### Verbatim microcopy

- Tab label: "Advanced"
- Staleness banner (known timestamp): "Analytics data may be outdated. Last refreshed: {date} at {time} UTC."
- Staleness banner (no refresh yet): "Analytics data has not been refreshed yet."
- Date validation error: "Date range cannot exceed 365 days"
- Filter region/role disabled placeholder: "No data available"
- Date preset labels: "Last 7 days", "Last 30 days", "Last 90 days"
- Apply button: "Apply"

### Conditional rendering

**Input: permission `action.redemption.analytics.advanced` + flag `redemption_analytics_advanced`**
- Both `true`: Advanced tab visible; filter bar, section placeholders, staleness banner (if stale) all render.
- Either `false`: Advanced tab absent from DOM; page shows Overview tab only.

**Input: `isStale` (from `/refresh-status`)**
- `isStale=true` OR `lastRefreshedAt=null`: Yellow staleness banner visible (until dismissed or next poll resolves `isStale=false`).
- `isStale=false`: Banner hidden.
- `/refresh-status` error: Banner hidden (silent fail).

**Input: `segments` array from segment breakdown (for filter dropdowns)**
- Non-empty: Region and role dropdowns enabled with populated options.
- Empty: Region and role dropdowns disabled with "No data available" placeholder.

---

## Depends on

- **Foundation tasks:** F1, F2, F4, F5
- **Prior stories:** None — US-05 creates the controller class and FE tab shell that all other stories depend on.

---

## Spec references

- `## Functional Requirements` — FR-08.6 (Advanced tab), FR-08.8 (staleness banner), FR-08.11 (filter bar)
- `## API Endpoints [BE + FE]` — `GET /api/v1/redemption/analytics/advanced/refresh-status`
- `## DTOs [BE]` — `AnalyticsRefreshStatusResponse`
- `## Service Layer [BE]` — `getRefreshStatus()` — queries `analytics_mv_refresh_log`, computes `isStale` (last_refreshed_at < NOW() - 4 hours)
- `## Permissions & Feature Flags [BE + FE]` — `action.redemption.analytics.advanced`, `redemption_analytics_advanced`
- `## Security Design [BE]` — rate limit: `RateLimitFilter` (10 req/min per tenant) applies; `/refresh-status` is NOT cached

---

## BE tasks [BE]

### BE-1: DTOs

**Files:** `src/main/java/com/tenxengage/app/dto/response/redemption/AnalyticsRefreshStatusResponse.java`

Java record with fields: `boolean isStale`, `Instant lastRefreshedAt` (nullable), `int staleThresholdHours`.
Provide `static AnalyticsRefreshStatusResponse of(Instant lastRefreshedAt, int thresholdHours)` factory that computes `isStale` as `lastRefreshedAt == null || lastRefreshedAt.isBefore(Instant.now().minus(thresholdHours, HOURS))`.

### BE-2: Service method + unit test

**Files:** `src/main/java/com/tenxengage/app/service/redemption/RedemptionAdvancedAnalyticsService.java` (new class), `src/test/java/com/tenxengage/app/service/redemption/RedemptionAdvancedAnalyticsServiceTest.java`

`getRefreshStatus()`: queries `analytics_mv_refresh_log` for the minimum `last_refreshed_at` across all MVs (the oldest refresh is the binding constraint); passes result into `AnalyticsRefreshStatusResponse.of(lastRefreshedAt, 4)`.

Unit test scenarios:
- Empty log table → `isStale=true`, `lastRefreshedAt=null`
- `last_refreshed_at = NOW()-5h` → `isStale=true`
- `last_refreshed_at = NOW()-1h` → `isStale=false`

Feature flag enforcement: service reads `FeatureFlagService.isEnabled("redemption_analytics_advanced")` for the current tenant; throws `AccessDeniedException` if false (controller maps to 403).

### BE-3: Controller endpoint + @WebMvcTest

**Files:** `src/main/java/com/tenxengage/app/controller/redemption/RedemptionAdvancedAnalyticsController.java` (new class), `src/test/java/com/tenxengage/app/controller/redemption/RedemptionAdvancedAnalyticsControllerTest.java`

`GET /api/v1/redemption/analytics/advanced/refresh-status` → `ResponseEntity<AnalyticsRefreshStatusResponse>`.
Annotate controller with `@RequiresPermission("action.redemption.analytics.advanced")`.

@WebMvcTest scenarios:
- 200 with mocked service returning `isStale=true` → response body has `isStale=true`
- 200 with mocked service returning `isStale=false` → `isStale=false`
- 403 when permission missing (mock `@RequiresPermission` enforcement)

---

## FE tasks [FE]

### FE-1: TypeScript types + service call

**Files:** `src/types/redemption-analytics-advanced.types.ts` (new file), `src/services/redemption-analytics-advanced.service.ts` (new file)

Copy types from `../tenxengage-contracts/` only — do not hand-write. `AnalyticsRefreshStatusResponse` type from contracts.
`getRefreshStatus(): Promise<AnalyticsRefreshStatusResponse>` in service file.

### FE-2: Hook

**File:** `src/hooks/redemption/useRefreshStatus.ts`

`staleTime: 0` (always refetch on mount — staleness check must be live), `refetchInterval: 300_000` (poll every 5 min).
Query key: `['redemption-analytics-advanced', 'refresh-status']`.

### FE-3a: Tab wiring + AdvancedFilterBar

**Files:**
- `src/pages/redemption/analytics/RedemptionAnalyticsPage.tsx` — add Advanced tab to existing Tabs component; guard with permission + flag check; Overview tab active by default
- `src/components/analytics/advanced/AdvancedFilterBar.tsx`
- `src/components/analytics/advanced/__tests__/AdvancedFilterBar.test.tsx`

AdvancedFilterBar props: `onFilterChange(filters: AdvancedAnalyticsFilters): void`, `isSegmentDataEmpty: boolean`.
Preset buttons set `dateFrom`/`dateTo` relative to today. Custom picker validates span ≤ 365 days inline.
When `isSegmentDataEmpty=true`, region and role dropdowns render `disabled` with "No data available" placeholder.

### FE-3b: StalenessBanner

**Files:**
- `src/components/analytics/advanced/StalenessBanner.tsx`
- `src/components/analytics/advanced/__tests__/StalenessBanner.test.tsx`

Props: `isStale: boolean`, `lastRefreshedAt: string | null`.
Renders a yellow `role="alert"` div. Dismiss button sets local state `dismissed=true`; banner hidden when `dismissed=true` OR `!isStale`.
Auto-hides when `isStale` transitions to `false` on next poll (React effect on `isStale` prop: if `!isStale`, set `dismissed=false` so banner doesn't re-appear if staleness recurs).

Vitest scenarios (AdvancedFilterBar): renders presets; custom range > 365d shows error; Apply disabled on invalid range; dropdowns disabled when `isSegmentDataEmpty=true`.
Vitest scenarios (StalenessBanner): renders when `isStale=true`; hidden when `isStale=false`; dismiss button hides banner; shows "not been refreshed yet" copy when `lastRefreshedAt=null`.

### FE-4: AdvancedAnalyticsTab container

**File:** `src/components/analytics/advanced/AdvancedAnalyticsTab.tsx`

Composes AdvancedFilterBar + StalenessBanner + section component placeholders (each section renders as a `<Skeleton />` in this story — actual sections wired in US-01 through US-07).
Calls `useRefreshStatus()`; passes `isStale` + `lastRefreshedAt` to StalenessBanner.
Calls `useSegmentBreakdown(filters)` with no filters to determine `isSegmentDataEmpty` for AdvancedFilterBar (empty segments array → disable dropdowns).

---

## E2E test [FE]

---

**Scenario 1:** `'CLIENT_ADMIN with flag enabled sees Advanced tab; staleness banner appears when isStale=true'` _(covers AC-1, AC-2, AC-5, AC-6)_

**File:** `e2e/redemption-analytics-advanced/tab-shell.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as CLIENT_ADMIN with `redemption_analytics_advanced=true` → navigate to `/redemption/admin/analytics` → verify Overview tab is active → click Advanced tab → tab panel renders → mock `/refresh-status` with `{"isStale":true,"lastRefreshedAt":"2026-06-19T10:00:00Z","staleThresholdHours":4}` → staleness banner visible → ✕ dismiss → banner disappears |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/refresh-status` → 200 + `{"isStale":true,"lastRefreshedAt":"2026-06-19T10:00:00Z","staleThresholdHours":4}`; `GET /api/v1/redemption/analytics/advanced/segment-breakdown` → 200 + `{"segments":[],"lastRefreshedAt":"..."}` |
| **Visible assertion** | `expect(page.getByRole('tab', {name:'Advanced'})).toBeVisible()`; `expect(page.getByRole('tab', {name:'Overview'})).toHaveAttribute('aria-selected','true')`; after clicking Advanced: `expect(page.getByRole('alert')).toContainText('may be outdated')`; after dismiss: `expect(page.getByRole('alert')).not.toBeVisible()` |
| **Negative case** | — |

---

**Scenario 2:** `'Starter tenant (flag=false) does not see Advanced tab'` _(covers AC-1)_

**File:** `e2e/redemption-analytics-advanced/tab-shell.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as CLIENT_ADMIN with `redemption_analytics_advanced=false` (Starter tenant) → navigate to `/redemption/admin/analytics` → verify Advanced tab is absent from DOM |
| **APIs to mock via `page.route()`** | `GET /api/v1/auth/me` → 200 with tenant flag `redemption_analytics_advanced=false` |
| **Visible assertion** | `expect(page.getByRole('tab', {name:'Advanced'})).not.toBeAttached()` |
| **Negative case** | — |

---

**Scenario 3:** `'Date range > 365 days shows inline error and disables Apply'` _(covers AC-3)_

**File:** `e2e/redemption-analytics-advanced/tab-shell.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Open Advanced tab → click custom date range picker → set start = Jan 1 2025, end = Jan 2 2026 (366 days) → verify error message and Apply disabled |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/analytics/advanced/refresh-status` → 200 `{"isStale":false,...}` |
| **Visible assertion** | `expect(page.getByText('Date range cannot exceed 365 days')).toBeVisible()`; `expect(page.getByRole('button',{name:'Apply'})).toBeDisabled()` |
| **Negative case** | Set range = 365 days exactly → error hidden; Apply enabled |

---

## Execution checklist

**BE session:**
- [ ] `AnalyticsRefreshStatusResponse.java` record created with `isStale`, `lastRefreshedAt`, `staleThresholdHours`, and `of()` factory _(AC-5)_
- [ ] `RedemptionAdvancedAnalyticsService.java` class created; `getRefreshStatus()` method queries `analytics_mv_refresh_log`, computes `isStale` at 4-hour threshold _(AC-5, AC-6)_
- [ ] Feature flag check in service: `redemption_analytics_advanced=false` → 403 _(AC-1)_
- [ ] `RedemptionAdvancedAnalyticsServiceTest`: isStale=true when log empty; isStale=true when last_refreshed_at > 4h ago; isStale=false when recent _(AC-5)_
- [ ] `RedemptionAdvancedAnalyticsController.java` class created; `GET /advanced/refresh-status` method with `@RequiresPermission("action.redemption.analytics.advanced")` _(AC-5)_
- [ ] `RedemptionAdvancedAnalyticsControllerTest`: 200 with isStale=true; 200 with isStale=false; 403 missing permission _(AC-5)_
- [ ] `./gradlew test` passes for new service + controller tests

**FE session:**
- [ ] `redemption-analytics-advanced.types.ts` created; `AnalyticsRefreshStatusResponse` type from contracts _(AC-5)_
- [ ] `redemption-analytics-advanced.service.ts` created; `getRefreshStatus()` call added _(AC-5)_
- [ ] `useRefreshStatus.ts` hook: `staleTime:0`, `refetchInterval:300_000` _(AC-7)_
- [ ] `AdvancedFilterBar.tsx`: presets, custom picker with 365-day validation, region/role multi-selects with `isSegmentDataEmpty` prop _(AC-3, AC-4)_
- [ ] `AdvancedFilterBar.test.tsx` Vitest tests pass _(AC-3, AC-4)_
- [ ] `StalenessBanner.tsx`: yellow `role="alert"` div, dismiss button, auto-hide when `isStale=false` _(AC-6, AC-7)_
- [ ] `StalenessBanner.test.tsx` Vitest tests pass _(AC-6)_
- [ ] `AdvancedAnalyticsTab.tsx`: composes filter bar + banner + placeholders; Overview tab active by default _(AC-1, AC-2)_
- [ ] `RedemptionAnalyticsPage.tsx`: Advanced tab added behind permission + flag guard _(AC-1, AC-2)_
- [ ] E2E: Scenario 1 (staleness banner) passes _(AC-1, AC-2, AC-5, AC-6)_
- [ ] E2E: Scenario 2 (Starter tenant) passes _(AC-1)_
- [ ] E2E: Scenario 3 (date validation) passes _(AC-3)_

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionAdvancedAnalyticsServiceTest` + `RedemptionAdvancedAnalyticsControllerTest` all green
2. **FE:** `npm run test` passes + E2E Scenarios 1–3 pass against real BE
3. Every AC (AC-1 through AC-7) is referenced by at least one passing test
