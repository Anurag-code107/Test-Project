---
id: US-04
title: "Breakage report + CSV export"
layers: ["BE", "FE"]
touches_entities: ["LedgerEntry"]
depends_on_stories: []
seed_id: "F-09.S-04"
---

# US-04: Breakage report + CSV export

## Description

**Actor:** CLIENT_ADMIN
**Trigger:** Admin opens **Redemption → Breakage**.

**Steps:**
1. Admin selects a date range, optional currency, and granularity (MONTH/QUARTER).
2. The table renders expired totals per period + currency.
3. Admin clicks **Export CSV** to download the aggregate report.

**Expected outcome:** A breakage report (aggregate-only) of expired balances by currency type and period; CSV export downloads with formula-injection-safe cells.

**Negative paths:**
- `to < from` or range > 24 months → `400`, surfaced inline.
- Excess exports → `429` with retry guidance.
- Missing `action.redemption.expiration.view_breakage` → no access (403 / route guard).

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/expiration/breakage` returns `BalanceBreakageReportResponse` aggregating `LedgerEntry` where `entry_type = EXPIRY`, grouped by period + currency, honoring `from`/`to`/`currencyId`/`granularity` (FR-09.6).
- **AC-2:** `GET /api/v1/redemption/expiration/breakage/export` returns `text/csv`; every string cell is escaped for CSV formula injection (CWE-1236) via the shared `CsvUtil.escapeCsv`; audit row `action=DATA_EXPORTED`, `resourceType=BALANCE_EXPIRY_BREAKAGE_EXPORT`.
- **AC-3:** The export is rate-limited via `AnalyticsExportRateLimiter` (per tenant) → `429` + `Retry-After` on excess.
- **AC-4:** `to < from` or a range exceeding 24 months → `400`.
- **AC-5:** Request lacking `action.redemption.expiration.view_breakage` → `403`; cross-tenant breakage is isolated (never includes another tenant's expiries).
- **AC-6:** The report is **aggregate-only** — counts + summed amounts per currency/period, no per-user identity or PII.

---

## Out of Scope

- Policy configuration — **US-01**.
- The batch that produces `EXPIRY` entries — **US-02/US-03** (this story only reads them).
- Reopening F-07/F-08 redemption analytics — breakage is F-09-owned; F-08 may link to it later.
- Per-user breakage drill-down — deferred (spec Out of Scope).

---

## UI States

- [ ] **Loading:** skeleton table rows while `GET /breakage` is in flight.
- [ ] **Empty:** no expiries in range → EmptyState "No expired balances in this period".
- [ ] **Error:** load failure → ErrorState + retry; toast "Could not load breakage report".

### Verbatim microcopy

- Button labels: "Export CSV", "Apply filters"
- Empty state: "No expired balances in this period"
- Error toast: "Could not load breakage report"
- Rate-limit toast: "You're exporting too frequently. Please wait a moment and try again."
- Range error: "End date must be on or after start date" / "Range cannot exceed 24 months"
- Granularity labels: "Monthly", "Quarterly"

### Conditional rendering

**Input: `granularity`**
- `MONTH`: period columns show month buckets.
- `QUARTER`: period columns show quarter buckets.

---

## Depends on

- **Foundation tasks:** F1 (`LedgerEntryType.EXPIRY`), F2, F3 (`LedgerEntryRepository.aggregateExpiryBreakage`), F4 (`action.redemption.expiration.view_breakage`)
- **Prior stories:** None for build (reads ledger). Meaningful data comes from US-03 at runtime; tests seed `EXPIRY` entries via fixtures.

---

## Spec references

- `## Functional Requirements` — FR-09.6
- `## DTOs [BE]` — `BalanceBreakageReportResponse`, `BreakageRowDto`
- `## API Endpoints [BE + FE]` — `GET /breakage`, `GET /breakage/export` + query params
- `## Service Layer [BE]` — `BalanceBreakageReportService.{getBreakage, exportBreakageCsv}`
- `## Security Design [BE]` — CSV formula-injection mitigation; `AnalyticsExportRateLimiter` export limit; aggregate-only
- `## Audit Trail [BE]` — CSV export audit
- `## Frontend Specification [FE]` — `BalanceBreakageReportPage`, `BreakageReportTable`
- `technical.md → ## Repository Queries [BE]` — `aggregateExpiryBreakage` native query; `## Hook Specs [FE]`

---

## BE tasks [BE]

### BE-1: DTOs
**Files:** `dto/response/BalanceBreakageReportResponse.java` (+ nested `BreakageRowDto`)

`BalanceBreakageReportResponse{from, to, granularity, rows}`; `BreakageRowDto{periodStart, periodEnd, currencyId, currencyDisplayName, expiredCount, totalExpiredAmount}` — aggregate-only. See `spec.md → ## DTOs [BE]`.

### BE-2: Service + unit test
**Files:** `service/redemption/BalanceBreakageReportService.java`, `util/CsvUtil.java` (promote `escapeCsv`), `test/.../service/redemption/BalanceBreakageReportServiceTest.java`

`getBreakage(from, to, currencyId, granularity)` aggregates via `LedgerEntryRepository.aggregateExpiryBreakage`; `exportBreakageCsv(...)` builds CSV with `CsvUtil.escapeCsv` on all string cells. Promote `escapeCsv` out of `RedemptionAnalyticsService` (currently `private`) into shared `CsvUtil` — do not duplicate. Unit test: aggregation correctness, currency filter, MONTH vs QUARTER bucketing, CSV escaping of a `=`-prefixed cell, empty range → header-only CSV.

