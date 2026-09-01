# Company Beneficiary Provisioning — System Design

**Status:** design approved 2026-08-24 · §8.1 resolved by probe 2026-08-25 (no OTP flow needed)
**Branch:** `features/company-distribution-store`
**Relates to:** [`2026-07-31-company-distribution-store-design.md`](2026-07-31-company-distribution-store-design.md),
[`2026-08-04-company-distribution-implementation-plan.md`](2026-08-04-company-distribution-implementation-plan.md)

This is the missing half of the Company Distribution Store: the piece that gives a partner company an
identity at XTRM and lets it pay its own sellers with its own money, rather than the platform paying on
its behalf.

---

## 0. Decisions locked (2026-08-24, @pushpendra)

| # | Decision |
|---|---|
| D-1 | ~~The company admin is **contact details on the company**, not a TenXEngage user account.~~ **Reversed by D-16.** The admin does get a login: creating a company with admin details now creates a PARTNER_ADMIN user through the existing onboarding-token flow. |
| D-2 | `Beneficiary/CreateBeneficiary` runs **after** the company-create transaction commits, and is **non-blocking**. A vendor outage never fails a company create. |
| D-3 | Existing companies are provisioned through an **explicit admin action**, not an automatic backfill. They have no admin details captured, so there is nothing to send until someone supplies them. |
| D-4 | The credentials XTRM returns are **persisted before any further call is attempted**. |
| D-5 | `AccountIdentityLevel` is stored and surfaced; the gate on it is **configurable and permissive by default**. |
| D-6 | Editing admin details later does **not** push an update to XTRM. Out of scope. |
| D-7 | Distribution payouts authenticate as the **partner company**. The platform token is never used to move a company's money. |
| D-8 | Funding a company wallet must eventually perform XTRM Step 1 (`TransferFundToCompany`). **Sequenced as its own phase**, not folded into this one. |
| D-9 | The add/edit company form is **extracted out of `UserSettingsPage.tsx`** as part of this work. |
| D-10 | A `PENDING` row is **claimed inside the create transaction**, before any vendor call, so the unique constraint — not the vendor — settles the race. |
| D-11 | **Reconciliation follows the remitter.** Dispatch and reconciliation resolve credentials through one shared method, so they cannot disagree about who paid. |
| D-12 | Deleting a partner company **disables and removes its XTRM row**; nothing is deleted at XTRM. |
| D-13 | ~~`EmailNotification` is **config-driven, off outside prod**.~~ **Amended 2026-08-27: config-driven, but ON by default everywhere.** That email carries the admin's XTRM *portal* credentials, and nothing else in this system hands them out — off, provisioning reports success while the admin cannot sign in. It was also the only XTRM call we suppress: `CreateUser`, `batchTransfer` and `userWithdrawFund` all hardcode `"true"`, so sellers always got their mail and only the company admin did not. Set `XTRM_BENEFICIARY_EMAIL_NOTIFICATION=false` per environment to suppress. |
| D-14 | Funding a company wallet **fails as a whole** if the XTRM transfer is rejected — it never credits the internal ledger alone. A balance showing money that is not at XTRM makes every later distribution fail at the vendor, after a seller's share is reserved. |
| D-15 | **One TenXEngage XTRM account serves every client**; only partner companies have their own. `platform()` stays a single global value — there is no per-client XTRM identity to build. |
| D-16 | **The company admin provisions their own beneficiary.** Creating a company captures five identity fields and creates the admin's login; the admin signs in, supplies their address, and *that* is what calls `CreateBeneficiary`. Reverses D-1. The email XTRM binds cannot be changed or reused, so the person who owns it is the one who types it. |
| D-17 | **Only the admin the account belongs to may view or complete the payout setup** — matched on `partner_companies.admin_email`, 403 for everyone else. Other admins of the same company keep full distribution rights; they simply cannot set up or change the account that funds it. |

D-10 to D-13 came out of a review pass against the code after the design was first approved (§13). D-14 and
D-15 were settled with @pushpendra on 2026-08-25, while planning the follow-on work in §8.1. D-16 came out
of the self-service provisioning plan on 2026-08-26; D-17 on 2026-08-27, when @pushpendra pointed out that
the Company Payout tab was visible to every admin.

### Why D-17 needs identity, not a permission

Every company admin holds the same shared `PARTNER_ADMIN` role, so `action.redemption.distribute` is
byte-identical for all of them. No permission — not even a new one — can express "the admin this account
belongs to", because permissions are granted per role, not per person.

