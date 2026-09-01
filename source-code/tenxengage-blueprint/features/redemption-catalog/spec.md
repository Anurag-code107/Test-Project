---
slug: redemption-catalog
name: Redemption Catalog
status: reviewed
format: story-sliced
roadmap: redemption-store
created: 2026-05-06
reviewed: 2026-05-06
contract: null
---

# Feature: Redemption Catalog

## Amendments

| Date | Author | Description | Design doc |
|---|---|---|---|
| 2026-05-27 | Robert (review) | Navigation restructure: merged Global Catalog + Redemption Catalog into a single Platform Settings tab. Form enhancements: image upload, currency dropdown from DB, geographic scope multiselect from DB. | `docs/superpowers/specs/2026-05-27-redemption-catalog-enhancements-design.md` |

### Navigation (FE)
"Global Catalog" and "Redemption Catalog" sidebar links are removed. Both are merged into a "Redemption Catalog" tab in Platform Settings (between "Manage Business Rules" and "Builder Config"). The tab has two sub-tabs: **Catalog Items** (global catalog management) and **Tenant Config** (tenant-level configuration).

> **Format:** story-sliced
> **Stories, tasks, and per-story tests live in sibling files:**
> - [`stories.md`](stories.md) — story index + dependency graph
> - [`stories/`](stories/) — one `US-NN-*.md` per story
> - [`tasks/foundation.md`](tasks/foundation.md) — horizontal bedrock tasks
> - [`tracker.md`](tracker.md) — session status tracker
> - [`test-plan.md`](test-plan.md) — cross-story integration tests
>
> **This file is the design reference.** Implementers read it alongside their story file.
>
> **Technical artifacts** (Flyway SQL, file paths, query shapes, hook specs): see [`technical.md`](technical.md).

---

## Overview

Redemption Catalog introduces the global and tenant-level configuration infrastructure that bridges vendor item catalogs (Xoxoday, XTRM) and partner users. It has three distinct operating surfaces:

**Platform Admin** manages the master set of globally available redemption items — creating XTRM cash options and Xoxoday non-cash items, setting their default processing mode, geographic availability, and whether returns are accepted. A background sync job keeps Xoxoday items current.

**Client Admin** sees the global catalog and builds their tenant's redemption experience by enabling specific items, overriding processing modes and thresholds per item, restricting availability to specific regions, and configuring the tenant-wide `batchCadence` for batch-mode redemptions.

**Partners** (PARTNER_SELLER and PARTNER_ADMIN) browse a currency-aware, region-filtered view of only the items their Client Admin has enabled, with shortfall indicators for items their current balance cannot cover and payout timelines surfaced upfront before submission.

`RedemptionCatalogItem` is a **platform-level entity** (no `client_id`) — analogous to `FeatureFlag`. Tenant-specific configuration lives in `ClientCatalogItemConfig` and `ClientCatalogRegionConfig`. A single `TenantRedemptionSettings` row per tenant stores the `batchCadence` setting.

### Naming reconciliation

The BRD annex refers to `ClientRedemptionConfig` for the per-item tenant override entity. The platform naming convention for tenant-scoped config entities uses `Client*` prefix tied to a specific domain noun (`ClientFeatureOverride`, `ClientPermissionGrant`, `ClientNotificationRoleConfig`). Following this pattern, the spec uses `ClientCatalogItemConfig`. BRD name `ClientRedemptionConfig` is not used.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-02.1 | Platform Admin (TENX_ADMIN) can create, edit, activate, and deactivate global `RedemptionCatalogItem` records; each item specifies its `category` (CASH or NON_CASH), compatible `currencyId`, `defaultMinRedemptionAmount`, `defaultProcessingMode`, `geographicScope` (ISO country codes array), `providerItemId`, `isReturnable` flag, and `defaultReturnWindowDays` |
| FR-02.2 | Platform Admin can view vendor integration health: last Xoxoday sync timestamp and status, failed sync count, and the last 10 inbound webhook delivery log entries per vendor |
| FR-02.3 | A scheduled background job (`XoxodaySyncJobService`) periodically syncs the Xoxoday catalog; items removed from Xoxoday's API are automatically set to `isActive = false`; existing `ClientCatalogItemConfig` records are preserved |
| FR-02.4 | CLIENT_ADMIN can enable or disable any globally active `RedemptionCatalogItem` for their tenant by upserting a `ClientCatalogItemConfig` record; the change is immediately visible to partner users browsing the catalog |
| FR-02.5 | CLIENT_ADMIN can override the default processing mode, `minTransactionAmount`, `minWalletBalance`, and `returnWindowDays` for any enabled item at the tenant level; if no override is set, the global catalog item's default applies |
| FR-02.6 | CLIENT_ADMIN can configure a `batchCadence` (DAILY or WEEKLY) in `TenantRedemptionSettings`; this setting governs when all BATCH-mode redemptions for their tenant are processed by the batch job (F-03) |
| FR-02.7 | CLIENT_ADMIN can configure catalog item availability per region via `ClientCatalogRegionConfig`; the enabled regions must be a subset of the item's `geographicScope`; the platform enforces this hard limit and returns 422 if the client attempts to enable an item for a region not in `geographicScope` |
| FR-02.8 | If no `ClientCatalogRegionConfig` row exists for a specific (tenant, item, region) combination, the tenant-level `ClientCatalogItemConfig.enabled` value applies globally across all vendor-supported regions |
| FR-02.9 | PARTNER_SELLER and PARTNER_ADMIN browse a catalog filtered to: (a) items where `ClientCatalogItemConfig.enabled = true` for their tenant, (b) the item's `currencyId` is a currency for which the caller holds a `RewardWallet`, and (c) the item is available in the caller's geographic region; items where `availableBalance < effectiveMinWalletBalance` are included but marked `canAfford = false` with `shortfallAmount` populated |
| FR-02.10 | The partner catalog browse response is organized by currency type; NON_CASH (Xoxoday) items within a currency group are sorted by regional relevance; CASH (XTRM) items are sorted by effective minimum redemption amount ascending |
| FR-02.11 | Each catalog item in the partner browse response includes an `estimatedPayoutTimeline` string derived from the effective processing mode: INSTANT shows vendor SLA text, BATCH shows the next batch run date based on `batchCadence`, APPROVAL_REQUIRED shows "After admin approval + [vendor SLA]" |

