---
id: US-05
title: "Xoxoday sync + integration health"
layers: ["BE", "FE"]
seed_id: "S-05"
touches_entities: ["RedemptionCatalogItem"]
depends_on_stories: ["US-01"]
---

# US-05: Xoxoday sync + integration health

## Description

**Actor:** TENX_ADMIN (Platform Admin)
**Trigger:** Platform Admin clicks "Trigger Sync" in the `SyncStatusBanner` on `GlobalCatalogAdminPage`, OR the `@Scheduled` job fires automatically.

**Steps:**
1. Admin clicks "Trigger Sync" → `POST /api/v1/admin/redemption-catalog/sync` → `RedemptionCatalogAdminService.triggerXoxodaySync()` submits async task to `XoxodaySyncJobService`; returns `{ jobId, status: 'QUEUED' }` with HTTP 202
2. `XoxodaySyncJobService` runs async:
   a. Calls Xoxoday catalog API; logs `step=xoxoday_sync_started`
   b. Compares returned items against all active NON_CASH `RedemptionCatalogItem` records (`findAllByIsActive(true)` filtered to NON_CASH)
   c. Items absent from Xoxoday response → sets `isActive=false`; logs `step=xoxoday_item_auto_deactivated`; updates `xoxodayLastSyncedAt`
   d. `ClientCatalogItemConfig` rows preserved — not touched
   e. On completion: logs `step=xoxoday_sync_completed`
3. On Xoxoday API failure: retries 3 times with exponential backoff; on exhaustion logs `step=xoxoday_sync_failed` + routes to DLQ; existing items NOT auto-deactivated on transient failure
4. Admin views integration health → `GET /api/v1/admin/redemption-catalog/integration-health` → `IntegrationHealthResponse` with `lastSyncAt`, `syncStatus`, `failedSyncCount`, last 10 webhook entries
5. `SyncStatusBanner` polls `useIntegrationHealth()` (staleTime 1 min) and shows last sync status + timestamp

**Expected outcome:** Items removed from Xoxoday's catalog are auto-deactivated; partner browse immediately excludes them; `ClientCatalogItemConfig` rows preserved for re-activation; admin sees last sync status in banner.

**Negative paths:**
- Xoxoday API timeout → 3 retries + DLQ; existing items NOT deactivated
- POST /sync called more than 2×/min → 429
- Non-TENX_ADMIN caller → 403

---

## Acceptance Criteria