The distinction is not cosmetic. A company has one beneficiary, bound at XTRM to `admin_email`, and XTRM
will neither change that address nor reuse it. The address fields it is completed with live on the shared
company row. So a second admin completing the form would overwrite the first admin's address while the
email already spent at the vendor stayed the first admin's — a beneficiary whose address belongs to one
person and whose email belongs to another, with no way back.

Both the read and the write are gated: the UI decides whether to offer the tab by whether the read
succeeds, so a permissive `GET` would put the tab in front of every admin again.

---

## 1. Scope

**In scope**

- Capture a default company admin (name, email, mobile, city, region, postal code, country) at company creation.
- Provision an XTRM beneficiary company immediately after a company is created, and store its identity,
  credentials and identity level.
- A connect/retry endpoint that serves both failed provisioning and companies that predate this feature.
- Switch the distribution payout path from platform credentials to the owning company's credentials.
- Surface XTRM connection state to the admin, on the company view and as a recipient-eligibility reason.

**Out of scope**

- Enrolling sellers under their own company rather than the platform (see §8.1). Follow-on work, and the
  replacement for the OTP flow this document originally anticipated — which turned out not to exist.
- Wiring `TransferFundToCompany` into company wallet funding (D-8). Prerequisite for real money, own phase.
- `UpdateBeneficiary` when admin details change (D-6).
- Any change to personal redemption. It keeps `Register/CreateUser` PATs and the platform account.

---

## 2. What already exists (verified in code, 2026-08-24)

| Thing | Where | State |
|---|---|---|
| Company create | `PartnerCompanyService.createPartnerCompany:102` | `@Transactional`; `partnerType` + `contactEmail` merged into `metadata` JSONB |
| Company entity | `PartnerCompany` | no admin concept at all |
| Individual beneficiary | `XtrmApiClientImpl:117` (`Register/CreateUser`) → `XtrmEnrollmentService` | working; idempotent, non-blocking, gated on address + `countryIso2`; remitter is always the platform |
| Per-company XTRM table | `partner_company_xtrm_accounts` (V57) | exists, **nothing populates it** — manual entry only |
| Credential resolver | `XtrmCredentialsResolver` | `platform()` and `forCompany()` both implemented |
| Credential value object | `XtrmCredentials` | five values travel together; `toString()` redacts the secret |
| Token cache | `XtrmApiClientImpl:76` | keyed by OAuth client id, so one company's token cannot be replayed for another |
| Two-arg transfer | `XtrmApiClient.transferFund(cmd, credentials)` | implemented, **not called by anything** |
| Payout leg → company | `CompanyDistributionItemRepository.findByRedemptionRequestId:47` | exists |
| Wallet listing | `XtrmApiClientImpl.getBeneficiaryWallets` | sandbox-confirmed shape for a payee PAT |

Commit `a326d677f` built the per-company credential foundation and deliberately stopped short of wiring it
into dispatch, on the grounds that "that changes which companies can distribute and is its own decision".
**This document is that decision.**

`Beneficiary/CreateBeneficiary`, `TransferFundToCompany` and the three `OTP/*` endpoints are implemented
nowhere in the codebase.

---

## 3. The probe that changed the design

The implementation plan recorded, as of 2026-08-19, that per-company pseudo credentials had to be pulled by
hand from the XTRM Manager portal — a manual step per company, and the largest operational obstacle in the
whole feature.

**That is wrong.** `Beneficiary/CreateBeneficiary` returns them:

```json
{"CreateBeneficiaryResponse": {"CreateBeneficiaryResult": {
    "BeneficiaryID": "SPN…",
    "AccountIdentityLevel": "Basic",
    "ClientID": "…_API_User",
    "SecretKey": "…",
    "OperationStatus": {"Success": true, "Errors": []}
}}}
```

Sandbox, 2026-08-24, issued under platform account `SPN26237883`. Credential values are deliberately not
reproduced here.

Three consequences:

1. **Provisioning is fully automatic.** Identity *and* credentials arrive in one call. The manual portal
   step disappears, and with it the reason the payout rails could not roll out without human setup per
   company.
2. **No wallet id comes back.** `partner_company_xtrm_accounts.xtrm_wallet_id` is `NOT NULL`, so the schema
   as written cannot store what this call returns. §5 resolves this.
3. **`AccountIdentityLevel` is `Basic`.** The implementation plan records XTRM requiring KYC and
   advanced-services approval per company before payouts. Whether `Basic` clears that bar is unknown.

