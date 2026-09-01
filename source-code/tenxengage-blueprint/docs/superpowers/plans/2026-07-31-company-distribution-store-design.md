# Company Distribution Store — System Design

> **Status**: DESIGN — **CLOSED**. All 15 decisions + nav locked, reviewed against live code, regression-traced (§13). Ready for the implementation plan. No code yet.
> ⚠️ **Blocked on a working-tree cleanup before any branching** — see §12.2.
> ⚠️ **Scope grew with OQ-6**: retiring "company redemption" exposed a live gap — a Partner Admin cannot currently redeem anything at all (§12.5).
> **Date**: 2026-07-31 · **Decisions locked**: 2026-08-03 · **Author**: Claude + @pushpendra
> **Extends**: F-01 wallet-ledger-foundation, F-02 redemption-catalog, F-03 redemption-flow, F-05 redemption-history
> **Kind**: enhancement (extends existing features; not a new roadmap feature — the roadmap has a fixed 9)

---

## 0. Decisions locked (2026-08-03, @pushpendra)

| # | Decision |
|---|---|
| OQ-1 | **Per-recipient `TransferFund` for all rails.** No `BatchTransfer` in v1 (it cannot carry a gift card — F-1). |
| OQ-2 | **No approval workflow.** The admin spends their own company's budget. |
| OQ-3 | **Distributions are EXCLUDED from redemption analytics entirely.** Nothing about a distribution appears on any analytics dashboard, trend, breakdown or breakage figure. Implementation surface in §12.3. |
| OQ-4 | **Transferred wallet credits expire under the client admin's configured cash-currency policy**, exactly like any earned reward. **This needs zero implementation** — §12.4 explains why, and flags one behavioural consequence. |
| OQ-5 | **One amount for every recipient.** The admin types `50` once; every selected seller receives $50. API takes a single `amount` + a list of `userIds`; per-item `amount` stays in the schema because each item still needs its own ledger/reserve leg. |
| OQ-7 | **The admin cannot include themself as a recipient.** They already redeem through their own individual seller flow. The caller is filtered out of the recipient list server-side, not just hidden in the UI. |
| OQ-8 | **No cap on recipient count.** See the operational note in §5.1. |
| OQ-9 | **No maker/checker on the funding API.** CLIENT_ADMIN / PLATFORM_ADMIN only, audited, idempotent on `reference`. |
| OQ-6 | **"Company redemption" is not a concept — it is deleted, not just blocked.** A partner admin redeems **as a seller, from their own individual wallet**; the company wallet's *only* outflow is distribution. The `XtrmVendorService` guard narrows to allow `COMPANY` only for `origin = COMPANY_DISTRIBUTION`, **and** the `submitCompanyRedemption` endpoint/service/DTO and the store's company-wallet source are removed. Fallout in §12.5 — it is bigger than it looks. |
| OQ-10 | **Cash only.** `currency_id = 'cash'` always; no currency selector anywhere. |
| OQ-11 | **Merge `features/redemption-store-feedback` → `roadmaps/redemption-store` first, then branch from the roadmap branch.** Fast-forward is available in all four repos — but there is uncommitted work and unpushed commits to deal with first (§12.2). |
| OQ-12 | **Distributions are excluded from every existing redemption surface** — analytics, breakage, tenant "All Redemptions", CSV export, and the seller's personal history + detail (§12.3). `ExportScope.COMPANY` is deleted as a consequence. |
| OQ-13 | **Only PARTNER_ADMIN and PARTNER_SELLER see distribution data.** Admin → all of their company's distributions with an "Initiated by" column; seller → their own awards. CLIENT_ADMIN gets no access to either (§6.2). |
| OQ-14 | **Recipients are active PARTNER_SELLERs of the caller's company only** (§6.2 B). |
| OQ-16 | **`redemption_requests` gains only `origin`** — `initiated_by_user_id` is dropped from it and lives solely on `company_distributions`, reachable via `company_distribution_items.redemption_request_id`. It was redundant: every query needing the initiator already joins the header for `rail`/`note`. One fact, one home (§4.2). |
| OQ-15 | **`redemption_requests` keeps its name — no rename.** It now holds payout legs for both the redemption store (`origin = SELF`) and the distribution store (`origin = COMPANY_DISTRIBUTION`). Renaming it (`wallet_payout_requests` was the candidate) would touch 73 Java files, 4 materialized views, two FK'd tables and a live `ledger_entries.reference_type` data migration — too much risk to fold in beside a new money flow. The naming debt is accepted and mitigated by documentation, not deferred silently (§4.2). |

Terminology: the third rail moves money into the seller's **system cash wallet** (`reward_wallets`, `wallet_type = INDIVIDUAL`) — *not* their XTRM digital wallet. It is `WALLET_CREDIT` in code and **"Wallet Transfer"** in the UI.

Still open: the **nav placement** confirmation only.

### The core invariant (from OQ-6)

> **The company wallet has exactly one outflow: distribution.**
> A partner admin's own redemptions come from their own individual wallet, through the ordinary seller flow. There is no such thing as a company redemption.

Everything in this design follows from that. It is worth stating because the codebase currently contradicts it in three places (§12.5), and because it makes the company wallet's ledger trivially auditable: every `RESERVE`/`DEBIT` on a company wallet belongs to a distribution, with no exceptions to reason about.

---

## 1. Scope

Three new surfaces, one new money flow — **and one concept removed**: "company redemption" (a partner admin spending the company wallet on themself) is deleted, because the company wallet's only outflow is distribution (§0, §12.5). A partner admin's own redemptions go through the ordinary seller flow, from their own individual wallet.

| Persona | Surface | What it does |
|---|---|---|
| Partner Admin | **Distribution Store** | Spend the **company wallet** on the company's own sellers |
| Partner Admin | **Distribution History** | Every company-wallet distribution, with per-recipient outcome |
| Partner Seller | **Company Award History** | Every reward received from company admins |
| Client Admin | **Fund company wallet** (API) | Replaces manual `INSERT` into `reward_wallets` |
| Partner Admin | *(fix)* **redeem for himself** | From his **own individual wallet**, via the ordinary seller flow — never from the company wallet. Needs `action.redemption.redeem` granted; he cannot redeem at all today (§12.5) |

Three distribution **rails**:

| Rail (code) | UI label | Money leaves the platform? | Recipient prerequisite | Vendor |
|---|---|---|---|---|
| `GIFT_CARD` | Gift Card | Yes → XTRM digital gift card, emailed to the seller | XTRM-enrolled + email on file | XTRM `TransferFund` (XTR94505) |
| `BANK_TRANSFER` | Bank Transfer | Yes → seller's linked bank (ACH) | ≥1 active linked bank | XTRM `TransferFund` (XTR94500) |
| `WALLET_CREDIT` | Wallet Transfer | **No** → company wallet ➝ seller's **system cash wallet** (`reward_wallets`, `INDIVIDUAL`) | none (any active company user) | none — internal ledger only |

**All three rails reserve at submit and settle per recipient.** The company wallet's funds are earmarked the moment the distribution is accepted, and each recipient's leg either completes or releases its own share independently — so a single bad recipient never blocks or silently consumes anyone else's money.

---

## 2. What already exists (verified in code)

This is the single most important input to the design: **most of the machinery is already built.**

| Capability | Where | State |
|---|---|---|
| COMPANY-type wallets | `reward_wallets.wallet_type`, `chk_wallet_owner`, `uq_reward_wallets_company` | ✅ built (V6) |
| Company wallet credit | `WalletService.creditCompany()` | ✅ built, **zero callers** — no API yet |
| Company wallet read API | `WalletService.getCompanyWallets()` → `GET /api/v1/wallets/company/{id}` | ✅ built, PARTNER_ADMIN-scoped |
| Double-entry ledger on any wallet type | `ledger_entries.reward_wallet_id` (generic FK) | ✅ built (V7) |
| Company-wallet redemption submit | `RedemptionSubmissionService.submitCompanyRedemption()` | ✅ built (admin → self) |
| Company-wallet payout **dispatch** | `XtrmVendorService.dispatch()` L96–99 | ❌ **hard-blocked**: `throw COMPANY_PAYOUT_NOT_SUPPORTED` |
| Two-rail routing (bank vs gift card) | `XtrmVendorService.dispatch()` L125–154 | ✅ built |
| Gift-card SKU catalog | `XtrmApiClient.getDigitalGiftCards()`, `catalog_items.provider_item_id`, `value_type` | ✅ built |
| After-commit async dispatch + crash recovery | `RedemptionSubmissionService.dispatchInstantCashAfterCommit()`, `BatchRedemptionProcessor.processBatch()` | ✅ built |
| Settlement (reserve→debit / release+fail) | `RedemptionWebhookService.settle()` — keyed on `request.getWalletId()`, wallet-type agnostic | ✅ built |
| Reconciliation cron (missed webhook) | `RedemptionReconciliationService` | ⚠️ built but **`INDIVIDUAL`-only** — must be widened, see F-8 |
| Company history read | `RedemptionHistoryRepository.findCompanyHistoryByPartnerCompany()` — scoped by `wallet.partnerCompanyId` | ✅ built |
| Audit | `@Audited` → `audit_logs` (generic resourceType/resourceId) | ✅ built |

**Consequence:** the new backend work is mostly *composition + guard removal*, not new payout infrastructure.

---

## 3. Findings that shape the design

These are hard constraints found in code, not preferences. They pre-answer several of the open questions in the brief.

### F-1 — `BatchTransfer` cannot carry a gift card. Confirmed, not a guess.

`XtrmApiClientImpl.batchTransfer()` L228–247 builds each item's `Destination` as **exactly one of**:

```java
"BeneficiaryBankID" + "BeneficiaryBankPaymentMethod"   // bank / ACH
"WalletId"                                             // AnyPay
"CardToken"                                            // card
```

There is no `SKU` and no `UserGiftCardEmailID` field anywhere in the batch envelope. Those two fields exist **only** on `TransferFundCommand` (`XtrmApiClient` L193–204) and are only sent by `transferFund()`. So:

- **Gift-card distribution via `BatchTransfer` is not expressible today.** It would need a new XTRM request shape whose field names are unverified — and `XtrmApiClientImpl`'s own javadoc (L33–37) admits the batch envelope's field names "were not pinned in code at authoring time."
- **Bank distribution via `BatchTransfer` *is* expressible** — `BeneficiaryBankID` is precisely what a bank distribution needs.