---

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (reads)** | P95 < 300ms | Partner catalog browse; indexed on `(client_id, enabled, currency_id)` |
| **Response time (writes)** | P95 < 500ms | Client Admin config upserts |
| **Peak concurrent users** | 200 concurrent | Catalog browse is moderate-frequency |
| **Max page size** | 50 items | Hard cap on browse and admin list endpoints |
| **Availability** | 99.9% | Partner catalog browse is a core user-facing flow |
| **Data sensitivity** | INTERNAL | Catalog config is internal business data; vendor API credentials are CONFIDENTIAL (environment variables — never in DB) |
| **Compliance** | GDPR | No user PII in F-02 entities; no special GDPR treatment required beyond platform standards |
| **Audit retention** | 7 years | Per platform standard |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec` (status must be `reviewed`)
- [ ] F-01 (wallet-ledger-foundation) spec reviewed — `RewardWallet.currencyId` and `WalletService.getMyWallets()` needed for catalog browse shortfall computation
- [ ] Contracts generated via `/generate-contracts` in contracts repo
- [ ] Flyway migration V9 confirmed as next (after F-01's V6–V8; current latest before F-01: V5)
- [ ] ADR resolved: Platform Admin cross-tenant access mechanism (NEEDS_CLARIFICATION #1)
- [ ] ADR resolved: "TenXEngage Platform Admin" role constant — TENX_ADMIN or new role (NEEDS_CLARIFICATION #3)
- [ ] Confirm `partner_company_locations` table exists and `country` is the canonical partner region field (NEEDS_CLARIFICATION #2)

---

## New Enums [BE]

| Enum Class | Values | Notes |
|---|---|---|
| `RedemptionProcessingMode.java` | `INSTANT, BATCH, APPROVAL_REQUIRED` | Item's fulfillment timing mode; used at global and tenant-override levels; read by F-03 for submission routing |
| `RedemptionCategory.java` | `CASH, NON_CASH` | Determines vendor routing: CASH → XTRM, NON_CASH → Xoxoday; immutable after item creation |
| `BatchCadence.java` | `DAILY, WEEKLY` | Tenant-configured batch cadence; semantically distinct from `SyncCadence` (connector data sync) — separate enum |

_Path: `src/main/java/com/tenxengage/app/entity/enums/`_

---

## Data Model / Entities [BE]

### RedemptionCatalogItem (table: `redemption_catalog_items`)

_Path: `src/main/java/com/tenxengage/app/entity/RedemptionCatalogItem.java`_
_Extends `BaseEntity`, does **NOT** implement `TenantAware` — platform-level entity, no `client_id`_
_No `@Filter(tenantFilter)` — global records accessible to Platform Admin without tenant context_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, `gen_random_uuid()` | Inherited from BaseEntity |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `name` | `VARCHAR(255)` | NOT NULL | Display name — never exposes vendor branding |
| `description` | `VARCHAR(2000)` | NULL | Rich description shown on item detail |
| `image_url` | `VARCHAR(2000)` | NULL | URL or object key of the uploaded catalog item image (optional) |
| `category` | `VARCHAR(20)` | NOT NULL | `RedemptionCategory` enum: `CASH` or `NON_CASH` |
| `currency_id` | `VARCHAR(50)` | NOT NULL | The single currency this item redeems from (`cash`, `points`, `credits`, `tickets`) |
| `default_min_redemption_amount` | `DECIMAL(18,2)` | NOT NULL, CHECK > 0 | Vendor-imposed minimum; tenant can only increase via override, never decrease below this |
| `default_processing_mode` | `VARCHAR(30)` | NOT NULL, DEFAULT `'INSTANT'` | `RedemptionProcessingMode` enum |
| `geographic_scope` | `TEXT[]` | NOT NULL, DEFAULT `'{}'` | ISO 3166-1 alpha-2 country codes; empty array = global availability |
| `provider_item_id` | `VARCHAR(255)` | NULL (CASH) / NOT NULL enforced at service layer (NON_CASH) | Xoxoday product ID or XTRM payout type identifier |
| `is_returnable` | `BOOLEAN` | NOT NULL, DEFAULT `false` | CASH items always false (enforced by CHECK); NON_CASH configurable |
| `default_return_window_days` | `INT` | NOT NULL, DEFAULT `0` | Days after fulfillment for returns; 0 = returns disabled |
| `is_active` | `BOOLEAN` | NOT NULL, DEFAULT `true` | Platform Admin flag; false = hidden from all tenants immediately |
| `xoxoday_last_synced_at` | `TIMESTAMPTZ` | NULL | Set by sync job; NULL for CASH items |
| `sync_metadata` | `JSONB` | NULL | Xoxoday sync response metadata; never exposed in partner responses |

**Check constraints (V9):**
- `CHECK (category <> 'CASH' OR is_returnable = false)` — CASH items are never returnable
- `CHECK (category <> 'NON_CASH' OR provider_item_id IS NOT NULL)` — NON_CASH must have providerItemId (enforced at activation, not creation)

**Indexes:**
- `idx_redemption_catalog_items_category` on `(category, is_active)`
- `idx_redemption_catalog_items_currency` on `(currency_id, is_active)`
- `uq_redemption_catalog_items_provider` UNIQUE on `(category, provider_item_id)` WHERE `provider_item_id IS NOT NULL`

> **FE note:** Geographic scope options are loaded from `GET /api/v1/location-levels` (returns tenant location hierarchy). Existing Xoxoday-synced items may have ISO codes not present in the tenant hierarchy — these are shown as read-only "From sync" chips in the form (removable but not re-selectable via picker).

---

### TenantRedemptionSettings (table: `tenant_redemption_settings`)

_Path: `src/main/java/com/tenxengage/app/entity/TenantRedemptionSettings.java`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_
_One row per tenant (UNIQUE on `client_id`). Auto-created with defaults on first GET request._

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK | Inherited |
| `client_id` | `UUID` | NOT NULL, FK → clients, UNIQUE | Tenant isolation |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `batch_cadence` | `VARCHAR(20)` | NOT NULL, DEFAULT `'DAILY'` | `BatchCadence` enum |

**Indexes:**
- `idx_tenant_redemption_settings_client_id` on `client_id` (covered by UNIQUE constraint)

---

### ClientCatalogItemConfig (table: `client_catalog_item_configs`)

_Path: `src/main/java/com/tenxengage/app/entity/ClientCatalogItemConfig.java`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_
_One row per (client, catalog item). UNIQUE on `(client_id, redemption_catalog_item_id)`._

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK | Inherited |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `redemption_catalog_item_id` | `UUID` | NOT NULL, FK → redemption_catalog_items | The globally active item being configured |
| `enabled` | `BOOLEAN` | NOT NULL, DEFAULT `false` | Whether the item is visible and selectable in this tenant |
| `processing_mode_override` | `VARCHAR(30)` | NULL | `RedemptionProcessingMode`; NULL = inherit global default |
| `min_transaction_amount_override` | `DECIMAL(18,2)` | NULL, CHECK > 0 | NULL = inherit `defaultMinRedemptionAmount`; cannot be set below item's global default |
| `min_wallet_balance_override` | `DECIMAL(18,2)` | NULL, CHECK >= 0 | Balance check at redemption submission (F-03); 0 = no minimum; NULL = platform default of 0 |
| `return_window_days_override` | `INT` | NULL, CHECK >= 0 | NULL = inherit `defaultReturnWindowDays`; 0 = returns disabled |
| `version` | `BIGINT` | NOT NULL, DEFAULT 0 | Optimistic locking — detects concurrent config updates (edge case #11 → 409) |

**Uniqueness:**
- `uq_client_catalog_item_config` UNIQUE on `(client_id, redemption_catalog_item_id)`

**Business rule:** `minTransactionAmountOverride`, when non-null, must be ≥ `RedemptionCatalogItem.defaultMinRedemptionAmount`. Attempting to set below global minimum returns 422 "Minimum transaction amount cannot be set below the catalog item's platform minimum of {amount}."

**Indexes:**
- `idx_client_catalog_item_configs_client_id` on `client_id`
- `idx_client_catalog_item_configs_client_enabled` on `(client_id, enabled)`

---

### ClientCatalogRegionConfig (table: `client_catalog_region_configs`)

_Path: `src/main/java/com/tenxengage/app/entity/ClientCatalogRegionConfig.java`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_
_One row per (client, catalog item, region code). Absence of a row = fall back to tenant-level default._

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK | Inherited |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `redemption_catalog_item_id` | `UUID` | NOT NULL, FK → redemption_catalog_items | |
| `region_code` | `VARCHAR(10)` | NOT NULL | ISO 3166-1 alpha-2; must be a member of the item's `geographicScope` |
| `enabled` | `BOOLEAN` | NOT NULL | Regional enable/disable |

**Business rule (service layer):** `region_code` must exist in the target `RedemptionCatalogItem.geographicScope` array — 422 "Region {code} is not supported by this catalog item's vendor" if not.

**Business rule (Platform Admin geographicScope update):** If an update to `geographicScope` would orphan existing `ClientCatalogRegionConfig` rows for removed regions, the update is rejected with 422 "Cannot narrow geographic scope while tenant configurations exist for region(s) [{codes}]. Remove regional configurations first."

**Uniqueness:**
- `uq_client_catalog_region_config` UNIQUE on `(client_id, redemption_catalog_item_id, region_code)`

**Indexes:**
- `idx_client_catalog_region_configs_client_item` on `(client_id, redemption_catalog_item_id)`
- `idx_client_catalog_region_configs_client_region` on `(client_id, region_code)`

---

## Permissions & Feature Flags [BE + FE]

### Permission Matrix

_`module.redemption_store`, `action.redemption.view_history`, `action.redemption.view_all_history` are seeded in F-01 V8. F-02 V12 seeds only the two rows below._

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `action.redemption.catalog.manage` | Manage Redemption Catalog | ACTION | `PLATFORM` | REDEMPTION_ACTIONS | — | — | — | — |
| `action.redemption.configure` | Configure Tenant Catalog | ACTION | `INTERNAL` | REDEMPTION_ACTIONS | Y | — | — | — |

_`action.redemption.catalog.manage` is granted only to `TENX_ADMIN` — not seeded in `client_role_permissions` (no tenant role can hold it). Partner browse uses `module.redemption_store` (F-01)._

### Feature Flag

No new feature flag. `redemption_store` (seeded F-01, all tiers enabled) gates this feature. `action.redemption.configure` gates Client Admin access within it.

_Flyway seed SQL in `technical.md → ## Flyway Migrations [BE]`._

