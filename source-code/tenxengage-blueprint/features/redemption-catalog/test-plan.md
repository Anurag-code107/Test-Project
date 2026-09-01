# Test Plan — redemption-catalog

_Cross-story integration tests. These scenarios require multiple stories to be `done` before they can run. Unit tests, @WebMvcTest tests, Vitest tests, and per-story E2E tests live in their respective `stories/US-NN-*.md` files. This file covers only multi-story and full-lifecycle scenarios._

---

## Scope

| Test Class | Requires | Location |
|---|---|---|
| `RedemptionCatalogIntegrationTest` | US-01, US-02, US-04 done | `src/test/java/com/tenxengage/app/integration/` |
| `CatalogTenantIsolationTest` | US-01, US-02 done | `src/test/java/com/tenxengage/app/integration/` |
| `XoxodaySyncIntegrationTest` | US-01, US-05 done | `src/test/java/com/tenxengage/app/integration/` |
| `RedemptionCatalogContractConformanceTest` | All stories done | `src/test/java/com/tenxengage/app/integration/` |
| `CatalogE2EIntegrationTest` | All stories done | `e2e/redemption-catalog-integration.spec.ts` |

All Java tests use `@SpringBootTest` + Testcontainers PostgreSQL. No mocking of the DB layer.

---

## Scenarios

### RedemptionCatalogIntegrationTest

**IT-01 — Full activation flow: create → enable → browse**

_Requires: US-01, US-02, US-04_

1. TENX_ADMIN creates NON_CASH item with `providerItemId`, `isActive=true`
2. TENX_ADMIN activates item → `isActive=true` confirmed
3. CLIENT_ADMIN upserts `ClientCatalogItemConfig` with `enabled=true`
4. PARTNER_SELLER calls `GET /api/v1/redemption/catalog` → item is present in response

_Expected:_ Item appears in partner browse immediately after CLIENT_ADMIN enable.

---

**IT-02 — Deactivation propagation: isActive=false hides item from all partners**

_Requires: US-01, US-02, US-04_

1. Setup: globally active item + CLIENT_ADMIN enabled
2. PARTNER_SELLER calls browse → item present
3. TENX_ADMIN calls `PATCH /{id}/deactivate` → `isActive=false`
4. PARTNER_SELLER calls browse → item absent

_Expected:_ 0 items in browse response after deactivation; no 5xx.

---

**IT-03 — Processing mode override: Client Admin BATCH → partner sees BATCH payout timeline**

_Requires: US-01, US-02, US-04_

1. Item created with `defaultProcessingMode=INSTANT`
2. CLIENT_ADMIN upserts config with `processingModeOverride=BATCH`; tenant `batchCadence=WEEKLY`
3. PARTNER_SELLER browses → item's `effectiveProcessingMode=BATCH`; `estimatedPayoutTimeline` reflects next weekly batch date

_Expected:_ `estimatedPayoutTimeline` contains "weekly" text; not "INSTANT" SLA.

---

**IT-04 — Shortfall indicator: availableBalance below effectiveMinWalletBalance**

_Requires: US-01, US-02, US-04_

1. CASH item with `defaultMinRedemptionAmount=100.00`; no config override (`effectiveMinWalletBalance=0` — wallet balance check is 0 by default)
2. CLIENT_ADMIN sets `minWalletBalanceOverride=100.00`
3. Partner has `RewardWallet.availableBalance=50.00`
4. PARTNER_SELLER browses → item present with `canAfford=false`, `shortfallAmount=50.00`

_Expected:_ `canAfford=false`; `shortfallAmount=50.00`; item still visible.

---

**IT-05 — Empty catalog: CLIENT_ADMIN enabled no items**

_Requires: US-01, US-04_

1. Platform has items but CLIENT_ADMIN has enabled none (`ClientCatalogItemConfig` absent or `enabled=false`)
2. PARTNER_SELLER calls `GET /api/v1/redemption/catalog` → empty paginated response

_Expected:_ `data: []`, `total: 0`; HTTP 200 (not 404).

---

### Regional integration — part of RedemptionCatalogIntegrationTest

**IT-06 — Regional restriction: US-only item invisible to GB partner**

_Requires: US-01, US-02, US-03, US-04_

1. Item with `geographicScope=['US','GB']` → CLIENT_ADMIN enables at tenant level
2. CLIENT_ADMIN adds `ClientCatalogRegionConfig` for `US` with `enabled=true`; `GB` has no regional row
3. PARTNER_SELLER with `region=GB` browses → no regional row for GB → falls back to `ClientCatalogItemConfig.enabled=true` → item is visible for GB (fallback rule FR-02.8)
4. CLIENT_ADMIN adds `ClientCatalogRegionConfig` for `GB` with `enabled=false`
5. PARTNER_SELLER with `region=GB` browses → `GB` row present and `enabled=false` → item absent

_Expected (step 3):_ GB partner sees item (fallback to tenant-level).
_Expected (step 5):_ GB partner does not see item after explicit disable.

---

**IT-07 — No regional override fallback: item accessible from any region**

_Requires: US-01, US-02, US-03, US-04_

1. Item with `geographicScope=['US','GB','IN']` → CLIENT_ADMIN enables; no `ClientCatalogRegionConfig` rows
2. PARTNER_SELLER with `region=IN` browses → item visible
3. PARTNER_SELLER with `region=GB` browses → item visible

_Expected:_ Item accessible from any region when no regional overrides exist.

---

**IT-08 — Geographic scope violation: CLIENT_ADMIN attempts region outside geographicScope**

_Requires: US-01, US-02, US-03_

1. Item with `geographicScope=['US']`
2. CLIENT_ADMIN calls `PUT /redemption/catalog/config/{id}/regions/DE` → 422 "Region DE is not supported by this catalog item's vendor"

