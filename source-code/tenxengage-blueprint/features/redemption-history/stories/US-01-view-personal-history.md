---
id: US-01
title: "View personal redemption history"
seed_id: "F-05.S-01"
layers: ["BE", "FE"]
touches_entities: ["RedemptionRequest"]
depends_on_stories: []
---

# US-01: View personal redemption history

## Description

**Actor:** `PARTNER_SELLER` or `PARTNER_ADMIN`
**Trigger:** User navigates to `/redemption/history`

**Steps:**
1. Page loads and calls `GET /api/v1/redemption/requests` — returns paginated list with `catalogItemName` and `completedAt`
2. User optionally applies filters: date range (`dateFrom`/`dateTo`), `status` (RedemptionStatus enum), `category` (CASH / NON_CASH)
3. Table re-fetches with active filters; results update
4. User clicks a row — `TransactionDetailSheet` slides open from the right showing full lifecycle detail
5. Detail shows all timestamps, `vendorReferenceId` (only when COMPLETED), failure reason (only when FAILED), and `linkedReturnId` link row (only when non-null)

**Expected outcome:** Paginated, filterable list renders correctly with catalog item names and completion timestamps. Detail sheet shows the full transaction record. Filters reset to show all transactions on clear.

**Negative paths:**
- Unknown `status` value → 400 returned; FE shows validation error before submit
- `dateFrom > dateTo` → 422; FE prevents submission with inline error
- `pageSize > 50` → 400; enforced at controller
- Unauthenticated → 401 redirect to `/login`
- Cross-tenant `GET /{id}` → 404

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/requests` with valid filter params returns 200 with `PaginatedResponse<RedemptionRequestResponse>` where every item includes `catalogItemName` (string) and `completedAt` (Instant, nullable)
- **AC-2:** Filter `status=COMPLETED` returns only COMPLETED rows; `category=CASH` returns only CASH rows; combined filters are AND'd; `dateFrom`/`dateTo` filter `submittedAt` (inclusive)
- **AC-3:** Invalid `status` enum value returns 400; `dateFrom > dateTo` returns 422; `pageSize > 50` returns 400
- **AC-4:** `GET /api/v1/redemption/requests/{id}` returns `RedemptionRequestDetailResponse` including new nullable field `linkedReturnId`; `vendorReferenceId` only present when `status=COMPLETED`; `failureReason` only present when `status=FAILED`
- **AC-5:** `PARTNER_SELLER` calling `GET /api/v1/redemption/requests/all` returns 403; cross-tenant `GET /requests/{id}` returns 404
- **AC-6:** FE renders two distinct empty states: "No transactions yet" (no active filters + 0 results); "No transactions match your filters" (≥1 active filter + 0 results)

---

## Out of Scope

- Company wallet history (US-02)
- Export functionality (US-03)
- CLIENT_ADMIN all-tenant view (US-04)
- Return submission from detail (F-06)
- `linkedReturnId` navigation (F-06 must be deployed first; this story only renders the UUID value)

---

## Non-Functional Notes

- **Telemetry:** `redemption.history.list.duration_ms` histogram label `scope=personal` — emit on every list call per `spec.md → ## Observability`
- **Perf:** List endpoint P95 < 300ms (spec default); `LEFT JOIN FETCH r.catalogItem` in JPQL prevents N+1 on `catalogItemName`

---

## UI States

- [ ] **Loading:** Skeleton rows (5) in `TransactionHistoryTable` while `usePersonalRedemptions` is in-flight
- [ ] **Empty (no filters):** `EmptyState` with copy "No transactions yet"
- [ ] **Empty (with filters):** `EmptyState` with copy "No transactions match your filters — try adjusting the date range or status filter"
- [ ] **Error:** Error state in table area with "Could not load transactions" + Retry button; `toast.error` on repeated failure
- [ ] **Detail sheet loading:** Skeleton in sheet body while `useRedemptionDetail` is in-flight
- [ ] **Detail sheet error:** Error message in sheet: "Could not load transaction details"

