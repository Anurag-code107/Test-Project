> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V9__create_redemption_catalog_items_table.sql

```sql
CREATE TABLE redemption_catalog_items (
    id                              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    name                            VARCHAR(255)  NOT NULL,
    description                     VARCHAR(2000) NULL,
    category                        VARCHAR(20)   NOT NULL CHECK (category IN ('CASH', 'NON_CASH')),
    currency_id                     VARCHAR(50)   NOT NULL,
    default_min_redemption_amount   DECIMAL(18,2) NOT NULL CHECK (default_min_redemption_amount > 0),
    default_processing_mode         VARCHAR(30)   NOT NULL DEFAULT 'INSTANT',
    geographic_scope                TEXT[]        NOT NULL DEFAULT '{}',
    provider_item_id                VARCHAR(255)  NULL,
    is_returnable                   BOOLEAN       NOT NULL DEFAULT false,
    default_return_window_days      INT           NOT NULL DEFAULT 0,
    is_active                       BOOLEAN       NOT NULL DEFAULT true,
    xoxoday_last_synced_at          TIMESTAMPTZ   NULL,
    sync_metadata                   JSONB         NULL,
    CONSTRAINT chk_cash_not_returnable
        CHECK (category <> 'CASH' OR is_returnable = false)
);

CREATE INDEX idx_redemption_catalog_items_category
    ON redemption_catalog_items(category, is_active);
CREATE INDEX idx_redemption_catalog_items_currency
    ON redemption_catalog_items(currency_id, is_active);
CREATE UNIQUE INDEX uq_redemption_catalog_items_provider
    ON redemption_catalog_items(category, provider_item_id)
    WHERE provider_item_id IS NOT NULL;
```

### V10__create_tenant_redemption_settings_table.sql

```sql
CREATE TABLE tenant_redemption_settings (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id     UUID         NOT NULL REFERENCES clients(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    batch_cadence VARCHAR(20)  NOT NULL DEFAULT 'DAILY',
    CONSTRAINT uq_tenant_redemption_settings_client UNIQUE (client_id)
);

CREATE INDEX idx_tenant_redemption_settings_client_id
    ON tenant_redemption_settings(client_id);
```

### V11__create_catalog_config_tables.sql

```sql
-- Client Admin per-item tenant configuration
CREATE TABLE client_catalog_item_configs (
    id                              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                       UUID          NOT NULL REFERENCES clients(id),
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    redemption_catalog_item_id      UUID          NOT NULL REFERENCES redemption_catalog_items(id),
    enabled                         BOOLEAN       NOT NULL DEFAULT false,
    processing_mode_override        VARCHAR(30)   NULL,
    min_transaction_amount_override DECIMAL(18,2) NULL CHECK (min_transaction_amount_override > 0),
    min_wallet_balance_override     DECIMAL(18,2) NULL CHECK (min_wallet_balance_override >= 0),
    return_window_days_override     INT           NULL CHECK (return_window_days_override >= 0),
    version                         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_client_catalog_item_config
        UNIQUE (client_id, redemption_catalog_item_id)
);

CREATE INDEX idx_client_catalog_item_configs_client_id
    ON client_catalog_item_configs(client_id);
CREATE INDEX idx_client_catalog_item_configs_client_enabled
    ON client_catalog_item_configs(client_id, enabled);

-- Client Admin per-item per-region availability overrides
CREATE TABLE client_catalog_region_configs (
    id                              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                       UUID         NOT NULL REFERENCES clients(id),
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    redemption_catalog_item_id      UUID         NOT NULL REFERENCES redemption_catalog_items(id),
    region_code                     VARCHAR(10)  NOT NULL,
    enabled                         BOOLEAN      NOT NULL,
    CONSTRAINT uq_client_catalog_region_config
        UNIQUE (client_id, redemption_catalog_item_id, region_code)
);

CREATE INDEX idx_client_catalog_region_configs_client_item
    ON client_catalog_region_configs(client_id, redemption_catalog_item_id);
CREATE INDEX idx_client_catalog_region_configs_client_region
    ON client_catalog_region_configs(client_id, region_code);
```

