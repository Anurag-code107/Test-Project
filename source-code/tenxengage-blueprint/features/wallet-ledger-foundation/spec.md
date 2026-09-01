---
slug: wallet-ledger-foundation
name: Wallet & Ledger Foundation
status: reviewed
format: story-sliced
roadmap: redemption-store
created: 2026-05-06
contract: null
---

# Feature: Wallet & Ledger Foundation

> **Reviewed**: 2026-05-06
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

Wallet & Ledger Foundation introduces the `RewardWallet` entity and `WalletService` — the financial data layer every other redemption-store feature depends on. The existing `RewardBalance` entity (a simple per-user earning accumulator) is evolved in-place into `RewardWallet`: the table is renamed, the balance split into `availableBalance` (spendable) and `reservedBalance` (locked in-flight), and company wallet support is added via a `walletType` discriminator and nullable `partnerCompanyId`. A new immutable `LedgerEntry` entity records every balance movement as an authoritative audit trail. `WalletService` becomes the single entry point for all balance mutations, replacing `RewardBalanceService`. `RewardGrantService` is updated to route its existing earning credits through `WalletService`. Partners see their available balance on every authenticated page via a `RewardBalanceWidget` component in `AppLayout`.

### Naming reconciliation

The BRD names the entity `RewardWallet` and the service `WalletService`. Both are used throughout this spec. The existing `RewardBalance` Java entity, `RewardBalanceService`, and `reward_balances` table are retired as part of this feature via V6 migration and code update. The existing API path `/api/v1/reward-balances` is kept alive temporarily via delegation while the frontend migrates to `/api/v1/wallets`.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | PARTNER_SELLER has a per-currency individual reward wallet (`walletType = INDIVIDUAL`) with `availableBalance` and `reservedBalance`, scoped to their tenant; wallet is auto-created on first credit if it does not exist |
| FR-2 | PARTNER_ADMIN has access to a per-currency company reward wallet (`walletType = COMPANY`) pooled at the partner-company level; company and individual balances are independent and never aggregate |
| FR-3 | Only `availableBalance` may be spent; `reservedBalance` is locked against in-flight redemptions; the constraint `availableBalance >= 0` is enforced at the service layer on every mutation |
| FR-4 | Every balance movement writes an immutable `LedgerEntry` record atomically before wallet totals are updated; the five entry types are: `CREDIT`, `RESERVE`, `DEBIT`, `RELEASE`, `RETURN_CREDIT` |
| FR-5 | `WalletService` is the single entry point for all balance mutations; `RewardGrantService` calls `walletService.credit()` on every earning event (incentive, training, activity, journey, deal) |
| FR-6 | `WalletService.credit()` is idempotent on `(walletId, referenceType, referenceId)` — a duplicate earning event delivery is detected and skipped without writing a second ledger entry |
| FR-7 | The platform nav header displays `availableBalance` per currency type on every authenticated page via `RewardBalanceWidget` in `AppLayout`; clicking the widget navigates to `/redemption-store` |
| FR-8 | The nav widget shows `availableBalance` only; for multi-currency holders it surfaces currencies with non-zero balance first, with a tooltip to expand all |
| FR-9 | CLIENT_ADMIN can retrieve wallet balances for any user or any partner company within their tenant |

