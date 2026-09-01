---
id: US-03
title: "Configure regional catalog"
layers: ["BE", "FE"]
seed_id: "S-03"
touches_entities: ["ClientCatalogRegionConfig"]
depends_on_stories: ["US-02"]
---

# US-03: Configure regional catalog

## Description

**Actor:** CLIENT_ADMIN
**Trigger:** CLIENT_ADMIN expands a catalog item in `CatalogConfigPage` and configures which regions the item is available in for their partners.

**Steps:**
1. CLIENT_ADMIN opens `/settings/redemption/catalog` → `CatalogConfigPage`; expands a catalog item row → `RegionalConfigMatrix` renders for that item
2. Matrix shows all regions from the item's `geographicScope`; cells outside `geographicScope` are disabled
3. Admin toggles a region cell → `PUT /api/v1/redemption/catalog/config/{catalogItemId}/regions/{regionCode}` with `{ enabled: true/false }`
4. Admin removes a regional override → `DELETE /api/v1/redemption/catalog/config/{catalogItemId}/regions/{regionCode}` → row deleted; fallback to tenant-level `ClientCatalogItemConfig.enabled`
5. Admin views current regional state → `GET /api/v1/redemption/catalog/config/{catalogItemId}/regions` returns `List<ClientCatalogRegionConfigResponse>`

**Expected outcome:** Partners in a configured region see the item only when `enabled=true` for that region; deletion of a region override falls back to tenant-level enabled; region codes outside the item's `geographicScope` always return 422.

**Negative paths:**
- `regionCode` not in item's `geographicScope` → 422 "Region {code} is not supported by this catalog item's vendor"
- PARTNER_SELLER or PARTNER_ADMIN calling regional config endpoints → 403
- DELETE is idempotent: 204 even if row doesn't exist

---

## Acceptance Criteria

- **AC-1:** `PUT /api/v1/redemption/catalog/config/{catalogItemId}/regions/{regionCode}` upserts `ClientCatalogRegionConfig`; `regionCode` must be a member of `RedemptionCatalogItem.geographicScope` → 422 if not; audit record written with `action=UPDATED, resourceType=TENANT_CATALOG_CONFIG`
- **AC-2:** `DELETE /api/v1/redemption/catalog/config/{catalogItemId}/regions/{regionCode}` returns 204; idempotent (204 even if row absent); audit record written with `action=DELETED, resourceType=TENANT_CATALOG_CONFIG`
- **AC-3:** Three-tier regional fallback: `ClientCatalogRegionConfig` row present → use its `enabled`; row absent → fall back to `ClientCatalogItemConfig.enabled` (FR-02.8); no `ClientCatalogItemConfig` → item not visible
- **AC-4:** PARTNER_SELLER or PARTNER_ADMIN calling any regional config endpoint → 403

---

## Out of Scope

- Partner browse regional filtering (US-04 — consumes regional config but does not manage it)
- `geographicScope` update scope-narrowing guard (US-01 — already validated in `updateCatalogItem()`)
- Cross-tenant region config visibility — each tenant sees only their own rows via Hibernate `@Filter`

---

## UI States

- [ ] **Matrix loading:** Skeleton cells while `useRegionalConfig()` is in flight
- [ ] **Disabled cells:** Regions NOT in item's `geographicScope` render as grayed-out, non-interactive
- [ ] **Toggle state:** Region cell shows enabled/disabled toggle; updates optimistically; reverts on API error
- [ ] **422 inline error:** If a region toggle fails with 422, show error tooltip on the cell: "Region not supported by this catalog item"

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-02 (`ClientCatalogItemConfig` rows must exist for the three-tier fallback to apply)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-02.7, FR-02.8
- `spec.md → ## Data Model / Entities [BE]` — `ClientCatalogRegionConfig` fields, uniqueness, business rules
- `spec.md → ## API Endpoints [BE + FE] → Client Admin` — region config endpoints
- `spec.md → ## DTOs [BE]` — `UpsertRegionConfigRequest`, `ClientCatalogRegionConfigResponse`
- `spec.md → ## Service Layer [BE]` — `TenantRedemptionCatalogService.upsertRegionConfig()`, `deleteRegionConfig()`, `getRegionalConfigs()`
- `spec.md → ## Security Design [BE]` — IDOR guard; `@Filter` on `ClientCatalogRegionConfig`
- `spec.md → ## Audit Trail [BE]` — UPSERT + DELETE `ClientCatalogRegionConfig`
- `spec.md → ## Edge Cases` — edge case #3 (Xoxoday auto-deactivated; regional rows preserved), #4 (three-tier fallback), #6 (geographicScope narrowing blocked — validated in US-01 service)
- `technical.md → ## Package Layout [BE]` — all file paths
- `technical.md → ## Repository Queries [BE]` — `ClientCatalogRegionConfigRepository` query signatures

---

## BE tasks [BE]