---

## DTOs [BE]

### Request DTOs

_Path: `src/main/java/com/tenxengage/app/dto/request/`_

| Class | Key Fields | Validation |
|---|---|---|
| `CreateRedemptionCatalogItemRequest` | `name, description, category, currencyId, defaultMinRedemptionAmount, defaultProcessingMode, geographicScope, providerItemId, isReturnable, defaultReturnWindowDays, imageUrl` | `@NotBlank name`, `@Size(max=255) name`, `@Size(max=2000) description`, `@NotNull category`, `@DecimalMin("0.01") amount`, `@NotNull currencyId`, `@Size(max=2000) imageUrl (optional)` |
| `UpdateRedemptionCatalogItemRequest` | Same fields as Create (all optional except structural constraints), imageUrl | `@Size`, conditional: NON_CASH must retain providerItemId, `@Size(max=2000) imageUrl (optional, null removes image)` |
| `UpsertClientCatalogItemConfigRequest` | `enabled, processingModeOverride, minTransactionAmountOverride, minWalletBalanceOverride, returnWindowDaysOverride` | `@NotNull enabled`, `@ValidEnum(RedemptionProcessingMode.class)` for override, `@DecimalMin("0.01")` for amounts, `@Min(0)` for days |
| `UpdateTenantRedemptionSettingsRequest` | `batchCadence` | `@NotNull`, `@ValidEnum(BatchCadence.class)` |
| `UpsertRegionConfigRequest` | `enabled` | `@NotNull` |

### Response DTOs

_Path: `src/main/java/com/tenxengage/app/dto/response/`_

| Record | Static Factory | Notes |
|---|---|---|
| `RedemptionCatalogItemResponse` | `from(RedemptionCatalogItem)` | Platform Admin list — omits `syncMetadata` |
| `RedemptionCatalogItemDetailResponse` | `from(RedemptionCatalogItem)` | Platform Admin detail — includes `xoxodayLastSyncedAt`, omits raw `syncMetadata` |
| `TenantCatalogItemResponse` | `from(RedemptionCatalogItem, ClientCatalogItemConfig)` | Client Admin view — global item + tenant config overlay; includes `isGloballyActive` field |
| `CatalogBrowseItemResponse` | `from(RedemptionCatalogItem, ClientCatalogItemConfig, walletBalance, region)` | Partner browse — never exposes `providerItemId`, `syncMetadata`, `minWalletBalance`, `client_id`; adds `canAfford`, `shortfallAmount`, `estimatedPayoutTimeline` |
| `TenantRedemptionSettingsResponse` | `from(TenantRedemptionSettings)` | Tenant batchCadence |
| `ClientCatalogItemConfigResponse` | `from(ClientCatalogItemConfig)` | Config details for one item |
| `ClientCatalogRegionConfigResponse` | `from(ClientCatalogRegionConfig)` | Regional config for one region |
| `IntegrationHealthResponse` | `(syncStatus, lastSyncAt, failedSyncCount, recentWebhooks)` | Platform Admin integration health |

