---
id: US-02
title: "View company redemption history"
seed_id: "F-05.S-02"
layers: ["BE", "FE"]
touches_entities: ["RedemptionRequest"]
depends_on_stories: ["US-01"]
---

# US-02: View company redemption history

## Description

**Actor:** `PARTNER_ADMIN`
**Trigger:** PARTNER_ADMIN clicks the "Company" tab on `TransactionHistoryPage` (created in US-01)

**Steps:**
1. `TransactionHistoryPage` renders with a "Personal" tab (active by default) and a "Company" tab (only visible to PARTNER_ADMIN — gated by role check)
2. PARTNER_ADMIN clicks "Company" tab → `useCompanyRedemptions` fires `GET /api/v1/redemption/requests/company`
3. Service resolves caller's `partnerCompanyId` from JWT, looks up COMPANY wallet IDs for that company, filters `redemption_requests` by those wallet IDs
4. Results display in the same `TransactionHistoryTable` component with the same filter controls
5. Clicking a row opens `TransactionDetailSheet` (reused from US-01)

**Expected outcome:** PARTNER_ADMIN sees all redemptions originating from the company COMPANY wallet, filtered and paginated. PARTNER_SELLER sees no Company tab at all.

**Negative paths:**
- PARTNER_SELLER navigates to `/redemption/history` → only Personal tab rendered, no Company tab
- Company endpoint called without `action.redemption.view_history` → 403
- Cross-tenant call → 404
- No company wallet exists yet → 200 with empty list

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/requests/company` returns 200 with `PaginatedResponse<RedemptionRequestResponse>` containing only records from the caller's company COMPANY wallet
- **AC-2:** Filters (dateFrom, dateTo, status, category) work on company history with identical validation rules to US-01 (400 for bad enum, 422 for dateFrom > dateTo, 400 for pageSize > 50)
- **AC-3:** `PARTNER_SELLER` calling `GET /api/v1/redemption/requests/company` returns 403
- **AC-4:** Cross-tenant call returns 404 (Hibernate tenant filter enforced)
- **AC-5:** FE Company tab is only rendered in the DOM when the caller has `PARTNER_ADMIN` role; PARTNER_SELLER session shows only the Personal tab

---

## Out of Scope

- Personal wallet history (US-01)
- Export from company tab (US-03 creates the ExportDialog; this story does not wire it — the export button appears after US-03 FE is merged)
- CLIENT_ADMIN all-tenant view (US-04)

---

## UI States

- [ ] **Loading:** Skeleton rows in company tab while `useCompanyRedemptions` is in-flight
- [ ] **Empty:** "No company transactions yet"
- [ ] **Empty (with filters):** "No company transactions match your filters"
- [ ] **Error:** Error state in tab body with "Could not load company transactions" + Retry

### Verbatim microcopy

- Tab labels: "Personal" and "Company"
- Empty (no filters): "No company transactions yet"
- Empty (with filters): "No company transactions match your filters — try adjusting the filters"

### Conditional rendering

**Input: caller role**
- `PARTNER_ADMIN`: tabs bar shows both "Personal" and "Company" tabs
- `PARTNER_SELLER`: tabs bar is absent; only the personal history table is shown (no tab switcher rendered at all)

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 FE (TransactionHistoryPage must exist to add the company tab)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-05.5
- `spec.md → ## API Endpoints [BE + FE]` — `GET /api/v1/redemption/requests/company`
- `spec.md → ## Service Layer [BE]` — `RedemptionHistoryService.getCompanyHistory()`
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.view_history`
- `spec.md → ## Security Design [BE]` — 404 cross-tenant, 403 wrong role
- `technical.md → ## Package Layout [BE]` — `RedemptionHistoryController.java` (new)
- `technical.md → ## Repository Queries [BE]` — `findCompanyHistory`
- `technical.md → ## Package Layout [FE]` — `useCompanyRedemptions`
- `technical.md → ## Hook Specs [FE]` — `useCompanyRedemptions`

---

## BE tasks [BE]

### BE-1: RedemptionHistoryController + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/redemption/RedemptionHistoryController.java` — new controller at `@RequestMapping("/api/v1/redemption/requests")` (same base path); add `@GetMapping("/company")` with `@RequiresPermission("action.redemption.view_history")`; same filter `@RequestParam` set as US-01; validates `dateFrom ≤ dateTo`; delegates to `RedemptionHistoryService.getCompanyHistory()`
- `src/test/java/com/tenxengage/app/controller/redemption/RedemptionHistoryControllerTest.java`

@WebMvcTest cases:
- `GET /requests/company` as PARTNER_ADMIN → 200 with paginated response
- `GET /requests/company?status=COMPLETED` → 200 filtered
- `GET /requests/company?dateFrom=2026-06-10&dateTo=2026-06-01` → 422
- `GET /requests/company` as PARTNER_SELLER → 403
- `GET /requests/company` wrong tenant → 404

### BE-2: RedemptionHistoryService.getCompanyHistory() + unit test

