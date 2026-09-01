---
slug: redemption-store
source: BRD_PDF.pdf · v1.0 Draft · April 2026
module: Rewards & Redemption
generated: 2026-05-05
---

# BRD Digest: Redemption Store Integration

> **Purpose:** BRD-specific cross-cutting context every spec derived from this BRD inherits. Project-level patterns (multi-tenancy, RBAC, audit, package structure) live in `CONSOLIDATED-PROJECT-CONTEXT.md` and are NOT repeated here.
>
> **Boundary:** Business-truth only. BRD-stated technical artifacts (entity names, event names, API ops, RBAC matrix) live in `digest-annex.md`. Spec authors reconcile those against the codebase, not this digest.

## Vision

Close the partner reward loop by giving every partner seller and partner company a seamless, currency-aware redemption experience that converts earned rewards into real, tangible value — without friction, without compliance burden, and without exposing internal infrastructure.

## Scope boundaries

- **Owns**: Dual reward wallet (individual + company), immutable ledger engine, redemption catalog (global management + tenant configuration + regional configuration), XTRM cash payout integration, Xoxoday non-cash integration, vendor webhook processing, transaction history and export, non-cash returns flow, persistent balance visibility platform-wide, payout SLA communication, redemption rate analytics.
- **Does NOT own**: Native fulfillment engine or merchandise catalog (delegated to XTRM and Xoxoday), currency types beyond the four platform-defined types, cross-currency redemptions, partial returns (v1), real-time currency conversion, native mobile redemption experience (v1), wallet-to-wallet transfers between individual sellers, reward balance expiration (v1 — Phase 2 decision).

## Personas

- **`CLIENT_ADMIN`** — Configures which redemption options are available for their tenant. Sets minimum thresholds and processing modes. Views and exports full tenant redemption history. Approves or rejects return requests.
- **TenXEngage Platform Admin** *(new role — cross-tenant)* — Manages the global redemption catalog (XTRM and Xoxoday items). Enables or disables catalog items globally. Manages vendor API credentials and integration health.
- **`ACTIVITY_APPROVER`** (BRD: "Approver") — Reviews and approves or rejects pending redemption requests (APPROVAL_REQUIRED mode) and return requests submitted by partner users.
- **`PARTNER_SELLER`** (BRD: "Partner Seller / Account Executive") — Browses the redemption catalog filtered to their available currency balances. Redeems individual wallet balance for cash or non-cash rewards. Views transaction history and submits return requests.
- **`PARTNER_ADMIN`** — Redeems from the company wallet on behalf of the partner organization. Monitors company wallet balance and transaction history. Exports company redemption data.
- **`PARTNER_SELLER`** (BRD: "Partner SE / Solutions Architect") — Redeems non-cash rewards (events, swag, experiences) earned through technical enablement and community contribution. Same role as Partner Seller; distinct usage pattern only.

## BRD-specific cross-cutting concepts

**Dual wallet model**: Every partner seller has an *individual* reward wallet; every partner company has a *pooled company* wallet. Both are tenant-scoped. Company wallet balances are independent of — and do not aggregate — individual seller balances. Redemption can originate from either wallet type.

**Per-currency balance tracking**: Each wallet maintains a *separate* running balance per currency type (cash, points, credits, tickets). These are tracked independently. A user may hold balances across multiple types simultaneously. Redemption eligibility is checked per currency type.

**Available vs. reserved balance**: Each currency balance has two components: `availableBalance` (spendable) and `reservedBalance` (locked against in-flight redemptions). Spendable amount is always `availableBalance`. Reservation happens *at submission*; debit happens *at vendor confirmation*.

**Ledger-first architecture**: TenXEngage owns the authoritative reward ledger. Every balance movement is written as an immutable ledger entry *before* wallet totals are updated. Vendors are fulfillment engines only — they do not hold or manage balances. Wallet totals are maintained as running aggregates for query performance and must always be derivable from the ledger sum.