**Never include in partner responses:** `providerItemId`, `syncMetadata`, `xoxodayLastSyncedAt`, `minWalletBalance`, `client_id`.

---

## API Endpoints [BE + FE]

### Platform Admin — Global Catalog Management [BE + FE]

_Controller: `RedemptionCatalogAdminController` (`/api/v1/admin/redemption-catalog`)_
_Auth: `@RequiresPermission("action.redemption.catalog.manage")` — TENX_ADMIN only; TenantFilter bypassed_

| Method | Path | Request | Response | Status | Audit |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/redemption-catalog` | `?category, isActive, search, page, pageSize` | `PaginatedResponse<RedemptionCatalogItemResponse>` | 200 | — |
| `POST` | `/api/v1/admin/redemption-catalog` | `CreateRedemptionCatalogItemRequest` | `RedemptionCatalogItemDetailResponse` | 201 | `@Audited` |
| `GET` | `/api/v1/admin/redemption-catalog/{id}` | — | `RedemptionCatalogItemDetailResponse` | 200 | — |
| `PUT` | `/api/v1/admin/redemption-catalog/{id}` | `UpdateRedemptionCatalogItemRequest` | `RedemptionCatalogItemDetailResponse` | 200 | `@Audited` |
| `POST` | `/api/v1/admin/redemption-catalog/{id}/image` | `multipart/form-data (file)` | `RedemptionCatalogItemResponse` | 200 | `@Audited` |
| `PATCH` | `/api/v1/admin/redemption-catalog/{id}/activate` | — | `RedemptionCatalogItemResponse` | 200 | `@Audited(action=ACTIVATED)` |
| `PATCH` | `/api/v1/admin/redemption-catalog/{id}/deactivate` | — | `RedemptionCatalogItemResponse` | 200 | `@Audited(action=DEACTIVATED)` |
| `POST` | `/api/v1/admin/redemption-catalog/sync` | — | `{ jobId, status }` | 202 | `@Audited(action=SYNCED)` |
| `GET` | `/api/v1/admin/redemption-catalog/integration-health` | — | `IntegrationHealthResponse` | 200 | — |

### Client Admin — Tenant Catalog Configuration [BE + FE]

_Controller: `RedemptionConfigController` (`/api/v1/redemption`)_
_All endpoints: `@RequiresPermission("action.redemption.configure")`_

| Method | Path | Request | Response | Status | Audit |
|---|---|---|---|---|---|
| `GET` | `/api/v1/redemption/settings` | — | `TenantRedemptionSettingsResponse` | 200 | — |
| `PUT` | `/api/v1/redemption/settings` | `UpdateTenantRedemptionSettingsRequest` | `TenantRedemptionSettingsResponse` | 200 | `@Audited` |
| `GET` | `/api/v1/redemption/catalog/config` | `?enabled, category, search, page, pageSize` | `PaginatedResponse<TenantCatalogItemResponse>` | 200 | — |
| `PUT` | `/api/v1/redemption/catalog/config/{catalogItemId}` | `UpsertClientCatalogItemConfigRequest` | `ClientCatalogItemConfigResponse` | 200 | `@Audited` |
| `GET` | `/api/v1/redemption/catalog/config/{catalogItemId}/regions` | — | `List<ClientCatalogRegionConfigResponse>` | 200 | — |
| `PUT` | `/api/v1/redemption/catalog/config/{catalogItemId}/regions/{regionCode}` | `UpsertRegionConfigRequest` | `ClientCatalogRegionConfigResponse` | 200 | `@Audited` |
| `DELETE` | `/api/v1/redemption/catalog/config/{catalogItemId}/regions/{regionCode}` | — | — | 204 | `@Audited` |

### Partner — Currency-Aware Catalog Browse [BE + FE]

_Controller: `RedemptionCatalogController` (`/api/v1/redemption/catalog`)_
_Permission: `@RequiresPermission("module.redemption_store")`_

| Method | Path | Request | Response | Status | Notes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/redemption/catalog` | `?currencyId, region, page, pageSize` | `PaginatedResponse<CatalogBrowseItemResponse>` | 200 | Currency-aware, region-aware, shortfall-aware |
| `GET` | `/api/v1/redemption/catalog/{id}` | — | `CatalogBrowseItemResponse` | 200 | 404 if item not enabled for tenant or not available in region |

**Browse filter logic (service layer — not in DB query directly):**
1. Start with `redemption_catalog_items` WHERE `is_active = true`
2. INNER JOIN `client_catalog_item_configs` WHERE `client_id = :clientId AND enabled = true`
3. Regional filter: batch-load all `client_catalog_region_configs` for `(clientId, itemIds)` in one query (NOT per-item — prevents N+1); for each item resolve: row present for callerRegion → use its `enabled`; row absent → use `ClientCatalogItemConfig.enabled` (fallback)
4. Currency filter: if `currencyId` param provided → filter by it; otherwise include items for all currencies the caller holds `RewardWallet` records for
5. Per item: compute `effectiveMinWalletBalance = COALESCE(config.minWalletBalanceOverride, 0)`; compare to caller's `RewardWallet.availableBalance` → set `canAfford` and `shortfallAmount`
6. Sort: group by `currency_id`, then within group: NON_CASH sorted by Xoxoday regional score desc, CASH sorted by effective min amount asc

**Error responses (all endpoints):**
- `400` — validation failure, invalid enum value, search > 200 chars, pageSize > 50
- `401` — not authenticated
- `403` — insufficient permissions
- `404` — item not found, not globally active, or not enabled for tenant
- `422` — business rule violation (region not in geographicScope, minAmount below global floor, geographicScope narrowing blocked by existing region configs)
- `429` — rate limit exceeded

---

## Service Layer [BE]

_Path: `src/main/java/com/tenxengage/app/service/`_

### RedemptionCatalogAdminService

