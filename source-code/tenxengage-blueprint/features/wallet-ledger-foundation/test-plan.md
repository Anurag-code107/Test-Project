# Test Plan: wallet-ledger-foundation

_Cross-story integration tests for [spec.md](spec.md)._

_Per-story tests (unit tests, @WebMvcTest, Vitest, Playwright E2E) live inside each `stories/US-NN-*.md`. This file covers only tests that span multiple stories or require the full system running — scenarios that isolated unit tests cannot catch._

_Uses `extends AbstractLocalIntegrationTest` (Testcontainers PostgreSQL 16)._
_Path: `src/test/java/com/tenxengage/app/integration/`_

---

## Lifecycle & CRUD

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `WalletIntegrationTest` | V6 migration applies — `reward_wallets` table exists with correct columns, constraints, and partial unique indexes | Table structure matches `technical.md → V6` SQL; existing rows have `wallet_type='INDIVIDUAL'`, `reserved_balance=0`, `version=0` | Foundation |
| `WalletIntegrationTest` | V7 migration applies — `ledger_entries` table exists with correct columns and indexes | Table structure matches `technical.md → V7` SQL; idempotency index `uq_ledger_credit_idempotency` present | Foundation |
| `WalletIntegrationTest` | Wallet auto-creation on first credit: no pre-existing wallet → `credit()` → wallet + ledger entry created atomically | One `RewardWallet` row created; one `LedgerEntry(CREDIT)` row written; `availableBalance = amount`; `available_balance_before = 0`, `available_balance_after = amount` | US-01, US-03 |

---

## Business Rule Enforcement

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `WalletIntegrationTest` | Insufficient balance: `availableBalance=5.00`, `reserve(10.00)` | `BusinessRuleException` thrown; `availableBalance` unchanged; no `LedgerEntry` written | US-03 |
| `WalletIntegrationTest` | Ledger snapshot integrity: `credit(100.00)` on wallet with `availableBalance=50.00` | `LedgerEntry.available_balance_before=50.00`, `available_balance_after=150.00`; `amount=100.00` | US-03 |
| `WalletIntegrationTest` | Idempotency: same `(referenceType, referenceId)` credited twice | Single `LedgerEntry` row; `availableBalance` incremented only once; second call returns current wallet state unchanged | US-03 |
| `WalletIntegrationTest` | Full balance lifecycle: CREDIT → RESERVE → DEBIT | After CREDIT: `available=100`, `reserved=0`; after RESERVE(30): `available=70`, `reserved=30`; after DEBIT(30): `available=70`, `reserved=0`; three `LedgerEntry` rows | US-03 |
| `WalletIntegrationTest` | Full balance lifecycle: CREDIT → RESERVE → RELEASE | After RESERVE(30): `available=70`, `reserved=30`; after RELEASE(30): `available=100`, `reserved=0` | US-03 |
| `WalletIntegrationTest` | RewardGrantService regression: `RewardGrantService.credit()` routes through `WalletService.credit()` | `LedgerEntry(CREDIT)` row written; `availableBalance` updated; `RewardGrantService` behaviour unchanged | US-03 |

---

## Concurrency

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `WalletIntegrationTest` | Concurrent credit race: two threads simultaneously credit the same wallet with no pre-existing row | Exactly one `RewardWallet` row created (pessimistic lock on auto-create); `availableBalance` equals sum of both credit amounts; two `LedgerEntry(CREDIT)` rows | US-03 |
| `WalletIntegrationTest` | Optimistic lock retry: two threads concurrently update the same existing wallet → one retries | Both updates eventually persist; final `availableBalance` correct; `wallet.optimistic_lock.retries` counter > 0 | US-03 |

---

## Tenant Isolation & Security

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `WalletIntegrationTest` | CLIENT_ADMIN of Tenant A calls `GET /api/v1/wallets/users/{userId}` with a userId from Tenant B | 404 — no enumeration of cross-tenant users | US-01 |
| `WalletIntegrationTest` | PARTNER_ADMIN calls `GET /api/v1/wallets/company/{companyId}` with a companyId from another PARTNER_ADMIN within the same tenant | 403 — company mismatch against JWT claim | US-01 |
| `WalletIntegrationTest` | Individual wallet read — PARTNER_SELLER of Tenant A; PARTNER_SELLER of Tenant B calls same `/me` | Tenant A user sees only their wallets; Tenant B user sees only their wallets; @Filter enforced | US-01 |

---

## Contract Conformance

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `WalletContractConformanceTest` | `GET /api/v1/wallets/me` response shape | 200; response body matches `RewardWalletResponse` schema in `../tenxengage-contracts/endpoints/wallet.yaml`; all required fields present with correct types; no `client_id`, `version`, `user_id`, `partner_company_id` fields | US-01 |
| `WalletContractConformanceTest` | `GET /api/v1/wallets/company/{companyId}` response shape | 200; matches contract schema | US-01 |
| `WalletContractConformanceTest` | `GET /api/v1/reward-balances` deprecated endpoint | 200; returns `RewardWalletResponse` shape with `balance` alias for `availableBalance` present | US-01 |

---

## Deprecated Endpoint Delegation

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `WalletIntegrationTest` | `GET /api/v1/reward-balances` delegates to `WalletService.getMyWallets()` | Response shape is `RewardWalletResponse`; `balance` field present as alias for `availableBalance`; no data loss | US-01 |

---

## Cross-Cutting Checks

| Check | Story/Foundation |
|---|---|
| Tenant isolation: `reward_wallets` rows from Tenant A not visible to Tenant B via `@Filter` | Foundation, US-01 |
| Balance amounts never logged at INFO — log output contains only walletId and currencyId | US-03 |
| `LedgerEntry` records are immutable — no UPDATE or DELETE permitted after write | Foundation, US-03 |
| `availableBalance` never goes below 0 — DB check constraint `available_balance >= 0` not violated | US-03 |
| `LedgerEntry.amount` always positive — DB check constraint `amount > 0` enforced | US-03 |