### Verbatim microcopy

- Empty (no filters): "No transactions yet"
- Empty (with filters): "No transactions match your filters — try adjusting the date range or status filter"
- Page title: "Transaction history"
- Filter bar: Status placeholder: "All statuses"; Category placeholder: "All types"; Date range placeholder: "Select date range"
- Detail sheet title: "Transaction detail"
- Detail sheet close button aria-label: "Close transaction detail"
- Detail field label for return link: "Linked return"
- Loading toast on export trigger (US-03 adds this button, not this story)

### Conditional rendering

**Input: `transaction.status`**
- `COMPLETED`: show `vendorReferenceId` field; hide failure reason row
- `FAILED`: hide `vendorReferenceId`; show `failureReason` as mapped generic message
- `PENDING_APPROVAL`, `RESERVED`, `PROCESSING`, `CANCELLED`: hide both fields

**Input: `transaction.linkedReturnId`**
- Non-null: render "Linked return" row with UUID value (no navigation link until F-06 deploys)
- Null: omit "Linked return" row entirely

**Input: active filters**
- No filters active + 0 results: empty state copy A
- ≥1 filter active + 0 results: empty state copy B

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** None

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-05.1, FR-05.2, FR-05.3, FR-05.7
- `spec.md → ## Modified Existing Endpoints [BE + FE]` — changes to `GET /requests` and `GET /requests/{id}`
- `spec.md → ## DTOs [BE]` — `RedemptionRequestResponse` (+ catalogItemName, completedAt), `RedemptionRequestDetailResponse` (+ linkedReturnId)
- `spec.md → ## Service Layer [BE]` — `RedemptionHistoryService.getPersonalHistory()`, `getRedemptionDetail()`
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.view_history`
- `spec.md → ## Security Design [BE]` — sortBy allowlist, enum param validation, 404 cross-tenant
- `spec.md → ## Edge Cases [BE + FE]` — EC-1 (empty), EC-10 (timezone), EC-11 (pageSize), EC-12 (cross-tenant), EC-14 (two empty states)
- `spec.md → ## Frontend Specification [FE]` — `TransactionHistoryPage`, `TransactionHistoryTable`, `HistoryFilterBar`, `TransactionDetailSheet`
- `technical.md → ## Package Layout [BE]` — modified files table; new service/controller paths
- `technical.md → ## Repository Queries [BE]` — `findPersonalHistory`, `countPersonalHistory`
- `technical.md → ## Package Layout [FE]` — file paths for all FE files
- `technical.md → ## Hook Specs [FE]` — `usePersonalRedemptions`, `useRedemptionDetail`

---

## BE tasks [BE]

### BE-1: Update response DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/response/RedemptionRequestResponse.java` — add `catalogItemName: String`, `completedAt: Instant`; update `from()` factory signature to accept `catalogItemName` param
- `src/main/java/com/tenxengage/app/dto/response/RedemptionRequestDetailResponse.java` — add `linkedReturnId: UUID` (nullable); update `from()` factory

See `spec.md → ## DTOs [BE]` for field semantics. `vendorReferenceId` remains guarded on COMPLETED status only (existing behavior preserved).

### BE-2: RedemptionHistoryService + unit test

**Files:**
- `src/main/java/com/tenxengage/app/service/redemption/RedemptionHistoryService.java`
- `src/test/java/com/tenxengage/app/service/redemption/RedemptionHistoryServiceTest.java`

Methods:
- `getPersonalHistory(UUID userId, RedemptionHistoryFilters filters, Pageable pageable)` → `Page<RedemptionRequestResponse>` — `@Transactional(readOnly=true)`; delegates to `RedemptionHistoryRepository.findPersonalHistory()`; maps result via updated `RedemptionRequestResponse.from(req, catalogItemName)`
- `getRedemptionDetail(UUID id, UUID userId)` → `RedemptionRequestDetailResponse` — `@Transactional(readOnly=true)`; null-safe `linkedReturnId` lookup (null until F-06 deploys)