### BE-1: Request + Response DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/request/UpsertRegionConfigRequest.java` — field: `enabled` (`@NotNull boolean`)
- `src/main/java/com/tenxengage/app/dto/response/ClientCatalogRegionConfigResponse.java` — `from(ClientCatalogRegionConfig)` factory; fields: `id`, `regionCode`, `enabled`, `createdAt`, `updatedAt`

### BE-2: TenantRedemptionCatalogService — regional methods + unit tests

**File:** `src/main/java/com/tenxengage/app/service/TenantRedemptionCatalogService.java` — MODIFIED; add:
- `getRegionalConfigs(catalogItemId)`: `@Transactional(readOnly=true)`; loads all `ClientCatalogRegionConfig` for `(clientId, catalogItemId)` using `findByClientIdAndRedemptionCatalogItemId`
- `upsertRegionConfig(catalogItemId, regionCode, request)`: `@Transactional`; 404 if catalog item not found; validates `regionCode` is in `item.geographicScope` → 422 if not; upsert via `findByClientIdAndRedemptionCatalogItemIdAndRegionCode`
- `deleteRegionConfig(catalogItemId, regionCode)`: `@Transactional`; calls `deleteByClientIdAndRedemptionCatalogItemIdAndRegionCode`; idempotent — no error if absent

**File:** `src/test/java/com/tenxengage/app/service/TenantRedemptionCatalogServiceTest.java` — MODIFIED; add cases:
- `upsertRegionConfig_persistsEnabledState` _(AC-1)_
- `upsertRegionConfig_rejects_regionNotInGeographicScope` _(AC-1)_
- `deleteRegionConfig_returns204_whenRowExists` _(AC-2)_
- `deleteRegionConfig_isIdempotent_whenRowAbsent` _(AC-2)_
- `getRegionalConfigs_returnsAllRowsForItem` _(AC-3)_

### BE-3: RedemptionConfigController — region endpoints + @WebMvcTest

**File:** `src/main/java/com/tenxengage/app/controller/RedemptionConfigController.java` — MODIFIED; add:
- `GET /redemption/catalog/config/{catalogItemId}/regions` → `getRegionalConfigs(catalogItemId)`
- `PUT /redemption/catalog/config/{catalogItemId}/regions/{regionCode}` → `upsertRegionConfig(catalogItemId, regionCode, request)`
- `DELETE /redemption/catalog/config/{catalogItemId}/regions/{regionCode}` → `deleteRegionConfig(catalogItemId, regionCode)` → 204

All endpoints: `@RequiresPermission("action.redemption.configure")`

**File:** `src/test/java/com/tenxengage/app/controller/RedemptionConfigControllerTest.java` — MODIFIED; add:
- `putRegionConfig_returns200_whenValid` _(AC-1)_
- `putRegionConfig_returns422_whenRegionNotInScope` _(AC-1)_
- `putRegionConfig_returns403_forPartnerSeller` _(AC-4)_
- `deleteRegionConfig_returns204_whenExists` _(AC-2)_
- `deleteRegionConfig_returns204_whenAbsent` _(AC-2)_

### BE-4: @Audited annotations

Add to controller region endpoints per `spec.md → ## Audit Trail [BE]`:
- `PUT /regions/{regionCode}` → `action=UPDATED, resourceType=TENANT_CATALOG_CONFIG` _(AC-1)_
- `DELETE /regions/{regionCode}` → `action=DELETED, resourceType=TENANT_CATALOG_CONFIG` _(AC-2)_

---

## FE tasks [FE]

### FE-1: TypeScript types + service calls

**Files:**
- `src/types/redemption-catalog.types.ts` — MODIFIED; add `UpsertRegionConfigRequest`, `ClientCatalogRegionConfigResponse` interfaces from `../tenxengage-contracts/`
- `src/services/redemption-catalog.service.ts` — MODIFIED; add:
  - `getRegionalConfigs(catalogItemId): Promise<ClientCatalogRegionConfigResponse[]>` → `GET /api/v1/redemption/catalog/config/{catalogItemId}/regions`
  - `upsertRegionConfig(catalogItemId, regionCode, request): Promise<ClientCatalogRegionConfigResponse>` → `PUT /api/v1/redemption/catalog/config/{catalogItemId}/regions/{regionCode}`
  - `deleteRegionConfig(catalogItemId, regionCode): Promise<void>` → `DELETE /api/v1/redemption/catalog/config/{catalogItemId}/regions/{regionCode}`

### FE-2: TanStack Query hooks

**File:** `src/hooks/useRedemptionCatalog.ts` — MODIFIED; add:
- `useRegionalConfig(catalogItemId)`: queryKey `['redemption-catalog', 'regions', catalogItemId]`, staleTime `5 * 60 * 1000`; see `technical.md → ## Hook Specs [FE]`
- Mutations: `useUpsertRegionConfig` + `useDeleteRegionConfig` — both invalidate `['redemption-catalog', 'regions', catalogItemId]` on success _(AC-1, AC-2)_

### FE-3: RegionalConfigMatrix component + Vitest test

