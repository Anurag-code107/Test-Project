# F-03: Redemption Flow

> **Slug**: `redemption-flow` · **Roadmap**: `redemption-store` · **Phase**: 1 · **Recommended order**: 3rd
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Redemption Flow", "Vendor Integration", "Payout Speed and SLAs", "Event Architecture"

## Business outcome

Partner sellers and partner admins can convert their earned rewards into real value by selecting a catalog item, submitting a redemption, and receiving confirmation — with their balance safely reserved on submission and finalized only when the vendor confirms delivery. The entire flow is vendor-transparent: partners experience a single unified store regardless of whether the reward is cash or non-cash.

## Primary persona of record

**`PARTNER_SELLER`** — The individual reward holder who browses the catalog, selects an item, and submits a redemption from their personal wallet.

## Secondary personas

- **`PARTNER_ADMIN`** — Submits redemptions from the company wallet on behalf of the partner organization; same flow, different wallet source.
- **`ACTIVITY_APPROVER`** / **`CLIENT_ADMIN`** — Review gate for Approval Required mode (their actions covered in F-04).

## User journey (sketch)

Partner Seller navigates to the Redemption Store, browses the currency-filtered catalog, selects a PayPal payout item, sees the 1–2 business day delivery estimate, confirms the amount, and submits. Their available balance decreases and reserved balance increases immediately. They receive a submission confirmation with the estimated delivery timeframe. When XTRM confirms disbursement, their reserved balance is permanently debited and they receive a completion notification.

## Functional requirements (business intent)

1. **FR-03.1** — Partner Seller can submit a redemption from their personal wallet by selecting a catalog item and specifying an amount; the submission immediately reserves the amount from available balance regardless of processing mode.
2. **FR-03.2** — Partner Admin can submit a redemption from the company wallet by selecting a catalog item; the amount is reserved from the company wallet on submission.
3. **FR-03.3** — The platform routes cash redemptions to XTRM and non-cash redemptions to Xoxoday automatically; the partner sees a single unified experience with no vendor branding or switching.
4. **FR-03.4** — For Instant processing mode, the redemption request is sent to the vendor immediately after submission (and after any required approval gate); the ledger debit is recorded when the vendor webhook confirms completion.
5. **FR-03.5** — For Batch processing mode, the redemption is queued and processed on the client-configured batch schedule (daily or weekly per `batchCadence`); the partner is shown the next scheduled processing date at submission time.
6. **FR-03.6** — For Approval Required mode, the redemption is held in pending approval state and is not sent to the vendor until a Client Admin or Approver approves it (covered in F-04); the balance remains reserved during the approval window.
7. **FR-03.7** — When a vendor webhook confirms fulfillment completion, the platform records a permanent ledger debit, transitions the redemption to completed status, and notifies the partner.
8. **FR-03.8** — When a vendor webhook signals failure or cancellation, the platform releases the reserved amount back to available balance, transitions the redemption to failed/cancelled status, and notifies the partner.
9. **FR-03.9** — All inbound vendor webhooks are authenticated via HMAC-SHA256 using vendor-specific signing secrets; unauthenticated or malformed webhook requests are rejected; webhook processing is idempotent.
10. **FR-03.10** — Transient vendor API failures trigger automatic retry with exponential backoff; webhook events that cannot be processed are routed to a dead-letter queue.
11. **FR-03.11** — The redemption confirmation screen displays the estimated payout timeline for the selected processing mode; for batch mode, the next scheduled run date is shown.

## Business rules

- Balance must be reserved at submission — before any vendor handoff and regardless of processing mode.
- Vendor handoff only occurs after any required approval gate has been cleared.
- No payment credentials, bank account numbers, or card details are stored in the platform at any point.
- XTRM handles KYC/AML, OFAC screening, and tax reporting natively; the platform passes user identity fields (name, email, country) at cash redemption time only.
- A redemption in PENDING_APPROVAL, PROCESSING, or RESERVED state cannot be submitted again on the same reserved balance.
- Failed or cancelled redemptions must fully restore the reserved amount to available balance via a RELEASE ledger entry.

