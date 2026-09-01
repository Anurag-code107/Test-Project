---
id: US-04
title: "View and export tenant-wide history"
seed_id: "F-05.S-04"
layers: ["BE", "FE"]
touches_entities: ["RedemptionRequest", "RedemptionExportJob"]
depends_on_stories: ["US-03"]
---

# US-04: View and export tenant-wide history

## Description

**Actor:** `CLIENT_ADMIN`
**Trigger:** CLIENT_ADMIN navigates to `/redemption/admin/history`

**Steps:**
1. Page loads; `GET /api/v1/redemption/requests/all` called; returns `RedemptionAdminHistoryResponse` with `userDisplayName` and `partnerCompanyName` for each row
2. CLIENT_ADMIN optionally filters by: status, category, date range, `userId` (UUID), `companyId` (UUID)
3. Table renders with user/company display columns; clicking a row opens `TransactionDetailSheet` (reused from US-01)
4. CLIENT_ADMIN clicks "Export" → `ExportDialog` opens (reused from US-03) with `scope=ALL_TENANT`; export file includes user + company columns
5. Export flow is identical to US-03 async/sync threshold behavior

**Expected outcome:** CLIENT_ADMIN has full visibility into every tenant redemption with user/company identity. Export file includes requesting user and company name. PARTNER_SELLER/ADMIN cannot access this page.

**Negative paths:**
- PARTNER_SELLER or PARTNER_ADMIN navigates to `/redemption/admin/history` → redirected to `/home` by `ProtectedRoute`
- PARTNER_SELLER/ADMIN calling `GET /requests/all` API directly → 403
- Rate limit: 31st request within 1 minute → 429
- Cross-tenant query → 404

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/requests/all` returns 200 with `PaginatedResponse<RedemptionAdminHistoryResponse>` where every item includes `userDisplayName` (string) and `partnerCompanyName` (string)
- **AC-2:** `userId` and `companyId` filter params narrow results correctly; standard filters (status, category, dateFrom, dateTo) also apply with same validation (400/422) as US-01
- **AC-3:** PARTNER_SELLER or PARTNER_ADMIN calling `GET /requests/all` → 403
- **AC-4:** 31st `GET /requests/all` within 1 minute → 429 with `Retry-After` header (per-tenant rate limit)
- **AC-5:** CLIENT_ADMIN can trigger export from this page; export file for scope=ALL_TENANT includes `userDisplayName` and `partnerCompanyName` columns in addition to all standard transaction fields
- **AC-6:** FE page at `/redemption/admin/history` is only accessible to CLIENT_ADMIN; PARTNER_SELLER/PARTNER_ADMIN redirected to `/home`

---

## Out of Scope

- Personal history (US-01), company history (US-02), personal/company export (US-03 — export mechanism reused here)
- Return submission from detail (F-06)
- Advanced analytics by item/tier/region/cohort (Phase 3)

---

## Non-Functional Notes

- **N+1 prevention:** `getTenantHistory()` must resolve `userDisplayName` and `partnerCompanyName` via JOIN in JPQL (not N+1 per-row lookups). See `spec.md → ## Non-Functional Notes` for this story.
- **Rate limit:** `GET /requests/all` — 30 req/min per tenant via `RateLimitFilter`. See `spec.md → ## Security Design [BE]`.

---

## UI States

- [ ] **Loading:** Skeleton rows in `TenantTransactionHistoryPage` table
- [ ] **Empty:** "No tenant transactions yet"
- [ ] **Empty (with filters):** "No tenant transactions match your filters"
- [ ] **Error:** Error state with "Could not load tenant transactions" + Retry
- [ ] **ExportDialog:** Reuses all states from US-03 (idle → polling → completed/failed)

### Verbatim microcopy

- Page title: "Tenant transaction history"
- Empty (no filters): "No tenant transactions yet"
- Empty (with filters): "No tenant transactions match your filters"
- Table column header: "User"
- Table column header: "Company"

### Conditional rendering