→ **Recommendation: use per-recipient `TransferFund` for both rails in v1.** Reasons beyond F-1: one dispatch path ⇒ one failure model, one reconciliation path, one test surface; the partner admin gets immediate per-recipient feedback instead of "goes out at 2am"; `TransferFund` returns a per-item `transactionId` synchronously, so per-recipient status is exact; XTRM caps a batch at 20 items so we'd chunk anyway. `BatchTransfer` stays available as a **Phase 2** optimisation for large bank-rail distributions — the design deliberately keeps the plumbing (`customer_batch_id`, `customer_transaction_id`, batch-status reconciliation) reachable.

### F-2 — The existing BATCH machinery is the *wrong* batch

`RedemptionProcessingMode.BATCH` means "queue until the nightly cron at `scheduled_batch_date`", and `XtrmVendorService.prepareBatchItems()` L235 resolves each rail from `profile.getPayoutMethod()` — the **legacy per-user default**, which the two-rail model already superseded for store redemptions. Reusing it for distributions would mean forking that resolution logic *and* delaying the admin's payout by up to a day. Distributions use `processing_mode = INSTANT`.

### F-3 — Free crash-recovery, if we use `INSTANT`

`BatchRedemptionProcessor.processBatch()` L123–135 already reclaims rows that are `PROCESSING + INSTANT + vendor_reference_id IS NULL + dispatch_attempted_at IS NULL`. Distribution items created as `INSTANT`/`CASH` inherit that sweep with **zero new recovery code**.

### F-4 — `redemption_requests.user_id` currently means *both* actor and beneficiary

`user_id NOT NULL REFERENCES users(id)` (V16 L14) is read as "the person who redeemed" by every existing query. A distribution needs **actor ≠ beneficiary**. This is the crux of the "same tables or new tables?" question (§4).

### F-5 — Two existing queries break if distributions land in `redemption_requests` naively

1. **In-flight cap** — `countByClientIdAndUserIdAndStatusIn(clientId, userId, IN_FLIGHT)` with `maxInFlight` default 10. If a distribution row carries `user_id = recipient`, a company award consumes the *recipient's* personal in-flight budget and can block their own redemptions — or the 11th recipient of a distribution gets rejected.
2. **Personal history** — `findPersonalHistory` filters on `user_id`, so awards would silently appear inside the seller's own Transaction History as if they had redeemed them.

Both are fixed by a discriminator column (§4.2) — but they must be fixed *deliberately*, which is why the discriminator is non-optional.

### F-6 — `RedemptionRequestType` is already taken

`entity/enums/RedemptionRequestType.java` = `{REDEMPTION, RETURN}`, used by the approval queue. The new discriminator needs a different name → **`RedemptionOrigin { SELF, COMPANY_DISTRIBUTION }`**.

### F-7 — `catalog_item_id` is `NOT NULL`

V16 L15. The `WALLET_CREDIT` rail has no catalog item and no vendor, which is the deciding argument in §4.1.

### F-8 — 🔴 Reconciliation is hard-filtered to `INDIVIDUAL` wallets

`RedemptionReconciliationService` (the missed-webhook recovery) passes the wallet type explicitly:

```java
findInFlightForReconciliation(client.getId(), RedemptionCategory.CASH, WalletType.INDIVIDUAL, IN_FLIGHT, cutoff);
countStuckPastCap(       client.getId(), RedemptionCategory.CASH, WalletType.INDIVIDUAL, IN_FLIGHT, cutoff);
```

Distribution payout legs are `wallet_type = COMPANY`, so **they are invisible to reconciliation** — and to the `recon_past_cap` warning that flags payouts stuck beyond the 3-day cap. An ambiguous XTRM outcome on a distribution would leave funds reserved on the company wallet indefinitely, **with no alert**.

**Both calls must be widened to cover `COMPANY` as well.** `reconcileSingle` itself needs no change: it resolves the PAT via `partnerRedemptionRepository.findByUserIdAndClientId(r.getUserId(), …)`, and `user_id` is the recipient (§4.2), so it resolves correctly once the filter lets the row through.

**Not to be confused with F-3.** The *crash-recovery* sweep in `BatchRedemptionProcessor` has **no** wallet-type filter, so that part really is free. Only missed-webhook reconciliation is broken.

---

## 4. Domain model — the "same tables or new tables?" answer

**Answer: both. Reuse `redemption_requests` for the two payout rails; add two small new tables for the distribution itself; keep `WALLET_CREDIT` out of `redemption_requests` entirely.**

### 4.1 Why not one extreme or the other

**Why not all-new tables.** Everything downstream of a payout is keyed on `redemption_requests.id`: `ledger_entries.reference_id` (+ the `uq_ledger_credit_idempotency` index), `redemption_webhook_events.redemption_request_id`, `settle()`, the reconciliation cron, the crash-recovery sweep, the batch id columns, history, analytics MVs, exports, returns. A parallel payout table means re-implementing **two independent money paths** — the highest-risk possible choice for a payout system.

**Why not reuse `redemption_requests` for all three rails.** `WALLET_CREDIT` moves money *within* the platform. If it becomes a `redemption_requests` row it is counted by `RedemptionAnalyticsService` and the advanced-analytics MVs as a redemption — and then counted **again** when the seller actually redeems that balance. Guaranteed double-counting of "total redeemed", plus a distorted breakage report. It also needs `catalog_item_id` (F-7) to be nullable or faked with a synthetic reserved card. Not worth it.

### 4.2 Changes to `redemption_requests` — **1** new column

⚠️ **The table name is now narrower than its contents (OQ-15).** `redemption_requests` holds payout legs for *both* stores. The name is kept deliberately — a rename costs 73 Java files, 4 MVs, two FK'd tables and a live `reference_type` data migration, which is not worth folding in beside a new money flow. The mitigation is that `origin` is the **documented discriminator**, stated where anyone will look:

- a `COMMENT ON TABLE redemption_requests` in V51 spelling out that it serves both stores and that `origin` distinguishes them;
- the same note in the `RedemptionRequest` entity javadoc, including that `user_id` is the **recipient** when `origin = COMPANY_DISTRIBUTION`.

Nobody should have to infer the table's scope from its name. If a rename is ever wanted, do it standalone and V6-style (with `@Deprecated` shims), never alongside feature work.

```sql
-- V51__add_distribution_origin_to_redemption_requests.sql
ALTER TABLE redemption_requests
  ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'SELF';

COMMENT ON TABLE redemption_requests IS
  'Wallet payout legs for BOTH the redemption store (origin=SELF) and the '
  'distribution store (origin=COMPANY_DISTRIBUTION). When origin=COMPANY_DISTRIBUTION, '
  'user_id is the RECIPIENT and wallet_id is the COMPANY wallet the money came from; '
  'the initiating partner admin is on company_distributions.initiated_by_user_id, '
  'reachable via company_distribution_items.redemption_request_id.';

CREATE INDEX idx_redemption_requests_origin
  ON redemption_requests(client_id, origin);

-- backfill is implicit: every existing row is SELF
```

**One column only (OQ-16, locked 2026-08-04).** An earlier draft also added `initiated_by_user_id` here. It was dropped as redundant: the initiator is reachable via `company_distribution_items.redemption_request_id` → `company_distributions.initiated_by_user_id`, and every query that needs it — Distribution History's "Initiated by", Company Award History's "Awarded by" — already joins the header for `rail` and `note`, so the join costs nothing. One fact, one home, no drift.

Semantics for an `origin = 'COMPANY_DISTRIBUTION'` row:

| Column | Value | Why |
|---|---|---|
| `user_id` | the **recipient** seller | so the recipient's PAT / bank / email resolve with **zero dispatch changes** — `XtrmVendorService.dispatch()` already reads `request.getUserId()` |
| `wallet_id` / `wallet_type` | the **company** wallet / `COMPANY` | money is drawn from the company budget; `settle()` already operates on `getWalletId()` |
| `origin` | `COMPANY_DISTRIBUTION` | the discriminator that fixes F-5 |
| `processing_mode` / `category` | `INSTANT` / `CASH` | inherits the proven after-commit path + free crash recovery (F-3) |
| *initiator* | **not stored here** | `company_distributions.initiated_by_user_id`, one join away |

This is the key move: **`user_id = recipient` makes the entire existing payout pipeline work unchanged.**

### 4.3 Two new tables

```sql
-- V52__create_company_distribution_tables.sql

CREATE TABLE company_distributions (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id             UUID          NOT NULL REFERENCES clients(id),
    partner_company_id    UUID          NOT NULL REFERENCES partner_companies(id),
    source_wallet_id      UUID          NOT NULL REFERENCES reward_wallets(id),
    rail                  VARCHAR(20)   NOT NULL,          -- GIFT_CARD | BANK_TRANSFER | WALLET_CREDIT
    catalog_item_id       UUID          NULL REFERENCES redemption_catalog_items(id),  -- GIFT_CARD only
    currency_id           VARCHAR(50)   NOT NULL,
    initiated_by_user_id  UUID          NOT NULL REFERENCES users(id),
    recipient_count       INT           NOT NULL CHECK (recipient_count > 0),
    total_amount          NUMERIC(19,4) NOT NULL CHECK (total_amount > 0),
    note                  VARCHAR(500)  NULL,              -- admin's message, shown to recipients
    client_idempotency_key VARCHAR(255) NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_distribution_catalog_item CHECK (
        (rail = 'GIFT_CARD' AND catalog_item_id IS NOT NULL)
        OR (rail <> 'GIFT_CARD' AND catalog_item_id IS NULL)
    )
);

CREATE UNIQUE INDEX uq_company_distributions_idem
    ON company_distributions(client_id, client_idempotency_key)
    WHERE client_idempotency_key IS NOT NULL;
CREATE INDEX idx_company_distributions_company
    ON company_distributions(client_id, partner_company_id, created_at DESC);

CREATE TABLE company_distribution_items (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id             UUID          NOT NULL REFERENCES clients(id),
    distribution_id       UUID          NOT NULL REFERENCES company_distributions(id),
    recipient_user_id     UUID          NOT NULL REFERENCES users(id),
    amount                NUMERIC(19,4) NOT NULL CHECK (amount > 0),

    -- GIFT_CARD / BANK_TRANSFER: the payout leg. Status + destination live there.
    redemption_request_id UUID          NULL REFERENCES redemption_requests(id),

    -- WALLET_CREDIT only: its own lifecycle, because it has no redemption_requests row to carry one.
    -- RESERVED -> COMPLETED | FAILED. See §5.6.
    status                VARCHAR(20)   NULL,
    debit_ledger_entry_id  UUID         NULL REFERENCES ledger_entries(id),  -- company side, on settle
    credit_ledger_entry_id UUID         NULL REFERENCES ledger_entries(id),  -- recipient side, on settle
    release_ledger_entry_id UUID        NULL REFERENCES ledger_entries(id),  -- on definitive failure
    failure_reason        VARCHAR(500)  NULL,
    settled_at            TIMESTAMPTZ   NULL,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    -- Exactly one lifecycle owner: a payout leg, or this row's own status.
    CONSTRAINT chk_distribution_item_leg CHECK (
        (redemption_request_id IS NOT NULL AND status IS NULL)
        OR (redemption_request_id IS NULL AND status IS NOT NULL)
    )
);

CREATE INDEX idx_distribution_items_unsettled
    ON company_distribution_items(client_id, status)
    WHERE status = 'RESERVED';   -- drives the stuck-item recovery sweep (§5.6)

CREATE UNIQUE INDEX uq_distribution_item_recipient
    ON company_distribution_items(distribution_id, recipient_user_id);
CREATE INDEX idx_distribution_items_recipient
    ON company_distribution_items(client_id, recipient_user_id, created_at DESC);
CREATE INDEX idx_distribution_items_distribution
    ON company_distribution_items(distribution_id);
```

