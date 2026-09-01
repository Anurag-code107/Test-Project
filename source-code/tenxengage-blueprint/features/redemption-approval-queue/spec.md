---
slug: redemption-approval-queue
name: Redemption Approval Queue
status: reviewed
format: story-sliced
roadmap: redemption-store
domain: null
builder_type: null
created: 2026-05-27
contract: null
---

> **Reviewed**: 2026-05-27

# Feature: Redemption Approval Queue

> **Format:** story-sliced
> **Stories, tasks, and per-story tests live in sibling files:**
> - [`stories.md`](stories.md) — story index + dependency graph
> - [`stories/`](stories/) — one `US-NN-*.md` per story (self-contained execution unit)
> - [`tasks/foundation.md`](tasks/foundation.md) — horizontal bedrock tasks
> - [`tracker.md`](tracker.md) — session status tracker
> - [`test-plan.md`](test-plan.md) — cross-story integration tests
>
> **This file is the design reference.** Implementers read it alongside their story file.
>
> **Technical artifacts** (Flyway SQL, file paths, hook specs): see [`technical.md`](technical.md).

---

## Overview

The Redemption Approval Queue gives CLIENT_ADMIN and ACTIVITY_APPROVER users a structured interface to review, approve, or reject redemption requests that are held in PENDING_APPROVAL state — i.e., those submitted against catalog items configured for Approval Required processing mode. Approving a request triggers the F-03 vendor routing flow; rejecting it releases the reserved balance back to the partner's wallet and records the reviewer's decision. The queue is filterable and paginated, notifies the requesting partner of outcomes via Kafka, and provides a full audit trail. Return request review (FR-04.6) is included as a filter stub in F-04 and fully implemented in F-06.

### Naming reconciliation (digest-annex advisory → codebase actuals)

| BRD / digest-annex name | Codebase name adopted | Decision |
|---|---|---|
| "Approval Queue" | `RedemptionApprovalController` + `GET /approval-queue` | Adopted — queue is the natural noun; approval-queue as URL segment follows kebab-case |
| "Pending approval" (state) | `RedemptionStatus.PENDING_APPROVAL` | Adopted — matches existing enum value introduced by F-03 |
| "Approval decision" | `approveRedemption()` / `rejectRedemption()` methods | Split into two explicit methods — the binary outcome is clearer as separate operations than a single `decide(decision)` call |
| "Reviewer identity" | `reviewed_by UUID` column | Overridden from potential "approver_id" — `reviewed_by` is consistent with past-tense audit field naming (`created_by`, `reviewed_by`) used elsewhere in the platform |

---

## Functional Requirements

| ID | Requirement | Entity | Endpoint | Error Condition | Audit |
|---|---|---|---|---|---|
| FR-04.1 | When a catalog item has `processingMode = APPROVAL_REQUIRED`, submitted redemptions transition to `PENDING_APPROVAL` status and appear in the approval queue for users with `action.redemption.approve` | `RedemptionRequest` | `GET /approval-queue` | — | — |
| FR-04.2 | The approval queue displays all PENDING_APPROVAL redemption requests with: requesting user display name, catalog item name, currency ID, amount (minor units), wallet type (PERSONAL or COMPANY), and submission timestamp | `RedemptionRequest` | `GET /approval-queue` | — | — |
| FR-04.3 | A user with `action.redemption.approve` can approve a pending redemption; approval transitions status to `RESERVED`, records `reviewedBy` + `reviewedAt` on the request, and calls the F-03 routing service to initiate vendor handoff | `RedemptionRequest` | `POST /{id}/approve` | 404 if not found; 409 if not `PENDING_APPROVAL` | `APPROVED / REDEMPTION_REQUEST` |
| FR-04.4 | A user with `action.redemption.approve` can reject a pending redemption with a required reason; rejection transitions status to `CANCELLED`, records `reviewedBy` + `reviewedAt` + `rejectionReason`, and releases the reserved balance back to the partner wallet | `RedemptionRequest` | `POST /{id}/reject` | 404; 409 if not `PENDING_APPROVAL`; 400 if `rejectionReason` blank | `REJECTED / REDEMPTION_REQUEST` |
| FR-04.5 | The requesting partner is notified via `notification-events` Kafka topic when their redemption is approved (`redemption.approved`) and when rejected (`redemption.rejected`); reviewer identity UUID and decision timestamp are recorded on the `RedemptionRequest` | `RedemptionRequest` | — | — | — |
| FR-04.6 | _(Placeholder — fully implemented in F-06)_ The queue endpoint accepts a `requestType` filter parameter (REDEMPTION \| RETURN); in F-04, RETURN filter always returns empty; the filter parameter is accepted and validated but no return-request data is returned until F-06 | — | `GET /approval-queue?requestType=RETURN` | — | — |
| FR-04.7 | The approval queue is filterable by `startDate`, `endDate`, `currencyId`, `requestType`, and `catalogItemId`; all filters are optional; default sort is `submittedAt DESC`; page size capped at 50 | `RedemptionRequest` | `GET /approval-queue` | 400 if `size > 50` | — |