---

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (reads)** | P95 < 300ms | Wallet list endpoints; indexed on `(client_id, user_id)` and `(client_id, partner_company_id)` |
| **Response time (writes)** | P95 < 500ms | `WalletService.credit()` including ledger write + balance update in one transaction |
| **Concurrency** | Optimistic locking (`@Version` on `RewardWallet`); up to 3 retries with exponential backoff on `OptimisticLockException` | Prevents lost updates on concurrent earning events |
| **Availability** | 99.9% | In the critical earning path — called by `RewardGrantService` on every reward grant |
| **Data sensitivity** | CONFIDENTIAL | Balance amounts are financial data |
| **Compliance** | GDPR | Wallet records linked to `userId`; subject to data-subject anonymization |
| **Audit retention** | 7 years | `LedgerEntry` records are immutable and must never be deleted |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec`
- [ ] Contracts generated via `/generate-contracts` in contracts repo
- [ ] Flyway migration V6 confirmed as next (current latest: V5)
- [ ] Confirm `partner_companies` table name and PK column before writing V6 FK reference
- [ ] Confirm `reward_balances` has no other FK references that would break on table rename (check migration history and any other entity referencing it)

---

## New Enums [BE]

| Enum Class | Values | Notes |
|---|---|---|
| `WalletType.java` | `INDIVIDUAL, COMPANY` | Discriminates individual seller wallets from pooled company wallets |
| `LedgerEntryType.java` | `CREDIT, RESERVE, DEBIT, RELEASE, RETURN_CREDIT` | The five balance movement types; see FR-4 for semantics |

_Path: `src/main/java/com/tenxengage/app/entity/enums/`_

---

## Data Model / Entities [BE]

### RewardWallet (table: `reward_wallets`) — EVOLVED FROM `reward_balances`

_Path: `src/main/java/com/tenxengage/app/entity/RewardWallet.java`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_
_Renamed from `RewardBalance` via V6 migration — all existing rows backfilled with `wallet_type = 'INDIVIDUAL'`, `reserved_balance = 0`, `version = 0`_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited from BaseEntity |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation — never expose in API responses |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `wallet_type` | `VARCHAR(20)` | NOT NULL, DEFAULT `'INDIVIDUAL'` | `WalletType` enum; added V6 |
| `user_id` | `UUID` | NULL, FK → users | Populated for INDIVIDUAL; NULL for COMPANY; existing column made nullable in V6 |
| `partner_company_id` | `UUID` | NULL, FK → partner_companies | Populated for COMPANY; NULL for INDIVIDUAL; added V6 |
| `currency_id` | `VARCHAR(50)` | NOT NULL | Currency code — "cash", "points", "credits", "tickets" |
| `available_balance` | `DECIMAL(18,2)` | NOT NULL, DEFAULT 0 | Renamed from `balance` in V6; spendable amount |
| `reserved_balance` | `DECIMAL(18,2)` | NOT NULL, DEFAULT 0 | Locked against in-flight redemptions; added V6 |
| `version` | `BIGINT` | NOT NULL, DEFAULT 0 | Optimistic locking (`@Version`); added V6 |

**Check constraint (V6):**

```
(wallet_type = 'INDIVIDUAL' AND user_id IS NOT NULL AND partner_company_id IS NULL)
OR
(wallet_type = 'COMPANY' AND partner_company_id IS NOT NULL AND user_id IS NULL)
```

**Unique constraints (V6 partial indexes):**
- `UNIQUE(client_id, user_id, currency_id)` WHERE `wallet_type = 'INDIVIDUAL'`
- `UNIQUE(client_id, partner_company_id, currency_id)` WHERE `wallet_type = 'COMPANY'`

**Indexes:**
- `idx_reward_wallets_client_user` on `(client_id, user_id)` — all currencies for a user
- `idx_reward_wallets_client_company` on `(client_id, partner_company_id)` — all currencies for a company

---

### LedgerEntry (table: `ledger_entries`) — NEW

_Path: `src/main/java/com/tenxengage/app/entity/LedgerEntry.java`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_
_Immutable — no update or delete operations permitted after write_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited; serves as the entry timestamp |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited; semantically unused (never updated) |
| `reward_wallet_id` | `UUID` | NOT NULL, FK → reward_wallets | The wallet this entry affects |
| `entry_type` | `VARCHAR(30)` | NOT NULL | `LedgerEntryType` enum |
| `amount` | `DECIMAL(18,2)` | NOT NULL, CHECK > 0 | Always positive; direction implied by `entry_type` |
| `currency_id` | `VARCHAR(50)` | NOT NULL | Denormalized from wallet for currency-filtered queries |
| `reference_type` | `VARCHAR(50)` | NULL | Source domain: `INCENTIVE`, `TRAINING`, `ACTIVITY`, `JOURNEY`, `DEAL`, `REDEMPTION`, `RETURN` |
| `reference_id` | `UUID` | NULL | FK to the source record |
| `note` | `VARCHAR(500)` | NULL | Human-readable context |
| `available_balance_before` | `DECIMAL(18,2)` | NOT NULL | Snapshot before entry; enables reconciliation |
| `available_balance_after` | `DECIMAL(18,2)` | NOT NULL | Snapshot after entry |
| `reserved_balance_before` | `DECIMAL(18,2)` | NOT NULL | Snapshot before |
| `reserved_balance_after` | `DECIMAL(18,2)` | NOT NULL | Snapshot after |

**Idempotency index:**
`UNIQUE(reward_wallet_id, reference_type, reference_id)` WHERE `reference_id IS NOT NULL AND entry_type = 'CREDIT'` — prevents double-crediting the same earning event.

**Indexes:**
- `idx_ledger_entries_client_id` on `client_id`
- `idx_ledger_entries_wallet_id` on `reward_wallet_id`
- `idx_ledger_entries_wallet_created` on `(reward_wallet_id, created_at DESC)` — paginated history (F-05)
- `idx_ledger_entries_reference` on `(reference_type, reference_id)` WHERE `reference_id IS NOT NULL`

---

## Permissions & Feature Flags [BE + FE]

### Permission Matrix

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `module.redemption_store` | Redemption Store | MODULE | `ALL` | MODULE_ACCESS | Y | — | Y | Y |
| `action.redemption.view_history` | View Redemption History | ACTION | `EXTERNAL` | REDEMPTION_ACTIONS | — | — | Y | Y |
| `action.redemption.view_all_history` | View All Redemption History | ACTION | `INTERNAL` | REDEMPTION_ACTIONS | Y | — | — | — |

_For F-01: wallet balance reads are gated on `module.redemption_store`. Own balance (`GET /api/v1/wallets/me`) uses `@PreAuthorize("isAuthenticated()")` — no action permission required. **Approved convention deviation:** conventions.md mandates `@RequiresPermission` on all protected endpoints; `/wallets/me` intentionally uses `isAuthenticated()` because balance data is the user's own financial record and should be accessible without an additional action permission, consistent with how `/me` endpoints work across the platform. Company wallet reads require `module.redemption_store` plus role check. Admin views require `action.redemption.view_all_history`._

_Additional action permissions (`action.redemption.redeem`, `action.redemption.configure`, etc.) are seeded in their respective features (F-02, F-03). V8 seeds only what F-01 needs._

### Feature Flag

| Feature Key | Description | Starter | Professional | Enterprise | Category |
|---|---|---|---|---|---|
| `redemption_store` | Enables Redemption Store — wallet, catalog, and redemption flow | `true` | `true` | `true` | REWARDS |

_Note: BRD annex specifies all tiers enabled. Verify with product before finalising._

_Flyway seed SQL lives in `technical.md → ## Flyway Migrations [BE]`._

