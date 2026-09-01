---
id: US-02
title: "Advance-expiry notification engine"
layers: ["BE"]
touches_entities: ["BalanceExpiryNotice", "BalanceExpirationPolicy", "RewardWallet", "LedgerEntry"]
depends_on_stories: ["US-01"]
seed_id: "F-09.S-02"
---

# US-02: Advance-expiry notification engine

## Description

**Actor:** SYSTEM (scheduled, off-peak)
**Trigger:** the scheduled expiry sweep's **warn phase** runs.

**Steps:**
1. Load all enabled, non-deleted policies across tenants (cross-tenant sweep).
2. For each policy, find candidate wallets for that currency with `availableBalance > 0`, binding `client_id` explicitly per wallet.
3. Compute last activity (ledger-derived) and the `scheduled_expiry_date` per mode.
4. If the balance is entering the lead window **and** the grace window has passed, create/advance a `BalanceExpiryNotice` to `NOTIFIED` and send `BALANCE_EXPIRING_SOON` — once.

**Expected outcome:** Each at-risk wallet+currency has exactly one `NOTIFIED` notice and one advance notification per expiry event; nothing is debited yet.

**Negative paths:**
- Notification send failure → `notified_at` is **not** set → the next sweep retries the warn (a partner is never expired without a delivered notice).

---

## Acceptance Criteria

