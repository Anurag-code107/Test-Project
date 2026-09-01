---
id: US-03
title: "Wallet mutation service + grant integration"
layers: ["BE"]
seed_id: ["F-01.S-03", "F-01.S-05"]
touches_entities: ["RewardWallet", "LedgerEntry"]
depends_on_stories: ["US-01"]
---

# US-03: Wallet mutation service + grant integration

## Description

**Actor:** Internal callers — `RewardGrantService` (earning events), F-03 redemption flow (reserve/debit/release), F-06 returns (returnCredit). No user-facing API endpoints in F-01.
**Trigger:** An earning event arrives at `RewardGrantService`, or F-03/F-06 invoke reserve/debit/release/returnCredit on `WalletService`.

**Steps:**
1. Caller invokes `WalletService.credit/creditCompany/reserve/debit/release/returnCredit`
2. For `credit` only: idempotency check — if `(walletId, referenceType, referenceId)` already exists in `ledger_entries` → skip, return current wallet state
3. For `credit` on a new user+currency combo: auto-create wallet with pessimistic write lock; write `@Audited` record
4. Optimistic lock (`@Version`) acquired; balance and ledger updated atomically in single `@Transactional`
5. `LedgerEntry` written with before/after balance snapshots
6. Updated `RewardWallet` returned; callers may read `availableBalance`/`reservedBalance`
7. On `OptimisticLockException`: retry up to 3 times with exponential backoff; after 3 failures log ERROR `step=optimistic_lock_exhausted` and throw 500

**Expected outcome:** Every balance movement produces an immutable `LedgerEntry` record. `RewardGrantService` routes all earnings through `WalletService.credit()`. Balance invariant `availableBalance >= 0` is always maintained.

**Negative paths:**
- `reserve(amount)` where `availableBalance < amount` → `BusinessRuleException("Insufficient available balance for {currencyId}")` → HTTP 400
- `debit(amount)` where `reservedBalance < amount` → `BusinessRuleException("Reserved balance insufficient for this operation")` → HTTP 400
- `release(amount)` where `reservedBalance < amount` → same 400
- Duplicate earning event (same `referenceId` + `referenceType`) → idempotency check passes; returns 200 with current balance; no second `LedgerEntry`
- Unknown `currencyId` → `CurrencyService.findByCode()` rejects → 400
- Optimistic lock exhausted after 3 retries → 500 "Service temporarily unavailable — please retry"

---

## Acceptance Criteria

- **AC-1:** `WalletService.credit()` writes `LedgerEntry(CREDIT)` and updates `availableBalance` atomically within a single `@Transactional`; idempotent on `(walletId, referenceType, referenceId)` — a duplicate call returns the current wallet state with no second ledger entry written
- **AC-2:** `WalletService.credit()` auto-creates individual wallet on first call for `(clientId, userId, currencyId)` using pessimistic write lock; writes `@Audited(action=CREATED, resourceType=REWARD_WALLET)` on auto-creation; `creditCompany()` same behaviour for COMPANY wallets
- **AC-3:** `WalletService.reserve()` throws `BusinessRuleException("Insufficient available balance for {currencyId}")` when `availableBalance < amount`; on success decreases `availableBalance` and increases `reservedBalance`; writes `LedgerEntry(RESERVE)`
- **AC-4:** `WalletService.debit()` writes `LedgerEntry(DEBIT)` and decreases `reservedBalance`; `WalletService.release()` writes `LedgerEntry(RELEASE)` and restores `availableBalance`; both throw `BusinessRuleException` if `reservedBalance < amount`
- **AC-5:** `WalletService.returnCredit()` writes `LedgerEntry(RETURN_CREDIT)` and increases `availableBalance`; all mutation methods enforce `@Version` optimistic lock with up to 3 retries on `OptimisticLockException`; after 3 failures logs ERROR `step=optimistic_lock_exhausted`
- **AC-6:** `RewardGrantService.credit()` routes through `walletService.credit()` (line 175 update); all existing `RewardGrantService` tests continue to pass

