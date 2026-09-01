# XTRM: Rename `user_redemption` → `partner_redemption` (+ `user_linked_bank_id` → `partner_linked_bank_id`, address embeddable; company-wallet payout stays deferred) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This is a refinement of the parent plan `2026-07-03-redemption-xtrm-payout-enhancement.md` (F-03 enhancement) — do not create a new `features/` folder.

**Goal:**
1. Rename the `user_redemption` table/entity to `partner_redemption` (enrollment is scoped to partner personas only).
2. Rename the column `user_linked_bank_id` → `partner_linked_bank_id` (entity/DB + our docs only — see the XTRM carve-out).
3. Group the 6 scattered address fields into a JPA `@Embeddable` value object **`PartnerAddress`** — one cohesive address in code, same 6 DB columns.
4. Keep the `tenxengage-contracts` source-of-truth docs consistent with the renames.
5. **Record** the confirmed payout model — no dispatch change is required.

**Decision — payout model (confirmed 2026-07-08):** In v1, **both** partner seller and partner admin redeem their **own individual** reward-wallet balance via the **individual** path — `CreateUser` → PAT → `TransferFund` to their own PAT. The partner admin does **NOT** redeem a company (pooled) wallet in v1. **Company-wallet redemption is future work.** The `COMPANY_PAYOUT_NOT_SUPPORTED` guard in `XtrmVendorService.dispatch` is therefore **correct and stays** — it defers the unbuilt company path (and prevents the fall-through that would pay pooled funds into an individual PAT).

**Decision — address embeddable (confirmed 2026-07-08):** JPA `@Embeddable` `PartnerAddress` grouping `line1, line2, city, region, postalCode, countryIso2` → mapped to the **existing** 6 columns. Chosen over `jsonb` to keep DB constraints (2-char country, lengths) + country queryability; XTRM `CreateUser` needs the fields discrete anyway. **DTOs stay flat** — no API-shape change; only the DTO↔entity mapping constructs/reads a `PartnerAddress`. Verified: the **only** entity-address consumer is `XtrmEnrollmentService`.

**Decision — column rename `user_linked_bank_id` → `partner_linked_bank_id` (confirmed 2026-07-08):** Rename the **entity field + DB column + our docs/tests only**. ⚠️ **DO NOT** rename XTRM's API naming: `XtrmApiClient` `LinkBankCommand`/`LinkBankResult`/`TransferFundCommand.userLinkedBankId` and the wire JSON key `"UserLinkedBankID"` (`XtrmApiClientImpl`) mirror XTRM's actual field and MUST stay. The sibling column `recipient_user_id` is **left as-is** (it denotes the XTRM recipient-user PAT); flag if you want it renamed too. `linked_bank_label` is unchanged (masked display label, e.g. `"Wells Fargo ••1898"`).

**Architecture:**
- **Dispatch — NO CHANGE.** Leave the `walletType == COMPANY` guard in place.
- **Renames — in place.** V34 has not been applied to any DB (verified: not in local; V34 is the highest migration; not merged), so edit it in place. Rename table + 3 indexes + the `user_linked_bank_id` column; entity `UserRedemption`→`PartnerRedemption`; repository; fixtures; `AuditResourceType.USER_REDEMPTION` value; entity field `userLinkedBankId`→`partnerLinkedBankId`; and all references. **All other column names unchanged.**
- **Address embeddable — Java-only.** Entity replaces its 6 `@Column` address fields with `@Embedded private PartnerAddress address`; column names unchanged. ⚠️ Hibernate returns a **null** embedded object when all its columns are null → null-check `getAddress()` before `getLine1()`.
- **Contracts — docs consistency.** Update table name, column names, `AuditResourceType`, `x-audited`. No request/response schema changes.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Flyway (V34, unapplied), JUnit 5 + Mockito. Backend + `tenxengage-contracts` (docs only). **No API-shape or frontend change** (verified: `tenxengage-frontend`, `tenxengage-admin-frontend`, `tenxengage-admin-backend` = 0 refs).

