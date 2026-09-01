---
id: US-03
title: "Balance expiry execution + policy-change cancellation"
layers: ["BE"]
touches_entities: ["BalanceExpiryNotice", "RewardWallet", "LedgerEntry"]
depends_on_stories: ["US-01", "US-02"]
seed_id: "F-09.S-03"
---

# US-03: Balance expiry execution + policy-change cancellation

## Description

**Actor:** SYSTEM (scheduled, off-peak) for execution; CLIENT_ADMIN (via US-01's `PUT`) for cancellation.

**Steps (expire phase):**
1. For each `NOTIFIED` notice whose `scheduled_expiry_date ≤ today` **and** whose governing policy is still enabled:
2. Acquire a row lock on the wallet; read live `availableBalance`.
3. Write an immutable `EXPIRY` `LedgerEntry` (`referenceType="BALANCE_EXPIRY_NOTICE"`, `referenceId=notice.id`), reduce `availableBalance`, set the notice `EXPIRED` (+ `expired_at`, `expired_amount`, `ledger_entry_id`).
4. Emit `BALANCE_EXPIRED` afterCommit.

**Steps (cancellation, on disable/relax):**
1. `BalanceExpirationPolicyService.upsertPolicy` (US-01) detects a disable or relax and calls `BalanceExpiryNoticeService.cancelPendingForPolicy(policyId)`.
2. Pending `SCHEDULED`/`NOTIFIED` notices → `CANCELLED`; already-notified partners get `BALANCE_EXPIRY_CANCELLED`.

**Expected outcome:** Balances reduced exactly once per expiry event with an auditable ledger trail; relaxing/disabling a policy cleanly cancels pending expirations and re-notifies.

**Negative paths:**
- Wallet drained between warn and expire → expires only the remaining `availableBalance`.
- Policy disabled after warn → notice skipped (AC-4), not expired.

---

## Acceptance Criteria

- **AC-1:** Expiry writes an immutable `EXPIRY` `LedgerEntry`, reduces `availableBalance`, sets the notice `EXPIRED`, and emits `BALANCE_EXPIRED` (FR-09.5).
- **AC-2:** Idempotent — a re-run produces **no** second debit (guarded by `existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType` and the notice status), `step=balance_expiry_idempotent_skip` logged (FR-09.8).
- **AC-3:** The wallet is row-locked during expiry; only live, unreserved `availableBalance` is expired; `reservedBalance` is never touched (FR-09.11, ADR #3).
- **AC-4 (flow-gap):** The expire phase **re-verifies** the governing policy is still enabled at execution time and skips notices whose policy is no longer enabled.
- **AC-5:** Disabling or relaxing a policy cancels pending `SCHEDULED`/`NOTIFIED` notices and emits `BALANCE_EXPIRY_CANCELLED` to already-notified partners (FR-09.10).
- **AC-6:** Audit rows: expiry → `action=EXPIRED`, `resourceType=REWARD_WALLET`, **SYSTEM actor**; cancellation → `action=CANCELLED`, `resourceType=BALANCE_EXPIRATION_POLICY`.
- **AC-7 (carried from US-02 ready-check — 🔴 HIGH, security):** COMPANY-wallet notifications (`BALANCE_EXPIRING_SOON`/`BALANCE_EXPIRED`/`BALANCE_EXPIRY_CANCELLED`) MUST be scoped to the owning `partner_company_id` — the recipient resolution must NOT role-broadcast `PARTNER_ADMIN` across all partner companies. Fix the shared notification recipient resolution (used by all three balance-expiry notification types) so a company's expiry notice reaches only that company's admins. Add a test asserting Tenant A's company notice is never delivered to Tenant B / another partner company.
- **AC-8 (carried from US-02 ready-check — 🔴 HIGH, reliability):** A Kafka/notification send failure must NOT permanently suppress a notice. Do not treat `notified_at` as set unless delivery is confirmed; on send failure the notice remains re-sendable on the next sweep (e.g. `PENDING_DELIVERY` state or outbox/retry). Add a test: simulated send failure → `notified_at` stays null → next sweep retries.
- **AC-9 (carried from US-02 ready-check — 🟡 MED, perf):** Replace the per-wallet `findLastActivityAt` N+1 with a single bulk `GROUP BY` query over candidate wallets per (client, currency).
- **AC-10 (carried from US-02 ready-check — 🟡 MED, perf):** `findExpiryCandidateWallets` must be bounded (add `Pageable`/batch iteration) so a large tenant cannot load all wallets into memory at once.

> AC-7–AC-10 were surfaced by US-02's adversarial ready-check and deferred here because they live in the shared `BalanceExpiryBatchService` / `SchedulerBalanceExpirationRepository` / notification dispatch that US-03 extends. Address them in the BE tasks below.

---

## Out of Scope

- Breakage reporting + CSV export — **US-04**.
- The advance-notice warn phase — **US-02** (this story consumes its `NOTIFIED` notices).
- Rendering `EXPIRY` entries in partner transaction history — handled additively by existing F-05 history reads.
- Any FE surface (BE-only).

---

## Non-Functional Notes

- **Concurrency:** wallet debit uses `SchedulerBalanceExpirationRepository.lockWallet` (`SELECT … FOR UPDATE`) + `@Version`.
- **Telemetry:** `step=balance_expired` (walletId, currencyId, expiredAmount, ledgerEntryId), `step=balance_expiry_cancelled` (currencyId, cancelledCount), `step=balance_expiry_batch_finished`; metrics `balance_expiry.executed.total{currencyId,walletType}`, `balance_expiry.amount.total{currencyId}`, `balance_expiry.cancelled.total{currencyId}`, `balance_expiry.batch.duration_ms`.
- **Rollback safety:** `BALANCE_EXPIRED` emitted only `afterCommit` — never for a rolled-back expiry.

---

## Depends on

- **Foundation tasks:** F1, F2, F3 (`SchedulerBalanceExpirationRepository.lockWallet`, fixtures), F4
- **Prior stories:** US-01 (policy + the `upsertPolicy` hook to wire cancellation into), US-02 (`BalanceExpiryBatchService` + `NOTIFIED` notices exist)

---

## Spec references

- `## Functional Requirements` — FR-09.5, FR-09.8, FR-09.10, FR-09.11
- `## Data Model / Entities [BE]` — `BalanceExpiryNotice` lifecycle, reused `LedgerEntry` (`referenceType`/`referenceId`), `RewardWallet`
- `## Workflow / Status Transitions [BE + FE]` — notice NOTIFIED→EXPIRED / →CANCELLED
- `## Service Layer [BE]` — `BalanceExpiryBatchService` expire phase; `cancelPendingForPolicy`; row-lock + idempotency rules
- `## Domain Events [BE]` — `BALANCE_EXPIRED`, `BALANCE_EXPIRY_CANCELLED`
- `## Audit Trail [BE]` — expiry execution (SYSTEM), cancellation
- `technical.md → ## Repository Queries [BE]` — `lockWallet`, `LedgerEntryRepository.existsBy...EntryType`

---

## BE tasks [BE]

### BE-1: Expire-phase service + unit test
**Files:** `service/redemption/BalanceExpiryBatchService.java`, `test/.../service/redemption/BalanceExpiryBatchServiceTest.java`

Extend `runExpirySweep()` with the expire phase: select due `NOTIFIED` notices, re-check policy still enabled (AC-4), `lockWallet`, write `EXPIRY` ledger entry (idempotent via `existsBy...`), reduce `availableBalance`, mark `EXPIRED`. Unit test: happy expire, idempotent re-run (no double-debit), reserved-balance-protected, policy-disabled-after-warn skip, partial balance after concurrent reservation.

### BE-2: Cancellation service + unit test
**Files:** `service/redemption/BalanceExpiryNoticeService.java`, `test/.../service/redemption/BalanceExpiryNoticeServiceTest.java`

`cancelPendingForPolicy(policyId)` → `SCHEDULED`/`NOTIFIED` → `CANCELLED`; emit `BALANCE_EXPIRY_CANCELLED` for previously-`NOTIFIED` notices. Unit test: cancels both states, emits only for notified, no-op when none pending.

### BE-3: Wire cancellation into US-01's upsert
**Files:** `service/redemption/BalanceExpirationPolicyService.java` (modify `upsertPolicy` from US-01)

On disable (`enabled false`) or relax (lengthen inactivity / push fixed date / lengthen lead), call `cancelPendingForPolicy` and reset `enabled_at` on relax. (Forward-wiring into the US-01 method — US-01 left this as Out of Scope.)

### BE-4: Audit (programmatic)
`auditLogService.logAsync(EXPIRED, REWARD_WALLET, …, SYSTEM)` on each expiry; `CANCELLED, BALANCE_EXPIRATION_POLICY` on cancellation. See `spec.md → ## Audit Trail`.

### BE-5: Notification emission
Emit `BALANCE_EXPIRED` (expire) and `BALANCE_EXPIRY_CANCELLED` (cancel) afterCommit via `NotificationEventProducer`; payloads carry entity IDs + amount only (no PII). Producer unit test asserts type keys + payloads.

---

## E2E test [FE]

_BE-only story — no Playwright. Verified by `BalanceExpiryBatchServiceTest` / `BalanceExpiryNoticeServiceTest` (unit) and the T1 lifecycle, idempotency, reserved-balance, concurrent-redemption, cancel-on-relax, and audit/event integration scenarios in `test-plan.md`._

---

## Execution checklist

**BE session:**
- [ ] Expire phase: due `NOTIFIED` notices selected; policy-still-enabled re-check _(AC-4)_
- [ ] `lockWallet` row lock; only `availableBalance` expired; reserved untouched _(AC-3)_
- [ ] `EXPIRY` `LedgerEntry` written (ref BALANCE_EXPIRY_NOTICE/notice.id); `availableBalance` reduced; notice → EXPIRED _(AC-1)_
- [ ] Idempotent re-run guarded by `existsBy...` + status; `balance_expiry_idempotent_skip` logged _(AC-2)_
- [ ] `cancelPendingForPolicy` cancels SCHEDULED/NOTIFIED; emits `BALANCE_EXPIRY_CANCELLED` for notified _(AC-5)_
- [ ] Cancellation wired into `BalanceExpirationPolicyService.upsertPolicy` on disable/relax _(AC-5)_
- [ ] `@Audited`/programmatic audit: EXPIRED/REWARD_WALLET (SYSTEM), CANCELLED/BALANCE_EXPIRATION_POLICY _(AC-6)_
- [ ] `BALANCE_EXPIRED` emitted afterCommit, no PII _(AC-1)_
- [ ] `BalanceExpiryBatchServiceTest` + `BalanceExpiryNoticeServiceTest` pass _(AC-1, AC-2, AC-3, AC-4, AC-5)_

---

## Done when

1. **BE:** `./gradlew test` passes — expire-phase + cancellation unit tests green.
2. Every AC is referenced by at least one passing unit test (or its mapped T1 integration scenario).