---

## 4. Provisioning sequence

```
POST /partner-companies
        │
        ├─ validate, insert partner_companies row, assign locations    [TRANSACTION]
        │  CLAIM: insert partner_company_xtrm_accounts (status=PENDING,
        │         all XTRM columns null) — uq_xtrm_account_per_company
        │                                                              [COMMIT]
        │
        └─ after commit, async ────────────────────────────────────────────────
                 │
                 │  1. Beneficiary/CreateBeneficiary          [platform credentials]
                 │       → BeneficiaryID, ClientID, SecretKey, AccountIdentityLevel
                 │
                 │     ⇩ PERSIST NOW — still PENDING
                 │        xtrm_account_number, encrypted_credentials,
                 │        account_identity_level, xtrm_beneficiary_name
                 │
                 │  2. oAuth/token                            [the new company's credentials]
                 │       → proves the credentials are usable
                 │
                 │  3. Wallet/GetBeneficiaryWallets           [platform credentials]
                 │       BeneficiaryAccountNumber = the company's SPN
                 │       → wallet id
                 │
                 │     ⇩ xtrm_wallet_id set, status = CONNECTED, connected_at = now
                 ▼
```

### Why the claim row comes first

Without it, two concurrent provisioning attempts — a double-submitted create, or an after-commit hook that
runs twice — both reach `CreateBeneficiary` before either inserts anything. `uq_xtrm_account_per_company`
then catches the duplicate at **insert time**, which is far too late: a second real beneficiary company
already exists at XTRM, with its own credentials, and we discard them. That orphan is invisible to us and
un-deletable through any endpoint we have.

Claiming the row inside the create transaction moves the contention to the one place it can be settled
reliably. The unique constraint serializes attempts **before** any money-side identity exists, so the loser
of the race never calls XTRM at all.

This is what the nullable columns of §5.2 are *for*. A claim row is a row where nothing is known yet.

### Why step 1 persists on its own

This is the load-bearing decision in the design.

`SecretKey` is returned **exactly once**, and `CreateBeneficiary` cannot be replayed for the same company —
the company name is taken on the second attempt. If the credentials were held in memory across steps 2 and 3
and either threw, the company's ability to pay would be permanently lost, recoverable only through XTRM
support. The cost of avoiding that is one extra write. It is not close.

The corollary is that `PENDING` is a **spectrum of real states**, not a placeholder: claimed-but-untried,
failed-with-a-reason, or credentials-held-awaiting-a-wallet. Steps 2 and 3 are enrichment, and re-running
them is free.

### Why step 2 exists at all

A token fetch is the only way to learn that the credentials are usable *before* money depends on them.
Without it the first proof would arrive during a real payout, after funds were reserved and a seller had
been promised an award. `XtrmApiClientImpl.getAccessToken(credentials)` already does exactly this and caches
the result per client id, so step 2 costs one HTTP call and warms the cache for the first payout.

### If step 3 does not work for a company SPN

`getBeneficiaryWallets` is documented as sandbox-confirmed for a payee **PAT**. Whether XTRM accepts a
company **SPN** in `BeneficiaryAccountNumber` is unverified. If it does not, the row stays `PENDING` with
`last_error`, and the wallet id is supplied through the connect endpoint (§6). No credentials are lost in
either case — which is the whole point of §4's ordering.

---

## 5. Data model

### 5.1 `partner_companies` — new columns (V58)

| Column | Type | Maps to XTRM |
|---|---|---|
| `admin_first_name` | `VARCHAR(100)` | `AdminFirstName` |
| `admin_last_name` | `VARCHAR(100)` | `AdminLastName` |
| `admin_email` | `VARCHAR(255)` | `AdminEmail` |
| `admin_mobile_number` | `VARCHAR(20)` | `AdminMobileNumber` |
| `admin_city` | `VARCHAR(100)` | `City` |
| `admin_region` | `VARCHAR(100)` | `Region` |
| `admin_postal_code` | `VARCHAR(20)` | `PostalCode` |
| `admin_country_iso2` | `VARCHAR(2)` | `CountryISO2` |

All nullable — every existing company has none, and a company can legitimately exist without payout intent.

**`admin_mobile_number` is stored as entered and formatted at the boundary.** `CreateUser` already sends
`PhoneDialCodes.mobilePhone(iso2, phone)` (`XtrmApiClientImpl:109`), and `XtrmProfileService:111` refuses
countries `PhoneDialCodes.isSupported` does not know. `CreateBeneficiary` gets exactly the same treatment —
a second, hand-rolled phone format for the same vendor is how the two drift apart.

