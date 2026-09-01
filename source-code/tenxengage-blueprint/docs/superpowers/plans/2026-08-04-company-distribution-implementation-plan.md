# Company Distribution Store — Implementation Plan

## ✅ COMPLETE (2026-08-04) — all 13 phases

**Phase 13 E2E: 47 checks, 0 failures — plus `PARTIALLY_COMPLETED` verified separately (below).** Driver preserved at
[`2026-08-04-phase13-e2e.sh`](2026-08-04-phase13-e2e.sh) — run it with
`DEV_PASSWORD='…' bash 2026-08-04-phase13-e2e.sh` against a `local`-profile backend.

All three rails exercised against the real stack — but read the XTRM caveat below before treating the two
payout rails as proven:

| Rail | Result | XTRM `TransferFund` |
|---|---|---|
| `WALLET_CREDIT` | recipient +25.00, company −25.00, `CREDIT` ledger entry, **no** redemption row (by design) | n/a — internal ledger only, no vendor call exists |
| `BANK_TRANSFER` | payout leg created, `origin=COMPANY_DISTRIBUTION`, → forced failure → **`RELEASE:15.00`** back to the company wallet | ❌ **never succeeded** |
| `GIFT_CARD` | payout leg created, `origin=COMPANY_DISTRIBUTION`, → success → `RESERVE`→`DEBIT`, reserved consumed | ❌ **never succeeded** |

### ⚠️ The XTRM payout call is NOT verified

Every `TransferFund` dispatch failed, and settlement in the E2E was **simulated** through
`POST /api/v1/dev/redemption/{id}/complete`, not driven by a real vendor response. Evidence:

```
[step=xtrm_token_failed] OAuth2 token fetch failed status=400
    error=invalid_credentials error_description=Credentials are invalid.
[step=xtrm_dispatch_failed] transport error on TransferFund issuerTxn=…
```

`vendor_reference_id` is **NULL on all four** distribution payout legs — XTRM never returned a reference.

**Cause is environmental, not code:** `application-local.yml` has
`client-id: ${XTRM_CLIENT_ID:placeholder-client-id}`, and the env var is unset, so the literal placeholder
was sent. Closing this needs real sandbox credentials in `.env.local` (`XTRM_CLIENT_ID`, `XTRM_CLIENT_SECRET`)
— and per [[reference_xtrm_skus]] the gift-card `providerItemId` must also be a SKU that
`GetDigitalGiftCards` actually returns, which is a separate unverified assumption.

**What the failure did prove**, and it is worth having: the dispatcher logged
`step=distribution_payout_ambiguous` four times and **held each item at `PROCESSING` with the money still
reserved** rather than auto-failing and releasing it. That is the correct conservative choice — a transport
error cannot distinguish "XTRM never got it" from "XTRM paid but the response was lost", and releasing on the
latter would double-pay. The reserve-then-settle design behaved exactly as intended under a genuinely
ambiguous vendor outcome.

**So what IS proven for these two rails:** reserve, leg creation with correct `origin`, the fan-out,
dispatch-attempt bookkeeping (`dispatch_attempted_at`), ambiguity handling, and the settle/release paths
(exercised through the real webhook-processing code, just with a simulated trigger). **Not proven:** the
XTRM HTTP contract itself — request shape, SKU validity, and how a real success or rejection is parsed.

Money conserved exactly: 3000 funded − 75 wallet-credit − 10 gift card = **2915** = the balance.
Reserved settled back to **0.00** — nothing stuck.

**OQ-3 proven with real data** (not an empty table): with 2 distribution legs present,
`mv_redemption_rate_trend` totals **80** against **80** `origin='SELF'` rows out of **82** total.
Unfiltered it would read 82.

**`PARTIALLY_COMPLETED` — now E2E-covered too** (2026-08-05). One `GIFT_CARD` distribution of 20.00 to two
sellers, then one leg forced to success and the other to failure via the dev webhook endpoint:

| Observed | Value |
|---|---|
| item statuses | `COMPLETED,FAILED` |
| API rollup | **`PARTIALLY_COMPLETED`** |
| requested / settled | `40.00` / `20.00` |
| company wallet | 2915.00 → reserve 40.00 → **2895.00**, reserved back to 0.00 |
| ledger | `RESERVE:40.00` → `DEBIT:20.00` (settled) + `RELEASE:20.00` (failed) |

Only the settled 20.00 left the wallet; the failed recipient's share came back. This is the case the whole
reserve design exists for, and it is the one an admin most needs the summary notification for.

Getting two payout-eligible recipients needed scaffolding: only one TechPartners seller has a real XTRM
profile, and `uq_distribution_item_recipient` rightly forbids listing the same recipient twice. A
`partner_redemption` row was inserted for the second seller with `enrollment_status=ENROLLED` and a
deliberately obvious `recipient_user_id='E2E-SYNTH-1'` — chosen over calling the XTRM sandbox to mint a real
recipient, since the settle outcome is simulated anyway and a fake id must never be mistaken for an
enrollment. **The row was deleted afterwards** and eligibility confirmed reverted to
*"No payout profile yet — use Wallet Transfer instead"*. Repeat that scaffold-then-remove if this needs
re-running; do not leave it in place.

### T-13.4 — `integrationTest` deliberately NOT run

The user has declined resetting the dev DB, and `integrationTest` cannot run without doing so:
`src/test/resources/application-localtest.yml` hardcodes `jdbc:postgresql://localhost:5432/tenxengage`
(**not** env-overridable) with `clean-disabled: false`. Running it wipes dev. Pointing it at a scratch
database would require editing that file.

What that task would have caught is largely covered by other means, all verified on 2026-08-05:

- **Migrations vs entity mappings.** The base config is `ddl-auto: validate`, so booting the app against the
  real migrated dev DB makes Hibernate check every entity mapping against the actual schema — the one risk
  unit tests structurally cannot catch (`create-drop` builds schema *from* the entities). The app started
  clean at schema v56, with Flyway reporting *"Successfully validated 55 migrations"*. That covers V51–V56
  and the new `CompanyDistribution` / `CompanyDistributionItem` entities plus `RedemptionRequest.origin`.
- **The JPQL I changed in T-11.3 actually executes.** Those three analytics counts are mocked in unit tests,
  so a syntax error would surface only at runtime. `GET /api/v1/redemption/analytics` returns 200 and reports
  `total = 79`, which equals the DB's `origin='SELF'` count for that client and window — against **83** for
  all origins, with **4** distribution legs present. Unfiltered it would have said 83, so the pinned
  `origin = SELF` constant is proven in the live query path, not just in a mock.
