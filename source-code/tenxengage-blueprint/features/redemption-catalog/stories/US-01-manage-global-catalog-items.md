---
id: US-01
title: "Manage global catalog items"
layers: ["BE", "FE"]
seed_id: "S-01"
touches_entities: ["RedemptionCatalogItem"]
depends_on_stories: []
---

# US-01: Manage global catalog items

## Description

**Actor:** TENX_ADMIN (Platform Admin)
**Trigger:** Platform Admin navigates to `/admin/redemption-catalog` to create or maintain the master set of redeemable items.

**Steps:**
1. Admin opens `/admin/redemption-catalog` → `GlobalCatalogAdminPage` renders with paginated item list
2. Admin clicks "New Item" → `GlobalCatalogItemForm` opens; fills name, category, currencyId, defaultMinRedemptionAmount, defaultProcessingMode, geographicScope, providerItemId (NON_CASH), isReturnable, defaultReturnWindowDays
3. On submit → `POST /api/v1/admin/redemption-catalog` → `RedemptionCatalogAdminService.createCatalogItem()` validates and persists; returns `RedemptionCatalogItemDetailResponse`
4. Admin edits an item → `PUT /api/v1/admin/redemption-catalog/{id}`
5. Admin activates → `PATCH /api/v1/admin/redemption-catalog/{id}/activate`; deactivates → `PATCH /{id}/deactivate`
6. Admin views list with filters (category, isActive, search) → `GET /api/v1/admin/redemption-catalog`

**Expected outcome:** New item appears in the list; activate/deactivate immediately changes `isActive`; all mutations write audit records.

**Negative paths:**
- CASH item with `isReturnable=true` → 400
- NON_CASH item activated without `providerItemId` → 422 "Cannot activate a non-cash catalog item without a provider item ID"
- PUT narrowing `geographicScope` when `ClientCatalogRegionConfig` rows exist for removed region → 422
- Duplicate `providerItemId` per category → 409
- Non-TENX_ADMIN caller → 403

---

## Acceptance Criteria

- **AC-1:** `POST /api/v1/admin/redemption-catalog` with valid body returns 201 + `RedemptionCatalogItemDetailResponse`; audit record written with `action=CREATED, resourceType=REDEMPTION_CATALOG_ITEM`
- **AC-2:** `PUT /api/v1/admin/redemption-catalog/{id}` returns 200; rejects `geographicScope` narrowing when orphaned `ClientCatalogRegionConfig` rows exist → 422
- **AC-3:** `PATCH /{id}/activate` sets `isActive=true`; `PATCH /{id}/deactivate` sets `isActive=false`; both return `RedemptionCatalogItemResponse` and write audit records
- **AC-4:** NON_CASH item activated without `providerItemId` → 422 "Cannot activate a non-cash catalog item without a provider item ID"; CASH item with `isReturnable=true` → 400
- **AC-5:** `GET /api/v1/admin/redemption-catalog` returns paginated list; `pageSize` hard cap 50 → 400 if exceeded; `search` max 200 chars → 400 if exceeded
- **AC-6:** Non-TENX_ADMIN (any tenant role) → 403 on all `/api/v1/admin/redemption-catalog` endpoints

---

## Out of Scope

- Xoxoday sync trigger and integration health (US-05)
- Tenant catalog configuration (US-02)
- Partner catalog browse (US-04)
- Regional config scope-narrowing guard is validated in `updateCatalogItem()` service — but the ClientCatalogRegionConfig rows themselves are managed in US-03

---

## UI States