---

## Out of Scope

- API endpoints for reserve/debit/release — wired by F-03 redemption flow
- `returnCredit` called from API — wired by F-06 returns
- LedgerEntry pagination endpoint — F-05
- Kafka domain events on balance mutations — Phase 2 per spec Out of Scope
- Company wallet reserve/debit/release — F-03

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 (WalletService.java class established; mutations added in same file)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-3, FR-4, FR-5, FR-6
- `spec.md → ## Service Layer [BE]` — all `WalletService` mutation method signatures, business rules, tenant isolation contract
- `spec.md → ## Workflow / Status Transitions [BE + FE]` — CREDIT/RESERVE/DEBIT/RELEASE/RETURN_CREDIT sequence and invalid operations
- `spec.md → ## Data Model / Entities [BE]` — `LedgerEntry` fields; `LedgerEntry.amount CHECK > 0`; idempotency index
- `spec.md → ## Audit Trail [BE]` — `@Audited` on `credit()` and `creditCompany()` auto-creation paths
- `spec.md → ## Observability [BE]` — MDC fields, key log events (balance_credited, balance_reserved, optimistic_lock_retry, duplicate_credit_skipped)
- `spec.md → ## Security Design [BE]` — race condition mitigation (pessimistic lock on wallet auto-create); double-credit prevention (idempotency index)
- `spec.md → ## Edge Cases` — edge cases 1 (race), 2 (unknown currency), 7 (optimistic lock exhausted), 8 (idempotent double credit)
- `technical.md → ## Repository Queries [BE]` — `findForUpdate` pessimistic lock query; `existsByRewardWalletIdAndReferenceTypeAndReferenceId` idempotency check

---

## BE tasks [BE]

### BE-1: WalletService mutation methods

**File:** `src/main/java/com/tenxengage/app/service/WalletService.java` — ADD to existing class (created in US-01):

Implement all six mutation methods per `spec.md → ## Service Layer [BE]`:

- `credit(clientId, userId, currencyId, amount, referenceType, referenceId, note)`:
  - Idempotency check: `ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceId(walletId, referenceType, referenceId)` — if true, log `step=duplicate_credit_skipped` and return current wallet
  - Auto-create wallet: `rewardWalletRepository.findForUpdate(clientId, userId, currencyId, INDIVIDUAL)` — if absent, persist new `RewardWallet` + write `@Audited` record
  - Write `LedgerEntry(CREDIT)` with before/after balance snapshots
  - Increment `availableBalance` by `amount`
  - Persist wallet; return

- `creditCompany(clientId, partnerCompanyId, currencyId, amount, referenceType, referenceId, note)` — same pattern for COMPANY wallet

- `reserve(walletId, amount, referenceType, referenceId)`:
  - Validate `availableBalance >= amount`; throw `BusinessRuleException("Insufficient available balance for " + currencyId)` if not _(AC-3)_
  - Write `LedgerEntry(RESERVE)`; decrease `availableBalance`, increase `reservedBalance`

- `debit(walletId, amount, referenceType, referenceId)`:
  - Validate `reservedBalance >= amount`; throw `BusinessRuleException("Reserved balance insufficient for this operation")` if not _(AC-4)_
  - Write `LedgerEntry(DEBIT)`; decrease `reservedBalance`

- `release(walletId, amount, referenceType, referenceId)`:
  - Validate `reservedBalance >= amount` _(AC-4)_
  - Write `LedgerEntry(RELEASE)`; decrease `reservedBalance`, restore `availableBalance`

- `returnCredit(walletId, amount, referenceType, referenceId)`:
  - Write `LedgerEntry(RETURN_CREDIT)`; increase `availableBalance` _(AC-5)_

**Optimistic lock wrapper (all mutation methods):** Wrap in retry loop — max 3 attempts, exponential backoff on `OptimisticLockException`; after 3 failures log `step=optimistic_lock_exhausted` at ERROR and re-throw _(AC-5)_

**Transaction scope:** All ledger writes + balance updates within single `@Transactional` boundary _(AC-1)_