**Columns, not `metadata` JSONB.** `partnerType` and `contactEmail` already live in that blob, which is why
neither can be indexed, constrained, or found by a schema reader. These fields are a vendor integration's
input; a typo in `admin_country_iso2` fails a payout, and the schema should be able to say so.

The existing `website` column feeds `WebAddress`. `EmailNotification` is sent as `"true"` — XTRM emails the
admin, which is the behaviour we want and not something worth a column until someone asks to turn it off.

### 5.2 `partner_company_xtrm_accounts` — amendments (V58)

| Change | Reason |
|---|---|
| `xtrm_wallet_id` → **nullable** | §4 persists identity + credentials before the wallet id is known. `NOT NULL` makes that impossible. |
| `xtrm_account_number` → **nullable** | A provisioning attempt that fails at step 1 has no SPN, and still needs somewhere to record `last_error`. |
| **new** `account_identity_level VARCHAR(30)` | Returned by `CreateBeneficiary`; needed for D-5. |
| **new** `xtrm_beneficiary_name VARCHAR(255)` | The name we actually sent as `BeneficiaryCompanyName`. See §5.4 — it is not always the company's name, and support cannot match our row to XTRM's without it. |
| `chk_xtrm_account_connected_has_credentials` **dropped**, replaced by `chk_xtrm_account_connected_is_payable` | The old name no longer describes what it checks. Dropped and recreated rather than silently redefined, so a schema reader sees the change. |

The replacement constraint is the important half. V57's invariant was *"`CONNECTED` means payable"*, enforced
by refusing `CONNECTED` without credentials — because that state "would fail only at dispatch, after money is
reserved". Relaxing two columns to nullable would silently weaken that invariant, since a `CONNECTED` row
missing an account number or a wallet is equally unpayable and equally late-failing. The new constraint keeps
the guarantee exactly as strong while letting `PENDING` carry partial progress:

```sql
ALTER TABLE partner_company_xtrm_accounts
    DROP CONSTRAINT chk_xtrm_account_connected_has_credentials;

ALTER TABLE partner_company_xtrm_accounts
    ADD CONSTRAINT chk_xtrm_account_connected_is_payable CHECK (
        status <> 'CONNECTED'
        OR (encrypted_credentials IS NOT NULL
            AND xtrm_account_number IS NOT NULL
            AND xtrm_wallet_id IS NOT NULL)
    );
```

**Three columns nullable is the cost of a truthful state machine.** The alternative — keeping them `NOT NULL`
and writing no row until provisioning fully succeeds — loses the two things an admin most needs: why the last
attempt failed, and the credentials from a partially-successful attempt.

### 5.3 Status lifecycle

| Status | Means | Can distribute? |
|---|---|---|
| `PENDING` | not yet fully provisioned. Holds whatever has been learned so far — nothing but a `last_error`, or an SPN and credentials awaiting a wallet id | no |
| `CONNECTED` | credentials validated, wallet id known | yes |
| `DISABLED` | deliberately switched off (revoked, suspended, offboarded) | no |

A `CONNECTED` row **is** the per-company enablement switch, as V57 intended. There is no second flag, so the
two can never disagree.

### 5.4 The name we send is not the name we store

**Unverified, and defended against rather than assumed.** `partner_companies` is unique on
`(client_id, name)` — per tenant. XTRM's namespace appears to be global under the issuer account, in which
case two tenants that each have a partner company called "Acme Corp" would collide on the second
`CreateBeneficiary`, and the failure would look like a vendor outage rather than a name clash.

Send a tenant-disambiguated `BeneficiaryCompanyName` and persist it in `xtrm_beneficiary_name`. If XTRM's
namespace turns out to be per-issuer after all, the disambiguation is harmless; if it is global, this is the
difference between a feature that works for one tenant and one that works for all of them. Storing what we
sent is the part that is unconditionally worth doing — without it, nobody can reconcile our row against
XTRM's portal.

### 5.5 Secrets

`encrypted_credentials` stays an AES-GCM blob of `{clientId, clientSecret}` written through
`XtrmCredentialsResolver.encryptCredentials`, which wraps the existing `ConnectorEncryptionService`. No new
crypto, no second key. The account number, wallet id and identity level stay in the clear — they are
identifiers, needed for reconciliation and support, and the table is unreadable without them.

---

## 6. API surface

### `POST /partner-companies` — extended

