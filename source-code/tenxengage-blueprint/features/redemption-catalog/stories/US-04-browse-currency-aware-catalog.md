---
id: US-04
title: "Browse currency-aware catalog"
layers: ["BE", "FE"]
seed_id: "S-04"
touches_entities: ["RedemptionCatalogItem", "ClientCatalogItemConfig", "ClientCatalogRegionConfig"]
depends_on_stories: ["US-01"]
---

# US-04: Browse currency-aware catalog

## Description

**Actor:** PARTNER_SELLER or PARTNER_ADMIN
**Trigger:** Partner navigates to `/redemption-store` to view items they can redeem with their current balances.

**Steps:**
1. Partner opens `/redemption-store` → `RedemptionStorePage` renders `CatalogBrowseGrid`
2. FE calls `GET /api/v1/redemption/catalog` (optionally with `?currencyId&region&page&pageSize`)
3. `RedemptionCatalogBrowseService.browsePartnerCatalog()` executes:
   a. Loads items: `redemption_catalog_items` WHERE `is_active=true` INNER JOIN `client_catalog_item_configs` WHERE `client_id=:clientId AND enabled=true`
   b. Batch-loads all `ClientCatalogRegionConfig` rows for `(clientId, itemIds)` in one query (no N+1)
   c. Per-item regional resolution: row present for caller's region → use its `enabled`; row absent → use `ClientCatalogItemConfig.enabled`
   d. Currency filter: if `currencyId` param → filter by it; else include items for all currencies caller holds `RewardWallet` records for
   e. Computes `effectiveMinWalletBalance = COALESCE(config.minWalletBalanceOverride, 0)` per item; compares to `RewardWallet.availableBalance` → sets `canAfford` + `shortfallAmount`
   f. Sorts: group by `currency_id`; within group NON_CASH by regional score desc, CASH by effective min amount asc
4. Partner clicks an item → `CatalogItemDetailSheet` opens; "Redeem" CTA disabled if `canAfford=false`
5. `GET /api/v1/redemption/catalog/{id}` → 404 if item not enabled for tenant/region

**Expected outcome:** Partner sees only items relevant to their wallets and region; `canAfford=false` items show `ShortfallBadge`; payout timeline surfaced before submission; sensitive fields never returned.

**Negative paths:**
- No enabled items for tenant → empty state "No rewards available yet. Check back soon."
- Item deactivated mid-browse → `GET /{id}` returns 404 → FE: "This item is no longer available."
- All balances zero → all items `canAfford=false` with `shortfallAmount` populated; partner can still browse
- `pageSize > 50` → 400
- Missing `module.redemption_store` permission → 403
- Unauthenticated → 401

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/catalog` returns only items where `ClientCatalogItemConfig.enabled=true AND isActive=true` AND regional filter resolves to enabled for caller's region; grouped by `currencyId` (NON_CASH by regional score desc, CASH by min amount asc)
- **AC-2:** Items where `availableBalance < effectiveMinWalletBalance` are included with `canAfford=false` + `shortfallAmount` populated; items where `canAfford=true` include `shortfallAmount=0` (FR-02.9)
- **AC-3:** `estimatedPayoutTimeline` derived from `effectiveProcessingMode`: INSTANT → vendor SLA text; BATCH → next run date based on tenant `batchCadence`; APPROVAL_REQUIRED → "After admin approval + [vendor SLA]" (FR-02.11)
- **AC-4:** `GET /api/v1/redemption/catalog/{id}` returns 404 if item not globally active, not enabled for tenant, or not available in caller's region
- **AC-5:** `CatalogBrowseItemResponse` never includes `providerItemId`, `syncMetadata`, `xoxodayLastSyncedAt`, `minWalletBalance`, or `client_id`
- **AC-6:** Unauthenticated request → 401; caller without `module.redemption_store` → 403

---

## Out of Scope

- Redemption submission (F-03) — "Redeem" CTA is present but out of scope for this story
- Company wallet browse (F-03)
- Transaction history (F-05)
- Return/refund flow (F-06)

---

## Non-Functional Notes

- N+1 prevention is mandatory: batch-load all `ClientCatalogRegionConfig` rows for `(clientId, itemIds)` in a single query before per-item regional resolution. Never query per item.
- Response time P95 < 300ms on the browse endpoint (index `(client_id, enabled, currency_id)` covers the hot path).

---

## UI States

- [ ] **Loading:** Skeleton cards in `CatalogBrowseGrid` while `usePartnerCatalog()` is in flight
- [ ] **Empty:** "No rewards available yet. Check back soon." (edge case #9 copy)
- [ ] **ShortfallBadge:** Shown on each `canAfford=false` item card; format: `getCurrency(currencyId).rewardFormat`
- [ ] **Item detail sheet:** Opens on card click; "Redeem" CTA disabled + tooltip "Insufficient balance" when `!canAfford`
- [ ] **Item no longer available:** 404 on detail fetch → "This item is no longer available." inline in sheet

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 (items must be globally active); FE depends on US-01 BE done

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-02.9, FR-02.10, FR-02.11
- `spec.md → ## Data Model / Entities [BE]` — `RedemptionCatalogItem`, `ClientCatalogItemConfig`, `ClientCatalogRegionConfig` fields
- `spec.md → ## API Endpoints [BE + FE] → Partner` — browse endpoints + filter logic
- `spec.md → ## DTOs [BE]` — `CatalogBrowseItemResponse` (explicit fields — no sensitive data)
- `spec.md → ## Service Layer [BE]` — `RedemptionCatalogBrowseService`, effective value resolution formulas
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `module.redemption_store`
- `spec.md → ## Security Design [BE]` — over-disclosure prevention; `CatalogBrowseItemResponse.from()` explicit record
- `spec.md → ## Edge Cases` — edge cases #1 (deactivated mid-browse), #4 (three-tier fallback), #5 (unknown partner region), #9 (empty catalog), #10 (all balances zero)
- `technical.md → ## Package Layout [BE]` — all file paths
- `technical.md → ## Repository Queries [BE]` — `findByCurrencyIdInAndIsActive`, `findByClientIdAndEnabledAndRedemptionCatalogItemIdIn`, `findByClientIdAndRedemptionCatalogItemId`

