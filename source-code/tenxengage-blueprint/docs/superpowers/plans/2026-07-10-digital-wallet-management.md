# Digital Wallet Management — My Profile (F-03 payout enhancement)

- **Status:** draft
- **Date:** 2026-07-10 (v1 scope trimmed 2026-07-13)
- **Type:** Enhancement to F-03 (redemption-flow) payout profile — plan doc + enhancement branch (NOT `/create-spec`).
- **Branch:** `features/redemption-xtrm-payout-enhancement` (confirmed 2026-07-13).

## Summary
Partner **admin** and partner **seller** can **view their XTRM digital wallets** (name + balance + currency) from **My Profile → Payout**.

> **⭐ v1 SCOPE (decided 2026-07-13): USD → USD only → VIEW-ONLY.** Because payouts are USD-only, *creating* non-USD wallets and *changing the default* (to reroute a payout) have no effect yet — so they're **DEFERRED**. v1 ships the **read-only wallet list**. **No migration, no default storage, no payout-path changes.** The create / set-default / currency-routing design is preserved in **§ Deferred** for when multi-currency is un-deferred.

### Two wallet concepts (don't conflate)
- **Internal reward wallets** (`reward_wallets`, our DB) — where earned rewards sit; what the user redeems *from*. **Unchanged.**
- **XTRM digital wallets** (external, XTRM account) — where AnyPay payouts *land*. **This feature VIEWS these.**

## XTRM API (CONFIRMED against sandbox, 2026-07-10)
- **List:** `POST /API/v4/Wallet/GetBeneficiaryWallets` — `{GetBeneficiaryWallets:{request:{IssuerAccountNumber, BeneficiaryAccountNumber(=PAT)}}}`
  → `GetBeneficiaryWalletsResponse.GetBeneficiaryWalletsResult.Wallets[]` = `{ID (number), Name, Currency, Type, Balance, IsBankLinked, AllowWalletLinkedBankOverride, EntityID}`.
- Keys on the user's **PAT** (`BeneficiaryAccountNumber` = PAT) → requires XTRM **enrollment** first (same gate as bank-link).
- **XTRM auto-creates a `Wallet - USD` at enrollment** — so an enrolled user always has at least the USD wallet (holds AnyPay payouts; sandbox showed balance 25.00).

## v1 Design (VIEW only)

### Backend
- **`XtrmApiClient`** (+ `@Profile` stub): add **`getBeneficiaryWallets(cmd)`** → returns the wallet list. (No `createUserWallet` in v1.)
- **`XtrmWalletService`** (new): `listWallets(userId)` — reads the PAT from `partner_redemption`; throws `XTRM_NOT_ENROLLED` if no PAT.
- **`RedemptionProfileController`:** `GET /api/v1/redemption/profile/wallets` → list.
- **No migration, no schema change** (nothing to default when USD-only).
- **Permissions:** gate `GET /wallets` on **`ANY[action.redemption.redeem, action.redemption.redeem_company]`** via `@RequiresPermission(logic = ANY)` — copy the annotation from the existing `RedemptionProfileController` handlers. ⚠️ **Do NOT gate on `redeem` alone.** Code-verified (V17 seed): **PARTNER_ADMIN holds ONLY `redeem_company`**, **PARTNER_SELLER holds ONLY `redeem`** — they're mutually exclusive, so a `redeem`-only guard would **403 every partner admin**.

### Contracts
- `GET /wallets` endpoint + a `DigitalWallet` model (`id`, `name`, `currency`, `balance`) in `redemption-payout.yaml`.

### Frontend
- **My Profile → Payout:** read-only **"Digital Wallets"** panel:
  - Show **ALL** the user's wallets (every currency, not just USD), each with its **balance prefixed by the currency symbol + code** (e.g. `$25.00 USD`, `₹0.00 INR`, `€0.00 EUR`, `A$0.00 AUD`) and the **wallet name**.
  - **Transfers are USD-only for now** → the **USD wallet is the payout destination**; badge it "Receives your payouts" (informational, not user-settable in v1).
  - **Formatting:** the app's `config/currencies.ts` covers **reward** currencies only (`cash`/`points`/…) — NOT ISO fiat. Use **`Intl.NumberFormat(undefined,{style:'currency',currency:<ISO>})`** + append the ISO **code** (disambiguates `$` USD vs AUD). Small `formatFiat(amount, isoCode)` helper.
  - **Keep the wallet `ID`** in the response (useful for the BE + future wallet actions). Hide the other internal fields (`EntityID`, `IsBankLinked`, `Type`) from the UI. Gated on enrollment (prompt to complete the payout profile first, like bank-link).