`CreatePartnerCompanyRequest` gains the eight admin fields. Validation is **all-or-nothing as a group**:
supply every admin field or none. A half-filled admin block is guaranteed to fail at XTRM, and failing it at
the boundary tells the user which field is missing instead of surfacing a vendor error minutes later.

The group being optional keeps the endpoint backward-compatible and honours the case where a company is
created with no payout intent.

### `POST /partner-companies/{id}/xtrm/connect` — new

One endpoint, three jobs (D-3 and the retry half of D-2):

1. Provision a company that predates this feature — the body carries the admin details, since the company
   has none stored. They are persisted to `partner_companies` before provisioning runs, so a retry does not
   need them again.
2. Retry a provisioning attempt that failed — **empty body**; it resumes from whatever the `PENDING` row
   already holds.
3. Supply a wallet id manually when step 3 of §4 could not discover one — body carries `xtrmWalletId` alone.

The body is therefore entirely optional, and which of the three happened is decided by the row's state, not
by a mode flag the caller has to get right.

Idempotent by state: if the row is already `CONNECTED` it returns the current state and calls nothing. It
never re-runs `CreateBeneficiary` for a company that already has an SPN — that call is not replayable, and a
second attempt would either fail on the duplicate name or, worse, mint a second account for one company.

Permission: the same client-admin permission that guards company create.

### `GET /partner-companies/{id}` and the list response

`PartnerCompanyResponse` gains a nested block:

```json
"xtrmAccount": {
  "status": "PENDING",
  "accountNumber": "SPN…",
  "identityLevel": "Basic",
  "lastError": "…"
}
```

Never the credentials, in any shape, under any field name.

---

## 7. The remitter switch

Distribution payouts reach XTRM through `XtrmVendorService:172`, which today calls the **no-arg**
`transferFund(cmd)` overload — platform credentials. That one line is the entire switch:

```java
XtrmCredentials creds = distributionItemRepository.findByRedemptionRequestId(request.getId())
        .flatMap(item -> distributionRepository.findById(item.getDistributionId()))
        .map(d -> credentialsResolver.forCompany(d.getClientId(), d.getPartnerCompanyId()))
        .orElseGet(credentialsResolver::platform);

TransferFundResult result = xtrmApiClient.transferFund(cmd, creds);
```

**No distribution row → platform credentials.** Personal redemption takes the `orElseGet` branch and behaves
byte-identically to today, which is the regression guarantee this feature has to make.

`forCompany` **throws** for a company that is not `CONNECTED` rather than falling back to the platform. That
is `XtrmCredentialsResolver`'s existing, deliberate behaviour and it matters here: a silent fallback would
look like success while paying the seller out of the **client's** money — a real transfer from the wrong
pocket that nothing downstream would notice.

### Reconciliation has to make the same choice, or items strand

**This is the half the first draft of this design missed entirely.**

`RedemptionReconciliationService:158` polls in-flight payouts through
`XtrmApiClientImpl.getTransactionDetails`, which hard-codes the **platform** `issuerAccountNumber` into the
request body *and* uses the platform-token `post(...)` overload. `getBatchStatus` has no credentials
overload at all. Both are correct today, because today the platform remits everything.

The moment §7 makes a company the remitter, that stops being true — and the failure is not a missing
feature, it is stranded money:

> `CompanyDistributionDispatcher:196` deliberately leaves an **ambiguous** payout in `PROCESSING` and
> comments that reconciliation will settle it. "Never release on an unknown outcome" is the right call. But
> if reconciliation queries as the platform for a transaction the *company* remitted, `res.found()` is false
> on every run, forever. The item never settles, and the recipient's share stays reserved indefinitely.

So `getTransactionDetails` and `getBatchStatus` both gain a credentials overload, and
`RedemptionReconciliationService` resolves the remitter **through the same shared method** dispatch uses
(D-11). Not a copy of the logic — the same method. Two independent implementations of "who paid for this?"
that disagree would produce exactly the stranding above, and the disagreement would be invisible until an
item aged out.

### Eligibility

`DistributionRecipientService` gains one ineligibility reason, sourced from
`XtrmCredentialsResolver.canPayFromOwnWallet`: *"your company isn't connected to XTRM yet"*.

It is reported on **listing**, not only on submit, following the decision in `6a8a70762` that the rails stay
browsable and only *sending* is withheld. An admin should see why a rail is closed before building a
distribution, not after.

---

## 8. What still gates real money

