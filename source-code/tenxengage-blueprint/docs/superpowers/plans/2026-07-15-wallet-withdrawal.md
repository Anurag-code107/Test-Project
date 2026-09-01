# Card instruments + Wallet withdrawal — My Profile (F-03 payout enhancement)

- **Status:** draft
- **Date:** 2026-07-15
- **Type:** Enhancement to F-03 (redemption-flow) payout — plan doc + enhancement branch (NOT `/create-spec`).
- **Branch:** `features/redemption-xtrm-payout-enhancement` (confirm).

## Summary
Two connected capabilities, both built on **linked cards** (the multi-bank model mirrored for cards):
1. **Card as a payout rail** — link multiple cards, pick a **default**, and receive redemption payouts to it via `TransferFund` + `CardToken`. Payout method becomes **AnyPay / Bank / Card**.
2. **Wallet withdrawal (cash-out)** — a new **Withdraw tab**: move XTRM wallet balance to **any linked bank or card** via `UserWithdrawFund` (2-step OTP).

> **⭐ SCOPE (decided 2026-07-15): USD.** Linked **banks** (built) + linked **cards** (new) are both **dual-purpose** (receive-payout AND withdraw), managed together under **Payout**. **Card = PCI-DSS SAQ D** (server-side `LinkCard`, org accepts — XTRM has no hosted capture).
> **⭐ Default is SHARED, but only EDITABLE under Payout.** The **default bank / default card** is a persistent setting on `partner_redemption` (one per instrument type), **set only under Payout** (it's a settings choice). It drives the **payout rail** AND is the **pre-selected Withdraw destination**. **The Withdraw override is TRANSACTION-SCOPED** — picking a different linked account applies to *that* withdrawal only and does **NOT** rewrite the persistent default (avoids a one-off cash-out silently changing where future payouts land). *(Optional future: an explicit "make this my default" opt-in on the Withdraw picker.)*
> - **Phase A — Bank withdrawal** (no card, no PCI): ships first.
> - **Phase B — Card block:** B1 multi-card linking (Payout) · B2 card payout rail · B3 card withdrawal.

> **⭐ Money is XTRM-side.** Payout and withdrawal move funds inside XTRM (company wallet → user; user wallet → bank/card). Neither touches our `reward_wallets`/ledger (debited at redemption). We orchestrate the XTRM calls + record for history.

> **⭐ Fees differ by direction (sandbox):** **payout to card** (`TransferFund`) — **issuer** bears the fee (source wallet debited Amount+Fee; seller gets full amount). **withdraw to card/bank** (`UserWithdrawFund`) — **seller** bears the fee (nets Amount−Fee).

## XTRM API (confirmed against sandbox 2026-07-15)
- **Payout to card — `POST /Fund/TransferFund`** (existing call, add `CardToken`): `PaymentMethodID="XTR94508"` (Rapid Transfer) + `TransactionDetails[].{RecipientUserID, PaymentAmount, CardToken}`. Response: `PaymentTransactionId`, `PaymentStatus="Completed"`, `Amount` (to beneficiary), `Fee`, `TotalAmount` (Amount+Fee, from source wallet), `PayDirectTransactionDetails` (Instant RBT). Sandbox: $9 to card, fee $0.36, total $9.36 from issuer wallet.
- **Withdraw — `POST /Fund/UserWithdrawFund`** — 2-step OTP:
  - **Initiate (no `OTP`):** `Success=true`, `Errors:["One Time Password sent…"]`, `PaymentTransactionId=null`. OTP → user (email/SMS). No money moves.
  - **Confirm (with `OTP`):** `PaymentTransactionId`, `PaymentStatus="Completed"`, `Amount` (NET), `Fee`, `TotalAmount` (GROSS), `Currency`.
  - **Completion signal:** `Success && PaymentTransactionId present` → done; `Success && PaymentTransactionId null` → OTP pending; `Success=false` → error.
  - **Bank rail:** `XTR94500` + `UserLinkedBankID` (= `partner_linked_bank.xtrm_beneficiary_id`). Sandbox $5 → fee $0.50 → $4.50.
  - **Card rail:** `XTR94508` + `CardToken` + `BankPaymentMethod="ACH"`. Sandbox $5 → fee $1.25 → $3.75.
- **Card linking — `POST /Card/LinkCard`** (`LinkCardType:"transfer"`, **`UserID` = the seller PAT** — the sandbox sample used a company `SPN` because it linked a *company* card; ours is a personal card, so PAT, same as `LinkBankBeneficiary`; raw `CardNo`/`ExpMonth`/`ExpYear`/`cvv`/name/billing) → `{ CardToken, CardStatus, AccountIdentityLevel }`. Called **server-side** with our cached XTRM token. ⚠️ **PCI-DSS SAQ D** — raw card is transient in this call only: **never persisted, never logged**; store **only** `CardToken` + masked last-4 + type/status.
- **Card remove — `POST /Card/DeleteCard`** `{ IssuerAccountNumber, UserID (= PAT), CardToken }` → `OperationStatus.Success`. ⚠️ Response envelope is **`DeleteCard.DeleteCardResult`** (not `…Response.…Result`) — unwrap accordingly. Mirrors `DeleteBankBeneficiary`.
- **`POST /Payment/GetPaymentMethods`** (`{}`) → catalog: `XTR94500` Bank, `XTR94508` Rapid Transfer, `XTR94502`/`XTR94504` AnyPay, `XTR94503` Prepaid Visa, `XTR94505` Gift Cards, `XTR94507` Bank Check.

## Design

### Data model
- **`partner_linked_card` (V37):** `id, client_id, user_id, card_token, masked_last4, card_type, status, created_at, updated_at, deleted`. Mirrors `partner_linked_bank` — multi-card list for the Payout instrument picker + Withdraw destinations. ⚠️ **ONLY** token + last-4 + type/status — **NEVER PAN/CVV/full expiry.** Partial-unique `(client_id,user_id,card_token) WHERE deleted=false`.
- **`partner_redemption` (V37):** add `partner_linked_card_id` (default card = its `card_token`) + `linked_card_label` — the CARD-rail default, mirroring `partner_linked_bank_id`. Payout stays "one default per method."
- **`partner_withdrawal` (V36):** `id, client_id, user_id, amount_gross, fee, amount_net, currency, destination_type (BANK|CARD), destination_label, destination_ref, xtrm_payment_transaction_id, status, created_at, updated_at, deleted` — withdrawal history/audit (does not touch the reward ledger).
- **Enum `RedemptionPayoutMethod`:** add **`CARD`** (→ ANYPAY | BANK | CARD).

### Backend
- **`XtrmApiClient`** (+ stub):
  - `linkCard(LinkCardCommand)` → `LinkCardResult{cardToken, last4, cardType, status}` (`POST /Card/LinkCard`).
  - `userWithdrawFund(UserWithdrawCommand)` → `UserWithdrawResult{success, otpRequired, paymentTransactionId, paymentStatus, amountNet, fee, totalGross, currency, errors, retryable}`; `otpRequired = success && blank(paymentTransactionId)`.
  - `transferFund` (existing): add optional `cardToken` to the command → `putIfPresent(detail, "CardToken", …)` for the CARD rail.
- **`XtrmCardService`** (new, mirrors `XtrmBankService`): `addCard` (lazy-enroll → `LinkCard` → store token+last4 only; set default if first), `listCards`, `removeCard(id)` (XTRM card-delete if available + soft-delete; auto-promote/reset default like banks), `setDefaultCard(id)`. **Raw card transient; never persisted/logged.**
- **`XtrmWalletService`:** `initiateWithdrawal(userId, req)` (resolve PAT; validate the chosen bank/card belongs to the user; optional balance check; call `userWithdrawFund` no-OTP → `OTP_SENT`), `confirmWithdrawal(userId, req+otp)` (call with OTP → record `partner_withdrawal` → return net/fee/gross/status).
- **`XtrmVendorService.dispatch`:** add **CARD branch** → `PaymentMethodID` = **new config `redemption.xtrm.rapid-transfer-payment-method-id:XTR94508`** (mirror the existing `bank-payment-method-id` `@Value` — NOT hardcoded) + `cardToken` = default card's token (`partner_redemption.partner_linked_card_id`); reject with **`CARD_NOT_LINKED`** if no default card (mirrors the `BANK`/`BANK_NOT_LINKED` block). BANK/ANYPAY unchanged.
- **`RedemptionProfileController`** (gate `ANY[redeem, redeem_company]`, XTRM calls rate-limited):
  - Cards: `GET /cards`, `POST /card`, `DELETE /cards/{id}`, `PUT /cards/default`.
  - Withdraw: `POST /wallet/withdraw/initiate`, `POST /wallet/withdraw/confirm`, `GET /wallet/withdrawals` (optional).
  - Payout method (existing) accepts `CARD` (reject if no default card, like `BANK_NOT_LINKED`).

### Contracts (`redemption-payout.yaml`)
- `LinkedCard { id, label(masked), cardType, isDefault }`, `AddCardRequest` (card fields — pass-through, never stored), `SetDefaultCardRequest { cardId }`.
- Withdrawal: `InitiateWithdrawalRequest { walletCurrency, amount, destinationType, bankId|cardId }`, `ConfirmWithdrawalRequest { …, otp }`, `WithdrawalResult { status: OTP_SENT|COMPLETED|FAILED, paymentTransactionId, amountNet, fee, totalGross, currency }`.
- Extend `RedemptionPayoutMethod` enum (+`CARD`) and `RedemptionProfileResponse` (`cardLinked`, `linkedCardLabel`).

### Frontend
- **Payout tab** (manage instruments): add a **"Linked cards"** section next to "Linked banks" (add-card form + list + remove + default). Payout-method selector gains **Card** (enabled when a card is linked; select the default card). *(Card-add form: card #/expiry/CVV/name/billing → BE → `LinkCard`; **never** persisted/logged FE- or BE-side beyond the token.)*
- **New "Withdraw" tab** (action only — reuses instruments): wallet balance → destination picker (**linked banks + cards**, **pre-selected to the user's default bank/card** set under Payout; overridable) → amount → **Withdraw** (initiate → "code sent") → **OTP** → **Confirm** → result "net + fee". Invalidate the wallet-balance query after success; disable confirm to prevent double-submit.
- Hooks: `useLinkedCards` + card mutations (add/remove/default), `useInitiateWithdrawal`, `useConfirmWithdrawal`.

## Phases
**Phase A — Bank withdrawal (ships first, no PCI):**
1. **BE** — `userWithdrawFund` client (+ stub), `XtrmWalletService` initiate/confirm (bank rail), `partner_withdrawal` (V36) + entity/repo, endpoints, tests.
2. **Contracts** — withdrawal models + endpoints.
3. **FE** — Withdraw tab + 2-step OTP (bank destination) + hooks + tests.

**Phase B — Card block (server-side `LinkCard`, SAQ D):**
4. **B1 — Multi-card linking (BE+FE):** `linkCard` client (+ stub), `partner_linked_card` (V37) + `partner_redemption.partner_linked_card_id`, `XtrmCardService` add/list/remove/set-default, card CRUD endpoints, "Linked cards" UI under Payout. Store only token+last-4; raw card never persisted/logged. Tests.
5. **B2 — Card payout rail (BE+FE):** `RedemptionPayoutMethod.CARD`, `transferFund` `CardToken`, `XtrmVendorService` CARD branch, payout-method selector Card option. Tests.
6. **B3 — Card withdrawal (BE+FE):** withdraw via `XTR94508`+`CardToken`; Withdraw picker lists banks + cards. Tests.

## Done When
- **Payout:** link ≥2 cards, set a default, choose method=Card → a redemption pays that card (`TransferFund`+`CardToken`).
- **Withdraw:** cash out wallet → a linked bank or card: initiate sends OTP, confirm completes, UI shows net + fee + status.
- Edge: no default card + method=Card rejected; wrong OTP; amount > balance; not-enrolled; remove-default auto-promotes.
- BE + FE tests green; contract synced. **No PAN/CVV persisted or logged anywhere.**

## Open questions / risks
1. ⚠️ **PCI (card) — IN SCOPE via server-side `LinkCard` (SAQ D accepted; no XTRM hosted capture).** Hard rules: never persist/log PAN, **never store CVV**, store only `CardToken`+last-4. Ops/legal own SAQ D (annual attestation, quarterly ASV scans, pen test, segmentation). Phase A has no PCI exposure.
2. ~~XTRM card-remove API~~ — ✅ **confirmed:** `POST /Card/DeleteCard` `{IssuerAccountNumber, UserID(PAT), CardToken}` → `OperationStatus.Success` (envelope `DeleteCard.DeleteCardResult`). `removeCard` = DeleteCard at XTRM + soft-delete our row.
3. **OTP delivery** relies on the payee's XTRM-registered email/phone — confirm set at enrollment; how sandbox surfaces the OTP for testing.
4. **Fee preview** — fee only returns on confirm (withdraw) / on the payout response — UI shows it after; no pre-quote.
5. **Balance check** before withdraw (`GetBeneficiaryWallets`). ⚠️ **No server-side idempotency key** — `UserWithdrawFund` has no `IssuerTransactionId` (unlike `TransferFund`); the only guards are the **single-use OTP** + FE **disable-confirm-after-click**. Acceptable, but note it.
6. **Withdrawal only applies to AnyPay-method users.** A **BANK/CARD** payout method pays through directly and **never credits the wallet** → those users have **no wallet balance** to withdraw. The Withdraw tab must show a clear empty state ("No wallet balance to withdraw") in that case.
7. **USD-only v1**; multi-currency deferred.
8. **Post-confirm record write:** if the `partner_withdrawal` insert fails *after* XTRM completed, the money moved but no local row — log-and-continue (history-only; low severity).

## Confirmed-from-sandbox (2026-07-15)
- Payout to card (`TransferFund` `XTR94508`+`CardToken`): txnId 941477, Completed, Amount 9.00 / Fee 0.36 / Total 9.36 (issuer-borne).
- Withdraw initiate (no OTP): Success=true + "One Time Password sent" + null txnId.
- Withdraw bank (`XTR94500`+`UserLinkedBankID`+OTP): txnId 426349, Completed, gross 5 / fee 0.50 / net 4.50.
- Withdraw card (`XTR94508`+`CardToken`+OTP): txnId, Completed, gross 5 / fee 1.25 / net 3.75.
- `LinkCard` → `{ CardToken, CardStatus:"Approved", AccountIdentityLevel }` (raw card in — PCI).
- `GetPaymentMethods` → catalog (above).
