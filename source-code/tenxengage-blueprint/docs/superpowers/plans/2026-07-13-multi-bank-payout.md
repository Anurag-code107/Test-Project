# Multiple Bank Accounts + Selectable Default — Payout (F-03 enhancement)

- **Status:** draft
- **Date:** 2026-07-13 (table design — reverted from table-free after review)
- **Type:** Enhancement to F-03 (redemption-flow) payout profile — plan doc + enhancement branch.
- **Branch:** `features/redemption-xtrm-payout-enhancement` (confirmed 2026-07-13).

## Summary
Today a user can link **one** bank account ("Replace bank account"), stored as a single `partner_linked_bank_id` on `partner_redemption`. This makes it **many**:
- **Add as many bank accounts as they want** ("Add another bank account", not "Replace").
- **Remove any specific** account.
- **Pick a default** bank. When the payout method is **Bank**, payouts go to the **default** bank.

> **⭐ v1 SCOPE (2026-07-13): USD → USD only.** Banks are **US / ACH / USD**; **India/WIRE + per-bank multi-rail DEFERRED** (every bank is ACH → no per-bank payment-method logic).

> **⭐ DESIGN: local TABLE for banks (decided 2026-07-13).** We persist a **`partner_linked_bank`** row per bank so the list is a **fast local read** — **no `GetLinkedBankAccounts` network round-trip per Payout-tab view** (XTRM latency + fragility). *(The OAuth token itself is app-level cached ~1h in `XtrmApiClientImpl`, so it's mainly the list round-trip we skip — plus a token fetch when the cache is cold.)* XTRM still holds the beneficiary (add/remove go to XTRM too, reusing the cached token), and we keep our table in sync on those writes. **The DEFAULT stays on the existing `partner_redemption.partner_linked_bank_id` column** so the **payout path is unchanged** (verified) and "one default" is guaranteed by a single value. *(Wallets stay table-free/view-only — we're not creating wallets yet.)*

## Current vs desired
| | Current | Desired |
|---|---|---|
| Bank list | single ref on `partner_redemption` | **many rows in `partner_linked_bank`** (local, fast) |
| Default | the one linked bank | `partner_redemption.partner_linked_bank_id` = the default's `BeneficiaryId` (+ `linked_bank_label`) |
| Add | "Replace" (overwrites) | **"Add another bank account"** — `LinkBankBeneficiary` at XTRM + insert local row |
| Remove | forgets our ref | remove a **specific** bank — `DeleteBankBeneficiary` at XTRM + soft-delete row |
| Payout (BANK) | uses `partner_linked_bank_id` | **unchanged** — still that column (= the default) |
| Profile response | `bankLinked` + `linkedBankLabel` | **unchanged**; full list via separate `GET /banks` (from our table) |

## XTRM API  *(all shapes CONFIRMED against sandbox 2026-07-13)*
- **`LinkBankBeneficiary`** (add) — request sends PAT as `UserID`. Response returns **only** `BeneficiaryId` + `BankBeneficiaryStatus` + `AccountIdentityLevel` + `ACHDebitApprovalStatus` — **NO masked account / bank name.** ✅ So on insert, **build `masked_label`/`currency`/`country_iso2`/`withdraw_type` from the add-form input**, not from this response.
- **`DeleteBankBeneficiary`** (remove) — `POST /API/v4/Bank/DeleteBankBeneficiary`, `{DeleteBankBeneficiary:{request:{IssuerAccountNumber, RecipientAccountNumber(=PAT), BeneficiaryBankID}}}`. ✅ Shape confirmed. **PAT field = `RecipientAccountNumber`.** `BeneficiaryBankID` = **the stored `BeneficiaryId`** (Link↔GetLinkedBankAccounts share one `BeneficiaryId` space — verified: linked `2bf492…` appears in the list). ⚠️ Residual: one live delete of a KNOWN `BeneficiaryId` to confirm Success + drop-from-list (low risk, one field). Parse `OperationStatus.Success` like other calls.
- **`GetLinkedBankAccounts`** (reconcile only — NOT normal listing) — request PAT field = `RecipientUserId`. Response = `…GetLinkedBankAccountsResult.Beneficiary.BeneficiaryDetails[]`, each: `BeneficiaryId`, `BeneficiaryName`, `Currency`, `Country` (⚠️ FULL name "United States of America", NOT ISO2), `PaymentMethods.Payment` (ACH/WIRE), `BankDetails.BeneficiaryBankInformation.{AccountNumber (masked "XXXX7871"), BankName, BankRoutingCode}`. Used only to compare `BeneficiaryId` sets and catch out-of-band deletes.
- **⚠️ PAT field name differs per call** (XTRM inconsistency, all confirmed): `UserID` (LinkBank) · `RecipientAccountNumber` (DeleteBank) · `RecipientUserId` (GetLinkedBankAccounts) · `BeneficiaryAccountNumber` (GetBeneficiaryWallets). `XtrmApiClient` must use the exact name per call.
- **`TransferFund`** (bank rail) → `UserLinkedBankID` = default's `BeneficiaryId` — **unchanged** (`XtrmVendorService.dispatch` already reads `partner_linked_bank_id`).