The credential finding in §3 removed the largest blocker, and §8.1 has since dissolved the second — there is
no OTP flow to build. One real gate remains (§8.2, funding), plus one piece of follow-on work that §8.1
identifies in its place.

**This design ships regardless of either** — a company that cannot pay simply never reaches `CONNECTED`, and
`WALLET_CREDIT` stays open the whole time.

### 8.1 Company → seller binding — RESOLVED 2026-08-25 (no OTP flow needed)

**The working theory in this section was wrong, and the resolution is simpler than the problem.** A seller is
bound to whichever account *created* them. There is no handshake to perform — there is an issuer to get right
at enrollment time.

Verified against the sandbox as company `SPN26241004`, one call, two users differing only in who created them:

| Seller | Created by | `OTP/GetConnectedStatus` from the company |
|---|---|---|
| `PAT26241022` | **the company** (`Register/CreateUser` with its pseudo credentials) | **`Connected`** |
| `PAT26240089` | the platform | `Not Connected` |

A controlled comparison: same company, same endpoint, same moment. Creating the user with the company's
credentials binds it to that company, and `TransferFund` from that company then works. Per XTRM, the client
can still pay a company-created user with its own token — so one PAT serves both remitters and nothing about
personal redemption needs to change.

**The 2026-08-19 result was measuring something else.** That company returned *"Not authorized to access this
resource"* — it could not call the OTP API at all. A `CreateBeneficiary`-created company answers cleanly. The
earlier reading, that a per-pair OTP handshake was required, was an inference from a company that lacked API
authority in the first place.

#### What this costs

The seller-facing OTP flow this section anticipated **does not exist as work**. What replaces it is smaller
but sharper-edged:

| Change | Note |
|---|---|
| `XtrmApiClient.createUser` gains a credentials overload | Same shape as `transferFund`, `getTransactionDetails`, `getBatchStatus` |
| `XtrmEnrollmentService` enrolls **as the seller's own company** | Resolves `partnerCompanyId` → that company's credentials |
| Enrollment must wait for the company to reach `CONNECTED` | **The sharp edge.** Enrollment is eager at profile completion today; enrol a seller under the platform once and they are bound to the wrong issuer permanently. This is an ordering constraint, not a credentials swap. |

#### Still open: existing sellers

Sellers already enrolled are platform-bound and carry real payout history (`PAT26240089` has 27 successful
personal payouts). They cannot receive company distributions as they stand.

**Untested, deliberately:** whether XTRM will create a user whose email already exists under a different
issuer. Testing it needs a real seller's address, and on success it would mint a second XTRM identity for a
real person. That question decides whether existing sellers can be migrated at all, or whether they stay
platform-only — personal redemptions working, company distributions not reaching them.

### 8.2 Funding moves no money at XTRM (known, D-8)

`POST /wallets/company/{companyId}/fund` (`WalletController:67`) credits the **internal ledger only**. Enable
company-remitted payouts without wiring XTRM Step 1 into funding and every distribution fails at XTRM for
insufficient funds while our UI shows a healthy balance — the two balances drift apart silently.

This is sequenced as its own phase because it changes what funding *means*, and because conflating it with
provisioning would make both harder to verify.

### 8.3 Smaller unknowns, to settle on the first company-authenticated call

- **`programId` is the platform's.** `XtrmCredentialsResolver.forCompany` passes the global
  `redemption.xtrm.program-id` alongside a company's account and wallet. Whether a company's payouts book
  under the platform's program is unverified. It costs nothing to check on the first real call, and
  everything to assume.
- **Webhooks are unproven for company-remitted transfers.** `RedemptionWebhookController` carries a single
  global signing secret and its header name is still marked `PLACEHOLDER` in the code. Whether XTRM
  delivers callbacks for a company-remitted transfer, and under which secret, is unknown. Reconciliation
  (§7) is the backstop that makes this survivable rather than blocking.

---

## 9. Failure behaviour

| Failure | Result |
|---|---|
| `CreateBeneficiary` unreachable or rejects | Company is created. The claim row stays `PENDING` carrying **only** `last_error` — no SPN exists yet, which is why §5.2 makes `xtrm_account_number` nullable. Nothing user-facing fails. |
| Two provisioning attempts race | The claim insert loses on `uq_xtrm_account_per_company` and never calls XTRM. No orphan account. |
| Token fetch fails (step 2) | Row stays `PENDING`, `last_error` records the OAuth error code — never the secret. |
| Wallet discovery fails (step 3) | Row stays `PENDING`. Credentials are safe. Connect endpoint retries or accepts a manual wallet id. |
| Credentials undecryptable | `forCompany` throws `XTRM_COMPANY_NOT_CONNECTED`; the log carries partner id and exception class only. |
| A distribution is submitted while `PENDING` | Refused at eligibility with a reason, before any money is reserved. |