**Vendor-transparent UX**: Users interact with a single unified Redemption Store. XTRM and Xoxoday routing is completely transparent — no vendor branding, no separate portals. Category (CASH → XTRM, NON_CASH → Xoxoday) determines routing automatically.

**Three processing modes**: Every catalog item has a default processing mode configurable at global and tenant levels:
- *Instant* — sent to vendor immediately on submission (after approval gate if applicable)
- *Batch* — queued and processed on client-configured schedule (daily or weekly, set via `batchCadence` field)
- *Approval Required* — held pending Client Admin or Approver review before vendor handoff

**Two-step balance safety**: Balance reserved on submission regardless of processing mode (preventing double-spend). Debit occurs only when vendor confirms completion. Release occurs on failure or cancellation.

## Integration intent

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | Incentive / Training / Activity / Journey / Deal modules | Reward earning events credit partner wallets |
| Sends to | PAS scoring pipeline (Phase 2) | Redemption activity contributes to Commercial Intent score (18% weight) |
| Sends to | XTRM | Cash payout requests with partner identity |
| Receives from | XTRM | Payout status updates (completed, failed, cancelled) |
| Sends to | Xoxoday | Non-cash order placement and return notifications |
| Receives from | Xoxoday | Order fulfillment and return confirmation status |
| Sends to | Notification framework | Redemption and return lifecycle events under NotificationCategory.REWARDS |
| Sends to | Audit framework | All redemption and return operations via @Audited |

## Phasing intent

- **Phase 1 (Production v1 — no timeline stated)** — Full earn-to-redeem loop: dual wallet + ledger engine + catalog + XTRM cash integration + Xoxoday non-cash integration + vendor webhooks + transaction history + returns flow + persistent balance widget + basic redemption analytics dashboard. Exit gate: §18 acceptance criteria list met.
- **Phase 2 (3–6 weeks post v1)** — PAS Commercial Intent signal integration · Batch processing scheduler UI · Advanced redemption analytics (by item/tier/region/cohort) · Partial return support · Reward balance expiration with configurable policy and breakage reporting · SLA breach monitoring · Wallet statement view.
- **Phase 3** — Real-time analytics with cohort comparisons · AI-driven redemption nudges · Native mobile redemption experience · Cross-currency redemption options.

## Open ADRs / decisions

| ADR | Decision needed | Owner | Blocks |
|---|---|---|---|
| ADR-01 | Should company wallet redemption require a minimum number of approvers (quorum), similar to incentive approval workflows? Defaulting to single approver. | Product | F-04 approval queue model |
| ADR-02 | Batch processing cadence — confirmed as configurable per client via `batchCadence` field. Phase 1 = config field + backend job. Phase 2 = dedicated scheduler UI. | Resolved | — |
| ADR-03 | Are there limits on simultaneous in-flight (reserved) redemption requests per user? No limit specified; resolve in /create-spec for F-03. | Product/Engineering | F-03 redemption flow |
| ADR-04 | Non-cash redemptions: confirmed amount-based (not quantity-based). | Resolved | — |
| ADR-05 | Failed redemptions surfaced to Client Admins: transaction history filtered by FAILED status covers Phase 1. Dedicated failed-transaction view is Phase 2 enhancement. | Resolved | — |

---

## Mission-critical decision tables

### Table: Return Status Lifecycle (source: "Returns")

| Status | Description |
|---|---|
| PENDING_APPROVAL | Return request submitted by user; awaiting Client Admin review |
| APPROVED | Client Admin approved; return request sent to Xoxoday; awaiting vendor confirmation |
| RETURN_CONFIRMED | Xoxoday confirmed the return; wallet credit issued |
| RETURN_REJECTED | Client Admin rejected the request, or Xoxoday declined after approval |
| CANCELLED | User cancelled their own request before Client Admin review |

### Table: Processing Mode SLAs (source: "Payout Speed and SLAs")