> **Brief deviation — FR-04.4**: The feature brief states the rejection reason is "optional". Upgraded to **required** (`@NotBlank`) during /create-spec Q&A with product owner. Rationale: an optional reason provides no audit value and makes compliance review ambiguous; the spec requires a non-blank reason on every rejection.

---

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (reads)** | P95 < 300ms | `GET /approval-queue` with filters |
| **Response time (writes)** | P95 < 500ms | Approve/reject — includes routing service call |
| **Peak concurrent users** | 20 approvers per tenant | Approval queue is admin-only; low concurrency |
| **Max page size** | 50 items | Hard cap on `size` query param |
| **Availability** | 99.9% | Admin workflow — blocking if down |
| **Data sensitivity** | CONFIDENTIAL | Financial decisions; internal only |
| **Compliance** | GDPR | `reviewed_by` is a UUID reference to a user |
| **Audit retention** | 7 years | Financial decision audit trail |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec` (status must be `reviewed`)
- [ ] Contracts generated via `/generate-contracts` in the backend repo
- [ ] F-03 (Redemption Flow) feature branch merged — `RedemptionRequest` entity and `PENDING_APPROVAL` status must exist
- [ ] Next Flyway migration number confirmed: V18 (current latest: V17 on features/redemption-flow branch, which is the base for this feature)
- [ ] `action.redemption.approve` permission does not yet exist — will be seeded in V19

---

## New Enums [BE]

None. All required enum values exist:
- `RedemptionStatus.PENDING_APPROVAL`, `RedemptionStatus.CANCELLED`, `RedemptionStatus.RESERVED` — from F-03
- `AuditAction.APPROVED`, `AuditAction.REJECTED` — already in `AuditAction.java`
- `AuditResourceType.REDEMPTION_REQUEST` — added by F-03

---

## Data Model / Entities [BE]

F-04 introduces no new entities. It extends the existing `RedemptionRequest` entity (table: `redemption_requests`) introduced by F-03 via ALTER TABLE migration V18.

### RedemptionRequest (table: `redemption_requests`) — extended columns only

_Existing entity: `com.tenxengage.app.entity.RedemptionRequest` (F-03). F-04 adds the following columns via V18 ALTER TABLE migration._

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `reviewed_by` | `UUID` | NULLABLE, FK → `users(id)` | Reviewer's user ID — null until a decision is made |
| `reviewed_at` | `TIMESTAMPTZ` | NULLABLE | Timestamp when approved or rejected |
| `rejection_reason` | `VARCHAR(1000)` | NULLABLE | Required (non-null, non-blank) when status transitions to `CANCELLED` via rejection; null when approved |

**PII Fields:** None introduced by F-04. `reviewed_by` is a UUID reference — not PII by itself.

**No new indexes** — existing `idx_redemption_requests_client_status` on `(client_id, status)` covers the approval queue query (`WHERE client_id = ? AND status = 'PENDING_APPROVAL'`).

---

## Permissions & Feature Flags [BE + FE]

### Permission Matrix

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `action.redemption.approve` | Approve/Reject Redemptions | ACTION | INTERNAL | REDEMPTION_ACTIONS | Y | Y | — | — |

_No new module permission. The approval queue is a sub-section of `module.redemption_store` (seeded in F-01 V8). The `ProtectedRoute` and sidebar item gate on `action.redemption.approve`, not a new module key._

### Feature Flag

No new feature flag. The approval queue is part of the existing `redemption_store` feature flag (all tiers: `true / true / true`, seeded in F-01 V8).

_Flyway seed SQL for this permission matrix lives in `technical.md → ## Flyway Migrations [BE]`._