Unit test covers (parameterized where applicable):
- `getPersonalHistory` with no filters → returns all rows
- `getPersonalHistory` filtered by status=COMPLETED → only COMPLETED rows
- `getPersonalHistory` filtered by category=CASH → only CASH rows  
- `getPersonalHistory` filtered by date range → only in-range rows
- `getRedemptionDetail` COMPLETED → `vendorReferenceId` present, `linkedReturnId` null
- `getRedemptionDetail` FAILED → `failureReason` present, `vendorReferenceId` null
- `getPersonalHistory` cross-tenant → empty page (tenant filter active)

See `technical.md → ## Repository Queries [BE] → RedemptionHistoryRepository` for `@Query` bodies to use.

### BE-3: Update RedemptionRequestController + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/RedemptionRequestController.java` — add `@RequestParam(required=false) RedemptionStatus status`, `@RequestParam(required=false) RedemptionCategory category`, `@RequestParam(required=false) LocalDate dateFrom`, `@RequestParam(required=false) LocalDate dateTo` to `listRedemptions()`; validate `dateFrom ≤ dateTo`; delegate to `RedemptionHistoryService.getPersonalHistory()` instead of `RedemptionSubmissionService.getPersonalRedemptions()`
- `src/test/java/com/tenxengage/app/controller/RedemptionRequestControllerTest.java` — extend with filter cases

@WebMvcTest cases:
- `GET /requests` → 200 with updated response shape (catalogItemName, completedAt present)
- `GET /requests?status=COMPLETED` → 200 filtered
- `GET /requests?status=INVALID` → 400
- `GET /requests?dateFrom=2026-06-01&dateTo=2026-05-01` → 422 (dateFrom > dateTo)
- `GET /requests?pageSize=51` → 400
- `GET /requests/{id}` → 200 with linkedReturnId field (null)
- `GET /requests/{id}` without permission → 403
- `GET /requests/{id}` wrong tenant → 404

---

## FE tasks [FE]

### FE-1: TypeScript types + service call

**Files:**
- `src/types/redemption-history/redemption-history.types.ts` — copy types from `../tenxengage-contracts/` after contracts are generated; include `RedemptionRequestResponse`, `RedemptionRequestDetailResponse`, `PaginatedResponse<T>`, `RedemptionStatus`, `RedemptionCategory`
- `src/services/redemption-history/redemption-history.service.ts` — `getPersonalRedemptions(filters, page, pageSize)` and `getRedemptionDetail(id)` typed wrappers

See `technical.md → ## Package Layout [FE]`. Do NOT hand-write types — copy from contracts only.

### FE-2: Hooks

**Files:**
- `src/hooks/redemption-history/usePersonalRedemptions.ts` — `queryKey: ['redemption-history', 'personal', { filters, page }]`, `staleTime: 2 * 60 * 1000`
- `src/hooks/redemption-history/useRedemptionDetail.ts` — `queryKey: ['redemption-history', 'detail', id]`, `staleTime: 5 * 60 * 1000`

See `technical.md → ## Hook Specs [FE]`.

### FE-3a: TransactionHistoryTable component + Vitest test

**Files:**
- `src/components/redemption-history/TransactionHistoryTable.tsx` — uses `<Table>` from shadcn/ui; props: `data: RedemptionRequestResponse[]`, `isLoading: boolean`, `onRowClick: (id: UUID) => void`; columns: date, item name, currency + amount, status badge, completion date; skeleton rows on loading; pagination controls
- `src/components/redemption-history/__tests__/TransactionHistoryTable.test.tsx`

Vitest test covers: renders rows from data; shows skeleton on isLoading; shows empty state "No transactions yet"; calls `onRowClick` with correct id on row click.

### FE-3b: HistoryFilterBar component

