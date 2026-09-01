---
id: US-02
title: "Configure tenant catalog"
layers: ["BE", "FE"]
seed_id: "S-02"
touches_entities: ["ClientCatalogItemConfig", "TenantRedemptionSettings"]
depends_on_stories: ["US-01"]
---

# US-02: Configure tenant catalog

## Description

**Actor:** CLIENT_ADMIN
**Trigger:** CLIENT_ADMIN navigates to `/settings/redemption/catalog` to enable/disable catalog items for their tenant and configure overrides.

**Steps:**
1. CLIENT_ADMIN opens `/settings/redemption/catalog` → `CatalogConfigPage` renders with `TenantCatalogConfigTable` showing all globally active items with tenant config overlay
2. Admin toggles the enable/disable switch on an item → `PUT /api/v1/redemption/catalog/config/{catalogItemId}` with `{ enabled: true/false }` → `TenantRedemptionCatalogService.upsertItemConfig()` creates or updates `ClientCatalogItemConfig`
3. Admin expands an item → `ItemConfigPanel` renders current overrides; admin sets `processingModeOverride`, `minTransactionAmountOverride`, `minWalletBalanceOverride`, `returnWindowDaysOverride` → same `PUT` endpoint
4. Admin opens settings panel → `TenantRedemptionSettingsForm` shows current `batchCadence`; admin changes to DAILY or WEEKLY → `PUT /api/v1/redemption/settings`
5. Admin GETs settings for first time → service auto-creates `TenantRedemptionSettings` row with DAILY default using `SELECT FOR UPDATE` (find-or-create)

**Expected outcome:** Enabled items immediately visible in partner browse; config overrides applied at browse time; `batchCadence` update logged to audit trail.

**Negative paths:**
- `minTransactionAmountOverride` below global `defaultMinRedemptionAmount` → 422 "Minimum transaction amount cannot be set below the catalog item's platform minimum of {amount}"
- Concurrent `ClientCatalogItemConfig` update (version mismatch) → 409 "This configuration was updated concurrently. Refresh and retry."
- PARTNER_SELLER or PARTNER_ADMIN calling config endpoints → 403
- `PUT /redemption/catalog/config/{id}` with `enabled=true` for a globally inactive item → 404

---

## Acceptance Criteria

- **AC-1:** `PUT /api/v1/redemption/catalog/config/{catalogItemId}` with `enabled=true` upserts `ClientCatalogItemConfig`; enabled item immediately appears in partner browse responses; audit record written with `action=UPDATED, resourceType=TENANT_CATALOG_CONFIG`
- **AC-2:** `minTransactionAmountOverride` below global `defaultMinRedemptionAmount` → 422 "Minimum transaction amount cannot be set below the catalog item's platform minimum of {amount}"; valid override persists successfully
- **AC-3:** `GET /api/v1/redemption/settings` auto-creates `TenantRedemptionSettings` with `batchCadence=DAILY` if no row exists; `PUT /api/v1/redemption/settings` updates `batchCadence` and writes audit record
- **AC-4:** `TenantCatalogItemResponse` includes `isGloballyActive` field; CLIENT_ADMIN list shows warning indicator for items where `isActive=false`
- **AC-5:** PARTNER_SELLER or PARTNER_ADMIN calling `GET/PUT /api/v1/redemption/catalog/config` or `GET/PUT /api/v1/redemption/settings` → 403

---

## Out of Scope

- Regional catalog configuration (US-03)
- Partner catalog browse (US-04) — tested there end-to-end
- Batch processing execution — batchCadence configured here; batch job runs in F-03
- Xoxoday sync auto-deactivation effect on Client Admin view (integration-tested in test-plan.md)

---

## Non-Functional Notes

