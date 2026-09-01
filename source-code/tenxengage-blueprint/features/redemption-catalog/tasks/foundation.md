# Foundation Tasks: redemption-catalog

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts redemption-catalog
> ```
> This enables FE story sessions to start immediately in parallel with BE foundation work.

---

## Task Summary

| # | Task | Layer | Deps | Parallel With | Size | Done When |
|---|---|---|---|---|---|---|
| F1 | Enums | BE | None | — | S | All enum classes compile; `AuditResourceType` includes 3 new values |
| F2 | Flyway migrations | BE | F1 | — | M | `./gradlew flywayMigrate` applies cleanly; 4 tables exist with correct columns and indexes |
| F3 | Base entities + repositories + fixtures | BE | F2 | — | M | Entity classes compile; all repo queries correctly scoped; `./gradlew test` passes |
| F4 | Permissions + feature flags seed | BE | F2 | — | S | V12 applies; 2 permission rows visible in DB |
| F5 | BE-only plumbing | — | — | — | N/A | **Omitted** — no Kafka events in F-02 per `spec.md → ## Out of Scope` |

---

## Task F1: Enums [BE] — Size: S

_Dependencies: None_
_Parallel with: None_
_Done when: All enum classes compile; `AuditResourceType` includes `REDEMPTION_CATALOG_ITEM`, `TENANT_CATALOG_CONFIG`, `TENANT_REDEMPTION_SETTINGS`_

**Files:**
- `src/main/java/com/tenxengage/app/entity/enums/RedemptionProcessingMode.java` — new enum: `INSTANT, BATCH, APPROVAL_REQUIRED`
- `src/main/java/com/tenxengage/app/entity/enums/RedemptionCategory.java` — new enum: `CASH, NON_CASH`
- `src/main/java/com/tenxengage/app/entity/enums/BatchCadence.java` — new enum: `DAILY, WEEKLY`
- `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` — add `REDEMPTION_CATALOG_ITEM`, `TENANT_CATALOG_CONFIG`, `TENANT_REDEMPTION_SETTINGS`

No `AuditAction` additions needed — platform uses existing `CREATED`, `UPDATED`, `ACTIVATED`, `DEACTIVATED`, `SYNCED`, `DELETED`.

Refer to `spec.md → ## New Enums [BE]` for values and intent.

---

## Task F2: Flyway Migrations [BE] — Size: M

_Dependencies: F1_
_Parallel with: None_
_Done when: `./gradlew flywayMigrate` applies cleanly; confirm all 4 tables and indexes exist via DB inspection_

**Files:**
- `src/main/resources/db/migration/V9__create_redemption_catalog_items_table.sql`
- `src/main/resources/db/migration/V10__create_tenant_redemption_settings_table.sql`
- `src/main/resources/db/migration/V11__create_catalog_config_tables.sql`

Refer to `technical.md → ## Flyway Migrations [BE]` for the exact SQL (verbatim — do not rewrite).

**V9 checklist:**
- [ ] `redemption_catalog_items` table created with all columns
- [ ] `chk_cash_not_returnable` check constraint added
- [ ] `idx_redemption_catalog_items_category` index created
- [ ] `idx_redemption_catalog_items_currency` index created
- [ ] `uq_redemption_catalog_items_provider` partial unique index created

**V10 checklist:**
- [ ] `tenant_redemption_settings` table created
- [ ] `uq_tenant_redemption_settings_client` unique constraint added
- [ ] `idx_tenant_redemption_settings_client_id` index created

**V11 checklist:**
- [ ] `client_catalog_item_configs` table created with all columns + version column
- [ ] `uq_client_catalog_item_config` unique constraint added
- [ ] `idx_client_catalog_item_configs_client_id` and `idx_client_catalog_item_configs_client_enabled` indexes created
- [ ] `client_catalog_region_configs` table created
- [ ] `uq_client_catalog_region_config` unique constraint added
- [ ] `idx_client_catalog_region_configs_client_item` and `idx_client_catalog_region_configs_client_region` indexes created

> **Note:** `redemption_catalog_items` does NOT have `client_id` — it is a platform-level entity (no tenant filter). All other tables have `client_id NOT NULL FK → clients`.

---