### V12__seed_redemption_catalog_permissions.sql

```sql
-- ============================================================
-- Redemption Catalog F-02: Permission additions
-- Note: module.redemption_store, action.redemption.view_history,
--       action.redemption.view_all_history seeded in F-01 V8.
-- ============================================================
INSERT INTO permissions (
    id, permission_key, display_name, description,
    category, permission_type, sort_order, created_at, updated_at, scope
)
VALUES
  (gen_random_uuid(),
   'action.redemption.catalog.manage',
   'Manage Redemption Catalog',
   'Create, edit, activate, and deactivate global redemption catalog items; trigger Xoxoday catalog sync',
   'REDEMPTION_ACTIONS', 'ACTION', 810, NOW(), NOW(), 'PLATFORM'),
  (gen_random_uuid(),
   'action.redemption.configure',
   'Configure Tenant Catalog',
   'Enable/disable catalog items for tenant, override processing modes and thresholds, configure regional availability and batch cadence',
   'REDEMPTION_ACTIONS', 'ACTION', 811, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- action.redemption.catalog.manage is PLATFORM scope — no client_role_permissions row.
-- Granted only via TENX_ADMIN platform-level check.

-- action.redemption.configure → CLIENT_ADMIN only
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN (
    'action.redemption.configure'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;
```

### V18__add_image_url_to_catalog_items.sql

```sql
ALTER TABLE redemption_catalog_items ADD COLUMN image_url VARCHAR(2000) NULL;
```

---

## Package Layout [BE]

_All paths relative to `../tenxengage-backend/`._

```
src/
├── main/
│   ├── java/com/tenxengage/app/
│   │   ├── entity/
│   │   │   ├── RedemptionCatalogItem.java              (extends BaseEntity — NO TenantAware; includes imageUrl String nullable — added V18)
│   │   │   ├── TenantRedemptionSettings.java            (extends BaseEntity, implements TenantAware)
│   │   │   ├── ClientCatalogItemConfig.java             (extends BaseEntity, implements TenantAware)
│   │   │   ├── ClientCatalogRegionConfig.java           (extends BaseEntity, implements TenantAware)
│   │   │   └── enums/
│   │   │       ├── RedemptionProcessingMode.java        (INSTANT, BATCH, APPROVAL_REQUIRED)
│   │   │       ├── RedemptionCategory.java              (CASH, NON_CASH)
│   │   │       └── BatchCadence.java                    (DAILY, WEEKLY)
│   │   ├── repository/
│   │   │   ├── RedemptionCatalogItemRepository.java
│   │   │   ├── TenantRedemptionSettingsRepository.java
│   │   │   ├── ClientCatalogItemConfigRepository.java
│   │   │   └── ClientCatalogRegionConfigRepository.java
│   │   ├── service/
│   │   │   ├── RedemptionCatalogAdminService.java
│   │   │   ├── TenantRedemptionCatalogService.java
│   │   │   ├── RedemptionCatalogBrowseService.java
│   │   │   └── XoxodaySyncJobService.java               (@Scheduled background job)
│   │   ├── controller/
│   │   │   ├── RedemptionCatalogAdminController.java    (/api/v1/admin/redemption-catalog)
│   │   │   ├── RedemptionConfigController.java          (/api/v1/redemption/settings, /catalog/config)
│   │   │   └── RedemptionCatalogController.java         (/api/v1/redemption/catalog)
│   │   └── dto/
│   │       ├── request/
│   │       │   ├── CreateRedemptionCatalogItemRequest.java
│   │       │   ├── UpdateRedemptionCatalogItemRequest.java
│   │       │   ├── UpsertClientCatalogItemConfigRequest.java
│   │       │   ├── UpdateTenantRedemptionSettingsRequest.java
│   │       │   └── UpsertRegionConfigRequest.java
│   │       └── response/
│   │           ├── RedemptionCatalogItemResponse.java
│   │           ├── RedemptionCatalogItemDetailResponse.java
│   │           ├── TenantCatalogItemResponse.java
│   │           ├── CatalogBrowseItemResponse.java
│   │           ├── TenantRedemptionSettingsResponse.java
│   │           ├── ClientCatalogItemConfigResponse.java
│   │           ├── ClientCatalogRegionConfigResponse.java
│   │           └── IntegrationHealthResponse.java
│   └── resources/
│       └── db/migration/
│           ├── V9__create_redemption_catalog_items_table.sql
│           ├── V10__create_tenant_redemption_settings_table.sql
│           ├── V11__create_catalog_config_tables.sql
│           ├── V12__seed_redemption_catalog_permissions.sql
│           └── V18__add_image_url_to_catalog_items.sql
└── test/
    └── java/com/tenxengage/app/
        ├── service/
        │   ├── RedemptionCatalogAdminServiceTest.java
        │   ├── TenantRedemptionCatalogServiceTest.java
        │   └── RedemptionCatalogBrowseServiceTest.java
        ├── controller/
        │   ├── RedemptionCatalogAdminControllerTest.java
        │   ├── RedemptionConfigControllerTest.java
        │   └── RedemptionCatalogControllerTest.java
        └── testdata/
            ├── RedemptionCatalogItemFixtures.java
            ├── TenantRedemptionSettingsFixtures.java
            ├── ClientCatalogItemConfigFixtures.java
            └── ClientCatalogRegionConfigFixtures.java
```