- **Repository/read paths** were exercised through real HTTP in the 47-check E2E, including the derived-status
  reads and the bulk-lookup helpers that previously NPE'd on `Map.of().get(null)`.

Residual risk if T-13.4 is never run: the ~14 pre-existing environment failures stay unexamined, and any
constraint behaviour not touched by the E2E (e.g. `idx_distribution_items_unsettled`'s partial-index
selectivity) is unverified. Neither blocks this feature.

### XTRM `TransferFund` — verified against the official API docs (2026-08-05)

Source: the Postman collection behind <https://apidoc.xtrm.com> (the page is a JS shell; the data is at
`https://apidoc.xtrm.com/api/collections/16879270/2sBXwtp9ZE` — 116 endpoints with example responses).

**Our request matches the documented contract exactly.** Every field `baseTransaction()` sends
(`IssuerAccountNumber`, `PaymentType`, `PaymentMethodID`, `WalletID`, `PaymentDescription`,
`PaymentCurrency`, `EmailNotification`, `ProgramID`) and every `TransactionDetails` field we use
(`IssuerTransactionId`, `PaymentAmount`, `RecipientUserID`, `UserLinkedBankID`, `CardToken`, `SKU`,
`UserGiftCardEmailID`) appears in the official example. This retires the *"field-name caveat — verify against
sandbox before prod"* note at the top of `XtrmApiClientImpl`. Response parsing also matches: the request key
is `TransactionDetails` (plural) but the response key is `TransactionDetail` (singular), and our parser
correctly reads the singular form plus `PaymentTransactionId` / `BeneficiaryTransactionId`.

Both rails are documented on this one endpoint, each with a 200 example: **bank** via `UserLinkedBankID` +
`PaymentMethodID=XTR94500`, **digital gift card** via `SKU` + `UserGiftCardEmailID` (its response carries
`RedemptionDetails` with the claim code / PIN).

#### ⚠️ `WalletID` is the SOURCE wallet, and XTRM validates it

An earlier note in this plan claimed XTRM "never learns the source". **That was wrong.** `WalletID` *is* the
funding wallet and a bad value returns `400 Invalid wallet id`. There are two distinct wallet families and
only one is accepted:

| Family | Lookup | Owner | Valid as `TransferFund.WalletID`? |
|---|---|---|---|
| Issuer company wallets | `GetCompanyWallets(IssuerAccountNumber)` → `CompanyWalletDetails[].WalletID` | the platform | ✅ yes |
| Beneficiary company wallets | `GetBeneficiaryWallets(IssuerAccountNumber, **BeneficiaryAccountNumber**)` → `Wallets[].ID` | a partner company | ❌ no |

Passing a partner/beneficiary company's wallet id is the most likely cause of `Invalid wallet id`; a
mismatched `IssuerAccountNumber`, or a wallet whose `WalletCurrency` differs from `PaymentCurrency`, will do
it too. To enumerate what is valid, call `GetCompanyWallets` with only `{"IssuerAccountNumber": "…"}` and
check the configured `redemption.xtrm.wallet-id` appears in the result.

**This does not affect the implementation as built.** We always send the fixed configured issuer wallet and
never a partner company's id — our `reward_wallets` COMPANY row is an internal budget ledger with no XTRM
identity. That is the supported path.

**Open design question:** if the intent is for each partner company's *own XTRM wallet* to fund its
distributions, `TransferFund` cannot express it — its source is always the issuer's wallet. That would need
partner companies registered as XTRM beneficiary companies plus `TransferFundWalletToWallet` (which does take
`FromAccountNumber` + `FromWalletID`) or `BeneficiaryCompanyWithdrawFund` — a materially different money
model. **Not decided; do not assume the current model is the intended one.**

### XTRM blocked — the two payout rails are switched OFF (2026-08-05)

XTRM confirmed they have **no company-to-user transfer API**. `TransferFund` always sources from the issuer's
own wallet, so a partner company's wallet cannot fund a payout — passing a company wallet id as `WalletID`
returns `400 Invalid wallet id`. TenXEngage is in touch with them; until it ships, `GIFT_CARD` and
`BANK_TRANSFER` are **disabled with a reason**, not stubbed.

**Why not a stub.** A fake success writes a `DEBIT` and marks the item `COMPLETED` for money that never
moved. The ledger would assert a payment that did not happen, recipients would be told a reward is coming
that never arrives, reconciliation would "confirm" phantom payouts, and once XTRM is live there would be no
way to separate real payments from fabricated ones. Not an acceptable trade on a money path.

**`WALLET_CREDIT` is untouched** — it moves money inside our own ledger and calls no vendor, so the
Distribution Store stays genuinely usable and the copy names it as the alternative.

**Browsable, not hidden.** The rails stay fully open — gift-card picker, recipient table and each seller's
payout readiness are all visible. Only *sending* is withheld. Disabling the tabs outright was the first cut
and it hid too much: an admin still needs to see where their sellers stand before XTRM ships.

| Layer | Switch | Behaviour when off |
|---|---|---|
| Backend (authoritative) | `redemption.distribution.xtrm-payout-rails-enabled` / `XTRM_PAYOUT_RAILS_ENABLED` | `assertAllEligible()` throws `RAIL_UNAVAILABLE`. **Submit only** — the listing still reports real per-seller readiness |
| Frontend (UX only) | `XTRM_PAYOUT_RAILS_ENABLED` in `redemptionFeatures.ts` | tabs stay clickable; the send button is disabled carrying the reason; the notice shows on the affected rail; default rail is Wallet Transfer |

The gate lives in `assertAllEligible()` rather than `evaluate()` precisely so the two differ: the listing
tells the truth about each seller, and the one path that moves money refuses. It is refused even for a
*fully eligible* seller, so the block can never be misread as a setup problem.

Implemented through the **existing per-rail eligibility mechanism**, so there is no new API shape, no new
error path and no new concept — a disabled rail is refused with the same `422 RECIPIENT_NOT_ELIGIBLE` an
unenrolled seller already produced. The rail check runs *before* the per-recipient reasons on purpose: while
the rail is off, "no payout profile" would send an admin to fix something that is not broken.

`redemptionFeatures.ts` is the right home rather than the `feature_flags` table: that table is
**subscription-tier** entitlements (`starter/professional/enterprise_enabled`), whereas this is a vendor
outage. The file's own docstring already scopes it to "capabilities the platform does not yet support
end-to-end".

**To re-enable:** set `XTRM_PAYOUT_RAILS_ENABLED=true` on both sides. No code change —
`DistributionRecipientServiceRailSwitchTest` pins that promise so the config comment cannot rot, and also
pins that `WALLET_CREDIT` keeps working while the others are off.