**Scope (verified):** backend `user_redemption`/`UserRedemption` rename = 18 files (~135 refs); `user_linked_bank_id`/`userLinkedBankId` entity rename ≈ 6 main + 4 test sites (XTRM API-client sites excluded); address embeddable = `XtrmEnrollmentService` only; contracts = 4 files.

---

## Column-rename touch-points (`userLinkedBankId` → `partnerLinkedBankId`)

**RENAME — EVERY Java identifier `userLinkedBankId` → `partnerLinkedBankId`** (updated 2026-07-08 per "all places"):
- `V34…sql` column def · `PartnerRedemption` field + `@Column` + Lombok getter/setter
- `XtrmVendorService`, `XtrmBankService`, `RedemptionProfileResponse` (+ javadoc), `RedemptionPayoutMethod` (javadoc)
- **`XtrmApiClient`** `LinkBankCommand`/`LinkBankResult`/`TransferFundCommand` fields + accessors; **`XtrmApiClientImpl`** `cmd.partnerLinkedBankId()`
- Tests/fixtures: `PartnerRedemptionFixtures`, `XtrmBankServiceTest`, `XtrmVendorServiceTest` (incl. the `cmd.` command asserts), `RedemptionProfileControllerTest`
- Contracts: `enums.md`, `redemption-payout.yaml`, model doc

**KEEP — only the XTRM wire JSON key `"UserLinkedBankID"`** (`XtrmApiClientImpl`, request line 123 + response-parse line 253): that string literal is XTRM's actual API field name; renaming it would break the integration.

---

## Decisions assumed (veto during review)

1. **Rename** `partner_redemption` / `PartnerRedemption` / `PartnerRedemptionRepository`.
2. **`AuditResourceType.USER_REDEMPTION` → `PARTNER_REDEMPTION`** (drives contract `x-audited`/enum changes; vetoing keeps them).
3. **Column + all Java identifiers** `userLinkedBankId` → `partnerLinkedBankId` (incl. XTRM client records/accessors); only the XTRM **wire key** `"UserLinkedBankID"` kept. `recipient_user_id` left as-is.
4. **Address embeddable `PartnerAddress`**, entity-only; DTOs stay flat.
5. **Update `tenxengage-contracts` docs** for consistency.

---

## File Map

