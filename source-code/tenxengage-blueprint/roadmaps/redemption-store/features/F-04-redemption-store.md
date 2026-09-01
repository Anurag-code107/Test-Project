# F-04: Redemption Approval Queue

> **Slug**: `redemption-approval-queue` · **Roadmap**: `redemption-store` · **Phase**: 1 · **Recommended order**: 4th
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Redemption Flow", "Returns", "Users and Personas"

## Business outcome

Client Admins and Approvers gain a structured queue for reviewing pending redemption requests before vendor handoff — preventing unauthorized spend, enforcing program controls, and giving administrators visibility and control over high-value or policy-gated redemptions. The same queue handles return request reviews (covered in F-06).

## Primary persona of record

**`ACTIVITY_APPROVER`** (BRD: "Approver") — The reviewer whose approval gates vendor handoff for APPROVAL_REQUIRED redemptions and return processing. Primary actor of this queue.

## Secondary personas

- **`CLIENT_ADMIN`** — Has the same approval powers as Approver; also the escalation path if no Approver is designated.
- **`PARTNER_SELLER`** / **`PARTNER_ADMIN`** — Receive notifications of approval outcomes; see updated status in their transaction history.

## User journey (sketch)

Approver navigates to the Approval Queue (nested under the admin/operations area), sees a list of pending redemptions awaiting review with full context (requester, item, currency, amount, submission time). They review the details, approve a legitimate request (which triggers vendor handoff in F-03), and reject a suspicious one with a reason (which releases the reserved balance back to the partner). The partner is notified of both outcomes.

## Functional requirements (business intent)

1. **FR-04.1** — When a catalog item is configured for Approval Required processing mode, submitted redemptions are held in pending approval state and appear in the approval queue for Client Admin and Approver users.
2. **FR-04.2** — The approval queue displays all pending redemption requests with: requesting user, catalog item name, currency type, amount, wallet type (personal or company), and submission timestamp.
3. **FR-04.3** — Client Admin and Approver can approve a pending redemption request; approval triggers vendor handoff and transitions the redemption to the routing flow (F-03).
4. **FR-04.4** — Client Admin and Approver can reject a pending redemption request with an optional reason; rejection releases the reserved balance back to available and notifies the requesting partner.
5. **FR-04.5** — The requesting partner is notified when their redemption is approved and when it is rejected; the approval or rejection timestamp and reviewer identity are recorded on the transaction.
6. **FR-04.6** — Return requests submitted by partners (F-06) also appear in the approval queue for Client Admin and Approver review.
7. **FR-04.7** — The approval queue is filterable by date range, currency type, request type (redemption vs. return), and catalog item.

## Business rules

- A redemption in PENDING_APPROVAL state is not sent to the vendor until approved; the balance remains reserved during the approval window.
- Rejection immediately releases the reserved balance — the partner can resubmit with different parameters.
- Approvals and rejections are audited via the platform's existing audit framework.
- Only users with `action.redemption.approve` permission can approve or reject redemptions from the queue.

## Constraints / validations

- ADR-01: Whether company wallet redemptions require a minimum number of approvers (quorum) is open — defaulting to single approver for Phase 1. Resolve in /create-spec.
- A redemption that has already been approved or rejected cannot be acted on again from the queue.

## Edge cases / open questions

- **ADR-01**: If product requires quorum approval for company wallet redemptions, the queue model needs a multi-approver flow with a tracking mechanism. Resolve before /create-spec.
- What is the behavior when the only Approver for a tenant is unavailable for an extended period? Is there an escalation path or timeout?
- Should rejected redemption requests be permanently closed or can the partner resubmit? (BRD implies resubmit is possible since balance is released.)

## Dependencies

- **Features**: F-03 (redemption requests created here), F-06 (return requests reviewed here)
- **ADRs**: ADR-01 (company wallet approver count)

## Riskiest unknown

Whether the Approver role (`ACTIVITY_APPROVER`) needs any new permissions or if the existing role's permission set covers redemption approval. If `ACTIVITY_APPROVER` currently only has permissions for activity-type approvals, adding `action.redemption.approve` requires a Flyway migration to the role's default permission grants.

## Candidate domain concepts (business nouns)

- **Approval queue**: The admin interface showing all redemptions and returns awaiting review.
- **Pending approval**: The state of a redemption submitted under Approval Required mode, awaiting admin action.
- **Approval decision**: The act of approving (triggering vendor handoff) or rejecting (releasing reserved balance) a pending request.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | F-03 (Redemption Flow) | APPROVAL_REQUIRED redemptions appear in queue |
| Receives from | F-06 (Returns) | Return requests appear in queue for review |
| Sends to | F-03 (Redemption Flow) | Approval decision triggers vendor handoff |
| Sends to | F-06 (Returns) | Return approval triggers Xoxoday notification |

---

## Suggested story seeds

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | View pending approval queue | Approvers see a filterable list of redemptions and returns awaiting review with full context | UI | F-03.S-01 |
| S-02 | Approve pending redemption | Approver approves a request, triggering vendor handoff and partner notification | workflow | S-01 |
| S-03 | Reject pending redemption | Approver rejects a request, releasing the reserved balance and notifying the partner | workflow | S-01 |

---

## `/create-spec` invocation

```
/create-spec redemption-store F-04
```