**Files:**
- `src/main/java/com/tenxengage/app/service/redemption/RedemptionHistoryService.java` — add `getCompanyHistory(UUID userId, RedemptionHistoryFilters filters, Pageable pageable)`: resolve caller's `partnerCompanyId` from user record → look up COMPANY `RewardWallet` IDs for that company → call `RedemptionHistoryRepository.findCompanyHistory(walletId, clientId, …)` → map to `RedemptionRequestResponse`
- `src/test/java/com/tenxengage/app/service/redemption/RedemptionHistoryServiceTest.java` — add test cases for `getCompanyHistory`

Unit test covers:
- `getCompanyHistory` returns only COMPANY wallet records (not INDIVIDUAL)
- `getCompanyHistory` with status filter
- `getCompanyHistory` when no COMPANY wallet exists → empty page
- `getCompanyHistory` cross-tenant → empty page

---

## FE tasks [FE]

### FE-1: Types + service call

No new types needed beyond US-01. Add to `src/services/redemption-history/redemption-history.service.ts`:
- `getCompanyRedemptions(filters, page, pageSize)` — `GET /api/v1/redemption/requests/company`

### FE-2: useCompanyRedemptions hook

**File:** `src/hooks/redemption-history/useCompanyRedemptions.ts`
- `queryKey: ['redemption-history', 'company', { filters, page }]`, `staleTime: 2 * 60 * 1000`

### FE-3: Company tab in TransactionHistoryPage

**File:** `src/pages/redemption-history/TransactionHistoryPage.tsx` — modify (extends US-01 FE):
- Wrap existing content in a tabs layout using `<Tabs>` from shadcn/ui
- "Personal" tab: existing `usePersonalRedemptions` flow
- "Company" tab: `useCompanyRedemptions` flow — rendered only when `can('action.redemption.view_history') && isPartnerAdmin` (use `usePermissions()` + user role check)
- Shared `TransactionHistoryTable` and `HistoryFilterBar` components

Vitest test (in `TransactionHistoryPage` test file):
- PARTNER_ADMIN renders both tabs
- PARTNER_SELLER renders only Personal tab (no Company tab in DOM)

---

## E2E test [FE]

**File:** `e2e/redemption-history.spec.ts`

---

**Scenario 1:** `'PARTNER_ADMIN sees company tab and company redemptions'` _(covers AC-1, AC-2, AC-5)_

| Field | Value |
|---|---|
| **User flow** | Log in as PARTNER_ADMIN → navigate to `/redemption/history` → verify both "Personal" and "Company" tabs visible → click "Company" tab → company redemptions load → apply status filter → filtered results |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests/company` → 200 with `PaginatedResponse` containing 2 company redemptions; filtered call → 200 with 1 item |
| **Visible assertion** | `expect(page.getByRole('tab', { name: 'Company' })).toBeVisible()`; `expect(page.getByRole('row')).toHaveCount(3)` |

---

**Scenario 2:** `'PARTNER_SELLER sees only Personal tab'` _(covers AC-3, AC-5)_

| Field | Value |
|---|---|
| **User flow** | Log in as PARTNER_SELLER → navigate to `/redemption/history` → verify Company tab is not rendered |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests` → 200 with personal items |
| **Visible assertion** | `expect(page.getByRole('tab', { name: 'Company' })).not.toBeInViewport()` (tab absent from DOM) |

---

## Execution checklist

**BE session:**
- [ ] `RedemptionHistoryController.java` created with `GET /company` endpoint _(AC-1, AC-3, AC-4)_
- [ ] `RedemptionHistoryService.getCompanyHistory()` added: resolves partnerCompanyId → COMPANY walletId → delegates to repo _(AC-1)_
- [ ] `RedemptionHistoryServiceTest` company history cases pass (no filter, filtered, empty company, cross-tenant) _(AC-1, AC-2, AC-4)_
- [ ] `RedemptionHistoryControllerTest` company endpoint cases pass (200, 403, 404, filter validation) _(AC-1, AC-2, AC-3, AC-4)_

**FE session:**
- [ ] `getCompanyRedemptions` service call added to `redemption-history.service.ts` _(AC-1)_
- [ ] `useCompanyRedemptions` hook created: correct queryKey, staleTime 2min _(AC-1)_
- [ ] `TransactionHistoryPage` updated: tabs added; Company tab conditional on role _(AC-5)_
- [ ] Vitest test: PARTNER_ADMIN sees both tabs; PARTNER_SELLER sees only Personal _(AC-5)_
- [ ] `npm run build` — no TypeScript errors
- [ ] E2E Scenario 1 passes _(AC-1, AC-2, AC-5)_
- [ ] E2E Scenario 2 passes _(AC-3, AC-5)_

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionHistoryServiceTest` (company cases) + `RedemptionHistoryControllerTest` (company cases) green
2. **FE:** `npm run test` passes + `npx playwright test e2e/redemption-history.spec.ts -g 'company'` passes against real BE
3. Every AC (AC-1 through AC-5) referenced by at least one passing test
