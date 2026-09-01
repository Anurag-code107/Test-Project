> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_ · _Latest existing: **V31** → this feature uses **V32** and **V33**._

### V32__create_balance_expiration_tables.sql

```sql
-- ============================================================
-- F-09 Balance Expiration: BalanceExpirationPolicy entity table
-- ============================================================
CREATE TABLE balance_expiration_policies (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id         UUID          NOT NULL REFERENCES clients(id),
    currency_id       VARCHAR(50)   NOT NULL,
    enabled           BOOLEAN       NOT NULL DEFAULT false,
    expiration_mode   VARCHAR(20)   NOT NULL,            -- ExpirationMode: INACTIVITY | FIXED_DATE
    inactivity_days   INTEGER       NULL,
    fixed_expiry_date DATE          NULL,
    lead_time_days    INTEGER       NOT NULL DEFAULT 30,
    enabled_at        TIMESTAMPTZ   NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN       NOT NULL DEFAULT false,
    version           BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_balance_expiration_policies_client_id
    ON balance_expiration_policies(client_id);
CREATE UNIQUE INDEX uq_balance_expiration_policies_client_currency
    ON balance_expiration_policies(client_id, currency_id);
CREATE INDEX idx_balance_expiration_policies_enabled
    ON balance_expiration_policies(enabled)
    WHERE enabled = true AND deleted = false;

-- ============================================================
-- F-09 Balance Expiration: BalanceExpiryNotice entity table
-- ============================================================
CREATE TABLE balance_expiry_notices (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id             UUID          NOT NULL REFERENCES clients(id),
    wallet_id             UUID          NOT NULL REFERENCES reward_wallets(id),
    currency_id           VARCHAR(50)   NOT NULL,
    policy_id             UUID          NOT NULL REFERENCES balance_expiration_policies(id),
    scheduled_expiry_date DATE          NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',  -- ExpiryNoticeStatus
    notified_at           TIMESTAMPTZ   NULL,
    notified_amount       NUMERIC(18,2) NULL,
    expired_at            TIMESTAMPTZ   NULL,
    expired_amount        NUMERIC(18,2) NULL,
    ledger_entry_id       UUID          NULL REFERENCES ledger_entries(id),
    cancelled_at          TIMESTAMPTZ   NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted               BOOLEAN       NOT NULL DEFAULT false,
    version               BIGINT        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_balance_expiry_notices_event
    ON balance_expiry_notices(wallet_id, currency_id, scheduled_expiry_date);   -- idempotency key (FR-09.8)
CREATE INDEX idx_balance_expiry_notices_status_date
    ON balance_expiry_notices(status, scheduled_expiry_date);
CREATE INDEX idx_balance_expiry_notices_client
    ON balance_expiry_notices(client_id);
CREATE INDEX idx_balance_expiry_notices_policy
    ON balance_expiry_notices(policy_id);

-- ============================================================
-- F-09: supporting index for ledger-derived last-activity lookup
-- (complements idx_ledger_entries_client_currency_type from V27)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_currency_created
    ON ledger_entries(client_id, reward_wallet_id, currency_id, created_at);  -- findLastActivityAt filters by reward_wallet_id
```

### V33__seed_balance_expiration_permissions.sql