| File | Change |
|---|---|
| `db/migration/V34__create_user_redemption_table.sql` | Rename file → `…partner_redemption…`; table + 3 index names + `user_linked_bank_id` col → `partner_linked_bank_id`. Other columns unchanged. |
| `entity/xtrm/PartnerAddress.java` | **NEW** `@Embeddable` (6 fields → existing columns) |
| `entity/xtrm/UserRedemption.java` | → `PartnerRedemption.java`, `@Table("partner_redemption")`; 6 address `@Column`s → `@Embedded PartnerAddress`; field `userLinkedBankId`→`partnerLinkedBankId` (`@Column(name="partner_linked_bank_id")`) |
| `repository/xtrm/UserRedemptionRepository.java` | → `PartnerRedemptionRepository.java`; entity type; fix index-name comment |
| `testdata/xtrm/UserRedemptionFixtures.java` (test) | → `PartnerRedemptionFixtures.java`; address via embeddable; `partnerLinkedBankId` builder |
| `entity/enums/AuditResourceType.java` | `USER_REDEMPTION` → `PARTNER_REDEMPTION` |
| `entity/enums/xtrm/RedemptionPayoutMethod.java` | javadoc `user_linked_bank_id` → `partner_linked_bank_id` |
| `controller/xtrm/RedemptionProfileController.java` | `@Audited(resourceType="PARTNER_REDEMPTION")` ×3 + type refs |
| `service/xtrm/XtrmEnrollmentService.java` | type refs; address gate + `CreateUser` map + save via `PartnerAddress` (null-safe); fix index comment |
| `service/xtrm/XtrmBankService.java` | **rename-only** (verified: passes DTO address to XTRM, never persists it); `set/getPartnerLinkedBankId` (×3) + type refs |
| `service/XtrmVendorService.java` | type refs; `profile.getPartnerLinkedBankId()` ×2; **guard UNCHANGED** |
| `dto/response/xtrm/RedemptionProfileResponse.java` | **rename-only** (verified: no address exposed); `profile.getPartnerLinkedBankId()` + javadoc |
| `dto/request/xtrm/SaveRedemptionAddressRequest.java` · `LinkBankAccountRequest.java` | **flat, unchanged** |
| `entity/enums/xtrm/XtrmEnrollmentStatus.java` | comment ref |
| `service/xtrm/XtrmApiClient.java` · `XtrmApiClientImpl.java` | **UNCHANGED** — XTRM `userLinkedBankId` / `"UserLinkedBankID"` kept |
| `service/XtrmVendorServiceTest.java` (test) | type refs + entity-builder `partnerLinkedBankId` (line ~123); **keep `cmd.userLinkedBankId()` + COMPANY-guard test** |
| `…/RedemptionProfileControllerTest.java` · `XtrmEnrollmentServiceTest.java` · `XtrmBankServiceTest.java` · `RedemptionRequestIntegrationTest.java` (test) | rename refs; address via embeddable; `partnerLinkedBankId` |
| `../tenxengage-contracts/models/user-redemption.md` | → `partner-redemption.md`; title; `Table: partner_redemption`; `userLinkedBankId`→`partnerLinkedBankId` |
| `../tenxengage-contracts/endpoints/redemption-payout.yaml` | prose `user_redemption`→`partner_redemption` (×3); `x-audited … USER_REDEMPTION`→`PARTNER_REDEMPTION` (×3); `userLinkedBankId`→`partnerLinkedBankId` |
| `../tenxengage-contracts/enums.md` · `enums-index.md` | `AuditResourceType` value; `…enrollment_status`; `userLinkedBankId` ref |
| `docs/superpowers/plans/2026-07-03-…md` | note confirmed model + embeddable + renames |

---

## Task 1: Rename migration — table, indexes, column

**Files:** `db/migration/V34__create_user_redemption_table.sql`

- [ ] **Step 1:** Rename file → `V34__create_partner_redemption_table.sql` (Flyway version V34 unchanged — safe, never applied).
- [ ] **Step 2:** `CREATE TABLE user_redemption` → `partner_redemption`.
- [ ] **Step 3:** Rename the column `user_linked_bank_id` → `partner_linked_bank_id`. Leave all other 19 columns untouched.
- [ ] **Step 4:** Rename indexes `idx_user_redemption_client_id`, `uq_user_redemption_user_id`, `idx_user_redemption_client_status` → `*_partner_redemption_*`.

## Task 2: Rename entity, repository, fixtures

**Files:** `entity/xtrm/UserRedemption.java`, `repository/xtrm/UserRedemptionRepository.java`, `testdata/xtrm/UserRedemptionFixtures.java`

- [ ] **Step 1:** Rename class + file `UserRedemption` → `PartnerRedemption`; `@Table(name = "partner_redemption")`.
- [ ] **Step 2:** Rename field `userLinkedBankId` → `partnerLinkedBankId` with `@Column(name = "partner_linked_bank_id")` (Lombok regenerates `get/setPartnerLinkedBankId`).
- [ ] **Step 3:** Rename `UserRedemptionRepository` → `PartnerRedemptionRepository`; `findBy…` method names unchanged (field-based); fix the `uq_user_redemption_user_id` javadoc.
- [ ] **Step 4:** Rename `UserRedemptionFixtures` → `PartnerRedemptionFixtures` (address + `partnerLinkedBankId` wiring in Tasks 4/5).