**Input: ProtectedRoute**
- `CLIENT_ADMIN`: page renders normally
- Any other role: `ProtectedRoute` redirects to `/home` before page mounts

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-03 FE (`ExportDialog` component must exist to reuse here)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-05.6, FR-05.8
- `spec.md → ## API Endpoints [BE + FE]` — `GET /api/v1/redemption/requests/all`
- `spec.md → ## DTOs [BE]` — `RedemptionAdminHistoryResponse`
- `spec.md → ## Service Layer [BE]` — `RedemptionHistoryService.getTenantHistory()`
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.view_all_history`, `action.redemption.export`
- `spec.md → ## Security Design [BE]` — rate limit 30 req/min/tenant, 403 wrong role, 404 cross-tenant
- `spec.md → ## Edge Cases [BE + FE]` — EC-7 (CLIENT_ADMIN downloads another user's export), EC-12 (cross-tenant)
- `spec.md → ## Frontend Specification [FE]` — `TenantTransactionHistoryPage`, `useTenantRedemptions`
- `technical.md → ## Package Layout [BE]` — `RedemptionHistoryController` (add /all endpoint), `RedemptionAdminHistoryResponse`
- `technical.md → ## Repository Queries [BE]` — `findTenantHistory`, `countTenantHistory`
- `technical.md → ## Package Layout [FE]` — `TenantTransactionHistoryPage`, `useTenantRedemptions`
- `technical.md → ## Hook Specs [FE]` — `useTenantRedemptions`

---

## BE tasks [BE]

### BE-1: RedemptionAdminHistoryResponse DTO

**File:** `src/main/java/com/tenxengage/app/dto/response/redemption/RedemptionAdminHistoryResponse.java`

Fields: all fields from `RedemptionRequestResponse` + `userId: UUID`, `userDisplayName: String`, `partnerCompanyId: UUID`, `partnerCompanyName: String`

`from(RedemptionRequest req, String userName, String companyName)` static factory — not from entity alone (name resolution happens in service).

### BE-2: RedemptionHistoryService.getTenantHistory() + unit test

**Files:**
- `src/main/java/com/tenxengage/app/service/redemption/RedemptionHistoryService.java` — add `getTenantHistory(RedemptionAdminHistoryFilters filters, Pageable pageable)` → `Page<RedemptionAdminHistoryResponse>` (`@Transactional(readOnly=true)`): calls `RedemptionHistoryRepository.findTenantHistory(clientId, userId, ...)` with LEFT JOIN FETCH on catalog item, then resolves userDisplayName + partnerCompanyName via JOIN (not per-row lookup)
- `src/test/java/com/tenxengage/app/service/redemption/RedemptionHistoryServiceTest.java` — add tenant history cases

Unit test covers:
- `getTenantHistory` with no filters → all tenant records returned
- `getTenantHistory` with userId filter → only that user's records
- `getTenantHistory` with companyId filter → only that company's records
- `getTenantHistory` — `userDisplayName` and `partnerCompanyName` populated in response
- `getTenantHistory` cross-tenant → empty page (tenant filter)

### BE-3: RedemptionHistoryController `/all` endpoint + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/redemption/RedemptionHistoryController.java` — add `@GetMapping("/all")` with `@RequiresPermission("action.redemption.view_all_history")`; filter params: standard set + `userId` (`@RequestParam(required=false) UUID userId`) + `companyId` (`@RequestParam(required=false) UUID companyId`); delegates to `RedemptionHistoryService.getTenantHistory()`
- `src/test/java/com/tenxengage/app/controller/redemption/RedemptionHistoryControllerTest.java` — add admin endpoint cases

@WebMvcTest cases:
- `GET /requests/all` as CLIENT_ADMIN → 200 with `RedemptionAdminHistoryResponse` items (verify `userDisplayName` field present)
- `GET /requests/all?userId={uuid}` → 200 filtered
- `GET /requests/all` as PARTNER_SELLER → 403
- `GET /requests/all` as PARTNER_ADMIN → 403
- `GET /requests/all` wrong tenant → 404
- `GET /requests/all?status=INVALID` → 400

---

## FE tasks [FE]

### FE-1: Service call + types

Add to `src/services/redemption-history/redemption-history.service.ts`:
- `getTenantRedemptions(filters, page, pageSize)` — `GET /api/v1/redemption/requests/all`

Add `RedemptionAdminHistoryResponse` to `src/types/redemption-history/redemption-history.types.ts` — copied from contracts.

### FE-2: useTenantRedemptions hook

**File:** `src/hooks/redemption-history/useTenantRedemptions.ts`
- `queryKey: ['redemption-history', 'all-tenant', { filters, page }]`, `staleTime: 2 * 60 * 1000`
- `endpoint: GET /api/v1/redemption/requests/all`

### FE-3: TenantTransactionHistoryPage + Vitest test

**File:** `src/pages/redemption-history/TenantTransactionHistoryPage.tsx`
- Composes `HistoryFilterBar` (adds `userId` and `companyId` UUID filter inputs) + `TransactionHistoryTable` (with extra User and Company columns) + `TransactionDetailSheet` (reused) + Export button → `ExportDialog` with `scope='ALL_TENANT'` prop
- Two distinct empty states (no filters / with filters)
- `userId` and `companyId` filter inputs are UUID text fields with `@Pattern` validation hint

Vitest test: renders with user/company columns; empty state text is "No tenant transactions yet"; Export button triggers ExportDialog with scope=ALL_TENANT.

### FE-4: Route + sidebar entry

**Files:**
- `src/App.tsx` — add inside `<ProtectedRoute permission="action.redemption.view_all_history">`: `<Route path="/redemption/admin/history" element={<TenantTransactionHistoryPage />} />`
- `Sidebar.tsx` (or equivalent) — add sidebar item "Tenant History" under "Redemption" section with `permissionKey="action.redemption.view_all_history"`

---

## E2E test [FE]

**File:** `e2e/redemption-history.spec.ts`

---

**Scenario 1:** `'CLIENT_ADMIN views all-tenant history with user and company names'` _(covers AC-1, AC-2)_

| Field | Value |
|---|---|
| **User flow** | CLIENT_ADMIN navigates to `/redemption/admin/history` → table loads with rows showing User and Company columns → filter by userId → only that user's rows shown |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/requests/all` → 200 with `PaginatedResponse<RedemptionAdminHistoryResponse>` (2 items with `userDisplayName: "Alice Smith"`, `partnerCompanyName: "Acme Corp"`); filtered call → 200 with 1 item |
| **Visible assertion** | `expect(page.getByText('Alice Smith')).toBeVisible()`; `expect(page.getByText('Acme Corp')).toBeVisible()` |

---

**Scenario 2:** `'PARTNER_SELLER cannot access tenant history page'` _(covers AC-3, AC-6)_

| Field | Value |
|---|---|
| **User flow** | Log in as PARTNER_SELLER → navigate to `/redemption/admin/history` → redirected to `/home` |
| **APIs to mock via `page.route()`** | Auth mocked as PARTNER_SELLER session; `GET /api/v1/redemption/requests/all` → 403 if called |
| **Visible assertion** | `expect(page.url()).toContain('/home')` after attempted navigation |

---

**Scenario 3:** `'CLIENT_ADMIN exports all-tenant history'` _(covers AC-5)_

| Field | Value |
|---|---|
| **User flow** | CLIENT_ADMIN on tenant history page → click Export → ExportDialog opens → select CSV → Export clicked → async flow → COMPLETED → Download |
| **APIs to mock via `page.route()`** | `POST /export` → 202 `{jobId, status:'PENDING'}`; `GET /export/{jobId}` → `{status:'COMPLETED', rowCount:2500}`; `GET /export/{jobId}/download` → `{downloadUrl:'https://...'}` |
| **Visible assertion** | `expect(page.getByText('Your export is ready')).toBeVisible()`; Download button visible |

---

## Execution checklist

**BE session:**
- [ ] `RedemptionAdminHistoryResponse.java` DTO created with `userDisplayName` + `partnerCompanyName` _(AC-1)_
- [ ] `RedemptionHistoryService.getTenantHistory()` added: uses JOIN for user/company names, no N+1 _(AC-1, AC-2)_
- [ ] `RedemptionHistoryServiceTest` tenant history cases pass (no filter, userId filter, companyId filter, N+1-free) _(AC-1, AC-2)_
- [ ] `RedemptionHistoryController.listTenantHistory()` added: `GET /requests/all` with `@RequiresPermission("action.redemption.view_all_history")` _(AC-1, AC-3)_
- [ ] `RedemptionHistoryControllerTest` admin endpoint cases pass (200 with correct DTO, 403 wrong roles, 404 cross-tenant, 400 bad filter) _(AC-1, AC-2, AC-3, AC-4)_

**FE session:**
- [ ] `RedemptionAdminHistoryResponse` type added to types file _(AC-1)_
- [ ] `getTenantRedemptions` service call added _(AC-1)_
- [ ] `useTenantRedemptions` hook created: correct queryKey, staleTime 2min _(AC-1)_
- [ ] `TenantTransactionHistoryPage.tsx` created with User + Company columns, userId/companyId filters, Export button, two empty states _(AC-1, AC-2, AC-5, AC-6)_
- [ ] Vitest test: columns render; empty states; Export dialog opens with scope=ALL_TENANT _(AC-1, AC-5)_
- [ ] Route added to `App.tsx` inside ProtectedRoute for `action.redemption.view_all_history` _(AC-6)_
- [ ] Sidebar entry added with `permissionKey="action.redemption.view_all_history"` _(AC-6)_
- [ ] `npm run build` — no TypeScript errors
- [ ] E2E Scenario 1 passes _(AC-1, AC-2)_
- [ ] E2E Scenario 2 passes _(AC-3, AC-6)_
- [ ] E2E Scenario 3 passes _(AC-5)_

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionHistoryServiceTest` (tenant cases) + `RedemptionHistoryControllerTest` (all endpoint cases) green
2. **FE:** `npm run test` passes + `npx playwright test e2e/redemption-history.spec.ts -g 'tenant'` passes against real BE
3. Every AC (AC-1 through AC-6) referenced by at least one passing test