---

## DTOs [BE]

### Request DTOs

No user-facing request DTOs in F-01. Wallets are created automatically; ledger entries are written internally only.

### Response DTOs

_Path: `src/main/java/com/tenxengage/app/dto/response/`_

| Record | Static Factory | Notes |
|---|---|---|
| `RewardWalletResponse` | `from(RewardWallet)` | New — replaces `RewardBalanceResponse` for wallet reads |
| `LedgerEntryResponse` | `from(LedgerEntry)` | New — stubbed in F-01; fully used by F-05 (Transaction History) |

**`RewardWalletResponse` fields:**

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Wallet ID — needed by F-03 to reference wallet on redemption submission |
| `walletType` | `String` | `"INDIVIDUAL"` or `"COMPANY"` |
| `currencyId` | `String` | Currency code |
| `availableBalance` | `String` | Plain string decimal — spendable amount |
| `reservedBalance` | `String` | Plain string decimal — locked in-flight |

**Never include:** `client_id`, `version`, `user_id`, `partner_company_id` in responses.

**`RewardBalanceResponse` (existing):** Kept during transition period; its `from()` factory delegates to the `RewardWalletResponse` shape. Removed once the frontend fully migrates to `/api/v1/wallets`.

---

## API Endpoints [BE + FE]

_Base path: `/api/v1/wallets`_
_Tag: `Wallet`_
_Controller: `WalletController` (new)_