| Method | Return Type | Notes |
|---|---|---|
| `createCatalogItem(request)` | `RedemptionCatalogItemDetailResponse` | `@Transactional`; validates providerItemId uniqueness per category |
| `updateCatalogItem(id, request)` | `RedemptionCatalogItemDetailResponse` | `@Transactional`; rejects geographicScope narrowing if orphaned region configs exist |
| `activateCatalogItem(id)` | `RedemptionCatalogItemResponse` | `@Transactional`; validates NON_CASH has providerItemId before activating |
| `deactivateCatalogItem(id)` | `RedemptionCatalogItemResponse` | `@Transactional`; sets `isActive=false`; does NOT cascade to `ClientCatalogItemConfig` |
| `listCatalogItems(filters, pageable)` | `Page<RedemptionCatalogItemResponse>` | `@Transactional(readOnly=true)` |
| `getCatalogItemDetail(id)` | `RedemptionCatalogItemDetailResponse` | `@Transactional(readOnly=true)` |
| `triggerXoxodaySync()` | `SyncJobResponse` | Submits async task to `XoxodaySyncJobService`; returns jobId |
| `getIntegrationHealth()` | `IntegrationHealthResponse` | Reads `xoxoday_last_synced_at` from items + last webhook log entries |

### TenantRedemptionCatalogService

| Method | Return Type | Notes |
|---|---|---|
| `getTenantSettings()` | `TenantRedemptionSettingsResponse` | `@Transactional(readOnly=true)`; auto-creates with DAILY default if no row (find-or-create with `SELECT FOR UPDATE`) |
| `updateTenantSettings(request)` | `TenantRedemptionSettingsResponse` | `@Transactional` |
| `getTenantCatalog(filters, pageable)` | `Page<TenantCatalogItemResponse>` | `@Transactional(readOnly=true)`; returns all globally active items with tenant config overlay |
| `upsertItemConfig(catalogItemId, request)` | `ClientCatalogItemConfigResponse` | `@Transactional`; 404 if item not globally active; 422 if minTransactionAmount below global floor |
| `getRegionalConfigs(catalogItemId)` | `List<ClientCatalogRegionConfigResponse>` | `@Transactional(readOnly=true)` |
| `upsertRegionConfig(catalogItemId, regionCode, request)` | `ClientCatalogRegionConfigResponse` | `@Transactional`; 422 if regionCode not in `geographicScope` |
| `deleteRegionConfig(catalogItemId, regionCode)` | `void` | `@Transactional`; idempotent — no-op if row doesn't exist |

### RedemptionCatalogBrowseService

| Method | Return Type | Notes |
|---|---|---|
| `browsePartnerCatalog(currencyId, region, pageable)` | `Page<CatalogBrowseItemResponse>` | `@Transactional(readOnly=true)`; resolves caller's wallets via `WalletService.getMyWallets()` |
| `getPartnerCatalogItem(catalogItemId)` | `CatalogBrowseItemResponse` | `@Transactional(readOnly=true)`; 404 if not enabled for tenant/region |

**Effective value resolution (consumed by F-03 at submission time):**
- `effectiveProcessingMode` = `COALESCE(ClientCatalogItemConfig.processingModeOverride, RedemptionCatalogItem.defaultProcessingMode)`
- `effectiveMinTransactionAmount` = `COALESCE(ClientCatalogItemConfig.minTransactionAmountOverride, RedemptionCatalogItem.defaultMinRedemptionAmount)`
- `effectiveMinWalletBalance` = `COALESCE(ClientCatalogItemConfig.minWalletBalanceOverride, 0)`
- `effectiveReturnWindowDays` = `COALESCE(ClientCatalogItemConfig.returnWindowDaysOverride, RedemptionCatalogItem.defaultReturnWindowDays)`

**Tenant isolation contract:** `TenantRedemptionCatalogService` and `RedemptionCatalogBrowseService` resolve `clientId` from `TenantContext.getCurrentClientId()`. `RedemptionCatalogAdminService` operates on global entities — does NOT use `TenantContext`.

---

## Workflow / Status Transitions [BE + FE]

### RedemptionCatalogItem global lifecycle

```
INACTIVE (isActive=false) ←── [Platform Admin: deactivate / Xoxoday sync auto-deactivate]
           |
           [Platform Admin: activate]
           ↓
ACTIVE (isActive=true)  ──── available for all tenant configurations
```

**Deactivation rules:**
- Setting `isActive=false` immediately hides the item from all partner browse responses (browse query filters by `is_active=true`)
- `ClientCatalogItemConfig` and `ClientCatalogRegionConfig` records are preserved on deactivation — Client Admin's configuration is not lost if the item is later re-activated
- The `TenantCatalogItemResponse` (Client Admin view) includes `isGloballyActive` field so admins see items marked as "Globally inactive" and can take action

**NON_CASH item activation gate:** A NON_CASH item cannot be activated unless `provider_item_id IS NOT NULL`. Attempting to activate without it returns 422 "Cannot activate a non-cash catalog item without a provider item ID."

---

## Security Design [BE]

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| `providerItemId` | Internal | Excluded from all partner-facing responses; `CatalogBrowseItemResponse.from()` explicitly omits it |
| `syncMetadata` | Internal | Never returned to any client; admin-only field |
| `minWalletBalance` | Internal | Not returned in partner browse response — prevents gaming minimum balance requirements |
| Vendor API credentials (XTRM, Xoxoday keys) | Confidential | Stored as environment variables; never persisted in DB; never logged |
| `client_id` | Internal | Never returned in any API response |
| Item names, descriptions, payout timelines | Internal | Visible to all authenticated users with `module.redemption_store` within their tenant |

### Rate Limiting

| Endpoint | Limit | Scope | Reason |
|---|---|---|---|
| `GET /api/v1/redemption/catalog` | 60 req/min | Per user | Opened on every Redemption Store page load |
| `POST /api/v1/admin/redemption-catalog` | 10 req/min | Per platform admin | Admin mutations |
| `PUT /api/v1/admin/redemption-catalog/{id}` | 10 req/min | Per platform admin | Admin mutations |
| `PATCH /api/v1/admin/redemption-catalog/{id}/activate` | 10 req/min | Per platform admin | Admin state changes |
| `PATCH /api/v1/admin/redemption-catalog/{id}/deactivate` | 10 req/min | Per platform admin | Admin state changes |
| `POST /api/v1/admin/redemption-catalog/sync` | 2 req/min | Per platform admin | Prevents Xoxoday API rate limit overrun |
| `PUT /api/v1/redemption/settings` | 20 req/min | Per tenant | Settings mutations |
| `PUT /api/v1/redemption/catalog/config/{id}` | 30 req/min | Per tenant | Bulk-config protection |
| `PUT /api/v1/redemption/catalog/config/{id}/regions/{code}` | 60 req/min | Per tenant | Regional grid bulk-update |
| `DELETE /api/v1/redemption/catalog/config/{id}/regions/{code}` | 60 req/min | Per tenant | Regional grid bulk-update |