Provisioning runs **after commit**, not inside `createPartnerCompany`'s transaction. An HTTP call inside that
transaction would hold a database connection open for the vendor's latency on every company create. This is
the same after-commit pattern already used for CASH INSTANT dispatch.

`AccountIdentityLevel` is stored and displayed. The gate is a config property listing acceptable levels,
**permissive by default** (D-5): we have observed exactly one value, and hard-coding a rule from one
observation would block real companies on a guess. When XTRM tells us which level clears payouts, the
property tightens without a code change.

**Said plainly: as shipped, that gate does nothing.** It is a configured lever with no default opinion, not
a protection. Anything that depends on identity level being enforced is depending on someone setting the
property.

### 9.1 Deleting a partner company

`PartnerCompanyService.deletePartnerCompany:190` is a **hard** delete, and V57 declares
`partner_company_id ... REFERENCES partner_companies(id)`. Left alone, every provisioned company becomes
undeletable — and it would surface as a generic `DATA_INTEGRITY_VIOLATION` whose fixed message names neither
the constraint nor the reason, so the first report would be "delete is broken", not "this company has an
XTRM account".

Per D-12: flip the row to `DISABLED`, then delete it in the same transaction as the company. Nothing is
deleted at XTRM — we have no endpoint for it, and an abandoned beneficiary company with no credentials in
our database can move no money.

### 9.2 Two operational prerequisites, neither introduced here

**The encryption key has a hard-coded default.** `ConnectorEncryptionService` reads
`${app.connector.encryption-key:0123456789abcdef0123456789abcdef}` and, on a wrong-length key, logs a
warning and pads it. Until this design, an unset property meant connector config was weakly protected; now
it means the authority to move a partner company's money is encrypted with a key that is in the repository.
**Fail startup instead of defaulting outside local.** This is a prerequisite in the implementation plan, not
a follow-up.

**`local` runs the real client.** `XtrmApiClientStub` is `@Profile({"localtest","test"})`; `local`
deliberately uses `XtrmApiClientImpl` against the XTRM sandbox. So creating a partner company in local dev
mints a live sandbox beneficiary — tolerable — and, with `EmailNotification: "true"`, emails whatever
address was typed into the form, which is not. Hence D-13: config-driven, off outside prod.

---

## 10. Frontend

The add/edit company form lives inside `UserSettingsPage.tsx`, **3852 lines**, alongside users, roles and
permission overrides.

**Extract it** (D-9) into its own component before adding fields. Eight new inputs and a grouped validation
rule make that file materially worse, and it is the file being edited either way — this is improving code we
are already working in, not unrelated refactoring. The extraction is mechanical: the form state, the
`toCreateRequest` mapper at `:1456` and the two dialogs move together.

Then:

- A **Company Admin** section in the form — the eight fields, validated as a group, with copy explaining
  that these details create the company's payout account.
- An **XTRM status** row on the company view: status badge, account number, identity level, and a
  **Connect** action for `PENDING` / failed rows that calls `POST /partner-companies/{id}/xtrm/connect`.
- The distribution recipient table already renders per-seller ineligibility reasons; the new company-level
  reason needs no new UI, only the string.

---

## 11. Existing code touched

| File | Change |
|---|---|
| `PartnerCompany` | eight admin columns |
| `CreatePartnerCompanyRequest` / `UpdatePartnerCompanyRequest` | eight admin fields, grouped validation |
| `PartnerCompanyResponse` | nested `xtrmAccount` block |
| `PartnerCompanyService` | claim row in-transaction; after-commit provisioning hook; delete disables + removes the XTRM row |
| `PartnerCompanyController` | `POST /{id}/xtrm/connect` |
| `XtrmApiClient` / `Impl` / `Stub` | new `createBeneficiary(...)`; **credentials overloads for `getTransactionDetails` and `getBatchStatus`**; wallet lookup reused as-is |
| `XtrmVendorService:172` | resolve credentials instead of defaulting to platform |
| `RedemptionReconciliationService:158,176` | resolve the remitter through the shared method (D-11) |
| `ConnectorEncryptionService` | fail startup on a missing key outside local |
| `DistributionRecipientService` | one new ineligibility reason |
| `UserSettingsPage.tsx` | company form extracted out |
| V58 migration | company admin columns; `partner_company_xtrm_accounts` amendments |
| `../tenxengage-contracts/endpoints/partner-companies.yaml` | the eight admin properties, the `xtrmAccount` block, the connect endpoint |
| `../tenxengage-contracts/models/partner-company.md` | admin fields and the XTRM account concept |