### ✉️ XTRM's answer: company→user IS supported — it needs per-company credentials (2026-08-19)

XTRM (Leo) confirmed the capability exists and explained the `400 Invalid wallet id` exactly: **you cannot
spend another account's balance with your own credentials.** The wallet is fine; the *authentication* is
wrong. Two different calls, two different remitters:

| | Call | Authenticate as | Source | Destination |
|---|---|---|---|---|
| **Step 1** — fund the partner company | `TransferFundToCompany` | **Client** (Apple, `SPN26237883`) | Apple's wallet | `BeneficiaryAccountNumber` + `BeneficiaryWalletID` = Pushpa `206415` |
| **Step 2** — partner company pays its sellers | `TransferFund` | **Partner company** (Pushpa, `SPN26240019`) — *pseudo credentials* | `WalletID` = `206415` | recipient PAT |

Step 1 works today because we authenticate as the client and the money leaves the client's wallet. Step 2
fails because the money must leave *Pushpa's* wallet, and we present Apple's credentials.

**Corrections to earlier entries in this plan.** Two things I asserted were wrong:
1. *"XTRM never learns the source, so it cannot reject a company-funded payout"* — false. `WalletID` **is** the
   source and is validated against the authenticated account.
2. *"Per-company wallets would need a materially different money model —`TransferFundWalletToWallet` or
   `BeneficiaryCompanyWithdrawFund`"* — also wrong, and needlessly complex. It is the **same `TransferFund`
   endpoint**; only the credentials and `IssuerAccountNumber`/`WalletID` change.

#### What this costs us in code

The internal design is **unaffected** — reserve/settle, the distribution tables, fan-out, release-on-failure
all stand. What changes is only which credentials and which source wallet the vendor call uses.

| Gap | Current state | Needed |
|---|---|---|
| Per-company XTRM identity (SPN + wallet id) | **nothing** — no such columns on `partner_companies` | store per company |
| Per-company credentials (client id + secret) | single global `@Value` pair | per company, **encrypted at rest** — these are secrets |
| OAuth token cache | one global `cachedToken` / `tokenExpiry` | keyed per credential — otherwise Pushpa's token gets used for another company's call |
| Credential selection in the call path | implicit | `TransferFund` must know which company it pays from |

⚠️ **The gap nobody has named yet: funding does not move money in XTRM.**
`POST /wallets/company/{id}/fund` credits our **internal ledger only**. So the dev DB reads 2895.00 while
Pushpa's XTRM wallet holds whatever Step 1 last put there. Enable Step 2 without wiring Step 1 into funding
and every distribution fails at XTRM for insufficient funds while our UI shows a healthy balance. **Funding
must perform Step 1**, or the two balances drift apart silently.

#### Operational prerequisites — outside our control, and per company

1. Managing account designated a **Manager Account** (XTRM support enables; Leo offered to do it on sandbox).
2. Each partner company **onboarded, KYC-verified, advanced-services approved, and formally linked**.
3. Pseudo credentials pulled per company from the Manager portal (Contacts → ⋯ → Get API Credentials).

**Product consequence:** a partner company cannot distribute until it has been KYC-verified and connected
with XTRM. That onboarding flow does not exist, and it means the payout rails roll out **per company**, not
with a single global switch — which the current `XTRM_PAYOUT_RAILS_ENABLED` flag cannot express.

### 🔑 Vendor authorization is per (remitter, beneficiary) — the real Step 2 blocker (2026-08-19)

With credentials working, `TransferFund` now returns:

```
"Not authorized to access this resource. Make One Time Password API Call to access this resource"
```

**This is not about credentials or wallets.** XTRM requires the *beneficiary* to have authorized the *remitter*
as a vendor, once, per pair. Verified live against the sandbox:

| Remitter | Beneficiary | `OTP/GetConnectedStatus` |
|---|---|---|
| Apple `SPN26237883` (platform) | seller `PAT26240089` | **`Connected`** |
| Pushpa `SPN26240019` (the company) | **same seller** | `Not authorized to access this resource.` |

The database says the same thing from the other side: that seller has **27 personal payouts with real XTRM
transaction ids** and **0 successful distribution legs**. The recipient is not the problem — the *remitter* is.

**Consequence for Step 2, and it is a big one.** Moving the remitter from the client to the partner company
invalidates every existing authorization. Each seller must complete a fresh OTP handshake **with their own
company as remitter** before that company can pay them:

1. `OTP/GetConnectedStatus(IssuerAccountNumber, UserID)` — already connected?
2. `OTP/GetOTPAuthorizedVendor(IssuerAccountNumber, RecipientUserId)` — sends an OTP **to the seller**
3. `OTP/ValidateOTPAuthorizeVendor(IssuerAccountNumber, RecipientUserID, OneTimePassword)` — → `Authorized`

We call **none** of these today. The OTP endpoints exist in the XTRM collection; our only OTP code is for
user-initiated *withdrawals*, which is a different flow.

Why personal redemptions work without us ever calling them: the seller is created in XTRM *by* the platform
account (`Register/CreateUser`), which appears to establish that connection implicitly. A partner company
never creates the seller, so no such connection exists for it.

**This adds a seller-facing step that cannot be automated away** — the OTP is delivered to the seller, so a
human has to enter it. Step 2 therefore needs a per-company authorization flow in the UI, not just backend
plumbing, and `DistributionRecipientService` gains a real new ineligibility reason: *"hasn't authorized your
company to pay them yet"*.

### Two defects Phase 13 found

1. **Wrong HTTP method returned 500, not 405** (`be5ff175`). `GlobalExceptionHandler` had no mapping for
   `HttpRequestMethodNotSupportedException`. Pre-existing, but retirement made it reachable: a stale FE
   bundle POSTing to `/requests/company` got a 500 that reads as an outage. Fixed + 3 tests.
2. **V56's comment about `REFRESH … CONCURRENTLY` was wrong** (`3b5af4c5`). The `uq_mv_*` indexes are
   expression-based (`COALESCE` over nullable region/role), so they never qualified for `CONCURRENTLY`;
   `AnalyticsMvRefreshScheduler` uses a plain `REFRESH` and needs no unique index. Nothing functional
   changed — only my stated reason was wrong. V28 carries the same wrong claim.

### Operational notes worth keeping

- **Editing an already-applied migration breaks its Flyway checksum** and the app then refuses to boot with
  *"Migrations have failed validation"*. Fix: `./gradlew flywayRepair` (**without** `--offline`).