**Files:**
- `src/components/redemption-history/HistoryFilterBar.tsx` — date range picker (react-day-picker v8 — not v9), status `<Select>`, category `<Select>`; emits `onFiltersChange(filters)` callback; inline validation: if dateFrom and dateTo both set and dateFrom > dateTo, show inline error "Start date must be before end date"
- Vitest test in same `__tests__/` folder: renders all filter controls; dateFrom > dateTo shows inline error; onChange called on valid filter change.

### FE-3c: TransactionDetailSheet component

**Files:**
- `src/components/redemption-history/TransactionDetailSheet.tsx` — uses `<Sheet>` from shadcn/ui (slide-out from right); props: `redemptionId: UUID | null`, `open: boolean`, `onClose: () => void`; driven by `useRedemptionDetail(id)` — fetches only when `redemptionId` is non-null; displays: submittedAt, processingStartedAt, completedAt, amount + currency, status badge, processingMode, walletType, vendorReferenceId (COMPLETED only), failureReason (FAILED only), linkedReturnId row (non-null only)
- Vitest test: renders field rows; COMPLETED status shows vendorReferenceId, not failureReason; FAILED shows failureReason, not vendorReferenceId; null linkedReturnId omits row; loading shows skeleton; onClose called on X click.

### FE-4: TransactionHistoryPage + route

**Files:**
- `src/pages/redemption-history/TransactionHistoryPage.tsx` — composes `HistoryFilterBar` + `TransactionHistoryTable` + `TransactionDetailSheet`; manages `filters` state and `selectedRedemptionId` state; calls `usePersonalRedemptions(filters, page)`; handles two empty states based on whether filters are active; sidebar entry "Transaction History" under "Redemption" section
- `src/App.tsx` — add route inside `<ProtectedRoute permission="module.redemption_store">`: `<Route path="/redemption/history" element={<TransactionHistoryPage />} />`

---

## E2E test [FE]

**File:** `e2e/redemption-history.spec.ts`

---

**Scenario 1:** `'personal history list renders with filters'` _(covers AC-1, AC-2)_

| Field | Value |
|---|---|
| **User flow** | Navigate to `/redemption/history` → table renders → apply `status=COMPLETED` filter → re-fetch → verify only COMPLETED rows shown → clear filter → all rows shown |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests` → 200 + `PaginatedResponse` with 2 COMPLETED items; filtered call → 200 with 1 item; cleared call → 200 with 3 items |
| **Visible assertion** | `expect(page.getByRole('row')).toHaveCount(3)` initially; after filter `toHaveCount(2)` (1 data + 1 header) |
| **Negative case** | n/a |

---

**Scenario 2:** `'transaction detail sheet opens on row click'` _(covers AC-4, AC-6)_

| Field | Value |
|---|---|
| **User flow** | Click first table row → `TransactionDetailSheet` slides in → verify fields rendered → COMPLETED row shows vendorReferenceId → click X → sheet closes |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests/{id}` → 200 with `RedemptionRequestDetailResponse` (status=COMPLETED, vendorReferenceId set, linkedReturnId=null) |
| **Visible assertion** | `expect(page.getByText('Transaction detail')).toBeVisible()`; `expect(page.getByText(vendorReferenceId)).toBeVisible()` |
| **Negative case** | After X click: `expect(page.getByText('Transaction detail')).not.toBeVisible()` |

---

**Scenario 3:** `'filter validation rejects invalid date range'` _(covers AC-3)_

| Field | Value |
|---|---|
| **User flow** | Set dateFrom = 2026-06-10, dateTo = 2026-06-01 → inline error appears before API call |
| **APIs to mock via `page.route()`** | None — client-side validation fires before request |
| **Visible assertion** | `expect(page.getByText('Start date must be before end date')).toBeVisible()` |

---

**Scenario 4:** `'empty state shows correct copy based on filter state'` _(covers AC-6)_

