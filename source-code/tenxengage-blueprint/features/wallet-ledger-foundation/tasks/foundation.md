# Foundation Tasks: wallet-ledger-foundation

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts wallet-ledger-foundation
> ```
> This enables FE story sessions (US-02) to start immediately after US-01 BE is done.

---

## Task Summary

| # | Task | Layer | Deps | Parallel With | Size | Done When |
|---|---|---|---|---|---|---|
| F1 | Enums | BE | None | — | S | All enum classes compile; `AuditResourceType` includes `REWARD_WALLET` |
| F2 | Flyway migrations | BE | F1 | — | M | `./gradlew flywayMigrate` applies cleanly; `reward_wallets` and `ledger_entries` tables exist with correct columns, constraints, and indexes |
| F3 | Base entities + repositories + fixtures | BE | F2 | — | M | Entity classes compile; all repo queries include `clientId`; `./gradlew test` passes including fixture usage |
| F4 | Permissions + feature flags seed | BE | F2 | — | S | V8 seed migration applies; 3 permission rows + 1 feature flag row visible in DB with correct tier booleans |
| F5 | BE-only plumbing | — | — | — | N/A | **Omitted** — Kafka domain events deferred to Phase 2 per `spec.md → ## Out of Scope` |

---

## Task F1: Enums [BE] — Size: S

_Dependencies: None_
_Parallel with: None_
_Done when: All enum classes compile; `AuditResourceType` includes `REWARD_WALLET`_

**Files:**
- `src/main/java/com/tenxengage/app/entity/enums/WalletType.java` — new enum: `INDIVIDUAL, COMPANY`
- `src/main/java/com/tenxengage/app/entity/enums/LedgerEntryType.java` — new enum: `CREDIT, RESERVE, DEBIT, RELEASE, RETURN_CREDIT`
- `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` — add `REWARD_WALLET`

Refer to `spec.md → ## New Enums [BE]` for values and intent.

No `AuditAction` additions needed — wallet creation uses existing `AuditAction.CREATED`.

---

## Task F2: Flyway Migrations [BE] — Size: M

_Dependencies: F1_
_Parallel with: None_
_Done when: `./gradlew flywayMigrate` applies cleanly; confirm `reward_wallets` and `ledger_entries` tables exist with correct columns, constraints, partial unique indexes, and regular indexes_

**Files:**
- `src/main/resources/db/migration/V6__rename_reward_balances_to_reward_wallets.sql`
- `src/main/resources/db/migration/V7__create_ledger_entries_table.sql`

Refer to `technical.md → ## Flyway Migrations [BE]` for the exact SQL (verbatim — do not rewrite).

**V6 checklist:**
- [ ] Table renamed `reward_balances` → `reward_wallets`
- [ ] Column renamed `balance` → `available_balance`
- [ ] `user_id` made nullable
- [ ] `reserved_balance`, `wallet_type`, `partner_company_id`, `version` columns added
- [ ] Check constraint `chk_wallet_owner` added
- [ ] Partial unique indexes `uq_reward_wallets_individual` and `uq_reward_wallets_company` created
- [ ] Lookup indexes `idx_reward_wallets_client_user` and `idx_reward_wallets_client_company` created
- [ ] Existing rows backfilled: `wallet_type = 'INDIVIDUAL'`, `reserved_balance = 0`, `version = 0`

**V7 checklist:**
- [ ] `ledger_entries` table created with all columns
- [ ] All 4 indexes created
- [ ] Idempotency unique index `uq_ledger_credit_idempotency` created

> **Prerequisites before running V6**: confirm `partner_companies` table name and PK column; confirm `reward_balances` has no other FK references that would break on rename (check migration history).

---

## Task F3: Base Entities + Repositories + Fixtures [BE] — Size: M

_Dependencies: F2_
_Parallel with: None_
_Done when: Entity classes compile; all repository queries scoped to `clientId`; `./gradlew test` passes including fixture usage_

**Files:**
- `src/main/java/com/tenxengage/app/entity/RewardWallet.java` — NEW; extends `BaseEntity`, implements `TenantAware`, carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`; fields: `walletType`, `userId`, `partnerCompanyId`, `currencyId`, `availableBalance`, `reservedBalance`, `version` (`@Version`)
- `src/main/java/com/tenxengage/app/entity/RewardBalance.java` — mark `@Deprecated` (keep compiling; remove in next cycle)
- `src/main/java/com/tenxengage/app/entity/LedgerEntry.java` — NEW; extends `BaseEntity`, implements `TenantAware`, carries `@Filter`; immutable — no update/delete operations
- `src/main/java/com/tenxengage/app/repository/RewardWalletRepository.java` — NEW; all queries in `technical.md → ## Repository Queries [BE]`
- `src/main/java/com/tenxengage/app/repository/RewardBalanceRepository.java` — mark `@Deprecated`; delegates to `RewardWalletRepository`
- `src/main/java/com/tenxengage/app/repository/LedgerEntryRepository.java` — NEW; queries in `technical.md`
- `src/test/java/com/tenxengage/app/testdata/RewardWalletFixtures.java` — NEW; builder-return pattern (follow `UserFixtures.java`); must support building INDIVIDUAL and COMPANY wallet variants
- `src/test/java/com/tenxengage/app/testdata/LedgerEntryFixtures.java` — NEW; builder-return pattern; supports all 5 `LedgerEntryType` values

Refer to `spec.md → ## Data Model / Entities [BE]` for field specs and `technical.md → ## Repository Queries [BE]` for all query signatures.

---

## Task F4: Permissions + Feature Flags Seed [BE] — Size: S

_Dependencies: F2_
_Parallel with: None_
_Done when: V8 seed migration applies without error; all 3 permission rows visible in DB; feature flag row exists with `starter_enabled=true`, `professional_enabled=true`, `enterprise_enabled=true`_

**Files:**
- `src/main/resources/db/migration/V8__seed_redemption_store_permissions.sql`

Refer to `technical.md → ## Flyway Migrations [BE] → V8__seed_redemption_store_permissions.sql` for the exact SQL (verbatim). The seed inserts:
- 3 permissions: `module.redemption_store`, `action.redemption.view_history`, `action.redemption.view_all_history`
- 1 feature flag: `redemption_store` (all tiers enabled)
- Role grants for `PARTNER_SELLER`, `PARTNER_ADMIN`, `CLIENT_ADMIN`
- Acme tenant seed grants (dev/seed only)

All inserts use `ON CONFLICT DO NOTHING` for idempotency.
