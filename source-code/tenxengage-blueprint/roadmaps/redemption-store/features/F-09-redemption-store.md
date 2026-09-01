# F-09: Balance Expiration

> **Slug**: `reward-balance-expiration` · **Roadmap**: `redemption-store` · **Phase**: 2 · **Recommended order**: 9th
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Reward Balance Expiration", "Release Strategy — Phase 2"

## Business outcome

Client Admins can configure time-bound reward balances to manage program liability and incentivize timely redemption — while partners receive clear advance warning before any balance expires, preserving trust and fairness.

## Primary persona of record

**`CLIENT_ADMIN`** — Configures the expiration policy per currency type for their tenant; uses breakage reports to track program liability reduction.

## Secondary personas

- **`PARTNER_SELLER`** / **`PARTNER_ADMIN`** — Receive advance notifications before expiry and see expiry events in their transaction history.

## User journey (sketch)

Client Admin navigates to the rewards configuration section, enables expiration for Points with a 12-month inactivity policy. Partners with points that haven't been earned or redeemed in 12 months receive an advance notification (e.g., 30 days before expiry) and another at expiry. Their balance is reduced by an expiry debit ledger entry, and the Client Admin sees the breakage reflected in their unredeemed liability report.

## Functional requirements (business intent)

1. **FR-09.1** — Client Admin can configure a balance expiration policy per currency type for their tenant, specifying either an inactivity period (e.g., 12 months without earning or redemption activity) or a fixed calendar date.
2. **FR-09.2** — Cash balances do not expire by default; expiration is opt-in per currency type and per client.
3. **FR-09.3** — The expiration policy must be explicitly enabled and configured before it takes effect; there is no platform-default expiration.
4. **FR-09.4** — When a balance is due to expire, the platform sends an advance notification to the partner at a configurable lead time (e.g., 30 days before expiry) with the amount and expiry date.
5. **FR-09.5** — At the point of expiry, the system writes an expiry debit ledger entry, reduces the available balance, and notifies the partner.
6. **FR-09.6** — Expired balances are separately trackable so Client Admins can view and export breakage (expired balance amounts by currency type and period).
7. **FR-09.7** — The expiration policy, lead time, and any changes to the policy are communicated to partners in advance — the policy cannot be changed retroactively to expire balances immediately without prior notice.

## Business rules

- Expiration is a Phase 2 decision — balances do not expire in v1; this is an explicit product commitment documented in the BRD.
- Cash does not expire by default; the expiration model applies primarily to points, credits, and tickets.
- The expiration policy (inactivity-based or date-based) must be explicitly configured and must not auto-activate.
- Expiry debit ledger entries must be distinguishable from redemption debits in reports and audit logs.

## Constraints / validations

- The inactivity period, if used, must be defined (BRD example: 12 months); the platform must define what constitutes "activity" (earning, redemption, or both).
- Expiration changes that would retroactively expire already-accumulating balances require a grace period notice to partners — the exact notice period is a Phase 2 product decision.

## Edge cases / open questions

- What constitutes "inactivity" for the inactivity-based policy — earning events only, redemption events only, or either? The BRD says "12 months of inactivity" without specifying.
- Should the expiry notification lead time be configurable per client, or is it a platform default?
- How does expiration interact with in-flight reserved balances — if a partner has a redemption in-flight when their balance expires, does the reserved amount expire?

## Dependencies

- **Features**: F-01 (wallet balance + ledger infrastructure required), F-07 (unredeemed balance liability reporting extended)
- **ADRs**: Inactivity definition, notice period policy — resolve in /create-spec for F-09.

## Riskiest unknown

The definition of "inactivity" — whether only earning events count, only redemption events count, or both. This determines how the expiration engine queries activity history and could produce unexpected behavior (e.g., a partner who earns frequently but never redeems has their balance expire despite high engagement if only redemptions count as activity).

## Candidate domain concepts (business nouns)

- **Expiration policy**: The Client Admin-configured rule defining when a balance expires (by inactivity period or calendar date).
- **Expiry debit**: The ledger entry that reduces a partner's available balance when their reward expires.
- **Breakage**: The total value of balances that expired without redemption — a financial liability metric for the program operator.
- **Inactivity period**: The configurable window of no qualifying activity after which a balance is eligible to expire.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | F-01 (Wallet & Ledger) | Balance totals and last-activity timestamps determine expiration eligibility |
| Sends to | F-01 (Wallet & Ledger) | Expiry debit entries reduce available balance and are recorded in the ledger |
| Sends to | F-07 / F-08 (Analytics) | Breakage data (expired balances) extends the unredeemed liability report |

---

## Suggested story seeds

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Configure balance expiration policy | Client Admin enables and configures per-currency expiration rules for their tenant | admin | F-01.S-01 |
| S-02 | Notify partners of approaching expiry | Partners receive advance warning before their balance expires so they can act | workflow | S-01 |
| S-03 | Execute balance expiration | System applies expiry debits and records immutable ledger entries at the configured expiry point | rules | S-01 |
| S-04 | Report on breakage | Client Admin views and exports expired balance totals by currency type and period | reporting | S-03 |

---

## `/create-spec` invocation

```
/create-spec redemption-store F-09
```