| Method | Path | Response | Status | Permission | Notes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/wallets/me` | `List<RewardWalletResponse>` | 200 | `isAuthenticated()` | Own individual wallet balances; all currencies |
| `GET` | `/api/v1/wallets/company/{companyId}` | `List<RewardWalletResponse>` | 200 | `module.redemption_store` | PARTNER_ADMIN: own company only; CLIENT_ADMIN: any company in tenant |
| `GET` | `/api/v1/wallets/users/{userId}` | `List<RewardWalletResponse>` | 200 | `action.redemption.view_all_history` | CLIENT_ADMIN admin view of any individual user's wallets |

**Authorization logic for `/company/{companyId}`:**
- PARTNER_ADMIN: server validates `companyId` matches the caller's associated partner company (resolved from JWT); returns 403 if mismatch
- CLIENT_ADMIN: server validates `companyId` belongs to `TenantContext.getCurrentClientId()`; returns 404 if not found

**Pagination note:** These endpoints return `List<RewardWalletResponse>` directly (not the project's paginated envelope). Intentional — a user has at most one wallet per currency (4 currencies max), making pagination unnecessary. Approved deviation from conventions.md.

**Deprecated (delegated during transition):**
- `GET /api/v1/reward-balances` → delegates to `walletService.getMyWallets()`; returns `RewardWalletResponse` list

**Error responses:**
- `401` — not authenticated
- `403` — PARTNER_ADMIN attempting another company's wallet
- `404` — user or company not found in tenant

---

## Service Layer [BE]

_Path: `src/main/java/com/tenxengage/app/service/`_

### WalletService (NEW — replaces RewardBalanceService)

| Method | Return Type | Notes |
|---|---|---|
| `getMyWallets()` | `List<RewardWalletResponse>` | `@Transactional(readOnly=true)` — resolves `userId` from `SecurityContext` |
| `getCompanyWallets(companyId)` | `List<RewardWalletResponse>` | `@Transactional(readOnly=true)` — validates company access |
| `getUserWallets(userId)` | `List<RewardWalletResponse>` | `@Transactional(readOnly=true)` — admin; validates `userId` in tenant |
| `credit(clientId, userId, currencyId, amount, referenceType, referenceId, note)` | `RewardWallet` | `@Transactional` — individual wallet credit; auto-creates wallet if absent; writes `LedgerEntry(CREDIT)`; idempotency check; optimistic lock retry max 3 |
| `creditCompany(clientId, partnerCompanyId, currencyId, amount, referenceType, referenceId, note)` | `RewardWallet` | `@Transactional` — company wallet credit; same behaviour as `credit()` |
| `reserve(walletId, amount, referenceType, referenceId)` | `RewardWallet` | `@Transactional` — used by F-03; validates `availableBalance >= amount`; writes `LedgerEntry(RESERVE)` |
| `debit(walletId, amount, referenceType, referenceId)` | `RewardWallet` | `@Transactional` — used by F-03 on vendor confirmation; writes `LedgerEntry(DEBIT)` |
| `release(walletId, amount, referenceType, referenceId)` | `RewardWallet` | `@Transactional` — used by F-03 on failure/cancel; writes `LedgerEntry(RELEASE)` |
| `returnCredit(walletId, amount, referenceType, referenceId)` | `RewardWallet` | `@Transactional` — used by F-06; writes `LedgerEntry(RETURN_CREDIT)` |

**Business rules:**
- `availableBalance` must never go negative; any operation that would reduce it below 0 throws `BusinessRuleException("Insufficient available balance for " + currencyId)` → HTTP 400.
- All ledger writes and balance updates occur within a single `@Transactional` boundary.
- Currency code must resolve via `CurrencyService.findByCode(clientId, currencyId)` — unknown codes rejected.
- Wallet auto-creation uses `findOrCreate` with pessimistic write lock to prevent duplicate wallet creation under concurrent first-credit race.

**Tenant isolation contract:** All service methods resolve `clientId` from `TenantContext.getCurrentClientId()` — never accept `clientId` as a parameter from the API layer. Internal calls from `RewardGrantService` pass `clientId` explicitly as a trusted internal caller.

### RewardGrantService (MODIFIED — existing)

Single change: replace `rewardBalanceService.credit(...)` call at line 175 with `walletService.credit(...)`. All other logic unchanged.

### RewardBalanceService (DEPRECATED)

Mark `@Deprecated`. Keep compiling for one release cycle; remove in the subsequent feature. `WalletService` is its direct replacement.

---

## Workflow / Status Transitions [BE + FE]

`RewardWallet` has no status field. Balance transitions are driven entirely by `LedgerEntry` writes. Valid movement sequence for the full redemption lifecycle (F-03 implements RESERVE/DEBIT/RELEASE):

```
CREDIT        → availableBalance increases (earning event)
RESERVE       → availableBalance decreases, reservedBalance increases (redemption submitted)
DEBIT         → reservedBalance decreases, total wallet value reduced (vendor confirmed)
  OR