---

## DTOs [BE]

### Request DTOs

_Path: `src/main/java/com/tenxengage/app/dto/request/redemption/`_

| Record | Key Fields | Validation |
|---|---|---|
| `RejectRedemptionRequest` | `rejectionReason` | `@NotBlank`, `@Size(max=1000)` — rejection reason is required |

**Validation rules:**
- `rejectionReason`: `@NotBlank @Size(max=1000)` — returns 400 if blank or null
- No request body for approve endpoint — POST with empty body accepted

### Response DTOs

_Path: `src/main/java/com/tenxengage/app/dto/response/redemption/`_

| Record | Static Factory | Notes |
|---|---|---|
| `ApprovalQueueItemResponse` | `from(RedemptionRequest, String userName, String catalogItemName)` | List view for approval queue — lightweight |

_`RedemptionRequestDetailResponse` (F-03, `dto/response/`) is extended with three new nullable fields: `reviewedBy: UUID`, `reviewedAt: Instant`, `rejectionReason: String`. This is an additive change handled in the modified endpoints section._

**Never include in responses:** `clientId`, `deleted`, `version`.

---

## API Endpoints [BE + FE]

_Base path: `/api/v1/redemption/requests`_
_Tag: `Redemption Approval Queue`_

| Method | Path | Request Body | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| `GET` | `/approval-queue` | — | `PaginatedResponse<ApprovalQueueItemResponse>` | 200 | `action.redemption.approve` | — |
| `POST` | `/{id}/approve` | — (empty) | `RedemptionRequestDetailResponse` | 200 | `action.redemption.approve` | `@Audited` |
| `POST` | `/{id}/reject` | `RejectRedemptionRequest` | `RedemptionRequestDetailResponse` | 200 | `action.redemption.approve` | `@Audited` |

**Query parameters for `GET /approval-queue`:**
- `startDate` (optional, ISO-8601 date) — filter `submittedAt >= startDate`
- `endDate` (optional, ISO-8601 date) — filter `submittedAt <= endDate`
- `currencyId` (optional) — filter by currency (e.g., `cash`, `points`)
- `requestType` (optional, enum: `REDEMPTION | RETURN`) — `RETURN` always returns empty in F-04
- `catalogItemId` (optional UUID) — filter by catalog item
- `page` (default 0), `size` (max 50, default 20)
- Sort: fixed `submittedAt DESC` — not client-controllable

**Error responses:**
- `400` — `rejectionReason` blank; `size > 50`; invalid date format
- `401` — Not authenticated
- `403` — Caller lacks `action.redemption.approve`
- `404` — Redemption not found or belongs to a different tenant (always 404, never 403)
- `409` — Redemption not in `PENDING_APPROVAL` state (already approved, rejected, or in another state)

---

## Service Layer [BE]

_Path: `src/main/java/com/tenxengage/app/service/redemption/`_

### RedemptionApprovalService

| Method | Return Type | Notes |
|---|---|---|
| `getApprovalQueue(filters, pageable)` | `Page<ApprovalQueueItemResponse>` | `@Transactional(readOnly=true)` — queries only `PENDING_APPROVAL` items for current tenant; controller wraps with `PaginatedResponse.from(page)`. `userName` and `catalogItemName` sourced from JOIN FETCH associations on the query (`r.user.displayName`, `r.catalogItem.name`) — no per-item lookups |
| `approveRedemption(redemptionId, approverId)` | `RedemptionRequestDetailResponse` | `@Transactional` — validates state, sets `reviewedBy`/`reviewedAt`, transitions status → `RESERVED`, calls routing service, publishes event |
| `rejectRedemption(redemptionId, rejectionReason, approverId)` | `RedemptionRequestDetailResponse` | `@Transactional` — validates state, sets approval fields + `rejectionReason`, transitions status → `CANCELLED`, releases reserved balance, publishes event |

