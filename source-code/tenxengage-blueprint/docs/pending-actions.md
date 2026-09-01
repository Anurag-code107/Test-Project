# Pending Actions

Items that need a follow-up but are deferred. Each entry has a date, context, and exact action needed.

---

## DB: Grant action.redemption.catalog.manage to CLIENT_ADMIN

**Feature:** F-02 — Redemption Catalog (`features/redemption-catalog`)
**Date:** 2026-05-18
**Approved by:** Vijay Kandiraju
**Context:** TENX_ADMIN cannot log into tenxengage-frontend (clientId check blocks it), so
GlobalCatalogAdminPage is unreachable locally for TENX_ADMIN. Vijay approved granting
`action.redemption.catalog.manage` to CLIENT_ADMIN for now — can be revoked later if not needed.
**Current state:** Added manually to DB. V12 migration does NOT have this grant.
**Why not visible in role management UI:** V12 defines this permission with `scope = 'PLATFORM'`.
The role management UI only shows `INTERNAL` and `EXTERNAL` scoped permissions — `PLATFORM` is
filtered out. This is why "View Role → Client Admin" shows 104/105 enabled instead of 105/105,
and `action.redemption.catalog.manage` does not appear in the REDEMPTION ACTIONS section.
**Action needed:** Add migration `V22__grant_catalog_manage_to_client_admin.sql` with 3 steps:
1. `UPDATE permissions SET scope = 'INTERNAL' WHERE permission_key = 'action.redemption.catalog.manage'`
   — makes it visible in the role management UI
2. `INSERT INTO client_role_permissions` for `base_role_name = 'CLIENT_ADMIN'`
   — formal role grant (replaces manual DB entry)
3. `INSERT INTO client_permission_grants` for Acme tenant `a0000000-0000-0000-0000-000000000001` (dev seed)
**Expected result after V22:** UI shows 105/105 enabled for CLIENT_ADMIN; `action.redemption.catalog.manage`
appears in REDEMPTION ACTIONS panel; no manual DB entry needed.
**Risk:** Any fresh DB setup is missing this permission until V22 is added.

---

## US-03 Advisory: Wallet credit / return state transition race in processVendorConfirmation

**Feature:** Redemption Returns (`features/redemption-returns`)
**Story:** US-03 BE — Xoxoday vendor integration
**Date:** 2026-06-13
**Source:** Codex adversarial review (confidence 0.95)
**Context:** `ReturnService.processVendorConfirmation` calls `walletMutationDelegate.doReturnCreditInTx(...)` in a `REQUIRES_NEW` transaction before the outer `@Transactional` saves `RETURN_CONFIRMED`. If the outer tx rolls back (e.g., `OptimisticLockException` from a concurrent webhook), the wallet credit is already committed but the return stays non-confirmed. Demoted to advisory because `adversarial-review` is in `--soft-stages` for US-03.
**Action needed:** Load the return with a pessimistic lock before calling `doReturnCreditInTx`. Alternatively, commit state transition first and enqueue wallet credit as an outbox event.
**File:** `src/main/java/com/tenxengage/app/service/redemption/ReturnService.java` lines 382–485

---

## US-03 Advisory: @Audited(COMPLETED) on webhook + resolve endpoints fires for REJECT paths

**Feature:** Redemption Returns (`features/redemption-returns`)
**Story:** US-03 BE — Xoxoday vendor integration
**Date:** 2026-06-13
**Context:** `ReturnWebhookController.handleReturnWebhook` is annotated `@Audited(action = "COMPLETED")` but handles both vendor confirm and vendor reject outcomes. `ReturnAdminController.resolveTimedOutReturn` has the same annotation but handles both CONFIRM and REJECT resolution. In both cases, REJECT paths record an incorrect COMPLETED audit action.
**Action needed:** Remove `@Audited` from both controller methods; emit programmatic audit events inside `ReturnService.processVendorConfirmation()` and `ReturnService.resolveTimedOut()` branching on the outcome.
**Files:**
- `src/main/java/com/tenxengage/app/controller/ReturnWebhookController.java` lines 79–83
- `src/main/java/com/tenxengage/app/controller/redemption/ReturnAdminController.java` lines 165–170

---

## US-03 Advisory: @Profile silently ignored on @PostConstruct validateConfig in ReturnVendorService

**Feature:** Redemption Returns (`features/redemption-returns`)
**Story:** US-03 BE — Xoxoday vendor integration
**Date:** 2026-06-13
**Context:** `ReturnVendorService.validateConfig()` is annotated `@PostConstruct` and `@Profile("!test & !localtest")`. Spring only honours `@Profile` on `@Bean` and `@Configuration` class declarations — on plain `@PostConstruct` methods inside a `@Service`, the annotation is silently ignored. The validation runs in every profile, including test/localtest.
**Action needed:** Either extract to a `@Configuration` class guarded by `@Profile`, or replace with a programmatic profile check: `if (env.acceptsProfiles(Profiles.of("!test", "!localtest"))) { ... }`.
**File:** `src/main/java/com/tenxengage/app/service/redemption/ReturnVendorService.java` lines 75–82

---

## US-03 Advisory: Kafka event published before commit in processVendorConfirmation

**Feature:** Redemption Returns (`features/redemption-returns`)
**Story:** US-03 BE — Xoxoday vendor integration
**Date:** 2026-06-13
**Context:** `ReturnService.processVendorConfirmation` calls `returnEventProducer.publishReturnConfirmed(ret)` directly inside the `@Transactional` boundary. `approveReturn()` correctly uses `TransactionSynchronizationManager.afterCommit()`. This inconsistency means a DB commit failure after the Kafka publish leaves consumers with a `RETURN_CONFIRMED` event for a return that was never saved as confirmed.
**Action needed:** Wrap the `publishReturnConfirmed` call in `TransactionSynchronizationManager.afterCommit(() -> returnEventProducer.publishReturnConfirmed(ret))` to match `approveReturn()`.
**File:** `src/main/java/com/tenxengage/app/service/redemption/ReturnService.java` lines 382–485

---
