---
id: US-01
title: "View approval queue"
layers: ["BE", "FE"]
seed_id: "F-04.S-01"
touches_entities: ["RedemptionRequest"]
depends_on_stories: []
---

# US-01: View approval queue

## Description

**Actor:** CLIENT_ADMIN or ACTIVITY_APPROVER
**Trigger:** User navigates to `/redemption/approval-queue` — either from the sidebar "Approval Queue" link under Redemption or directly via URL.

**Steps:**
1. Page loads with `ApprovalQueuePage` rendered inside `AppLayout` + `ProtectedRoute permission="action.redemption.approve"`
2. `useApprovalQueue(filters)` fires `GET /api/v1/redemption/requests/approval-queue` with default filters (no date range, no currency, requestType=REDEMPTION, page=0, size=20)
3. `ApprovalQueueTable` renders a row per item showing: requesting user display name, catalog item name, currency ID, amount, wallet type, submitted date; Approve and Reject action buttons (wired in US-02 and US-03)
4. `ApprovalQueueFilters` renders filter controls: date range (from/to), currency selector, request type selector, catalog item selector
5. Changing any filter re-fires the query with updated params; pagination controls navigate pages

**Expected outcome:** Paginated, filterable table of PENDING_APPROVAL redemption requests sorted newest-first. Empty state shown when no items. `requestType=RETURN` always renders the empty state.

**Negative paths:**
- `size > 50` → 400 "Page size must not exceed 50"
- Invalid date format → 400
- PARTNER_SELLER JWT → 403 (sidebar item also hidden)
- No JWT → 401

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/requests/approval-queue` with a valid `action.redemption.approve` JWT returns 200 + `PaginatedResponse<ApprovalQueueItemResponse>` containing only `PENDING_APPROVAL` items for the caller's tenant, sorted `submittedAt DESC`
- **AC-2:** Each `ApprovalQueueItemResponse` contains: `id`, `requesterDisplayName`, `catalogItemName`, `currencyId`, `amount` (minor units), `walletType`, `submittedAt`
- **AC-3:** Optional filters `startDate`, `endDate`, `currencyId`, `catalogItemId` narrow results correctly; `requestType=RETURN` always returns empty `data[]`; `size > 50` returns 400; unknown `requestType` value returns 400
- **AC-4:** No JWT → 401; `PARTNER_SELLER` JWT → 403; cross-tenant isolation enforced — queue returns only the caller's tenant items regardless of what's in the DB for other tenants
- **AC-5:** FE renders loading skeleton while query is in flight; renders `EmptyState` with heading "No pending redemptions" when `data[]` is empty; renders error toast "Could not load approval queue" on 5xx

---

## Out of Scope

- Approve and Reject action button implementations (wired in US-02 and US-03 respectively — buttons are rendered in the table but the click handlers are no-ops until those stories)
- Full RETURN queue implementation (F-06 — `requestType=RETURN` is a filter stub in F-04)
- Approver notification when new item enters queue (not in Phase 1 FRs)

---

## UI States

- [ ] **Loading:** Skeleton rows (3–5) in `ApprovalQueueTable` while `useApprovalQueue` is fetching
- [ ] **Empty:** `EmptyState` with heading "No pending redemptions" and description "No redemption requests are pending approval." — shown when `totalElements = 0` (including when `requestType=RETURN` filter applied)
- [ ] **Error:** Toast "Could not load approval queue" with retry CTA; table area shows error fallback

### Verbatim microcopy

- Empty heading: "No pending redemptions"
- Empty description: "No redemption requests are pending approval."
- Error toast: "Could not load approval queue"
- Filter labels: "Date from", "Date to", "Currency", "Request type", "Catalog item"
- Request type options: "Redemption", "Return"
- Table column headers: "Requester", "Item", "Currency", "Amount", "Wallet", "Submitted", "Actions"
- Approve button (row action): "Approve" (wired in US-02)
- Reject button (row action): "Reject" (wired in US-03)

### Conditional rendering

**Input: `requestType` filter**
- `REDEMPTION` (default): table shows PENDING_APPROVAL redemption items normally
- `RETURN`: table shows empty state "No pending redemptions" (not an error — stub behavior until F-06)

**Input: caller permission**
- Has `action.redemption.approve`: sidebar "Approval Queue" item visible; page accessible
- Missing `action.redemption.approve`: sidebar item hidden; direct URL access redirected by `ProtectedRoute`

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** None

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-04.1, FR-04.2, FR-04.6, FR-04.7
- `spec.md → ## API Endpoints [BE + FE]` — `GET /approval-queue` row; query params table
- `spec.md → ## DTOs [BE]` — `ApprovalQueueItemResponse` record fields
- `spec.md → ## Service Layer [BE]` — `getApprovalQueue()` business rules; JOIN FETCH note
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.approve`; sidebar entry
- `spec.md → ## Security Design [BE]` — input validation table (requestType allowlist, size cap, date format)
- `spec.md → ## Frontend Specification [FE]` — `ApprovalQueuePage`, `ApprovalQueueTable`, `ApprovalQueueFilters` components; `useApprovalQueue` hook; route entry
- `spec.md → ## Edge Cases` — #1 (empty queue), #6 (RETURN filter stub), #7 (size > 50)
- `technical.md → ## Package Layout [BE]` — controller and service file paths
- `technical.md → ## Package Layout [FE]` — component, hook, service, page file paths
- `technical.md → ## Repository Queries [BE]` — `findApprovalQueue` JPQL with JOIN FETCH
- `technical.md → ## Hook Specs [FE]` — `useApprovalQueue` query key + staleTime