RELEASE       → reservedBalance decreases, availableBalance restored (failed/cancelled)
  OR
RETURN_CREDIT → availableBalance increases (vendor confirmed return — F-06)
```

**Invalid operations (rejected at service layer with 400):**
- `reserve(amount)` where `availableBalance < amount` → "Insufficient available balance for {currencyId}"
- `debit(amount)` where `reservedBalance < amount` → "Reserved balance insufficient for this operation"
- `release(amount)` where `reservedBalance < amount` → "Reserved balance insufficient for this operation"

---

## Security Design [BE]

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| `availableBalance`, `reservedBalance` | Confidential (financial) | Never logged at INFO; balance amounts logged at DEBUG only |
| `userId`, `partnerCompanyId` | Pseudonymous | Logged as UUID only |
| `client_id` | Internal | Never returned in API responses |
| `LedgerEntry.amount` | Confidential | Not exposed in nav widget; only in transaction history (F-05) |

### Rate Limiting

| Endpoint | Limit | Scope | Reason |
|---|---|---|---|
| `GET /api/v1/wallets/me` | 60 req/min | Per user | Called by nav widget on every page load |
| `GET /api/v1/wallets/company/{companyId}` | 60 req/min | Per user | Same |
| `GET /api/v1/wallets/users/{userId}` | 30 req/min | Per tenant | Admin reporting use |

### OWASP Risks & Mitigations

| Risk | Where | Mitigation |
|---|---|---|
| **Broken Access Control (A01)** | `GET /api/v1/wallets/users/{userId}` | Resolved via `findByClientIdAndUserId` — cross-tenant userId returns 404, never 403 |
| **IDOR (A01)** | `GET /api/v1/wallets/company/{companyId}` | PARTNER_ADMIN's `companyId` validated against JWT claim; CLIENT_ADMIN's `companyId` validated against tenant |
| **Race condition** | Concurrent earning events to same wallet | `@Version` optimistic locking + 3-retry exponential backoff |
| **Double credit** | Duplicate earning event delivery | Idempotency index on `(reward_wallet_id, reference_type, reference_id)` in `ledger_entries` |
| **Negative balance** | Any debit-type operation | Service layer hard check before write; never persisted |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `companyId` (path param) | UUID format | 400 if not valid UUID; 403/404 on access check |
| `userId` (path param) | UUID format | 400 if not valid UUID; 404 if not in tenant |
| `currencyId` (internal) | Must resolve in `CurrencyService` | `BusinessRuleException` → 400 |
| `amount` (internal) | Must be > 0 | `BusinessRuleException` → 400 |

---

## Audit Trail [BE]

`LedgerEntry` is the financial audit trail for all balance movements. `@Audited` is applied only to wallet creation events.

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| Wallet auto-created (first credit) | `RewardWallet` | `walletType`, `currencyId`, `userId`/`partnerCompanyId`, `createdAt` | `CLIENT_ADMIN` |

### New Audit Enum Values

| Enum | New Value | Reason |
|---|---|---|
| `AuditResourceType` | `REWARD_WALLET` | New entity type for wallet creation audit |

No new `AuditAction` values needed. Wallet creation uses `AuditAction.CREATED`. Balance movements are recorded as `LedgerEntry` records — the ledger is the financial audit trail.

### `@Audited` Annotation Details

| Service Method | `action` | `resourceType` | `description` |
|---|---|---|---|
| `credit()` — first credit auto-creates wallet | `CREATED` | `REWARD_WALLET` | `"Auto-created individual wallet for {currencyId}"` |
| `creditCompany()` — first credit auto-creates wallet | `CREATED` | `REWARD_WALLET` | `"Auto-created company wallet for {currencyId}"` |

**Audit record retention:** 7 years. `LedgerEntry` records are append-only and must never be deleted.

---

## Observability [BE]

### MDC Fields

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` header | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` from JWT | `TenantFilter` (existing) |
| `userId` | User ID from JWT | `JwtAuthenticationFilter` (existing) |
| `featureArea` | `"wallet-ledger"` | Set in `WalletService` constructor |

### Key Log Events

| Event | Level | `step` value | Key Fields |
|---|---|---|---|
| Wallet auto-created | INFO | `wallet_created` | `walletId`, `walletType`, `currencyId` |
| Balance credited | INFO | `balance_credited` | `walletId`, `currencyId`, `referenceType`, `referenceId` |
| Balance reserved | INFO | `balance_reserved` | `walletId`, `currencyId` |
| Balance debited | INFO | `balance_debited` | `walletId`, `currencyId` |
| Balance released | INFO | `balance_released` | `walletId`, `currencyId` |
| Insufficient balance | WARN | `insufficient_balance` | `walletId`, `currencyId` |
| Optimistic lock retry | WARN | `optimistic_lock_retry` | `walletId`, `attempt` |
| Optimistic lock exhausted | ERROR | `optimistic_lock_exhausted` | `walletId`, `attempts` |
| Duplicate credit skipped | INFO | `duplicate_credit_skipped` | `referenceType`, `referenceId` |
| Tenant isolation violation | ERROR | `tenant_isolation_violation` | `requestedId`, `callerTenantId` |

**Never log balance amounts at INFO. Log wallet IDs and currency codes only.**

### Metrics

| Metric | Type | Labels |
|---|---|---|
| `wallet.credit.total` | Counter | `tenantId`, `currencyId`, `referenceType` |
| `wallet.reserve.total` | Counter | `tenantId`, `currencyId` |
| `wallet.debit.total` | Counter | `tenantId`, `currencyId` |
| `wallet.release.total` | Counter | `tenantId`, `currencyId` |
| `wallet.insufficient_balance.total` | Counter | `tenantId`, `currencyId` |
| `wallet.credit.duration_ms` | Histogram | — |
| `wallet.optimistic_lock.retries` | Counter | — |

---

## Frontend Specification [FE]

_TypeScript types live in `../tenxengage-contracts/` — copy from there, do not hand-write. Full FE file paths and hook specs: see `technical.md`._

### Pages

No new pages in F-01. Redemption Store page is introduced in F-02/F-03.

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `RewardBalanceWidget` | `className?: string` | `useMyWallets()` hook | Persistent nav header; shows primary available balance; click → `/redemption-store`; visible only when `module.redemption_store` granted |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `useMyWallets()` | `['wallets', 'me']` | `GET /api/v1/wallets/me` | 2 min | On redemption mutations (F-03) |
| `useCompanyWallet(companyId)` | `['wallets', 'company', companyId]` | `GET /api/v1/wallets/company/{companyId}` | 2 min | On company redemption mutations (F-03) |
| `useUserWalletsAdmin(userId)` | `['wallets', 'user', userId]` | `GET /api/v1/wallets/users/{userId}` | 5 min | — |

_StaleTime 2 min for own wallets to keep the nav widget reasonably current._

### Modified Files

- `AppLayout.tsx` — add `<RewardBalanceWidget />` to header, conditional on `module.redemption_store` permission
- `reward-balance.service.ts` — updated to call `/api/v1/wallets/me` during transition; replaced by `wallet.service.ts` once migration is complete

---

## Caching Strategy [BE]

No server-side caching. Balance data changes on every earning event and redemption — stale reads would mislead users about their available balance. TanStack Query client-side caching with 2-minute stale time handles frontend performance.

---

## Data Retention & Compliance [BE]

### Soft Delete

`RewardWallet`: No soft delete. Wallet records are permanent while the user/company is active. On GDPR data-subject deletion: `user_id` is anonymized to a tenant-level sentinel UUID; wallet and ledger records are retained for financial compliance.

`LedgerEntry`: **Never deleted.** Append-only. Financial records exemption under GDPR Article 17(3)(b) — retained 7 years minimum.

### PII Handling

| Field | Entity | PII Type | GDPR Treatment |
|---|---|---|---|
| `user_id` | `RewardWallet` | Pseudonymous UUID | Anonymized on data-subject deletion; wallet record preserved |
| `user_id` (indirect via wallet join) | `LedgerEntry` | Pseudonymous | Retained under financial records exemption |

### Data Retention Periods

| Data | Retention | Justification |
|---|---|---|
| `RewardWallet` records | Duration of partnership + 7 years | Financial liability records |
| `LedgerEntry` records | 7 years minimum | Financial audit trail |
| Audit log entries | 7 years | Compliance requirement |

---

## Edge Cases [BE + FE]

1. **Wallet auto-creation race:** Two concurrent earning events for same user + currency with no existing wallet. Service uses `SELECT FOR UPDATE` on first-create to prevent duplicate wallet rows — second thread waits, then finds the wallet already created by the first.
2. **Unknown currency code:** Earning event references currency not in tenant's currencies → `WalletService.credit()` rejects with 400 "Currency {code} not found for tenant". No wallet created.
3. **PARTNER_ADMIN companyId mismatch:** `GET /api/v1/wallets/company/{companyId}` where caller's JWT company differs from path `companyId` → 403.
4. **PARTNER_SELLER accessing company endpoint:** `GET /api/v1/wallets/company/{companyId}` called by a PARTNER_SELLER role — they hold `module.redemption_store` but are not PARTNER_ADMIN. Server-side role check returns 403 before any wallet lookup.
5. **PARTNER_ADMIN with no company association:** PARTNER_ADMIN whose JWT contains no `partnerCompanyId` claim (e.g., during initial onboarding) calling `/company/{companyId}` → 403 "Caller has no associated partner company."
6. **Zero balance currencies:** Nav widget collapses currencies where both balances are zero; expand control reveals all.
7. **Optimistic lock exhausted after 3 retries:** Log at ERROR (`step=optimistic_lock_exhausted`), return 500 "Service temporarily unavailable — please retry".
8. **Idempotent double credit:** Same `referenceId` + `referenceType` re-delivered → HTTP 200 with current balance; logs `step=duplicate_credit_skipped`; no second ledger entry written.
9. **Cross-tenant admin enumeration:** `GET /api/v1/wallets/users/{userId}` with userId from another tenant → 404.
10. **Nav widget loading state:** Renders skeleton loader before query resolves; never a blank space.
11. **RewardBalanceController transition:** Old `/api/v1/reward-balances` delegates to `WalletService.getMyWallets()`. Frontend must migrate to `/api/v1/wallets/me` before the deprecated endpoint is removed.

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, @WebMvcTest, Vitest, E2E Playwright) — live inside each `stories/US-NN-*.md` alongside the code they verify
- **Cross-story integration tests** (Testcontainers full-lifecycle, multi-entity workflows, tenant isolation) — in [test-plan.md](test-plan.md)

**Key integration scenarios for test-plan.md:**
- Wallet auto-creation on first credit (no pre-existing wallet → credit → wallet + ledger entry created atomically)
- Concurrent credit race (two threads credit same wallet → optimistic lock → correct final balance, no lost update)
- Individual vs company wallet isolation (PARTNER_SELLER cannot access `/company/{companyId}` endpoint)
- Tenant isolation (CLIENT_ADMIN of Tenant A cannot see Tenant B user's wallets via `/users/{userId}`)
- Idempotency (same `referenceId` credited twice → single ledger entry, correct balance)
- Insufficient balance rejection (`availableBalance = 5`, reserve 10 → 400)
- Ledger snapshot integrity (`available_balance_before + amount = available_balance_after` for each CREDIT)
- Deprecated endpoint delegation (`GET /api/v1/reward-balances` returns `RewardWalletResponse` shape via `WalletService.getMyWallets()`)
- PARTNER_ADMIN company mismatch (`GET /api/v1/wallets/company/{otherId}` → 403)
- RewardGrantService regression (credit routed through `WalletService.credit()` → ledger entry written, balance updated)

---

## Modified Existing Endpoints [BE + FE]

| Endpoint | Change | Reason | Breaking? |
|---|---|---|---|
| `GET /api/v1/reward-balances` | Delegates to `WalletService.getMyWallets()`; response updated to `RewardWalletResponse` shape | Table renamed; service replaced | No — `balance` field kept as alias for `availableBalance`; new fields additive |
| `GET /api/v1/reward-balances/{userId}` | Delegates to `WalletService.getUserWallets(userId)` | Same | No — additive |

---

## Out of Scope

- Redemption submission, RESERVE/DEBIT/RELEASE operations — F-03
- Transaction history UI and paginated ledger endpoint — F-05
- Redemption Store page and catalog — F-02/F-03
- Approval queue for company wallet redemptions — F-04 (ADR-01 unresolved)
- In-flight redemption limit per user — ADR-03, F-03
- Reward balance expiration — Phase 2 (F-09)
- Cross-currency redemptions — v1 non-goal
- Kafka domain event publishing from wallet mutations — Phase 2
- PAS Commercial Intent signal from redemption activity — Phase 2

---

## Verification Steps

### Backend Verification
1. `./gradlew bootRun` — app starts; V6 renames `reward_balances` → `reward_wallets`, existing rows present with `wallet_type = 'INDIVIDUAL'`; V7 creates `ledger_entries`; V8 seeds permissions
2. `./gradlew test` — all new and existing tests pass; `RewardGrantService` tests pass with `walletService.credit()` call
3. Security: `GET /api/v1/wallets/company/{otherCompanyId}` as PARTNER_ADMIN → 403; `GET /api/v1/wallets/users/{otherTenantUserId}` as CLIENT_ADMIN → 404; unauthenticated → 401
4. Idempotency: same earning event delivered twice → one `LedgerEntry`, correct `availableBalance`
5. Concurrent credit: two threads credit same wallet → final `availableBalance` equals sum of both amounts

### Frontend Verification
1. `npm run build` — no TypeScript errors
2. `RewardBalanceWidget` visible in nav for PARTNER_SELLER and PARTNER_ADMIN; hidden for CLIENT_ADMIN
3. Skeleton renders on load; available balance renders after query resolves
4. Zero-balance currencies collapsed by default; expand shows all
5. Widget click navigates to `/redemption-store`