**New:** `XtrmCompanyProvisioningService` — owns §4 end to end. It is a separate service rather than more
weight on `PartnerCompanyService` because it has one purpose, it is the only thing that may write
credentials, and it is the unit worth testing in isolation.

---

## 12. Testing

**Backend unit**

- Provisioning happy path: three calls, row reaches `CONNECTED`.
- **Credentials persist when step 2 or step 3 throws.** The single most important test in this design.
- `CreateBeneficiary` failure leaves the company created, nothing user-facing broken, and a `PENDING` row
  holding `last_error` with a null account number.
- Connect endpoint: provisions a legacy company, retries a `PENDING` row, refuses to re-create for a company
  that already has an SPN, is a no-op when `CONNECTED`.
- Remitter selection: a distribution leg resolves company credentials; a personal redemption resolves
  platform credentials.
- `forCompany` throws rather than falling back for a `PENDING` company.
- V58 constraint: `CONNECTED` with a null wallet id is rejected by the database.
- No log line and no API response contains the secret.
- **A second concurrent provisioning attempt loses the claim insert and never calls XTRM.** Guards G-3.
- **Reconciliation resolves the same credentials as dispatch for the same payout leg.** Guards G-1, and
  should assert they come from one method rather than asserting two equal values.
- Deleting a provisioned company succeeds and removes the XTRM row.
- `PhoneDialCodes` formats `AdminMobileNumber`; an unsupported `admin_country_iso2` is refused at the API.

**Frontend**

- Grouped admin validation: all eight or none.
- Extracted form renders and submits identically for create and edit.
- XTRM status row renders each status; Connect appears only for `PENDING` / failed.

The `test` task is green at ~1429 and stays there. `integrationTest` carries ~14 pre-existing environment
failures unrelated to this work — verify against `git diff` before attributing any to it.

---

## 13. Review pass, 2026-08-24

The design above was approved and then reviewed against the code. Nine gaps; three would have shipped as
defects.

| # | Gap | Where it landed |
|---|---|---|
| G-1 | Reconciliation queries as the platform, so company-remitted payouts strand in `PROCESSING` with the recipient's share reserved | §7, D-11 |
| G-2 | Hard delete + FK makes every provisioned company undeletable, behind a generic 409 | §9.1, D-12 |
| G-3 | The vendor call preceded any row, so a race minted orphan XTRM accounts | §4, D-10 |
| G-4 | `ConnectorEncryptionService` defaults to a key committed to the repository | §9.2 |
| G-5 | `local` runs the real client, so company creation emails a possibly-typo'd address | §9.2, D-13 |
| G-6 | Company names are unique per tenant here, plausibly global at XTRM | §5.4 — *unverified*, defended against |
| G-7 | `AdminMobileNumber` formatting and country support ignored, duplicating `CreateUser`'s logic | §5.1 |
| G-8 | `programId` and webhook delivery unproven for a company remitter | §8.3 — *unverified*, named |
| G-9 | D-5's identity-level gate reads as a protection but ships inert | §9 |

G-6 and G-8 are hypotheses, not findings. They are written as open questions with a cheap defensive
measure, because the alternative — asserting them — is how a design doc starts lying.

The one that changed my mind is **G-3**. Claiming the row first looked like extra machinery; it is the
opposite. It gives `last_error` somewhere to land in every failure mode, which removes a patch the first
draft needed, and it turns the nullable columns of §5.2 from a concession into the mechanism.

## 14. Why this shape

The feature was blocked on something that turned out not to be true. The plan recorded per-company
credentials as a manual portal step, which made the payout rails a per-company onboarding project; the
`CreateBeneficiary` response hands those credentials back automatically, which turns the same thing into a
side effect of creating a company. Almost everything else here follows from taking that seriously: if
credentials arrive automatically, they arrive **once**, and the design's first obligation is not to lose
them — hence persist-then-enrich, hence a nullable wallet column, hence a `PENDING` state that means
something. The rest is wiring that already exists (`transferFund(cmd, credentials)`, `forCompany`, the
per-client-id token cache) finally being called, and one honest admission in §8 that a second authorization
question is still open and does not block shipping this.