### BE-3: Controller + @WebMvcTest
**Files:** `controller/BalanceExpirationController.java` (add the two GETs), `test/.../controller/BalanceExpirationControllerTest.java`

GET `/breakage`, GET `/breakage/export` (produces `text/csv`), each `@RequiresPermission("action.redemption.expiration.view_breakage")`. Inject `AnalyticsExportRateLimiter` in the export. @WebMvcTest: 200 report, 200 CSV (content-type), 400 bad range, 403 missing permission, 429 rate-limited.

### BE-4: Audit annotation
`@Audited(action="DATA_EXPORTED", resourceType="BALANCE_EXPIRY_BREAKAGE_EXPORT", description="Exported balance expiration breakage report")` on the export method.

---

## FE tasks [FE]

### FE-1: TypeScript types + service call
**Files:** `src/types/balanceExpiration.types.ts` (extend), `src/services/balanceExpiration.service.ts` (extend)

`getBreakage(params)` and a CSV download helper for `/breakage/export`. Copy types from contracts.

### FE-2: Hook
**File:** `src/hooks/useBalanceBreakage.ts`

`queryKey: ['balance-breakage', clientId, {from, to, currencyId, granularity}]`, staleTime 5 min, manual invalidation on filter change (per `technical.md → ## Hook Specs`).

### FE-3: Component + Vitest
**Files:** `src/components/balanceExpiration/BreakageReportTable.tsx`, `src/components/balanceExpiration/__tests__/BreakageReportTable.test.tsx`

Filter bar (date range, currency, granularity) + table (`periodStart, periodEnd, currencyDisplayName, expiredCount, totalExpiredAmount`) + Export CSV button. Currency label via `getCurrency(id).label`. Table scrolls horizontally on narrow viewports. Vitest: render rows, apply filter, empty state, export click triggers download, 429 toast.

### FE-4: Page wiring
**Files:** `src/pages/balanceExpiration/BalanceBreakageReportPage.tsx`, `src/App.tsx`

Route `/redemption/breakage` wrapped in `<ProtectedRoute permission="action.redemption.expiration.view_breakage">`; sidebar entry under "Redemption".

---

## E2E test [FE]

**Scenario 1:** `'breakage report renders rows'` _(covers AC-1)_
**File:** `e2e/balance-expiration-breakage.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Navigate to `/redemption/breakage` → set range + granularity → table renders |
| **APIs to mock** | `GET .../breakage?...` → 200 `BalanceBreakageReportResponse` with 2 rows |
| **Visible assertion** | `expect(page.getByText('Points')).toBeVisible()` and a non-zero `totalExpiredAmount` cell |
| **Negative case** | invalid range → `expect(page.getByText('End date must be on or after start date')).toBeVisible()` _(AC-4)_ |

**Scenario 2:** `'export CSV downloads'` _(covers AC-2)_
**File:** `e2e/balance-expiration-breakage.spec.ts`

| Field | Value |
|---|---|
| **User flow** | With a rendered report, click Export CSV → download begins |
| **APIs to mock** | `GET .../breakage/export?...` → 200 `text/csv` body |
| **Visible assertion** | download event fired (Playwright `waitForEvent('download')`) |

**Scenario 3:** `'empty state when no expiries'` _(covers AC-1)_
**File:** `e2e/balance-expiration-breakage.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Load report for a range with no expiries |
| **APIs to mock** | `GET .../breakage?...` → 200 `{rows: []}` |
| **Visible assertion** | `expect(page.getByText('No expired balances in this period')).toBeVisible()` |

---

## Execution checklist

**BE session:**
- [ ] `BalanceBreakageReportResponse.java` + `BreakageRowDto` created (aggregate-only) _(AC-1, AC-6)_
- [ ] `CsvUtil.escapeCsv` promoted from `RedemptionAnalyticsService` _(AC-2)_
- [ ] `BalanceBreakageReportService.{getBreakage,exportBreakageCsv}` added _(AC-1, AC-2)_
- [ ] `BalanceBreakageReportServiceTest` passes (aggregation, currency filter, granularity, CSV escaping, empty) _(AC-1, AC-2)_
- [ ] Controller GET `/breakage` + GET `/breakage/export` with `@RequiresPermission` + rate limiter _(AC-1, AC-2, AC-3, AC-5)_
- [ ] `@Audited` DATA_EXPORTED on export _(AC-2)_
- [ ] `BalanceExpirationControllerTest` @WebMvcTest passes (200/400/403/429, CSV content-type) _(AC-3, AC-4, AC-5)_

**FE session:**
- [ ] `balanceExpiration.types.ts` / `.service.ts` extended for breakage + export
- [ ] `useBalanceBreakage` hook created _(AC-1)_
- [ ] `BreakageReportTable` component (filters + table + export) _(AC-1)_
- [ ] `BreakageReportTable.test.tsx` Vitest passes _(AC-1, AC-4)_
- [ ] UI states: loading, empty, error; 429 toast _(AC-3)_
- [ ] Page wired to real API + ProtectedRoute + sidebar _(AC-5)_
- [ ] E2E `balance-expiration-breakage.spec.ts` scenarios pass _(AC-1, AC-2, AC-4)_

---

## Done when

1. **BE:** `./gradlew test` passes — `BalanceBreakageReportServiceTest` + `BalanceExpirationControllerTest` (breakage cases) green.
2. **FE:** `npm run test` + `npx playwright test e2e/balance-expiration-breakage.spec.ts` pass against real BE.
3. Every AC is referenced by at least one passing test.