- **`bootRun` needs `JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"`.** `org.gradle.jvmargs` configures the Gradle
  daemon, not the forked app JVM, so Postgres rejects the system zone with
  *`invalid value for parameter "TimeZone": "Asia/Calcutta"`*.
- **Auth is an HTTP-only cookie** (`rc_access_token`, `Path=/api/`); `LoginResponse` carries no token, and the
  login body is **not** wrapped in `data` the way every other endpoint is.
- **Funding is `CLIENT_ADMIN`-only** (`action.wallet.fund_company`). A partner admin correctly cannot create
  balance for their own company — verified 403.
- `/distribution/recipients` **requires `?rail=`**, and reports ineligible sellers with a reason
  (*"No payout profile yet — use Wallet Transfer instead"*) rather than omitting them.

---

### Batch history

| Phase | State |
|---|---|
| 1 · foundation (V51 `origin`) | ✅ `9fb9574a` — R1 guard mutation-tested |
| 2 · `origin` filters | ✅ `8d97979d` — four sites, not five (see below) |
| 3 · permission grant (V52) | ✅ `01106a62` — PARTNER_ADMIN gains `redeem` |
| 4 · F-8 reconciliation | ✅ `2c81feba` — guard mutation-tested |
| 5 · new tables (V53) | ✅ `f96877b7` — entity/DDL columns diffed clean |
| 6 · submit + recipients | ✅ `c54786be` — 18 money tests |
| 7 · dispatch + settle + sweep | ✅ incl. `4a4071d4` |
| 8 · funding API | ✅ |
| 9 · retirement | ✅ **−700 LOC net in BE**; FE company client chain removed too (see correction 8) |
| 10 · delete `redeem_company` (V55) | ✅ guard clause in the migration; verified 0 rows left, both partner roles keep `redeem` |
| 11 · analytics MV rebuild (V56) | ✅ 5 views + **10** indexes rebuilt; MV row counts **identical** before/after (3/18/2/14/3) |
| 12 · contracts + FE | ✅ |
| 13 · full regression + E2E | ✅ 47 E2E checks + `PARTIALLY_COMPLETED`; BE 1635/0, FE 866/867 — ⚠️ XTRM `TransferFund` unverified (invalid sandbox creds) |