**Business rules:**
- A redemption must be in `PENDING_APPROVAL` state to be acted on; any other state throws `BusinessRuleException` with message "Redemption is not in PENDING_APPROVAL state" → controller maps to 409
- `rejectionReason` must be non-blank for reject; enforced at DTO validation (400) and service layer (guard)
- On approve: call `RedemptionRoutingService.routeApprovedRedemption(redemptionRequest)` in the same transaction — this delegates to F-03's vendor routing logic
- On reject: call `WalletService.releaseReservedBalance(redemptionRequest)` in the same transaction — releases the reserved ledger entry
- Both approve and reject publish to `notification-events` via `NotificationEventProducer` after the DB write commits (out-of-transaction emit — advisory notification, loss tolerable)
- `reviewed_by` and `reviewed_at` are set on both approve and reject; the status field distinguishes the outcome

**Tenant isolation contract:** `clientId` resolved from `tenantValidator.getCurrentClientId()` (injected bean) — never accepted as a parameter. All repository calls use `findByIdAndClientId`.

---

## Workflow / Status Transitions [BE + FE]

```
PENDING_APPROVAL → RESERVED   (action: approve, trigger: CLIENT_ADMIN or ACTIVITY_APPROVER)
                               → calls RedemptionRoutingService for vendor handoff
PENDING_APPROVAL → CANCELLED  (action: reject, trigger: CLIENT_ADMIN or ACTIVITY_APPROVER)
                               → releases reserved balance via WalletService
```

**Invalid transitions** (must return 409 with message "Redemption is not in PENDING_APPROVAL state"):
- Any status other than `PENDING_APPROVAL` on the approve or reject endpoints

**Who can trigger:**
- Both transitions: users with `action.redemption.approve` (CLIENT_ADMIN or ACTIVITY_APPROVER)

**Concurrent transition handling:** `RedemptionRequest` carries `@Version` (optimistic locking). If two approvers attempt to approve the same item simultaneously, the second will receive a `409 Conflict`. The FE must show "This redemption was just actioned by another approver. Please refresh the queue."

---

## Security Design [BE]

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| `reviewed_by` (UUID) | Internal | Not exposed to PARTNER_SELLER or PARTNER_ADMIN roles; returned only in admin-facing responses |
| `rejection_reason` | Confidential | Free text, max 1000 chars; sanitized/trimmed in service layer; visible to approver roles only |
| `amount`, `currencyId` | Confidential | Financial fields; returned only to users with `action.redemption.approve` |
| Requesting user display name (joined) | Internal | Joined from `users` at query time; returned only in approval queue response to authorized roles |

### Rate Limiting

No custom rate limiting for approval endpoints. Standard API-gateway limits apply. Approve/reject endpoints require `action.redemption.approve` — limited to internal admin users; abuse risk is low.

### OWASP Risks & Mitigations

| Risk | Where | Mitigation |
|---|---|---|
| **Broken Access Control (A01)** | `POST /{id}/approve`, `POST /{id}/reject` | `id` resolved via `findByIdAndClientIdForUpdate(id, clientId)` — wrong-tenant ID returns 404, never 403 |
| **Broken Access Control (A01)** | `GET /approval-queue` | All queries filter by `client_id = TenantContext.getCurrentClientId()` — cross-tenant data impossible if filter applied |
| **Injection (A03)** | Filter query params | All filters are structured types (UUID, ISO date, enum); no free-text search; all queries use parameterized JPQL |
| **Insecure Design (A04)** | Approve/reject idempotency | Non-`PENDING_APPROVAL` state returns 409 — prevents double-approval race conditions from being silently accepted |
| **Mass Assignment** | `RejectRedemptionRequest` | Explicit Java record — only `rejectionReason` bound; no dynamic property binding |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `rejectionReason` | `@NotBlank @Size(max=1000)` | 400 with field-level error |
| `requestType` filter | Allowlist: `REDEMPTION`, `RETURN` | 400 — unknown value |
| `size` query param | `@Max(50)` | 400 — capped to prevent oversized responses |
| `startDate`, `endDate` | ISO-8601 date format | 400 — invalid format |
| `catalogItemId`, `{id}` | `UUID` format | 400 — malformed UUID |

---

## Audit Trail [BE]