---

## BE tasks [BE]

### BE-1: Response DTO

**File:** `src/main/java/com/tenxengage/app/dto/response/redemption/ApprovalQueueItemResponse.java`

Java record with fields: `UUID id`, `String requesterDisplayName`, `String catalogItemName`, `String currencyId`, `BigDecimal amount`, `WalletType walletType`, `Instant submittedAt`.

Static factory: `from(RedemptionRequest r)` — reads `r.getUser().getDisplayName()` and `r.getCatalogItem().getName()` from JOIN FETCH-loaded associations (no extra queries).

See `spec.md → ## DTOs [BE] → Response DTOs`.

### BE-2: Service method + unit test

**Files:** `src/main/java/com/tenxengage/app/service/redemption/RedemptionApprovalService.java`, `src/test/java/com/tenxengage/app/service/redemption/RedemptionApprovalServiceTest.java`

`getApprovalQueue(filters, pageable)` — `@Transactional(readOnly = true)`:
1. Resolve `clientId` from `tenantValidator.getCurrentClientId()`
2. If `requestType == RETURN` → return empty `Page` (stub; FR-04.6)
3. Call `redemptionRequestRepository.findApprovalQueue(clientId, filters..., pageable)`
4. Map page: `page.map(ApprovalQueueItemResponse::from)`
5. Controller wraps result in `PaginatedResponse.from(page)`

Unit test coverage: happy path returns mapped page; `requestType=RETURN` returns empty; filters passed through correctly.

See `spec.md → ## Service Layer [BE]` and `technical.md → ## Repository Queries [BE]`.

### BE-3: Controller + @WebMvcTest

**Files:** `src/main/java/com/tenxengage/app/controller/redemption/RedemptionApprovalController.java`, `src/test/java/com/tenxengage/app/controller/redemption/RedemptionApprovalControllerTest.java`

`@RestController @RequestMapping("/api/v1/redemption/requests") @Tag(name = "Redemption Approval Queue")`

```
@GetMapping("/approval-queue")
@RequiresPermission("action.redemption.approve")
public ResponseEntity<PaginatedResponse<ApprovalQueueItemResponse>> getApprovalQueue(
    @RequestParam(required = false) String currencyId,
    @RequestParam(required = false) UUID catalogItemId,
    @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate startDate,
    @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate endDate,
    @RequestParam(required = false) RedemptionRequestType requestType,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") @Max(50) int size)
```

If `size > 50` → throw `IllegalArgumentException("Page size must not exceed 50")` → 400.

@WebMvcTest coverage (parameterized where applicable):
- 200 happy path with valid JWT; assert `PaginatedResponse` wrapper shape
- 403 with PARTNER_SELLER JWT
- 401 with no JWT
- 400 with `size=51`
- 400 with unknown `requestType` value
- 200 with `requestType=RETURN` → empty data array

