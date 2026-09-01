# RedemptionReturn

Non-cash return request lifecycle record. Tracks partner return submissions for eligible Xoxoday redemptions from submission through admin approval, Xoxoday vendor confirmation or rejection, and the 7-day RETURN_TIMED_OUT safety path. Tenant-isolated via `client_id` Hibernate filter with optimistic locking.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated — PK, default `gen_random_uuid()` |
| `redemptionId` | UUID | Yes | FK → redemption_requests(id) — originating redemption |
| `partnerUserId` | UUID | Yes | Submitting partner user UUID (from JWT at submit time) |
| `status` | ReturnStatus | Yes | State machine lifecycle |
| `reason` | string (TEXT, max 500) | No | Optional partner-provided return reason; PII-adjacent; Jsoup-sanitized before persistence |
| `reviewedBy` | UUID | No | Admin/approver UUID who took action on the return |
| `reviewedAt` | datetime | No | Timestamp of admin action (approve or reject) |
| `reviewNotes` | string (TEXT, max 1000) | No | Admin notes on rejection or manual resolve; Jsoup-sanitized |
| `vendorReturnReference` | string (max 255) | No | Xoxoday's return ID, set after successful approval API call |
| `amount` | decimal string | Yes | Full redemption amount — always copied from originating redemption at submit time; caller cannot set |
| `currencyId` | string (max 50) | Yes | Currency type from originating redemption |
| `approvedAt` | datetime | No | Set when admin approves |
| `timedOutAt` | datetime | No | Set by scheduler when RETURN_TIMED_OUT |
| `confirmedAt` | datetime | No | Set when Xoxoday confirms the return |
| `rejectedAt` | datetime | No | Set on RETURN_REJECTED (admin rejection or Xoxoday webhook) |
| `cancelledAt` | datetime | No | Set on CANCELLED by partner |
| `createdAt` | datetime | Yes | Inherited from BaseEntity |
| `updatedAt` | datetime | Yes | Inherited from BaseEntity |

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Tenant isolation — resolved server-side via Hibernate filter; NEVER in any response |
| `partnerUserId` (raw) | UUID — not returned directly; joined as `partnerDisplayName` in responses |
| `reviewedBy` (raw) | UUID — not returned directly; never exposed as raw UUID |
| `vendorReturnReference` | Admin-only field — excluded from partner-facing responses |
| `reviewNotes` | Admin-only field — excluded from partner-facing responses |
| `version` | Optimistic lock counter; internal |
| `deleted` | Soft delete flag; internal |

## Business Rules

- `amount` is always copied from `RedemptionRequest.amount` at submit time — caller cannot set it; system overwrites any submitted value
- At most one non-CANCELLED return per redemption (service-layer uniqueness, not DB constraint); partner may resubmit after CANCELLED
- `RETURN_CONFIRMED` and `RETURN_REJECTED` are terminal — no further transitions or resubmission after either
- `CANCELLED` is non-terminal — partner may submit a new return for the same redemption
- Eligibility check at submit time: `status=COMPLETED`, `category=NON_CASH`, `NOW() <= completedAt + returnWindowDays`, `isReturnable=true` on catalog item; returns 422 on failure
- `reason` is sanitized via Jsoup at service layer before persistence; excluded from all log output
- `reviewNotes` is sanitized via Jsoup at service layer before persistence
- `doReturnCreditInTx()` idempotency guard prevents double-credit if Xoxoday webhook and admin resolve race on RETURN_TIMED_OUT path
- After 7 days in APPROVED state with no Xoxoday webhook, `ReturnTimeoutScheduler` (@Scheduled hourly) transitions to `RETURN_TIMED_OUT`

## Status Transitions

```
[SUBMIT]                  → PENDING_APPROVAL   (partner submits via POST /returns)
PENDING_APPROVAL          → APPROVED           (admin approves via POST /admin/returns/{id}/approve)
PENDING_APPROVAL          → RETURN_REJECTED    (admin rejects via POST /admin/returns/{id}/reject)
PENDING_APPROVAL          → CANCELLED          (partner cancels via DELETE /returns/{id})
APPROVED                  → RETURN_CONFIRMED   (Xoxoday webhook confirms; RETURN_CREDIT ledger entry written)
APPROVED                  → RETURN_REJECTED    (Xoxoday webhook rejects; no wallet credit)
APPROVED                  → RETURN_TIMED_OUT   (ReturnTimeoutScheduler fires at T+7d with no webhook)
RETURN_TIMED_OUT          → RETURN_CONFIRMED   (admin manual resolve via POST /admin/returns/{id}/resolve with CONFIRM)
RETURN_TIMED_OUT          → RETURN_REJECTED    (admin manual resolve via POST /admin/returns/{id}/resolve with REJECT)
```

`RETURN_CONFIRMED`, `RETURN_REJECTED`, and terminal-path `CANCELLED` accept no further transitions — incoming transition returns 409.

Concurrent transitions resolved via `@Version` (optimistic locking) — conflict returns 409.

## Multi-Tenancy

- Tenant-scoped via `client_id` (Hibernate `@Filter` — never exposed in responses)
- `clientId` resolved from `TenantContext.getCurrentClientId()` (JWT) — never accepted from API parameters

## Relationships

- `@ManyToOne(fetch=LAZY)` → `RedemptionRequest` (FK: `redemption_id`) — originating redemption