_Uses existing `@Audited` infrastructure in `com.tenxengage.app.audit/`_

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| Approve redemption | `RedemptionRequest` | `oldStatus=PENDING_APPROVAL`, `newStatus=RESERVED`, `reviewedBy`, `reviewedAt`, redemption `id` | CLIENT_ADMIN |
| Reject redemption | `RedemptionRequest` | `oldStatus=PENDING_APPROVAL`, `newStatus=CANCELLED`, `reviewedBy`, `reviewedAt`, `rejectionReason`, redemption `id` | CLIENT_ADMIN |

### New Audit Enum Values

None — existing enum values cover all operations:
- `AuditAction.APPROVED`, `AuditAction.REJECTED` — already exist
- `AuditResourceType.REDEMPTION_REQUEST` — already added by F-03

### `@Audited` Annotation Details (Non-CRUD Only)

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `POST /{id}/approve` | `APPROVED` | `REDEMPTION_REQUEST` | `Approved redemption request` |
| `POST /{id}/reject` | `REJECTED` | `REDEMPTION_REQUEST` | `Rejected redemption request` |

**Audit record retention:** 7 years. Append-only; never soft-deleted.

**What NOT to audit:** `GET /approval-queue` — read operation on non-PII data; logging volume would be prohibitive.

---

## Observability [BE]

### MDC Fields

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` header | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` from JWT | `TenantFilter` (existing) |
| `userId` | User ID from JWT | `JwtAuthenticationFilter` (existing) |
| `featureArea` | `"redemption-approval-queue"` | Set in `RedemptionApprovalService` |

### Key Log Events

| Event | Level | `step` value | Key Fields | Purpose |
|---|---|---|---|---|
| Redemption approved | INFO | `redemption_approved` | `redemptionId`, `reviewedBy`, `newStatus` | Business event tracking |
| Redemption rejected | INFO | `redemption_rejected` | `redemptionId`, `reviewedBy`, `newStatus` | Business event tracking |
| Invalid state transition | WARN | `redemption_approval_state_error` | `redemptionId`, `currentStatus` | Detect concurrent approval race |
| Tenant isolation violation | ERROR | `tenant_isolation_violation` | `requestedId`, `callerTenantId` | Security alert |
| Notification event published | INFO | `notification_event_published` | `redemptionId`, `notificationType` | Event publishing confirmation |

**Sensitive data in logs:** Log entity IDs and `userId` (UUID) only. Never log `rejectionReason` content or user names.

### Metrics

| Metric Name | Type | Labels | Purpose |
|---|---|---|---|
| `redemption.approved.total` | Counter | `tenantId` | Approval volume tracking |
| `redemption.rejected.total` | Counter | `tenantId` | Rejection volume tracking |
| `approval_queue.list.duration_ms` | Histogram | — | Latency monitoring (alert if P95 > 300ms) |

---

## Domain Events [BE]

### Events Produced

| Topic | Trigger | notificationTypeKey | At-Least-Once Guarantee |
|---|---|---|---|
| `notification-events` | After `approveRedemption` DB write | `redemption.approved` | Deduplicate on `resourceId` + `notificationTypeKey` at consumer |
| `notification-events` | After `rejectRedemption` DB write | `redemption.rejected` | Deduplicate on `resourceId` + `notificationTypeKey` at consumer |

**Message schema (both events use existing `NotificationEvent` record):**
```json
{
  "notificationTypeKey": "redemption.approved",
  "clientId": "uuid",
  "title": "Redemption Approved",
  "message": "Your redemption request has been approved.",
  "resourceType": "redemption_request",
  "resourceId": "redemption-uuid",
  "actorUserId": "approver-user-uuid",
  "targetUserIds": ["requesting-partner-user-uuid"],
  "metadata": {
    "catalogItemId": "uuid",
    "amount": "5000",
    "currencyId": "cash"
  }
}
```

_For `redemption.rejected`, `message` is "Your redemption request was rejected." — rejection reason is NOT included in the event payload (confidentiality risk in Kafka log retention)._

**No new producer class** — published via existing `NotificationEventProducer`.

**Failure semantics:** Direct Kafka emit (same as `NotificationEventProducer`). Advisory notification — occasional loss tolerable. For financial audit trail, the DB `reviewedBy`/`reviewedAt` fields are the authoritative record.

