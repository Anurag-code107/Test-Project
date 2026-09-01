# Design: Balance-Expiration Blocking Fixes

- **Date:** 2026-06-29
- **Feature branch:** `features/reward-balance-expiration`
- **Status:** Approved design — pending implementation
- **Source:** Two blocking findings from the Codex adversarial review run during `/ready-check` (report: `.ready-check/features/reward-balance-expiration/review.json`, step `adversarial-review` = failed).

## Context

The feature passed every ready-check stage except the adversarial review, which surfaced two **high-severity, money-/trust-impacting** defects. Both were independently verified against the code before this design was written.

### Finding 1 — Expiry notifications broadcast tenant-wide

`NotificationDispatcher` (`service/NotificationDispatcher.java:60-91`) treats an **empty/null `targetUserIds`** as a *role broadcast*: it notifies every user in the tenant holding the notification type's seeded default roles. V33 seeds `BALANCE_EXPIRING_SOON` / `BALANCE_EXPIRED` / `BALANCE_EXPIRY_CANCELLED` for `PARTNER_SELLER` + `PARTNER_ADMIN`, and audience filtering only applies when `metadata.incentiveId` is present (it isn't here). Two methods return empty recipient lists:

- `BalanceExpiryBatchService.resolveRecipients(wallet)` — returns `List.of()` for **COMPANY** wallets (INDIVIDUAL wallets correctly return `[userId]`). Used for `BALANCE_EXPIRING_SOON` (warn) and `BALANCE_EXPIRED` (expire).
- `BalanceExpiryNoticeService.resolveRecipients()` — returns `List.of()` for **all** wallet types. Used for `BALANCE_EXPIRY_CANCELLED`. This leaks even INDIVIDUAL-wallet cancellations to the whole tenant.

Result: one company's (or one individual's) balance-expiry event notifies every partner seller/admin in the tenant. Confidentiality / trust-boundary failure.

### Finding 2 — Inactivity expiry never revalidates activity

`BalanceExpiryBatchService.processExpireForNotice(notice, policy)` (lines 426-491) locks the wallet, checks idempotency + zero-balance, then debits the full `availableBalance` to zero. It never re-checks last activity. The last-activity lookup happens only in the warn phase. A wallet that receives `CREDIT` / `DEBIT` / `RESERVE` / `RETURN_CREDIT` **after** the warning but before the scheduled date is still expired, destroying a valid balance and violating the inactivity semantics.

## Decisions (chosen during brainstorming)

1. **Finding 1 approach: local resolver.** Resolve recipients from the wallet in the backend; do **not** change the shared `NotificationEvent` record or `NotificationDispatcher`. Rationale: zero blast radius on other notification types and the cross-repo admin-backend consumer; matches the existing INDIVIDUAL pattern.
2. **COMPANY audience: PARTNER_ADMINs of the owning company only.** Matches the original code comment's intent; admins manage the company's balances.
3. **Finding 2 stale notice: cancel silently.** When revalidation shows the wallet is no longer due, mark the notice `CANCELLED`, skip the debit, emit no notification. The next sweep re-warns when the new inactivity window approaches.

## Design

### Fix 1 — `WalletNotificationRecipientResolver`

New `@Component` in `service/redemption`, injected into both producers. Single responsibility: map a wallet to the user IDs that should receive its balance-expiry notifications.

```
List<UUID> resolve(RewardWallet wallet):
  INDIVIDUAL && userId != null          -> [userId]
  COMPANY    && partnerCompanyId != null -> active PARTNER_ADMIN user ids of that company
  otherwise                              -> []   (unresolved)
```

**Caller contract — the load-bearing part of this fix:** because an empty `targetUserIds` is a *broadcast*, callers MUST NOT publish a directed event with an empty recipient list. After `resolve()`:

- recipients **non-empty** → publish as today.
- recipients **empty** → **do not publish**; log `WARN step=balance_expiry_recipients_unresolved walletId=… walletType=…` and increment metric `balance_expiry.recipients_unresolved.total`.

**Call sites updated:**
- `BalanceExpiryBatchService` warn (`BALANCE_EXPIRING_SOON`) and expire (`BALANCE_EXPIRED`). Note the warn path: if recipients are unresolved the warning is not sent, so the notice stays `SCHEDULED` and the balance is **not** expired (consistent with FR-09.7: never expire without a delivered warning).
- `BalanceExpiryNoticeService` cancellation (`BALANCE_EXPIRY_CANCELLED`). This service holds a `notice` (which has `walletId`) but not the wallet, so it loads the wallet via `RewardWalletRepository.findById` (verify availability) then calls the resolver.

**New repository method** on `UserRepository` (JPQL pinned, mirrors `findByClientIdAndBaseRoleNames`):

```java
@Query("""
    SELECT DISTINCT u FROM User u
    JOIN u.clientRole cr
    WHERE u.clientId = :clientId
    AND u.partnerCompanyId = :partnerCompanyId
    AND cr.baseRoleName = 'PARTNER_ADMIN'
    AND u.status = com.tenxengage.app.entity.enums.UserStatus.ACTIVE
    """)
List<User> findActivePartnerAdminsByCompany(@Param("clientId") UUID clientId,
                                            @Param("partnerCompanyId") UUID partnerCompanyId);
```

Avoids loading all company users. (`PARTNER_ADMIN` confirmed as the exact `base_role_name` string — `UserSeeder`, `RecommendationScoringService`.)

**Dispatcher interaction (verified):** providing explicit `targetUserIds` takes the dispatcher's *specific-user* path, which bypasses the V33 default-role seed (`PARTNER_SELLER,PARTNER_ADMIN`) — that seed only governs the broadcast path. The dispatcher still applies its per-user `notifications_enabled` / opted-out checks **and** a `clientId`-match filter to explicit recipients, so opt-outs and tenant isolation are preserved. The "PARTNER_ADMIN only" audience is therefore enforced by the resolver, not the seed.