- **AC-1:** `POST /api/v1/admin/redemption-catalog/sync` returns 202 with `{ jobId, status }` and writes audit record; `XoxodaySyncJobService` async job sets `isActive=false` on items absent from Xoxoday response; existing `ClientCatalogItemConfig` rows preserved unchanged (FR-02.3)
- **AC-2:** Xoxoday API timeout → job retries 3 times with exponential backoff; after exhaustion routes to DLQ; items present before sync NOT auto-deactivated on transient failure (edge case #12)
- **AC-3:** `GET /api/v1/admin/redemption-catalog/integration-health` returns `IntegrationHealthResponse` with `lastSyncAt`, `syncStatus`, `failedSyncCount`, and last 10 webhook log entries (FR-02.2)
- **AC-4:** `POST /api/v1/admin/redemption-catalog/sync` rate-limited at 2 req/min per platform admin → 429 on breach

---

## Out of Scope

- XTRM webhook handler (F-03)
- Kafka events from sync (Phase 2)
- Vendor credential management UI — credentials are environment variables
- Xoxoday webhook delivery log persistence — log entries sourced from existing webhook log infrastructure; this story reads them only
- Non-NON_CASH item sync — CASH items are XTRM-managed, not Xoxoday-synced

---

## Non-Functional Notes

- `XoxodaySyncJobService` uses Spring `@Scheduled` + `@Async` annotations; tests mock the Xoxoday API client — do not call the real vendor in tests.
- Retry with exponential backoff: `@Retryable(maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=2))`; DLQ on exhaustion via `@Recover`.
- Log `step` values per `spec.md → ## Observability → Key Log Events`: `xoxoday_sync_started`, `xoxoday_sync_completed`, `xoxoday_sync_failed`, `xoxoday_item_auto_deactivated`.

---

## UI States

- [ ] **Banner loading:** Spinner in `SyncStatusBanner` while `useIntegrationHealth()` is in flight
- [ ] **Last sync status:** Shows `lastSyncAt` (formatted), `syncStatus` (SUCCESS/FAILED/IN_PROGRESS), `failedSyncCount`
- [ ] **Trigger Sync button loading:** Button shows spinner during `POST /sync`; disabled to prevent double-submit
- [ ] **Rate limit toast:** 429 response → toast "Sync rate limit reached. Wait 1 minute before triggering again."
- [ ] **Sync success toast:** On 202 → "Sync job queued (Job ID: {jobId})"

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 (catalog items must exist before sync can deactivate them)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-02.2, FR-02.3
- `spec.md → ## Data Model / Entities [BE]` — `RedemptionCatalogItem.xoxodayLastSyncedAt`, `syncMetadata`
- `spec.md → ## API Endpoints [BE + FE] → Platform Admin` — `POST /sync`, `GET /integration-health`
- `spec.md → ## DTOs [BE]` — `IntegrationHealthResponse`
- `spec.md → ## Service Layer [BE]` — `RedemptionCatalogAdminService.triggerXoxodaySync()`, `getIntegrationHealth()`; `XoxodaySyncJobService`
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.catalog.manage` (TENX_ADMIN only)
- `spec.md → ## Security Design [BE]` — rate limit 2 req/min for POST /sync
- `spec.md → ## Audit Trail [BE]` — `SYNCED` audit record on sync trigger; `@Audited(action=SYNCED)`
- `spec.md → ## Observability` — log events for sync lifecycle; `redemption.catalog.sync.duration_ms` histogram metric
- `spec.md → ## Edge Cases` — edge case #3 (ClientCatalogItemConfig preserved on deactivation), #12 (transient failure guard)
- `technical.md → ## Package Layout [BE]` — `XoxodaySyncJobService.java` path + all file paths
- `technical.md → ## Repository Queries [BE]` — `findAllByIsActive`, `findByProviderItemId`

---

## BE tasks [BE]

### BE-1: IntegrationHealthResponse DTO

**File:**
- `src/main/java/com/tenxengage/app/dto/response/IntegrationHealthResponse.java` — record; fields: `syncStatus` (String: SUCCESS/FAILED/IN_PROGRESS/NEVER_SYNCED), `lastSyncAt` (OffsetDateTime, nullable), `failedSyncCount` (int), `recentWebhooks` (List of webhook log entry summary — max 10 entries); static factory `from(...)` _(AC-3)_

### BE-2: XoxodaySyncJobService + unit tests

**Files:**
- `src/main/java/com/tenxengage/app/service/XoxodaySyncJobService.java` — NEW; `@Component`; methods:
  - `submitSyncJob()`: `@Async`; called by `triggerXoxodaySync()`; returns `CompletableFuture<SyncResult>`
  - `runSync()`: `@Transactional`; `@Retryable(maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=2))`; calls Xoxoday API client; loads all active NON_CASH items via `findAllByIsActive(true)`; compares by `providerItemId`; deactivates absent items; updates `xoxodayLastSyncedAt`; logs sync events
  - `handleSyncFailure()`: `@Recover`; logs `step=xoxoday_sync_failed`; routes to DLQ; does NOT deactivate items _(AC-2)_
- `src/test/java/com/tenxengage/app/service/XoxodaySyncJobServiceTest.java` — NEW; test cases (mock Xoxoday API client):
  - `runSync_deactivatesItemsAbsentFromXoxodayResponse` _(AC-1)_
  - `runSync_preservesClientCatalogItemConfigOnDeactivation` _(AC-1)_
  - `runSync_updatesXoxodayLastSyncedAt` _(AC-1)_
  - `handleSyncFailure_doesNotDeactivateItems` _(AC-2)_

### BE-3: RedemptionCatalogAdminService — sync + health methods + unit tests

**File:** `src/main/java/com/tenxengage/app/service/RedemptionCatalogAdminService.java` — MODIFIED; add:
- `triggerXoxodaySync()`: generates `jobId` (UUID), submits to `XoxodaySyncJobService.submitSyncJob()`; returns `SyncJobResponse { jobId, status: 'QUEUED' }` _(AC-1)_
- `getIntegrationHealth()`: reads `xoxodayLastSyncedAt` + `syncStatus` from items; reads last 10 webhook log entries; returns `IntegrationHealthResponse` _(AC-3)_

**File:** `src/test/java/com/tenxengage/app/service/RedemptionCatalogAdminServiceTest.java` — MODIFIED; add:
- `triggerXoxodaySync_returns202WithJobId` _(AC-1)_
- `getIntegrationHealth_returnsLastSyncStatus` _(AC-3)_

### BE-4: RedemptionCatalogAdminController — sync + health endpoints + @WebMvcTest

**File:** `src/main/java/com/tenxengage/app/controller/RedemptionCatalogAdminController.java` — MODIFIED; add:
- `POST /api/v1/admin/redemption-catalog/sync` → `triggerXoxodaySync()` → 202; `@Audited(action=SYNCED, resourceType=REDEMPTION_CATALOG_ITEM)` _(AC-1)_
- `GET /api/v1/admin/redemption-catalog/integration-health` → `getIntegrationHealth()` → 200 _(AC-3)_

**File:** `src/test/java/com/tenxengage/app/controller/RedemptionCatalogAdminControllerTest.java` — MODIFIED; add:
- `triggerSync_returns202_withJobId` _(AC-1)_
- `triggerSync_returns403_forNonTenxAdmin` _(AC-1, permission)_
- `triggerSync_returns429_whenRateLimitExceeded` _(AC-4)_
- `getIntegrationHealth_returns200_withHealthData` _(AC-3)_

---

## FE tasks [FE]

### FE-1: TypeScript types + service calls

**Files:**
- `src/types/redemption-catalog.types.ts` — MODIFIED; add `IntegrationHealthResponse`, `SyncJobResponse` interfaces from `../tenxengage-contracts/`
- `src/services/redemption-catalog-admin.service.ts` — MODIFIED; add:
  - `triggerCatalogSync(): Promise<SyncJobResponse>` → `POST /api/v1/admin/redemption-catalog/sync`
  - `getIntegrationHealth(): Promise<IntegrationHealthResponse>` → `GET /api/v1/admin/redemption-catalog/integration-health`

### FE-2: TanStack Query hooks

**File:** `src/hooks/useRedemptionCatalog.ts` — MODIFIED; add:
- `useIntegrationHealth()`: queryKey `['redemption-integration-health']`, staleTime `60 * 1000`; see `technical.md → ## Hook Specs [FE]`
- Mutation: `useTriggerCatalogSync` — invalidates `['redemption-integration-health']` + `['global-catalog']` on success _(AC-1, AC-3)_

### FE-3: SyncStatusBanner component + Vitest test

**Files:**
- `src/components/redemption-catalog/SyncStatusBanner.tsx` — NEW; uses `useIntegrationHealth()` + `useTriggerCatalogSync()`; renders: last sync timestamp (formatted), `syncStatus` badge (color-coded), `failedSyncCount`; "Trigger Sync" button → `useTriggerCatalogSync()` mutation; button loading state during POST; 429 toast; 202 success toast with jobId _(AC-1, AC-3, AC-4)_
- `src/components/redemption-catalog/__tests__/SyncStatusBanner.test.tsx` — NEW; Vitest cases:
  - `renders lastSyncAt and syncStatus` _(AC-3)_
  - `trigger sync button calls mutation` _(AC-1)_
  - `shows loading state during mutation` _(AC-1)_
  - `shows 429 toast on rate limit` _(AC-4)_

### FE-4: Wire into GlobalCatalogAdminPage

**File:** `src/pages/GlobalCatalogAdminPage.tsx` — MODIFIED; render `SyncStatusBanner` at the top of the page, above the catalog items table _(AC-1, AC-3)_

---

## E2E test [FE]

**Scenario 1:** `'Platform Admin triggers sync and sees updated health status'` _(covers AC-1, AC-3)_

**File:** `e2e/redemption-catalog-admin.spec.ts` (appended to existing file from US-01)

| Field | Value |
|---|---|
| **User flow** | Log in as TENX_ADMIN → navigate to `/admin/redemption-catalog` → see `SyncStatusBanner` → click "Trigger Sync" → button shows loading → success toast "Sync job queued" → `GET /integration-health` shows updated `lastSyncAt` |
| **APIs to mock via `page.route()`** | `GET /api/v1/admin/redemption-catalog/integration-health` → initial `{ syncStatus: 'SUCCESS', lastSyncAt: '...' }`; `POST /api/v1/admin/redemption-catalog/sync` → 202 `{ jobId: 'test-job-id', status: 'QUEUED' }`; second GET health → updated |
| **Visible assertion** | `expect(page.getByText('Sync job queued')).toBeVisible()`; integration health banner shows updated timestamp |

---

**Scenario 2:** `'Sync trigger rate limit returns 429 toast'` _(covers AC-4)_

**File:** `e2e/redemption-catalog-admin.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as TENX_ADMIN → navigate to `/admin/redemption-catalog` → click "Trigger Sync" → 429 response |
| **APIs to mock via `page.route()`** | `POST /api/v1/admin/redemption-catalog/sync` → 429 |
| **Visible assertion** | `expect(page.getByText('Sync rate limit reached')).toBeVisible()` |

---

## Execution checklist

**BE session:**
- [ ] `IntegrationHealthResponse.java` DTO created with `syncStatus`, `lastSyncAt`, `failedSyncCount`, `recentWebhooks` _(AC-3)_
- [ ] `XoxodaySyncJobService.runSync()` implemented — deactivates absent items, preserves ClientCatalogItemConfig, updates xoxodayLastSyncedAt _(AC-1)_
- [ ] `XoxodaySyncJobService.handleSyncFailure()` implemented — no deactivation on transient failure _(AC-2)_
- [ ] `XoxodaySyncJobServiceTest` unit tests pass _(AC-1, AC-2)_
- [ ] `RedemptionCatalogAdminService.triggerXoxodaySync()` implemented _(AC-1)_
- [ ] `RedemptionCatalogAdminService.getIntegrationHealth()` implemented _(AC-3)_
- [ ] `RedemptionCatalogAdminServiceTest` sync + health cases pass _(AC-1, AC-3)_
- [ ] `POST /sync` + `GET /integration-health` endpoints added to `RedemptionCatalogAdminController` _(AC-1, AC-3)_
- [ ] `@Audited(action=SYNCED)` on `POST /sync` _(AC-1)_
- [ ] `RedemptionCatalogAdminControllerTest` sync + health + rate-limit cases pass _(AC-1, AC-3, AC-4)_

**FE session:**
- [ ] `IntegrationHealthResponse` + `SyncJobResponse` types added from contracts _(AC-1, AC-3)_
- [ ] `triggerCatalogSync()` + `getIntegrationHealth()` added to `redemption-catalog-admin.service.ts` _(AC-1, AC-3)_
- [ ] `useIntegrationHealth` hook created with correct queryKey + staleTime _(AC-3)_
- [ ] `useTriggerCatalogSync` mutation with cache invalidation _(AC-1)_
- [ ] `SyncStatusBanner.tsx` created — health display, trigger button, loading/rate-limit states _(AC-1, AC-3, AC-4)_
- [ ] `SyncStatusBanner.test.tsx` Vitest tests pass _(AC-1, AC-3, AC-4)_
- [ ] `SyncStatusBanner` wired into `GlobalCatalogAdminPage` _(AC-1)_
- [ ] E2E: `'Platform Admin triggers sync and sees updated health status'` passes _(AC-1, AC-3)_
- [ ] E2E: `'Sync trigger rate limit returns 429 toast'` passes _(AC-4)_

---

## Done when

1. `./gradlew test` passes — all `XoxodaySyncJobServiceTest` + updated `RedemptionCatalogAdminServiceTest` + `RedemptionCatalogAdminControllerTest` cases green
2. `npm run test` passes — `SyncStatusBanner.test.tsx` Vitest cases green
3. `npx playwright test e2e/redemption-catalog-admin.spec.ts` passes against real BE (sync scenarios)
4. Every AC above is referenced by at least one passing test