---

## FE tasks [FE]

### FE-1: TypeScript types + service call

**Files:** `src/types/redemption/redemption.types.ts` (extend), `src/services/redemption/redemption-approval.service.ts`

Copy `ApprovalQueueItem` type from `../tenxengage-contracts/` after `/generate-contracts` — do not hand-write. Add `RedemptionRequestType` string union type.

Service: `getApprovalQueue(filters: ApprovalQueueFilters): Promise<PaginatedResponse<ApprovalQueueItem>>` — `GET /api/v1/redemption/requests/approval-queue` with query params.

### FE-2: Hook

**File:** `src/hooks/redemption/useApprovalQueue.ts`

```ts
queryKey: ['approval-queue', clientId, { currencyId, catalogItemId, startDate, endDate, requestType, page, size }]
staleTime: 5 * 60 * 1000  // 5 min
```

See `technical.md → ## Hook Specs [FE] → useApprovalQueue`.

### FE-3a: ApprovalQueueTable component + test

**Files:** `src/components/redemption/ApprovalQueueTable.tsx`, `src/components/redemption/__tests__/ApprovalQueueTable.test.tsx`

Props: `items: ApprovalQueueItem[], pagination: PaginationMeta, onApprove: (id: string) => void, onReject: (id: string) => void, isLoading: boolean`

Renders shadcn `<Table>` with columns: Requester, Item, Currency, Amount, Wallet, Submitted, Actions (Approve + Reject buttons — handlers wired in US-02/US-03; stub `onClick` here).

Loading state: render skeleton rows when `isLoading=true`. Empty state: render `<EmptyState>` when `items.length === 0`.

Vitest tests: renders correct columns; shows skeleton on loading; shows EmptyState on empty items; calls `onApprove` / `onReject` with correct id.

### FE-3b: ApprovalQueueFilters component + test

**Files:** `src/components/redemption/ApprovalQueueFilters.tsx`, `src/components/redemption/__tests__/ApprovalQueueFilters.test.tsx`

Props: `filters: ApprovalQueueFilters, onChange: (filters: ApprovalQueueFilters) => void`

Renders: date-from picker (shadcn `<Calendar>` via `<Popover>`), date-to picker, currency `<Select>`, request type `<Select>` (options: Redemption, Return), catalog item `<Select>`.

Vitest tests: changing each filter calls `onChange` with updated value.

### FE-4: Page wiring

**Files:** `src/pages/redemption/ApprovalQueuePage.tsx`, `src/App.tsx` (add route)

`ApprovalQueuePage` composes `ApprovalQueueFilters` + `ApprovalQueueTable` + pagination controls. Manages filter state locally; passes to `useApprovalQueue`.

Route entry in `App.tsx` (follow existing nesting pattern):
```tsx
<Route element={<ProtectedRoute permission="action.redemption.approve" />}>
  <Route element={<AppLayout />}>
    <Route path="/redemption/approval-queue" element={<ApprovalQueuePage />} />
  </Route>
</Route>
```

Sidebar entry in redemption nav section:
```ts
{ label: "Approval Queue", path: "/redemption/approval-queue", permissionKey: "action.redemption.approve" }
```

---

## E2E test [FE]

**File:** `e2e/redemption-approval-queue.spec.ts`

---

**Scenario 1:** `'approval queue renders items for authorized user'` _(covers AC-1, AC-2)_

| Field | Value |
|---|---|
| **User flow** | Login as CLIENT_ADMIN → navigate to `/redemption/approval-queue` → assert table visible with mocked items |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests/approval-queue` → 200 + `{ data: [{ id, requesterDisplayName, catalogItemName, currencyId, amount, walletType, submittedAt }], page: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false }` |
| **Visible assertion** | `expect(page.getByRole('table')).toBeVisible()`; `expect(page.getByText('Test User')).toBeVisible()` |
| **Negative case** | — |

---

**Scenario 2:** `'approval queue shows empty state when no items'` _(covers AC-5)_

| Field | Value |
|---|---|
| **User flow** | Login as CLIENT_ADMIN → navigate to `/redemption/approval-queue` → queue returns empty response → empty state visible |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests/approval-queue` → 200 + `{ data: [], totalElements: 0, ... }` |
| **Visible assertion** | `expect(page.getByText('No pending redemptions')).toBeVisible()` |
| **Negative case** | — |