**PII rule enforced:** `actorUserId` and `targetUserIds` are UUIDs only. No names or emails in payload.

---

## Frontend Specification [FE]

_TypeScript types sourced from `../tenxengage-contracts/` — never hand-written._

### Pages

| Page | Route | Layout | Permission | Sidebar Entry |
|---|---|---|---|---|
| `ApprovalQueuePage` | `/redemption/approval-queue` | `ClientAdminLayout` | `action.redemption.approve` | Yes — under "Redemption" section |

_`ProtectedRoute permission="action.redemption.approve"` wraps the route. Sidebar item has `permissionKey: "action.redemption.approve"`._

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `ApprovalQueueTable` | `items, pagination, onApprove, onReject, isLoading` | `useApprovalQueue(filters)` | Paginated table; shows requesting user, item, amount, wallet type, submitted date; row actions: Approve / Reject |
| `ApprovalQueueFilters` | `filters, onChange` | — | Date range, currency, request type, catalog item filters |
| `ApproveConfirmDialog` | `redemptionId, onConfirm, onCancel` | — | Simple confirmation modal — "Approve this redemption?" |
| `RejectDialog` | `redemptionId, onConfirm, onCancel` | — | Modal with required `rejectionReason` text area; submit disabled until reason provided |

### Forms

| Form | Fields | Validation | Submit Action |
|---|---|---|---|
| `RejectDialog` form | `rejectionReason` (required, max 1000 chars) | `rejectRedemptionSchema` (zod: `z.string().min(1).max(1000)`) | `POST /api/v1/redemption/requests/{id}/reject` |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `useApprovalQueue(filters)` | `['approval-queue', clientId, filters]` | `GET /api/v1/redemption/requests/approval-queue` | 5 min | On successful approve or reject mutation |
| `useApproveRedemption()` | — (mutation) | `POST /api/v1/redemption/requests/{id}/approve` | — | Invalidates `['approval-queue', clientId]` |
| `useRejectRedemption()` | — (mutation) | `POST /api/v1/redemption/requests/{id}/reject` | — | Invalidates `['approval-queue', clientId]` |

_After approve/reject mutation succeeds, the queue invalidates and refetches — the actioned item disappears from the list._

---

## Caching Strategy [BE]

No server-side caching. Approval queue data changes frequently (items are approved and rejected in real time) and stale reads are not acceptable for an admin decision workflow. TanStack Query handles client-side caching with 5-minute stale time.

---

## Data Retention & Compliance [BE]

### Soft Delete vs Hard Delete

F-04 adds no new entity. `RedemptionRequest` already follows soft delete (`deleted = false` flag from F-03). Approval decisions are retained on the `RedemptionRequest` record for the full financial audit period.

### PII Handling

| Field | Entity | PII Type | GDPR Treatment |
|---|---|---|---|
| `reviewed_by` | `redemption_requests` | Pseudonymous UUID | Retain — UUID is not PII by itself; resolve to user on demand |

No new raw PII fields introduced by F-04. The requesting user's `displayName` is joined at query time from the existing `users` table — no denormalization.

### Data Retention Periods

| Data Type | Retention Period | Justification |
|---|---|---|
| `redemption_requests` approval fields (`reviewed_by`, `reviewed_at`, `rejection_reason`) | 7 years (with parent record) | Financial decision audit trail |
| Audit log entries for approve/reject | 7 years | Compliance requirement |

---

## Edge Cases [BE + FE]

