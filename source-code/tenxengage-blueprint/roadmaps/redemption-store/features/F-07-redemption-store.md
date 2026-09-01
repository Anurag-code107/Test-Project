# F-07: Basic Redemption Analytics Dashboard

> **Slug**: `redemption-analytics-basic` · **Roadmap**: `redemption-store` · **Phase**: 1 · **Recommended order**: 7th
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Redemption Rate and Analytics Dashboard", "Acceptance Criteria Summary for v1 Launch"

## Business outcome

Client Admins gain a health dashboard for their rewards program — showing what percentage of earned balances have been redeemed and what remains outstanding — so they can identify whether the program is driving real engagement and where liability is accumulating.

## Primary persona of record

**`CLIENT_ADMIN`** — The program manager who monitors redemption health, identifies friction, and uses the data to tune catalog and threshold settings.

## Secondary personas

None — this is a Client Admin-only analytics view.

## User journey (sketch)

Client Admin navigates to the Redemption section of their admin dashboard, sees redemption rate cards by currency type (e.g., Points: 34% redeemed of total earned), a total unredeemed balance figure across all wallets by currency type (exportable as CSV), and a failed/cancelled redemption rate card. They filter by the past 30 days to see recent program health and export the unredeemed balance report for their finance team.

## Functional requirements (business intent)

1. **FR-07.1** — Client Admin can view redemption rate per currency type: total amount redeemed divided by total amount earned, expressed as a percentage, for all users and companies within their tenant.
2. **FR-07.2** — Dashboard displays the unredeemed balance (outstanding liability) per currency type — the total available + reserved balance across all wallets in the tenant — with an option to export as CSV.
3. **FR-07.3** — Dashboard displays the failed and cancelled redemption rate per currency type to identify fulfillment friction.
4. **FR-07.4** — Dashboard data is filterable by date range (earning and redemption events within the selected window).
5. **FR-07.5** — Dashboard reflects data in near real-time as redemptions are completed and earning events are processed.
6. **FR-07.6** — The unredeemed balance export includes a breakdown per user and per company, not just the tenant aggregate.

## Business rules

- Redemption rate is calculated as: (total redeemed amount in currency type) / (total earned amount in currency type) × 100.
- Unredeemed balance is the sum of available + reserved balances across all wallets in the tenant for each currency type.
- Analytics are always scoped to the Client Admin's tenant — no cross-tenant data leakage.

## Constraints / validations

- Tier/region breakdown is NOT in Phase 1 (deferred to F-08, Phase 2). ⚠️ Explicit deferral from BRD v1 acceptance criteria — "Analytics filter by partner tier, region, and date range" deferred to Phase 2.
- Breakdown by catalog item is NOT in Phase 1 (deferred to F-08).

## Edge cases / open questions

- How is "total earned" calculated — lifetime or filtered to the selected date range? Using lifetime for rate denominator gives a more accurate picture; using the selected range gives a period-specific view. Clarify in /create-spec.
- Should the analytics dashboard be a separate page or a section within the existing Client Admin reporting area?

## Dependencies

- **Features**: F-03 (redemption transaction data), F-05 (transaction records used for calculations), F-01 (wallet balances for unredeemed liability)

## Riskiest unknown

Whether the analytics queries can run acceptably against the main transactional tables, or whether a separate aggregation/materialized view is needed. If the tenant has thousands of partner users and millions of transactions, a real-time redemption rate query could be expensive.

## Candidate domain concepts (business nouns)

- **Redemption rate**: The percentage of total earned reward balance that has been redeemed; the primary health metric for a rewards program.
- **Unredeemed balance (liability)**: The total outstanding wallet balance across a tenant — what the program owes partners in potential redemptions.
- **Fulfillment friction**: The proportion of redemptions that fail or are cancelled — a signal that the vendor integration or catalog has issues.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | F-01 (Wallet & Ledger) | Current wallet balances feed the unredeemed liability calculation |
| Receives from | F-03 (Redemption Flow) | Redemption transaction data feeds rate and friction calculations |

---

## Suggested story seeds

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | View redemption rate by currency | Client Admin sees the percentage of earned balances that have been redeemed per currency type | reporting | F-03.S-06 |
| S-02 | View unredeemed balance liability | Client Admin sees and exports the total outstanding unredeemed balance across all tenant wallets by currency type | reporting | F-01.S-01 |
| S-03 | View failed and cancelled redemption rates | Client Admin identifies fulfillment friction by monitoring failure and cancellation rates | reporting | S-01 |
| S-04 | Filter analytics by date range | Client Admin analyzes redemption health across specific time windows | UI | S-01 |

---

## `/create-spec` invocation

```
/create-spec redemption-store F-07
```