---

## BE tasks [BE]

### BE-1: CatalogBrowseItemResponse DTO

**File:**
- `src/main/java/com/tenxengage/app/dto/response/CatalogBrowseItemResponse.java` — explicit record; `from(RedemptionCatalogItem, ClientCatalogItemConfig, walletBalance, region)` factory; fields: `id`, `name`, `description`, `category`, `currencyId`, `effectiveMinTransactionAmount`, `effectiveProcessingMode`, `estimatedPayoutTimeline`, `canAfford`, `shortfallAmount`, `geographicScope`; explicitly omits `providerItemId`, `syncMetadata`, `xoxodayLastSyncedAt`, `minWalletBalance`, `client_id` _(AC-5)_

### BE-2: RedemptionCatalogBrowseService + unit tests

**Files:**
- `src/main/java/com/tenxengage/app/service/RedemptionCatalogBrowseService.java` — NEW; implement:
  - `browsePartnerCatalog(currencyId, region, pageable)`: `@Transactional(readOnly=true)`
    1. Resolve caller's currency IDs via `WalletService.getMyWallets()` (unless `currencyId` param provided)
    2. Pre-filter: `findByCurrencyIdInAndIsActive(currencyIds, true, pageable)`
    3. Batch-load configs: `findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(clientId, true, itemIds)` — single query, not per item
    4. Batch-load region configs: `findByClientIdAndRedemptionCatalogItemIdIn(clientId, itemIds)` in one query
    5. Per item: regional resolution → include/exclude; compute `effectiveMinWalletBalance`, `canAfford`, `shortfallAmount`; build `estimatedPayoutTimeline` from `effectiveProcessingMode` + tenant `batchCadence`
    6. Sort: group by currency, NON_CASH by regional score desc, CASH by min amount asc
  - `getPartnerCatalogItem(catalogItemId)`: `@Transactional(readOnly=true)`; 404 if globally inactive, not enabled for tenant, or region-excluded
- `src/test/java/com/tenxengage/app/service/RedemptionCatalogBrowseServiceTest.java` — NEW; test cases:
  - `browsePartnerCatalog_excludesInactiveItems` _(AC-1)_
  - `browsePartnerCatalog_excludesDisabledTenantItems` _(AC-1)_
  - `browsePartnerCatalog_appliesRegionalFilter` _(AC-1)_
  - `browsePartnerCatalog_setsCanAffordFalse_whenBalanceBelowMinWallet` _(AC-2)_
  - `browsePartnerCatalog_populatesShortfallAmount` _(AC-2)_
  - `browsePartnerCatalog_buildsPayoutTimeline_instant` _(AC-3)_
  - `browsePartnerCatalog_buildsPayoutTimeline_batch` _(AC-3)_
  - `browsePartnerCatalog_buildsPayoutTimeline_approvalRequired` _(AC-3)_
  - `browsePartnerCatalog_neverIncludesSensitiveFields` _(AC-5)_
  - `getPartnerCatalogItem_returns404_whenRegionExcluded` _(AC-4)_