```sql
-- ============================================================
-- F-09 Balance Expiration: Permission catalog
-- Note: module.redemption_store already seeded by F-01 (V8) — reused, not re-seeded.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(), 'action.redemption.expiration.configure',
   'Configure Balance Expiration',
   'Configure per-currency reward balance expiration policies (enable/disable, mode, lead time)',
   'REDEMPTION_ACTIONS', 'ACTION', 413, NOW(), NOW(), 'INTERNAL'),
  (gen_random_uuid(), 'action.redemption.expiration.view_breakage',
   'View Balance Expiration Breakage',
   'View and export the reward balance expiration (breakage) report by currency type and period',
   'REDEMPTION_ACTIONS', 'ACTION', 414, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- F-09: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (gen_random_uuid(), 'reward_balance_expiration',
   'Reward Balance Expiration — per-currency expiration policies (inactivity or fixed date), advance-expiry + expiry notifications, and breakage reporting/CSV export',
   false, true, true, NOW(), NOW(), 'REWARDS')
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- F-09: Role grants — CLIENT_ADMIN only (per Permission Matrix in spec.md)
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN (
    'action.redemption.expiration.configure',
    'action.redemption.expiration.view_breakage'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- F-09: Tenant-level grant — Acme seed tenant (dev/seed only).
-- REQUIRED: the 5-layer permission model intersects role permissions with
-- tenant grants; without this row the permission is stripped at Layer 0
-- (cf. V31 corrective for advanced analytics). Production tenant grants are
-- provisioned via the subscription/tier flow, mirroring V27.
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.expiration.configure',
    'action.redemption.expiration.view_breakage'
  )
ON CONFLICT (client_id, permission_key) DO NOTHING;

-- ============================================================
-- F-09: Notification types (REWARDS) — partner recipients
-- ============================================================
INSERT INTO notification_types (id, key, category, title, description, default_roles, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'BALANCE_EXPIRING_SOON',     'REWARDS', 'Reward Balance Expiring Soon',
   'A reward balance is scheduled to expire',              'PARTNER_SELLER,PARTNER_ADMIN', NOW(), NOW()),
  (gen_random_uuid(), 'BALANCE_EXPIRED',           'REWARDS', 'Reward Balance Expired',
   'A reward balance has expired',                         'PARTNER_SELLER,PARTNER_ADMIN', NOW(), NOW()),
  (gen_random_uuid(), 'BALANCE_EXPIRY_CANCELLED',  'REWARDS', 'Reward Balance Expiry Cancelled',
   'A scheduled reward balance expiry was cancelled',      'PARTNER_SELLER,PARTNER_ADMIN', NOW(), NOW())
ON CONFLICT (key) DO NOTHING;
```

**Java-only enum changes (no migration — stored as `varchar`):**
- `LedgerEntryType` — add `EXPIRY`.
- `AuditResourceType` — add `BALANCE_EXPIRATION_POLICY`, `BALANCE_EXPIRY_BREAKAGE_EXPORT`.
- New enums `ExpirationMode {INACTIVITY, FIXED_DATE}`, `ExpiryNoticeStatus {SCHEDULED, NOTIFIED, EXPIRED, CANCELLED}`.

**Migration safety:** both tables are new (no locks on existing tables); the `ledger_entries` index uses `IF NOT EXISTS` and `CREATE INDEX` (non-concurrent is acceptable in a Flyway migration on this volume).

---

## Package Layout [BE]

_All paths relative to `../tenxengage-backend/`._

| Responsibility | File Path |
|---|---|
| Entity — policy | `src/main/java/com/tenxengage/app/entity/BalanceExpirationPolicy.java` (extends BaseEntity, implements TenantAware) |
| Entity — notice | `src/main/java/com/tenxengage/app/entity/BalanceExpiryNotice.java` (extends BaseEntity, implements TenantAware) |
| Enum — mode | `src/main/java/com/tenxengage/app/entity/enums/ExpirationMode.java` |
| Enum — notice status | `src/main/java/com/tenxengage/app/entity/enums/ExpiryNoticeStatus.java` |
| Enum — ledger (edit) | `src/main/java/com/tenxengage/app/entity/enums/LedgerEntryType.java` (add `EXPIRY`) |
| Enum — audit resource (edit) | `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` (add 2 values) |
| Repository — policy | `src/main/java/com/tenxengage/app/repository/BalanceExpirationPolicyRepository.java` |
| Repository — notice | `src/main/java/com/tenxengage/app/repository/BalanceExpiryNoticeRepository.java` |
| Repository — scheduler (cross-tenant) | `src/main/java/com/tenxengage/app/repository/SchedulerBalanceExpirationRepository.java` |
| Service — policy/config | `src/main/java/com/tenxengage/app/service/redemption/BalanceExpirationPolicyService.java` |
| Service — breakage report | `src/main/java/com/tenxengage/app/service/redemption/BalanceBreakageReportService.java` |
| Service — expiry batch | `src/main/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchService.java` (`@Scheduled`, SYSTEM actor) |
| Controller | `src/main/java/com/tenxengage/app/controller/BalanceExpirationController.java` (`@RequestMapping("/api/v1/redemption/expiration")`) |
| Request DTO | `src/main/java/com/tenxengage/app/dto/request/UpsertBalanceExpirationPolicyRequest.java` |
| Response DTO — policy | `src/main/java/com/tenxengage/app/dto/response/BalanceExpirationPolicyResponse.java` |
| Response DTO — breakage | `src/main/java/com/tenxengage/app/dto/response/BalanceBreakageReportResponse.java` (+ nested `BreakageRowDto`) |
| Response DTO — preview | `src/main/java/com/tenxengage/app/dto/response/ExpiringBalancePreviewResponse.java` |
| Service test — policy | `src/test/java/com/tenxengage/app/service/redemption/BalanceExpirationPolicyServiceTest.java` |
| Service test — batch | `src/test/java/com/tenxengage/app/service/redemption/BalanceExpiryBatchServiceTest.java` |
| Controller test | `src/test/java/com/tenxengage/app/controller/BalanceExpirationControllerTest.java` |
| Fixtures — policy | `src/test/java/com/tenxengage/app/testdata/BalanceExpirationPolicyFixtures.java` (builder-return) |
| Fixtures — notice | `src/test/java/com/tenxengage/app/testdata/BalanceExpiryNoticeFixtures.java` (builder-return) |

