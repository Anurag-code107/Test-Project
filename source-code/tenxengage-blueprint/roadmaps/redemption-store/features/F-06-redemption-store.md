# F-06: Non-Cash Returns

> **Slug**: `redemption-returns` · **Roadmap**: `redemption-store` · **Phase**: 1 · **Recommended order**: 6th
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Returns", "Functional Scope"

## Business outcome

Partners can request a refund on non-cash rewards that didn't meet expectations — with a clear, tracked workflow and the assurance that their balance is restored only when the vendor confirms. Client Admins retain control with an approval gate, and the audit trail is unambiguous.

## Primary persona of record

**`PARTNER_SELLER`** — The recipient of the non-cash reward who initiates the return from their transaction history when an item needs to be refunded.

## Secondary personas

- **`PARTNER_ADMIN`** — Can submit returns on behalf of the company wallet for non-cash company redemptions.
- **`ACTIVITY_APPROVER`** / **`CLIENT_ADMIN`** — Review and approve or reject return requests from the approval queue (F-04).

## User journey (sketch)

Partner Seller navigates to their transaction history (F-05), finds the completed merchandise redemption within the return window, clicks "Request Return", optionally adds a reason (wrong item received), and submits. The return appears in PENDING_APPROVAL status. The Client Admin reviews it in the approval queue, approves it, and the platform notifies Xoxoday. When Xoxoday confirms the return, the partner's point balance is restored and they receive a notification.

## Functional requirements (business intent)

1. **FR-06.1** — A partner may submit a return request for a completed non-cash (Xoxoday) redemption that is within the client-configured return window and for a catalog item marked as returnable; cash (XTRM) redemptions are not eligible for returns once disbursed.
2. **FR-06.2** — The return option is displayed in transaction history only for eligible completed non-cash redemptions; ineligible transactions (outside return window, non-returnable item, cash redemption, or already-returned) do not show the return option.
3. **FR-06.3** — Partner submits a return request with an optional reason; the return is created in PENDING_APPROVAL status and the Client Admin and Approver are notified.
4. **FR-06.4** — On Client Admin or Approver approval of a return, the platform notifies Xoxoday via the vendor API; the partner's wallet is not credited until Xoxoday confirms the return.
5. **FR-06.5** — When Xoxoday confirms the return, a return credit ledger entry is written and the full original amount is restored to the partner's available balance in the originating currency type; the partner is notified.
6. **FR-06.6** — When Xoxoday rejects the return (e.g., item already used), the return is marked RETURN_REJECTED, no wallet credit is issued, and the partner is notified with the rejection reason.
7. **FR-06.7** — When Client Admin or Approver rejects the return request, Xoxoday is not contacted, the return is marked RETURN_REJECTED, and the partner is notified.
8. **FR-06.8** — The partner can cancel their own return request while it is still in PENDING_APPROVAL state; cancellation transitions the return to CANCELLED and requires no admin action.
9. **FR-06.9** — Return credit ledger entries are distinguishable from standard reward earning credits in transaction history, analytics, and audit logs.
10. **FR-06.10** — Partial returns are not supported in v1; only full-amount returns are accepted.
11. **FR-06.11** — The return status lifecycle follows the defined states: PENDING_APPROVAL → APPROVED → RETURN_CONFIRMED or RETURN_REJECTED; or PENDING_APPROVAL → CANCELLED.

## Business rules

- Wallet credit is issued only after Xoxoday confirmation — Client Admin approval alone is not sufficient.
- A redemption may have at most one return request; a second return cannot be submitted for the same transaction.
- The return window is measured from the vendor's fulfillment confirmation timestamp (completedAt on the redemption transaction), not from submission time.
- Cash redemptions (XTRM) are never returnable under any circumstances.
- Items with `isReturnable = false` are never eligible for return regardless of Client Admin configuration.

## Constraints / validations

- Return amount is always the full redemption amount — no partial return in v1.
- Return requests can only be submitted for redemptions in COMPLETED status.
- The return window duration (in days) is set per-item in the tenant catalog configuration (F-02).

## Edge cases / open questions

- **Return approval timeout (open)**: BRD §8.5 does not define a TTL for the APPROVED state (awaiting Xoxoday confirmation). If Xoxoday never responds, the return is stuck. /create-spec must define a timeout and fallback (e.g., escalate to failed after N days, notify admin).
- What is the behavior when Xoxoday's return API is unavailable when the platform attempts to notify them after approval? Should the platform retry, or hold in APPROVED state until Xoxoday is reachable?
- If a partner received a partially-used gift card, what is the expected behavior of the vendor return — is Xoxoday expected to handle partial value returns even though the platform only supports full-amount?

## Dependencies

- **Features**: F-03 (redemption must be COMPLETED before return can be initiated), F-04 (return request enters approval queue), F-05 (return initiated from transaction history and linked in history)
- **ADRs**: Return approval timeout mechanism must be defined in /create-spec.
- **External counterparties**: Xoxoday return API availability and response timing.

## Riskiest unknown

The Xoxoday return confirmation latency and reliability. The APPROVED → RETURN_CONFIRMED transition depends entirely on Xoxoday's API or webhook response. If Xoxoday's return process involves manual review on their side, the partner's balance could be in limbo for days. The spec must define the handling strategy for slow or absent vendor responses.

## Candidate domain concepts (business nouns)

- **Return request**: A partner's request to refund a completed non-cash redemption within the configured return window.
- **Return window**: The client-configured number of days after fulfillment within which returns are accepted.
- **Return eligibility**: The combination of criteria (COMPLETED status, within return window, returnable item, non-cash only) that makes a redemption eligible for return.
- **Return credit**: The ledger entry that restores the partner's available balance after Xoxoday confirms the return.
- **Return status lifecycle**: PENDING_APPROVAL → APPROVED → RETURN_CONFIRMED / RETURN_REJECTED, or CANCELLED.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | F-05 (Transaction History) | Return request initiated from a specific transaction history entry |
| Sends to | F-04 (Approval Queue) | Return request appears in the approval queue for admin review |
| Sends to | F-01 (Wallet & Ledger) | Return credit restores available balance after vendor confirmation |
| Sends to | F-05 (Transaction History) | Return transaction recorded and linked to originating redemption |
| Sends to | Xoxoday | Return notification sent after admin approval; awaiting vendor confirmation |

---

## Suggested story seeds

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Submit a return request | Partner initiates a return from their transaction history for an eligible completed non-cash redemption | workflow | F-05.S-01 |
| S-02 | Review and decide on return requests | Client Admin / Approver reviews pending returns and approves or rejects from the approval queue | admin | S-01, F-04.S-01 |
| S-03 | Notify Xoxoday of approved return | Platform sends the return notification to Xoxoday after admin approval and waits for vendor confirmation | integration | S-02 |
| S-04 | Credit wallet on Xoxoday confirmation | When Xoxoday confirms, the partner's balance is restored and they receive notification | workflow | S-03 |
| S-05 | Handle return rejection paths | Both admin rejections and vendor rejections close the return without wallet credit, with appropriate notification | workflow | S-02 |

---

## `/create-spec` invocation

```
/create-spec redemption-store F-06
```