### BE-3: RedemptionCatalogController + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/RedemptionCatalogController.java` — NEW; tag `Redemption Catalog`; base path `/api/v1/redemption/catalog`; `@RequiresPermission("module.redemption_store")`; no `@Audited` (read-only)
  - `GET /redemption/catalog` → `browsePartnerCatalog(currencyId, region, pageable)`
  - `GET /redemption/catalog/{id}` → `getPartnerCatalogItem(catalogItemId)`
- `src/test/java/com/tenxengage/app/controller/RedemptionCatalogControllerTest.java` — NEW; @WebMvcTest cases:
  - `browseCatalog_returns200_forPartnerSeller` _(AC-1)_
  - `browseCatalog_returns403_whenPermissionMissing` _(AC-6)_
  - `browseCatalog_returns401_whenUnauthenticated` _(AC-6)_
  - `browseCatalog_returns400_whenPageSizeExceeds50` _(AC-1)_
  - `getCatalogItem_returns404_whenItemDisabledForTenant` _(AC-4)_
  - `browseCatalog_responseNeverIncludesProviderItemId` _(AC-5)_

---

## FE tasks [FE]

### FE-1: TypeScript types + service calls

**Files:**
- `src/types/redemption-catalog.types.ts` — MODIFIED; add `CatalogBrowseItemResponse`, `CatalogBrowseFilters` interfaces from `../tenxengage-contracts/`
- `src/services/redemption-catalog.service.ts` — MODIFIED; add browse functions:
  - `getPartnerCatalog(filters): Promise<PaginatedResponse<CatalogBrowseItemResponse>>` → `GET /api/v1/redemption/catalog`
  - `getPartnerCatalogItem(id): Promise<CatalogBrowseItemResponse>` → `GET /api/v1/redemption/catalog/{id}`

### FE-2: TanStack Query hooks

**File:** `src/hooks/useRedemptionCatalog.ts` — MODIFIED; add:
- `usePartnerCatalog(filters)`: queryKey `['redemption-catalog', 'browse', { currencyId, region, page, pageSize }]`, staleTime `2 * 60 * 1000`; see `technical.md → ## Hook Specs [FE]`
- `usePartnerCatalogItem(id)`: queryKey `['redemption-catalog', 'item', id]`, staleTime `5 * 60 * 1000`

### FE-3a: CatalogItemCard component + Vitest test

**Files:**
- `src/components/redemption-catalog/CatalogItemCard.tsx` — NEW; props: `item: CatalogBrowseItemResponse`; renders item name, `estimatedPayoutTimeline`, currency formatted amount via `getCurrency(currencyId).rewardFormat`; shows `ShortfallBadge` when `!canAfford`
- `src/components/redemption-catalog/__tests__/CatalogItemCard.test.tsx` — NEW; Vitest cases:
  - `renders item name and payout timeline` _(AC-3)_
  - `renders ShortfallBadge when canAfford is false` _(AC-2)_
  - `does not render ShortfallBadge when canAfford is true` _(AC-2)_

### FE-3b: ShortfallBadge component + Vitest test

**Files:**
- `src/components/redemption-catalog/ShortfallBadge.tsx` — NEW; props: `shortfallAmount: string, currencyId: string`; formats via `getCurrency(currencyId).rewardFormat`
- `src/components/redemption-catalog/__tests__/ShortfallBadge.test.tsx` — NEW; Vitest cases:
  - `renders formatted shortfall amount` _(AC-2)_

### FE-3c: CatalogBrowseGrid component

**File:**
- `src/components/redemption-catalog/CatalogBrowseGrid.tsx` — NEW; props: `currencyId?: string, region?: string`; uses `usePartnerCatalog(filters)`; renders `CatalogItemCard` per item grouped by currency; skeleton on loading; empty state "No rewards available yet. Check back soon." when list is empty

### FE-3d: CatalogItemDetailSheet component

**File:**
- `src/components/redemption-catalog/CatalogItemDetailSheet.tsx` — NEW; props: `itemId: string`; uses `usePartnerCatalogItem(itemId)`; drawer with full item details + `estimatedPayoutTimeline`; "Redeem" CTA rendered but disabled with tooltip when `!canAfford`; 404 → "This item is no longer available." _(AC-4)_

### FE-4: RedemptionStorePage + route wiring

**Files:**
- `src/pages/RedemptionStorePage.tsx` — NEW; renders `CatalogBrowseGrid`; on card click opens `CatalogItemDetailSheet`
- `src/App.tsx` — MODIFIED; add:
  ```tsx
  <Route element={<ProtectedRoute permission="module.redemption_store" />}>
    <Route element={<AppLayout />}>
      <Route path="/redemption-store" element={<RedemptionStorePage />} />
    </Route>
  </Route>
  ```

---

## E2E test [FE]