1. **Empty approval queue** — `GET /approval-queue` returns `Page` with `content: []` and `totalElements: 0`; FE shows `<EmptyState message="No pending redemptions" />`.
2. **Concurrent approval** — Two approvers approve the same item simultaneously: the second gets `409 Conflict` due to `@Version` optimistic locking; FE shows "This redemption was just actioned by another approver. Please refresh the queue."
3. **Cross-tenant access** — `POST /{id}/approve` where `id` belongs to another tenant: `RedemptionApprovalService` resolves via `findByIdAndClientIdForUpdate` — returns 404, never 403.
4. **Approve already-rejected** — `POST /{id}/approve` on a `CANCELLED` redemption returns 409 with message "Redemption is not in PENDING_APPROVAL state".
5. **Reject without reason** — `POST /{id}/reject` with `rejectionReason` blank or missing returns 400 with field-level validation error.
6. **RETURN requestType filter** — `GET /approval-queue?requestType=RETURN` returns empty list in F-04; no error. Full implementation in F-06.
7. **size > 50** — Returns 400 with message "Page size must not exceed 50".
8. **Notification publish failure** — If `NotificationEventProducer.publish` throws, the exception is caught and logged (`WARN` level); the approve/reject DB write is already committed and is NOT rolled back. The business decision stands; the notification is advisory only.
9. **Vendor routing failure on approve** — If `RedemptionRoutingService.routeApprovedRedemption` throws, the entire `approveRedemption` transaction rolls back; status remains `PENDING_APPROVAL`; approver sees 500 with guidance to retry.
10. **Wallet release failure on reject** — If `WalletService.releaseReservedBalance` throws, the entire `rejectRedemption` transaction rolls back; status remains `PENDING_APPROVAL`; approver sees 500 with guidance to retry.

---

## Modified Existing Endpoints [BE + FE]

| Endpoint | Change | Reason | Breaking? |
|---|---|---|---|
| `GET /api/v1/redemption/requests/{id}` | `RedemptionRequestDetailResponse` extended with `reviewedBy: UUID \| null`, `reviewedAt: Instant \| null`, `rejectionReason: String \| null` | Partners and admins need to see approval decision on their redemption detail view | No — additive change |

---

## Planning Seeds (from feature brief)

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | View pending approval queue | Approvers see a filterable list of redemptions awaiting review with full context | UI | F-03.S-01 |
| S-02 | Approve pending redemption | Approver approves a request, triggering vendor handoff and partner notification | workflow | S-01 |
| S-03 | Reject pending redemption | Approver rejects a request, releasing the reserved balance and notifying the partner | workflow | S-01 |

---

## Out of Scope

- Return request approval implementation (deferred to F-06; only filter stub wired in F-04)
- Quorum / multi-approver flow for company wallet redemptions (deferred to Phase 2, ADR-01)
- Auto-timeout or escalation for pending approvals left unactioned for extended periods (not in BRD Phase 1)
- Approval delegation to a backup approver
- Approver notification when a new redemption enters the queue (not in Phase 1 FRs)

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, `@WebMvcTest`, Vitest, E2E Playwright) — inside each `stories/US-NN-*.md` file
- **Cross-story integration tests** (Testcontainers full-lifecycle, state machine, tenant isolation, event publishing) — in [test-plan.md](test-plan.md)

_14 integration test scenarios planned for `ApprovalQueueControllerTest` and `ApprovalQueueServiceTest` — see plan file for full scenario list._

---

## Verification Steps

### Backend Verification
1. `./gradlew bootRun` — app starts; Flyway V18 and V19 migrations apply without errors
2. `./gradlew test` — all new and existing tests pass
3. Security: `GET /approval-queue` without JWT → 401; with `PARTNER_SELLER` JWT → 403; with `ACTIVITY_APPROVER` JWT → 200
4. `POST /{id}/approve` with correct JWT on `PENDING_APPROVAL` redemption → 200 + status changes to `RESERVED`
5. `POST /{id}/approve` on already-approved item → 409
6. `POST /{id}/reject` with blank `rejectionReason` → 400; with valid reason → 200 + status `CANCELLED`
7. Cross-tenant: `POST /{id}/approve` with Tenant B UUID, Tenant A JWT → 404
8. Tail logs on approve: verify `step=redemption_approved`, `tenantId`, `userId` appear; verify `notification_event_published` log line

### Frontend Verification
1. `npm run build` — no TypeScript errors
2. `npm run test` — Vitest passes; `npx playwright test` — E2E passes
3. Approval queue page visible for CLIENT_ADMIN and ACTIVITY_APPROVER; not visible in sidebar for PARTNER_SELLER
4. Queue table renders items with correct fields; empty state shows when no items
5. Approve dialog: click Approve → confirmation → item disappears from queue
6. Reject dialog: submit disabled with blank reason; submit with reason → item disappears
7. Concurrent edit: simulate 409 response → toast "This redemption was just actioned by another approver. Please refresh the queue."
