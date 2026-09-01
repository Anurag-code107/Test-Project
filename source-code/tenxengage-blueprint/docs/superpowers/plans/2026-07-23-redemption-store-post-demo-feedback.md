# Redemption Store — Post-Demo Feedback — Enhancement Plan

**Created:** 2026-07-23 · **Branch:** `features/redemption-store-feedback` (cut from `roadmaps/redemption-store` after the XTRM payout enhancement was merged into it, 2026-07-23)
**Status:** PLAN ONLY — not yet implemented (author requested "plan first"). Reviewed 2026-07-23 (three passes; incl. the two-rail payment-model pivot); 8 fixes folded in (tagged "fix #n"). **All review-pass-3 decisions resolved (OD-1/2/3) — implementation-ready.**
**Scope:** Enhancement to F-03 (redemption + catalog). No new spec — plan doc per the enhancement workflow. 12 post-demo feedback items (11 in scope, 1 parked).
**Worktrees:** FE = `tenxengage-frontend` (this branch). BE = `tenxengage-backend`. Contracts = `tenxengage-contracts`. Plan doc = `tenxengage-blueprint`.

---

## Decisions locked (2026-07-23, @pushpendra)

- **Payment-mode toggle:** a top-right toggle on the redemption store — **Bank Transfer ↔ Gift Card** — for **partner admin + partner seller**. Bank Transfer = link/pick bank + enter amount (min $1); Gift Card = browse gift-card catalogs. **Bank transfer is personal-only** (pays into the user's own linked bank; company bank transfer deferred, consistent with `COMPANY_REDEMPTION_ENABLED` off) — fix #7.
- **Bank-transfer backing card — PER-CLIENT (not global):** one reserved catalog row **per client** (`owner_client_id = that client`), flagged `is_bank_transfer`, category CASH / currency cash / min $1, hidden from browse **and** the client-admin catalog list. **Lazily created (idempotent, one per client) on that client's first bank link.** Redemption uses the **normal owner-scoped path and the existing redeem guard — NO bypass** (this is why per-client beats global: the `clientId == ownerClientId` guard stays universally enforced).
- **Bank-transfer empty state:** if the user has no linked bank, show a meaningful message + a **"Link a bank account"** CTA that navigates to **`/settings/profile?tab=payout` with the Bank sub-tab pre-selected**. User **stays on the Payout page** after linking (no auto-return).
- **Redeem drawer:** everything **inline in the right-side drawer** — rename "Minimum amount" → **"Desired Amount"**, add an amount input, submit in the drawer. **Remove the secondary popup** (`RedemptionSubmitModal`).
- **Catalog card:** remove currency; larger amount; label **"Starting at $X"** (it's the minimum).
- **Catalog-creation form:** CASH category **stays** (for XTRM cash-type digital gift cards); **SKU / Provider Item ID mandatory for ALL form-created catalogs** (CASH + NON_CASH), SKU == `providerItemId`; **revert** the just-shipped "hide Provider Item ID for CASH" (commit `842cdd1b`).
- **Transaction detail:** show reviewer **name** instead of ID; relabel **"Vendor reference" → "Payment Transaction ID"** (same `vendorReferenceId` value).
- **Transaction history Actions:** NON_CASH → Request Return (as today); **CASH → "N/A"**.
- **Payment model — TWO rails only, selected by ACTION (2026-07-23 update):** the user does **not** pick a payout method. Redeeming a **Digital Gift Card** runs the XTRM gift-card *TransferFund* API (keyed on the item SKU); toggling **Bank Transfer** runs the XTRM bank *TransferFund* API (pays the linked bank). The rail is bound to the redemption **type**, not a stored `payoutMethod`. **CARD / ANYPAY method selection is dropped from the UX.** This **supersedes fix #8** (a bank transfer can no longer follow a stored card/anypay method). **Dispatch integration finalized from the two curls (2026-07-23) — see §0.6:** both hit `/API/v4/Fund/TransferFund`, differing only by `PaymentMethodID` (bank `XTR94500` + `UserLinkedBankID`; gift card `XTR94505` + `SKU` + `UserGiftCardEmailID`); response `PaymentTransactionId` → `vendorReferenceId`.
- **CARD / ANYPAY removed; NON_CASH retained (2026-07-23):** Card-linking + Digital-Wallet (ANYPAY) UI and the payout-method selector are **removed**; dispatch does gift-card + bank only. **NON_CASH catalogs stay** (future Xoxoday; Xoxoday sync remains disabled — out of scope now), so item F's NON_CASH→Return / CASH→N/A split is unchanged. **Digital gift card:** debits the user's **cash reward wallet** (same funding as bank transfer), delivered to the user by **email**.
- **BE teardown depth — CONFIRMED: UI-only removal now, full teardown later.** For this batch, **only the CARD/ANYPAY UI is removed**. Keep the `partner_linked_card` table, `RedemptionPayoutMethod` enum values, card-linking endpoints/services, and the existing dispatch branches **dormant** (no destructive migration; card-instruments feature `cce73ca` stays intact). **The one non-UI change still required:** `XtrmVendorService.dispatch` routes by redemption **type** (§0.6) so the two active rails don't consult the (now unset) `payoutMethod`; the dormant CARD/ANYPAY branches simply become unreachable via the live flows.
- **PARKED:** add-quantity in the redeem drawer (#7) — awaiting Vijay.

### ⚠️ Findings from the code map that change the shape of the work
1. **History list rows carry NO `category`** (`RedemptionRequestResponse` has no category field) → **F needs a BE + contract addition** (expose `category` on the list DTO), not just an FE tweak.
2. **`reviewedBy` is a raw UUID** on `RedemptionRequestDetailResponse` (no name) → **E#8 needs a BE + contract addition** (resolve approver name via `UserRepository`; precedent = `ApprovalQueueItemResponse.requestingUserDisplayName`).
3. **"Rejected" = status `CANCELLED`** (there is no `REJECTED` enum value); the sheet already renders `rejectionReason`. E#8 must cover both COMPLETED and CANCELLED.
4. **The "Payout page" is a tab** (`/settings/profile?tab=payout` → `MyProfilePage` → `PayoutTab`), and its **inner Bank/Card/Wallet sub-tabs are uncontrolled** (`defaultValue` off `payoutMethod`). Pre-selecting Bank requires making that inner `Tabs` **URL-controllable**.
5. **The store has no bank-vs-giftcard concept today** — only a `category` CASH/NON_CASH badge. The toggle is genuinely new.
6. **Redeem owner guard is in-memory** (`findById().filter(clientId.equals(ownerClientId))`) at `RedemptionSubmissionService` personal L136-140 / company L308-312 — the bank-transfer card (owner = client) passes it unchanged.

---

## Section 0 — Backend foundation: per-client bank-transfer card

The structural core. Everything in Section A depends on this.

### 0.1 Migration — `V45__add_is_bank_transfer_to_catalog_items.sql` (BE)
- `ALTER TABLE redemption_catalog_items ADD COLUMN is_bank_transfer BOOLEAN NOT NULL DEFAULT FALSE;`
- **Partial unique index** guaranteeing at most one per client:
  `CREATE UNIQUE INDEX uq_catalog_bank_transfer_per_client ON redemption_catalog_items (owner_client_id) WHERE is_bank_transfer = TRUE AND deleted = FALSE;`
- (Next version after `V44`.)

### 0.2 Entity + repository (BE)
- `RedemptionCatalogItem`: add `@Column(name="is_bank_transfer", nullable=false) @Builder.Default private boolean isBankTransfer = false;`
- `RedemptionCatalogItemRepository`:
  - **Exclude the card from every catalog-facing read** — add `AndIsBankTransferFalse` to the owner-scoped reads used by browse + admin list: `findByOwnerClientIdAndDeletedFalse`, `findByOwnerClientIdAndCategoryAndIsActiveAndDeletedFalse`, `findByOwnerClientIdAndCurrencyIdInAndIsActiveAndDeletedFalse`, `findByOwnerClientIdAndIsActiveAndDeletedFalse`, `searchByNameForOwner` (add `AND e.isBankTransfer = false` to the `@Query`). (Rename with care + update the ~call sites in `RedemptionCatalogAdminService` / `RedemptionCatalogBrowseService` and their tests — mirror the V44 `AndDeletedFalse` rename pass.)
  - **Add a dedicated finder** for the payout path: `Optional<RedemptionCatalogItem> findByOwnerClientIdAndIsBankTransferTrueAndDeletedFalse(UUID ownerClientId)`.

### 0.3 `BankTransferCardService` — idempotent get-or-create (BE, new)
- `RedemptionCatalogItem ensureBankTransferCard(UUID clientId)` annotated **`@Transactional(propagation = REQUIRES_NEW)`** (mirrors the existing `stampDispatchAttempt`): return the existing card via the new finder, else build one — `ownerClientId=clientId, isBankTransfer=true, category=CASH, currencyId="cash", defaultMinRedemptionAmount=1.00, defaultProcessingMode=INSTANT, isActive=true, name="Bank Transfer", isReturnable=false, providerItemId=null`.
- **Concurrency (fix #2):** pre-check with the finder, insert, partial unique index as backstop. A unique-violation must **not** poison the caller's transaction — that's why creation runs in its **own `REQUIRES_NEW` transaction**; on violation, catch → re-read the existing row and return it.
- Not exposed through the catalog admin CRUD (system-managed).

### 0.4 Hook: create on first bank link (BE)
- `XtrmBankService.addBank(userId, request)` (L68-119) — after the local `PartnerLinkedBank` save, call `bankTransferCardService.ensureBankTransferCard(profile.getClientId())`. Idempotent, so "first link" detection is unnecessary; it simply guarantees the card exists once the client has any bank.
- **Non-fatal (fix #2):** wrap the call so a failure **never fails the bank link** (try/catch + log). Because it runs `REQUIRES_NEW`, a rollback there doesn't taint `addBank`. The **redeem-time safety net** (A.3 also calls `ensureBankTransferCard`) covers any miss.

### 0.5 Route the bank-transfer card through the dedicated path only (BE — fix #1)
- The hidden card must be redeemable **only** via `POST /redemption/requests/bank-transfer`, never via the public `POST /redemption/requests` (otherwise a caller who learns the card id could POST a normal redemption against it and bypass the "no linked bank" precondition).
- **`RedemptionSubmissionService`:** extract the **full post-fetch submission core** into a private `doSubmit(RedemptionCatalogItem item, RewardWallet wallet, BigDecimal amount, WalletType type, /* + company context: partnerCompanyId */ ...)` — i.e. **everything after the item fetch**: min-amount + min-wallet-balance + insufficient-balance checks, the **in-flight-redemption limit**, **idempotency**, request creation, reservation, ledger write, and after-commit dispatch. **Not just reservation→ledger→dispatch** — the bank-transfer path must inherit ALL these guardrails, not a subset. The public `submitPersonalRedemption` (L136-140) / `submitCompanyRedemption` (L308-312) keep their `findById(...).filter(isActive).filter(!isDeleted).filter(owner)` fetch and **add `.filter(item -> !item.isBankTransfer())`**, then delegate to `doSubmit`.
- The dedicated endpoint (A.3) resolves the card via `findByOwnerClientIdAndIsBankTransferTrueAndDeletedFalse` and calls `doSubmit` **directly** — so the guard blocks the public path without blocking the intended one. (Fallback if the `doSubmit` extraction is too invasive: a shared private submit with an `allowBankTransfer` flag — but the extraction is cleaner and preferred.)

### 0.6 Route dispatch by redemption TYPE — two rails, no payout-method selection (BE — supersedes fix #8; 2026-07-23 model update)
- **Model:** only two payout rails, chosen by the redemption *type*, **not** by `profile.getPayoutMethod()`:
  - **Digital gift card** (CASH catalog item with SKU) → XTRM **gift-card TransferFund** API (uses `providerItemId`); **debits the user's cash reward wallet**, delivered to the user by **email** (`UserGiftCardEmailID` = the user's email — validate present). Dispatch **auto-enrolls** the user (`ensureEnrolledForPayout`, `XtrmVendorService` L88-89), so a not-yet-enrolled user redeeming a gift card is enrolled on the fly — same as the bank path.
  - **Bank transfer** (`isBankTransfer` card) → XTRM **bank TransferFund** API (pays `partnerLinkedBankId`); debits the cash reward wallet.
  - **NON_CASH is a future rail:** retained for **Xoxoday** (sync stays disabled) and **out of scope** here — the current gift-card rail is XTRM **CASH** digital gift cards only.
- **Original review-pass-2 finding (now moot under this model):** the current `XtrmVendorService.dispatch` picks the rail from `profile.getPayoutMethod()` (BANK/CARD/ANYPAY; individual ~L97-124, batch ~L197-225, default `ANYPAY`). Under the old model a bank transfer could have paid out to a card/wallet. The new model removes that risk entirely — the rail is bound to the action.
- **Change:** refactor `XtrmVendorService.dispatch` (individual + batch) to branch on the redemption **type** (`isBankTransfer` → bank rail; gift-card SKU → gift-card rail) **instead of** `profile.getPayoutMethod()`. Per UI-only removal, **leave the CARD/ANYPAY branches in place but dormant** (unreachable via live flows — teardown later); the type router takes precedence so `payoutMethod` is never consulted for the two active rails.
- **API integration (finalized from the two curls, 2026-07-23):** both rails hit the **same** endpoint `POST /API/v4/Fund/TransferFund` with the **same response shape** — they differ only by `PaymentMethodID` and the per-detail destination field:
  - **Bank transfer:** `PaymentMethodID = XTR94500` (existing `redemption.xtrm.bank-payment-method-id`), `TransactionDetails[].UserLinkedBankID = partnerLinkedBankId`. **This is the existing BANK path — reuse as-is.**
  - **Digital gift card:** `PaymentMethodID = XTR94505` (**new** config `redemption.xtrm.gift-card-payment-method-id:XTR94505`), `TransactionDetails[].SKU = item.providerItemId`, `TransactionDetails[].UserGiftCardEmailID = redeeming user's email`. No bank/card destination.
  - Common Transaction fields (already sent today): `IssuerAccountNumber`, `PaymentType=Personal`, `WalletID` (issuer wallet), `PaymentCurrency=USD`, `EmailNotification=true`, `RecipientUserID = profile.recipientUserId` (PAT), `IssuerTransactionId = request id`, `PaymentAmount`.
- **`XtrmApiClient.TransferFundCommand`** gains `sku` + `giftCardEmail` (gift-card rail); the bank rail keeps `userLinkedBankId`. `XtrmVendorService.dispatch` selects the payment-method-id + which fields to populate by redemption **type**.
- **Response mapping (persistence unchanged):** `TransferFundResult.TransactionDetail[0].PaymentTransactionId` → stored as **`vendorReferenceId`** (exactly the value E#9 relabels to "Payment Transaction ID"); `BeneficiaryTransactionId` → `beneficiaryTransactionId` (reconciliation key). Gift-card `RedemptionDetails` (claim URL / instructions / brand image) is **delivered to the user by email** by XTRM — not surfaced in-app this batch (future).
- **Finalization unchanged:** the gift-card rail plugs into the same dispatch → PROCESSING → webhook/reconciliation settle flow (no new finalization logic). If XTRM returns `PaymentStatus=Completed` synchronously (as in the sandbox), reconciliation settles it. **Never log the XTRM bearer token / cookies; sandbox creds rotated out-of-band.**

### 0.7 Legacy CASH items — manual deactivation + dispatch guard (OD-1: (a))
- **No migration.** The author deactivates the legacy "cash payout" CASH items (no SKU) **manually via the client-admin Active/Inactive toggle** — they appear in the owner-scoped admin list, so a few clicks removes them from the store. (Deactivated items don't appear in the seller browse and can't be redeemed.)
- **Dispatch guard (§0.6 backstop — KEEP):** in the type-router, a **non-bank-transfer CASH item with no SKU** is un-routable → reject with a definitive `BusinessRuleException` (releases the reservation, marks FAILED) instead of calling the gift-card API with a null SKU. Cheap safety net if such an item is ever left active or re-activated.

---

## Section A — Redemption store payment-mode toggle — [#10, #11, #12, #5]

### A.1 Store toggle + mode panels (FE)
- **`src/pages/RedemptionStorePage.tsx`** — add a **top-right toggle** (Bank Transfer / Gift Card). Persist mode in a URL query param (`?mode=bank|giftcard`, default `giftcard`) so refresh/deep-link survive.
  - **Gift Card mode:** the existing `<CatalogBrowseGrid>` (unchanged).
  - **Bank Transfer mode:** a new `<BankTransferPanel>`:
    - `useLinkedBanks()` empty → **empty state**: message + **"Link a bank account"** button → `navigate("/settings/profile?tab=payout")` (Bank is the only sub-tab now — no `payoutSub` param needed; see §A.2).
    - bank linked → show default bank + an **amount input (min $1)** + **Submit** → new `useBankTransferRedeem()` hook → `POST /redemption/requests/bank-transfer`. On success, navigate to `/redemption/confirmation/{id}` (same as gift-card).

### A.2 Payout tab — remove method selection from the UI, keep bank linking (FE; 2026-07-23 — CONFIRMED, UI-ONLY)
- **Remove the payout-method selector from the UI** in **`src/components/redemption-payout/PayoutTab.tsx`**: remove the **Card** and **Wallet (ANYPAY)** sub-tabs (+ `AddCardForm`, `DigitalWalletsPanel`, `useLinkedCards` usage) and the "set default method" concept, and update the "Payouts go to" summary so it no longer surfaces the (now unselectable) method. PayoutTab keeps only **bank linking**; the inner `<Tabs>` collapses to a single bank section.
- **Consequence:** with Bank the only content, the earlier "make inner Tabs URL-controllable to pre-select Bank" work is **dropped** — the CTA just lands on `/settings/profile?tab=payout` showing bank linking (no `payoutSub` param).
- **BE stays dormant:** `partner_linked_card` table, ANYPAY enum values, and card-linking endpoints all remain (no teardown this batch) — only the UI is removed. The one required non-UI change is the dispatch type-routing (§0.6).

### A.3 Bank-transfer submit endpoint (BE)
- New `POST /api/v1/redemption/requests/bank-transfer` (in the redemption submission controller, same `@RequiresPermission` as `POST /redemption/requests`). Body `{ amount, walletId }` — **`walletId` REQUIRED (OD-3)**: the caller's cash INDIVIDUAL **`RewardWallet`** (our internal reward-balance wallet, **not** any XTRM wallet). **Personal-only (fix #7)** — company bank transfer deferred.
- Handler: resolve `clientId`/`userId`; **reject if the user has no default linked bank** (`PartnerRedemption.partnerLinkedBankId` blank → 409 "No bank linked"); `ensureBankTransferCard(clientId)` (redeem-time safety net); validate the required **`walletId`** is the caller's cash INDIVIDUAL `RewardWallet` (fix #3 — funding source; insufficient-balance + min-wallet-balance checks apply); call the shared `doSubmit(card, cashWallet, amount, INDIVIDUAL)` (0.5). Min $1 enforced by the card's `defaultMinRedemptionAmount`; owner guard passes; **dispatch routes to the bank rail by type (§0.6)** — `XTR94500` + `UserLinkedBankID`, to the user's linked bank. Returns the standard confirmation.
- **Assumption to confirm (fix #3):** partners hold cash-currency balances to fund bank transfer (the demo's CASH redemptions imply yes).
- **No new redeem logic** — reuses reservation, ledger, dispatch, reconciliation as-is.

---

## Section B — Catalog card display — [#3, #4, #5]

- **`src/components/redemption-catalog/CatalogItemCard.tsx`:**
  - **[#3]** Remove the currency-code span (`{item.currencyId.toUpperCase()}`, ~L44). Keep the Cash/Non-Cash category badge.
  - **[#4]** Make the amount prominent — bump from `text-sm text-muted-foreground font-medium` to e.g. `text-lg font-semibold text-foreground`.
  - **[#5]** Prefix the formatted amount: **`Starting at {getCurrency(item.currencyId).rewardFormat(item.effectiveMinTransactionAmount)}`**.
- **Note (minor, flag to author):** `CatalogBrowseGrid` groups items into sections by `currencyId` with an uppercased currency heading. #3 is about the *card*; the grid headings are left as-is unless you also want those removed.

---

## Section C — Redeem drawer (inline submit) — [#6]

- **`src/components/redemption-catalog/CatalogItemDetailSheet.tsx`** (the right drawer): fold the amount entry + submit **into the drawer**.
  - Rename the label "Minimum amount" (~L102) → **"Desired Amount"**, and render an **amount `<Input>`** (pre-filled to `effectiveMinTransactionAmount`, `min` = same) directly below.
  - Replace the "Redeem" button's `setRedeemModalOpen(true)` with an inline **submit** calling `useRedemptionSubmit` (personal path) using `{ catalogItemId, walletId, amount, currencyId }`. Keep the disabled/tooltip logic (`canAfford && wallet`) and the 422 inline field error.
  - On success → close drawer + `navigate('/redemption/confirmation/{id}')` (existing behavior).
- **Retire `src/components/redemption-flow/RedemptionSubmitModal.tsx`** from the store flow. **Before deleting (fix #5):** grep for other callers; update/remove its test; and ensure the inline drawer submit preserves **both** error paths the modal has today — **422** → inline field error on the amount, and **409** ("Maximum in-flight redemptions reached") → toast + in-flight handling (both surface via `useRedemptionSubmit`). Keep the file only if the gated "Redeem (Company)" path (behind `COMPANY_REDEMPTION_ENABLED`, off) still needs it.
- **[#7] add-quantity — PARKED** (out of scope; tracked as follow-up).

---

## Section D — Catalog-creation form: SKU mandatory + revert CASH hide — [#2, #12, #3]

- **FE `src/components/redemption-catalog/GlobalCatalogItemForm.tsx`:**
  - **Revert commit `842cdd1b`:** remove the `category !== "CASH"` wrapper around the Provider Item ID field (show it for CASH again) and remove the "clear providerItemId on switch to CASH" effect.
  - **Make Provider Item ID / SKU required for ALL categories** — Zod `providerItemId: z.string().min(1, "SKU is required").max(255)` (drop `.optional()`); show the field for both CASH and NON_CASH; relabel to make "SKU" explicit if desired.
- **BE `CreateRedemptionCatalogItemRequest`:** make `providerItemId` `@NotBlank` (creation only). The programmatic bank-transfer card is built via `BankTransferCardService` (not this request DTO), so it's unaffected. Existing rows unaffected (validation is create-time). The create-service uniqueness check (`findByOwnerClientIdAndProviderItemIdAndDeletedFalse`) still applies.

---

## Section E — Transaction detail — [#8, #9]

### E.1 Reviewer name instead of ID (BE + contract + FE) — [#8]
- **BE:** `RedemptionRequestDetailResponse` — add `String reviewedByName`. **`from(...)` gains a `reviewedByName` param (fix #6)** — pin its exact caller (the detail endpoint's service, e.g. `RedemptionHistoryService.getRedemptionDetail`) and update every `from(...)` call site. That caller resolves `reviewedBy` (UUID) → `UserRepository.findById(...)` → `firstName + " " + lastName` (null-safe; falls back to the id/`—` if the user is gone; single lookup, no N+1 since detail is one record). Mirror `ApprovalQueueItemResponse.requestingUserDisplayName`. Applies to both COMPLETED and CANCELLED (rejected) details.
- **Contract:** `tenxengage-contracts` redemption detail schema — add `reviewedByName`.
- **FE:** `redemption-history.types.ts` add `reviewedByName?: string`; **`TransactionDetailSheet.tsx`** — the "Reviewed by" row renders `data.reviewedByName ?? data.reviewedBy`.

### E.2 "Vendor reference" → "Payment Transaction ID" (FE only) — [#9]
- **`TransactionDetailSheet.tsx`** — change the label string "Vendor reference" → **"Payment Transaction ID"**. Value stays `data.vendorReferenceId` (which stores the XTRM transfer-fund `paymentTransactionId`). No BE/contract change.

---

## Section F — Transaction history Actions column — [#1]

- **BE:** `RedemptionRequestResponse` (list DTO) — add `RedemptionCategory category`, mapped from `RedemptionRequest.getCategory()` in its `from(...)`. This covers the **own-history** personal + company lists. **(Fix #4: the tenant-wide admin table `TenantTransactionHistoryPage` has no Return action and is out of scope for F.)** Null category → the Actions cell renders nothing (safe fallback).
- **Contract:** `redemption-history.yaml` — add `category` to the list-item schema.
- **FE:** `redemption-history.types.ts` — add `category?: RedemptionCategory` to `RedemptionRequestResponse`. **`TransactionHistoryTable.tsx`** Actions cell:
  - `category === "CASH"` → render **"N/A"** (muted).
  - `category === "NON_CASH"` → the existing **"Request Return"** button when `isReturnEligible`, else nothing.
  - Column still gated on the return permission (as today); this only changes what renders inside it.
- Applies to partner seller + partner admin own-history (`TransactionHistoryPage` → `TransactionHistoryTable`).

---

## Execution approach — SINGLE PASS, then the full test suite

Implement the **entire plan in one pass** — all sections, all repos — **not** section-by-section with review pauses. Internal build order is dependency-driven only:
1. **Section 0 (BE foundation)** — V45, entity/repo exclusion + finder, `BankTransferCardService` (`REQUIRES_NEW`), `XtrmBankService` hook (non-fatal), `doSubmit` extraction + `!isBankTransfer` guard, dispatch type-router + backstop guard. (Everything else depends on it.)
2. **Section A** — bank-transfer endpoint (BE) → store toggle + `BankTransferPanel` + PayoutTab method-selector removal (FE).
3. **B + C + D + E + F** — catalog card, inline redeem drawer, SKU-required form (+ revert CASH hide), reviewer name + "Payment Transaction ID", history `category`/Actions.

Then run the **full test suite once at the end** (see Test plan): JUnit/BE-unit → FE automated (Vitest) → E2E (Playwright) → **integration tests LAST**.

Commits grouped per repo (backend / contracts / frontend), explicit CRLF-safe paths. Contract changes (E `reviewedByName`, F `category`) land in the `tenxengage-contracts` submodule first, then bump the pointer in BE + FE. The dev-only `controller/dev` package is version-controlled (all `@Profile("local")`).

---

## Test plan — run ONCE at the end, integration LAST

Run order: **JUnit** (BE unit — JUnit 5 + Mockito, XTRM/repos mocked) → **FE automated** (Vitest + Testing Library) → **E2E** (Playwright, backend-mocked) → **BE-int** (integration, real stack/DB) **last**.

### 1. JUnit / BE unit tests (Mockito)
- BU-1 `ensureBankTransferCard` creates one card (CASH / cash / min $1 / `isBankTransfer` / active); a second call returns the same row (idempotent).
- BU-2 owner-scoped browse + admin-list reads exclude the bank-transfer card.
- BU-3 `XtrmBankService.addBank` provisions the card on first link (mocked XtrmApiClient); non-fatal on failure; no second card for a second user in the same client.
- BU-4 public `POST /redemption/requests` (personal + company) rejects the bank-transfer card (`!isBankTransfer` guard).
- BU-5 dedicated bank-transfer endpoint: linked bank + amount ≥ $1 → PROCESSING; amount < $1 → below-min; no bank → 409; cross-client resolves only the caller's card; `walletId` validated as the caller's cash INDIVIDUAL `RewardWallet`.
- BU-6 dispatch type-router: bank redemption → `XTR94500` + `UserLinkedBankID`; gift-card redemption → `XTR94505` + `SKU` + `UserGiftCardEmailID`; response `PaymentTransactionId` → `vendorReferenceId`, `BeneficiaryTransactionId` stored.
- BU-7 dispatch backstop: non-bank-transfer CASH item with no SKU → definitive reject, reservation released (never calls the gift-card API with a null SKU).
- BU-8 create request: `providerItemId` blank → 400, present → 201 (both categories); existing create tests that omit it updated.
- BU-9 detail DTO: `reviewedByName` resolved via `UserRepository` for COMPLETED and CANCELLED; safe fallback when the user is missing.
- BU-10 list DTO: `RedemptionRequestResponse` carries `category`.

### 2. FE automated tests (Vitest + Testing Library)
- FE-1 store toggle switches `CatalogBrowseGrid` ↔ `BankTransferPanel`; mode persists in `?mode=`.
- FE-2 Bank Transfer + no bank → empty state + CTA navigates to `/settings/profile?tab=payout`.
- FE-3 Bank Transfer + bank linked → amount input (min $1) + submit → bank-transfer hook → confirmation nav.
- FE-4 PayoutTab shows only bank linking — Card + Wallet (ANYPAY) sub-tabs removed.
- FE-5 catalog card: no currency; larger amount; "Starting at $X".
- FE-6 redeem drawer: "Desired Amount" + inline submit, no secondary modal; success closes + navigates; below-min / insufficient → inline error (422) + in-flight (409) handling preserved.
- FE-7 create form: SKU shown + required for CASH and NON_CASH; submit without → error, with → payload carries it; the shipped CASH-hide test updated.
- FE-8 detail: reviewer name (fallback to id); "Payment Transaction ID" label with the `vendorReferenceId` value.
- FE-9 history Actions: CASH → "N/A"; NON_CASH eligible → "Request Return"; ineligible → empty; `TransactionHistoryTable` test updated.

### 3. E2E tests (Playwright, backend-mocked)
- E2E-1 store → toggle Bank Transfer with no bank → CTA lands on Payout → Bank tab → link bank → back in store → enter amount → submit → confirmation.
- E2E-2 store → Gift Card mode → open a card → drawer → enter Desired Amount → submit inline → confirmation.
- Note: FE E2E runs fine headless/backend-mocked; it may hang under the `claude -p` dispatch — invoke directly.

### 4. Integration tests — BE-int, RUN LAST (real stack / DB)
⚠️ **These run against the LIVE dev DB (`localhost:5432/tenxengage`, Flyway clean enabled) — get explicit OK before running; they can migrate/wipe dev data.**
- IT-1 V45 migration applies cleanly (column + partial unique index); the index actually **blocks a duplicate** bank-transfer card insert.
- IT-2 first real `addBank` → exactly one bank-transfer card provisioned per client.
- IT-3 end-to-end bank transfer: dedicated endpoint → reservation + ledger → dispatch (bank rail) → PROCESSING → webhook/reconciliation settle → COMPLETED; `vendorReferenceId` persisted. (Catches real-SQL bugs — e.g. the `lower(bytea)` class.)
- IT-4 end-to-end gift card: submit → dispatch (gift-card rail, SKU + email) → settle.
- IT-5 owner isolation over real queries: client B cannot redeem client A's bank-transfer card via the dedicated OR public path.
- IT-6 real-query checks: history list `category` + detail `reviewedByName` resolve over the actual repository queries.

### Regression guard (must stay green)
- BE `./gradlew test` — catalog / browse / submission / export / history / approval; the V44 + V45 rename/exclusion passes must not break existing owner-scoped tests.
- FE — `GlobalCatalogAdminPage`, `GlobalCatalogItemForm`, `PayoutTab`, `TransactionHistoryTable`/history, `CatalogItemCard`/`CatalogItemDetailSheet`.

---

## Done when
- **0/A:** first bank link provisions exactly one per-client bank-transfer card (hidden from browse + admin list); the store toggle switches modes; Bank Transfer with no bank shows the CTA → Payout Bank tab; with a bank, an amount ≥ $1 redeems to the linked bank via the existing CASH path (owner guard intact, no bypass).
- **B/C:** card hides currency, shows a bigger "Starting at $X"; redeem happens entirely in the drawer (no popup).
- **D:** SKU/Provider Item ID is shown+required for CASH and NON_CASH on create; the shipped CASH-hide is reverted.
- **E/F:** detail shows reviewer name + "Payment Transaction ID"; history Actions shows "N/A" for CASH and "Request Return" for eligible NON_CASH.
- All new + existing tests green; per-repo commits pushed to `features/redemption-store-feedback`.

## Decisions from review pass 3 (all RESOLVED 2026-07-23)

### OD-1 — Fate of existing CASH catalog items — RESOLVED: (a) deactivate, manual 🟢
**(a)** The new bank-transfer flow **replaces** the legacy "cash payout" catalog items → **deactivate/hide them.** Done **manually via the client-admin Active/Inactive toggle** (no migration — author's call). The §0.6 dispatch guard rejects any un-routable item as a backstop. (CASH items that already carry a valid gift-card SKU stay active as digital gift cards.)

### OD-2 — Gift card amount — RESOLVED: open-value 🟢
**Open-value** — gift-card SKUs accept any amount ≥ min. So §C's free **"Desired Amount"** input and §B's **"Starting at $X"** stand as written; **no denomination selector** needed.

### OD-3 — `walletId` on the bank-transfer endpoint — RESOLVED 🟢
`walletId` = **our internal `RewardWallet`** — the user's **cash reward-balance wallet, debited** (the funding source), the *same* wallet the gift-card redeem passes. It is **NOT** the XTRM issuer wallet (`WalletID=203871`, a fixed server-side `XTRM_WALLET_ID` config sent in the TransferFund body) and **NOT** an XTRM beneficiary wallet (the bank destination is the linked bank `UserLinkedBankID`, resolved server-side). **Make `walletId` REQUIRED** — the store already has it via `useMyWallets()`; the BE only validates it's the caller's cash INDIVIDUAL `RewardWallet`.

## Parked (follow-up)
- **#7** add-quantity in the redeem drawer — awaiting Vijay. When confirmed: extend the drawer submit (Section C) with a quantity control and decide totalling semantics (amount × quantity vs N codes).