- [ ] **Loading:** Skeleton rows in admin table while `useGlobalCatalogItems()` is in flight
- [ ] **Empty:** "No catalog items yet — create your first item" with primary CTA
- [ ] **Error:** 5xx fallback with retry button; toast "Could not load catalog items"
- [ ] **Form validation:** Inline field errors on submit; required field highlights

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** None

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-02.1, FR-02.2 (partial — health in US-05)
- `spec.md → ## Data Model / Entities [BE]` — `RedemptionCatalogItem` fields, check constraints
- `spec.md → ## API Endpoints [BE + FE] → Platform Admin` — all 8 CRUD + activate/deactivate + sync endpoints (sync in US-05)
- `spec.md → ## DTOs [BE]` — `CreateRedemptionCatalogItemRequest`, `UpdateRedemptionCatalogItemRequest`, `RedemptionCatalogItemResponse`, `RedemptionCatalogItemDetailResponse`
- `spec.md → ## Service Layer [BE]` — `RedemptionCatalogAdminService` method signatures and business rules
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.catalog.manage` (TENX_ADMIN only)
- `spec.md → ## Security Design [BE]` — rate limits; OWASP injection mitigation on search; IDOR mitigations
- `spec.md → ## Audit Trail [BE]` — CREATED, UPDATED, ACTIVATED, DEACTIVATED audit records
- `spec.md → ## Workflow / Status Transitions` — NON_CASH activation gate; deactivation rules
- `spec.md → ## Edge Cases` — edge case #6 (geographicScope narrowing blocked)
- `technical.md → ## Package Layout [BE]` — all file paths
- `technical.md → ## Repository Queries [BE]` — `RedemptionCatalogItemRepository` query signatures

---

## BE tasks [BE]

### BE-1: Request + Response DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/request/CreateRedemptionCatalogItemRequest.java` — fields: `name` (`@NotBlank @Size(max=255)`), `description` (`@Size(max=2000)`), `category` (`@NotNull`), `currencyId` (`@NotNull`), `defaultMinRedemptionAmount` (`@DecimalMin("0.01")`), `defaultProcessingMode`, `geographicScope` (List<String>), `providerItemId` (`@Size(max=255)`), `isReturnable`, `defaultReturnWindowDays` (`@Min(0)`)
- `src/main/java/com/tenxengage/app/dto/request/UpdateRedemptionCatalogItemRequest.java` — same fields, all optional; `geographicScope` update triggers scope-narrowing check in service
- `src/main/java/com/tenxengage/app/dto/response/RedemptionCatalogItemResponse.java` — `from(RedemptionCatalogItem)` factory; omits `syncMetadata`
- `src/main/java/com/tenxengage/app/dto/response/RedemptionCatalogItemDetailResponse.java` — `from(RedemptionCatalogItem)` factory; includes `xoxodayLastSyncedAt`; omits raw `syncMetadata`

See `spec.md → ## DTOs [BE]` for full field lists. Never include `syncMetadata` in any response DTO.

### BE-2: RedemptionCatalogAdminService + unit tests

**Files:**
- `src/main/java/com/tenxengage/app/service/RedemptionCatalogAdminService.java` — NEW; implement:
  - `createCatalogItem(request)`: `@Transactional`; validates `providerItemId` uniqueness per category; CASH + `isReturnable=true` → 400
  - `updateCatalogItem(id, request)`: `@Transactional`; rejects geographicScope narrowing if orphaned `ClientCatalogRegionConfig` rows exist via `existsByRedemptionCatalogItemIdAndRegionCode`
  - `activateCatalogItem(id)`: `@Transactional`; validates NON_CASH has `providerItemId` before activating → 422 if absent
  - `deactivateCatalogItem(id)`: `@Transactional`; sets `isActive=false`; does NOT cascade to `ClientCatalogItemConfig`
  - `listCatalogItems(filters, pageable)`: `@Transactional(readOnly=true)`; search uses `LOWER(e.name) LIKE :q`; escape LIKE special chars
  - `getCatalogItemDetail(id)`: `@Transactional(readOnly=true)`
- `src/test/java/com/tenxengage/app/service/RedemptionCatalogAdminServiceTest.java` — NEW; test cases:
  - `createCatalogItem_returns201_whenValid` _(AC-1)_
  - `createCatalogItem_rejects_cashItemWithIsReturnable` _(AC-4)_
  - `createCatalogItem_rejects_duplicateProviderItemId`
  - `activateCatalogItem_rejects_nonCashWithoutProviderItemId` _(AC-4)_
  - `updateCatalogItem_rejects_geographicScopeNarrowing` _(AC-2)_
  - `deactivateCatalogItem_setsIsActiveFalse_doesNotCascade` _(AC-3)_
  - `listCatalogItems_enforcesPageSizeCap` _(AC-5)_