**Scenario 1:** `'Partner browses catalog and sees shortfall badge on unaffordable item'` _(covers AC-1, AC-2, AC-3)_

**File:** `e2e/redemption-catalog-browse.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as PARTNER_SELLER → navigate to `/redemption-store` → catalog loads → one item with `canAfford=true` shows payout timeline; one item with `canAfford=false` shows ShortfallBadge → click `canAfford=false` item → detail sheet → "Redeem" CTA is disabled |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/catalog` → list with 2 items (one canAfford=true, one canAfford=false with shortfallAmount); `GET /api/v1/redemption/catalog/{id}` → single item with canAfford=false |
| **Visible assertion** | `expect(page.getByTestId('shortfall-badge')).toBeVisible()`; `expect(page.getByRole('button', { name: 'Redeem' })).toBeDisabled()` |

---

**Scenario 2:** `'Partner sees empty state when no items are enabled'` _(covers AC-1)_

**File:** `e2e/redemption-catalog-browse.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as PARTNER_SELLER → navigate to `/redemption-store` → catalog returns empty list |
| **APIs to mock via `page.route()`** | `GET /api/v1/redemption/catalog` → `{ data: [], total: 0 }` |
| **Visible assertion** | `expect(page.getByText('No rewards available yet. Check back soon.')).toBeVisible()` |

---

**Scenario 3:** `'Unauthenticated user is rejected'` _(covers AC-6)_

**File:** `e2e/redemption-catalog-browse.spec.ts`

| Field | Value |
|---|---|
| **User flow** | No login → navigate to `/redemption-store` → ProtectedRoute redirects |
| **APIs to mock via `page.route()`** | None — auth gate blocks before API |
| **Visible assertion** | URL is login page; `expect(page.getByTestId('redemption-store-page')).not.toBeAttached()` |

---

## Execution checklist

**BE session:**
- [ ] `CatalogBrowseItemResponse.java` DTO created — explicit record, sensitive fields excluded _(AC-5)_
- [ ] `RedemptionCatalogBrowseService.browsePartnerCatalog()` implemented — batch region load (no N+1), canAfford/shortfall, payout timeline, sorting _(AC-1, AC-2, AC-3)_
- [ ] `RedemptionCatalogBrowseService.getPartnerCatalogItem()` implemented — 404 on inactive/disabled/region-excluded _(AC-4)_
- [ ] `RedemptionCatalogBrowseServiceTest` unit tests pass _(AC-1, AC-2, AC-3, AC-4, AC-5)_
- [ ] `RedemptionCatalogController` created — browse + detail endpoints with `@RequiresPermission("module.redemption_store")` _(AC-6)_
- [ ] `RedemptionCatalogControllerTest` @WebMvcTest cases pass _(AC-1, AC-4, AC-5, AC-6)_

**FE session:**
- [ ] `CatalogBrowseItemResponse` + `CatalogBrowseFilters` types added from contracts _(AC-1)_
- [ ] Browse service functions added to `redemption-catalog.service.ts` _(AC-1, AC-4)_
- [ ] `usePartnerCatalog` + `usePartnerCatalogItem` hooks created with correct queryKey + staleTime _(AC-1)_
- [ ] `CatalogItemCard.tsx` created — payout timeline, ShortfallBadge conditional _(AC-2, AC-3)_
- [ ] `CatalogItemCard.test.tsx` Vitest tests pass _(AC-2, AC-3)_
- [ ] `ShortfallBadge.tsx` created — formatted shortfall _(AC-2)_
- [ ] `ShortfallBadge.test.tsx` Vitest tests pass _(AC-2)_
- [ ] `CatalogBrowseGrid.tsx` created — grouped list, skeleton, empty state _(AC-1)_
- [ ] `CatalogItemDetailSheet.tsx` created — Redeem CTA disabled when !canAfford, 404 message _(AC-4)_
- [ ] `RedemptionStorePage.tsx` created _(AC-1)_
- [ ] Route `/redemption-store` wired in `App.tsx` with `ProtectedRoute` _(AC-6)_
- [ ] E2E: `'Partner browses catalog and sees shortfall badge'` passes _(AC-1, AC-2, AC-3)_
- [ ] E2E: `'Partner sees empty state when no items are enabled'` passes _(AC-1)_
- [ ] E2E: `'Unauthenticated user is rejected'` passes _(AC-6)_

---

## Done when

1. `./gradlew test` passes — all `RedemptionCatalogBrowseServiceTest` + `RedemptionCatalogControllerTest` cases green
2. `npm run test` passes — `CatalogItemCard.test.tsx`, `ShortfallBadge.test.tsx` Vitest cases green
3. `npx playwright test e2e/redemption-catalog-browse.spec.ts` passes against real BE
4. Every AC above is referenced by at least one passing test