## Constraints / validations

- Redemption amount must be ≥ the catalog item's configured minimum transaction amount.
- Available balance for the requested currency type must be ≥ the client-configured minimum wallet balance threshold at time of submission.
- Non-cash redemptions are amount-based — no quantity model.
- Cash redemptions (XTRM) are non-returnable once disbursed.

## Edge cases / open questions

- **ADR-03 (resolved)**: In-flight redemption cap is 10 per partner (Vijay, 2026-05-21). Configurable per client via `maxInFlightRedemptions` on tenant redemption settings (default: 10).
- How does the batch processor handle items where the vendor is unavailable when the batch runs — does it retry the entire batch or mark individual items as failed?
- What is the platform's behavior when a webhook arrives for a redemption that is already in COMPLETED or FAILED state (late delivery or duplicate)?

## Dependencies

- **Features**: F-01 (wallet balance reservation/debit/release), F-02 (catalog item routing and processing mode)
- **ADRs**: ADR-03 (in-flight limits)
- **External counterparties**: XTRM API availability; Xoxoday API availability

## Riskiest unknown

Idempotency of webhook processing under concurrent delivery. If XTRM or Xoxoday delivers the same completion webhook twice (retry on their side), the platform must not double-debit the ledger. The idempotency key design for webhook processing is the most failure-prone aspect of this feature.

## Candidate domain concepts (business nouns)

- **Redemption request**: A partner's submission to convert a wallet balance amount into a specific catalog reward.
- **Vendor routing**: The automatic selection of XTRM (cash) or Xoxoday (non-cash) based on catalog item category.
- **Balance reservation**: The move of a redemption amount from available to reserved at submission time.
- **Fulfillment confirmation**: The vendor webhook signal that triggers permanent ledger debit and status finalization.
- **Processing mode**: Instant, Batch (with client-configured cadence), or Approval Required.
- **Dead-letter queue**: The holding area for webhook events that cannot be processed, for manual investigation.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Sends to | F-01 (Wallet & Ledger) | Balance reserved on submission; released or debited on webhook outcome |
| Sends to | F-04 (Approval Queue) | APPROVAL_REQUIRED redemptions appear in the approval queue awaiting review |
| Sends to | F-05 (Transaction History) | Redemption transactions recorded for history and export |
| Sends to | PAS pipeline (Phase 2) | Redemption completion signals commercial intent for partner scoring |
| Receives from | F-02 (Catalog) | Item category, processing mode, and minimum amounts govern submission and routing |
| Receives from | F-04 (Approval Queue) | Approval decision triggers vendor handoff |
| Receives from | XTRM / Xoxoday | Webhook status updates finalize ledger and transaction state |

---

## Suggested story seeds

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Submit personal wallet redemption | Partner Seller selects a catalog item and submits a redemption with immediate balance reservation | workflow | F-02.S-04 |
| S-02 | Submit company wallet redemption | Partner Admin redeems from the company wallet with the same flow as personal wallet | workflow | S-01 |
| S-03 | Route cash redemptions to XTRM | Cash redemption requests are submitted to XTRM with partner identity; ledger finalized on webhook | integration | S-01 |
| S-04 | Route non-cash redemptions to Xoxoday | Non-cash orders are placed with Xoxoday; ledger finalized on fulfillment webhook | integration | S-01 |
| S-05 | Queue and process batch redemptions | Redemptions in batch mode are queued and processed on the client-configured cadence | workflow | S-01 |
| S-06 | Process vendor webhooks idempotently | XTRM and Xoxoday status updates authenticate and apply to ledger and transaction state without double-processing | integration | S-03, S-04 |
| S-07 | Notify partners of redemption lifecycle | Partners receive timely notifications at submission, completion, and failure | workflow | S-01 |

## Non-functional requirements

- **Peak load**: No application-level limit — system scales horizontally via auto-scaling at infrastructure level (Pushpendra, 2026-05-21)
- **Data retention**: Indefinite — no deletion policy on redemption transaction records (Pushpendra, 2026-05-21)

---

## `/create-spec` invocation

```
/create-spec redemption-store F-03
```