### BE-3: RedemptionCatalogAdminController + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/RedemptionCatalogAdminController.java` — NEW; tag `Redemption Catalog Admin`; base path `/api/v1/admin/redemption-catalog`; all endpoints annotated `@RequiresPermission("action.redemption.catalog.manage")`; TenantFilter bypassed for this controller (global entity)
- `src/test/java/com/tenxengage/app/controller/RedemptionCatalogAdminControllerTest.java` — NEW; @WebMvcTest cases:
  - `createItem_returns201_whenValid` _(AC-1)_
  - `createItem_returns403_forNonTenxAdmin` _(AC-6)_
  - `activateItem_returns200` _(AC-3)_
  - `activateItem_returns422_whenNonCashMissingProviderItemId` _(AC-4)_
  - `deactivateItem_returns200` _(AC-3)_
  - `listItems_returns400_whenPageSizeExceeds50` _(AC-5)_
  - `updateItem_returns422_whenGeographicScopeNarrowed` _(AC-2)_

### BE-4: @Audited annotations

Add `@Audited` to controller methods per `spec.md → ## Audit Trail [BE] → @Audited Annotation Details`:
- `POST /` → `action=CREATED, resourceType=REDEMPTION_CATALOG_ITEM` _(AC-1)_
- `PUT /{id}` → `action=UPDATED, resourceType=REDEMPTION_CATALOG_ITEM` _(AC-2)_
- `PATCH /{id}/activate` → `action=ACTIVATED, resourceType=REDEMPTION_CATALOG_ITEM` _(AC-3)_
- `PATCH /{id}/deactivate` → `action=DEACTIVATED, resourceType=REDEMPTION_CATALOG_ITEM` _(AC-3)_

---

## FE tasks [FE]

#### FE-0: Navigation restructure (prerequisite, do before form tasks)
- Remove "Global Catalog" primary nav item from `sidebarConfigs.ts`
- Remove "Redemption Catalog" item from Settings section in `sidebarConfigs.ts`
- Add "Redemption Catalog" tab to `PlatformSettingsPage.tsx` (between business-rules and builder-config)
- Create `RedemptionCatalogTab.tsx` with "Catalog Items" and "Tenant Config" sub-tabs
- Update `App.tsx` routing — redirect `/admin/redemption-catalog` and `/settings/redemption/catalog` to `/settings/platform?tab=redemption-catalog`

### FE-1: TypeScript types + service calls

**Files:**
- `src/types/redemption-catalog.types.ts` — NEW; copy `RedemptionCatalogItemResponse`, `RedemptionCatalogItemDetailResponse`, `CreateRedemptionCatalogItemRequest`, `UpdateRedemptionCatalogItemRequest` interfaces from `../tenxengage-contracts/` after contracts generated; do not hand-write
- `src/services/redemption-catalog-admin.service.ts` — NEW; functions:
  - `getGlobalCatalogItems(filters): Promise<PaginatedResponse<RedemptionCatalogItemResponse>>` → `GET /api/v1/admin/redemption-catalog`
  - `createCatalogItem(request): Promise<RedemptionCatalogItemDetailResponse>` → `POST /api/v1/admin/redemption-catalog`
  - `updateCatalogItem(id, request): Promise<RedemptionCatalogItemDetailResponse>` → `PUT /api/v1/admin/redemption-catalog/{id}`
  - `activateCatalogItem(id): Promise<RedemptionCatalogItemResponse>` → `PATCH /api/v1/admin/redemption-catalog/{id}/activate`
  - `deactivateCatalogItem(id): Promise<RedemptionCatalogItemResponse>` → `PATCH /api/v1/admin/redemption-catalog/{id}/deactivate`
  - `getCatalogItemDetail(id): Promise<RedemptionCatalogItemDetailResponse>` → `GET /api/v1/admin/redemption-catalog/{id}`

### FE-2: TanStack Query hooks

**File:** `src/hooks/useRedemptionCatalog.ts` — NEW (shared hook file for all catalog hooks); add:
  - `useGlobalCatalogItems(filters)`: queryKey `['global-catalog', filters]`, staleTime `2 * 60 * 1000`
  - Mutations: `useCreateCatalogItem`, `useUpdateCatalogItem`, `useActivateCatalogItem`, `useDeactivateCatalogItem` — all invalidate `['global-catalog']` on success

See `technical.md → ## Hook Specs [FE] → useGlobalCatalogItems` for query key.

### FE-3a: GlobalCatalogItemForm component + Vitest test