- **AC-1:** Candidate detection works per mode — `INACTIVITY`: `lastActivityAt + inactivityDays ≤ today` where `lastActivityAt = MAX(created_at)` over `entry_type ∈ {CREDIT, DEBIT, RESERVE, RETURN_CREDIT}`; `FIXED_DATE`: `today ≥ fixedExpiryDate` (FR-09.1).
- **AC-2:** Advance notice is sent **exactly once** per expiry event — guarded by `notified_at IS NULL`; the marker is set in the same transaction so retries don't double-notify (FR-09.4).
- **AC-3:** Grace window — nothing is notified until at least one full `leadTimeDays` has elapsed since `enabled_at`; no retroactive notice (FR-09.7).
- **AC-4:** Currencies with no enabled policy (incl. cash by default) are skipped (FR-09.2, FR-09.3).
- **AC-5:** Only `availableBalance` is considered; wallets with non-positive available balance produce no notice (ADR #3).
- **AC-6:** The notice is idempotent on the unique `(wallet_id, currency_id, scheduled_expiry_date)` key — a re-run creates no duplicate notice (FR-09.8).
- **AC-7:** The cross-tenant sweep binds `client_id` per wallet (via `SchedulerBalanceExpirationRepository`, no ambient `TenantContext`) — no cross-tenant leakage.

---

## Out of Scope

- The expiry debit / balance reduction and `BALANCE_EXPIRED` — **US-03**.
- Cancellation on disable/relax and `BALANCE_EXPIRY_CANCELLED` — **US-03**.
- Any FE surface (this story is BE-only); rendering notices in the partner inbox is handled by the existing notification framework.

---

## Non-Functional Notes

- **Scheduling:** off-peak `@Scheduled` sweep; `featureArea="balance-expiration"`, MDC actor `SYSTEM`.
- **Telemetry:** log `step=balance_expiry_warned` (walletId, currencyId, scheduledExpiryDate, amount) and `step=balance_expiry_batch_started`; metric `balance_expiry.warned.total{currencyId}`.
- **Producer hardening:** harden `event/NotificationEventProducer.publish()` to observe the `kafkaTemplate.send()` future with `.whenComplete(...)`; emit from `TransactionSynchronizationManager.afterCommit`.

---

## Depends on

- **Foundation tasks:** F1, F2, F3 (entities, `SchedulerBalanceExpirationRepository`, fixtures), F4 (notification_types seed)
- **Prior stories:** US-01 (enabled `BalanceExpirationPolicy` rows must exist to sweep)

---

## Spec references

- `## Functional Requirements` — FR-09.1, FR-09.2, FR-09.3, FR-09.4, FR-09.7, FR-09.8
- `## Data Model / Entities [BE]` — `BalanceExpiryNotice`, ledger-derived last-activity rule, reused `RewardWallet`/`LedgerEntry`
- `## Service Layer [BE]` — `BalanceExpiryBatchService.runExpirySweep()` warn phase; business rules (grace, once-only, opt-in)
- `## Domain Events [BE]` — `BALANCE_EXPIRING_SOON`, afterCommit, recipient resolution, once-only
- `## Observability [BE]` — `step` values + metrics
- `technical.md → ## Repository Queries [BE]` — `SchedulerBalanceExpirationRepository.{findAllByEnabledTrueAndDeletedFalse, findExpiryCandidateWallets, findLastActivityAt}`

---

## BE tasks [BE]

### BE-1: Warn-phase service + unit test
**Files:** `service/redemption/BalanceExpiryBatchService.java`, `test/.../service/redemption/BalanceExpiryBatchServiceTest.java`

Implement `runExpirySweep()` warn phase: iterate enabled policies (cross-tenant), find candidate wallets, compute last-activity + `scheduledExpiryDate`, apply grace window, upsert `BalanceExpiryNotice` SCHEDULED→NOTIFIED, set `notified_at`/`notified_amount`. Unit test (Mockito) covers: INACTIVITY candidate, FIXED_DATE candidate, grace-window skip, once-only (no duplicate notice on re-run), cash/disabled skip, zero/non-positive available balance skip.

### BE-2: Notification producer wiring + producer test
**Files:** `event/NotificationEventProducer.java` (harden `.whenComplete`), `service/redemption/BalanceExpiryBatchService.java`, `test/.../service/redemption/BalanceExpiryBatchServiceTest.java`

Emit `BALANCE_EXPIRING_SOON` afterCommit with payload `{currencyId, amount, scheduledExpiryDate, walletId}` (no PII); recipient = wallet owner (individual → `user_id`; company → `PARTNER_ADMIN` via `partner_company_id`). Producer unit test asserts type key + payload fields + that no event is sent when `notified_at` was already set.

### BE-3: Scheduler registration
**Files:** `service/redemption/BalanceExpiryBatchService.java` (`@Scheduled` off-peak cron) — or the project's scheduling config.

Bind `client_id` per wallet explicitly; set MDC `featureArea`, `tenantId`, `userId=SYSTEM`; emit `step=balance_expiry_batch_started`/`balance_expiry_warned` logs.

---

## E2E test [FE]

_BE-only story — no Playwright. Behavior is verified by `BalanceExpiryBatchServiceTest` (unit) and the T1 lifecycle / grace-window / once-only / tenant-isolation integration tests in `test-plan.md`._

---

## Execution checklist

**BE session:**
- [ ] `BalanceExpiryBatchService.runExpirySweep()` warn phase implemented _(AC-1, AC-2, AC-3, AC-4, AC-5)_
- [ ] Candidate detection uses ledger-derived `findLastActivityAt` over activity entry types _(AC-1)_
- [ ] Grace-window guard against `enabled_at + leadTimeDays` _(AC-3)_
- [ ] `BalanceExpiryNotice` upsert idempotent on unique event key _(AC-6)_
- [ ] Cross-tenant sweep binds `client_id` per wallet via `SchedulerBalanceExpirationRepository` _(AC-7)_
- [ ] `NotificationEventProducer.publish()` hardened with `.whenComplete`; `BALANCE_EXPIRING_SOON` emitted afterCommit, no PII _(AC-2)_
- [ ] `BalanceExpiryBatchServiceTest` passes (INACTIVITY, FIXED_DATE, grace skip, once-only, cash/disabled skip, zero-balance skip, producer fires once) _(AC-1, AC-2, AC-3, AC-4, AC-5, AC-6)_

---

## Done when

1. **BE:** `./gradlew test` passes — `BalanceExpiryBatchServiceTest` (warn phase) green.
2. Every AC is referenced by at least one passing unit test (or the T1 integration scenarios it maps to).