| Field | Value |
|---|---|
| **User flow** | 1) No filters → mocked 200 with empty array → "No transactions yet" shown. 2) Apply status filter → mocked 200 with empty array → "No transactions match your filters" shown |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests` → 200 with `PaginatedResponse` totalElements=0 |
| **Visible assertion** | `expect(page.getByText('No transactions yet')).toBeVisible()` then `expect(page.getByText('No transactions match your filters')).toBeVisible()` |

---

**Scenario 5:** `'PARTNER_SELLER cannot access all-tenant endpoint'` _(covers AC-5)_

| Field | Value |
|---|---|
| **User flow** | Direct call to `/api/v1/redemption/requests/all` mocked to 403 → FE handles gracefully (no crash, no broken layout) |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests/all` → 403 |
| **Visible assertion** | Page does not crash; no all-tenant tab or route accessible from PARTNER_SELLER session |

---

## Execution checklist

**BE session:**
- [ ] `RedemptionRequestResponse.java` updated: `catalogItemName` + `completedAt` added; `from()` factory updated _(AC-1)_
- [ ] `RedemptionRequestDetailResponse.java` updated: `linkedReturnId` added; `from()` factory updated _(AC-4)_
- [ ] `RedemptionHistoryService.java` created with `getPersonalHistory()` _(AC-1, AC-2)_
- [ ] `RedemptionHistoryService.java` `getRedemptionDetail()` added _(AC-4)_
- [ ] `RedemptionHistoryServiceTest.java` unit tests pass — no-filter, status filter, category filter, date filter, cross-tenant cases _(AC-1, AC-2)_
- [ ] `RedemptionRequestController.listRedemptions()` updated with filter params; delegates to `RedemptionHistoryService` _(AC-1, AC-2)_
- [ ] `RedemptionRequestController` validates `dateFrom ≤ dateTo` → 422 _(AC-3)_
- [ ] `RedemptionRequestControllerTest` extended: filter happy paths, 400/422/403/404 cases _(AC-1, AC-2, AC-3, AC-5)_

**FE session:**
- [ ] `redemption-history.types.ts` created with types copied from contracts _(AC-1)_
- [ ] `redemption-history.service.ts` created: `getPersonalRedemptions()` + `getRedemptionDetail()` _(AC-1, AC-4)_
- [ ] `usePersonalRedemptions` hook created: correct queryKey, staleTime 2min _(AC-1, AC-2)_
- [ ] `useRedemptionDetail` hook created: correct queryKey, staleTime 5min _(AC-4)_
- [ ] `TransactionHistoryTable.tsx` created + Vitest tests pass _(AC-1, AC-2)_
- [ ] `HistoryFilterBar.tsx` created + Vitest tests pass: dateFrom > dateTo inline error _(AC-3)_
- [ ] `TransactionDetailSheet.tsx` created + Vitest tests pass: COMPLETED/FAILED/null linkedReturnId conditional rendering _(AC-4, AC-6)_
- [ ] `TransactionHistoryPage.tsx` created: two empty states, filter state management _(AC-6)_
- [ ] Route added to `App.tsx`: `/redemption/history` inside ProtectedRoute _(AC-5)_
- [ ] `npm run build` — no TypeScript errors
- [ ] E2E Scenario 1 passes _(AC-1, AC-2)_
- [ ] E2E Scenario 2 passes _(AC-4, AC-6)_
- [ ] E2E Scenario 3 passes _(AC-3)_
- [ ] E2E Scenario 4 passes _(AC-6)_
- [ ] E2E Scenario 5 passes _(AC-5)_

---

## Done when

1. **BE:** `./gradlew test` passes — all `RedemptionHistoryServiceTest` + updated `RedemptionRequestControllerTest` cases green
2. **FE:** `npm run test` passes + `npx playwright test e2e/redemption-history.spec.ts -g 'personal history'` passes against real BE
3. Every AC (AC-1 through AC-6) referenced by at least one passing test