**Files:**
- `src/components/redemption-catalog/GlobalCatalogItemForm.tsx` — NEW; props: `item?: RedemptionCatalogItemDetailResponse, onSave: () => void`; form fields per spec; zod schema `createCatalogItemSchema`; conditional `providerItemId` required for NON_CASH; `isReturnable` disabled for CASH; `geographicScope` multiselect
- `src/components/redemption-catalog/__tests__/GlobalCatalogItemForm.test.tsx` — NEW; Vitest cases:
  - `renders all fields for NON_CASH category` _(AC-1)_
  - `disables isReturnable for CASH category` _(AC-4)_
  - `shows validation error when name is empty` _(AC-1)_

### FE-3b: GlobalCatalogAdminPage + Vitest test

**Files:**
- `src/pages/GlobalCatalogAdminPage.tsx` — NEW; renders item table with `useGlobalCatalogItems()`; category/isActive/search filters; activate/deactivate toggle per row; "New Item" button opens `GlobalCatalogItemForm`; skeleton on loading; empty state
- `src/components/redemption-catalog/__tests__/GlobalCatalogAdminPage.test.tsx` — NEW; Vitest cases:
  - `renders skeleton while loading` _(AC-5)_
  - `renders empty state when no items` _(AC-5)_
  - `renders item list when data loaded` _(AC-5)_

### FE-4: Route wiring

**File:** `src/App.tsx` — MODIFIED; add:
```tsx
<Route element={<ProtectedRoute permission="action.redemption.catalog.manage" />}>
  <Route element={<AppLayout />}>
    <Route path="/admin/redemption-catalog" element={<GlobalCatalogAdminPage />} />
  </Route>
</Route>
```

### FE-5: Currency dropdown
Replace static text `Input` for `currencyId` in `GlobalCatalogItemForm.tsx` with a shadcn `Select` loaded from `GET /api/v1/currencies`. Display: `{name} ({type})`. Save: `currency.code`.

### FE-6: Geographic scope multiselect
Replace static `COUNTRY_OPTIONS` array with options loaded from `GET /api/v1/location-levels`. Regions shown as group labels; countries as selectable items. Unmatched codes (from Xoxoday sync) shown as read-only "From sync" chips. Save: array of `location_value.code` values.

### FE-7: Image upload component
New component `CatalogImageUpload.tsx`. Optional file picker (png/jpeg/webp, ≤ 5 MB). In edit mode: calls `POST /api/v1/admin/redemption-catalog/{id}/image` immediately on file select. In create mode: upload triggered after `createCatalogItem` succeeds. Shows preview, progress state, Remove button.

---

## E2E test [FE]

**Scenario 1:** `'Platform Admin creates NON_CASH item and activates it'` _(covers AC-1, AC-3, AC-4)_

**File:** `e2e/redemption-catalog-admin.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as TENX_ADMIN → navigate to `/admin/redemption-catalog` → click "New Item" → fill form (NON_CASH, providerItemId set) → submit → item appears in list → click activate → `isActive` indicator turns active |
| **APIs to mock via `page.route()`** | `POST /api/v1/admin/redemption-catalog` → 201 + item fixture; `GET /api/v1/admin/redemption-catalog` → paginated list; `PATCH /activate` → 200 |
| **Visible assertion** | `expect(page.getByTestId('catalog-item-row')).toBeVisible()`; activate badge shows active state |
| **Negative case** | Attempt activate on NON_CASH without providerItemId → inline 422 error shown |

---

**Scenario 2:** `'Non-TENX_ADMIN cannot access catalog admin page'` _(covers AC-6)_

**File:** `e2e/redemption-catalog-admin.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as CLIENT_ADMIN → navigate to `/admin/redemption-catalog` → should be redirected or see 403 |
| **APIs to mock via `page.route()`** | None needed — ProtectedRoute blocks render |
| **Visible assertion** | `expect(page.getByTestId('catalog-admin-page')).not.toBeAttached()` |
| **Negative case** | N/A |

---

## Execution checklist