## Task F3: Base Entities + Repositories + Fixtures [BE] — Size: M

_Dependencies: F2_
_Parallel with: None_
_Done when: Entity classes compile; repository queries correctly scoped (global vs tenant); `./gradlew test` passes including new fixture usage_

**Files:**

_Entities:_
- `src/main/java/com/tenxengage/app/entity/RedemptionCatalogItem.java` — extends `BaseEntity`; does **NOT** implement `TenantAware`; no `@Filter`; fields: `name`, `description`, `category` (RedemptionCategory), `currencyId`, `defaultMinRedemptionAmount`, `defaultProcessingMode` (RedemptionProcessingMode), `geographicScope` (String[]), `providerItemId`, `isReturnable`, `defaultReturnWindowDays`, `isActive`, `xoxodayLastSyncedAt`, `syncMetadata` (JSONB as String)
- `src/main/java/com/tenxengage/app/entity/TenantRedemptionSettings.java` — extends `BaseEntity`, implements `TenantAware`; carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`; fields: `clientId`, `batchCadence` (BatchCadence)
- `src/main/java/com/tenxengage/app/entity/ClientCatalogItemConfig.java` — extends `BaseEntity`, implements `TenantAware`; carries `@Filter`; fields: `clientId`, `redemptionCatalogItemId` (UUID FK), `enabled`, `processingModeOverride`, `minTransactionAmountOverride`, `minWalletBalanceOverride`, `returnWindowDaysOverride`, `version` (`@Version`)
- `src/main/java/com/tenxengage/app/entity/ClientCatalogRegionConfig.java` — extends `BaseEntity`, implements `TenantAware`; carries `@Filter`; fields: `clientId`, `redemptionCatalogItemId` (UUID FK), `regionCode`, `enabled`

_Repositories:_
- `src/main/java/com/tenxengage/app/repository/RedemptionCatalogItemRepository.java` — all queries in `technical.md → ## Repository Queries [BE]`; no tenant filter on this repo
- `src/main/java/com/tenxengage/app/repository/TenantRedemptionSettingsRepository.java` — `findByClientId`, `findByClientIdWithLock` (`@Lock PESSIMISTIC_WRITE`)
- `src/main/java/com/tenxengage/app/repository/ClientCatalogItemConfigRepository.java` — all queries in `technical.md`
- `src/main/java/com/tenxengage/app/repository/ClientCatalogRegionConfigRepository.java` — all queries in `technical.md`

_Test fixtures (builder-return pattern — follow `UserFixtures.java`):_
- `src/test/java/com/tenxengage/app/testdata/RedemptionCatalogItemFixtures.java` — must support CASH and NON_CASH variants; active and inactive variants
- `src/test/java/com/tenxengage/app/testdata/TenantRedemptionSettingsFixtures.java` — DAILY and WEEKLY variants
- `src/test/java/com/tenxengage/app/testdata/ClientCatalogItemConfigFixtures.java` — enabled and disabled variants; with and without overrides
- `src/test/java/com/tenxengage/app/testdata/ClientCatalogRegionConfigFixtures.java` — enabled and disabled regional variants

Refer to `spec.md → ## Data Model / Entities [BE]` for field specs and `technical.md → ## Repository Queries [BE]` for all query signatures.

---

## Task F4: Permissions + Feature Flags Seed [BE] — Size: S

_Dependencies: F2_
_Parallel with: None_
_Done when: V12 applies without error; 2 permission rows visible in DB (`action.redemption.catalog.manage`, `action.redemption.configure`); `action.redemption.configure` has `client_role_permissions` row for `CLIENT_ADMIN`_

**Files:**
- `src/main/resources/db/migration/V12__seed_redemption_catalog_permissions.sql`

Refer to `technical.md → ## Flyway Migrations [BE] → V12__seed_redemption_catalog_permissions.sql` for the exact SQL (verbatim).

Key points:
- `action.redemption.catalog.manage` is `PLATFORM` scope — no `client_role_permissions` row (TENX_ADMIN only, checked at application layer)
- `action.redemption.configure` → `CLIENT_ADMIN` role grant only
- No new feature flag — `redemption_store` (seeded F-01 V8) gates this feature
- All inserts use `ON CONFLICT DO NOTHING` for idempotency