## v1 Phases
1. ✅ **DONE (2026-07-13) — BE.** `XtrmApiClient.getBeneficiaryWallets` (+ impl + stub; `WalletInfo`/`GetWalletsCommand`/`GetWalletsResult` records; `Balance`→BigDecimal parse), `XtrmWalletService.listWallets` (enrollment-gated, no write on GET), `GET /wallets` on `RedemptionProfileController` (`ANY[redeem, redeem_company]`), `DigitalWalletResponse` DTO, `RateLimitFilter` 20/min. Tests: `XtrmWalletServiceTest` (4) + controller wallet cases. **Full BE `test` suite GREEN.**
2. ✅ **DONE (2026-07-13) — Contracts.** `GET /wallets` + `DigitalWallet` model in `redemption-payout.yaml` (YAML validated).
3. ✅ **DONE (2026-07-13) — FE.** `DigitalWallet` type, `listWallets`, `useDigitalWallets(enabled)`, `formatFiat` helper (`Intl.NumberFormat` + ISO code), read-only `DigitalWalletsPanel` (all currencies; USD badged "Receives your payouts") wired into `PayoutTab`; `DigitalWalletsPanel.test` (4). **Payout FE tests GREEN + `tsc -b` clean.**

**All 3 phases done + tests green. Nothing committed.** Deferred items (create / set-default / multi-currency routing) remain in § Deferred.

## Done When (v1)
- Admin + seller see their XTRM wallets (name + balance + currency, correctly formatted) in the Payout tab.
- Backend + FE tests green; contract synced.

## Code-verified (2026-07-13)
Fact-checked against source (parallel review):
- 🔴 **Permission claim was WRONG — now fixed above.** PARTNER_ADMIN holds ONLY `redeem_company`, PARTNER_SELLER holds ONLY `redeem` (`V17__seed_redemption_flow_permissions.sql:26-47`). A `redeem`-only guard would 403 admins → gate `ANY[redeem, redeem_company]`.
- ✅ No `getBeneficiaryWallets` on `XtrmApiClient`, no `XtrmWalletService` — both new, as planned.
- ✅ Base path `/api/v1/redemption/profile` (`RedemptionProfileController.java:36`); `COMPANY_PAYOUT_NOT_SUPPORTED` guard exists (`XtrmVendorService.java:68-71`).
- ✅ `redemption.xtrm.wallet-id` is a single scalar `@Value` in `XtrmApiClientImpl` (not `XtrmVendorService`) — confirms the deferred currency→wallet-map work.
- ✅ FE `config/currencies.ts` is reward-only (`cash/points/credits/tickets`) → use `Intl.NumberFormat`/`formatFiat`, not that config.

---

## § Deferred (multi-currency — un-defer only when >1 payout currency is supported)
All of this is **out of v1** (USD→USD). Kept here so the design + confirmed API facts aren't lost.

- **Create wallet** — `POST /API/v4/Wallet/CreateUserWallet` `{CreateUserWallet:{request:{IssuerAccountNumber, UserID(=PAT), WalletName, WalletCurrency}}}` → `CreateUserWalletResult{WalletID (number), WalletName, WalletCurrency}`. ⚠️ Create returns `WalletID/WalletName/WalletCurrency` while List returns `ID/Name/Currency` — client must handle both.
- **Set default + storage** — `default_wallet_id` (+ currency) on `partner_redemption` (migration), `PUT /wallets/default`. XTRM has **no** "set-default" endpoint → tracked our side.
- **Payout routing** — `TransferFund` (to user) has **no destination-wallet field**; the recipient wallet is chosen by **`PaymentCurrency`** (XTRM credits/creates the wallet in that currency). So "default wallet" = **default payout currency**. Set `PaymentCurrency` = the default wallet's currency in `XtrmVendorService.dispatch` (AnyPay).
- **Source-wallet constraint** — the source (issuer) `WalletID` **must match `PaymentCurrency`** (sandbox: `203871 - Invalid wallet id` for INR because 203871 is the issuer's USD wallet). → requires the **issuer to hold a wallet per payout currency** + a **currency→issuer-wallet-id map** (via `GetCompanyWallets`) + provisioning (`CreateCompanyWallet`). Config: replace the single `redemption.xtrm.wallet-id` with the map (⚠️ bound as a scalar `@Value` in **`XtrmApiClientImpl`**, not `XtrmVendorService`).
- **Currency conversion** — reward is `cash`; a non-USD payout currency needs a `cash → fiat` rule (face value vs FX) — a **finance/product decision** (Vijay).
- **Currency set** — when enabled, offer **ISO fiat** (USD/EUR/INR/AUD…), NOT the reward currencies.

## Open questions (v1)
- **Company wallets** — out of scope (deferred); `COMPANY_PAYOUT_NOT_SUPPORTED` guard stays.
- **`GET /wallets`** is a live XTRM call per view — consider a short cache / lazy-load if latency bites.
- Overlaps `2026-07-13-multi-bank-payout.md` (both are payout-profile panels) — sequence together.