**Controller methods (→ spec.md API Endpoints):**

| Method | Mapping | `@RequiresPermission` | `@Audited` |
|---|---|---|---|
| `getPolicies()` | `@GetMapping("/policies")` | `action.redemption.expiration.configure` | — |
| `upsertPolicy(@PathVariable String currencyId, @Valid @RequestBody UpsertBalanceExpirationPolicyRequest)` | `@PutMapping("/policies/{currencyId}")` | `action.redemption.expiration.configure` | `action="EDITED", resourceType="BALANCE_EXPIRATION_POLICY"` |
| `getExpiringSoon(@RequestParam Integer withinDays, @RequestParam(required=false) String currencyId)` | `@GetMapping("/expiring-soon")` | `action.redemption.expiration.configure` | — |
| `getBreakage(from, to, currencyId?, granularity)` | `@GetMapping("/breakage")` | `action.redemption.expiration.view_breakage` | — |
| `exportBreakageCsv(from, to, currencyId?, granularity)` | `@GetMapping("/breakage/export")` (produces `text/csv`) | `action.redemption.expiration.view_breakage` | `action="DATA_EXPORTED", resourceType="BALANCE_EXPIRY_BREAKAGE_EXPORT"` |

**Cross-cutting reuse / hardening (implementation notes):**
- CSV export rate limit: inject `security/AnalyticsExportRateLimiter` and call `tryAcquireWithRetryAfter(clientId)` in `exportBreakageCsv` (returns `RateLimitResult` → `429` + `Retry-After` on deny). Same per-tenant bucket policy as F-08 liability export.
- `escapeCsv`: currently `private` in `RedemptionAnalyticsService`. Promote it to a shared `com.tenxengage.app.util.CsvUtil.escapeCsv(String)` (or equivalent) and have both services call it — do NOT duplicate.
- `event/NotificationEventProducer.publish()`: harden to handle the `kafkaTemplate.send()` future with `.whenComplete(...)` (it currently only catches `JsonProcessingException`). Emit the 3 balance-expiry notifications from `TransactionSynchronizationManager.afterCommit`.

---

## Repository Queries [BE]

### BalanceExpirationPolicyRepository (tenant-`@Filter`-ed; request path)
- `List<BalanceExpirationPolicy> findByClientId(UUID clientId)`
- `Optional<BalanceExpirationPolicy> findByClientIdAndCurrencyId(UUID clientId, String currencyId)`