---

## Repository Queries [BE]

### RedemptionCatalogItemRepository (no tenant filter — global entity)

- `findAllByOrderByNameAsc(pageable)` — admin list, all items
- `findAllByCategoryAndIsActive(category, isActive, pageable)` — admin filtered list
- `findById(id)` — detail (standard JPA)
- `findByCurrencyIdInAndIsActive(currencyIds, isActive, pageable)` — browse pre-filter
- `findByProviderItemId(providerItemId)` — idempotency check during sync
- `findAllByIsActive(isActive)` — sync job: load all active NON_CASH items for comparison
- `searchByName(@Query: LOWER(e.name) LIKE :q)` — admin search

### TenantRedemptionSettingsRepository

- `findByClientId(clientId)` — returns `Optional<TenantRedemptionSettings>`
- `findByClientIdWithLock(clientId)` — `@Lock(LockModeType.PESSIMISTIC_WRITE)` — find-or-create safety

### ClientCatalogItemConfigRepository

- `findByClientIdAndRedemptionCatalogItemId(clientId, catalogItemId)` — single item config
- `findByClientIdOrderByRedemptionCatalogItemId(clientId, pageable)` — tenant config list
- `findByClientIdAndEnabled(clientId, enabled, pageable)` — filtered list
- `existsByClientIdAndRedemptionCatalogItemId(clientId, catalogItemId)` — pre-insert check
- `findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(clientId, true, itemIds)` — browse batch lookup

### ClientCatalogRegionConfigRepository

- `findByClientIdAndRedemptionCatalogItemId(clientId, catalogItemId)` — all regional overrides for item
- `findByClientIdAndRedemptionCatalogItemIdAndRegionCode(clientId, catalogItemId, regionCode)` — single regional lookup
- `deleteByClientIdAndRedemptionCatalogItemIdAndRegionCode(clientId, catalogItemId, regionCode)` — remove override
- `existsByRedemptionCatalogItemIdAndRegionCode(catalogItemId, regionCode)` — scope narrowing check (cross-tenant — used by admin service)

---

## Service Layer Additions [BE]

### `RedemptionCatalogAdminService`

#### `uploadCatalogItemImage(UUID id, MultipartFile file)`
- Validates size ≤ 5 MB and MIME type (png/jpeg/webp)
- Deletes old image from storage if `imageUrl` is set
- Generates key: `catalog/{id}/image-{uuid}.{ext}`
- Calls `FileStorageService.upload(key, stream, size, contentType)`
- Stores returned object key in `imageUrl`, saves entity

