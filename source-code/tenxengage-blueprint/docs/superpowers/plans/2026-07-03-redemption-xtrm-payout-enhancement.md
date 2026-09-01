# Redemption XTRM Payout & Enrollment — Enhancement Implementation Plan

> **Type:** Enhancement to **F-03 Redemption Flow** (`features/redemption-flow`) — not a new feature. Roadmap stays at 9 features.
> **Branch (all repos):** `features/redemption-xtrm-payout-enhancement` (off `roadmaps/redemption-store`)
> **Date:** 2026-07-03

---

## Context — why now

F-03 (Redemption Flow) chartered "XTRM + Xoxoday routing, all 3 processing modes, vendor webhooks", but the **XTRM cash payout was parked** ("TransferFund API broken", US-05 / S-06 blocked). Hands-on XTRM sandbox testing (2026-06→07) established the correct integration and surfaced one genuinely new requirement:

1. **Wrong rail (correction):** the current `XtrmVendorService` sends a **digital-gift-card SKU on `XTR94505` (Digital Gift Cards)**. Cash must pay via **AnyPay Individual `XTR94502`** using `TransferFund` (INSTANT / APPROVAL modes) or `BatchTransfer` (BATCH mode). Confirmed working end-to-end in sandbox.
2. **New scope (enrollment):** XTRM requires each payee to be **enrolled** (`CreateUser` → returns a `PAT` recipient id) before payout. F-03 assumed "identity passed at call time, not stored" — which does **not** match how XTRM works. So we must enroll users and store the `PAT`.
3. **Optional bank/ACH:** users may link a bank (`LinkBankBeneficiary` → `UserLinkedBankID`) for the Bank rail.

Money lands in the recipient's XTRM **AnyPay wallet** (auto-created); the recipient self-withdraws to bank/card inside XTRM. XTRM owns KYC/AML/OFAC natively; TenXEngage stores **no** bank/card numbers — only XTRM reference ids.

This enhancement **completes and corrects F-03's XTRM cash payout** and adds enrollment + optional bank linking.

---

## Decisions locked (2026-07-03 review) — authoritative; supersede any looser wording below