### 4.4 Status is **derived**, never stored twice

This is a deliberate anti-drift decision. There is **no `status` column** on either new table.

- **Item status** = `COALESCE(rr.status, i.status)` — payout items read the live redemption status, `WALLET_CREDIT` items carry their own (§5.6). So `settle()`, the webhook, the reconciliation cron and the existing recovery sweep keep working **with no knowledge of distributions at all**.

  ⚠️ **Partial loss of the no-drift property (revised 2026-08-03).** Since `WALLET_CREDIT` gained a reserve lifecycle, that rail *does* store status — two rails derive, one stores. Mitigations: only `CompanyDistributionService` ever writes it; the flip commits in the same transaction as the wallet mutations, so status and money cannot disagree; and the `ledger_entries` for the item remain the reconciliation source of truth if they ever appear to. The alternative — deriving wallet-credit status from the presence of ledger rows — was rejected as a query too subtle to be obviously correct.
- **Header status** = rollup over items, computed on read:
  `all COMPLETED → COMPLETED` · `any of PENDING_APPROVAL/RESERVED/PROCESSING → PROCESSING` · `all FAILED/CANCELLED → FAILED` · `mixed terminal → PARTIALLY_COMPLETED`.
- `recipient_count` and `total_amount` **are** stored — they are immutable submission facts, not mutable state.

  ⚠️ **They record what was *requested*, not what moved.** Once items can fail independently, `total_amount` overstates the money that actually left the company wallet. Every surface showing it must label it **"Requested"** and show a computed **"Settled"** alongside (`SUM(amount)` over `COMPLETED` items) — otherwise Distribution History reads as though a partially-failed distribution paid out in full.

### 4.5 Audit — reuse, no new table

`audit_logs` is already generic. Two new `@Audited` resource types:

| Action | resourceType | resourceId | Notes |
|---|---|---|---|
| `DISTRIBUTED` | `COMPANY_DISTRIBUTION` | header id | + rail, recipient_count, total_amount in the description |
| `FUNDED` | `REWARD_WALLET` | company wallet id | funding API (§6.4) |

Per-recipient payout audit already happens via the existing `REDEMPTION_REQUEST` audit rows. Full money trail = `audit_logs` (who/when/intent) + `ledger_entries` (before/after balances on **both** wallets) + `redemption_requests` (vendor refs). No new audit infrastructure.

### 4.6 Answer, in one table

| Concern | Table | New? |
|---|---|---|
| Distribution intent (who/when/rail/note/totals) | `company_distributions` | 🆕 |
| Per-recipient line — **and the whole `WALLET_CREDIT` lifecycle** | `company_distribution_items` | 🆕 |
| Payout leg + its lifecycle (gift card / bank) | `redemption_requests` | reuse **+1 column** (`origin`) |
| Balance movement (both sides, all rails) | `ledger_entries` | reuse as-is |
| Wallets (company source, individual destination) | `reward_wallets` | reuse as-is |
| Vendor callbacks | `redemption_webhook_events` | reuse as-is |
| Audit | `audit_logs` | reuse as-is |
| Notifications | `notifications` | reuse + 1 new type |
| Permissions | `permissions` + 4 grant/override tables | reuse — 3 keys added, 1 deleted |
| Analytics | 5 materialized views | rebuilt with an `origin = 'SELF'` filter |

---

## 5. The three rails in detail

### 5.1 Submit transaction (common, atomic)

`POST /api/v1/redemption/distribution` → **one** transaction:

1. Resolve caller's `partner_company_id`; reject if absent (403).
2. Idempotency: `SELECT` on `(client_id, client_idempotency_key)` → return the existing header.
3. **Lock the company wallet** `findByIdForUpdate(sourceWalletId)`, assert `wallet_type = COMPANY` **and** `partner_company_id = caller's company` **and** `currency_id` matches.
4. Validate recipients: every one is active, `partner_company_id = caller's company`, distinct, **not the caller** (OQ-7), and rail-eligible (§5.5). No count cap (OQ-8).
5. Validate the single `amount` against the rail (SKU min/max for `GIFT_CARD`, catalog min for `BANK_TRANSFER`, `> 0` for `WALLET_CREDIT`), and `amount × recipientCount ≤ wallet.available_balance` **under the lock**.
6. Insert header + N items.
7. Per rail:
   - **`GIFT_CARD` / `BANK_TRANSFER`** — per recipient: insert a `redemption_requests` row (`origin=COMPANY_DISTRIBUTION`, `status=PROCESSING`, `INSTANT`, `CASH`), move `amount` available→reserved on the company wallet, write the `RESERVE` ledger entry (`reference_type='REDEMPTION_REQUEST'`, `reference_id=<request id>`). Reservation total is one net wallet update.
   - **`WALLET_CREDIT`** — **one** `RESERVE` for the whole total on the company wallet (`reference_type='COMPANY_DISTRIBUTION'`, `reference_id=<header id>`), and each item inserted `status='RESERVED'`. The per-recipient debit/credit happens **after commit** (§5.6) — the submit transaction never touches a recipient wallet.
8. Commit → **`202 Accepted`** with the header id + per-item ids.
9. `AFTER_COMMIT` — **all three rails** fan out per item on the bounded executor: payout rails dispatch to XTRM (§5.2), `WALLET_CREDIT` settles the debit/credit pair (§5.6). Nothing external or cross-wallet happens inside the submit transaction.

**Deadlock note.** The submit transaction now locks **only the company wallet** on every rail — recipient wallets are touched after commit, one at a time (§5.6). That removes the multi-wallet lock ordering problem entirely rather than managing it.

**Operational note on the uncapped recipient count (OQ-8).** With no cap, one submit is `N` item inserts + `N` ledger writes while holding the company-wallet row lock, and every other distribution for that company queues behind it. Two things keep that safe rather than merely uncapped:

- The **after-commit fan-out is a bounded executor**, not `N` parallel calls. Recipients are dispatched at a fixed concurrency (config `redemption.distribution.dispatch-concurrency`, default 4) so a 2 000-recipient distribution does not open 2 000 simultaneous XTRM connections or trip a vendor rate limit. It takes longer; it does not fall over.
- Items are **independent** once committed, so a slow tail never blocks or fails the ones already dispatched.

Real company sizes here are tens, not thousands, so the transaction stays small in practice. If a tenant ever does submit a very large distribution the failure mode is *slow*, not *wrong* — and a cap can be added later as a `TenantRedemptionSettings` value without touching the schema. Load-test one large distribution before go-live to pin the practical ceiling.

**Why `WALLET_CREDIT` reserves too (revised 2026-08-03, @pushpendra).** The original design put all N debit/credit pairs in the submit transaction — all-or-nothing, no reserve, on the grounds that an internal transfer has no external step that can go ambiguous. That reasoning was sound but incomplete:

- In a single transaction nothing *can* be half-applied — Postgres rolls back, the company wallet is untouched, so there is literally nothing to release. A reserve there would be redundant bookkeeping.
- **But an uncapped recipient count (OQ-8) makes one giant transaction the wrong shape.** N debit/credit pairs means locking N recipient wallets while holding the company-wallet lock: exposed to lock/statement timeouts and deadlocks, blocking every other distribution for that company, and on failure losing the *entire* distribution.
- The fix is to settle per recipient in its own transaction — **and that is exactly what cannot be done safely without a reserve.** Between two un-reserved chunks, a concurrent distribution (or the same admin's second one) can spend balance the later chunks were counting on, so recipient 400 fails for insufficient funds *after* 1–399 already received money. That is real partial payout with no earmark.

So the reserve is not protecting against a mid-transaction tear; it is what makes it safe to **stop holding one giant lock**. It also makes all three rails uniform — reserve at submit, settle per item — so `PARTIALLY_COMPLETED` means the same thing everywhere.

### 5.2 Dispatch (payout rails only)

`AFTER_COMMIT`, per item, on a bounded executor — this is exactly `dispatchInstantCashAfterCommit()` (`RedemptionSubmissionService` L582–609) applied per item:

1. `stampDispatchAttempt()` in its own committed tx (double-pay guard) → 2. `orchestrationService.dispatch(request)` → CASH → `XtrmVendorService.dispatch()` → 3. `persistVendorRef()`.

Failure split, unchanged from today:

| Outcome | Action | Company wallet |
|---|---|---|
| `BusinessRuleException` (definitive: not enrolled, send limit, bank not linked, rejected) | release reserve, item `FAILED` | refunded |
| `ExternalServiceException` (transport/ambiguous) | leave `PROCESSING` | stays reserved — **never** released (no double-pay) |
| Success | `PROCESSING` until the XTRM webhook (or the reconciliation cron — **once widened per F-8**) settles | reserve → debit on completion |

Recovery: F-3 — free.

### 5.3 The one real dispatch change

`XtrmVendorService.dispatch()` L96–99:

```java
if (request.getWalletType() == WalletType.COMPANY) {
    throw new BusinessRuleException("COMPANY_PAYOUT_NOT_SUPPORTED", ...);
}
```

Replace with: allow `COMPANY` when `origin = COMPANY_DISTRIBUTION`. Everything after that line already resolves the recipient from `request.getUserId()`, picks the rail from `catalogItem.isBankTransfer()`, and snapshots the destination label. **No new vendor code.**

The blocking comment ("needs a company beneficiary / SPN — not an individual PAT") described a *different* feature: paying an XTRM company account. That is not this. Here the recipient is always an individual PAT; the company wallet is purely our internal budget, and the XTRM source is the platform issuer wallet (`SourceWalletId`, client config) exactly as for personal redemptions.

**Narrow the guard, don't delete it (OQ-6 ✅).** Deleting the check outright would un-block the `submitCompanyRedemption` path, where an admin redeems the **company** wallet *for themself* — which OQ-6 says is not a concept. The guard becomes:

```java
if (request.getWalletType() == WalletType.COMPANY
        && request.getOrigin() != RedemptionOrigin.COMPANY_DISTRIBUTION) {
    throw new BusinessRuleException("COMPANY_PAYOUT_NOT_SUPPORTED", ...);
}
```

This opens exactly the new flow and nothing else. It is kept as a **defence in depth** even though §12.5 removes the endpoint that could reach it: historical `wallet_type = COMPANY, origin = SELF` rows already exist in dev from earlier testing, and the guard ensures a reconciliation sweep or a future caller can never pay one out.

### 5.4 `BANK_TRANSFER` uses the reserved bank-transfer card

The bank rail is selected by `catalog_item.is_bank_transfer`, and `BankTransferCardService.ensureBankTransferCard(clientId)` already provides the per-client reserved card idempotently. The distribution service calls it — no new catalog concept.

### 5.5 Recipient eligibility per rail

| Rail | Eligible when | Destination shown to the admin |
|---|---|---|
| `GIFT_CARD` | `partner_redemption.enrollment_status = ENROLLED` **and** `recipient_user_id` non-null **and** `users.email` non-blank | the recipient's email |
| `BANK_TRANSFER` | ≥1 non-deleted `partner_linked_bank`; the **default** bank is used | masked label, e.g. `KOTAK ••8943` |
| `WALLET_CREDIT` | active seller in the company | `Cash wallet` |

Base eligibility for every rail: **active `PARTNER_SELLER` in the caller's company** (§6.2 B), which also satisfies OQ-7 — an admin can never appear in their own recipient list because they are not a seller.

The recipient endpoint returns **all** such sellers, with per-rail readiness flags + an `ineligibleReason`, so the UI can show a greyed row saying *"No payout profile — use Wallet Transfer instead"* rather than hiding people. That is the story that makes `WALLET_CREDIT` valuable: un-enrolled sellers can still be paid, and they redeem it themselves later.

Note: unlike personal dispatch, distribution does **not** lazily enrol (`ensureEnrolledForPayout`) — the admin picks from a pre-filtered list, so enrolment is a precondition, not a side effect.

### 5.6 `WALLET_CREDIT` settle loop + recovery

After the submit transaction commits (total reserved, items `RESERVED`), each item is settled in **its own transaction**, on the same bounded executor as the payout rails:

1. Lock the item row; skip if no longer `RESERVED` (idempotency + concurrent-sweep guard).
2. Lock the company wallet → `DEBIT` from **reserved** balance (`reference_type='COMPANY_DISTRIBUTION_ITEM'`, `reference_id=<item id>`).
3. `ensureIndividualWalletExists` for the recipient → `CREDIT` their cash wallet, same reference pair.
4. Stamp `debit_ledger_entry_id`, `credit_ledger_entry_id`, `settled_at`, `status='COMPLETED'`.

Both wallet mutations and the item flip commit together, so a recipient can never be credited without the company being debited.

**Failure handling**

| Outcome | Action |
|---|---|
| Definitive (recipient deactivated, wallet currency mismatch, validation) | `RELEASE` that item's amount reserved→available, `status='FAILED'`, `failure_reason` set. Only that recipient is affected. |
| Transient (lock timeout, optimistic-lock exhaustion, DB blip) | Leave `RESERVED`. The sweep retries. Funds stay reserved — never released on an unknown outcome. |
| Crash mid-loop | The reserve is already committed, so the money is still earmarked. The sweep finishes the remaining items. |

**No double-spend on retry.** `doDebitInTx` / `doCreditInTx` / `doReleaseInTx` are already idempotent on `(wallet, referenceType, referenceId, entryType)`, and every leg here is keyed on the **item id** — so a re-run after a crash cannot double-credit. This is existing behaviour, not new code.

**⚠️ A new recovery sweep is required.** `BatchRedemptionProcessor.processBatch()` scans `redemption_requests` only (verified: its sole repositories are `ClientRepository`, `RedemptionRequestRepository`, `RewardWalletRepository`, `LedgerEntryRepository`). A `WALLET_CREDIT` item has no `redemption_requests` row, so it is **invisible** to that sweep and would sit `RESERVED` forever. Add a scheduled sweep over `idx_distribution_items_unsettled` (`status='RESERVED'`) that re-attempts settlement, and after a configurable age escalates to manual review rather than auto-releasing — the same conservatism the payout rails use.

---

## 6. API surface

All under the existing `module.redemption_store` module gate.

### 6.1 Partner Admin — Distribution Store

```
GET  /api/v1/redemption/distribution/recipients?rail=GIFT_CARD|BANK_TRANSFER|WALLET_CREDIT
     → [{ userId, fullName, email, role, eligible, ineligibleReason, destinationLabel }]

GET  /api/v1/redemption/distribution/catalog
     → gift-card items redeemable by this client (reuses the catalog browse service:
        isActive, !deleted, !isBankTransfer, ownerClientId = caller's client, providerItemId non-null)
        → [{ id, name, providerImageUrl, valueType, minAmount, maxAmount, currencyId }]

GET  /api/v1/wallets/company/{companyId}                    (existing — source balance)

POST /api/v1/redemption/distribution                        → 202 Accepted
{
  "rail": "GIFT_CARD",
  "sourceWalletId": "…",
  "catalogItemId": "…",                  // GIFT_CARD only
  "amount": 50.00,                       // ONE amount, applied to every recipient (OQ-5)
  "note": "Q3 top performers 🎉",
  "clientIdempotencyKey": "…",
  "userIds": [ "…", "…", … ]             // must not contain the caller (OQ-7)
}
→ { distributionId, rail, amount, recipientCount, totalAmount, status: "PROCESSING",
    items: [ { itemId, recipientUserId, amount, status } ] }
```

No `currencyId` in the payload — cash only (OQ-10); the server takes it from the source company wallet. `totalAmount = amount × recipientCount`.

### 6.2 Partner Admin — Distribution History

```
GET  /api/v1/redemption/distribution?rail=&status=&dateFrom=&dateTo=&page=&size=&sort=
     → paged headers with derived rollup status
GET  /api/v1/redemption/distribution/{id}
     → header + items (recipient, amount, derived status, destination label,
        paymentTransactionId, failureReason)
```

**Visibility (locked 2026-08-03, @pushpendra): PARTNER_ADMIN and PARTNER_SELLER only.**

| Role | Sees | Scope |
|---|---|---|
| PARTNER_ADMIN | Distribution History — what was distributed to sellers, with per-recipient detail | **own company only** |
| PARTNER_SELLER | Company Award History — what they received | **own awards only** |
| CLIENT_ADMIN / PLATFORM_ADMIN | ❌ nothing | no access to either surface |

Enforced the same way `getCompanyWallets` already does it: resolve the caller's `partner_company_id` and reject a mismatch, rather than trusting a company id from the request.

⚠️ **Consequence — the client admin has zero visibility into distributions.** Combined with §12.3 (excluded from tenant history, analytics and export), a CLIENT_ADMIN can **fund** a company wallet (§6.4) but can never see how it was spent. Their only trace is `audit_logs` and `ledger_entries` at the DB level. That is a deliberate governance choice, not an oversight — flagged here because it is the kind of thing a finance or compliance reviewer asks about later, and re-opening it means re-adding a read endpoint, not just a filter.

**✅ A — a partner admin sees ALL the company's distributions** (locked 2026-08-03, @pushpendra), not only their own — every admin in the company shares one wallet, so every distribution drawn from it is visible, with an **"Initiated by"** column to attribute each one. No `initiated_by_user_id = me` predicate. Scoped by `partner_company_id`, so one company never sees another's.

**✅ B — recipients are PARTNER_SELLERs of the same company only** (locked 2026-08-03, @pushpendra). Active `PARTNER_SELLER` role + `partner_company_id` = the caller's company. This satisfies OQ-7 structurally — an admin can never appear in their own recipient list because they are not a seller — and is why PARTNER_ADMIN does not need `view_company_awards`. Admins distribute; sellers receive.

### 6.3 Partner Seller — Company Award History

```
GET  /api/v1/redemption/awards?rail=&status=&dateFrom=&dateTo=&page=&size=
     → own items only, from company_distribution_items WHERE recipient_user_id = me
GET  /api/v1/redemption/awards/{itemId}
```

### 6.4 Client Admin — fund a company wallet

Replaces the manual DB insert. Wraps the already-built, currently-uncalled `WalletService.creditCompany()`.

```
POST /api/v1/admin/wallets/company/{companyId}/fund
{ "currencyId": "cash", "amount": 10000.00, "reference": "PO-4471", "note": "Q3 budget" }
→ { walletId, availableBalance, reservedBalance, ledgerEntryId }
```

- Permission `action.wallet.fund_company` → CLIENT_ADMIN + PLATFORM_ADMIN. **Never** PARTNER_ADMIN (they would fund their own budget).
- Idempotent on `(reference_type='COMPANY_WALLET_FUNDING', reference_id)` — the existing `uq_ledger_credit_idempotency` index does the work, so a double-click cannot double-fund.
- Auto-creates the wallet on first funding (`creditCompany` already does).
- `@Audited(action="FUNDED", resourceType="REWARD_WALLET")`.

### 6.5 Permissions to seed

| Key | Type | Roles | Purpose |
|---|---|---|---|
| `action.redemption.redeem` | *(existing key)* | **grant to PARTNER_ADMIN** | 🔴 required by OQ-6 — a partner admin cannot redeem anything today (§12.5) |
| `action.redemption.distribute` | ACTION / EXTERNAL | PARTNER_ADMIN | create distributions |
| `action.redemption.view_distribution_history` | ACTION / EXTERNAL | **PARTNER_ADMIN only** | Distribution History — *not* CLIENT_ADMIN |
| `action.redemption.view_company_awards` | ACTION / EXTERNAL | **PARTNER_SELLER only** | Company Award History — PARTNER_ADMIN cannot be a recipient (sub-decision B), so does not need it |
| `action.wallet.fund_company` | ACTION / INTERNAL | CLIENT_ADMIN, PLATFORM_ADMIN | funding API |

⚠️ Each must be seeded into **both** `client_role_permissions` **and** `client_permission_grants` — otherwise the Layer-0 tenant filter strips them and every call 403s. Follow V8's pattern exactly.

---

## 7. Screens and columns

### 7.1 Nav

Two new sidebar items inside the existing collapsible **Redemption** group (`sidebarConfigs.ts`), which is what "under the Redemption Store tab" maps to in the current nav, plus one for the seller:

| Label | Route | Gate | Who sees it |
|---|---|---|---|
| Distribution Store | `/redemption/distribution` | `action.redemption.distribute` | PARTNER_ADMIN |
| Distribution History | `/redemption/distribution/history` | `action.redemption.view_distribution_history` | PARTNER_ADMIN |
| Company Awards | `/redemption/awards` | `action.redemption.view_company_awards` | PARTNER_SELLER |

Because the sidebar hides any item whose permission the caller lacks, a CLIENT_ADMIN sees none of the three, a partner admin sees the first two, and a seller sees only the third — no role-specific nav code needed beyond the gates.

All three additionally inherit the group's `module.redemption_store` gate, so a company- or user-level override hides them with the rest of the module. The partner admin's *personal* store stays at `/redemption-store` — the two are separate pages, since an admin has both a personal and a company wallet.

### 7.2 Distribution Store page

Layout: company balance header → rail tabs → reward picker → **amount** → recipient table → review & send.

- **Balance header** — Available / Reserved, plus a live "remaining after this distribution" figure. No currency selector (cash only).
- **Empty state — unfunded company.** A company that has never been funded has **no company wallet row at all** (it is auto-created by the first credit), so `sourceWalletId` cannot be supplied and the page has nothing to select. `GET /wallets/company/{id}` returns an empty list, and the page must render "This company's wallet hasn't been funded yet" with no rails enabled — not a crash or an empty dropdown. Funding (§6.4) is the prerequisite.
- **Rail tabs** — `Gift Card` · `Bank Transfer` · `Wallet Transfer`, mirroring the existing `RedemptionStorePage` toggle (`?rail=` in the URL for refresh/deep-link survival).
- **Gift-card picker** — reuse `CatalogItemCard` / `GiftCardSkuCombobox`; a `FIXED` SKU pins the amount, a `VARIABLE` SKU shows min–max.
- **Amount** — a **single** field above the recipient table: "Each recipient gets ___" (OQ-5). Read-only and pre-filled for a `FIXED` gift-card SKU.
- **Recipient table** — checkbox · Name · Email · Role · Destination · Eligible?. **No per-row amount column** — one amount governs the whole distribution. Ineligible rows render greyed with the reason, never hidden. The signed-in admin does not appear in the list at all (OQ-7).
- **Review strip (sticky)** — recipients selected · `amount × N = total` · remaining balance · `Send`, disabled with the reason when the total exceeds the available balance.
- **Result** — inline per-recipient outcome list (`PROCESSING` initially), with a link to Distribution History.

### 7.3 Distribution History — columns

**List (one row per distribution):**

| Column | Source |
|---|---|
| Date | `created_at` |
| Type | `rail` badge |
| Reward | catalog item name · `Bank transfer` · `Wallet credit` |
| Recipients | `recipient_count` |
| Requested | `total_amount` + currency — what was submitted |
| Settled | `SUM(amount)` over `COMPLETED` items — what actually left the wallet; differs on a partial failure (§4.4) |
| Status | derived rollup badge (incl. `PARTIALLY_COMPLETED`) |
| Initiated by | `distributions.initiated_by_user_id` → full name |
| Note | truncated `note` |
| Actions | View details |

**Detail drawer (one row per recipient):**

| Column | Source |
|---|---|
| Recipient | `recipient_user_id` → name + email |
| Amount | `items.amount` |
| Status | derived item status |
| Destination | `redemption_requests.payout_destination_label`, or `Reward wallet` |
| Payment Transaction ID | `vendor_reference_id` (label matches the existing rename) |
| Completed at | `completed_at` |
| Failure reason | `failure_reason` |

Header block above the table: rail, reward, initiated by, submitted at, note, totals, rollup status. Reuses the `TransactionDetailSheet` pattern.

### 7.4 Company Award History (seller) — columns

| Column | Source |
|---|---|
| Date | `items.created_at` |
| Awarded by | `distributions.initiated_by_user_id` → full name |
| Company | `partner_company_id` → name (a seller can be moved between companies) |
| Type | `rail` badge |
| Reward | catalog item name · `Bank transfer` · `Wallet credit` |
| Amount | `items.amount` |
| Status | derived item status |
| Destination | payout label, or `Reward wallet` |
| Note | admin's message |
| Actions | View details |

⚠️ **The detail endpoint must be excluded too, not just the list.** `RedemptionHistoryService.getRedemptionDetail` and `RedemptionSubmissionService.getRedemptionById` both resolve via `findByIdAndClientIdAndUserId(id, clientId, userId)` — and `user_id` is the recipient, so a seller can open a distribution's *redemption detail* through the personal endpoint even though the list hides it. Not a security hole (it is their own award), but list-hides / detail-serves is the kind of inconsistency that produces "why does this link 404 from one screen and work from another" bug reports. Filter on `origin` in both, and route the seller to the Company Award detail instead.

**Separation rule (fixes F-5.2):** rows with `origin = COMPANY_DISTRIBUTION` are **excluded** from the seller's personal Transaction History. Personal history = "redemptions I made from my own wallet"; Company Awards = "rewards my company gave me". No double-listing. `WALLET_CREDIT` awards additionally show up as a `CREDIT` in the seller's ledger/balance — correct, because that money genuinely arrived in their wallet.

---

## 8. Money-safety summary

| Risk | Mitigation |
|---|---|
| Overdrawing the company wallet | `findByIdForUpdate` on the company wallet held for the whole submit tx; `SUM(amounts) ≤ available_balance` checked under the lock |
| Double distribution (double-click / retry) | header `client_idempotency_key` unique index → returns the original header |
| Double funding | `uq_ledger_credit_idempotency` on `(wallet, reference_type, reference_id)` |
| Double-pay after a timeout | `dispatch_attempted_at` stamped in its own committed tx *before* the vendor call; the recovery sweep only re-dispatches `dispatch_attempted_at IS NULL` |
| Money stuck reserved (payout rails) | reconciliation cron polls XTRM and settles; ambiguous items stay `PROCESSING` deliberately |
| Money stuck reserved (`WALLET_CREDIT`) | **new** sweep over `status='RESERVED'` items (§5.6); escalates to manual review by age, never auto-releases on an unknown outcome |
| Partial payout when settling per recipient | the up-front `RESERVE` earmarks the full total, so a concurrent distribution cannot consume balance later recipients depend on |
| Recipient credited without company debited | both legs + the item status flip commit in one transaction |
| Re-crediting a recipient after a crash/retry | every leg keyed on the **item id**; `doDebitInTx`/`doCreditInTx`/`doReleaseInTx` are already idempotent on `(wallet, referenceType, referenceId, entryType)` |
| One bad recipient failing everyone | per-item independent dispatch/settle on all three rails; header shows `PARTIALLY_COMPLETED` |
| Cross-company distribution | recipients validated against the caller's `partner_company_id`; source wallet validated against the same |
| Cross-tenant leakage | `client_id` on both new tables + Hibernate `tenantFilter` + explicit `clientId` predicates (the house pattern) |
| Deadlock on `WALLET_CREDIT` | company wallet first, then individual wallets in ascending id order |
| Status drift between tables | no duplicated status column anywhere — item/header status is derived (§4.4) |
| Distribution eating a seller's in-flight cap | in-flight count filtered to `origin = SELF` (F-5.1) |

---

## 9. Notifications

One new type: **`COMPANY_AWARD_RECEIVED`** → in-app + email to the recipient, on item completion.

Per rail: `GIFT_CARD` — XTRM already emails the card, so ours is the heads-up ("Acme Corp sent you a $50 Amazon gift card — check your inbox"). `BANK_TRANSFER` — the only signal the seller gets before the money lands. `WALLET_CREDIT` — the *only* signal at all, since nothing external happens.

**Timing: on item settle, never on submit.** Every rail notifies when its item reaches `COMPLETED`, not when the distribution is accepted. For `WALLET_CREDIT` this matters especially — the money is merely *reserved* at submit and lands at settle (§5.6), so notifying at submit would tell the seller funds had arrived while their balance still showed nothing.

Also needed: an admin-facing summary when a distribution reaches a terminal rollup, especially `PARTIALLY_COMPLETED` (some recipients failed → their money is back in the company wallet).

⚠️ Two wiring notes:
1. The existing Kafka `REDEMPTION_REQUESTED` / `_COMPLETED` / `_FAILED` handlers notify `event.userId()` — which for a distribution row is the **recipient**. So the recipient would receive *"Your redemption was submitted"* copy for something they didn't do. The event handler must branch on `origin` and use award copy (or skip; the award notification covers it).
2. Local email is Resend SMTP and sends fail **silently** (`@Async` + swallowed exception) — verify via `tenxengage-backend/logs/tenxengage.log`, never by "no error in the UI".

---

## 10. Existing code touched

| File | Change | Size |
|---|---|---|
`XtrmVendorService.dispatch()` | replace the `COMPANY` guard with an `origin`-aware allow | small
`RedemptionRequest` entity + V51 | `origin` only (OQ-16) — needs `@Builder.Default` or personal redemption breaks (R1) | small
`RedemptionSubmissionService` | in-flight count filtered to `origin = SELF` | small
`RedemptionHistoryRepository.findPersonalHistory` | exclude `origin = COMPANY_DISTRIBUTION` | small
`RedemptionOrchestrationService.dispatchNotification` | branch copy on `origin` | small
`RedemptionRequestController` + `RedemptionSubmissionService` + DTO | **delete** `submitCompanyRedemption` (OQ-6, §12.5) | −180 lines
`CatalogItemDetailSheet.tsx`, `TransactionHistoryPage.tsx`, `redemptionFeatures.ts` | **delete** the company-wallet redemption source + Company history tab + the `COMPANY_REDEMPTION_ENABLED` flag (OQ-6; all already flag-hidden, so no visible change) | small
`RedemptionHistoryController` | **retire** the unused company-history endpoint; keep the repo query | small
New migration | grant `PARTNER_ADMIN` → `action.redemption.redeem` (🔴 §12.5) | small
New migration | **delete `action.redemption.redeem_company`** from all 5 permission tables, after the guard query (§12.5) | small
`RedemptionProfileController` (15 gates), `RedemptionExportService` (scope → `distribute`), `App.tsx`, `sidebarConfigs.ts`, `MyProfilePage.tsx` | strip `redeem_company` (§12.5) | small
`WalletService` | **no change needed** — `reserve`/`debit`/`release`/`creditInCurrentTx` are already wallet-type agnostic (keyed on `walletId`, no `WalletType` assumption) and already idempotent per `(wallet, referenceType, referenceId, entryType)` | none
**New** scheduled sweep for `WALLET_CREDIT` items stuck `RESERVED` (§5.6) — the existing sweep sees only `redemption_requests` | required, or funds sit reserved forever | medium
`RedemptionReconciliationService` | 🔴 **widen both calls to include `COMPANY`** — currently hard-passes `WalletType.INDIVIDUAL`, so distributions are invisible to missed-webhook recovery *and* to the past-cap alert (F-8) | small
`RedemptionHistoryService.getRedemptionDetail`, `RedemptionSubmissionService.getRedemptionById` | filter `origin` so the detail endpoint matches the list (§7.4) | small
`findTenantHistory` (+ the export's row source) | `AND origin = 'SELF'` — §12.3 excludes distributions from tenant history and export | small
`RedemptionExportService`, `ExportScope`, FE `ExportJobScope` | **delete** COMPANY scope / `isCompanyExport` / `canCompany` — nothing left to select (§12.3) | small
`WalletController` / new admin controller | funding endpoint | small
5 analytics MVs (new migration, DROP + CREATE + reindex) | `AND rr.origin = 'SELF'` — OQ-3 exclusion (§12.3) | medium
`RedemptionRequestRepository` (2 count methods) | `AndOrigin` variants — OQ-3 exclusion | small
`sidebarConfigs.ts` | 3 nav items | small
**New** | `CompanyDistributionService`, controller, 2 entities + repos, 3 FE pages, contracts | the bulk

**Nothing to change for balance expiration** — a `WALLET_CREDIT` writes an ordinary `CREDIT` entry, which the existing wallet-level policy already governs (§12.4).

Regression watch: the analytics dashboard numbers must **not** move when a distribution is sent (that is the OQ-3 acceptance test); the crash-recovery sweep picks distribution items up for free (F-3) but the **reconciliation cron does not until widened** (F-8); and the client-admin tenant history *will* gain distribution rows unless §12.3's adjacent call is overridden.

---

## 11. Out of scope for v1 (Phase 2 candidates)

- `BatchTransfer` for large bank-rail distributions (plumbing kept reachable — F-1).
- **Any analytics on distributions** (OQ-3 excludes them for now). When it is wanted, the clean shape is an `origin` dimension on the MVs so self-service and distribution can be reported side by side rather than silently merged.
- **Exporting distribution data** — §12.3 excludes distributions from the redemption export and retires COMPANY scope, so v1 has no download at all. The follow-up is a dedicated CSV export on the distribution endpoints (reusing `RedemptionExportJob`'s async job pattern), not re-opening the redemption export.
- Approval workflow for distributions above a threshold (OQ-2).
- Scheduled / recurring distributions.
- CSV upload of recipient+amount pairs.
- Cancelling an in-flight distribution (failed items already auto-refund).
- Returns on distributed gift cards (`redemption_returns` covers NON_CASH; these are CASH).
- Paying an XTRM **company** beneficiary/SPN — the thing the old guard comment was about. Genuinely different feature.
- Company-level budget caps per admin / per period.

---

## 12. Decisions

### Resolved (2026-08-03) — see §0 for the locked wording

OQ-1 ✅ · OQ-2 ✅ · OQ-3 ✅ (§12.3) · OQ-4 ✅ (§12.4) · OQ-5 ✅ · OQ-7 ✅ · OQ-8 ✅ · OQ-9 ✅ · OQ-10 ✅ · OQ-11 ✅ (§12.1–12.2)

### Still open

**None.** All 15 questions plus nav placement are resolved.

**NAV ✅ (2026-08-03):** new **sidebar items** inside the existing collapsible **Redemption** group, each on its own route — per §7.1. Not sub-tabs inside `/redemption-store`.

### 12.1 Branch topology — clean, fast-forwardable

`features/redemption-store-feedback` is **ahead of** `roadmaps/redemption-store` and **behind by 0** in all four repos, so the merge is a fast-forward everywhere and the stack is exactly as expected:

| Repo | commits ahead | behind |
|---|---|---|
| `tenxengage-backend` | 7 | 0 |
| `tenxengage-frontend` | 5 | 0 |
| `tenxengage-contracts` | 2 | 0 |
| `tenxengage-blueprint` | 3 | 0 |

Those commits are this design's prerequisites — the bank-transfer card foundation (V45), the two-rail dispatch router, the bank-transfer submit endpoint, and the gift-card SKU/value-type model. Branching off `roadmaps/redemption-store` *before* the merge would start from a tree without any of them, which is why the merge comes first.

**Decision:** merge `features/redemption-store-feedback` → `roadmaps/redemption-store` (FF) in all four repos, then cut `features/company-distribution-store` from the roadmap branch. MR target: `roadmaps/redemption-store`.

### 12.2 Pre-merge cleanup — ✅ DONE (2026-08-03)

**Committed as 12 commits across three repos** (5 backend, 5 frontend, 2 contracts), each staged by
path so the CRLF churn never entered the index. Every commit was verified to build **independently**
(backend `compileJava`+`compileTestJava`, frontend `tsc --noEmit`) so `git bisect` stays meaningful.

| Repo | commits | notes |
|---|---|---|
| `tenxengage-backend` | 5 | V47–V50 now tracked. `RedemptionSubmissionService.java` carried 3 themes and was split hunk-by-hunk (10+7+42 = its full +59) |
| `tenxengage-frontend` | 5 | includes 6 previously-untracked source files (`GiftCardEnrollmentNotice`, `utils/redemptionAmount`, 4 test files) |
| `tenxengage-contracts` | 2 | split by file, not theme — hunk-splitting a spec would leave a schema referenced but undefined, and bisect has no value in a docs repo |

**Test state after the commits — no new failures introduced.** All four failures below were confirmed
to fail identically at the base commit *before* any of this work:

| Suite | result | pre-existing failures |
|---|---|---|
| backend `test` | 1574 / 1576 | `TenxengageApplicationTests.contextLoads`, `IncentiveServiceTest.generateForecastStreaming_…` |
| frontend `vitest` | 854 / 856 | `sidebarConfigs` (expects 7 redemption sub-items), `ApprovalQueueTable` (renders item data) |

Both frontend failures sit in files this feature must touch anyway — `sidebarConfigs` gains three nav
items (§7.1), so that test needs updating as part of the work regardless. Fix them in the feature
branch, not before the merge.

`application-local.yml` was deliberately **left uncommitted** rather than discarded: it holds
demo-frequency crons that would revert committed `3e407e43`, and leaving it dirty preserves the local
demo setup while keeping it out of history. Prefer the env overrides
(`REDEMPTION_BATCH_CRON` etc.) for future demos.

Unrelated pre-existing stash in the backend (`WIP on features/redemption-analytics-basic`) — left untouched.

**Still to do:** push all three repos, fast-forward merge into `roadmaps/redemption-store`, then cut
`features/company-distribution-store`.

<details>
<summary>Original audit findings (for the record)</summary>

The merge was **not** safe to run. Four findings, in priority order:

**1. Uncommitted work on the feedback branch — real, and substantial.**

| Repo | files flagged `M` | **actual content changes** |
|---|---|---|
| `tenxengage-backend` | 1 050 | **32 files, +749 / −70** |
| `tenxengage-frontend` | 28 | **28 files, +1 750 / −298** |
| `tenxengage-contracts` | 123 | **3 files, +154 / −8** |
| `tenxengage-blueprint` | 0 | none |

Backend touches `RedemptionSubmissionService` (+59/−12), `XtrmVendorService` (+10/−3), `RedemptionRequest` (+6), the catalog services, and ~10 test classes. Frontend is the whole `redemption-catalog` component set. This is a coherent slice of in-flight work — **it must be committed (or deliberately discarded) before the merge**, or it will be carried across branches as a dirty tree and the merge will not represent what was tested.

**2. Four migrations are UNTRACKED — never committed.**

```
?? src/main/resources/db/migration/V47__add_payout_beneficiary_id_to_redemption_requests.sql
?? src/main/resources/db/migration/V48__add_max_transaction_amount_override_to_item_configs.sql
?? src/main/resources/db/migration/V49__add_provider_image_url_to_catalog_items.sql
?? src/main/resources/db/migration/V50__scope_catalog_provider_uniqueness_to_live_items.sql
```

These have already been applied to the local dev DB (the columns exist and the code reads them), but they exist in **no commit on any branch**. Consequences: (a) any other environment is missing four schema changes; (b) this design's **V51/V52 numbering is only valid once V47–V50 are committed** — otherwise the next developer legitimately claims V47 and the numbering collides.

**3. Unpushed commits everywhere.**

| Repo | unpushed |
|---|---|
| `tenxengage-backend` | 3 |
| `tenxengage-frontend` | 4 |
| `tenxengage-contracts` | 2 |
| `tenxengage-blueprint` | 0 |

So the answer to "is everything committed and pushed" is **no on both counts**, in three of four repos.

**4. The `M`-vs-real gap is CRLF noise, and it is a live hazard.**

Every repo has `core.autocrlf=true` and **no `.gitattributes`**. That is why the backend shows 1 050 modified files but only 32 have real changes, and why `git diff` emits ~1 000 `LF will be replaced by CRLF` warnings. A careless `git add -A` would commit ~1 000 pure line-ending changes, producing an unreviewable diff and near-guaranteed conflicts on the merge.

→ Stage the 32 + 28 + 3 real files **explicitly by path**, never `git add -A` / `git add .`. Separately, adding a committed `.gitattributes` (`* text=auto eol=lf`) is the durable fix — but that is its own normalisation commit and should not be mixed into this feature's work.

**Ordered pre-flight checklist**

1. ✅ Review the real diffs per repo; decide commit vs discard.
2. ✅ Commit V47–V50 with the code that reads them (backend), staging **by path**.
3. ✅ Commit frontend + contracts real changes, by path.
4. ⬜ Push all three repos; confirm `@{u}..HEAD` is empty.
5. ⬜ Re-verify `behind = 0` (confirmed 0 in all three at commit time), then fast-forward merge into `roadmaps/redemption-store`.
6. ⬜ Push the roadmap branch; confirm V47–V50 are present on it.
7. ⬜ Bump the `contracts` submodule pointer in backend + frontend once contracts is pushed.
8. ⬜ Cut `features/company-distribution-store` from it. Only then does V51/V52 numbering hold.

</details>

### 12.3 OQ-3 + OQ-12 — excluding distributions from every existing redemption surface

Analytics must show nothing for distributions. `WALLET_CREDIT` is already structurally excluded (it never creates a `redemption_requests` row). The gift-card and bank rails do, so they need an explicit `origin = 'SELF'` filter in two places:

- **Five materialized views** in `V28__create_advanced_analytics_materialized_views.sql` — `mv_item_redemption_breakdown`, `mv_segment_redemption_breakdown`, `mv_time_to_first_redemption`, `mv_redemption_rate_trend`, `mv_failure_mode_breakdown`. Each already carries `WHERE rr.deleted = false`, so each gains `AND rr.origin = 'SELF'`. Postgres has no `CREATE OR REPLACE MATERIALIZED VIEW`, so this is a `DROP` + `CREATE` migration that must also **recreate every index** on those views.
- **Basic analytics counts** — `RedemptionRequestRepository.countByClientIdAndCurrencyIdAndSubmittedAtBetween` and `countByClientIdAndCurrencyIdAndStatusInAndSubmittedAtBetween`, both derived query methods → add `AndOrigin` variants.

**✅ Resolved (2026-08-03, @pushpendra): distributions are excluded EVERYWHERE in the redemption surfaces.**

| Surface | Distributions |
|---|---|
| Analytics dashboards + breakage | **excluded** (OQ-3) |
| Client Admin "All Redemptions" tenant history | **excluded** — `findTenantHistory` gains `origin = 'SELF'` |
| Redemption CSV export | **excluded** |
| Partner Seller personal Transaction History (+ detail) | **excluded** — they live in Company Awards (§7.4) |
| Distribution History / Company Award History | the *only* places distributions appear |

#### Knock-on: `ExportScope.COMPANY` becomes dead and should be removed

`isCompanyExport` selects company-wallet redemptions for the caller's partner company. After this feature that set is empty in every practical sense:

- `submitCompanyRedemption` is deleted (OQ-6), so no new `origin = SELF` company-wallet rows can ever be created;
- distributions are `origin = COMPANY_DISTRIBUTION` and now excluded.

What remains is historical residue (the FAILED rows dev accumulated while the dispatch guard rejected them). So remove `ExportScope.COMPANY`, the `isCompanyExport` branch, the `canCompany` check, and the `'COMPANY'` member of the FE `ExportJobScope` union.

**Safe to remove:** `redemption_export_jobs.scope` is a plain `VARCHAR(20)` mapped to a `String` field — **not** `@Enumerated` — so historical job rows carrying `'COMPANY'` still read back fine. And the FE never sends that scope today: its Company tab is behind `COMPANY_REDEMPTION_ENABLED = false` and is itself being deleted (§12.5).

**This simplifies the permission cleanup.** §12.5 identified `canCompany` as the *last genuine consumer* of `action.redemption.redeem_company` and planned to re-gate it on `action.redemption.distribute`. With COMPANY scope gone, `canCompany` disappears outright — so **no re-gating is needed at all**, and the key drops to zero consumers by simple deletion.

⚠️ **Accepted gap:** with distributions out of both tenant history and the export, there is **no way to export distribution data in v1**. A partner admin can view Distribution History on screen but not download it. Deliberate for v1; a dedicated export on the distribution endpoints is the obvious Phase-2 follow-up (§11).

### 12.4 OQ-4 — expiry needs zero implementation (correcting an earlier claim)

I previously said this decision "decides whether the `CREDIT` entry carries an expiry reference". **That was wrong** — expiration in this codebase is not lot-based, so there is nothing to attach. Reading `V32__create_balance_expiration_tables.sql` and `BalanceExpiryBatchService`:

- A policy is per `(client_id, currency_id)` with mode `INACTIVITY` or `FIXED_DATE` — it expires a **wallet's whole balance**, never an individual credit lot.
- "Activity" is derived from ledger entry types: `ACTIVITY_ENTRY_TYPES = {CREDIT, DEBIT, RESERVE, RETURN_CREDIT}`.

A `WALLET_CREDIT` distribution writes an ordinary `CREDIT` entry on the recipient's individual cash wallet. So it is **already** inside the configured cash-currency policy, and it is **already** counted as activity. Your answer is satisfied with no code, no schema and no migration.

One behavioural consequence worth knowing: because `CREDIT` counts as activity, distributing to a dormant seller **resets their inactivity clock**, postponing expiry of their entire existing balance — not just the new money. That is almost certainly the desired reading of "the wallet is not dormant, money just arrived", but it is a real side effect of choosing the wallet-credit rail, so it should be called out in the test plan rather than discovered later.

### 12.5 OQ-6 fallout — retiring "company redemption" is bigger than deleting one endpoint

Auditing every reference to the company-redemption concept turned up a **live gap that exists today**, plus three places where the codebase contradicts the §12 invariant.

#### 🔴 A Partner Admin cannot currently redeem *anything*

`V17__seed_redemption_flow_permissions.sql` grants:

| Role | Granted |
|---|---|
| `PARTNER_SELLER` | `action.redemption.redeem` |
| `PARTNER_ADMIN` | `action.redemption.redeem_company` **only** |

So for a partner admin today:

- `POST /api/v1/redemption/requests` (personal) requires `action.redemption.redeem` → **403**.
- `POST /api/v1/redemption/requests/company` requires `redeem_company` → passes, reserves the funds, then `XtrmVendorService.dispatch()` throws `COMPANY_PAYOUT_NOT_SUPPORTED` after commit → `failInstantDispatch` releases the reservation and marks it **FAILED**.

They can browse the store (the nav gate accepts `redeem_company`) and submit, and it always fails. **Both doors are shut.** This is a pre-existing bug on `features/redemption-store-feedback`, not something this feature introduces — but OQ-6 ("the company admin will redeem as seller for self benefit") cannot be true until it is fixed.

→ **Required: grant `PARTNER_ADMIN` the `action.redemption.redeem` permission** (new migration, both `client_role_permissions` **and** `client_permission_grants`). Without it, the model OQ-6 describes does not work.

#### The three contradictions to remove

**First, a correction.** An earlier draft of this section claimed the company-wallet redemption option was *live UI silently failing for partner admins*. **That was wrong.** `config/redemptionFeatures.ts` defines `COMPANY_REDEMPTION_ENABLED = false`, which already hides both company surfaces:

```ts
/** Company-wallet redemption. Today only personal (individual-wallet) redemptions are
 *  supported, so the Partner-Admin company surfaces are hidden:
 *   - "Redeem (Company)" button in CatalogItemDetailSheet.tsx
 *   - "Company" tab in the transaction-history page (TransactionHistoryPage.tsx)  */
export const COMPANY_REDEMPTION_ENABLED: boolean = false;
```

So **nothing user-visible changes** when we delete this — a partner admin cannot see or reach the company-redemption path today. This is pure code cleanup: removing a half-built capability that the product has now decided will never exist. (The separate `action.redemption.redeem` 403 above **is** real and unaffected by this flag.)

#### Deletion inventory

| # | Artifact | Note |
|---|---|---|
| 1 | `RedemptionRequestController` L79–88 — `POST /api/v1/redemption/requests/company` | endpoint + its `@Audited` + `@RequiresPermission` |
| 2 | `RedemptionSubmissionService.submitCompanyRedemption()` L367–549 | ~180 lines, a near-duplicate of the personal core |
| 3 | `SubmitCompanyRedemptionRequest` | DTO record |
| 4 | `RedemptionRequestControllerTest` / `RedemptionSubmissionServiceTest` | the company-redemption test methods |
| 5 | `contracts/endpoints/redemption-flow.yaml` | the company-submit path |
| 6 | `CatalogItemDetailSheet.tsx` — `canRedeemCompany` (L56–58), `useCompanyWallet` call (L114–117), the "Redeem (Company)" block (L310–345) | already flag-hidden |
| 7 | `TransactionHistoryPage.tsx` — the "Company" tab | already flag-hidden |
| 8 | `config/redemptionFeatures.ts` — `COMPANY_REDEMPTION_ENABLED` | both its surfaces are gone, so the flag goes too |
| 9 | `RedemptionHistoryController` L46 + `RedemptionHistoryService.getCompanyHistory()` | the company-history endpoint. Zero frontend callers once #7 is gone; post-change it would return distribution payout legs ungrouped — a second, differently-shaped view of the same money |

#### Explicitly NOT deleted

| Kept | Why |
|---|---|
| `GET /api/v1/wallets/company/{companyId}`, `useCompanyWallet`, `walletService.getCompanyWallet` | **the Distribution Store needs them** for its balance header. Only their use as a *redemption source* goes. |
| `findCompanyHistoryByPartnerCompany()` repository query | reused by the Distribution History detail view |
| the narrowed `XtrmVendorService` guard | defence in depth — historical `COMPANY + SELF` rows must never become payable |

Also **removed** (§12.3): `ExportScope.COMPANY` + `isCompanyExport` + `canCompany`, and the `'COMPANY'` member of the FE `ExportJobScope` union.

#### `action.redemption.redeem_company` — delete it

*(This reverses an earlier draft of this section, which said to keep the key as load-bearing. Once `PARTNER_ADMIN` holds `action.redemption.redeem`, that is no longer true — the derivation below is what the audit actually shows.)*

Every consumer of the key, and what happens to it:

| # | Consumer | Fate |
|---|---|---|
| 1 | `RedemptionRequestController` L79 — company submit | **deleted** by OQ-6 |
| 2 | `RedemptionHistoryController` L46 — company history | **retired** by OQ-6 |
| 3 | `RedemptionProfileController` — **15 endpoints**, gated `{redeem, redeem_company}` | **redundant** — an OR-gate that `redeem` alone satisfies once PARTNER_ADMIN holds it |
| 4 | `MyProfilePage.tsx` L47 — `canAny(redeem, redeem_company)` | **redundant**, same reason |
| 5 | `App.tsx` L210 + `sidebarConfigs.ts` L102 — store route + nav | **redundant**, same reason |
| 6 | `RedemptionExportService` L97 — `canCompany` → COMPANY export scope | **removed entirely** — §12.3 excludes distributions from the export, which leaves COMPANY scope with nothing to select |

On #6 — **superseded by §12.3 (2026-08-03).** An earlier pass planned to re-gate this check on `action.redemption.distribute`, reasoning that the code's real question is *"is this a partner admin acting for the company?"* rather than *"may this user redeem the company wallet?"*. That re-gating is now unnecessary: since distributions are excluded from the export, COMPANY scope selects nothing and the whole branch — `ExportScope.COMPANY`, `isCompanyExport`, `canCompany` — is deleted rather than re-pointed.

With all six consumers resolved, the key has **zero remaining users** → delete it, with no replacement gate anywhere.

**Deletion is cheap and safe — verified.** `permission_key` is a plain `VARCHAR(100)` with **no foreign key** in any of the five tables that carry it (`permissions`, `client_role_permissions`, `client_permission_grants`, `company_permission_overrides`, `user_permission_overrides`). So removal is five `DELETE`s with no ordering constraints and no FK failures. Mirror the house rule in reverse: delete from **both** `client_role_permissions` **and** `client_permission_grants`, plus both override tables, or a stale deny-row could outlive the key.

⚠️ **One pre-deletion check against real data.** The reasoning above assumes nobody holds `redeem_company` without also holding `redeem`. That is true for the seeded `PARTNER_ADMIN` role once the new grant lands — but a live tenant may have a **custom client role** granted `redeem_company`. Before the delete migration runs:

```sql
SELECT cr.id, cr.base_role_name, cr.name
FROM   client_role_permissions crp
JOIN   client_roles cr ON cr.id = crp.client_role_id
WHERE  crp.permission_key = 'action.redemption.redeem_company'
  AND  NOT EXISTS (
         SELECT 1 FROM client_role_permissions x
         WHERE x.client_role_id = crp.client_role_id
           AND x.permission_key = 'action.redemption.redeem');
```

Any row returned is a role that would lose payout-profile access. The migration must grant those roles `action.redemption.redeem` in the same transaction as the delete.

#### Regression risk to watch

Partner admins are the role most changed by this feature: they gain `redeem` (new personal-redemption ability), lose the company-wallet store source, and gain three new screens. The test plan needs a **partner-admin-specific pass** covering: personal redemption now succeeding, the company wallet no longer appearing as a store source, the payout profile still reachable, and exports unchanged.

---

## 13. Regression safety for the existing individual flow

The seller / partner-admin **personal** redemption path is live and must not move. Every planned change was traced against it.

### Structurally incapable of affecting it

| Change | Why it cannot touch the personal path |
|---|---|
| `company_distributions`, `company_distribution_items` | new tables; nothing existing reads them |
| `WalletService` | **no change at all** — `reserve`/`debit`/`release`/`creditInCurrentTx` are reused exactly as-is |
| New `reference_type` values (`COMPANY_DISTRIBUTION*`, `COMPANY_WALLET_FUNDING`) | `uq_ledger_credit_idempotency` is keyed on `(wallet, reference_type, reference_id)`; new type values cannot collide with existing rows |
| Narrowing the `XtrmVendorService` guard | the guard only evaluates for `wallet_type = COMPANY`; an INDIVIDUAL wallet never reaches it, before or after |
| New endpoints, new permissions, new tables' DTOs | purely additive |
| Distribution reusing `BankTransferCardService.ensureBankTransferCard` | idempotent get-or-create that personal bank transfer already calls |

### Shared code — the real risks, each with its test

**R1 🔴 `origin` must be `@Builder.Default`, or personal redemption breaks outright.**
There are exactly **two** `RedemptionRequest.builder()` sites today (`RedemptionSubmissionService:297` personal, `:485` company — the latter being deleted), so after this work the personal path has **one** insert site. If the entity field lacks `@Builder.Default private RedemptionOrigin origin = SELF`, Hibernate includes the column in the INSERT with an explicit **NULL** — and a `NOT NULL` column then rejects **every personal redemption**. The DB-side `DEFAULT 'SELF'` does *not* rescue this; a default only applies when the column is omitted from the INSERT, which Hibernate does not do. The entity already uses this pattern for `deleted`, so follow it.
*Test:* submit a personal redemption without referencing `origin` → the row must persist with `origin = 'SELF'`.

**R2 🔴 Permission migration ordering + cache eviction.**
`PermissionService` resolves via `@Cacheable(value = "effectivePermissions", key = "#userId")`. Two consequences:
- Grant `PARTNER_ADMIN → action.redemption.redeem` in the **same migration as, or strictly before**, deleting `action.redemption.redeem_company`. Reversed, partner admins lose the store *and* all 15 payout-profile endpoints *and* company export scope.
- **Evict `effectivePermissions` after the migration.** A partner admin with a live session otherwise keeps stale permissions — either 403'd out of their payout profile, or still passing on a key that no longer exists.
Sellers are unaffected either way: they hold `action.redemption.redeem` already and never held `redeem_company`.
*Test:* seller and partner-admin can both reach the store, the payout profile and their own redemption after the migration + eviction.

**R3 🟠 The `origin = 'SELF'` filters depend on a complete backfill.**
V51 must be `NOT NULL DEFAULT 'SELF'` so every existing row backfills. If any row were left `NULL`, *every* new filter (personal history, in-flight count, the five analytics MVs) would silently drop it and a seller's history would lose rows.
*Test:* post-migration assertion `SELECT count(*) FROM redemption_requests WHERE origin IS NULL` = 0.

**R4 🟠 `CatalogItemDetailSheet.tsx` is the seller's redeem drawer.**
Removing the company-wallet source edits live seller code in the hot path. It is flag-hidden (`COMPANY_REDEMPTION_ENABLED = false`), so behaviour should not change — but the edit is where a seller's redeem could regress. Its test file just gained +133 lines of coverage, which is the safety net.
*Test:* the full personal redeem journey through the drawer stays green.

**R5 🟠 In-flight cap filter is a no-op that must stay one.**
Adding `AND origin = 'SELF'` to `countByClientIdAndUserIdAndStatusIn` must not change the number for a seller who has no distributions. The signature change forces a call-site edit; getting it wrong either disables the cap (over-permissive) or double-counts.
*Test:* a seller at the in-flight limit is still rejected; receiving a company award does not consume any of their allowance.

**R6 🟡 Analytics MV `DROP` + `CREATE` must restore every index.**
The `origin = 'SELF'` filter is a **no-op against existing data** (all rows backfill to SELF), so the acceptance criterion is that every dashboard number is **identical** before and after. The real risk is the recreate omitting an index that existed on one of the five MVs.
*Test:* enumerate indexes on all five MVs before dropping, diff after; snapshot dashboard figures before/after and assert equality.

**R7 🟡 Widening reconciliation adds rows; it must not change how INDIVIDUAL rows are handled.**
Also watch that company rows do not swamp the `recon_past_cap` warning and mask an individual payout that needs manual review.
*Test:* an INDIVIDUAL payout stuck in `PROCESSING` still reconciles exactly as today.

**R8 🟡 The notification handler's `SELF` branch must remain byte-identical.**
`dispatchNotification` gains an `origin` branch; the existing submitted/completed/failed copy for personal redemptions must not change.

### Regression suite to run before merge

The existing green baseline is **BE 1574/1576** and **FE 854/856** (4 known pre-existing failures). Any *new* failure is this feature's fault. Beyond the unit suites, the personal path needs an explicit end-to-end pass: gift-card redeem, bank-transfer redeem, in-flight cap, approval queue, transaction history + detail, export, and the payout profile — for **both** a seller and a partner admin, since the partner admin's capabilities change most.

---

## 14. Why this shape (one paragraph)

The company wallet is **internal budget**, not an XTRM account. Every recipient is an ordinary individual payee with an ordinary PAT, and the XTRM source is the same platform issuer wallet personal redemptions already use. So a company distribution is structurally *N personal payouts drawn from a different internal wallet* — which is why setting `user_id = recipient`, `wallet_id = company wallet`, and adding an `origin` discriminator lets the existing payout machinery (dispatch, two-rail routing, `settle()`, the webhook, crash recovery, audit) carry the two payout rails rather than a second money path being written beside it. That reuse is the central bet of this design: **one `settle()` is worth more than tidy column semantics.**

It is not free, and the doc is explicit about the bill. The dispatch guard narrows rather than disappears (§5.3); reconciliation must be widened because it is hard-filtered to `INDIVIDUAL` (F-8); roughly eight query sites must carry an `origin = 'SELF'` filter forever; the recipient is stored twice; and the table name is now narrower than its contents (OQ-15). Those are accepted costs with named mitigations, not oversights.

The two new tables are not merely bookkeeping either. `company_distributions` records intent and grouping — who sent what to whom, and why — which `redemption_requests` genuinely cannot express. `company_distribution_items` does that *and* owns the full lifecycle of the `WALLET_CREDIT` rail, which has no vendor, no webhook, and therefore no `redemption_requests` row at all. That rail stays out deliberately: money which never leaves the platform must not be counted as redeemed, or it is counted again when the seller finally redeems it.

All three rails reserve at submit and settle per recipient, so the company's funds are earmarked the instant a distribution is accepted and each recipient's leg completes or releases its own share independently — one bad recipient can neither block nor quietly consume anyone else's money.
