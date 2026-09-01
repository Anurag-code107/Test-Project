> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V6__rename_reward_balances_to_reward_wallets.sql

```sql
-- Rename table
ALTER TABLE reward_balances RENAME TO reward_wallets;

-- Rename balance → available_balance
ALTER TABLE reward_wallets RENAME COLUMN balance TO available_balance;

-- Make user_id nullable (COMPANY wallets have no user)
ALTER TABLE reward_wallets ALTER COLUMN user_id DROP NOT NULL;

-- Add new columns
ALTER TABLE reward_wallets ADD COLUMN reserved_balance    DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE reward_wallets ADD COLUMN wallet_type         VARCHAR(20)   NOT NULL DEFAULT 'INDIVIDUAL';
ALTER TABLE reward_wallets ADD COLUMN partner_company_id  UUID          NULL REFERENCES partner_companies(id);
ALTER TABLE reward_wallets ADD COLUMN version             BIGINT        NOT NULL DEFAULT 0;

-- Check constraint: exactly one owner per wallet type
ALTER TABLE reward_wallets ADD CONSTRAINT chk_wallet_owner CHECK (
    (wallet_type = 'INDIVIDUAL' AND user_id IS NOT NULL AND partner_company_id IS NULL)
    OR
    (wallet_type = 'COMPANY' AND partner_company_id IS NOT NULL AND user_id IS NULL)
);

-- Partial unique indexes
CREATE UNIQUE INDEX uq_reward_wallets_individual
    ON reward_wallets(client_id, user_id, currency_id)
    WHERE wallet_type = 'INDIVIDUAL';

CREATE UNIQUE INDEX uq_reward_wallets_company
    ON reward_wallets(client_id, partner_company_id, currency_id)
    WHERE wallet_type = 'COMPANY';

-- Multi-currency lookup indexes
CREATE INDEX idx_reward_wallets_client_user    ON reward_wallets(client_id, user_id);
CREATE INDEX idx_reward_wallets_client_company ON reward_wallets(client_id, partner_company_id);
```

### V7__create_ledger_entries_table.sql

```sql
CREATE TABLE ledger_entries (
    id                        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                 UUID          NOT NULL REFERENCES clients(id),
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    reward_wallet_id          UUID          NOT NULL REFERENCES reward_wallets(id),
    entry_type                VARCHAR(30)   NOT NULL,
    amount                    DECIMAL(18,2) NOT NULL CHECK (amount > 0),
    currency_id               VARCHAR(50)   NOT NULL,
    reference_type            VARCHAR(50)   NULL,
    reference_id              UUID          NULL,
    note                      VARCHAR(500)  NULL,
    available_balance_before  DECIMAL(18,2) NOT NULL,
    available_balance_after   DECIMAL(18,2) NOT NULL,
    reserved_balance_before   DECIMAL(18,2) NOT NULL,
    reserved_balance_after    DECIMAL(18,2) NOT NULL
);

CREATE INDEX idx_ledger_entries_client_id      ON ledger_entries(client_id);
CREATE INDEX idx_ledger_entries_wallet_id      ON ledger_entries(reward_wallet_id);
CREATE INDEX idx_ledger_entries_wallet_created ON ledger_entries(reward_wallet_id, created_at DESC);
CREATE INDEX idx_ledger_entries_reference      ON ledger_entries(reference_type, reference_id)
    WHERE reference_id IS NOT NULL;

-- Idempotency: prevent double-crediting the same earning event
CREATE UNIQUE INDEX uq_ledger_credit_idempotency
    ON ledger_entries(reward_wallet_id, reference_type, reference_id)
    WHERE reference_id IS NOT NULL AND entry_type = 'CREDIT';
```

### V8__seed_redemption_store_permissions.sql

```sql
-- ============================================================
-- Redemption Store: Permission catalog (F-01 subset)
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(), 'module.redemption_store',            'Redemption Store',            'Access to Redemption Store module',                          'MODULE_ACCESS',      'MODULE', 400, NOW(), NOW(), 'ALL'),
  (gen_random_uuid(), 'action.redemption.view_history',     'View Redemption History',     'View own redemption transaction history and wallet balances', 'REDEMPTION_ACTIONS', 'ACTION', 401, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(), 'action.redemption.view_all_history', 'View All Redemption History', 'View all tenant redemption history and wallet balances',      'REDEMPTION_ACTIONS', 'ACTION', 402, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- Redemption Store: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (gen_random_uuid(), 'redemption_store', 'Enables Redemption Store — wallet, catalog, and redemption flow', true, true, true, NOW(), NOW(), 'REWARDS')
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- Redemption Store: Role grants
-- ============================================================

-- PARTNER_SELLER
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN (
    'module.redemption_store',
    'action.redemption.view_history'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- PARTNER_ADMIN
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN (
    'module.redemption_store',
    'action.redemption.view_history'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- CLIENT_ADMIN
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN (
    'module.redemption_store',
    'action.redemption.view_all_history'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- Acme tenant seed grants (dev/seed only)
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'module.redemption_store',
    'action.redemption.view_history',
    'action.redemption.view_all_history'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

---

## Package Layout [BE]

_All paths relative to `../tenxengage-backend/`._

```
src/
├── main/
│   ├── java/com/tenxengage/app/
│   │   ├── entity/
│   │   │   ├── RewardWallet.java                  (NEW — replaces RewardBalance; extends BaseEntity, implements TenantAware)
│   │   │   ├── RewardBalance.java                 (@Deprecated — keep compiling; remove in next cycle)
│   │   │   ├── LedgerEntry.java                   (NEW — extends BaseEntity, implements TenantAware)
│   │   │   └── enums/
│   │   │       ├── WalletType.java                (NEW — INDIVIDUAL, COMPANY)
│   │   │       └── LedgerEntryType.java           (NEW — CREDIT, RESERVE, DEBIT, RELEASE, RETURN_CREDIT)
│   │   ├── repository/
│   │   │   ├── RewardWalletRepository.java        (NEW)
│   │   │   ├── RewardBalanceRepository.java       (@Deprecated — delegates to RewardWalletRepository)
│   │   │   └── LedgerEntryRepository.java         (NEW)
│   │   ├── service/
│   │   │   ├── WalletService.java                 (NEW — single entry point for all balance mutations)
│   │   │   ├── RewardBalanceService.java          (@Deprecated — delegates to WalletService)
│   │   │   └── RewardGrantService.java            (MODIFIED — line 175: rewardBalanceService.credit() → walletService.credit())
│   │   ├── controller/
│   │   │   ├── WalletController.java              (NEW — /api/v1/wallets)
│   │   │   └── RewardBalanceController.java       (MODIFIED — delegates to WalletService; endpoints marked @Deprecated)
│   │   └── dto/
│   │       └── response/
│   │           ├── RewardWalletResponse.java      (NEW)
│   │           ├── RewardBalanceResponse.java     (MODIFIED — from() delegates to RewardWalletResponse shape)
│   │           └── LedgerEntryResponse.java       (NEW — stubbed; fully used in F-05)
│   └── resources/
│       └── db/migration/
│           ├── V6__rename_reward_balances_to_reward_wallets.sql
│           ├── V7__create_ledger_entries_table.sql
│           └── V8__seed_redemption_store_permissions.sql
└── test/
    └── java/com/tenxengage/app/
        ├── service/
        │   └── WalletServiceTest.java             (NEW)
        ├── controller/
        │   └── WalletControllerTest.java          (NEW)
        └── testdata/
            ├── RewardWalletFixtures.java          (NEW — builder-return pattern)
            └── LedgerEntryFixtures.java           (NEW)