1. **Enrollment scope = `PARTNER_SELLER` + `PARTNER_ADMIN` only** (payee roles). CLIENT_ADMIN / ACTIVITY_APPROVER are NOT enrolled (they don't redeem — enrolling them wastes XTRM `CreateUser` calls). Eager enroll at profile completion **for these roles**, with lazy `ensureEnrolledForPayout` as the fallback.
2. **Address is REQUIRED — CONFIRMED by testing (2026-07-03).** XTRM `CreateUser` rejects requests without an address, so `Address` (min: `AddressLine1` + `CountryISO2`) is mandatory. This is **new scope beyond the BRD** (BRD listed only name/email/country, passed at call time — F-03 brief L43). Resolution:
   - **Collect a full address** (line1, line2, city, region, postal, country) on the profile / Payout tab. `AddressLine1` + `countryIso2` are **required to enroll** a payee; the rest are optional but recommended (the Bank rail's `LinkBankBeneficiary` needs the full address).
   - **Store the address on `user_redemption`** (V34 adds `address_line1/2`, `city`, `region`, `postal_code`, `country_iso2`) — **NOT** the core `users` table (which has only `phone` + `country_code`).
   - **Address is PII** stored by this feature (bounded — no SSN/DOB/bank numbers); subject to GDPR erasure with the user. See Data Retention.
   - **Enrollment gating:** a payee must have `AddressLine1` + country before enrollment can succeed. Pre-existing payees without an address are prompted to complete their payout profile before their first payout (lazy enroll fails cleanly per FR-09 until then).
3. **Company-wallet payout (Partner Admin) is DEFERRED.** v1 delivers **individual-wallet** cash payout (user `PAT` via AnyPay Individual `XTR94502`). COMPANY-wallet cash payout needs a **company beneficiary** (`CreateCompanyBeneficiary` → `SPN`, AnyPay Company `XTR94504`) — added later (see Future Extensions). **Keep dispatch branching by `RedemptionRequest.walletType` now** so this slots in additively.

### Future Extension — company beneficiary (easy, additive — confirmed feasible)

Adding company-wallet payout later is a parallel track, not a rework:
- Dispatch already branches on `walletType` (INDIVIDUAL vs COMPANY) → route COMPANY → company beneficiary, INDIVIDUAL → user PAT.
- Store the company `SPN` (from `CreateCompanyBeneficiary`) on `PartnerCompany` or a small `company_redemption` table, parallel to `user_redemption`.
- Add `CreateCompanyBeneficiary` client method + `AnyPay Company (XTR94504)` to the payout-method map/dispatch.
- Individual path untouched. The only "keep the door open" requirement for v1: dispatch must branch by `walletType` and not hard-assume individual.

---

## Scope

**In (v1):**
- XTRM user enrollment (`CreateUser`) at first profile completion — idempotent; lazy backfill for pre-existing users; non-blocking on failure with retry.
- New `user_redemption` record: platform `userId` → XTRM `recipientUserId` (PAT) + payout-method preference + `userLinkedBankId` + enrollment status/identity level.
- AnyPay Individual (`XTR94502`) payout: `TransferFund` (INSTANT/APPROVAL), `BatchTransfer` (BATCH); per-item batch reconciliation.
- Optional bank/ACH linking (`LinkBankBeneficiary`) on a profile tab; `XTR94500` Bank rail.
- Webhook reconciliation of payout status → redemption status + ledger finalize (debit on success, release on failure).
- KYC send-limit handling (friendly message + balance release).
- `XtrmVendorService` refactor; config default `XTR94505` → `XTR94502`.

**Out (v1, deferred):** Prepaid Virtual Debit Card (`XTR94503`), Bank Check (`XTR94507`), Digital Gift Cards (`XTR94505`), Rapid Transfer / push-to-card (`XTR94508`, needs card tokenization + PCI); AnyPay Company (`XTR94504` / `SPN`); `UserWithdrawFund` (recipient does this in XTRM); multiple linked banks; non-USD; raising KYC limits in-app; `GetDigitalGiftCards` catalog seeding; **syncing post-enrollment address/identity changes back to XTRM** — enrollment is one-time (`CreateUser`); later profile/address edits are saved on `user_redemption` but NOT pushed to XTRM (no `UpdateUser` call in v1).

---

## Pre-requisite — sync the enhancement branch across the 4 repos

Create/checkout `features/redemption-xtrm-payout-enhancement` in each repo, based on that repo's `roadmaps/redemption-store` (blueprint) / redemption-flow base:

```bash
# blueprint (done)
git -C tenxengage-blueprint checkout features/redemption-xtrm-payout-enhancement
# backend / frontend / contracts
for r in tenxengage-backend tenxengage-frontend tenxengage-contracts; do
  git -C $r checkout -b features/redemption-xtrm-payout-enhancement 2>/dev/null || git -C $r checkout features/redemption-xtrm-payout-enhancement
done
```

---

## PHASE 1 — Planning (self-contained; NO changes to existing blueprint docs)

**Decision:** this enhancement is kept **entirely in this plan doc**. We do **NOT** amend `features/redemption-flow`'s reviewed `spec.md` / `technical.md` / `stories.md` / `tracker.md`, and we create **no** new `features/` folder (roadmap stays 9).

- **Design reference = Appendix A** (full spec detail) in this doc.
- **Technical reference = Appendix B** (full technical detail) in this doc.
- `features/redemption-flow` is **read-only context** — implementers read this plan alongside it.
- Execution is driven by the phased tasks below + **Done When**; there are no separate story files or tracker rows to add.

### Existing-code footprint (unavoidable — this IS the feature; kept minimal)

The payout fix cannot be delivered without editing a few **existing code** files. Keep each change surgical; everything else is brand-new files.

| Existing file | Minimal change | Why unavoidable |
|---|---|---|
| `service/XtrmVendorService.java` | swap gift-card `TransferFund` (SKU on `XTR94505`) → AnyPay `XTR94502` `TransferFund` + add `dispatchBatch`; drop `SKU`/`UserGiftCardEmailID`; recipient = stored PAT | it currently sends the **wrong** payload — this is the core fix |
| XTRM webhook handler (existing) | add reconciliation of `TransferFund`/`BatchTransfer` txn refs → redemption status + ledger | payout finalization |
| Profile-completion / onboarding service (existing) | after completion, capture address + call `enrollIfNeeded` (payee roles) | enrollment trigger |
| `application-local.yml` / env | `redemption.xtrm.payment-method-id` default `XTR94505` → `XTR94502` | cash rail |
| `entity/enums/AuditAction.java`, `AuditResourceType.java` | add `ENROLLED`, `BANK_LINKED`, `BANK_UNLINKED`, `USER_REDEMPTION` | audit |
| `RedemptionOrchestrationService.java` | remove stale "stubs" comment (no logic change) | cleanup |

**Everything else is NEW files:** `UserRedemption` entity + repo + fixtures, `XtrmEnrollmentService`, `XtrmBankService`, `XtrmApiClient` (+ stub), `RedemptionProfileController`, request/response DTOs, V34 migration, FE `redemption-payout` components/hooks/types. To keep the `XtrmVendorService` edit minimal, put the new AnyPay/batch payload-building in the new `xtrm` services and have `XtrmVendorService.dispatch` delegate.

---

## PHASE 2 — Backend (`tenxengage-backend`)

### Task 4 — Flyway `V34__create_user_redemption_table.sql`

```sql
CREATE TABLE user_redemption (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           UUID          NOT NULL REFERENCES clients(id),
    user_id             UUID          NOT NULL REFERENCES users(id),
    recipient_user_id   VARCHAR(50)   NULL,                       -- XTRM PAT (from CreateUser)
    enrollment_status   VARCHAR(30)   NOT NULL DEFAULT 'NOT_ENROLLED',
    enrollment_error    VARCHAR(500)  NULL,                       -- sanitized, no PII
    identity_level      VARCHAR(30)   NULL,                       -- XTRM AccountIdentityLevel
    address_line1       VARCHAR(255)  NULL,                       -- required to enroll (payee); PII
    address_line2       VARCHAR(255)  NULL,
    city                VARCHAR(120)  NULL,
    region              VARCHAR(120)  NULL,
    postal_code         VARCHAR(20)   NULL,
    country_iso2        VARCHAR(2)    NULL,                       -- required to enroll; 2-letter ISO
    payout_method       VARCHAR(30)   NOT NULL DEFAULT 'ANYPAY',
    user_linked_bank_id VARCHAR(100)  NULL,                       -- XTRM BeneficiaryId (ref only)
    linked_bank_label   VARCHAR(100)  NULL,                       -- masked display label
    enrolled_at         TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX        idx_user_redemption_client_id     ON user_redemption(client_id);
CREATE UNIQUE INDEX uq_user_redemption_user_id        ON user_redemption(user_id);
CREATE INDEX        idx_user_redemption_client_status ON user_redemption(client_id, enrollment_status);
```
(No `deleted` column — 1:1 system record. Latest migration was V33.)

### Task 5 — Enums (`entity/enums/xtrm/`)
- `XtrmEnrollmentStatus` = `NOT_ENROLLED | ENROLLED | FAILED`
- `RedemptionPayoutMethod` = `ANYPAY | BANK`

### Task 6 — Entity + repo + fixtures
- `entity/xtrm/UserRedemption` (extends `BaseEntity`, implements `TenantAware`, `@Filter`, `@Version`).
- `repository/xtrm/UserRedemptionRepository`: `findByUserIdAndClientId`, `existsByUserIdAndClientId`, `findByClientIdAndEnrollmentStatus(...)` (backfill; ordered).
- `testdata/xtrm/UserRedemptionFixtures`.

### Task 7 — XTRM client + services (`service/xtrm/`)
- `XtrmApiClient` — `createUser`, `transferFund`, `batchTransfer`, `linkBankBeneficiary`; reuse the existing OAuth token cache + `RestClient` + `onStatus` parse pattern. `XtrmApiClientStub` = `@Profile({"local","test"})` (canned responses; never prod).
- `XtrmEnrollmentService` — `enrollIfNeeded(userId)` (idempotent; XTRM call outside `@Transactional`), `ensureEnrolledForPayout(userId)` (lazy; throws `XTRM_NOT_ENROLLED`), `getOrCreateProfile(userId)`; lazy backfill sweep per-tenant.
- `XtrmBankService` — `linkBank`, `removeBank`, `setPayoutMethod` (rejects `BANK` when no bank linked → `BANK_NOT_LINKED`).

### Task 8 — Refactor `XtrmVendorService`
- `dispatch(request)`: build `TransferFund` — `PaymentMethodID=XTR94502` (or `XTR94500`+`userLinkedBankId` when method=BANK), `WalletID`=issuer source wallet (config `203871`), `RecipientUserID`=`ensureEnrolledForPayout(userId)`; **drop `SKU` + `UserGiftCardEmailID`**.
- `dispatchBatch(requests)`: one `BatchTransfer` (`SourceWalletId`=203871, `Items[]`), reconcile per-item accepted/rejected → each redemption.
- Send-limit / not-enrolled failures → release reserved balance + friendly reason. Remove stale "stubs" comment in `RedemptionOrchestrationService`.

### Task 9 — `RedemptionProfileController` (`/api/v1/redemption/profile`)
- `GET /profile` → `RedemptionProfileResponse`
- `PUT /profile/payout-method` (`SetPayoutMethodRequest`)
- `POST /profile/bank-account` (`LinkBankAccountRequest`) · `DELETE /profile/bank-account`
- All self-only (resolve `user_redemption` from JWT — no id in path/body). `@RequiresPermission({action.redemption.redeem, action.redemption.redeem_company}, ANY)`.

### Task 10 — Profile-completion hook + address capture
- Collect the address on the Payout profile (line1 + country required; captured for `PARTNER_SELLER` / `PARTNER_ADMIN`), persist to **`user_redemption`** (address columns), then call `enrollIfNeeded(userId)` (non-blocking; failure → status FAILED, retry later). Only payee roles are enrolled. A payee without line1 + country cannot enroll — prompt them to complete the Payout profile before first payout.

### Task 11 — Webhook reconciliation
- Extend the existing XTRM webhook handler: map `TransferFund`/`BatchTransfer` transaction refs → redemption request; transition status; ledger debit (success) / release (failure); idempotent by transaction id.

### Task 12 — Config
- `redemption.xtrm.payment-method-id` default `XTR94505` → `XTR94502` (`application-local.yml` / `XTRM_PAYMENT_METHOD`).

### Task 13 — Audit
- Add `AuditAction.{ENROLLED, BANK_LINKED, BANK_UNLINKED}`, `AuditResourceType.USER_REDEMPTION`; `@Audited` on bank-account POST/DELETE + payout-method PUT; enrollment audited from the service.

---

## PHASE 3 — Contracts (`tenxengage-contracts`)

- Endpoints: `/redemption/profile`, `/redemption/profile/payout-method`, `/redemption/profile/bank-account` (POST/DELETE).
- Models: `RedemptionProfileResponse` (enrollmentStatus, payoutMethod, bankLinked, linkedBankLabel, identityLevel — **no** recipientUserId / userLinkedBankId / clientId), `LinkBankAccountRequest`, `SetPayoutMethodRequest`.
- Enums: `XtrmEnrollmentStatus`, `RedemptionPayoutMethod`; document new `AuditAction`/`AuditResourceType` values in `enums.md` + bump `enums-index.md` counts.

---

## PHASE 4 — Frontend (`tenxengage-frontend`)

- **`MyProfilePage`** — add address section (line1/line2/city/region/postal/country; line1 + country required, inline validation) and a URL-driven **Payout tab**.
- **`PayoutTab`** — enrollment status (Ready / Pending / Action needed), payout method radio (AnyPay default / Bank; Bank without a linked bank prompts to link), linked-bank masked label + Remove/Replace.
- **`LinkBankForm`** — bank + address fields; `POST /redemption/profile/bank-account`; surface XTRM 422 (duplicate bank / invalid) inline.
- **Hooks** — `useRedemptionProfile()` (+ `retry:false`), `useLinkBankAccount` / `useRemoveBankAccount` / `useSetPayoutMethod` (invalidate `['redemption-profile', userId]`). Error-code map for `BANK_NOT_LINKED`, `XTRM_SEND_LIMIT`, `XTRM_NOT_ENROLLED`, duplicate-bank.
- Types copied from contracts under `types/redemption-payout/`.

---

## Done When

> **Status (updated 2026-07-06).** Implemented end-to-end across backend, contracts, and frontend on
> `features/redemption-xtrm-payout-enhancement` in each repo; per-repo ready-check run (PASS-WITH-FINDINGS,
> all HIGH/MEDIUM resolved). Verified here by unit/`@WebMvcTest`/compile/build only — **no live-stack or
> XTRM-sandbox run** (no Postgres/Docker in this env), so DB migration apply, real dispatch/webhook, and
> Playwright are still pending. One item below is intentionally **not** met as originally worded (see ⚠ — grouped BatchTransfer).

- [x] Enhancement kept **self-contained in this plan** — no changes to `redemption-flow`'s reviewed spec/technical/stories/tracker; no new `features/` folder (roadmap stays 9).
- [x] V34 `user_redemption` migrates cleanly (latest was V33); enums added. — _migration + enums authored & committed; clean-apply on a live DB not run in this env (no Postgres)._
- [x] Enrolling a test user at profile completion creates a `user_redemption` row `ENROLLED` with a `recipient_user_id`; re-enroll is a no-op; failure is non-blocking + retried. — _unit-tested (`XtrmEnrollmentServiceTest`) incl. idempotent no-op, non-blocking failure, lazy retry; captured via `PUT /redemption/profile/address` (authenticated, payee-only) rather than the token onboarding wizard. Live-stack/sandbox e2e pending._
- [x] INSTANT cash redemption dispatches `TransferFund` (AnyPay `XTR94502`, no SKU) to the user's PAT; webhook flips status to COMPLETED + debits ledger; not-enrolled / send-limit failures release the reserved balance. — _Dispatch (AnyPay `XTR94502`, no SKU, recipient = stored PAT) + webhook → COMPLETED/DEBIT done & unit-tested. Per the H1 fix, CASH INSTANT dispatches **after commit** (starts `PROCESSING`, webhook finalizes). Failure handling splits by kind: **definitive** rejections (not-enrolled / send-limit / payout-rejected) **release the reservation + mark FAILED** (FR-06/FR-09); **ambiguous/transient** (XTRM unreachable) hold `PROCESSING` for reconciliation (no double-pay). Live-stack/sandbox e2e pending._
- [ ] ⚠ BATCH mode dispatches `BatchTransfer`; per-item rejections reconcile to individual redemptions. — _Conscious deviation: BATCH dispatches **per-item `TransferFund`** through the existing `BatchRedemptionProcessor` (per-item webhook reconciliation already satisfies the per-item-failure intent, FR-12). Grouped `BatchTransfer` client method is built & stubbed but **not wired** (no perf need at ~20 payouts/day). Wire it if grouped batching is later required._
- [x] Bank linking stores only the XTRM `BeneficiaryId` + masked label (no account number); ACH without a linked bank is blocked with a prompt. — _unit-tested (`XtrmBankService`, `LinkBankForm`)._
- [x] Profile endpoints are self-only (IDOR-guarded); no PAT/bank-id/bank-number in any response or log. — _JWT-resolved, no id in path/body; `RedemptionProfileResponse` omits PAT/bank-id/clientId; no-leak asserted in controller test; ready-check security pass clean._
- [x] New audit values recorded on enrollment / bank-link / payout; documented in contracts. — _`AuditAction.{ENROLLED,BANK_LINKED,BANK_UNLINKED}` + `AuditResourceType.USER_REDEMPTION` added; `@Audited` on bank/payout-method endpoints, enrollment audited from the service; documented in `enums.md` / `enums-index.md`. (Payout dispatch reuses the existing redemption audit.)_
- [x] FE Payout tab + bank form + address fields work; KYC-limit + not-enrolled + duplicate-bank surfaced with friendly copy. — _built + Vitest-tested; error map covers `BANK_NOT_LINKED` / `XTRM_SEND_LIMIT` / `XTRM_NOT_ENROLLED` / duplicate-bank / `XTRM_UNAVAILABLE`. Playwright e2e pending._
- [x] `ready-check` per repo (backend + frontend) — done; findings H1/M2/M3 fixed, LOWs intentional/deferred.  **[ ] MRs into `roadmaps/redemption-store` still pending** — per the PR workflow, opened only after feature testing on a real stack.

**Done beyond the original checklist (ready-check + hardening):** M3 rate-limit on `POST /bank-account` (5/min per IP); M2 transient XTRM outages → **503** (not 422); INSTANT crash-recovery in the batch sweep; INSTANT definitive-failure releases the reservation + marks FAILED (FR-06/FR-09); a CASH-INSTANT full-loop integration test (enroll → after-commit dispatch → webhook) authored; realigned the stale NON_CASH INSTANT integration assertions (now COMPLETED, matching code + the unit tests).

### Pending before merge

- **Real-stack verification** (nothing below was runnable here — no Postgres/Docker/XTRM):
  - Run the Docker-gated `integrationTest` suite, incl. the new CASH-INSTANT full-loop test (`RedemptionRequestIntegrationTest`). All new/changed tests **compile**; only runtime is unverified.
  - Apply **V34** on a live DB (`bootRun`, `local` profile, UTC).
  - FE **Playwright/E2E**.
- **XTRM sandbox confirmation:** request/response + webhook JSON field names, event-type strings, and the signature header are XAPI-v4 best-guesses flagged in code — confirm against the sandbox before prod.
- **MRs into `roadmaps/redemption-store`** — per the PR workflow, opened only after the feature testing above.
- **Deferred (intentional, documented):** grouped `BatchTransfer` wiring (per-item `TransferFund` works, FR-12 satisfied); company-wallet payout (Decision 3).
- **Minor residuals:**
  - Transient outage during *lazy* enrollment still returns 422 (only the already-enrolled bank-link path returns 503).
  - NON_CASH INSTANT confirmation response can carry `COMPLETED` (outside the documented submit-status enum) — pre-existing Xoxoday-stub shortcut; converge NON_CASH onto the after-commit + webhook model once Xoxoday's real webhook lands.
  - The redemption `integrationTest` suite had drifted from the code (stale INSTANT assertions, now realigned) — worth confirming it actually runs in CI.
  - LOW ready-check items left as-is: substring error classification (pin to XTRM codes post-sandbox), `backfillEnrollments` unscheduled + untested, `batchTransfer` response-parsing untested.
- **Catalog admin — permission + ownership (CONFIRMED 2026-07-22) → see dedicated plan `2026-07-22-catalog-management-and-redemption-ux.md`:**
  - **Catalog ownership is now CONFIRMED (final): client admins own catalog management.** (Was provisional "yes for now"; @pushpendra confirmed 2026-07-22.) The two workstreams below are therefore **unblocked** and fully scoped in the dedicated plan doc — kept here as pointers.
  - **Permission migration (unblocked):** `action.redemption.catalog.manage` is granted to Client Admin only in the dev DB, not in any migration (V12 seeds it PLATFORM/TENX-admin only) → a fresh DB / `flyway clean` leaves Client Admin 403'd on the catalog UI. Fix: seed `catalog.manage` for base role `CLIENT_ADMIN` in **both** `client_role_permissions` AND `client_permission_grants` (mirror V12's `action.redemption.configure` pattern) + correct V12's "PLATFORM scope only" comment.
  - **🔴 Cross-tenant catalog isolation (unblocked):** `RedemptionCatalogItem` has **no `client_id`** — one global/shared pool, so a client-admin-created item is visible to every other client's admin and `activate/deactivate` is a global switch. Requirement: a client's catalog items must be private to that client. Fix: add `owner_client_id` (nullable — null = global/platform, set = client-private); enforce "global OR own-client" across every catalog read + write; rework activate/deactivate to own-items-only; tenant-isolation tests. **Open sub-question:** private item auto-available to that client's sellers on create, or still require the `ClientCatalogItemConfig` enable step?
  - **Geographic Scope field + Regional Availability matrix are dormant and now hidden** (`CATALOG_GEOGRAPHIC_SCOPE_ENABLED = false`, FE `config/redemptionFeatures.ts`). Geo never filters seller visibility or redeemability — it is only a vendor allowlist (guards which regions a tenant may configure) + a display tag; the per-region `ClientCatalogRegionConfig` it feeds is inert because the seller store never sends a `region` param. Hidden this session: the Geographic Scope input **and** the "Regional Availability" CA/US toggle matrix (+ its expand control) on both catalog admin tables. To make geography actually gate sellers: derive the browsing user's region → pass to `GET /redemption/catalog?region=` → enforce at redeem in `RedemptionSubmissionService`.
  - **Company-wallet redemption UI hidden** behind `COMPANY_REDEMPTION_ENABLED = false` (the "Redeem (Company)" button + the "Company" transaction-history tab). Backend company-history endpoints + export COMPANY scope exist; only the UI surface is gated. Ties to Decision 3 (company-wallet payout deferred).

---

# Appendix A — Full Feature Spec (design detail)

> Complete design reference for the enhancement — **self-contained here; we do NOT amend `features/redemption-flow`'s reviewed spec.** Entities/endpoints/permissions are additions to F-03's runtime, delivered as new files + the minimal existing-code edits listed in the Phase 1 footprint.

## Overview

Makes the redemption store's **cash payout path** work end-to-end through XTRM. Today `XtrmVendorService` sends a digital-gift-card SKU on the wrong XTRM payment method (`XTR94505` "Digital Gift Cards") and no user is provisioned in XTRM, so cash redemptions cannot pay out. This enhancement (1) **enrolls each partner user into XTRM** the first time they complete their profile, storing their XTRM recipient id in a new `user_redemption` record; (2) refactors dispatch to pay cash via **AnyPay Individual (`XTR94502`)** using `TransferFund` (INSTANT/APPROVAL) or `BatchTransfer` (BATCH); (3) lets a user optionally **link a bank account** for ACH payout; (4) reconciles payout status on the existing XTRM webhook. Money lands in the recipient's XTRM AnyPay wallet (auto-created); the recipient self-withdraws inside XTRM. XTRM handles KYC/AML/OFAC natively; the platform stores no bank/card credentials — only XTRM reference ids.

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-01 | A partner user is enrolled into XTRM (`CreateUser`) the first time they complete their profile; the returned XTRM recipient id (`PAT…`) and identity level are stored in a new `user_redemption` record. Idempotent — an already-enrolled user is not re-enrolled. |
| FR-02 | The profile-completion form collects the user's address (line 1 required + line 2/city/region/postal/country); `AddressLine1` + `CountryISO2` are required for enrollment; full address is reused for bank linking. No SSN or DOB collected. |
| FR-03 | Cash redemptions dispatch to XTRM using the user's chosen rail (default **AnyPay Individual `XTR94502`**): INSTANT + APPROVAL modes use `TransferFund`; BATCH mode uses `BatchTransfer`. Issuer source wallet / account / program are server config; recipient is the user's stored `PAT`. |
| FR-04 | A user may optionally link a bank (separate profile tab) via `LinkBankBeneficiary`; the returned `UserLinkedBankID` is stored. Selecting the ACH/Bank rail without a linked bank prompts the user to link one first. A linked bank can be viewed and removed/replaced. |
| FR-05 | `user_redemption` persists platform `userId` → XTRM `recipientUserId`, payout-method preference (default AnyPay), `userLinkedBankId` (nullable), enrollment status, identity level. Wallet auto-created by XTRM on first transfer (no explicit wallet call). |
| FR-06 | When XTRM rejects a payout for exceeding the identity-based send limit, the platform surfaces a friendly message and releases the reserved wallet balance. Limit increases happen in XTRM's portal, not TenXEngage. |
| FR-07 | Payout dispatch records the XTRM transaction reference; final COMPLETED/FAILED arrives via the existing XTRM webhook and is reconciled to the originating redemption request, transitioning status and finalizing the ledger (debit on success, release on failure). |
| FR-08 | The existing `XtrmVendorService` gift-card dispatch (SKU on `XTR94505`) is replaced by the AnyPay cash-payout model; `RedemptionOrchestrationService` continues routing `CASH → XTRM`. |
| FR-09 | A redemption dispatched for a user with no valid `recipientUserId` (not enrolled/failed) does not call XTRM with a missing recipient — it holds/fails the redemption with a clear reason and releases the reserved balance. |
| FR-10 | If XTRM `CreateUser` fails during profile completion, profile completion still succeeds; enrollment is marked failed and retried later (lazily before first payout and/or on next login). Non-blocking to the profile flow. |
| FR-11 | Users who completed onboarding before this shipped are enrolled lazily (before first payout / on next login), not only via the profile-completion hook. |
| FR-12 | `BatchTransfer` per-item accepted/rejected results are reconciled to individual redemption requests; a rejected item marks only its own redemption failed (with reason) or retries it individually — a partial failure never fails the whole batch or drops items. |
| FR-13 | Enrollment, bank-linking, and payout dispatch are audit-logged (actor, timestamp, XTRM reference ids) with no PII/SSN/bank numbers in the audit payload. |

## Functional Completeness Audit

| # | Dimension | Status | FR / Notes |
|---|---|---|---|
| 1 | Idempotent enrollment (no double-enroll / concurrency) | ✓ Already covered | FR-01 |
| 2 | KYC / send-limit handling | ✓ Already covered | FR-06 |
| 3 | Payout status finalization | ✓ Already covered | FR-07 |
| 4 | Payout attempted for a not-yet-enrolled user | ⊕ Approved | FR-09 |
| 5 | Enrollment failure at profile completion (recovery/retry) | ⊕ Approved | FR-10 |
| 6 | Existing (pre-feature) users backfill | ⊕ Approved | FR-11 |
| 7 | Batch partial-success handling | ⊕ Approved | FR-12 |
| 8 | Audit / traceability of money-movement actions | ⊕ Approved | FR-13 |

_All proposed gaps approved; none rejected or deferred._

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| Response time (reads) | P95 < 300ms | Redemption-profile / linked-bank reads |
| Response time (writes) | P95 < 500ms excl. XTRM call | XTRM calls external; run outside the DB transaction |
| Peak load | ~20 payouts/day | Batch sizing + rate limits are non-issues |
| Availability | 99.9% (core payout) | Finalization async via webhook; dispatch tolerates XTRM downtime (redemption stays PROCESSING, idempotent retry) |
| Data sensitivity | PII + Confidential | Identity passed to XTRM at call time; only XTRM ref ids stored; no bank/card numbers, no SSN |
| Compliance | KYC/AML/OFAC delegated to XTRM | Platform surfaces XTRM compliance failures; stores no payment credentials |
| Audit retention | 7 years | Money-movement actions |

## Prerequisites

- This plan (Appendix A/B) is the reviewed design + technical reference — `redemption-flow`'s own docs are **not** amended. Contracts generated in Phase 3.
- Next Flyway migration = **V34** (latest is V33).
- Existing `redemption.xtrm.*` config present; F-03 redemption-flow deployed (`RedemptionRequest`, `RedemptionWebhookEvent`, `RedemptionOrchestrationService`, XTRM webhook).

## New Enums [BE]

| Enum Class | Values | Notes |
|---|---|---|
| `XtrmEnrollmentStatus.java` | `NOT_ENROLLED, ENROLLED, FAILED` | Idempotency (FR-01), gating (FR-09), retry (FR-10). `varchar(30)`. `entity/enums/xtrm/` |
| `RedemptionPayoutMethod.java` | `ANYPAY, BANK` | User's rail (v1). `XTR94502` / `XTR94500`. Default `ANYPAY`. |

> Naming reconciliation: BRD annex `RedemptionTransaction` → codebase `RedemptionRequest` (used as-is). `UserRedemption` is new (no BRD/codebase equivalent).

## Data Model / Entities [BE]

### Entity-shape decisions

| Entity | Shape | Source |
|---|---|---|
| `UserRedemption` | Hardcoded JPA entity | This enhancement |

### UserRedemption (table: `user_redemption`)

_`entity/xtrm/UserRedemption` — extends `BaseEntity`, implements `TenantAware`, `@Filter(name="tenantFilter", condition="client_id = :clientId")`._

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK, `gen_random_uuid()` | BaseEntity |
| `client_id` | UUID | NOT NULL, FK clients | Tenant isolation — never in responses |
| `created_at`/`updated_at` | TIMESTAMPTZ | NOT NULL | BaseEntity |
| `version` | BIGINT | NOT NULL DEFAULT 0 | `@Version` |
| `user_id` | UUID | NOT NULL, UNIQUE, FK users | one profile per user |
| `recipient_user_id` | VARCHAR(50) | NULL until enrolled | XTRM `PAT` — the payout `RecipientUserID` |
| `enrollment_status` | VARCHAR(30) | NOT NULL DEFAULT `'NOT_ENROLLED'` | `XtrmEnrollmentStatus` |
| `enrollment_error` | VARCHAR(500) | NULL | sanitized last error (no PII) |
| `identity_level` | VARCHAR(30) | NULL | XTRM `AccountIdentityLevel` |
| `address_line1` | VARCHAR(255) | NULL (required to enroll) | **PII** — mandatory for XTRM `CreateUser` |
| `address_line2` | VARCHAR(255) | NULL | PII |
| `city` | VARCHAR(120) | NULL | PII |
| `region` | VARCHAR(120) | NULL | PII |
| `postal_code` | VARCHAR(20) | NULL | PII |
| `country_iso2` | VARCHAR(2) | NULL (required to enroll) | 2-letter ISO; required for `CreateUser` |
| `payout_method` | VARCHAR(30) | NOT NULL DEFAULT `'ANYPAY'` | `RedemptionPayoutMethod` |
| `user_linked_bank_id` | VARCHAR(100) | NULL | XTRM `BeneficiaryId` (ref, NOT the account number) |
| `linked_bank_label` | VARCHAR(100) | NULL | masked display label (e.g. `"Wells Fargo ••1898"`) |
| `enrolled_at` | TIMESTAMPTZ | NULL | when enrollment succeeded |

**No soft-delete** — 1:1 system record; bank removal nulls the ref fields; cascades with user cleanup.

**PII:** the **address fields are PII stored here** (required by XTRM `CreateUser`; reused for bank-linking) — bounded PII, subject to GDPR erasure with the user. `recipient_user_id`/`user_linked_bank_id` are pseudonymous XTRM refs. **No** SSN/DOB, **no** bank/card numbers stored.

**Relationships:** `@OneToOne` (logical) → `User` (FK `user_id`, UNIQUE).

**Indexes:** `idx_user_redemption_client_id` (client_id); `uq_user_redemption_user_id` UNIQUE (user_id); `idx_user_redemption_client_status` (client_id, enrollment_status) — backfill sweeps (FR-11).

## Permissions & Feature Flags [BE + FE]

_No new permissions and no new feature flag._

| Permission Key | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER | Used for |
|---|---|---|---|---|---|
| `module.redemption_store` (existing) | Y | — | Y | Y | access profile/payout surfaces |
| `action.redemption.redeem` (existing) | — | — | — | Y | link/manage bank, personal payout |
| `action.redemption.redeem_company` (existing) | — | — | Y | — | company payout |

- Bank-link + redemption-profile endpoints: `@RequiresPermission({action.redemption.redeem, action.redemption.redeem_company}, ANY)` — self-only (IDOR-guarded).
- Enrollment: authenticated profile-completion flow; no new permission.
- Feature flag: reuse existing `redemption_store` (all tiers). No permission/flag seed migration.

## DTOs [BE]

**Request** (`dto/request/xtrm/`):
- `LinkBankAccountRequest(contactName, accountNumber, routingNumber, swiftBic, institutionName, addressLine1, addressLine2, city, region, postalCode, countryIso2, withdrawType)` — structural validation only (`@NotBlank`, `@Size`, `@Pattern("[A-Z]{2}")` for countryIso2). **Pass-through to XTRM, never persisted.** Domain errors (duplicate bank, invalid routing) come from XTRM → 422 with errorCode (not `@Pattern` on the DTO).
- `SetPayoutMethodRequest(payoutMethod)` — `@NotNull`, `@ValidEnum(RedemptionPayoutMethod)`.

**Response** (`dto/response/xtrm/`):
- `RedemptionProfileResponse.from(UserRedemption)` — renders: `enrollmentStatus` ("Ready/Pending/Action needed"), `payoutMethod`, `bankLinked` (bool), `linkedBankLabel` (masked), `identityLevel`. **Never exposes** `recipientUserId`, `userLinkedBankId`, `clientId`, `version`, `enrollment_error`.

## API Endpoints [BE + FE]

_Base `/api/v1/redemption` · Tag `Redemption Payout`. All resolve `user_redemption` from JWT — no id in path/body (IDOR guard); self-only._

| Method | Path | Body | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| GET | `/redemption/profile` | — | `RedemptionProfileResponse` | 200 | redeem OR redeem_company | — |
| PUT | `/redemption/profile/payout-method` | `SetPayoutMethodRequest` | `RedemptionProfileResponse` | 200 | redeem OR redeem_company | `@Audited` |
| POST | `/redemption/profile/bank-account` | `LinkBankAccountRequest` | `RedemptionProfileResponse` | 201 | redeem OR redeem_company | `@Audited` |
| DELETE | `/redemption/profile/bank-account` | — | `RedemptionProfileResponse` | 200 | redeem OR redeem_company | `@Audited` |

**Errors:** 400 validation · 401 unauth · 403 no redemption perm · 409 optimistic-lock · 422 XTRM domain rejection (duplicate bank/invalid routing/enrollment rejected, with errorCode) · 429 rate limit.

_Enrollment has no public endpoint — triggered in the profile-completion flow + lazy path._

## Service Layer [BE] (`service/xtrm/`)

**XtrmEnrollmentService:** `enrollIfNeeded(userId)` (idempotent; no-op if ENROLLED; XTRM call **outside** `@Transactional`), `getOrCreateProfile(userId)`, `ensureEnrolledForPayout(userId)` → PAT or throws `BusinessRuleException("XTRM_NOT_ENROLLED", …)`.

**XtrmBankService:** `linkBank(userId, request)` (stores `BeneficiaryId` + masked label; no raw bank data), `removeBank(userId)`, `setPayoutMethod(userId, method)` (rejects `BANK` when no bank → `BANK_NOT_LINKED`).

**XtrmVendorService (refactored):** `dispatch(request)` — INSTANT/APPROVAL `TransferFund` (AnyPay `XTR94502`, or `XTR94500`+`userLinkedBankId` when BANK); recipient = stored PAT via `ensureEnrolledForPayout`; **drops SKU + UserGiftCardEmailID**. `dispatchBatch(requests)` — BATCH `BatchTransfer`, per-item reconciliation (FR-12).

Rules: `WalletID`/`SourceWalletId` = issuer wallet config (`203871`), never a recipient wallet. Not-enrolled / send-limit failures → release reserved balance + friendly reason. XTRM errors sanitized before logging; raw XTRM message not echoed to API responses. `clientId` from `tenantValidator.getCurrentClientId()`; `user_redemption` queries filter by clientId+userId.

## Workflow / Status Transitions (`XtrmEnrollmentStatus`)

```
NOT_ENROLLED → ENROLLED   (CreateUser succeeds; profile completion / lazy pre-payout)
NOT_ENROLLED → FAILED     (CreateUser errors; profile completion — non-blocking)
FAILED       → ENROLLED   (retry succeeds; next login / pre-payout)
FAILED       → FAILED     (retry errors — remains retryable)
```
`ENROLLED` terminal (no un-enroll v1); re-enroll while ENROLLED is a no-op. Concurrent profile edits → 409 via `@Version`.

## Security Design [BE]

**Data classification:** `recipient_user_id` / `user_linked_bank_id` = Confidential pseudonymous refs (never in responses/logs raw); bank account/routing (request only) = Confidential, **never persisted**, pass-through to XTRM, not logged; identity (name/email/phone/address) = PII, passed to XTRM at call time, address on profile, no SSN/DOB.

**Rate limiting:** `POST /redemption/profile/bank-account` 5 req/min per user (external XTRM call); enrollment inherits profile-update limit.

**OWASP:** IDOR/Broken Access Control — recipient/`user_redemption` from JWT, never client-supplied PAT/userId; XTRM issuer/wallet/program are server config. Sensitive-data exposure — raw PAT/bank-id/numbers never in responses/logs; only masked label. Tenant — `findBy…AndClientId`, cross-tenant → 404. Mass assignment — Java records; server sets recipient/wallet/program. Webhook replay — existing HMAC + idempotency by XTRM txn id.

**Input validation:** `countryIso2` `@Pattern("[A-Z]{2}")` + `@NotBlank`; bank fields `@NotBlank`/`@Size` (XTRM domain errors → 422 errorCode); `payoutMethod` `@ValidEnum`.

## Audit Trail [BE]

| Operation | Entity | Captured | View |
|---|---|---|---|
| ENROLL | UserRedemption | userId, enrollment_status, identity_level (no PII) | CLIENT_ADMIN |
| LINK BANK | UserRedemption | userId, masked label (no account #) | CLIENT_ADMIN |
| REMOVE BANK | UserRedemption | userId | CLIENT_ADMIN |
| SET PAYOUT METHOD | UserRedemption | userId, old→new | CLIENT_ADMIN |
| PAYOUT DISPATCH | RedemptionRequest | redemptionId, userId, XTRM txn ref, method | CLIENT_ADMIN |

**New enum values:** `AuditAction.{ENROLLED, BANK_LINKED, BANK_UNLINKED}`, `AuditResourceType.USER_REDEMPTION` (Java enum + `contracts/enums.md` + `enums-index.md` counts; no Flyway). Retention 7 years, append-only.

## Observability [BE]

| Event | Level | `step` | Fields |
|---|---|---|---|
| Enrollment attempt | INFO | `xtrm_enroll` | userId, enrollmentStatus |
| Enrollment failed | WARN | `xtrm_enroll_failed` | userId, sanitized reason |
| Bank linked | INFO | `xtrm_bank_linked` | userId (no account #) |
| Payout dispatched | INFO | `xtrm_dispatch` | redemptionId, method, txn ref |
| Payout rejected (limit) | WARN | `xtrm_send_limit` | redemptionId |
| Batch item rejected | WARN | `xtrm_batch_item_rejected` | redemptionId, reason |

Never log PAT / bank numbers / XTRM raw error bodies / identity fields. Metrics: `xtrm.enroll.total{result}`, `xtrm.payout.dispatch.total{method,mode}`, `xtrm.payout.failed.total{reason}`.

## Caching Strategy [BE]

No new server-side caching. `user_redemption` read per-user; TanStack Query handles client cache (5-min stale). XTRM OAuth token cache in `XtrmVendorService.getAccessToken()` unchanged.

## Data Retention & Compliance [BE]

No soft-delete on `user_redemption` (1:1 system record; bank removal nulls refs; removed on user erasure). PII: **address fields (line1/2, city, region, postal, country) are stored on `user_redemption`** and removed on user erasure; `recipient_user_id`/`user_linked_bank_id` are pseudonymous refs (removed on user erasure). No raw bank/card/SSN stored. Retention: record = life of user; audit = 7 years.

## Edge Cases [BE + FE]

1. Not enrolled at payout → lazy enroll; if still fails, redemption failed + reserved released; FE message.
2. Enrollment fails at profile completion → profile still completes; status FAILED; retried later; non-blocking note.
3. Pre-existing user (no hook) → lazy-enrolled before first payout / next login.
4. KYC send-limit exceeded → friendly message, reserved released, user directed to XTRM.
5. ACH selected without linked bank → `BANK_NOT_LINKED`; prompt to link.
6. Duplicate bank details → XTRM "already linked" → friendly 422.
7. Batch partial success → only rejected redemptions failed/retried; accepted proceed.
8. XTRM downtime → redemption stays PROCESSING (reserved held); idempotent retry; webhook finalizes.
9. Cross-tenant/cross-user → profile endpoints self-only (JWT); cross-tenant → 404.
10. Concurrent profile edits → 409; FE refresh-and-retry.
11. Idempotent re-enroll → no-op.
12. Country code invalid/missing → enrollment blocked with validation message.

## Acceptance Tests

Tests live **alongside the implementation code** (no separate story/test-plan files for this self-contained enhancement): BE unit (`XtrmEnrollmentServiceTest`, `XtrmBankServiceTest`, updated `XtrmVendorServiceTest`) + `@WebMvcTest` (`RedemptionProfileControllerTest`) + Testcontainers integration (enroll→dispatch→webhook lifecycle, tenant isolation, batch reconciliation); FE Vitest + Playwright. XTRM calls via a `@Profile({"local","test"})` stub client (never prod). Verified per **Done When** + **Verification Steps**.

## Modified Existing Endpoints [BE + FE]

| Endpoint | Change | Breaking? |
|---|---|---|
| Profile-completion/update (existing) | persist address + trigger `enrollIfNeeded` | No — additive |
| `XtrmVendorService.dispatch` (internal) | gift-card SKU → AnyPay `TransferFund`/`BatchTransfer`; drop SKU/UserGiftCardEmailID | No public change |
| XTRM webhook (existing) | reconcile txn refs → redemption; idempotent | No — additive |
| Config `redemption.xtrm.payment-method-id` | default `XTR94505` → `XTR94502` | No — config default |

## Out of Scope

Prepaid Visa (`XTR94503`), Bank Check (`XTR94507`), Digital Gift Cards (`XTR94505`), Rapid Transfer/push-to-card (`XTR94508`, card tokenization + PCI); AnyPay Company (`XTR94504`/`SPN`); `UserWithdrawFund` (recipient does it in XTRM); multiple linked banks; non-USD; raising KYC limits in-app; `GetDigitalGiftCards` catalog seeding; **syncing post-enrollment address/identity changes back to XTRM** — enrollment is one-time (`CreateUser`); later profile/address edits are saved on `user_redemption` but NOT pushed to XTRM (no `UpdateUser` call in v1).

## Verification Steps

**BE:** `bootRun` (local, `-Duser.timezone=UTC`) — V34 applies; `test` passes (XTRM stub); enroll flow creates `ENROLLED` row w/ `recipient_user_id`; INSTANT redeem → `TransferFund` (AnyPay) → webhook → COMPLETED + debit; forced send-limit/not-enrolled → reserved released; no PAT/bank number in any response/log.
**FE:** `build` + `test` + `playwright` pass; address fields validate inline; Payout tab shows status; link-bank surfaces XTRM 422 inline; Bank-without-linked-bank prompts.

---

# Appendix B — Full Technical Detail

> Complete technical reference — **self-contained here; we do NOT amend `features/redemption-flow/technical.md`.** Latest migration is **V33** → this uses **V34**. BE feature sub-package `xtrm`; FE `redemption-payout`.

## Flyway — V34__create_user_redemption_table.sql

```sql
CREATE TABLE user_redemption (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           UUID          NOT NULL REFERENCES clients(id),
    user_id             UUID          NOT NULL REFERENCES users(id),
    recipient_user_id   VARCHAR(50)   NULL,
    enrollment_status   VARCHAR(30)   NOT NULL DEFAULT 'NOT_ENROLLED',
    enrollment_error    VARCHAR(500)  NULL,
    identity_level      VARCHAR(30)   NULL,
    address_line1       VARCHAR(255)  NULL,
    address_line2       VARCHAR(255)  NULL,
    city                VARCHAR(120)  NULL,
    region              VARCHAR(120)  NULL,
    postal_code         VARCHAR(20)   NULL,
    country_iso2        VARCHAR(2)    NULL,
    payout_method       VARCHAR(30)   NOT NULL DEFAULT 'ANYPAY',
    user_linked_bank_id VARCHAR(100)  NULL,
    linked_bank_label   VARCHAR(100)  NULL,
    enrolled_at         TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX        idx_user_redemption_client_id     ON user_redemption(client_id);
CREATE UNIQUE INDEX uq_user_redemption_user_id        ON user_redemption(user_id);
CREATE INDEX        idx_user_redemption_client_status ON user_redemption(client_id, enrollment_status);
```

**No permission/feature-flag seed migration** (reuse existing). **Config default** `redemption.xtrm.payment-method-id`: `XTR94505` → `XTR94502` (`application-local.yml` / `XTRM_PAYMENT_METHOD`).

## Package Layout [BE] (sub-package `xtrm`)

```
entity/xtrm/UserRedemption.java                 (BaseEntity, TenantAware, @Filter, @Version)
entity/enums/xtrm/XtrmEnrollmentStatus.java     (NOT_ENROLLED|ENROLLED|FAILED)
entity/enums/xtrm/RedemptionPayoutMethod.java   (ANYPAY|BANK)
repository/xtrm/UserRedemptionRepository.java
service/xtrm/XtrmEnrollmentService.java
service/xtrm/XtrmBankService.java
service/xtrm/XtrmApiClient.java                  (CreateUser, TransferFund, BatchTransfer, LinkBankBeneficiary, token)
service/xtrm/XtrmApiClientStub.java             (@Profile({"local","test"}) — never prod)
service/XtrmVendorService.java                   (REFACTORED — AnyPay dispatch/dispatchBatch; drops SKU)
controller/xtrm/RedemptionProfileController.java
dto/request/xtrm/LinkBankAccountRequest.java, SetPayoutMethodRequest.java
dto/response/xtrm/RedemptionProfileResponse.java
resources/db/migration/V34__create_user_redemption_table.sql
test/service/xtrm/{XtrmEnrollmentServiceTest,XtrmBankServiceTest}.java
test/service/XtrmVendorServiceTest.java          (UPDATE — AnyPay payload assertions)
test/controller/xtrm/RedemptionProfileControllerTest.java
test/testdata/xtrm/UserRedemptionFixtures.java   (mandatory)
```
Existing touched: `RedemptionOrchestrationService` (remove stale "stubs" comment), profile/onboarding controller+service (address + `enrollIfNeeded`), XTRM webhook handler (reconciliation), `AuditAction`/`AuditResourceType` (new values).

## Repository Queries [BE] (all include clientId)

- `findByUserIdAndClientId(userId, clientId)` → `Optional<UserRedemption>`
- `existsByUserIdAndClientId(userId, clientId)` → boolean (pair with `DataIntegrityViolationException` catch on the unique index)
- `findByClientIdAndEnrollmentStatus(clientId, status, pageable)` → `Page` (backfill sweep; ordered by createdAt)

_No cross-tenant sweep on this repo (tenant-isolation pitfall) — per-tenant or dedicated scheduler repo._

## Package Layout [FE] (sub-folder `redemption-payout`)

```
types/redemption-payout/redemption-payout.types.ts        (copy from contracts)
services/redemption-payout/redemption-payout.service.ts
hooks/redemption-payout/useRedemptionProfile.ts
hooks/redemption-payout/useRedemptionProfileMutations.ts  (link/remove bank, set method)
components/redemption-payout/{PayoutTab,LinkBankForm,ProfileAddressSection}.tsx + __tests__
pages/client-admin/MyProfilePage.tsx                       (EXTEND — address section + Payout tab, URL-driven via useSearchParams)
```
No new route (existing `/settings/profile`).

## Hook Specs [FE]

- `useRedemptionProfile()` → `queryKey ['redemption-profile', userId]`, `staleTime 5m`, `retry:false`.
- `useLinkBankAccount()` POST, `useRemoveBankAccount()` DELETE, `useSetPayoutMethod()` PUT — each invalidates `['redemption-profile', userId]` (guard `if (userId)`; `null` fallback). Error map `data?.errorCode ?? data?.code`: `BANK_NOT_LINKED`, `XTRM_SEND_LIMIT`, `XTRM_NOT_ENROLLED`, duplicate-bank → friendly copy; XTRM 422 inline on the bank form (not toast).

## Audit Annotations [BE]

New: `AuditAction.{ENROLLED, BANK_LINKED, BANK_UNLINKED}`, `AuditResourceType.USER_REDEMPTION` (+ `contracts/enums.md` / `enums-index.md`).

| Endpoint | action | resourceType | description |
|---|---|---|---|
| POST `/redemption/profile/bank-account` | `BANK_LINKED` | `USER_REDEMPTION` | Linked bank account |
| DELETE `/redemption/profile/bank-account` | `BANK_UNLINKED` | `USER_REDEMPTION` | Removed bank account |
| PUT `/redemption/profile/payout-method` | `EDITED` | `USER_REDEMPTION` | Updated payout method |

Enrollment (`ENROLLED`) audited from `XtrmEnrollmentService` (system-triggered, actor = enrolling user). Payout dispatch reuses existing redemption audit.

**XTRM client:** reuse existing OAuth token cache (`getAccessToken()`), `RestClient`, `onStatus` swallow-then-parse. All XTRM HTTP calls run **outside** `@Transactional`; persist results in a short follow-up transaction. Stub `@Profile({"local","test"})`; real client active in prod.

---

## Post-plan refinements (executed 2026-07-08) — see `2026-07-08-xtrm-unified-admin-payout-and-rename.md`

The mentions of `user_redemption` / `UserRedemption` / `USER_REDEMPTION` above are **superseded** by these refinements:

- **Payout model confirmed:** both partner seller **and** partner admin redeem their own individual balance via `CreateUser`→PAT→`TransferFund` to their own PAT. Company (pooled) wallet redemption is **deferred** — the `COMPANY_PAYOUT_NOT_SUPPORTED` guard in `XtrmVendorService.dispatch` stays as the placeholder. No dispatch change.
- **Renamed** `user_redemption` → `partner_redemption` (table + entity `PartnerRedemption` + repository + fixtures + 3 indexes), and column `user_linked_bank_id` → `partner_linked_bank_id` (entity/DB + docs only; XTRM API naming `userLinkedBankId` / `"UserLinkedBankID"` kept).
- **Audit** `AuditResourceType.USER_REDEMPTION` → `PARTNER_REDEMPTION` (+ contracts `x-audited` / enums).
- **Address** grouped into a JPA `@Embeddable PartnerAddress` value object (same 6 columns; request/response DTOs stay flat — no API/FE change).
- Verified: backend `compileJava`/`compileTestJava` clean; `XtrmVendorServiceTest`, `XtrmEnrollmentServiceTest`, `XtrmBankServiceTest`, `RedemptionProfileControllerTest` green; `tenxengage-contracts` docs updated (model doc → `partner-redemption.md`).