### OWASP Risks & Mitigations

| Risk | Where | Mitigation |
|---|---|---|
| **Broken Access Control (A01)** | Platform Admin endpoints | `@RequiresPermission("action.redemption.catalog.manage")` + TenantFilter bypass scoped to TENX_ADMIN; PARTNER_* cannot call `/api/v1/admin/*` |
| **Broken Access Control (A01)** | Client Admin config endpoints | `@RequiresPermission("action.redemption.configure")`; PARTNER_* receive 403 |
| **IDOR (A01)** | `PUT /api/v1/redemption/catalog/config/{catalogItemId}` | `catalogItemId` is global (no `client_id`); service validates `isActive=true` — inactive items return 404; Hibernate `@Filter` on `ClientCatalogItemConfig` prevents cross-tenant config access |
| **Tenant data leakage** | All config endpoints | Hibernate `@Filter(tenantFilter)` on `ClientCatalogItemConfig` and `ClientCatalogRegionConfig` enforces isolation at query level |
| **Injection (A03)** | `search` param on admin list | Parameterized JPQL LIKE; `@Size(max=200)`; LIKE special characters escaped before use |
| **Over-disclosure** | Partner browse response | `CatalogBrowseItemResponse.from()` explicit record — only declared fields serialized |
| **Business rule bypass** | minTransactionAmount below global floor | Service validates override ≥ global default before persisting; 422 on violation |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `name` | `@NotBlank`, `@Size(max=255)` | 400 |
| `description` | `@Size(max=2000)` | 400 |
| `providerItemId` | `@Size(max=255)` | 400 |
| `geographicScope` codes | Service validates against known ISO 3166-1 alpha-2 set | 422 |
| `regionCode` (path param) | Service validates against item's `geographicScope` | 422 |
| `batchCadence` | `@ValidEnum(BatchCadence.class)` | 400 |
| `minTransactionAmountOverride` | `@DecimalMin("0.01")` when non-null; service validates ≥ global min | 400 / 422 |
| `returnWindowDaysOverride` | `@Min(0)` when non-null | 400 |
| `search` | `@Size(max=200)` | 400 |
| `pageSize` | `@Max(50)` | 400 |

---

## Audit Trail [BE]

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| CREATE `RedemptionCatalogItem` | `RedemptionCatalogItem` | Full item snapshot, `createdBy` | TENX_ADMIN |
| UPDATE `RedemptionCatalogItem` | `RedemptionCatalogItem` | Changed fields, `updatedBy` | TENX_ADMIN |
| ACTIVATE/DEACTIVATE | `RedemptionCatalogItem` | Old `isActive` → new, `changedBy` | TENX_ADMIN |
| SYNC triggered | `RedemptionCatalogItem` | `jobId`, `triggeredBy` | TENX_ADMIN |
| UPSERT `ClientCatalogItemConfig` | `ClientCatalogItemConfig` | `enabled` change, config overrides, `changedBy` | CLIENT_ADMIN |
| UPSERT `ClientCatalogRegionConfig` | `ClientCatalogRegionConfig` | `regionCode`, `enabled`, `changedBy` | CLIENT_ADMIN |
| DELETE `ClientCatalogRegionConfig` | `ClientCatalogRegionConfig` | `regionCode`, `deletedBy` | CLIENT_ADMIN |
| UPDATE `TenantRedemptionSettings` | `TenantRedemptionSettings` | Old `batchCadence` → new, `changedBy` | CLIENT_ADMIN |

### New Audit Enum Values

| Enum | New Value | Reason |
|---|---|---|
| `AuditResourceType` | `REDEMPTION_CATALOG_ITEM` | New global entity type for platform audit |
| `AuditResourceType` | `TENANT_CATALOG_CONFIG` | Covers `ClientCatalogItemConfig` and `ClientCatalogRegionConfig` audit events |
| `AuditResourceType` | `TENANT_REDEMPTION_SETTINGS` | Settings change audit |

### `@Audited` Annotation Details (Non-CRUD)

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `PATCH /{id}/activate` | `ACTIVATED` | `REDEMPTION_CATALOG_ITEM` | `"Activated catalog item"` |
| `PATCH /{id}/deactivate` | `DEACTIVATED` | `REDEMPTION_CATALOG_ITEM` | `"Deactivated catalog item"` |
| `POST /sync` | `SYNCED` | `REDEMPTION_CATALOG_ITEM` | `"Triggered Xoxoday catalog sync"` |

**Audit record retention:** 7 years.

---

## Observability [BE]