---

**Scenario 3:** `'RETURN filter shows empty state (stub behavior)'` _(covers AC-3)_

| Field | Value |
|---|---|
| **User flow** | Navigate to queue → change request type filter to "Return" → mock returns empty → empty state shown |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests/approval-queue?requestType=RETURN*` → 200 + `{ data: [], totalElements: 0, ... }` |
| **Visible assertion** | `expect(page.getByText('No pending redemptions')).toBeVisible()` |
| **Negative case** | — |

---

**Scenario 4:** `'approval queue hidden from PARTNER_SELLER'` _(covers AC-4)_

| Field | Value |
|---|---|
| **User flow** | Login as PARTNER_SELLER → sidebar rendered → "Approval Queue" item not visible |
| **APIs to mock via `page.route()`** | Auth mock returning PARTNER_SELLER permissions (no `action.redemption.approve`) |
| **Visible assertion** | `expect(page.getByText('Approval Queue')).not.toBeVisible()` (sidebar item absent) |
| **Negative case** | Direct navigation to `/redemption/approval-queue` → redirected |

---

## Execution checklist

**BE session:**
- [ ] `ApprovalQueueItemResponse.java` record created with `from(RedemptionRequest)` factory _(AC-1, AC-2)_
- [ ] `RedemptionApprovalService.java` created with `getApprovalQueue()` method; `requestType=RETURN` returns empty Page _(AC-1, AC-3)_
- [ ] `RedemptionApprovalServiceTest` unit tests pass: happy path, RETURN filter, filter passthrough _(AC-1, AC-3)_
- [ ] `RedemptionApprovalController.java` created with `GET /approval-queue`, `@RequiresPermission("action.redemption.approve")`, `PaginatedResponse.from()` wrap _(AC-1, AC-4)_
- [ ] `size > 50` guard throws `IllegalArgumentException` → 400 _(AC-3)_
- [ ] `RedemptionApprovalControllerTest` @WebMvcTest passes: 200, 403, 401, 400 (size), 400 (bad requestType), RETURN empty _(AC-1, AC-3, AC-4)_

**FE session:**
- [ ] `ApprovalQueueItem` TypeScript type added from contracts; `ApprovalQueueFilters` type defined _(AC-2)_
- [ ] `redemptionApprovalService.getApprovalQueue()` service call added _(AC-1)_
- [ ] `useApprovalQueue` hook created with correct `queryKey` + `staleTime: 5 * 60 * 1000` _(AC-1)_
- [ ] `ApprovalQueueTable` component renders table; loading skeleton; empty state _(AC-2, AC-5)_
- [ ] `ApprovalQueueTable.test.tsx` Vitest tests pass: renders columns, skeleton, empty state _(AC-2, AC-5)_
- [ ] `ApprovalQueueFilters` component renders all filters; `onChange` fires on change _(AC-3)_
- [ ] `ApprovalQueueFilters.test.tsx` Vitest tests pass _(AC-3)_
- [ ] `ApprovalQueuePage` composed and wired to real API _(AC-1)_
- [ ] Route added to `App.tsx` with `ProtectedRoute permission="action.redemption.approve"` _(AC-4)_
- [ ] Sidebar entry added with `permissionKey: "action.redemption.approve"` _(AC-4)_
- [ ] E2E Scenario 1 passes: queue renders items _(AC-1, AC-2)_
- [ ] E2E Scenario 2 passes: empty state shown _(AC-5)_
- [ ] E2E Scenario 3 passes: RETURN filter stub _(AC-3)_
- [ ] E2E Scenario 4 passes: PARTNER_SELLER sidebar hidden _(AC-4)_

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionApprovalServiceTest` + `RedemptionApprovalControllerTest` all green
2. **FE:** `npm run test` passes + `npx playwright test e2e/redemption-approval-queue.spec.ts` passes against real BE
3. Every AC (AC-1 through AC-5) is referenced by at least one passing test