| Processing Mode | Expected Payout Timeline | User Communication |
|---|---|---|
| Instant | Cash (XTRM): within 1–2 business days depending on payment method. Non-cash (Xoxoday): digital delivery within minutes; physical items per Xoxoday fulfillment SLA. | Confirmation shown immediately on submission with estimated delivery timeframe |
| Batch | Processed on client-configured schedule (daily or weekly). Payout follows vendor SLA after batch runs. | User shown next batch date at submission. Notification sent when batch is processed. |
| Approval Required | Clock starts after Client Admin approves. Vendor SLA applies from approval timestamp. | User notified of pending approval status. Second notification when approved and processing begins. |

### Table: Redemption Vendor Routing (source: "Redemption Flow")

| Redemption Category | Vendor | Examples |
|---|---|---|
| CASH | XTRM | ACH, PayPal, Venmo, international bank transfer |
| NON_CASH | Xoxoday | Gift cards, merchandise, prepaid Visa/MC, travel, events |

---

## Concrete SLAs / numeric guarantees

| Capability / context | Numeric guarantee | Unit | Source anchor |
|---|---|---|---|
| Instant cash payout (XTRM) | 1–2 | business days | "Payout Speed and SLAs" |
| Instant non-cash digital delivery (Xoxoday) | within minutes | — | "Payout Speed and SLAs" |
| Physical item delivery | per Xoxoday fulfillment SLA | — | "Payout Speed and SLAs" |
| Batch processing cadence | daily or weekly (client-configured) | — | "Redemption Flow", "Payout Speed and SLAs" |
| PAS Commercial Intent weight | 18% | component weight | "Event Architecture", "Final Recommendation" |

---

## Reliability & UX guarantees (business intent)

- Redemption Store UI follows WCAG-aligned patterns consistent with the broader tenXengage platform.
- Wallet balance is always visible — displayed in the platform header or nav on every authenticated page so partners never have to hunt for their balance.
- Estimated payout timelines are set upfront — expected delivery time is shown at the catalog item level before the user submits, not after.
- Batch mode shows the next scheduled processing date at submission time.
- The Redemption Store feels like a seamless part of tenXengage — no vendor branding or names visible to end users.
- Balance state is always clear — available vs. reserved is surfaced at a glance, with shortfalls shown inline on unavailable items.
- Admin configuration is self-serve and low-friction — Client Admins can manage their catalog and thresholds without platform team involvement.
- Return flows are straightforward: find the transaction, request the return, track the status.
- Regional relevance by default — Xoxoday catalog surfaces locally relevant options first.

---

## Non-goals (v1)

- Building a native redemption fulfillment engine or merchandise catalog — delegated to XTRM and Xoxoday
- Supporting currency types beyond the four platform-defined types: cash, points, credits, tickets
- Cross-currency redemptions (e.g., combining points and tickets in a single transaction)
- Partial returns — only full-amount returns are supported in v1
- Real-time currency conversion between reward types
- Native mobile redemption experience — initial release targets the web platform
- Wallet-to-wallet transfers between individual partner sellers
- Reward balance expiration — point/currency expiry policies are a Phase 2 decision; balances do not expire in v1

---

## Undefined terms-of-art (candidate ADRs)

- **"Non-returnable items"** — The BRD defines this via an `isReturnable` flag on each catalog item but does not state the business criteria for what makes an item non-returnable (e.g., instantly delivered digital gift cards are cited as an example). /create-spec for F-02 and F-06 must establish the classification rule.
- **"Minimum balance threshold"** — Client-configured per tenant with no stated platform default or recommended range. /create-spec for F-02 must establish whether there is a platform-level floor below which clients cannot set this.
- **"Return window"** — Client-configured number of days post-fulfillment; BRD user story cites 30 days as an example. No default is stated. /create-spec for F-02 must clarify whether the platform ships with a default (e.g., 30 days) or requires explicit Client Admin configuration.

---

## Current-state foundation

The existing platform already includes the following, which this feature preserves and extends:

- Reward transactions tracked via the existing `TransactionStatus` and `TransactionType` enums (REWARD, REDEMPTION, ADJUSTMENT, BONUS, CLAWBACK, TRANSFER)
- Four defined currency types: cash, points, credits, tickets — no new types are introduced
- Incentive programs (SALES, TRAINING, ACTIVITY, JOURNEY) that already generate reward earning events
- Compliance module with anti-bribery controls, government deal restrictions, value cap monitoring, and `ComplianceAlertType` framework
- Audit logging via `@Audited` annotation with `AuditAction` and `AuditResourceType` enums
- Notification framework with `NotificationCategory.REWARDS` already defined
- Permission system (5-layer resolution) and feature flag infrastructure (`FeatureFlagService`, tier-based enablement)
- Multi-tenant isolation via `client_id`, Hibernate `@Filter`, `TenantAware` interface, and `TenantEntityListener`

---

## Risks register

*(Source: "Risks" — verbatim)*

| Risk | Mitigation |
|---|---|
| Vendor API instability (XTRM or Xoxoday downtime) | Idempotent retry logic with exponential backoff. Redemption requests remain in PENDING/PROCESSING state during outage. Webhook dead-letter queue for unprocessable events. |
| Ledger inconsistency from concurrent requests | Optimistic locking on wallet balance fields. All ledger writes within a single database transaction. Wallet totals derived from ledger entries if inconsistency is detected. |
| Webhook replay or spoofing attacks | HMAC-SHA256 signature verification on all inbound webhooks. Idempotency key checks prevent duplicate processing. |
| KYC/AML failure for international cash payouts | XTRM handles KYC natively. TenXEngage surfaces clear failure messaging when XTRM rejects a payout due to compliance. Payout reserved balance is released on failure. |
| Return abuse or fraud (users returning used items) | Xoxoday confirmation required before wallet credit. Client Admin approval gate. `isReturnable` flag per catalog item to exclude non-returnable items. |
| Catalog drift between Xoxoday and TenXEngage | Periodic catalog sync job keeps `RedemptionCatalogItem` table current. Items removed by Xoxoday are auto-deactivated. |
| Scope creep from analytics and mobile requests | Strict phase gating. Phase 2 and 3 items explicitly deferred in release strategy. |
| Low redemption rate at launch | Ensure catalog is curated and regionally relevant from day one. Surface balance prominently on every page. Set minimum thresholds conservatively. |
| Regional catalog misconfiguration | Xoxoday geographic availability data drives hard limits. Client Admins cannot enable items outside Xoxoday coverage. UI validation enforces this at configuration time. |
| User confusion over payout timelines | SLA information shown proactively at catalog item selection — before submission. Batch mode shows next run date explicitly. |

---

## Entity-shape decisions

The following entities are modeled as configurable data objects (tenant-editable fields under Platform Settings → Managed Data):

_(none — all entities in this roadmap are hardcoded JPA entities)_

All entities default to hardcoded JPA entities. Field-level configuration is deepened per-feature in `/create-spec` via `docs/patterns/managed-data.md`.

| Entity | Shape | First introduced by |
|---|---|---|
| `RedemptionRequest` | Hardcoded JPA entity | `/create-spec redemption-store F-03` (features/redemption-flow) |
| `RedemptionWebhookEvent` | Hardcoded JPA entity | `/create-spec redemption-store F-03` (features/redemption-flow) |
| `RedemptionExportJob` | Hardcoded JPA entity | `/create-spec redemption-store F-05` (features/redemption-history) |
| `RedemptionReturn` | Hardcoded JPA entity | `/create-spec redemption-store F-06` (features/redemption-returns) |
| `BalanceExpirationPolicy` | Hardcoded JPA entity | `/create-spec redemption-store F-09` (features/reward-balance-expiration) |
| `BalanceExpiryNotice` | Hardcoded JPA entity | `/create-spec redemption-store F-09` (features/reward-balance-expiration) |