### MDC Fields

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` from JWT | `TenantFilter` (existing) |
| `userId` | User ID from JWT | `JwtAuthenticationFilter` (existing) |
| `featureArea` | `"redemption-catalog"` | Set in service constructors |

### Key Log Events

| Event | Level | `step` value | Key Fields |
|---|---|---|---|
| Catalog item created | INFO | `catalog_item_created` | `catalogItemId`, `category`, `currencyId` |
| Catalog item deactivated | INFO | `catalog_item_deactivated` | `catalogItemId` |
| Catalog item activated | INFO | `catalog_item_activated` | `catalogItemId` |
| Xoxoday sync started | INFO | `xoxoday_sync_started` | `jobId` |
| Xoxoday sync completed | INFO | `xoxoday_sync_completed` | `jobId`, `itemsSynced`, `itemsDeactivated` |
| Xoxoday sync failed | ERROR | `xoxoday_sync_failed` | `jobId`, `errorMessage` (sanitized) |
| Xoxoday item auto-deactivated | INFO | `xoxoday_item_auto_deactivated` | `catalogItemId`, `providerItemId` |
| Tenant config updated | INFO | `tenant_catalog_config_updated` | `catalogItemId`, `enabled`, `tenantId` |
| Region config updated | INFO | `region_config_updated` | `catalogItemId`, `regionCode`, `enabled` |
| Region scope violation | WARN | `region_scope_violation` | `catalogItemId`, `regionCode` |
| Partner region unknown | WARN | `partner_region_unknown` | `userId` — no region on profile |
| Shortfall computed | DEBUG | `catalog_shortfall_computed` | `catalogItemId`, `currencyId` — never log amounts |
| Tenant isolation violation | ERROR | `tenant_isolation_violation` | `requestedId`, `callerTenantId` |

### Metrics

| Metric | Type | Labels |
|---|---|---|
| `redemption.catalog.items.total` | Counter | `category`, `isActive` |
| `redemption.catalog.browse.total` | Counter | `tenantId`, `currencyId` |
| `redemption.catalog.shortfall.total` | Counter | `tenantId`, `currencyId` |
| `redemption.catalog.sync.duration_ms` | Histogram | — |
| `redemption.catalog.sync.items_deactivated.total` | Counter | — |
| `redemption.tenant_config.updates.total` | Counter | `tenantId`, `enabled` |

---

## Frontend Specification [FE]

_TypeScript types live in `../tenxengage-contracts/` — copy from there, do not hand-write. Full FE file paths and hook specs: see `technical.md`._

### Pages

| Page | Route | Permission | Sidebar Entry |
|---|---|---|---|
| `RedemptionStorePage` | `/redemption-store` | `module.redemption_store` | Yes — "Redemption Store" (partner sidebar) |
| `CatalogConfigPage` | `/settings/redemption/catalog` | `action.redemption.configure` | Yes — under "Redemption" in Client Admin settings |
| `GlobalCatalogAdminPage` | `/admin/redemption-catalog` | `action.redemption.catalog.manage` | Yes — Platform Admin sidebar |

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `CatalogBrowseGrid` | `currencyId?, region?` | `usePartnerCatalog()` | Groups items by currency type; shows shortfall badges |
| `CatalogItemCard` | `item: CatalogBrowseItemResponse` | — | Name, payout timeline, `canAfford` indicator; uses `getCurrency(currencyId)` for formatting |
| `ShortfallBadge` | `shortfallAmount, currencyId` | — | Inline badge for items partner cannot afford; format via `getCurrency().rewardFormat` |
| `CatalogItemDetailSheet` | `itemId` | `usePartnerCatalogItem(id)` | Drawer with full details + payout timeline; "Redeem" CTA disabled if `!canAfford` |
| `TenantCatalogConfigTable` | — | `useTenantCatalogConfig()` | Admin table with enable toggle + config panel per item; shows `isGloballyActive` warning |
| `ItemConfigPanel` | `catalogItemId` | `useCatalogItemConfig(id)` | Processing mode override, thresholds, return window fields |
| `RegionalConfigMatrix` | `catalogItemId, geographicScope` | `useRegionalConfig(id)` | Region × enabled grid; disables regions not in `geographicScope` |
| `GlobalCatalogItemForm` | `item?, onSave` | — | Platform Admin create/edit form |
| `SyncStatusBanner` | — | `useIntegrationHealth()` | Last sync time, status; "Trigger Sync" button |

### Forms

| Form | Fields | Validation | Submit Action |
|---|---|---|---|
| `GlobalCatalogItemForm` | `name, description, category, currencyId, defaultMinRedemptionAmount, defaultProcessingMode, geographicScope (multiselect), providerItemId, isReturnable, defaultReturnWindowDays` | `createCatalogItemSchema` (zod) | `POST /api/v1/admin/redemption-catalog` |
| `ItemConfigForm` | `enabled, processingModeOverride, minTransactionAmountOverride, minWalletBalanceOverride, returnWindowDaysOverride` | `catalogItemConfigSchema` (zod) | `PUT /api/v1/redemption/catalog/config/{id}` |
| `TenantRedemptionSettingsForm` | `batchCadence` (radio: DAILY / WEEKLY) | `tenantRedemptionSettingsSchema` (zod) | `PUT /api/v1/redemption/settings` |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `usePartnerCatalog(filters)` | `['redemption-catalog', 'browse', filters]` | `GET /api/v1/redemption/catalog` | 2 min | On partner redemption mutations (F-03) |
| `usePartnerCatalogItem(id)` | `['redemption-catalog', 'item', id]` | `GET /api/v1/redemption/catalog/{id}` | 5 min | On redemption |
| `useTenantCatalogConfig(filters)` | `['redemption-catalog', 'config', filters]` | `GET /api/v1/redemption/catalog/config` | 5 min | On `upsertItemConfig` |
| `useCatalogItemConfig(catalogItemId)` | `['redemption-catalog', 'config', catalogItemId]` | `GET /api/v1/redemption/catalog/config` (single item) | 5 min | On `upsertItemConfig`; used by `ItemConfigPanel` |
| `useRegionalConfig(catalogItemId)` | `['redemption-catalog', 'regions', catalogItemId]` | `GET /api/v1/redemption/catalog/config/{id}/regions` | 5 min | On upsert/delete region config |
| `useTenantRedemptionSettings()` | `['redemption-settings']` | `GET /api/v1/redemption/settings` | 10 min | On settings update |
| `useGlobalCatalogItems(filters)` | `['global-catalog', filters]` | `GET /api/v1/admin/redemption-catalog` | 2 min | On create/update/activate/deactivate/sync |
| `useIntegrationHealth()` | `['redemption-integration-health']` | `GET /api/v1/admin/redemption/integration-health` | 1 min | On sync trigger |

---

## Caching Strategy [BE]

`RedemptionCatalogItem` global list is a good caching candidate — changes only on Platform Admin mutation or sync job completion. Client-level config is not cached (tenant configs change frequently; stale reads would give partners wrong availability).

| What | Cache | TTL | Key | Invalidation |
|---|---|---|---|---|
| Global catalog items list | `@Cacheable("redemption-catalog-items")` | 10 min | `{category}:{currencyId}:{isActive}` | `@CacheEvict(allEntries=true)` on any item mutation or sync completion |

---

## Data Retention & Compliance [BE]

### Soft Delete

- `RedemptionCatalogItem`: No soft delete — deactivation via `isActive = false`; record preserved indefinitely.
- `ClientCatalogItemConfig`: No soft delete — disabling via `enabled = false`; config history preserved.
- `ClientCatalogRegionConfig`: Hard delete — regional overrides are explicitly removed; no need to preserve deleted configs.
- `TenantRedemptionSettings`: No delete — settings record is permanent per tenant.

### PII Handling

No PII fields in any F-02 entity. All entities contain product configuration data only.

### Data Retention Periods

| Data | Retention | Justification |
|---|---|---|
| `RedemptionCatalogItem` records | Indefinite | Global platform catalog history |
| `ClientCatalogItemConfig` records | Duration of client relationship + 7 years | Tenant config audit |
| `ClientCatalogRegionConfig` records | Until explicitly deleted | Operational config only |
| `TenantRedemptionSettings` | Duration of client relationship | Operational settings |
| Audit log entries | 7 years | Compliance requirement |

---

## Edge Cases [BE + FE]

1. **Item deactivated while partner is browsing**: `GET /api/v1/redemption/catalog/{id}` for a globally deactivated item returns 404; FE shows "This item is no longer available."
2. **Client Admin enables a globally inactive item**: `PUT /api/v1/redemption/catalog/config/{id}` with `enabled=true` while `isActive=false` → 404. FE shows "This item has been removed by the platform."
3. **Xoxoday sync auto-deactivates item Client Admin had enabled**: `ClientCatalogItemConfig.enabled` remains `true` but browse filter excludes it (`isActive=false`). `TenantCatalogItemResponse` shows `isGloballyActive=false` so Client Admin sees "Globally inactive" warning.
4. **Three-tier regional fallback**: For item X in region R: (1) if `ClientCatalogRegionConfig(client, X, R)` exists → use its `enabled`; (2) else if `ClientCatalogItemConfig(client, X)` exists → use its `enabled`; (3) else → item not visible.
5. **Partner region unknown**: Caller has no region on profile → browse includes items with no regional restrictions. Log WARN `step=partner_region_unknown`. FE: `region` query param allows explicit override.
6. **Geographic scope narrowing blocked**: Platform Admin attempts to remove a country from `geographicScope` where a `ClientCatalogRegionConfig` row exists for that country → 422 "Cannot narrow geographic scope while tenant configurations exist for region(s) [XX, YY]. Remove regional configurations first."
7. **`batchCadence` update mid-batch**: Changing cadence from WEEKLY to DAILY does NOT affect in-flight batch redemptions (F-03). Only the next scheduled run uses the new cadence.
8. **`TenantRedemptionSettings` auto-creation race**: Two concurrent first-access requests → `SELECT FOR UPDATE` prevents duplicate row; second thread waits and finds existing row.
9. **Empty catalog**: Client Admin enables no items → partner sees empty state: "No rewards available yet. Check back soon." Never show an empty catalog without explanation.
10. **All balances at zero**: All items show `canAfford: false` with `shortfallAmount = effectiveMinTransactionAmount`. Partner can browse freely; submission blocked at F-03.
11. **Concurrent config upsert**: Two admins updating same `ClientCatalogItemConfig` simultaneously → 409 "This configuration was updated concurrently. Refresh and retry."
12. **Sync job failure**: Xoxoday API timeout → log ERROR `step=xoxoday_sync_failed`, retry with exponential backoff (3 attempts), then DLQ. Existing items NOT auto-deactivated on transient failure — only on confirmed absence in a successful sync response.

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, @WebMvcTest, Vitest, E2E Playwright) — live inside each `stories/US-NN-*.md`
- **Cross-story integration tests** — in [test-plan.md](test-plan.md)

**Key integration scenarios for test-plan.md:**
- Full activation flow: Platform Admin creates item → Client Admin enables it → Partner browses and sees it
- Deactivation propagation: Platform Admin deactivates item → immediately absent from all partner browse responses
- Processing mode override: Client Admin sets BATCH override → partner catalog shows BATCH payout timeline
- Regional restriction: Client Admin restricts item to `US` only → `GB` partner does NOT see item; `US` partner does
- Geographic scope violation: Client Admin attempts to enable item in region not in `geographicScope` → 422
- Tenant isolation: Client Admin A's config for itemX is invisible to Client Admin B's tenant browse
- Xoxoday sync auto-deactivation: sync job processes response with item absent → item `isActive=false` → partner catalog empty for that item
- Shortfall indicator: `availableBalance=50`, `effectiveMinWalletBalance=100` → `canAfford=false`, `shortfallAmount=50`
- No regional override fallback: item enabled at tenant level, no regional configs → accessible from any region
- `batchCadence` update: change to WEEKLY → `estimatedPayoutTimeline` on next batch item shows "Next weekly batch" date

---

## Out of Scope

- Redemption submission, RESERVE/DEBIT/RELEASE — F-03
- Company wallet redemptions and approval queue — F-04
- Transaction history and export — F-05
- Non-cash return flow — F-06
- Analytics dashboard — F-07/F-08
- Vendor API webhook processing (XTRM/Xoxoday webhook handlers) — F-03
- Batch processing scheduler execution — F-03 (batchCadence configured here; execution is F-03's concern)
- Partial returns — v1 non-goal
- Cross-currency redemptions — v1 non-goal
- Reward balance expiration — Phase 2 (F-09)
- Dedicated batch scheduler status UI — Phase 2
- Platform Admin vendor credential management UI — managed via environment variables

---

## Planning Seeds (from feature brief)

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Manage global catalog items | Platform Admin can create and maintain the master set of redeemable items across both vendors | admin | — |
| S-02 | Configure tenant catalog | Client Admin enables/disables items and sets thresholds for their partner program | admin | S-01 |
| S-03 | Configure regional catalog | Client Admin controls which items are available to partners by region within their tenant | admin | S-02 |
| S-04 | Browse currency-aware catalog | Partners see only items they can redeem with their current balances, organized by currency type, with payout timelines shown | UI | F-01.S-01 |
| S-05 | Sync Xoxoday catalog | Platform keeps the non-cash catalog current with Xoxoday's item inventory and geographic availability | integration | S-01 |

---

## Verification Steps

### Backend Verification
1. `./gradlew bootRun` — app starts; V9 creates `redemption_catalog_items`, V10 creates `tenant_redemption_settings`, V11 creates `client_catalog_item_configs` + `client_catalog_region_configs`, V12 seeds permissions
2. `./gradlew test` — all new and existing tests pass
3. Security: `GET /api/v1/redemption/catalog/config` as PARTNER_SELLER → 403; `GET /api/v1/admin/redemption-catalog` without TENX_ADMIN → 403; unauthenticated → 401
4. Tenant isolation: Client A's `ClientCatalogItemConfig` absent from browse response when authenticated as Client B tenant
5. Deactivation propagation: set `isActive=false` on item → `GET /api/v1/redemption/catalog` as partner → item absent
6. Shortfall: partner with `availableBalance=0` → all items show `canAfford=false` with correct `shortfallAmount`

### Frontend Verification
1. `npm run build` — no TypeScript errors
2. Partner with `module.redemption_store` sees Redemption Store page; partner with no enabled items sees empty state
3. `canAfford: false` items show `ShortfallBadge` with correct currency format via `getCurrency(currencyId).rewardFormat`
4. Client Admin: toggling enable/disable in `TenantCatalogConfigTable` reflects immediately on partner browse
5. Regional config grid: adding a region restricts item to only that region for partners in other regions
6. `GlobalCatalogAdminPage` visible only when `action.redemption.catalog.manage` is present; hidden otherwise