## Task 3: Rename audit resource type (assumed-yes)

**Files:** `entity/enums/AuditResourceType.java`, `controller/xtrm/RedemptionProfileController.java`, `service/xtrm/XtrmEnrollmentService.java`, `service/xtrm/XtrmEnrollmentServiceTest.java`

- [ ] **Step 1:** `AuditResourceType.USER_REDEMPTION` → `PARTNER_REDEMPTION`.
- [ ] **Step 2:** Update the 3 `@Audited(resourceType = "USER_REDEMPTION")` literals → `"PARTNER_REDEMPTION"`.
- [ ] **Step 3:** Update the enum usage in `XtrmEnrollmentService` + its test's `eq(...)`.

## Task 4: Introduce `PartnerAddress` `@Embeddable`

**Files:** `entity/xtrm/PartnerAddress.java` (new), `entity/xtrm/PartnerRedemption.java`, `service/xtrm/XtrmEnrollmentService.java`, `testdata/xtrm/PartnerRedemptionFixtures.java`

> **Verified:** the ONLY consumer of the entity's address fields is `XtrmEnrollmentService`. `XtrmBankService` and `RedemptionProfileResponse` don't touch entity address (rename-only, Task 5).

- [ ] **Step 1:** Create `PartnerAddress` `@Embeddable` (`line1, line2, city, region, postalCode, countryIso2`), each `@Column(name=…, length=…)` mapped to existing columns. Optional `isEnrollable()` = `line1` + `countryIso2` non-blank.
- [ ] **Step 2:** In `PartnerRedemption`, replace the six address `@Column` fields (lines ~63–79) with `@Embedded private PartnerAddress address;`.
- [ ] **Step 3:** `XtrmEnrollmentService` — `saveAddressAndEnroll` (lines ~116–121) constructs a `PartnerAddress` + `setAddress(...)`; the gate (line ~146) and `CreateUserCommand` mapping (lines ~153–156) read `partner.getAddress()` **null-safe**.
- [ ] **Step 4:** `PartnerRedemptionFixtures` sets address via `PartnerAddress`.
- [ ] **Note:** DTOs stay flat.

## Task 5: Update remaining references (rename only — no logic change)

**Files:** `XtrmVendorService.java`, `XtrmBankService.java`, `RedemptionProfileResponse.java`, `RedemptionProfileController.java`, `RedemptionPayoutMethod.java`, `LinkBankAccountRequest.java`, `SaveRedemptionAddressRequest.java`, `XtrmEnrollmentStatus.java`

- [ ] **Step 1:** Replace remaining `UserRedemption` type usages/imports/comments with `PartnerRedemption`.
- [ ] **Step 2:** Rename **entity-field** accesses `get/setUserLinkedBankId` → `get/setPartnerLinkedBankId` in `XtrmVendorService` (×2), `XtrmBankService` (×3), `RedemptionProfileResponse` (×1 + javadoc), and the `RedemptionPayoutMethod` javadoc. **Leave `XtrmApiClient`/`XtrmApiClientImpl` XTRM naming untouched.**
- [ ] **Step 3:** Confirm `XtrmVendorService`'s `COMPANY_PAYOUT_NOT_SUPPORTED` guard is **intact**.

## Task 6: Update tests

**Files:** `RedemptionProfileControllerTest.java`, `XtrmEnrollmentServiceTest.java`, `XtrmBankServiceTest.java`, `RedemptionRequestIntegrationTest.java`, `XtrmVendorServiceTest.java`

- [ ] **Step 1:** Update entity/repo/fixture type refs; address setup/assertions via `PartnerAddress`; entity-field `partnerLinkedBankId` (`XtrmBankServiceTest` ×3, `XtrmVendorServiceTest` line ~123 builder, `RedemptionProfileControllerTest` jsonPath probes ×2).
- [ ] **Step 2:** Keep `XtrmVendorServiceTest`'s `cmd.userLinkedBankId()` (XTRM command, lines ~101/117) and the COMPANY-guard case unchanged.