---

## Package Layout [FE]

_All paths relative to `../tenxengage-frontend/src/`._

```
src/
├── types/
│   └── redemption-catalog.types.ts
├── services/
│   ├── redemption-catalog.service.ts        (partner browse + client admin config)
│   └── redemption-catalog-admin.service.ts  (platform admin global catalog)
├── hooks/
│   └── useRedemptionCatalog.ts
├── components/
│   └── redemption-catalog/
│       ├── CatalogBrowseGrid.tsx
│       ├── CatalogItemCard.tsx
│       ├── ShortfallBadge.tsx
│       ├── CatalogItemDetailSheet.tsx
│       ├── TenantCatalogConfigTable.tsx
│       ├── ItemConfigPanel.tsx
│       ├── RegionalConfigMatrix.tsx
│       ├── GlobalCatalogItemForm.tsx
│       ├── SyncStatusBanner.tsx
│       └── __tests__/
│           ├── CatalogItemCard.test.tsx
│           ├── ShortfallBadge.test.tsx
│           └── TenantCatalogConfigTable.test.tsx
└── pages/
    ├── RedemptionStorePage.tsx
    ├── CatalogConfigPage.tsx
    └── GlobalCatalogAdminPage.tsx
```

Route additions to `App.tsx` (follow existing nesting pattern — `ProtectedRoute` wraps `AppLayout` wraps `Route`):
```tsx
<Route element={<ProtectedRoute permission="module.redemption_store" />}>
  <Route element={<AppLayout />}>
    <Route path="/redemption-store" element={<RedemptionStorePage />} />
  </Route>
</Route>
<Route element={<ProtectedRoute permission="action.redemption.configure" />}>
  <Route element={<AppLayout />}>
    <Route path="/settings/redemption/catalog" element={<CatalogConfigPage />} />
  </Route>
</Route>
<Route element={<ProtectedRoute permission="action.redemption.catalog.manage" />}>
  <Route element={<AppLayout />}>
    <Route path="/admin/redemption-catalog" element={<GlobalCatalogAdminPage />} />
  </Route>
</Route>
```

---

## Hook Specs [FE]

### `usePartnerCatalog(filters)`
```ts
queryKey: ['redemption-catalog', 'browse', { currencyId, region, page, pageSize }]
staleTime: 2 * 60 * 1000
```
Invalidate on: redemption submission mutations (F-03).

### `usePartnerCatalogItem(id)`
```ts
queryKey: ['redemption-catalog', 'item', id]
staleTime: 5 * 60 * 1000
```

### `useTenantCatalogConfig(filters)`
```ts
queryKey: ['redemption-catalog', 'config', { enabled, category, search, page, pageSize }]
staleTime: 5 * 60 * 1000
```
Invalidate on: `upsertItemConfig` mutation.

### `useCatalogItemConfig(catalogItemId)` (used by ItemConfigPanel)
```ts
queryKey: ['redemption-catalog', 'config', catalogItemId]
staleTime: 5 * 60 * 1000
```
Invalidate on: `upsertItemConfig` mutation.

### `useRegionalConfig(catalogItemId)`
```ts
queryKey: ['redemption-catalog', 'regions', catalogItemId]
staleTime: 5 * 60 * 1000
```
Invalidate on: `upsertRegionConfig`, `deleteRegionConfig` mutations.

### `useTenantRedemptionSettings()`
```ts
queryKey: ['redemption-settings']
staleTime: 10 * 60 * 1000
```
Invalidate on: `updateTenantSettings` mutation.

### `useGlobalCatalogItems(filters)` (Platform Admin)
```ts
queryKey: ['global-catalog', { category, isActive, search, page, pageSize }]
staleTime: 2 * 60 * 1000
```
Invalidate on: create, update, activate, deactivate, sync mutations.

### `useIntegrationHealth()`
```ts
queryKey: ['redemption-integration-health']
staleTime: 60 * 1000
```
Invalidate on: sync trigger mutation.