## Design

### Data model
- **New table `partner_linked_bank`:** `id` (PK), `client_id`, `user_id`, `xtrm_beneficiary_id`, `masked_label`, `currency`, `country_iso2`, `withdraw_type`, `created_at`, `updated_at`, `deleted`.
  - Unique `(client_id, user_id, xtrm_beneficiary_id)` **as a PARTIAL index `WHERE deleted = false`** (belt-and-suspenders vs XTRM's own duplicate rejection) — partial so a **re-add after a soft-delete can't collide** if XTRM ever reissues a `BeneficiaryId`.
  - Entity follows codebase conventions: `extends BaseEntity implements TenantAware`, `@Filter` tenant scope on `client_id`, standard soft-delete `deleted` flag. `masked_label` length **100** (matches `maskLabel`'s cap). `currency` defaults **`USD`** — it's NOT on the add-form (ACH is USD-implicit in v1).
  - **No `is_default` column** — the default lives on `partner_redemption.partner_linked_bank_id` (single value → one default free; keeps payout unchanged). `isDefault` in the list = `row.xtrm_beneficiary_id == partner_redemption.partner_linked_bank_id`.
- **Migration V35:** create the table; **migrate** each existing `partner_redemption.partner_linked_bank_id` + `linked_bank_label` into a row (backfill `currency`/`country_iso2`/`withdraw_type` = `USD`/`US`/`ACH`). Keep `partner_linked_bank_id` on `partner_redemption` as the default pointer (already populated for existing users → they become their own default, no data loss).

### Backend
- **`XtrmApiClient`** (+ `@Profile` stub): add **`deleteBankBeneficiary(cmd)`** (`linkBankBeneficiary` exists).
- **`XtrmBankService`:**
  - `addBank` (was `linkBank`): **lazy-enroll** (`ensureEnrolledForPayout`) → `LinkBankBeneficiary` (**outside the tx**, as today) → then persist: **insert a `partner_linked_bank` row** — store the returned `BeneficiaryId`; `masked_label = maskLabel(request)` (existing helper: `institutionName` + `••`+last4), `withdraw_type = request.withdrawType()`, `country_iso2 = request.countryIso2()`, `currency = "USD"` (v1). ⚠️ **Set the default (`partner_linked_bank_id`) ONLY when it's currently null** (first bank) — today's `linkBank` overwrites it **unconditionally**, and that overwrite IS the "replace" behavior we're removing. Duplicate → `XTRM_BANK_DUPLICATE` (mapped in `classifyBankError`, friendly copy exists).
  - `listBanks(userId)` → **read our table** (fast, no XTRM). Mark `isDefault` by matching `partner_linked_bank_id`. *(No enrollment guard needed — it's a local read.)*
  - `removeBank(userId, bankId)` → resolve the row by our **PK** (tenant-scoped) → `DeleteBankBeneficiary` at XTRM (**outside tx**; idempotent on not-found; transient failure → keep row + retry error) → **soft-delete the row**. ⚠️ **Behavior change:** today's `removeBank` does NOT call XTRM (it just nulls the ref — the beneficiary lingers at XTRM); now we actually delete it there. If the removed bank was the default: **other rows remain → auto-promote the oldest remaining** (`created_at` asc — deterministic) as the new `partner_linked_bank_id` (+ its label); **none remain →** clear it and, if method = BANK, **reset to ANYPAY**. (Invariant: has banks ⇔ has default.)
  - `setDefaultBank(userId, bankId)` → set `partner_redemption.partner_linked_bank_id` (+ label) = that row's `xtrm_beneficiary_id`.
- **`XtrmVendorService.dispatch` (BANK rail):** **UNCHANGED.**
- **`RedemptionProfileController`** — under `/api/v1/redemption/profile`, **gate every method on `ANY[action.redemption.redeem, action.redemption.redeem_company]`** via **`@RequiresPermission(logic = ANY)`** (the existing 5 handlers use this custom annotation, NOT `@PreAuthorize` — copy it verbatim). ⚠️ NOT `redeem` alone — PARTNER_ADMIN holds only `redeem_company` (V17 seed):
  - `GET /banks` → list from our table (**local — no XTRM, no rate-limit needed**).
  - `POST /bank-account` → **add** (append; hits XTRM → rate-limited, as today).
  - `DELETE /banks/{bankId}` → remove (hits XTRM → **rate-limit it too**; note `RateLimitFilter` uses exact-path match and can't match a path variable — extend it to a prefix/pattern or add an explicit rule).
  - `PUT /banks/default` → set default (local write, no XTRM).
- **`RedemptionProfileResponse`:** **unchanged** — keep `bankLinked` (= `partner_linked_bank_id` set) + `linkedBankLabel` (= default's label). With the auto-promote invariant, `bankLinked` is a reliable BANK-radio flag. Full list via `GET /banks`. **No breaking DTO change.**

### Contracts
- Add `GET /banks`, `DELETE /banks/{bankId}`, `PUT /banks/default` + a `LinkedBank` model (`{ id, label, currency, isDefault }`) in `redemption-payout.yaml`; note `POST /bank-account` is now additive; **remove the old single `DELETE /bank-account`**. `RedemptionProfileResponse` unchanged.
- **Identifier rule:** `bankId` in the API = **our table PK (`id`)**, NEVER the raw `xtrm_beneficiary_id`. The BE resolves the beneficiary id server-side; `PUT /banks/default` takes `{ bankId }` in the body. The FE never sees XTRM ids.

### Frontend (`PayoutTab.tsx`)
- **Bank account card:** render the list from `GET /banks` — each row: label (name + masked acct) + **Default** badge/radio + **Remove**. Replace the single-bank block + "Replace bank account" with **"Add another bank account"** (`LinkBankForm` unchanged).
- **Payout method = Bank:** enabled when `bankLinked`; show/select the default.
- New `useLinkedBanks` list hook; change `removeBankAccount()` in `services/redemption-payout/redemption-payout.service.ts` from no-arg to take a `bankId`; **invalidate the `useLinkedBanks` query** after add/remove/set-default (mutations return only `RedemptionProfileResponse`, not the list). Update `PayoutTab.test.tsx`.

## Phases
1. ✅ **DONE (2026-07-13) — BE data model + service.** `partner_linked_bank` entity + repo + **V35 migration** (partial unique index `WHERE deleted=false` + backfill), `XtrmApiClient.deleteBankBeneficiary` (+ impl + stub, confirmed shape), `XtrmBankService` addBank/listBanks/removeBank(userId,bankId)/setDefaultBank, `LinkedBankResponse` + `SetDefaultBankRequest` DTOs, controller `GET /banks` + `POST /bank-account` (additive) + `DELETE /banks/{bankId}` + `PUT /banks/default` (all `ANY[redeem, redeem_company]`, old `DELETE /bank-account` removed), `RateLimitFilter` prefix rule for `/banks/{id}`. Tests: `XtrmBankServiceTest` (15 cases) + `RedemptionProfileControllerTest` updated. **Full backend `test` suite GREEN** (compile clean; V35 + entity mapping validated on context load). *(Payout path untouched.)* Not committed yet.
2. ✅ **DONE (2026-07-13) — Contracts.** `tenxengage-contracts/endpoints/redemption-payout.yaml`: added `GET /banks`, `DELETE /banks/{bankId}`, `PUT /banks/default` + `LinkedBank` + `SetDefaultBankRequest` schemas; `POST /bank-account` reworded additive; old `DELETE /bank-account` removed. YAML validated (parses — 7 paths, 9 schemas); matches BE DTOs + `x-permission-any` + `x-audited`. Not committed yet.
3. ✅ **DONE (2026-07-13) — Frontend.** `redemption-payout.types` (`LinkedBank` + `SetDefaultBankRequest`); service `listBanks`/`removeBankAccount(bankId)`/`setDefaultBank`; hooks `useLinkedBanks` + `useSetDefaultBank` + list-invalidation on add/remove/default; `PayoutTab` bank **list** (default radio + `Default` badge + per-row Remove) + "Add another bank account"; `PayoutTab.test` reworked. **Full FE suite GREEN (705 tests / 102 files) + `tsc -b` clean.** `LinkBankForm` unchanged. Not committed yet.

**All 3 phases implemented + tests green (BE + contracts + FE). Nothing committed. Remaining before MR:** local end-to-end manual test (incl. the one residual live delete to confirm `BeneficiaryBankID`==`BeneficiaryId`), then commit across repos + per-repo `/ready-check` + MR into `roadmaps/redemption-store`.

## Done When
- Add ≥2 banks (rows persisted; list loads **without an XTRM call**); remove any specific one (gone at XTRM + soft-deleted locally); set the default.
- A BANK-method **INSTANT/APPROVAL** redemption pays the **default** bank (already wired).
- **Partner ADMIN** (holds `redeem_company`) can use all endpoints (not 403).
- Edge tests: remove-default-with-others → auto-promote; remove-last → ANYPAY; duplicate-add → `XTRM_BANK_DUPLICATE`, no dup row; XTRM delete failure → keep row + retry; migration moves the existing bank as default.
- BE + FE tests green; contract synced.

## Open questions / risks
1. ~~`DeleteBankBeneficiary` shape / PAT field name~~ — ✅ **CONFIRMED 2026-07-13** (PAT = `RecipientAccountNumber`, bank id = `BeneficiaryBankID` = stored `BeneficiaryId`). Residual: one live delete of a known id to confirm `BeneficiaryBankID == BeneficiaryId` end-to-end (low risk). Delete success/not-found response bodies still to be captured, but parse `OperationStatus.Success` like every other call.
2. **Table↔XTRM sync / reconciliation** — the local table can drift if a bank is deleted out-of-band at XTRM. Mitigate: on a BANK payout, XTRM `beneficiary not found` → mark the row stale + prompt re-select; optionally a periodic `GetLinkedBankAccounts` reconcile. (Low risk — users don't have direct XTRM access.)
3. **Rate-limit filter** — `RateLimitFilter` is exact-path; extend it to cover `DELETE /banks/{bankId}` (path variable). `GET /banks` + `PUT /banks/default` are local, no limit needed.
4. **⚠️ BATCH + BANK (pre-existing, out of scope):** `XtrmApiClientImpl.batchTransfer` sends **no `UserLinkedBankID`** per item — a BANK-default user redeeming via a **BATCH** item pays AnyPay, not their bank. Not introduced here; "default bank" strictly routes for **INSTANT/APPROVAL**. Flag for a separate fix.
5. **Rail per bank (ACH vs WIRE)** — deferred multi-country (v1 all ACH/USD); `withdraw_type` stored per row for forward-compat.
6. **Column rename (optional):** `partner_linked_bank_id` → `default_linked_bank_id` for clarity — later cosmetic migration.
7. **Overlaps the digital-wallet plan** — build sequentially, multi-bank first.

## Review notes (from independent review, 2026-07-13)
- 🔴 **Permission fix applied** — endpoints gate on `ANY[redeem, redeem_company]` (admin holds `redeem_company`).
- 🟡 Applied: delete-shape caveat, FE cache-invalidation + `removeBankAccount(bankId)`, old `DELETE /bank-account` removal, BATCH+BANK caveat, rate-limit `DELETE /banks/{id}`.
- ✅ **Resolved by the table design:** `GET /banks` no longer hits XTRM (no rate-limit / no enrollment-guard concern), and `addBank`'s stale-default round-trip is gone (the table is our truth; default-null check only).

### Code-verified (2026-07-13)
Every backend assertion in this plan was fact-checked against source (parallel review):
- ✅ `partner_redemption.partner_linked_bank_id` + `linked_bank_label` exist (`PartnerRedemption.java:73-78`, born in V34).
- ✅ Payout reads that column → `UserLinkedBankID` (`XtrmVendorService.java:84-101` → `XtrmApiClientImpl.java:130`) → default-on-column = payout unchanged.
- ✅ All 5 existing handlers already gate `ANY[redeem, redeem_company]` via `@RequiresPermission(logic=ANY)`; `POST`+`DELETE /bank-account` already exist.
- ✅ `bankLinked` + `linkedBankLabel` on `RedemptionProfileResponse`; `linkBankBeneficiary` exists, no `deleteBankBeneficiary` yet.
- ✅ `batchTransfer` omits `UserLinkedBankID` and `BatchItem` has no field for it (`XtrmApiClientImpl.java:167-172`) → BATCH+BANK caveat is real.
- ✅ `XTRM_BANK_DUPLICATE` already in `classifyBankError` (`:412-418`); `RateLimitFilter` is exact `.equals` (`:84-90`, map comment misleadingly says "prefix"); latest migration V34 → V35 free.