**Migrations now V51 → V56** (the header below says V55; Phase 11's rebuild became its own migration, V56).

### Batch C verification notes

- **V55/V56 were dry-run inside a `BEGIN … ROLLBACK`** against the dev DB before being applied. Postgres DDL is transactional, so this proved both execute, the guard passes, all 10 indexes rebuild, and every MV row count is unchanged — with dev left untouched. Worth repeating for any future MV migration.
- **`effectivePermissions` (T-10.4) needed no local eviction** — local Redis `DBSIZE` was 0. V55 carries a note so deployed environments still evict.
- **`flywayMigrate` fails under `--offline`** (`checker-qual` not cached). Run it without the flag.

### Corrections this implementation made to the plan below

1. **Phase 2 is four sites, not five.** `findPersonalHistory`/`countPersonalHistory` already require `walletType = INDIVIDUAL`, so a distribution row was never reachable there — design F-5.2 was wrong. Left untouched rather than adding a redundant filter.
2. **T-2.5 (notification copy) moved to Phase 7**, not Phase 2. `RedemptionEventPayload` carries no `origin`, so there is nothing to branch on until distributions publish events; adding the Kafka field earlier would ship a schema change with no consumer.
3. **Migrations renumbered to implementation order**: V51 `origin`, **V52 permissions**, **V53 tables** (the plan below had 52/53 swapped). Flyway would otherwise hit an out-of-order version on a dev DB.
4. **The two "pre-existing failures" are FLAKY, not failing.** `TenxengageApplicationTests.contextLoads` and `IncentiveServiceTest.generateForecastStreaming_…` both pass in isolation (3/3) and pass in most full runs. Treat this branch's baseline as **fully green**; if one of exactly those two goes red, suspect flakiness before a regression.
5. **⚠️ `gradle-wrapper.jar` is missing in any fresh backend worktree.** `.gitignore` line 4 `!gradle/wrapper/gradle-wrapper.jar` is defeated by line 8 `*.jar` (later pattern wins), so it was never committed. Copy it from `tenxengage-backend/gradle/wrapper/` or nothing builds. Worth a one-line `.gitignore` reorder as its own fix.
6. **Unit tests build schema from ENTITIES, not migrations** — the test profile uses `ddl-auto: create-drop`. A mismatch between a migration and its `@Column` annotations will not surface until `integrationTest`, so diff them explicitly when adding tables.
7. **T-9.4 kept the export worker's `COMPANY` branch.** The plan said remove `ExportScope.COMPANY` outright. The *requestable* scope is gone (the enum value is deleted, so a new COMPANY export 422s), but the async worker still recognises the stored string `"COMPANY"`: a job queued before this deploy would otherwise fall through to the PERSONAL branch and export the wrong person's rows. Dev had zero export jobs; production may not. Safe to delete once no non-terminal job carries that scope.
8. **Phase 9 was bigger on the FE than the plan implies.** T-9.2/T-9.3 list the flag and two components, but the FE still had a *live client* for both deleted endpoints — `submitCompanyRedemption`, `getCompanyRedemptions`, the `useCompanyRedemptions` hook, `SubmitCompanyRedemptionRequest`, and the `type="company"` branch through `useRedemptionSubmit` → `RedemptionSubmitModal`. Left in place they would have 404'd. All removed; `RedemptionSubmitModal` is now personal-only.
9. **T-11.3 was three queries, not two.** Auditing *every* aggregate over `redemption_requests` (rather than only the two named) found `countGroupByStatusByClientIdAndSubmittedAtBetween` also unfiltered. All three now pin `origin = SELF` as a constant, not a parameter — no caller legitimately wants another origin. **The two operational sweeps deliberately keep no origin filter** (`findByClientIdAndStatusAndProcessingMode…`, `findStrandedApprovalItems`): they move real money and hiding distribution legs from them would strand funds. Same table, opposite requirement — the F-8 lesson again.
10. **Phase 11 became V56, not V55.** V55 is the permission deletion; the MV rebuild follows it.

---

> **Design**: [2026-07-31-company-distribution-store-design.md](2026-07-31-company-distribution-store-design.md) — CLOSED, 15 decisions locked
> **Branch**: `features/company-distribution-store` (all four repos, off `roadmaps/redemption-store`)
> **MR target**: `roadmaps/redemption-store`
> **Migrations**: V51 → V56 (V50 is the highest on the roadmap branch)
> **Date**: 2026-08-04

---

## 0. The regression contract — read this first

The redemption store is **live**. Partner sellers redeem from it today, and partner admins are about to be able to. Nothing below is done until the existing flow still works.

### 0.1 The baseline, measured before any change

| Suite | Green baseline | Known pre-existing failures |
|---|---|---|
| backend `./gradlew test` | **1574 / 1576** | `TenxengageApplicationTests.contextLoads`, `IncentiveServiceTest.generateForecastStreaming_…` |
| frontend `npx vitest run` | **854 / 856** | `sidebarConfigs` (**label drift**, see below), `ApprovalQueueTable` |

**Any new failure is this feature's fault.** Those four were verified failing at the base commit before any of this work, so they are the floor, not a licence.

⚠️ **`sidebarConfigs` is red for a reason unrelated to counts.** The test asserts a sub-item labelled `"Tenant History"`; the config actually says `"All Redemptions"` — someone renamed the label and never updated the test. The count is 7 on both sides.

So T-12.3 has **two** fixes, not one: correct the stale label *and* extend the list to 10 for our three new items. Updating only the count leaves it failing for the original reason.

### 0.2 What must still work — Partner Seller

Unchanged behaviour, verified after every phase that touches shared code:

1. Browse the store; gift-card cards render with brand images
2. Redeem a gift card — `INSTANT` `CASH` → `PROCESSING` → webhook → `COMPLETED`
3. Bank-transfer redemption, including choosing which linked bank
4. In-flight cap still rejects at the limit
5. Transaction History list **and** detail
6. CSV export at `PERSONAL` scope
7. Payout profile: address, link/unlink bank, link/unlink card, digital wallets, withdrawal (all 15 endpoints)
8. Return request on a `NON_CASH` item
9. Wallet balance widget in the nav

### 0.3 What must work — Partner Admin

The admin is the role this feature changes most, and one item is a **fix**, not a preservation:

| # | Behaviour | Today | After |
|---|---|---|---|
| 1 | Redeem from their **own individual wallet** | 🔴 **403** — granted `redeem_company`, never `redeem` | ✅ works (T-3.1) |
| 2 | Payout profile — all 15 endpoints | ✅ via `redeem_company` | ✅ via `redeem` — must survive the key's deletion |
| 3 | CSV export | company scope via `redeem_company` | `PERSONAL` scope; company scope removed (§12.3) |
| 4 | Store visibility / nav | ✅ via `redeem_company` | ✅ via `redeem` |
| 5 | The 3 distribution screens | — | ✅ new |

⚠️ **Row 1 is the trap.** "Partner admin redemption works fine after this feature" is only achievable because we *grant a permission they never had*. It is broken today. Do not treat a passing admin redemption test as proof nothing regressed — it was never passing.

### 0.4 Gates

- **Every commit compiles and tests independently** (`compileJava compileTestJava` / `tsc --noEmit`). Last batch proved this is achievable; it keeps `git bisect` meaningful.
- **After each phase that touches shared code** (Phases 2, 3, 8, 9, 10): run the full BE + FE suites and diff against the baseline. Do not batch this to the end.
- **Stage by path only.** `core.autocrlf=true` with no `.gitattributes` means `git status` shows ~1000 phantom files. Never `git add -A`.
- **Feature flag on from the start** (T-1.4) so the whole surface can be disabled without a revert.

---

## 1. Phase order, and why it is this order

Risk is front-loaded onto *additive* changes and deferred on *destructive* ones.

| Phase | What | Risk to existing flow |
|---|---|---|
| **1** | Foundation: V51 `origin`, entity, feature flag | 🔴 R1 lives here |
| **2** | `origin` filters on existing queries | 🟠 R3, R5 |
| **3** | Permission **grant** (additive) — fixes admin 403 | 🟢 pure addition |
| **4** | F-8: widen reconciliation | 🟡 shared payout code |
| **5** | V52 new tables + entities | 🟢 additive |
| **6** | Distribution service + the 3 rails | 🟢 new code |
| **7** | Dispatch guard narrowing + wallet-credit sweep | 🟡 shared dispatch |
| **8** | Funding API | 🟢 additive |
| **9** | Retirement: company redemption, `ExportScope.COMPANY`, FE surfaces | 🟠 deletes shared code |
| **10** | Permission **deletion** of `redeem_company` | 🔴 R2 — last, once nothing references it |
| **11** | Analytics MV rebuild | 🟡 R6 |
| **12** | Contracts + FE | 🟢 new screens |
| **13** | Full regression + E2E | — |

**Two deliberate orderings:**

- **Grant early (3), delete late (10).** The design's §12.5 proposed one migration doing both. Splitting them is strictly safer: the grant is additive and immediately fixes the admin 403, while the deletion waits until Phase 9 has removed every consumer. If the deletion has to be reverted, the grant stays and nobody is locked out.
- **Retirement (9) before deletion (10).** `redeem_company` cannot be dropped while `ExportScope.COMPANY` and the FE gates still read it.

---

## 2. Phase 1 — Foundation

### T-1.1 · V51 — `origin` (one column only) [BE]

```sql
ALTER TABLE redemption_requests
  ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'SELF';

CREATE INDEX idx_redemption_requests_origin ON redemption_requests(client_id, origin);

COMMENT ON TABLE redemption_requests IS
  'Wallet payout legs for BOTH the redemption store (origin=SELF) and the '
  'distribution store (origin=COMPANY_DISTRIBUTION). When origin=COMPANY_DISTRIBUTION, '
  'user_id is the RECIPIENT and wallet_id is the COMPANY wallet the money came from; '
  'the initiating partner admin is on company_distributions.initiated_by_user_id, '
  'reachable via company_distribution_items.redemption_request_id.';
```

**Verify after migrating:** `SELECT count(*) FROM redemption_requests WHERE origin IS NULL` → **0**.

**✅ Resolved (OQ-16, 2026-08-04, @pushpendra): only `origin` is added.** `initiated_by_user_id` lives solely on `company_distributions`. It was redundant here — derivable via `company_distribution_items.redemption_request_id` → `company_distributions.initiated_by_user_id`, and every query needing the initiator ("Initiated by" in §7.3, "Awarded by" in §7.4) already joins the header for `rail` and `note`, so the join is free. One fact, one home.

Two side benefits: V51 is a single-column migration, and R1's surface shrinks to exactly one field.

### T-1.1b · Historical `COMPANY + SELF` rows — decide, don't discover

V51 backfills **every** existing row to `SELF`, including the dead company-wallet rows left by `submitCompanyRedemption` (mostly `FAILED`, from dev testing while the dispatch guard rejected them).

Consequence: `wallet_type = COMPANY, origin = SELF` becomes a combination that contradicts the §12 invariant *"every company-wallet movement belongs to a distribution"*. Those rows will keep appearing in tenant history and being counted by the analytics MVs — **exactly as they do today**, so this is not a regression. But it is a latent inconsistency a reviewer will ask about.

Two options, pick one before V51 ships:
1. **Accept and document** — add a note to the table comment that pre-V51 `COMPANY + SELF` rows are retired artifacts. Zero risk.
2. **Clean up** — soft-delete them in V51 (`deleted = true` where `wallet_type = 'COMPANY'`), which also removes them from tenant history and the MVs. Needs a count against real data first, and is only safe if none are `COMPLETED`.

**✅ Decided: option (1)** — document, don't clean up. Option 2 mutates money rows for cosmetic gain, and the rows behave exactly as they do today either way. The table comment in T-1.1 already explains the `origin` semantics; add one clause noting pre-V51 `COMPANY` rows are retired `submitCompanyRedemption` artifacts. A data-hygiene ticket can retire them later, out of this feature's blast radius.

### T-1.2 · 🔴 `RedemptionOrigin` enum + entity field — R1 [BE]

New enum `RedemptionOrigin { SELF, COMPANY_DISTRIBUTION }` — **not** `RedemptionRequestType`, which is taken (F-6).

```java
@Enumerated(EnumType.STRING)
@Column(name = "origin", nullable = false, length = 30)
@Builder.Default                                    // ← R1: without this, every personal redemption breaks
private RedemptionOrigin origin = RedemptionOrigin.SELF;
```

One field, not two (OQ-16).

**Why `@Builder.Default` is mandatory:** Hibernate includes the column in every INSERT. Without a Java-side default it sends explicit `NULL`, the `NOT NULL` constraint rejects it, and **all** personal redemption fails. The DB `DEFAULT 'SELF'` does not help — defaults only apply when the column is omitted. The entity already uses this pattern for `deleted`.

Also add the §4.2 javadoc note about `user_id` being the recipient when `origin = COMPANY_DISTRIBUTION`.

**Tests**
- `RedemptionSubmissionServiceTest`: submit a personal redemption without referencing `origin` → persisted row is `SELF` ← **the R1 guard**
- Same for the bank-transfer path

### T-1.3 · Confirm the 2 insert sites [BE]

`RedemptionRequest.builder()` appears at `RedemptionSubmissionService:297` (personal) and `:485` (company — deleted in Phase 9). No other site constructs one. Grep to confirm nothing new appeared before relying on the default.

### T-1.4 · Feature flag `company_distribution` [BE + FE]

Seed into `feature_flags` alongside the permissions (T-3.1's migration). Gate the distribution controller and the 3 nav items on it.

**Why:** it lets the whole new surface be switched off without a revert, and lets the FE merge ahead of BE readiness.

⚠️ **What the flag does NOT cover.** It gates the *new* surface only — Phases 5–8. These phases modify or delete shared code and are **not** flag-protected; rolling them back needs a code revert:

| Phase | Not flag-protected |
|---|---|
| 2 | the `origin` filters on existing queries |
| 9 | the retirement deletions |
| 10 | the `redeem_company` deletion |
| 11 | the MV rebuild |

So the flag is real insurance for the distribution feature, but it is **not** a blanket undo for this plan. §16 has the per-phase rollback posture, which is the actual safety story.

---

## 3. Phase 2 — `origin` filters on existing queries

Every one of these is a **no-op for existing data** (all rows are `SELF`). That is the acceptance criterion: behaviour must not change until distributions exist.

| Task | Site | Guard |
|---|---|---|
| **T-2.1** | `RedemptionHistoryRepository.findPersonalHistory` — exclude `COMPANY_DISTRIBUTION` | F-5.2 |
| **T-2.2** | `RedemptionHistoryService.getRedemptionDetail` + `RedemptionSubmissionService.getRedemptionById` — same filter, so detail matches the list | §7.4 |
| **T-2.3** | `findTenantHistory` (+ the export's row source) — `origin = 'SELF'` | OQ-12 |
| **T-2.4** | `countByClientIdAndUserIdAndStatusIn` → add `origin` — in-flight cap counts self-service only | R5 / F-5.1 |
| **T-2.5** | `RedemptionOrchestrationService.dispatchNotification` — branch on `origin`; the `SELF` path stays byte-identical | R8 |

**Tests**
- Seller history list and detail: a `COMPANY_DISTRIBUTION` row is invisible; a `SELF` row is unaffected
- In-flight cap: still rejects at the limit; a distribution row does **not** consume the seller's allowance
- Tenant history + export: distribution rows absent, self rows unchanged
- Notification copy for a personal redemption is unchanged

**Gate:** full BE suite vs baseline.

---

## 4. Phase 3 — Permission grant (additive, fixes the admin 403) — **R2, part 1 of 2**

### T-3.1 · V53 — seed keys, grant `redeem`, seed the flag [BE]

Seed into **both** `client_role_permissions` **and** `client_permission_grants` — the Layer-0 filter strips a key present in only one.

| Key | Roles |
|---|---|
| `action.redemption.distribute` | PARTNER_ADMIN |
| `action.redemption.view_distribution_history` | PARTNER_ADMIN |
| `action.redemption.view_company_awards` | PARTNER_SELLER |
| `action.wallet.fund_company` | CLIENT_ADMIN, PLATFORM_ADMIN |
| `action.redemption.redeem` | **grant to PARTNER_ADMIN** ← fixes §0.3 row 1 |

Plus `feature_flags` row for `company_distribution`.

### T-3.2 · Evict `effectivePermissions` [ops]

`PermissionService` caches on `@Cacheable(value = "effectivePermissions", key = "#userId")`. Evict after the migration or a logged-in admin keeps stale permissions.

**Tests**
- Partner admin can now submit a personal redemption (was 403)
- Seller permissions unchanged
- Partner admin still reaches all 15 payout-profile endpoints

**Gate:** full BE suite + a manual login as each role.

---

## 5. Phase 4 — F-8: widen reconciliation

### T-4.1 · Include `COMPANY` wallets in missed-webhook recovery [BE]

`RedemptionReconciliationService` hard-passes `WalletType.INDIVIDUAL` to both `findInFlightForReconciliation` and `countStuckPastCap`. Distribution payout legs are `COMPANY`, so today they would sit reserved forever **with no alert**.

Widen both. `reconcileSingle` needs no change — it resolves the PAT via `r.getUserId()`, which is the recipient.

**Tests**
- An `INDIVIDUAL` payout stuck in `PROCESSING` still reconciles exactly as before ← **R7** regression guard
- A `COMPANY` payout stuck in `PROCESSING` is now picked up ← the F-8 fix
- `recon_past_cap` counts both, and company volume does not mask an individual needing review ← **R7**

---

## 6. Phase 5 — V52 new tables

### T-5.1 · V52 migration [BE]
`company_distributions` + `company_distribution_items` exactly as §4.3, including all CHECKs, the two unique indexes, and the partial index on `status = 'RESERVED'`.

### T-5.2 · Entities + repositories [BE]
`CompanyDistribution`, `CompanyDistributionItem` (both `TenantAware`, `@Filter(tenantFilter)`), `WalletCreditItemStatus { RESERVED, COMPLETED, FAILED }`, plus repositories with the queries §6.2/§6.3 need.

---

## 7. Phase 6 — Distribution service

### T-6.1 · `CompanyDistributionService.submit` [BE]
The single atomic transaction of §5.1: resolve the caller's company → idempotency check → **lock the company wallet** → validate recipients (active `PARTNER_SELLER`, same company, not the caller, distinct) → validate the single amount against the rail → `amount × count ≤ available_balance` **under the lock** → insert header + items → reserve → commit → `202`.

No recipient wallet is touched in this transaction, on any rail.

**Audit:** `@Audited(action = "DISTRIBUTED", resourceType = "COMPANY_DISTRIBUTION", resourceId = "#result.body.distributionId.toString()")` on the controller method, with rail / recipient count / total in the description (design §4.5). Easy to omit and compliance-relevant.

### T-6.2 · Recipients + catalog endpoints [BE]
`GET /recipients?rail=` with per-rail readiness flags + `ineligibleReason`; `GET /catalog` reusing the browse service.

### T-6.3 · History + awards endpoints [BE]
Company-scoped list/detail for the admin (all admins' distributions, "Initiated by" attributed), own-awards list/detail for the seller. Derived status per §4.4; **Requested vs Settled** totals per §4.4.

**Tests:** overdraft rejected under concurrency; cross-company recipient rejected; caller-as-recipient rejected; non-seller recipient rejected; idempotent re-POST returns the original; admin cannot read another company's distributions; seller cannot read another seller's awards.

---

## 8. Phase 7 — Dispatch + wallet-credit settlement

### T-7.1 · Narrow the dispatch guard [BE]
```java
if (request.getWalletType() == WalletType.COMPANY
        && request.getOrigin() != RedemptionOrigin.COMPANY_DISTRIBUTION) {
    throw new BusinessRuleException("COMPANY_PAYOUT_NOT_SUPPORTED", ...);
}
```
Kept as defence in depth — dev already holds historical `COMPANY + SELF` rows that must never become payable.

### T-7.2 · After-commit fan-out, bounded [BE]
All three rails, on a bounded executor (`redemption.distribution.dispatch-concurrency`, default 4). Payout rails reuse `stampDispatchAttempt` → `dispatch` → `persistVendorRef`. Failure split unchanged: definitive → release + FAILED; ambiguous → leave `PROCESSING`.

### T-7.3 · Wallet-credit settle loop [BE]
Per §5.6, per item in its own transaction: lock item → skip unless `RESERVED` → `DEBIT` company from reserved → `ensureIndividualWalletExists` → `CREDIT` recipient → stamp ledger ids + `COMPLETED`. Both legs and the status flip commit together.

### T-7.4 · Stuck-item sweep [BE]
Scheduled sweep over `status = 'RESERVED'`, retrying and escalating by age to manual review — never auto-releasing on an unknown outcome. **Required**: the existing sweep sees only `redemption_requests`.

**Tests:** retry cannot double-credit (idempotent on item id); a definitive per-item failure releases only that share; company debit and recipient credit are atomic; the sweep finishes a crash-interrupted distribution.

---

## 9. Phase 8 — Funding API

### T-8.1 · `POST /api/v1/admin/wallets/company/{companyId}/fund` [BE]
Wraps the already-built, currently-uncalled `WalletService.creditCompany`. `CLIENT_ADMIN` / `PLATFORM_ADMIN` only — never `PARTNER_ADMIN`. Idempotent on `(COMPANY_WALLET_FUNDING, reference)` via the existing index. `@Audited(action = "FUNDED", resourceType = "REWARD_WALLET", resourceId = "#result.body.walletId.toString()")` — this is the one endpoint that creates balance from nothing, so the audit row is not optional.

**Tests:** double-submit funds once; partner admin gets 403; wallet auto-created on first funding.

---

## 10. Phase 9 — Retirement

Each is flag-hidden or dead today, so none should change visible behaviour.

| Task | Delete |
|---|---|
| **T-9.1** | `POST /redemption/requests/company`, `submitCompanyRedemption` (~180 lines), `SubmitCompanyRedemptionRequest`, their tests |
| **T-9.2** | `CatalogItemDetailSheet.tsx` — `canRedeemCompany`, the `useCompanyWallet` call, the "Redeem (Company)" block ← **R4: this is the seller's redeem drawer**. Update `__tests__/CatalogItemDetailSheet.test.tsx` in the same commit — that file is R4's safety net, so it must stay green rather than be deleted around |
| **T-9.3** | `TransactionHistoryPage.tsx` "Company" tab; `COMPANY_REDEMPTION_ENABLED` flag |
| **T-9.4** | `ExportScope.COMPANY`, `isCompanyExport`, `canCompany`, FE `ExportJobScope` `'COMPANY'` |
| **T-9.5** | `RedemptionHistoryController` company-history endpoint (0 FE callers); **keep** `findCompanyHistoryByPartnerCompany` for T-6.3 |

**Keep:** `GET /wallets/company/{id}` + `useCompanyWallet` — the Distribution Store needs them for its balance header.

**Tests:** the seller's full redeem journey through the drawer stays green (its test file has +133 lines of coverage); `PERSONAL` export unchanged; historical export rows with `scope='COMPANY'` still deserialize (it is a `String` column, not `@Enumerated`).

**Gate:** full BE + FE suites.

---

## 11. Phase 10 — 🔴 Delete `redeem_company` — **R2, part 2 of 2**

### T-10.1 · Pre-deletion guard query [BE]
Before writing the migration, run against real data:

```sql
SELECT cr.id, cr.base_role_name, cr.name
FROM   client_role_permissions crp
JOIN   client_roles cr ON cr.id = crp.client_role_id
WHERE  crp.permission_key = 'action.redemption.redeem_company'
  AND  NOT EXISTS (SELECT 1 FROM client_role_permissions x
                   WHERE x.client_role_id = crp.client_role_id
                     AND x.permission_key = 'action.redemption.redeem');
```

Any row is a **custom role** that would lose payout-profile access. Grant it `redeem` in the same transaction as the delete.

### T-10.2 · V55 — delete the key [BE]
From all five tables: `permissions`, `client_role_permissions`, `client_permission_grants`, `company_permission_overrides`, `user_permission_overrides`. No FKs on `permission_key`, so no ordering constraints.

### T-10.3 · Strip remaining references [BE + FE]
`RedemptionProfileController` (15 gates → `redeem` only), `App.tsx`, `sidebarConfigs.ts`, `MyProfilePage.tsx`.

### T-10.4 · Evict `effectivePermissions` again [ops]

**Tests:** partner admin and seller both reach the store, the payout profile, and their own redemption after the deletion + eviction. **Rollback:** re-seed the key; the Phase 3 grant means nobody is locked out meanwhile.

---

## 12. Phase 11 — Analytics MV rebuild

### T-11.1 · Enumerate indexes AND snapshot the dashboard immediately before the rebuild [BE]

Capture every index on the 5 MVs **before** dropping. The recreate omitting one is the real risk, not the filter.

⚠️ **Take the "before" dashboard snapshot here, not at project start.** The acceptance criterion is "figures identical before and after", but this phase sits 10 phases deep — a snapshot taken at kickoff would have the whole feature's worth of change between the two readings, making any discrepancy impossible to attribute. Snapshot immediately before T-11.2 and compare immediately after, so the comparison isolates the MV change.

### T-11.2 · V54 — `DROP` + `CREATE` with `AND rr.origin = 'SELF'` [BE]
`mv_item_redemption_breakdown`, `mv_segment_redemption_breakdown`, `mv_time_to_first_redemption`, `mv_redemption_rate_trend`, `mv_failure_mode_breakdown`. Postgres has no `CREATE OR REPLACE MATERIALIZED VIEW`. Recreate all indexes.

### T-11.3 · Basic analytics counts [BE]
`countByClientIdAndCurrencyIdAndSubmittedAtBetween` and `…AndStatusInAndSubmittedAtBetween` → `AndOrigin` variants.

**Acceptance (R6):** snapshot every dashboard figure before and after — they must be **identical**, because the filter is a no-op on existing data. Index list must diff clean.

---

## 13. Phase 12 — Contracts + Frontend

### T-12.1 · Contracts [contracts]
**Add:** `endpoints/company-distribution.yaml` (recipients, catalog, submit, history, detail, awards); the funding endpoint; models for both new tables.

**Remove** — the company-submit endpoint is documented in **three** places, not one:

| File | What |
|---|---|
| `endpoints/redemption-flow.yaml:201` | the `/api/v1/redemption/requests/company` path + `SubmitCompanyRedemptionRequest` schema |
| `endpoints/redemption-history.yaml:16` | the same path documented again |
| `endpoints/redemption-history.yaml:412` | a prose cross-reference pointing readers at it |

Plus the `COMPANY` export scope from the export schemas. Missing any of these leaves the contract advertising an endpoint that returns 404.

### T-12.2 · Types, services, hooks [FE]
`company-distribution.types.ts`, service, TanStack hooks for recipients / catalog / submit / history / detail / awards.

### T-12.3 · Nav + routes [FE]
Three sidebar items (§7.1) with their gates + the feature flag.

**Fix `sidebarConfigs.test.ts` — two separate corrections:**
1. `"Tenant History"` → `"All Redemptions"` — the pre-existing label drift that makes it red today
2. Extend the expected list from 7 to **10** for Distribution Store, Distribution History, Company Awards

Doing only (2) leaves the test failing for the original reason and will look like our regression.

### T-12.4 · Distribution Store page [FE]
Balance header (+ unfunded empty state), rail tabs with `?rail=`, gift-card picker (`FIXED` pins the amount), **single** amount field, recipient table with readiness + `ineligibleReason`, sticky review strip, per-recipient result list.

### T-12.5 · Distribution History [FE]
List with the §7.3 columns including **Requested** and **Settled**; detail drawer per recipient.

### T-12.6 · Company Award History [FE]
Seller's own awards, §7.4 columns.

### T-12.7 · Notifications [BE]
`COMPANY_AWARD_RECEIVED`, fired **on item settle, never on submit** (§9). Admin summary on a terminal rollup, especially `PARTIALLY_COMPLETED`. Note: local email fails silently — verify in `logs/tenxengage.log`.

---

## 14. Phase 13 — Full regression

### T-13.1 · Unit suites vs baseline
BE ≥ 1574 passing with only the 2 known failures; FE ≥ 854 with only `ApprovalQueueTable` (after `sidebarConfigs` is fixed by T-12.3).

### T-13.2 · Personal-flow E2E, **both roles**
Every item in §0.2 as a partner seller, and §0.3 as a partner admin. This is the deliverable the user asked for: the redemption store working for individual redemptions by both roles.

### T-13.3 · Distribution E2E
All three rails end to end; partial failure → `PARTIALLY_COMPLETED` with the failed share back in the company wallet; a wallet-transfer recipient's balance and ledger both reflect the credit.

### T-13.4 · Integration tests last
Against the real stack. Note the `integrationTest` task runs against the **live dev DB** with Flyway clean enabled and has ~14 pre-existing environment failures — verify against `git diff` before blaming this feature.

---

## 15. Done when

- [ ] All 15 design decisions implemented as specified
- [ ] BE and FE suites at or above baseline, no new failures
- [ ] §0.2 green as a partner seller · §0.3 green as a partner admin, **including the previously-403 personal redemption**
- [ ] Analytics dashboard figures byte-identical pre/post
- [ ] R1–R8 each have a passing named test
- [ ] F-8 has a test proving a `COMPANY` payout stuck in `PROCESSING` reconciles
- [ ] Every commit builds independently
- [ ] `company_distribution` flag can disable the whole surface
- [ ] Contracts updated; MRs open against `roadmaps/redemption-store`

---

## 16. Rollback posture

| Phase | If it goes wrong |
|---|---|
| 1–2 | Additive columns + no-op filters. Revert the code; the column can stay. |
| 3 | Additive grant. Revert = drop the grant rows; admin returns to today's broken state. |
| 4 | Revert to `INDIVIDUAL`-only. Distributions stop reconciling; personal is unaffected. |
| 5–8 | New surface only. Disable the `company_distribution` flag. |
| 9 | Restore deleted code from git. Nothing user-visible depended on it. |
| **10** | **Re-seed `redeem_company`.** The Phase 3 grant means nobody is locked out in the meantime — this is exactly why grant and delete are separate phases. |
| 11 | Re-run the previous MV definitions. Data is unaffected; only the views are rebuilt. |