```

---

## Repository Queries [BE]

### RewardWalletRepository

```java
// Own individual wallets for a user
List<RewardWallet> findByClientIdAndUserIdAndWalletType(UUID clientId, UUID userId, WalletType type);

// Find specific wallet by user + currency (for credit/reserve operations)
Optional<RewardWallet> findByClientIdAndUserIdAndCurrencyIdAndWalletType(
    UUID clientId, UUID userId, String currencyId, WalletType type);

// With pessimistic lock — used during auto-create to prevent duplicate wallet race
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM RewardWallet w WHERE w.clientId = :clientId AND w.userId = :userId " +
       "AND w.currencyId = :currencyId AND w.walletType = :walletType")
Optional<RewardWallet> findForUpdate(
    @Param("clientId") UUID clientId,
    @Param("userId") UUID userId,
    @Param("currencyId") String currencyId,
    @Param("walletType") WalletType walletType);

// Company wallets for a partner company
List<RewardWallet> findByClientIdAndPartnerCompanyIdAndWalletType(
    UUID clientId, UUID partnerCompanyId, WalletType type);

// Admin: all individual wallets for a user (any currency)
List<RewardWallet> findByClientIdAndUserId(UUID clientId, UUID userId);
```

### LedgerEntryRepository

```java
// Idempotency check before writing a CREDIT entry
boolean existsByRewardWalletIdAndReferenceTypeAndReferenceId(
    UUID rewardWalletId, String referenceType, UUID referenceId);

// Paginated history for a wallet (used by F-05)
Page<LedgerEntry> findByRewardWalletId(UUID rewardWalletId, Pageable pageable);

// Paginated history for entire tenant (CLIENT_ADMIN — used by F-05)
Page<LedgerEntry> findByClientId(UUID clientId, Pageable pageable);
```

---

## Package Layout [FE]

_All paths relative to `../tenxengage-frontend/src/`._

**Before implementing RewardBalanceWidget**, run `npx shadcn-ui add skeleton` — the `skeleton` component is not yet present in the project and is required for the nav widget loading state.

```
src/
├── types/
│   └── wallet.types.ts                            (NEW — copy from ../tenxengage-contracts/ after contracts generated)
├── services/
│   └── wallet.service.ts                          (NEW — replaces reward-balance.service.ts for wallet reads)
├── hooks/
│   └── useWalletApi.ts                            (NEW — TanStack Query hooks for wallet endpoints)
├── components/
│   ├── layout/
│   │   └── AppLayout.tsx                          (MODIFIED — add <RewardBalanceWidget /> to header)
│   └── rewards/
│       ├── RewardBalanceWidget.tsx                (NEW — persistent nav balance widget)
│       └── __tests__/
│           └── RewardBalanceWidget.test.tsx       (NEW)
```

**No new route entry needed** — `RewardBalanceWidget` is embedded in `AppLayout`, not a routed page.

---

## Hook Specs [FE]

### `useMyWallets()`

```ts
queryKey:  ['wallets', 'me']
queryFn:   walletService.getMyWallets        // GET /api/v1/wallets/me
staleTime: 2 * 60 * 1000                    // 2 min — fresher than default for nav widget accuracy
```

Invalidated by: redemption submit/cancel mutations (F-03).

### `useCompanyWallet(companyId)`

```ts
queryKey:  ['wallets', 'company', companyId]
queryFn:   () => walletService.getCompanyWallet(companyId)   // GET /api/v1/wallets/company/{companyId}
staleTime: 2 * 60 * 1000
enabled:   !!companyId
```

Invalidated by: company redemption mutations (F-03).

### `useUserWalletsAdmin(userId)`

```ts
queryKey:  ['wallets', 'user', userId]
queryFn:   () => walletService.getUserWallets(userId)        // GET /api/v1/wallets/users/{userId}
staleTime: 5 * 60 * 1000
enabled:   !!userId
```