_Expected:_ HTTP 422; no `ClientCatalogRegionConfig` row created.

---

### CatalogTenantIsolationTest

**IT-09 — Tenant isolation: Client A's config invisible to Client B**

_Requires: US-01, US-02_

1. Two tenants: ClientA and ClientB
2. ClientA upserts `ClientCatalogItemConfig` for itemX with `enabled=true`, `processingModeOverride=BATCH`
3. ClientB calls `GET /api/v1/redemption/catalog/config` → itemX not in list (or present with default `enabled=false`)
4. ClientB calls `GET /api/v1/redemption/catalog` as PARTNER → itemX absent (not enabled for tenant B)

_Expected:_ No cross-tenant data leakage; Hibernate `@Filter` enforced at query level.

---

**IT-10 — Tenant isolation: regional config scoped to tenant**

_Requires: US-01, US-02, US-03_

1. ClientA adds `ClientCatalogRegionConfig` for (itemX, `US`, `enabled=false`)
2. ClientB authenticates and calls `GET /api/v1/redemption/catalog/config/{itemX}/regions` → empty list (ClientA's row absent)

_Expected:_ ClientB sees no regional rows created by ClientA.

---

### XoxodaySyncIntegrationTest

**IT-11 — Xoxoday sync auto-deactivation: item absent from API response → isActive=false**

_Requires: US-01, US-05_

1. NON_CASH item `providerItemId='xox-123'` is active
2. CLIENT_ADMIN has `ClientCatalogItemConfig.enabled=true` for item
3. Mock Xoxoday API to return catalog WITHOUT `xox-123`
4. `XoxodaySyncJobService.runSync()` invoked
5. `RedemptionCatalogItem.isActive` → `false`; `ClientCatalogItemConfig.enabled` remains `true`
6. PARTNER_SELLER browses → item absent

_Expected:_ Item deactivated; client config preserved; browse excludes item.

---

**IT-12 — Xoxoday sync transient failure: items NOT deactivated**

_Requires: US-01, US-05_

1. NON_CASH item is active
2. Mock Xoxoday API to throw timeout on all 3 attempts
3. `XoxodaySyncJobService.handleSyncFailure()` invoked after retries exhausted
4. `RedemptionCatalogItem.isActive` still `true`

_Expected:_ Item NOT deactivated; failure only logged and routed to DLQ.

---

**IT-13 — batchCadence update: estimatedPayoutTimeline reflects new cadence**

_Requires: US-01, US-02, US-04_

1. CLIENT_ADMIN sets `batchCadence=DAILY`
2. CLIENT_ADMIN enables item with `processingModeOverride=BATCH`
3. PARTNER_SELLER browses → `estimatedPayoutTimeline` shows "daily" batch text
4. CLIENT_ADMIN changes `batchCadence=WEEKLY`
5. PARTNER_SELLER browses again → `estimatedPayoutTimeline` shows "weekly" batch text

_Expected:_ Timeline updates immediately after cadence change; no cache issue (staleTime on `useTenantRedemptionSettings` is 10 min — validate at service layer).

---

### RedemptionCatalogContractConformanceTest

**IT-14 — OpenAPI contract conformance: all 3 controllers**

_Requires: All stories done; contracts generated_

Validate every endpoint in the 3 controllers against `../tenxengage-contracts/endpoints/redemption-catalog.yaml` using the platform's OpenAPI validator pattern:

| Controller | Endpoints to validate |
|---|---|
| `RedemptionCatalogAdminController` | GET /admin/redemption-catalog, POST, GET /{id}, PUT /{id}, PATCH /{id}/activate, PATCH /{id}/deactivate, POST /sync, GET /integration-health |
| `RedemptionConfigController` | GET /settings, PUT /settings, GET /catalog/config, PUT /catalog/config/{id}, GET /catalog/config/{id}/regions, PUT regions/{code}, DELETE regions/{code} |
| `RedemptionCatalogController` | GET /catalog, GET /catalog/{id} |

_Expected:_ All response shapes match declared schemas; no undeclared fields; 4xx/5xx error shapes match platform error schema.

---

### CatalogE2EIntegrationTest (Playwright — full stack)

**IT-15 — Full-stack activation and partner browse flow**

_Requires: All stories done; FE deployed against real BE_

**File:** `e2e/redemption-catalog-integration.spec.ts`

1. TENX_ADMIN creates item and activates it (via `GlobalCatalogAdminPage`)
2. CLIENT_ADMIN enables item (via `CatalogConfigPage`)
3. PARTNER_SELLER navigates to `/redemption-store` → item card visible
4. PARTNER_SELLER clicks item → detail sheet opens; payout timeline shown

_Expected:_ End-to-end flow succeeds without mocking any API routes.

---

**IT-16 — Full-stack regional restriction flow**

_Requires: US-01, US-02, US-03, US-04 done_

**File:** `e2e/redemption-catalog-integration.spec.ts`

1. CLIENT_ADMIN adds regional config: `US` enabled, `GB` disabled
2. PARTNER_SELLER with `region=US` → item visible
3. PARTNER_SELLER with `region=GB` → item absent (explicit disable row)

_Expected:_ Regional restriction enforced end-to-end; no false positives.

---

## Running the test plan

```bash
# Java integration tests (run from tenxengage-backend/)
./gradlew test --tests "com.tenxengage.app.integration.*"

# E2E integration flow (run from tenxengage-frontend/)
npx playwright test e2e/redemption-catalog-integration.spec.ts

# Contract conformance
./gradlew test --tests "com.tenxengage.app.integration.RedemptionCatalogContractConformanceTest"
```

_The feature is **not ready to ship** until T1 row in `tracker.md` is marked `done` (all scenarios above passing)._
