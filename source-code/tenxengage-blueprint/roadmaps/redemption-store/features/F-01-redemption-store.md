# F-01: Wallet & Ledger Foundation

> **Slug**: `wallet-ledger-foundation` · **Roadmap**: `redemption-store` · **Phase**: 1 · **Recommended order**: 1st
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Reward Wallet", "Ledger Engine", "Persistent Balance Visibility", "Current-State Foundation"

## Business outcome

Every partner seller and partner company gains a real-time, currency-aware reward wallet backed by an immutable ledger — giving them a trustworthy balance they can see on every platform page and spend on rewards. This closes the foundational data layer required for the entire redemption experience.

## Primary persona of record

**`PARTNER_SELLER`** — The balance holder whose earned rewards are tracked and displayed. Primary beneficiary of the persistent balance visibility and the earning integrations.

## Secondary personas

- **`PARTNER_ADMIN`** — Holds and views the company wallet balance; same ledger infrastructure applies.
- **`CLIENT_ADMIN`** — Reads wallet balances for reporting and analytics in downstream features.

## User journey (sketch)

A partner seller completes an incentive activity and sees their available balance update in the platform nav widget immediately. They click the widget and are taken to the Redemption Store, where their per-currency balances are displayed. When they submit a redemption (covered in F-03), their available balance decreases and reserved balance increases — transparently tracked by the ledger engine.

## Functional requirements (business intent)

1. **FR-01.1** — The platform maintains a separate spendable balance per currency type (cash, points, credits, tickets) for each individual partner seller, scoped to their client tenant.
2. **FR-01.2** — The platform maintains a pooled company wallet per currency type for each partner company, scoped to their client tenant; company wallet balances are independent of and do not aggregate individual seller balances.
3. **FR-01.3** — Each wallet balance exposes two components: an available (spendable) amount and a reserved amount locked against in-flight redemptions; only the available amount may be spent.
4. **FR-01.4** — Every balance movement is written as an immutable ledger entry before wallet totals are updated; wallet totals are always derivable from the sum of ledger entries and are maintained as running aggregates for query performance.
5. **FR-01.5** — The ledger supports five movement types: credit (reward earned), reserve (redemption submitted), debit (fulfillment confirmed), release (fulfillment failed or cancelled), return credit (return confirmed by vendor).
6. **FR-01.6** — When an incentive program, training completion, activity challenge, journey milestone, or deal closure generates a reward earning event, the platform credits the partner's wallet for the appropriate currency type and writes a credit ledger entry.
7. **FR-01.7** — The partner's available balance per currency type is persistently visible in the platform navigation header on every authenticated page; clicking the balance widget navigates directly to the Redemption Store.
8. **FR-01.8** — The nav balance widget displays available balance only; reserved balance is not included to avoid confusion.
9. **FR-01.9** — For partners holding multiple currency types, the nav widget displays the most contextually relevant currency (e.g., points on a training page, cash on a deals page) with a tooltip to expand all balances.
10. **FR-01.10** — The platform rejects a redemption initiation when the available balance for the requested currency type falls below the client-configured minimum threshold; the minimum threshold is enforced at the time of submission.

## Business rules

- Individual and company wallets are always distinct; they never aggregate.
- Each currency type balance is tracked independently; a multi-currency holder has separate available and reserved amounts per type.
- Ledger entries are immutable — once written they cannot be updated or deleted.
- Reserved balance can only be released by a RELEASE or DEBIT entry; it cannot be reduced by a new RESERVE entry.
- Wallet totals are derived from the ledger on reconciliation; the running aggregate is maintained for performance but the ledger is authoritative.

## Constraints / validations

- A wallet may not go negative in available balance; redemption submissions that would cause a negative available balance are rejected.
- `reservedBalance` may not exceed `availableBalance + reservedBalance` (total wallet value).
- Earning events from incentive programs must reference a valid currency type from the four platform-defined types.

## Relevant non-functional requirements

- Wallet balance reads must be responsive under normal tenant scale.
- Vendor webhook processing must handle concurrent events without race conditions on wallet totals (optimistic locking on balance fields).
- Balance updates in the nav widget must reflect in real-time when rewards are earned or redemptions are processed.

## Edge cases / open questions

- **ADR-03**: No limit on in-flight reservations specified — if a partner reserves multiple redemptions simultaneously across a shared company wallet, total reserved could exhaust the company balance for other Partner Admins. Resolve in /create-spec for F-03.
- What is the platform's behavior when an earning event references a currency type that the partner's wallet doesn't yet have a record for? Should the wallet record be auto-created on first credit?
- How are earning events from existing incentive modules routed to wallet credits — via Kafka event consumption or direct service call?

## Dependencies

- **Features**: — (foundation; no upstream feature dependency)
- **ADRs**: —
- **External counterparties**: Existing incentive / training / activity / journey modules must emit earning events consumable by this feature.

## Riskiest unknown

Whether the existing incentive program earning event model (TransactionType.REWARD) maps cleanly onto the new per-currency wallet credit model — or whether a new earning event schema is needed. If the existing TransactionType enum is reused, /create-spec must confirm no migration breakage occurs for historical reward records.

## Candidate domain concepts (business nouns)

- **Individual reward wallet**: A per-currency balance record owned by a single partner seller within a tenant.
- **Company reward wallet**: A pooled per-currency balance record owned by a partner company, redeemable by its Partner Admin.
- **Available balance**: The portion of a wallet's balance that can be spent on a redemption.
- **Reserved balance**: The portion of a wallet's balance locked against in-flight redemptions; not spendable until released or debited.
- **Ledger entry**: An immutable record of a single balance movement — the authoritative source of truth for all balance changes.
- **Earning event**: A platform signal from an incentive program, training, activity, or deal that triggers a wallet credit.
- **Balance widget**: The persistent UI element in the platform nav showing available balance on every page.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | Incentive / Training / Activity / Journey / Deal modules | Earning events trigger wallet credits for the appropriate currency type |
| Sends to | F-02 (Redemption Catalog) | Available balance per currency type drives catalog item eligibility and shortfall display |
| Sends to | F-03 (Redemption Flow) | Balance reservation and release/debit happen in response to redemption lifecycle events |
| Sends to | F-07 (Basic Analytics) | Wallet balance aggregates feed the unredeemed balance report |

---

## Suggested story seeds
| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Set up individual reward wallets | Partners have a per-currency wallet that reflects earned balances in real-time | data | — |
| S-02 | Set up company reward wallets | Partner Admins can access a company-level wallet separate from individual balances | data | S-01 |
| S-03 | Record balance movements as ledger entries | Every earn and redemption event produces an immutable audit-ready ledger record | workflow | S-01 |
| S-04 | Display persistent balance in nav | Partners see their available balance on every page without visiting the redemption store | UI | S-01 |
| S-05 | Credit wallets from program earning events | Platform earning events automatically credit the partner's wallet for the correct currency type | integration | S-01 |

---

## `/create-spec` invocation

```
/create-spec redemption-store F-01
```