### Fix 2 — Revalidate inactivity under the lock

In `processExpireForNotice`, **after** `lockWallet` + idempotency + zero-balance checks and **before** writing the `EXPIRY` ledger entry, for **INACTIVITY** policies only:

1. Re-fetch live last activity for this wallet+currency via a new single-wallet query `SchedulerBalanceExpirationRepository.findLastActivityAt(clientId, currencyId, walletId, activityTypes)` returning `Instant` (nullable), using the same `ACTIVITY_ENTRY_TYPES` set as the warn phase.
2. Recompute `freshExpiryDate = lastActivity.atZone(UTC).toLocalDate().plusDays(inactivityDays)` — identical to `computeScheduledExpiryDate`.
3. **Staleness test (corrected):** if `freshExpiryDate != notice.getScheduledExpiryDate()` → the inactivity clock moved (post-warning activity) → mark notice `CANCELLED`, skip the debit, return false, log `step=balance_expiry_revalidation_stale`. (Comparing to the notice's own date — not to `today` — is what's correct: the warning named a specific date, and the notice may only be honored for that exact date.)
4. **Null-activity edge (corrected):** if live last activity is `null`, that is a data anomaly (append-only ledger + a notice that could only have been created from prior activity) → **skip + log `WARN step=balance_expiry_revalidation_no_activity`**; do **not** expire.
5. Otherwise (`freshExpiryDate == scheduledExpiryDate`) → still inactive, warning was valid → expire as today.

**FIXED_DATE** policies skip revalidation entirely (the date is activity-independent).

## Non-goals / scope

- **No** changes to `NotificationEvent`, `NotificationDispatcher`, the Flyway migrations, or the OpenAPI contract (`contracts/endpoints/balance-expiration.yaml`).
- **No** edit to the blueprint feature spec (`../tenxengage-blueprint/features/reward-balance-expiration/spec.md`). It currently documents the pre-fix behavior; updating it is a separate, optional follow-up noted here.
- Performance N+1s (per-wallet recipient lookup; per-notice revalidation lookup) are accepted for correctness-first. Each loop iteration already does a blocking `publishAndConfirm` and/or a row lock + ledger writes, so one extra indexed SELECT is negligible. Possible follow-ups: a per-sweep `company -> admins` cache and a bulk revalidation query.

## Files touched

- **New:** `service/redemption/WalletNotificationRecipientResolver.java` (+ `WalletNotificationRecipientResolverTest`)
- `service/redemption/BalanceExpiryBatchService.java` — use resolver in warn + expire event sites; skip-publish-on-empty; revalidation in `processExpireForNotice`
- `service/redemption/BalanceExpiryNoticeService.java` — use resolver (load wallet); skip-publish-on-empty
- `repository/SchedulerBalanceExpirationRepository.java` — add `findLastActivityAt` single-wallet query
- `repository/UserRepository.java` — add `findActivePartnerAdminsByCompany`
- Tests: `BalanceExpiryBatchServiceTest`, `BalanceExpiryNoticeServiceTest`, new resolver test, `BalanceExpirationLifecycleIT`

## Test plan (TDD — write failing tests first)

**Fix 1**
- `WalletNotificationRecipientResolverTest`: INDIVIDUAL → `[owner]`; COMPANY → company PARTNER_ADMIN ids; COMPANY with no admins / null ids → `[]`.
- `BalanceExpiryBatchServiceTest`: COMPANY warn+expire events carry the admin ids (non-empty); unresolved → no publish (verify producer not called) + metric.
- `BalanceExpiryNoticeServiceTest`: cancellation recipients scoped per wallet type; unresolved → no publish.

**Fix 2**
- `BalanceExpiryBatchServiceTest`: INACTIVITY, post-warning activity (freshExpiryDate ≠ scheduled) → notice `CANCELLED`, **no** `EXPIRY` ledger entry, balance untouched; still-inactive (equal dates) → expires; null live activity → skip + no debit; FIXED_DATE → expires regardless of activity.

**Integration (`BalanceExpirationLifecycleIT`)** — the two regressions Codex requested:
- warn → wallet activity → expire sweep → balance **not** expired.
- COMPANY-wallet expiry → only that company's admins are targeted (no tenant-wide broadcast).

## Verification items (verified against the code 2026-06-29)
1. **Verified safe.** Warn-phase find-or-create (`findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate`) keys on `(wallet, currency, scheduledExpiryDate)` and skips when `notifiedAt != null`. A cancelled stale notice keeps its past date; the recomputed inactivity date only moves forward, so that exact date can never recur — no suppression of a future re-warn. The expire-phase query filters `status = NOTIFIED`, so a `CANCELLED` notice is never re-picked.
2. **Verified.** `RewardWalletRepository extends JpaRepository<RewardWallet, UUID>` → `findById` available. The cancel path runs in a request transaction with `TenantContext` set, so the load is tenant-scoped (correct; notice and wallet share the tenant).
3. **Verified.** `PARTNER_ADMIN` is the exact `base_role_name` string (`UserSeeder`, `RecommendationScoringService`). V33 seeds the three balance notification types with default roles `PARTNER_SELLER,PARTNER_ADMIN` (broadcast-path only; irrelevant to the directed-recipient fix).

## Residual risks (accepted)
- The revalidation SELECT reads `ledger_entries` while the pessimistic lock is on the `reward_wallets` row, so a credit landing between revalidation and the EXPIRY write is not strictly serialized by this lock. Narrow window; far better than today's zero-revalidation; the grace window covers the common case.