## Task 7: Update `tenxengage-contracts` (source-of-truth docs)

**Files:** `../tenxengage-contracts/models/user-redemption.md`, `endpoints/redemption-payout.yaml`, `enums.md`, `enums-index.md`

- [ ] **Step 1:** Rename `models/user-redemption.md` → `partner-redemption.md`; title `# PartnerRedemption`; `Table: partner_redemption`; field `userLinkedBankId` → `partnerLinkedBankId` (keep the "XTRM UserLinkedBankID" description of the external concept).
- [ ] **Step 2:** In `redemption-payout.yaml`, update the 3 prose `user_redemption` → `partner_redemption`; the 3 `x-audited: … USER_REDEMPTION` → `PARTNER_REDEMPTION` (if audit rename approved); and the `userLinkedBankId` mentions → `partnerLinkedBankId`.
- [ ] **Step 3:** In `enums.md` / `enums-index.md`, update the `AuditResourceType` value, the `…enrollment_status` reference, and the `userLinkedBankId` reference.

## Task 8: Verify

- [ ] **Step 1:** `./gradlew compileJava compileTestJava` — clean compile.
- [ ] **Step 2:** Run targeted tests: `XtrmVendorServiceTest`, `XtrmEnrollmentServiceTest`, `XtrmBankServiceTest`, `RedemptionProfileControllerTest`.
- [ ] **Step 3:** grep `user_redemption|UserRedemption|USER_REDEMPTION` = **zero** (backend `src` + contracts). grep entity-field `user_linked_bank_id` = **zero**; `userLinkedBankId` should remain **only** in `XtrmApiClient`/`XtrmApiClientImpl` + the two `cmd.userLinkedBankId()` test asserts.
- [ ] **Step 4:** Update the parent plan `2026-07-03-…` to record the confirmed model + renames + embeddable.

---

## Done When

- [ ] Table `partner_redemption`, entity `PartnerRedemption`, repo `PartnerRedemptionRepository`.
- [ ] Column `partner_linked_bank_id` + entity field `partnerLinkedBankId`; **XTRM `userLinkedBankId`/`"UserLinkedBankID"` untouched**; `recipient_user_id` + `linked_bank_label` unchanged.
- [ ] Address is one `@Embedded PartnerAddress` mapped to the same 6 columns; all reads null-safe; DTOs/API/response JSON stay flat.
- [ ] `AuditResourceType.PARTNER_REDEMPTION` everywhere (backend + contracts); no `USER_REDEMPTION` literals remain.
- [ ] `XtrmVendorService.dispatch` **still** throws `COMPANY_PAYOUT_NOT_SUPPORTED` for `walletType == COMPANY`; test still asserts it.
- [ ] Contracts (model/yaml/enums) reflect the renames — no source-of-truth drift.
- [ ] `compileJava` + `compileTestJava` clean; targeted tests green; grep checks pass.
- [ ] Nothing committed until you say so (branch `features/redemption-xtrm-payout-enhancement`; contracts on its matching branch).

## Not changing

- **No dispatch/logic change** — company-wallet payout stays deferred; guard stays.
- **No DB column change except the one rename** — 20 columns, same types; `partner_linked_bank_id` is the only renamed column; address columns keep their names (grouped in Java only).
- **No API-shape or frontend change** — DTOs/response JSON stay flat (FE/admin verified 0 refs). Contracts docs update for consistency only.
- **XTRM API naming preserved** — `userLinkedBankId` / `"UserLinkedBankID"` unchanged.
- No new permissions or feature flags.

## Rollback

All edits local on `features/redemption-xtrm-payout-enhancement` (backend) + matching contracts branch, uncommitted; `git restore` / `git clean` reverts. File renames of V34 + the contracts model doc are tracked by git.