### BE-2: @Audited on auto-create paths

In `WalletService.credit()` and `creditCompany()`, annotate the wallet auto-creation code path with:
```
@Audited(action = AuditAction.CREATED, resourceType = AuditResourceType.REWARD_WALLET,
         description = "Auto-created individual wallet for {currencyId}")
```
_(AC-2)_ — see `spec.md → ## Audit Trail [BE] → @Audited Annotation Details`

### BE-3: WalletServiceTest — mutation cases

**File:** `src/test/java/com/tenxengage/app/service/WalletServiceTest.java` — ADD to existing test class:

- `credit_writesLedgerEntry_andUpdatesBalance` _(AC-1)_
- `credit_isIdempotent_whenSameReferenceIdDeliveredTwice` _(AC-1)_
- `credit_autoCreatesWallet_onFirstCreditForUserCurrency` _(AC-2)_
- `reserve_decreasesAvailableBalance_andIncreasesReservedBalance` _(AC-3)_
- `reserve_throwsBusinessRuleException_whenInsufficientBalance` _(AC-3)_
- `debit_decreasesReservedBalance` _(AC-4)_
- `debit_throwsBusinessRuleException_whenInsufficientReservedBalance` _(AC-4)_
- `release_restoresAvailableBalance` _(AC-4)_
- `returnCredit_increasesAvailableBalance` _(AC-5)_
- `credit_retriesOnOptimisticLockException_upTo3Times` _(AC-5)_

### BE-4: RewardGrantService update + regression tests

**Files:**
- `src/main/java/com/tenxengage/app/service/RewardGrantService.java` — MODIFIED; line 175: replace `rewardBalanceService.credit(...)` with `walletService.credit(...)`; inject `WalletService` if not already present; all other logic unchanged _(AC-6)_
- Existing `RewardGrantServiceTest` — verify all existing tests still pass; add one new case `credit_routesThroughWalletService` that asserts `walletService.credit()` is called _(AC-6)_

---

## E2E test

_Omitted — `layers: ["BE"]`. Coverage via WalletServiceTest unit tests in BE-3._

---

## Execution checklist

**BE session:**
- [ ] `WalletService.credit()` implemented — idempotency check, wallet auto-create with pessimistic lock, `LedgerEntry(CREDIT)` write, balance update, `@Transactional` _(AC-1, AC-2)_
- [ ] `WalletService.creditCompany()` implemented — same pattern for COMPANY wallet _(AC-2)_
- [ ] `@Audited` annotation on auto-create path in `credit()` and `creditCompany()` _(AC-2)_
- [ ] `WalletService.reserve()` implemented — balance validation + `LedgerEntry(RESERVE)` _(AC-3)_
- [ ] `WalletService.debit()` implemented — reservedBalance validation + `LedgerEntry(DEBIT)` _(AC-4)_
- [ ] `WalletService.release()` implemented — reservedBalance validation + `LedgerEntry(RELEASE)` _(AC-4)_
- [ ] `WalletService.returnCredit()` implemented — `LedgerEntry(RETURN_CREDIT)` + availableBalance increase _(AC-5)_
- [ ] Optimistic lock retry wrapper applied to all mutation methods — max 3 retries, ERROR log on exhaustion _(AC-5)_
- [ ] All ledger writes + balance updates within single `@Transactional` boundary _(AC-1)_
- [ ] `WalletServiceTest` mutation cases pass — happy paths + idempotency + insufficient balance + optimistic lock _(AC-1, AC-2, AC-3, AC-4, AC-5)_
- [ ] `RewardGrantService.java` line 175 updated — `walletService.credit()` replaces `rewardBalanceService.credit()` _(AC-6)_
- [ ] Existing `RewardGrantServiceTest` cases still pass + new `credit_routesThroughWalletService` case added _(AC-6)_

---

## Done when

1. `./gradlew test` passes — all `WalletServiceTest` mutation cases + `RewardGrantServiceTest` cases green
2. Every AC above is referenced by at least one passing unit test