### BalanceExpiryNoticeRepository (tenant-`@Filter`-ed; request path + per-wallet batch ops)
- `Optional<BalanceExpiryNotice> findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(UUID clientId, UUID walletId, String currencyId, LocalDate scheduledExpiryDate)` — idempotency lookup (FR-09.8)
- `List<BalanceExpiryNotice> findByClientIdAndStatusAndScheduledExpiryDateLessThanEqual(UUID clientId, ExpiryNoticeStatus status, LocalDate date)` — due-to-expire
- `List<BalanceExpiryNotice> findByClientIdAndPolicyIdAndStatusIn(UUID clientId, UUID policyId, Collection<ExpiryNoticeStatus> statuses)` — cancel-on-relax (FR-09.10)

### SchedulerBalanceExpirationRepository (NOT `@Filter`-ed — cross-tenant batch only)
> Tenant-isolation deviation is **intentional and documented** (see spec.md → Security Design): this repository is the cross-tenant sweep entry point. The top-level enabled-policy scan has no `clientId` parameter by design; every per-wallet query below binds `clientId` explicitly.
- `List<BalanceExpirationPolicy> findAllByEnabledTrueAndDeletedFalse()` — cross-tenant enabled policies (sweep entry; no `clientId` by design)
- `@Query("SELECT w FROM RewardWallet w WHERE w.clientId = :clientId AND w.currencyId = :currencyId AND w.availableBalance > 0") List<RewardWallet> findExpiryCandidateWallets(@Param("clientId") UUID clientId, @Param("currencyId") String currencyId)`
- `@Query("SELECT MAX(e.createdAt) FROM LedgerEntry e WHERE e.clientId = :clientId AND e.rewardWalletId = :walletId AND e.currencyId = :currencyId AND e.entryType IN :activityTypes") Instant findLastActivityAt(@Param("clientId") UUID clientId, @Param("walletId") UUID walletId, @Param("currencyId") String currencyId, @Param("activityTypes") Collection<LedgerEntryType> activityTypes)` — activity types = `{CREDIT, DEBIT, RESERVE, RETURN_CREDIT}`
- `@Query(value = "SELECT * FROM reward_wallets w WHERE w.id = :walletId AND w.client_id = :clientId FOR UPDATE", nativeQuery = true) Optional<RewardWallet> lockWallet(@Param("walletId") UUID walletId, @Param("clientId") UUID clientId)` — row lock for atomic expiry (FR-09.11)

### LedgerEntryRepository (reused — F-01)
- `boolean existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(UUID rewardWalletId, String referenceType, UUID referenceId, LedgerEntryType entryType)` — ledger-layer idempotency for the EXPIRY debit (`referenceType="BALANCE_EXPIRY_NOTICE"`, `referenceId=notice.id`)
- **Add** breakage aggregation (native, period-bucketed):
  - `@Query(value = "SELECT date_trunc(:bucket, e.created_at)::date AS period_start, e.currency_id, COUNT(*) AS expired_count, COALESCE(SUM(e.amount),0) AS total_expired_amount FROM ledger_entries e WHERE e.client_id = :clientId AND e.entry_type = 'EXPIRY' AND e.created_at >= :from AND e.created_at < :to AND (:currencyId IS NULL OR e.currency_id = :currencyId) GROUP BY 1, e.currency_id ORDER BY 1, e.currency_id", nativeQuery = true) List<BreakageRowProjection> aggregateExpiryBreakage(@Param("clientId") UUID clientId, @Param("from") Instant from, @Param("to") Instant to, @Param("currencyId") String currencyId, @Param("bucket") String bucket)` — `bucket ∈ {'month','quarter'}`

_Projection interface: `BreakageRowProjection { LocalDate getPeriodStart(); String getCurrencyId(); long getExpiredCount(); BigDecimal getTotalExpiredAmount(); }`_

---

## Package Layout [FE]

_All paths relative to `../tenxengage-frontend/src/`._