- `TenantRedemptionSettings` auto-creation uses `SELECT FOR UPDATE` to prevent duplicate row on concurrent first-access (edge case #8). Test this with `findByClientIdWithLock`.
- Optimistic locking (`@Version`) on `ClientCatalogItemConfig`: concurrent update returns 409; FE must surface "Refresh and retry" toast, not a generic error.

---

## UI States

- [ ] **Loading:** Skeleton rows in `TenantCatalogConfigTable` while `useTenantCatalogConfig()` is in flight
- [ ] **Globally inactive warning:** `isGloballyActive=false` → amber warning badge "Globally inactive" beside item name in table
- [ ] **Optimistic toggle:** enable/disable switch flips immediately; reverts on API error with error toast
- [ ] **Concurrent conflict:** 409 response from `PUT` → toast "Configuration was updated concurrently. Refresh and retry." (does not revert silently)
- [ ] **Error fallback:** 5xx → toast "Could not save configuration" with retry
- [ ] **Settings form empty state:** `GET /redemption/settings` auto-creates row; spinner while loading then form shows DAILY default

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 (catalog items must exist before tenants can configure them)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-02.4, FR-02.5, FR-02.6
- `spec.md → ## Data Model / Entities [BE]` — `ClientCatalogItemConfig` fields + business rule; `TenantRedemptionSettings` fields
- `spec.md → ## API Endpoints [BE + FE] → Client Admin` — all settings + config endpoints
- `spec.md → ## DTOs [BE]` — `UpsertClientCatalogItemConfigRequest`, `UpdateTenantRedemptionSettingsRequest`, `TenantCatalogItemResponse`, `ClientCatalogItemConfigResponse`, `TenantRedemptionSettingsResponse`
- `spec.md → ## Service Layer [BE]` — `TenantRedemptionCatalogService` method signatures
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.configure` (CLIENT_ADMIN only)
- `spec.md → ## Security Design [BE]` — IDOR guard; Hibernate `@Filter` on config entities; OWASP A01
- `spec.md → ## Audit Trail [BE]` — UPSERT `ClientCatalogItemConfig`, UPDATE `TenantRedemptionSettings`
- `spec.md → ## Edge Cases` — edge cases #2 (enable inactive item → 404), #7 (batchCadence update mid-batch), #8 (settings auto-creation race), #11 (concurrent config upsert → 409)
- `technical.md → ## Package Layout [BE]` — all file paths
- `technical.md → ## Repository Queries [BE]` — `ClientCatalogItemConfigRepository`, `TenantRedemptionSettingsRepository` query signatures

---

## BE tasks [BE]

### BE-1: Request + Response DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/request/UpsertClientCatalogItemConfigRequest.java` — fields: `enabled` (`@NotNull`), `processingModeOverride` (`@ValidEnum(RedemptionProcessingMode.class)`, nullable), `minTransactionAmountOverride` (`@DecimalMin("0.01")`, nullable), `minWalletBalanceOverride` (`@DecimalMin("0.00")`, nullable), `returnWindowDaysOverride` (`@Min(0)`, nullable)
- `src/main/java/com/tenxengage/app/dto/request/UpdateTenantRedemptionSettingsRequest.java` — field: `batchCadence` (`@NotNull @ValidEnum(BatchCadence.class)`)
- `src/main/java/com/tenxengage/app/dto/response/TenantCatalogItemResponse.java` — `from(RedemptionCatalogItem, ClientCatalogItemConfig)` factory; includes `isGloballyActive` field (from `item.isActive`)
- `src/main/java/com/tenxengage/app/dto/response/ClientCatalogItemConfigResponse.java` — `from(ClientCatalogItemConfig)` factory; includes all override fields
- `src/main/java/com/tenxengage/app/dto/response/TenantRedemptionSettingsResponse.java` — `from(TenantRedemptionSettings)` factory; `id`, `batchCadence`, `createdAt`, `updatedAt`

### BE-2: TenantRedemptionCatalogService + unit tests

**Files:**
- `src/main/java/com/tenxengage/app/service/TenantRedemptionCatalogService.java` — NEW; implement:
  - `getTenantSettings()`: `@Transactional(readOnly=true)` with find-or-create: call `findByClientIdWithLock`, create with `DAILY` default if absent
  - `updateTenantSettings(request)`: `@Transactional`; find-or-create then update `batchCadence`
  - `getTenantCatalog(filters, pageable)`: `@Transactional(readOnly=true)`; loads all globally active items + tenant `ClientCatalogItemConfig` overlay; builds `TenantCatalogItemResponse` with `isGloballyActive`
  - `upsertItemConfig(catalogItemId, request)`: `@Transactional`; 404 if item not found or `isActive=false`; validates `minTransactionAmountOverride ≥ defaultMinRedemptionAmount` → 422 on violation; `existsByClientIdAndRedemptionCatalogItemId` for insert vs update; includes `@Version` for optimistic lock
- `src/test/java/com/tenxengage/app/service/TenantRedemptionCatalogServiceTest.java` — NEW; test cases:
  - `getTenantSettings_autoCreatesWithDailyDefault` _(AC-3)_
  - `updateTenantSettings_changesBatchCadence` _(AC-3)_
  - `upsertItemConfig_enablesItem` _(AC-1)_
  - `upsertItemConfig_rejects_minTransactionAmountBelowFloor` _(AC-2)_
  - `upsertItemConfig_returns404_whenItemGloballyInactive` _(AC-1)_
  - `getTenantCatalog_includesIsGloballyActiveField` _(AC-4)_

### BE-3: RedemptionConfigController + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/RedemptionConfigController.java` — NEW; tag `Redemption Config`; base paths `/api/v1/redemption/settings` and `/api/v1/redemption/catalog/config`; all endpoints: `@RequiresPermission("action.redemption.configure")`; Hibernate `@Filter` active (tenant-scoped)
  - `GET /redemption/settings` → `getTenantSettings()`
  - `PUT /redemption/settings` → `updateTenantSettings(request)`
  - `GET /redemption/catalog/config` → `getTenantCatalog(filters, pageable)`
  - `PUT /redemption/catalog/config/{catalogItemId}` → `upsertItemConfig(catalogItemId, request)`
- `src/test/java/com/tenxengage/app/controller/RedemptionConfigControllerTest.java` — NEW; @WebMvcTest cases:
  - `putItemConfig_returns200_whenValid` _(AC-1)_
  - `putItemConfig_returns403_forPartnerSeller` _(AC-5)_
  - `putItemConfig_returns422_whenMinAmountBelowFloor` _(AC-2)_
  - `getTenantSettings_returns200` _(AC-3)_
  - `putSettings_returns403_forPartnerSeller` _(AC-5)_
  - `getTenantCatalog_includesIsGloballyActiveFlag` _(AC-4)_

### BE-4: @Audited annotations

Add `@Audited` per `spec.md → ## Audit Trail [BE]`:
- `PUT /redemption/catalog/config/{id}` → `action=UPDATED, resourceType=TENANT_CATALOG_CONFIG` _(AC-1)_
- `PUT /redemption/settings` → `action=UPDATED, resourceType=TENANT_REDEMPTION_SETTINGS` _(AC-3)_

---

## FE tasks [FE]

### FE-1: TypeScript types + service calls

**Files:**
- `src/types/redemption-catalog.types.ts` — MODIFIED; add `UpsertClientCatalogItemConfigRequest`, `UpdateTenantRedemptionSettingsRequest`, `TenantCatalogItemResponse`, `ClientCatalogItemConfigResponse`, `TenantRedemptionSettingsResponse` interfaces from `../tenxengage-contracts/` after contracts generated
- `src/services/redemption-catalog.service.ts` — NEW; tenant config functions:
  - `getTenantCatalogConfig(filters): Promise<PaginatedResponse<TenantCatalogItemResponse>>` → `GET /api/v1/redemption/catalog/config`
  - `upsertItemConfig(catalogItemId, request): Promise<ClientCatalogItemConfigResponse>` → `PUT /api/v1/redemption/catalog/config/{catalogItemId}`
  - `getTenantRedemptionSettings(): Promise<TenantRedemptionSettingsResponse>` → `GET /api/v1/redemption/settings`
  - `updateTenantRedemptionSettings(request): Promise<TenantRedemptionSettingsResponse>` → `PUT /api/v1/redemption/settings`

### FE-2: TanStack Query hooks

**File:** `src/hooks/useRedemptionCatalog.ts` — MODIFIED; add:
- `useTenantCatalogConfig(filters)`: queryKey `['redemption-catalog', 'config', filters]`, staleTime `5 * 60 * 1000`; see `technical.md → ## Hook Specs [FE]`
- `useCatalogItemConfig(catalogItemId)`: queryKey `['redemption-catalog', 'config', catalogItemId]`, staleTime `5 * 60 * 1000`
- `useTenantRedemptionSettings()`: queryKey `['redemption-settings']`, staleTime `10 * 60 * 1000`
- Mutations: `useUpsertItemConfig` — invalidates `['redemption-catalog', 'config']` on success _(AC-1)_
- Mutation: `useUpdateTenantSettings` — invalidates `['redemption-settings']` on success _(AC-3)_

### FE-3a: TenantCatalogConfigTable component + Vitest test

**Files:**
- `src/components/redemption-catalog/TenantCatalogConfigTable.tsx` — NEW; renders table from `useTenantCatalogConfig()`; row per item with enable toggle (`useUpsertItemConfig`), `isGloballyActive=false` warning badge; click row → `ItemConfigPanel` opens in sheet/drawer; skeleton on loading
- `src/components/redemption-catalog/__tests__/TenantCatalogConfigTable.test.tsx` — NEW; Vitest cases:
  - `renders globally inactive warning badge when isGloballyActive is false` _(AC-4)_
  - `toggle calls upsertItemConfig with enabled=true` _(AC-1)_
  - `renders skeleton while loading` _(AC-5, loading state)_

### FE-3b: ItemConfigPanel component

**Files:**
- `src/components/redemption-catalog/ItemConfigPanel.tsx` — NEW; props: `catalogItemId: string`; uses `useCatalogItemConfig(catalogItemId)`; form fields: `processingModeOverride` (select), `minTransactionAmountOverride` (number), `minWalletBalanceOverride` (number), `returnWindowDaysOverride` (number); zod schema `catalogItemConfigSchema`; `useUpsertItemConfig` on save; 422 inline error for floor violation _(AC-2)_; 409 toast for concurrent conflict

### FE-3c: TenantRedemptionSettingsForm component

**Files:**
- `src/components/redemption-catalog/TenantRedemptionSettingsForm.tsx` (or inline in `CatalogConfigPage.tsx`) — fields: `batchCadence` radio (DAILY / WEEKLY); uses `useTenantRedemptionSettings()` + `useUpdateTenantSettings()` mutation; zod schema `tenantRedemptionSettingsSchema` _(AC-3)_

### FE-4: CatalogConfigPage + route wiring

**Files:**
- `src/pages/CatalogConfigPage.tsx` — NEW; composes `TenantCatalogConfigTable` + `TenantRedemptionSettingsForm`; page title "Redemption Settings"
- `src/App.tsx` — MODIFIED; add:
  ```tsx
  <Route element={<ProtectedRoute permission="action.redemption.configure" />}>
    <Route element={<AppLayout />}>
      <Route path="/settings/redemption/catalog" element={<CatalogConfigPage />} />
    </Route>
  </Route>
  ```

---

## E2E test [FE]

**Scenario 1:** `'CLIENT_ADMIN enables item and sets processing mode override'` _(covers AC-1, AC-2)_

**File:** `e2e/redemption-catalog-config.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as CLIENT_ADMIN → navigate to `/settings/redemption/catalog` → toggle enable on item → expand item → set `processingModeOverride=BATCH`, valid `minTransactionAmountOverride` → save → verify PUT response 200 |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/catalog/config` → list with one item; `PUT /api/v1/redemption/catalog/config/{id}` → 200 `ClientCatalogItemConfigResponse`; attempt with amount below floor → 422 |
| **Visible assertion** | Enable switch shows enabled state; 422 error message "Minimum transaction amount cannot be set below..." is visible |
| **Negative case** | Submit `minTransactionAmountOverride` below global floor → inline 422 error displayed |

---

**Scenario 2:** `'CLIENT_ADMIN updates batchCadence'` _(covers AC-3)_

**File:** `e2e/redemption-catalog-config.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as CLIENT_ADMIN → navigate to `/settings/redemption/catalog` → select WEEKLY in settings form → save → verify GET /settings returns WEEKLY |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/settings` → `{ batchCadence: 'DAILY' }`; `PUT /api/v1/redemption/settings` → `{ batchCadence: 'WEEKLY' }` |
| **Visible assertion** | WEEKLY radio button selected after save |

---

**Scenario 3:** `'PARTNER_SELLER cannot access config endpoints'` _(covers AC-5)_

**File:** `e2e/redemption-catalog-config.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as PARTNER_SELLER → navigate to `/settings/redemption/catalog` → ProtectedRoute blocks render |
| **APIs to mock via `page.route()`** | None — ProtectedRoute blocks render before API call |
| **Visible assertion** | `expect(page.getByTestId('catalog-config-page')).not.toBeAttached()` |

---

## Execution checklist

**BE session:**
- [ ] `UpsertClientCatalogItemConfigRequest.java` DTO created with all validations _(AC-1, AC-2)_
- [ ] `UpdateTenantRedemptionSettingsRequest.java` DTO created _(AC-3)_
- [ ] `TenantCatalogItemResponse.java` DTO created — includes `isGloballyActive` field _(AC-4)_
- [ ] `ClientCatalogItemConfigResponse.java` DTO created _(AC-1)_
- [ ] `TenantRedemptionSettingsResponse.java` DTO created _(AC-3)_
- [ ] `TenantRedemptionCatalogService.getTenantSettings()` implemented — find-or-create with `SELECT FOR UPDATE` _(AC-3)_
- [ ] `TenantRedemptionCatalogService.updateTenantSettings()` implemented _(AC-3)_
- [ ] `TenantRedemptionCatalogService.getTenantCatalog()` implemented — `isGloballyActive` overlay _(AC-4)_
- [ ] `TenantRedemptionCatalogService.upsertItemConfig()` implemented — 404 on inactive item, 422 on min amount violation _(AC-1, AC-2)_
- [ ] `TenantRedemptionCatalogServiceTest` unit tests pass _(AC-1, AC-2, AC-3, AC-4)_
- [ ] `RedemptionConfigController` created — settings + config endpoints with `@RequiresPermission("action.redemption.configure")` _(AC-5)_
- [ ] `@Audited` annotations added to `PUT /catalog/config/{id}` + `PUT /settings` _(AC-1, AC-3)_
- [ ] `RedemptionConfigControllerTest` @WebMvcTest cases pass _(AC-1, AC-2, AC-3, AC-4, AC-5)_

**FE session:**
- [ ] Types added to `redemption-catalog.types.ts` from contracts _(AC-1, AC-3, AC-4)_
- [ ] `redemption-catalog.service.ts` created — 4 tenant config service functions _(AC-1, AC-3)_
- [ ] `useTenantCatalogConfig`, `useCatalogItemConfig`, `useTenantRedemptionSettings` hooks created _(AC-1, AC-3, AC-4)_
- [ ] `useUpsertItemConfig` mutation with cache invalidation _(AC-1)_
- [ ] `useUpdateTenantSettings` mutation with cache invalidation _(AC-3)_
- [ ] `TenantCatalogConfigTable.tsx` created — enable toggle, `isGloballyActive` warning badge, skeleton _(AC-4)_
- [ ] `TenantCatalogConfigTable.test.tsx` Vitest tests pass _(AC-4)_
- [ ] `ItemConfigPanel.tsx` created — override fields, 422 floor error, 409 conflict toast _(AC-2)_
- [ ] `TenantRedemptionSettingsForm` created — DAILY/WEEKLY radio, submit _(AC-3)_
- [ ] `CatalogConfigPage.tsx` created — composes table + settings form _(AC-1, AC-3)_
- [ ] Route `/settings/redemption/catalog` wired in `App.tsx` with `ProtectedRoute` _(AC-5)_
- [ ] E2E: `'CLIENT_ADMIN enables item and sets processing mode override'` passes _(AC-1, AC-2)_
- [ ] E2E: `'CLIENT_ADMIN updates batchCadence'` passes _(AC-3)_
- [ ] E2E: `'PARTNER_SELLER cannot access config endpoints'` passes _(AC-5)_

---

## Done when

1. `./gradlew test` passes — all `TenantRedemptionCatalogServiceTest` + `RedemptionConfigControllerTest` cases green
2. `npm run test` passes — `TenantCatalogConfigTable.test.tsx` Vitest cases green
3. `npx playwright test e2e/redemption-catalog-config.spec.ts` passes against real BE
4. Every AC above is referenced by at least one passing test