**BE session:**
- [ ] `CreateRedemptionCatalogItemRequest.java` DTO created with all validations _(AC-1, AC-4)_
- [ ] `UpdateRedemptionCatalogItemRequest.java` DTO created _(AC-2)_
- [ ] `RedemptionCatalogItemResponse.java` DTO created — omits `syncMetadata` _(AC-1, AC-3, AC-5)_
- [ ] `RedemptionCatalogItemDetailResponse.java` DTO created — includes `xoxodayLastSyncedAt` _(AC-1)_
- [ ] `RedemptionCatalogAdminService.createCatalogItem()` implemented — CASH+isReturnable validation, providerItemId uniqueness _(AC-1, AC-4)_
- [ ] `RedemptionCatalogAdminService.updateCatalogItem()` implemented — geographicScope narrowing guard _(AC-2)_
- [ ] `RedemptionCatalogAdminService.activateCatalogItem()` implemented — NON_CASH providerItemId check _(AC-3, AC-4)_
- [ ] `RedemptionCatalogAdminService.deactivateCatalogItem()` implemented — no cascade _(AC-3)_
- [ ] `RedemptionCatalogAdminService.listCatalogItems()` implemented — pageSize cap, search escaping _(AC-5)_
- [ ] `RedemptionCatalogAdminServiceTest` unit tests pass _(AC-1, AC-2, AC-3, AC-4, AC-5)_
- [ ] `RedemptionCatalogAdminController` created — all endpoints with `@RequiresPermission("action.redemption.catalog.manage")` _(AC-6)_
- [ ] `@Audited` annotations added to create/update/activate/deactivate _(AC-1, AC-2, AC-3)_
- [ ] `RedemptionCatalogAdminControllerTest` @WebMvcTest cases pass _(AC-1, AC-2, AC-3, AC-4, AC-5, AC-6)_

**FE session:**
- [ ] `redemption-catalog.types.ts` created from contracts — `RedemptionCatalogItemResponse` + detail + request interfaces _(AC-1)_
- [ ] `redemption-catalog-admin.service.ts` created — all 6 service functions _(AC-1, AC-2, AC-3)_
- [ ] `useGlobalCatalogItems` hook created with correct queryKey + staleTime _(AC-5)_
- [ ] Mutations: `useCreateCatalogItem`, `useActivateCatalogItem`, `useDeactivateCatalogItem` invalidate `['global-catalog']` _(AC-1, AC-3)_
- [ ] `GlobalCatalogItemForm.tsx` created — CASH/NON_CASH conditional fields, zod validation _(AC-1, AC-4)_
- [ ] `GlobalCatalogItemForm.test.tsx` Vitest tests pass _(AC-1, AC-4)_
- [ ] `GlobalCatalogAdminPage.tsx` created — list + filters + activate/deactivate + form open _(AC-5)_
- [ ] Skeleton + empty state implemented _(AC-5)_
- [ ] Route `/admin/redemption-catalog` wired in `App.tsx` with `ProtectedRoute` _(AC-6)_
- [ ] E2E: `'Platform Admin creates NON_CASH item and activates it'` passes _(AC-1, AC-3, AC-4)_
- [ ] E2E: `'Non-TENX_ADMIN cannot access catalog admin page'` passes _(AC-6)_
- [ ] FE-0 (nav restructure): sidebar "Global Catalog" and "Redemption Catalog" links removed; Platform Settings "Redemption Catalog" tab added between business-rules and builder-config; `RedemptionCatalogTab.tsx` created with Catalog Items / Tenant Config sub-tabs; routes redirecting to `/settings/platform?tab=redemption-catalog`
- [ ] FE-5 (currency dropdown): renders options from `GET /api/v1/currencies`; displays `{name} ({type})`; saves correct currency code on select
- [ ] FE-6 (geo scope multiselect): loads from `GET /api/v1/location-levels`; regions as group labels, countries selectable; unmatched Xoxoday codes shown as read-only "From sync" chips
- [ ] FE-7 (image upload): file picker renders (png/jpeg/webp ≤ 5 MB); upload fires on file select in edit mode; upload triggered after create in create mode; preview shown; Remove button works

---

## Done when

1. `./gradlew test` passes — all `RedemptionCatalogAdminServiceTest` + `RedemptionCatalogAdminControllerTest` cases green
2. `npm run test` passes — `GlobalCatalogItemForm.test.tsx` + `GlobalCatalogAdminPage.test.tsx` Vitest cases green; new tests for currency dropdown, geo scope multiselect, and image upload component all green
3. `npx playwright test e2e/redemption-catalog-admin.spec.ts` passes against real BE
4. Every AC above is referenced by at least one passing test
5. FE: form submits correctly with all three new fields — `currencyId` (from dropdown), `geographicScope` (from multiselect), `imageUrl` (from upload component)