**Files:**
- `src/components/redemption-catalog/RegionalConfigMatrix.tsx` — NEW; props: `catalogItemId: string, geographicScope: string[]`; uses `useRegionalConfig(catalogItemId)`; renders a grid row per region in `geographicScope`; regions NOT in `geographicScope` are NOT rendered; each row shows region code + enabled toggle; toggle calls `useUpsertRegionConfig`; "Remove override" action calls `useDeleteRegionConfig` _(AC-2, AC-3)_
- `src/components/redemption-catalog/__tests__/RegionalConfigMatrix.test.tsx` — NEW; Vitest cases:
  - `renders enabled toggle for region in geographicScope` _(AC-1)_
  - `toggle calls upsertRegionConfig with correct regionCode` _(AC-1)_
  - `delete calls deleteRegionConfig` _(AC-2)_
  - `shows 422 error for region not in scope` _(AC-1)_

### FE-4: Wire into CatalogConfigPage

**File:** `src/pages/CatalogConfigPage.tsx` — MODIFIED; when a row in `TenantCatalogConfigTable` is expanded, render `RegionalConfigMatrix` with `catalogItemId` and `geographicScope` props from the selected item

---

## E2E test [FE]

**Scenario 1:** `'CLIENT_ADMIN adds a regional override'` _(covers AC-1, AC-3)_

**File:** `e2e/redemption-catalog-regional.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as CLIENT_ADMIN → navigate to `/settings/redemption/catalog` → expand item with `geographicScope=['US','GB']` → enable `US` region → verify PUT 200 |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/catalog/config/{id}/regions` → empty list; `PUT .../regions/US` → `{ regionCode: 'US', enabled: true }`; attempt with invalid region code → 422 |
| **Visible assertion** | `US` region shows enabled toggle; 422 error "Region not supported" visible on invalid attempt |
| **Negative case** | Attempt to enable region code `XX` not in `geographicScope` → inline 422 error |

---

**Scenario 2:** `'CLIENT_ADMIN deletes regional override — fallback to tenant-level'` _(covers AC-2, AC-3)_

**File:** `e2e/redemption-catalog-regional.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as CLIENT_ADMIN → expand item → existing `US` regional override shown → click "Remove override" for `US` → DELETE called → 204 |
| **APIs to mock via `page.route()`** | `GET .../regions` → list with one `US` row; `DELETE .../regions/US` → 204 |
| **Visible assertion** | `US` row disappears from regional config; fallback indicator "Using tenant default" shown |

---

## Execution checklist

**BE session:**
- [ ] `UpsertRegionConfigRequest.java` DTO created _(AC-1)_
- [ ] `ClientCatalogRegionConfigResponse.java` DTO created _(AC-1, AC-2)_
- [ ] `TenantRedemptionCatalogService.getRegionalConfigs()` implemented _(AC-3)_
- [ ] `TenantRedemptionCatalogService.upsertRegionConfig()` implemented — 422 if regionCode not in geographicScope _(AC-1)_
- [ ] `TenantRedemptionCatalogService.deleteRegionConfig()` implemented — idempotent _(AC-2)_
- [ ] `TenantRedemptionCatalogServiceTest` regional test cases pass _(AC-1, AC-2, AC-3)_
- [ ] `RedemptionConfigController` region endpoints added with `@RequiresPermission("action.redemption.configure")` _(AC-4)_
- [ ] `@Audited` on PUT + DELETE region endpoints _(AC-1, AC-2)_
- [ ] `RedemptionConfigControllerTest` regional cases pass _(AC-1, AC-2, AC-4)_

**FE session:**
- [ ] `UpsertRegionConfigRequest` + `ClientCatalogRegionConfigResponse` types added from contracts _(AC-1)_
- [ ] Region service functions added to `redemption-catalog.service.ts` _(AC-1, AC-2)_
- [ ] `useRegionalConfig` hook created with correct queryKey + staleTime _(AC-3)_
- [ ] `useUpsertRegionConfig` + `useDeleteRegionConfig` mutations with cache invalidation _(AC-1, AC-2)_
- [ ] `RegionalConfigMatrix.tsx` created — grid, toggle, delete, 422 error display _(AC-1, AC-3)_
- [ ] `RegionalConfigMatrix.test.tsx` Vitest tests pass _(AC-1, AC-2)_
- [ ] `RegionalConfigMatrix` wired into `CatalogConfigPage` expansion _(AC-1)_
- [ ] E2E: `'CLIENT_ADMIN adds a regional override'` passes _(AC-1, AC-3)_
- [ ] E2E: `'CLIENT_ADMIN deletes regional override — fallback to tenant-level'` passes _(AC-2, AC-3)_

---

## Done when

1. `./gradlew test` passes — all regional `TenantRedemptionCatalogServiceTest` + `RedemptionConfigControllerTest` cases green
2. `npm run test` passes — `RegionalConfigMatrix.test.tsx` Vitest cases green
3. `npx playwright test e2e/redemption-catalog-regional.spec.ts` passes against real BE
4. Every AC above is referenced by at least one passing test