| Responsibility | File Path |
|---|---|
| TypeScript types | `types/balanceExpiration.types.ts` (copy from contracts repo — do NOT hand-write) |
| API service | `services/balanceExpiration.service.ts` |
| Hook — policies | `hooks/useBalanceExpirationPolicies.ts` |
| Hook — expiring soon | `hooks/useExpiringSoon.ts` |
| Hook — breakage | `hooks/useBalanceBreakage.ts` |
| Hook — upsert mutation | `hooks/useUpsertBalanceExpirationPolicy.ts` |
| Page — settings | `pages/balanceExpiration/BalanceExpirationSettingsPage.tsx` |
| Page — breakage report | `pages/balanceExpiration/BalanceBreakageReportPage.tsx` |
| Component — policy form | `components/balanceExpiration/BalanceExpirationPolicyForm.tsx` |
| Component — expiring preview | `components/balanceExpiration/ExpiringSoonPreviewCard.tsx` |
| Component — breakage table | `components/balanceExpiration/BreakageReportTable.tsx` |
| Zod schema | `components/balanceExpiration/balanceExpirationPolicySchema.ts` |
| Component tests | `components/balanceExpiration/__tests__/BalanceExpirationPolicyForm.test.tsx`, `BreakageReportTable.test.tsx` |

**Route entries (`App.tsx`), each wrapped in `ProtectedRoute`:**
```tsx
<Route path="/settings/redemption/balance-expiration"
       element={<ProtectedRoute permission="action.redemption.expiration.configure"><BalanceExpirationSettingsPage /></ProtectedRoute>} />
<Route path="/redemption/breakage"
       element={<ProtectedRoute permission="action.redemption.expiration.view_breakage"><BalanceBreakageReportPage /></ProtectedRoute>} />
```

**Sidebar entries** (with `permissionKey`): "Balance Expiration" under Redemption Settings (`action.redemption.expiration.configure`); "Breakage" under Redemption (`action.redemption.expiration.view_breakage`).

---

## Hook Specs [FE]

### useBalanceExpirationPolicies (list hook)
```ts
queryKey: ['balance-expiration-policies', clientId]
staleTime: 5 * 60 * 1000   // 5 min
```
Invalidate on: `useUpsertBalanceExpirationPolicy` mutation.

### useExpiringSoon (preview hook)
```ts
queryKey: ['balance-expiring-soon', clientId, { withinDays, currencyId }]
staleTime: 1 * 60 * 1000   // 1 min
```
Invalidate on: `useUpsertBalanceExpirationPolicy` mutation.

### useBalanceBreakage (report hook)
```ts
queryKey: ['balance-breakage', clientId, { from, to, currencyId, granularity }]
staleTime: 5 * 60 * 1000   // 5 min
```
Invalidate on: manual (filter change). CSV export is a direct `GET /breakage/export` download (not a query hook).

### useUpsertBalanceExpirationPolicy (mutation)
```ts
mutationFn: (currencyId, body) => PUT /api/v1/redemption/expiration/policies/{currencyId}
onSuccess: invalidate ['balance-expiration-policies', clientId] and ['balance-expiring-soon', clientId]
422 errorCode → map to react-hook-form field errors
```

---

## Audit Annotations [BE]

| Operation | `action` value | `resourceType` value | `description` |
|---|---|---|---|
| `PUT /policies/{currencyId}` | `EDITED` | `BALANCE_EXPIRATION_POLICY` | "Configured balance expiration policy" |
| `GET /breakage/export` | `DATA_EXPORTED` | `BALANCE_EXPIRY_BREAKAGE_EXPORT` | "Exported balance expiration breakage report" |
| Expiry execution (batch, programmatic via `auditLogService.logAsync`) | `EXPIRED` | `REWARD_WALLET` | "Expired unused balance" (SYSTEM actor) |
| Cancel-on-relax (batch/service, programmatic) | `CANCELLED` | `BALANCE_EXPIRATION_POLICY` | "Cancelled pending balance expirations" |

**New enum values for Java files (no Flyway):**
- `AuditResourceType`: `BALANCE_EXPIRATION_POLICY`, `BALANCE_EXPIRY_BREAKAGE_EXPORT`.
- `AuditAction`: none (reuses `EDITED`, `EXPIRED`, `CANCELLED`, `DATA_EXPORTED`).
- `LedgerEntryType`: `EXPIRY`.
- New: `ExpirationMode {INACTIVITY, FIXED_DATE}`, `ExpiryNoticeStatus {SCHEDULED, NOTIFIED, EXPIRED, CANCELLED}`.
