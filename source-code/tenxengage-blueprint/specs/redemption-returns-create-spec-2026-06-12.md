---
slug: redemption-returns
stepsCompleted: [parse-input, load-brd-context, load-project-context, resolve-open-questions, detect-feature-shape, load-shape-references, scope-decomposition, security-analysis, events-analysis, test-strategy, permissions-analysis, derive-slug, generate-spec-content, generate-technical-content, write-plan-file]
filesWritten: ["spec.md", "technical.md"]
---

# Spec Plan: redemption-returns

## Feature
- **Slug**: `redemption-returns`
- **Folder**: `features/redemption-returns/`
- **Branch**: `features/redemption-returns` (off `roadmaps/redemption-store`)

## Context

F-06 Non-Cash Returns closes the partner reward loop by giving PARTNER_SELLER and PARTNER_ADMIN users a self-service path to return non-cash (Xoxoday) redemptions that didn't meet expectations. The feature is eligible now because F-03 through F-05 are tracker-complete with PRs merged. F-03 vendor stories (US-05/06/07) have an external blocker (XTRM/Xoxoday APIs) but that doesn't block F-06's spec — the return flow depends on Xoxoday's return API which is separate from the F-03 order placement flow.

The spec extends two existing page surfaces (F-04 approval queue and F-05 transaction history) rather than introducing new standalone pages. The primary backend concern is lifecycle correctness: credits are issued only on Xoxoday webhook confirmation — never on admin approval alone — using the already-implemented `WalletMutationDelegate.doReturnCreditInTx()` idempotent credit path from F-02.

The key design decision was introducing `RETURN_TIMED_OUT` as an additional state beyond the BRD's five-state lifecycle. This prevents partners from being stuck in limbo if Xoxoday doesn't respond within 7 days, while avoiding auto-rejection (which would leave the partner with no balance and no recourse). The admin manual resolve path at `RETURN_TIMED_OUT` provides the escape valve.

F-06 also completes FR-04.6, the placeholder stub in `RedemptionApprovalService.java:79-81` that returns `Page.empty()` for `requestType=RETURN`. The completion is via a dedicated admin endpoint (`GET /api/v1/redemption/admin/returns`) and a new Returns tab in the F-04 approval queue page, keeping the existing queue endpoint's response type clean.

## Phase 0 answers (locked)

| Question | Answer |
|---|---|
| Return timeout mechanism | `RETURN_TIMED_OUT` at T+7d from `APPROVED`; admin manually resolves to CONFIRMED or REJECTED; no auto-reject |
| Xoxoday API unavailability | Exponential backoff (5 attempts, 1s/2s/4s/8s/32s); DLQ + ops alert on persistent failure; return stays `APPROVED` |
| Availability SLA | 99.9% — core partner-facing financial flow (same as F-03) |
| Max concurrent load | No stated limit (inherited from F-03 — no hard cap) |
| Partially-used gift card | Full-amount credit on Xoxoday confirmation; partial-value handling is Xoxoday's concern, not platform's |
| Eligibility for resubmission after CANCELLED | Allowed — CANCELLED is non-terminal; RETURN_CONFIRMED and RETURN_REJECTED are terminal (no resubmission) |

## Scope summary

1 entity (`RedemptionReturn`), ~10 endpoints, ~3 frontend surfaces (dialog + tab + tab). Single spec.

## Permissions matrix

| Permission Key | Scope | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|
| `action.redemption.return.request` | EXTERNAL | — | — | Y | Y |
| `action.redemption.return.review` | INTERNAL | Y | Y | — | — |

Feature flag: `redemption_non_cash_returns` — starter:`false`, professional:`true`, enterprise:`true`

## Kafka topics

- **`return-events`** (new, 3 partitions, 1 replica)
  - Producer: `ReturnEventProducer`
  - Partition key: `clientId`
  - Events: `RETURN_REQUESTED`, `RETURN_APPROVED`, `RETURN_CONFIRMED`, `RETURN_REJECTED`, `RETURN_CANCELLED`, `RETURN_TIMED_OUT`
  - Base payload fields: `eventId`, `eventType`, `occurredAt`, `clientId`, `returnId`, `redemptionId`, `amount`, `currencyId`, `status`
  - Idempotency: consumers deduplicate on `eventId` UUID

## NEEDS_CLARIFICATION

_(none — all ambiguities resolved interactively during run)_

## Registry edits

_(none — no domain registry changes required)_

---

### File: features/redemption-returns/spec.md

---
slug: redemption-returns
name: Non-Cash Returns
status: draft
format: story-sliced
roadmap: redemption-store
domain: null
builder_type: null
created: 2026-06-12
contract: null
visual_reference:
  component_path: null
  notes: null
applicable_sections:
  source: null
  sections: []
---

# Feature: Non-Cash Returns

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

Non-Cash Returns closes a key gap in the Redemption Store: when a partner receives a non-cash reward (gift card, merchandise, prepaid card) that doesn't meet expectations, they can request a return from their transaction history. The return goes through a Client Admin or Approver approval gate before being forwarded to Xoxoday's return API. The partner's balance is restored only after Xoxoday confirms — never on admin approval alone. A 7-day RETURN_TIMED_OUT safeguard protects partners from indefinite limbo if Xoxoday is slow to respond. This feature completes the F-04 approval queue placeholder (FR-04.6) and integrates with F-05 transaction history as the launch surface.

### Naming reconciliation (digest-annex advisory → codebase actuals)

| BRD / digest-annex name | Codebase name adopted | Decision |
|---|---|---|
| "Return request" | `RedemptionReturn` entity | Adopted — no codebase equivalent; BRD name is clear and consistent with the redemption domain |
| "Return status" | `ReturnStatus` enum (new) | New enum; separate from `RedemptionStatus` — return lifecycle states differ fundamentally from redemption states |
| "Return events" (snake_case in BRD) | UPPER_SNAKE_CASE Kafka events | Overridden — matches platform convention established by F-03 (`REDEMPTION_REQUESTED` etc.) |
| "Return credit" | `LedgerEntryType.RETURN_CREDIT` | Already exists in codebase; reused as-is |
| `action.redemption.return.request` / `.return.review` | Adopted verbatim | BRD-aligned; consistent with `action.redemption.approve` pattern from F-04 |

---

## Functional Requirements

| ID | Requirement | Entity | Endpoint | Error Condition | Audit |
|---|---|---|---|---|---|
| FR-06.1 | A partner may submit a return request for a completed non-cash (Xoxoday) redemption that is within the client-configured return window and for a catalog item marked `isReturnable = true`; cash (XTRM) redemptions are not eligible under any circumstances | `RedemptionReturn` | `POST /api/v1/redemption/returns` | 422 if not eligible (outside return window, `isReturnable = false`, XTRM redemption, non-COMPLETED status); 409 if active return already exists | `SUBMITTED / REDEMPTION_RETURN` |
| FR-06.2 | The "Request Return" action is displayed in partner transaction history only for eligible completed non-cash redemptions; ineligible transactions (outside window, non-returnable item, XTRM, not COMPLETED, or already-returned) do not surface this option | `RedemptionReturn`, `RedemptionRequest` | `GET /api/v1/redemption/requests/personal` (F-05 — adds `isReturnEligible` flag) | — | — |
| FR-06.3 | Partner submits a return with an optional reason (max 500 chars); return is created in `PENDING_APPROVAL` status; CLIENT_ADMIN and ACTIVITY_APPROVER are notified via Kafka | `RedemptionReturn` | `POST /api/v1/redemption/returns` | 400 if `reason` > 500 chars | `SUBMITTED / REDEMPTION_RETURN` |
| FR-06.4 | A user with `action.redemption.return.review` can approve a return request; approval transitions return to `APPROVED`, records `reviewedBy` + `approvedAt`, and asynchronously calls Xoxoday's return API; partner's balance is NOT credited at approval time | `RedemptionReturn` | `POST /api/v1/redemption/admin/returns/{id}/approve` | 404 if not found; 409 if not `PENDING_APPROVAL` | `APPROVED / REDEMPTION_RETURN` |
| FR-06.5 | Upon Xoxoday return confirmation webhook, system writes a `RETURN_CREDIT` ledger entry via `WalletMutationDelegate.doReturnCreditInTx()`, restores the full original amount to the partner's available balance in the originating currency, transitions return to `RETURN_CONFIRMED`, and notifies the partner | `RedemptionReturn` | `POST /api/v1/webhooks/redemption-returns/{vendor}` | Idempotent — duplicate webhook returns 200 with no side effects | `COMPLETED / REDEMPTION_RETURN` |
| FR-06.6 | When Xoxoday rejects the return via webhook (e.g., item already used), return transitions to `RETURN_REJECTED`, no wallet credit is issued, and the partner is notified with the vendor's rejection reason | `RedemptionReturn` | `POST /api/v1/webhooks/redemption-returns/{vendor}` | Idempotent — duplicate webhook returns 200 | `REJECTED / REDEMPTION_RETURN` |
| FR-06.7 | A user with `action.redemption.return.review` can reject a return request with a mandatory reason (max 1000 chars); rejection transitions return to `RETURN_REJECTED`, Xoxoday is not contacted, and the partner is notified | `RedemptionReturn` | `POST /api/v1/redemption/admin/returns/{id}/reject` | 404 if not found; 409 if not `PENDING_APPROVAL`; 400 if `rejectionReason` blank | `REJECTED / REDEMPTION_RETURN` |
| FR-06.8 | The partner can cancel their own return request while it is in `PENDING_APPROVAL` status; cancellation transitions to `CANCELLED` with no admin action required | `RedemptionReturn` | `DELETE /api/v1/redemption/returns/{id}` | 404 if not found or not owned by caller; 409 if not `PENDING_APPROVAL` | `CANCELLED / REDEMPTION_RETURN` |
| FR-06.9 | Return credit ledger entries (`LedgerEntryType.RETURN_CREDIT`) are distinct from standard reward earning credits in transaction history, analytics, and audit logs | `LedgerEntry` (existing) | — | — | — |
| FR-06.10 | Partial returns are not supported in v1; only full-amount returns are accepted; the system sets `amount = originalRedemption.amount` automatically and ignores any amount value submitted by the caller | `RedemptionReturn` | — | 422 if partial amount is explicitly passed | — |
| FR-06.11 | The return status lifecycle follows: `PENDING_APPROVAL → APPROVED → RETURN_CONFIRMED \| RETURN_REJECTED`; or `PENDING_APPROVAL → CANCELLED`; all other transitions return 409 | `RedemptionReturn` | — | 409 on invalid transition | — |
| FR-06.12 | _(Probe addition — approved)_ After 7 days in `APPROVED` state with no Xoxoday webhook, a scheduled job transitions the return to `RETURN_TIMED_OUT`; the partner and the tenant's CLIENT_ADMIN are notified via Kafka; an admin with `action.redemption.return.review` can then manually resolve the return to `RETURN_CONFIRMED` or `RETURN_REJECTED` via `POST /{id}/resolve` | `RedemptionReturn` | `POST /api/v1/redemption/admin/returns/{id}/resolve` | 409 if not `RETURN_TIMED_OUT` | `COMPLETED or REJECTED / REDEMPTION_RETURN` |
| FR-06.13 | _(Probe addition — approved)_ When the Xoxoday return API call fails after admin approval, the system retries with exponential backoff (initial 1s, max 5 attempts, cap 32s); on persistent failure the event is routed to the DLQ and an ops alert is raised; the return remains in `APPROVED` state (not auto-rejected) until the RETURN_TIMED_OUT scheduler fires | `RedemptionReturn` | — | — | — |

> **Return window source**: The return window (days after fulfillment within which returns are accepted) is configured per catalog item in the tenant catalog configuration (F-02). Eligibility check: `NOW() <= redemptionRequest.completedAt + returnWindowDays`.

---

## Functional Completeness Audit

| # | Dimension | Status | FR / Notes |
|---|---|---|---|
| 1 | Return status lifecycle | ✓ Already covered | FR-06.11 — PENDING_APPROVAL → APPROVED → RETURN_CONFIRMED \| RETURN_REJECTED \| RETURN_TIMED_OUT; or → CANCELLED |
| 2 | Approval timeout / long-running vendor non-response | ⊕ Approved | FR-06.12 — 7-day RETURN_TIMED_OUT with admin manual resolution |
| 3 | Vendor API unavailability at approval time | ⊕ Approved | FR-06.13 — exponential backoff → DLQ → ops alert; return stays APPROVED |
| 4 | Partial-return scenario | ✓ Already covered | FR-06.10 — not supported in v1; full-amount only |
| 5 | Partially-used gift card edge case | ✓ Already covered | Out of scope for platform: TenXEngage issues full original amount on Xoxoday confirmation; partial-value handling is Xoxoday's responsibility |
| 6 | Cancellation path | ✓ Already covered | FR-06.8 — partner cancels own PENDING_APPROVAL return; resubmission allowed after CANCELLED |

---

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (reads)** | P95 < 300ms | List endpoints with filters |
| **Response time (writes)** | P95 < 500ms | Return submission, approve, reject |
| **Peak concurrent users** | No stated limit | Inherited from F-03 — no hard concurrency cap |
| **Max page size** | 50 items | Hard cap on `size` query param |
| **Availability** | 99.9% | Core partner-facing financial flow |
| **Data sensitivity** | CONFIDENTIAL | Financial transaction data; `reason` field may contain personal details |
| **Compliance** | GDPR | `reason` text is PII-adjacent; see Data Retention |
| **Audit retention** | 7 years | Financial transaction audit trail |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec` (status must be `reviewed`)
- [ ] Contracts generated via `/generate-contracts` in the backend repo
- [ ] F-03 (Redemption Flow) merged — `RedemptionRequest`, `COMPLETED` status, `LedgerEntryType.RETURN_CREDIT`, `WalletMutationDelegate.doReturnCreditInTx()` must exist
- [ ] F-04 (Redemption Approval Queue) merged — approval queue page exists in FE for Returns tab addition
- [ ] F-05 (Redemption History) merged — transaction history page exists for "Request Return" CTA
- [ ] Next Flyway migration number confirmed: V25 (current latest: V24 — `V24__seed_redemption_history_permissions.sql`)

---

## New Enums [BE]

| Enum Class | Values | Notes |
|---|---|---|
| `ReturnStatus.java` | `PENDING_APPROVAL, APPROVED, RETURN_CONFIRMED, RETURN_REJECTED, CANCELLED, RETURN_TIMED_OUT` | Separate from `RedemptionStatus` — return lifecycle states differ from redemption states |
| `ReturnResolution.java` | `CONFIRM, REJECT` | Used in `ResolveTimedOutReturnRequest`; drives the two paths on admin manual resolve |

_Path: `src/main/java/com/tenxengage/app/entity/enums/`_

No changes to existing enums. All required `AuditAction` values (`SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED`, `EXPIRED`) already exist.

---

## Data Model / Entities [BE]

### Entity-shape decisions

| Entity | Shape | Source |
|---|---|---|
| `RedemptionReturn` | Hardcoded JPA entity | This spec — transaction-record entity with status lifecycle, financial fields, and vendor integration; not a configurable data object |

### RedemptionReturn (table: `redemption_returns`)

_Path: `src/main/java/com/tenxengage/app/entity/`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited from BaseEntity |
| `client_id` | `UUID` | NOT NULL, FK → clients(id) | Tenant isolation — NEVER expose in API responses |
| `redemption_id` | `UUID` | NOT NULL, FK → redemption_requests(id) | Originating redemption |
| `partner_user_id` | `UUID` | NOT NULL | Submitting partner user (from JWT at submit time) |
| `status` | `ReturnStatus` | NOT NULL | State machine lifecycle |
| `reason` | `TEXT` | NULL, max 500 chars | Optional partner-provided return reason |
| `reviewed_by` | `UUID` | NULL | Admin/approver UUID who took action on the return |
| `reviewed_at` | `TIMESTAMPTZ` | NULL | Timestamp of admin action (approve or reject) |
| `review_notes` | `TEXT` | NULL, max 1000 chars | Admin notes on rejection or manual resolve |
| `vendor_return_reference` | `VARCHAR(255)` | NULL | Xoxoday's return ID, set after successful approval API call |
| `amount` | `NUMERIC(19,4)` | NOT NULL | Full redemption amount (always copied from originating redemption) |
| `currency_id` | `VARCHAR(50)` | NOT NULL | Currency type from originating redemption |
| `approved_at` | `TIMESTAMPTZ` | NULL | Set when admin approves |
| `timed_out_at` | `TIMESTAMPTZ` | NULL | Set by scheduler when RETURN_TIMED_OUT |
| `confirmed_at` | `TIMESTAMPTZ` | NULL | Set when Xoxoday confirms |
| `rejected_at` | `TIMESTAMPTZ` | NULL | Set on RETURN_REJECTED (admin or Xoxoday) |
| `cancelled_at` | `TIMESTAMPTZ` | NULL | Set on CANCELLED |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `deleted` | `BOOLEAN` | NOT NULL, DEFAULT `false` | Soft delete flag |
| `version` | `BIGINT` | NOT NULL, DEFAULT `0` | Optimistic locking (`@Version`) |

**PII Fields:**
- `reason` — free text, may contain personal info; see Data Retention & Compliance

**Relationships:**
- `@ManyToOne(fetch=LAZY)` → `RedemptionRequest` (FK: `redemption_id`)

**Indexes:**
- `idx_redemption_returns_client_id` on `client_id`
- `idx_redemption_returns_client_status` on `(client_id, status)`
- `idx_redemption_returns_redemption_id` on `redemption_id`
- `idx_redemption_returns_partner_user` on `(client_id, partner_user_id)`
- `idx_redemption_returns_vendor_ref` on `vendor_return_reference` WHERE NOT NULL (webhook lookup)

**Business uniqueness rule** (service-layer, not DB constraint): At most one non-CANCELLED return per redemption. A partner may resubmit after CANCELLED. RETURN_CONFIRMED and RETURN_REJECTED are terminal — no resubmission.

---

## Permissions & Feature Flags [BE + FE]

### Permission Matrix

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `action.redemption.return.request` | Request Redemption Return | ACTION | `EXTERNAL` | REDEMPTION_ACTIONS | — | — | Y | Y |
| `action.redemption.return.review` | Review Return Requests | ACTION | `INTERNAL` | REDEMPTION_ACTIONS | Y | Y | — | — |

### Feature Flag

| Feature Key | Description | starterEnabled | professionalEnabled | enterpriseEnabled | Category |
|---|---|---|---|---|---|
| `redemption_non_cash_returns` | Enable non-cash gift-card / prepaid return requests for partners | `false` | `true` | `true` | REDEMPTION |

_Flyway seed SQL lives in `technical.md → ## Flyway Migrations [BE]`._

---

## DTOs [BE]

### Request DTOs

_Path: `src/main/java/com/tenxengage/app/dto/request/redemption/`_

| Record | Key Fields | Validation |
|---|---|---|
| `SubmitReturnRequest` | `redemptionId (UUID)`, `reason (String, nullable)` | `@NotNull` on `redemptionId`; `@Size(max=500)` on `reason` |
| `RejectReturnRequest` | `rejectionReason (String)` | `@NotBlank`, `@Size(max=1000)` |
| `ResolveTimedOutReturnRequest` | `resolution (ReturnResolution)`, `notes (String, nullable)` | `@NotNull` on `resolution`; `@Size(max=1000)` on `notes` |

### Response DTOs

_Path: `src/main/java/com/tenxengage/app/dto/response/redemption/`_

| Record | Static Factory | Rendered Fields |
|---|---|---|
| `ReturnSummaryResponse` | `from(RedemptionReturn, String catalogItemName)` | `id (UUID)`, `redemptionId (UUID)`, `catalogItemName (String)` rendered as item name, `amount (BigDecimal)` rendered as formatted currency, `currencyId (String)` rendered as currency label, `status (ReturnStatus)` rendered as status badge, `reason (String, nullable)` rendered as truncated text or "—", `createdAt (Instant)` rendered as relative time, `resolvedAt (Instant, nullable)` rendered as "Resolved {date}" or "—" |
| `ReturnDetailResponse` | `from(RedemptionReturn, String catalogItemName, String partnerDisplayName)` | All fields from `ReturnSummaryResponse` plus `partnerDisplayName (String)` rendered as "Requested by", `reviewedAt (Instant, nullable)` rendered as "Reviewed on {date}", `reviewNotes (String, nullable)` rendered as "Admin notes" section (admin role only), `vendorReturnReference (String, nullable)` rendered as "Vendor reference" (admin role only), `approvedAt`, `timedOutAt`, `confirmedAt`, `rejectedAt`, `cancelledAt` rendered as a status timeline |
| `ReturnQueueItemResponse` | `from(RedemptionReturn, String catalogItemName, String partnerDisplayName, String partnerCompanyName)` | `id (UUID)`, `catalogItemName (String)` rendered as item name, `partnerDisplayName (String)` rendered as "Requested by", `partnerCompanyName (String)` rendered as company name, `amount (BigDecimal)` rendered as formatted currency, `currencyId (String)`, `status (ReturnStatus)` rendered as status badge, `reason (String, nullable)` rendered as "Reason" column, `createdAt (Instant)` rendered as submission date |

**Never include in responses:** `client_id`, `deleted`, `version`, raw `reviewed_by` UUID (substitute `reviewerDisplayName` if displayed).

---

## API Endpoints [BE + FE]

### Partner — Return Submission

_Base path: `/api/v1/redemption/returns`_
_Tag: `Redemption Returns`_

| Method | Path | Request Body | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| `POST` | `/` | `SubmitReturnRequest` | `ReturnDetailResponse` | 201 | `action.redemption.return.request` | `SUBMITTED / REDEMPTION_RETURN` |
| `GET` | `/` | — | `Page<ReturnSummaryResponse>` | 200 | `action.redemption.return.request` | — |
| `GET` | `/{id}` | — | `ReturnDetailResponse` | 200 | `action.redemption.return.request` | — |
| `DELETE` | `/{id}` | — | — | 204 | `action.redemption.return.request` | `CANCELLED / REDEMPTION_RETURN` |

**Query parameters (GET /):** `status` (optional, `ReturnStatus`), `page`, `size` (max 50), `sort` (allowlist: `createdAt`, `amount`)

### Admin — Return Review

_Base path: `/api/v1/redemption/admin/returns`_
_Tag: `Redemption Returns Admin`_

| Method | Path | Request Body | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| `GET` | `/` | — | `Page<ReturnQueueItemResponse>` | 200 | `action.redemption.return.review` | — |
| `GET` | `/{id}` | — | `ReturnDetailResponse` | 200 | `action.redemption.return.review` | — |
| `POST` | `/{id}/approve` | — | `ReturnDetailResponse` | 200 | `action.redemption.return.review` | `APPROVED / REDEMPTION_RETURN` |
| `POST` | `/{id}/reject` | `RejectReturnRequest` | `ReturnDetailResponse` | 200 | `action.redemption.return.review` | `REJECTED / REDEMPTION_RETURN` |
| `POST` | `/{id}/resolve` | `ResolveTimedOutReturnRequest` | `ReturnDetailResponse` | 200 | `action.redemption.return.review` | `COMPLETED or REJECTED / REDEMPTION_RETURN` |

**Query parameters (GET /):** `status` (optional), `startDate`, `endDate` (optional ISO date), `page`, `size` (max 50), `sort` (allowlist: `createdAt`, `amount`)

### Webhook

_Base path: `/api/v1/webhooks/redemption-returns`_
_Tag: `Redemption Return Webhooks`_
_No JWT — excluded from security filter chain; HMAC-SHA256 gated_

| Method | Path | Request Body | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| `POST` | `/{vendor}` | raw JSON (Xoxoday payload) | — | 200 | — (HMAC-SHA256 required) | `COMPLETED or REJECTED / REDEMPTION_RETURN` |

**Valid vendor values:** `xoxoday` only. Other values → 404.

**Error responses (all endpoints):**
- `400` — Validation failure, blank rejection reason, unknown sort field
- `401` — Not authenticated
- `403` — Insufficient permissions; invalid HMAC on webhook
- `404` — Not found or belongs to different tenant (always 404, never 403)
- `409` — Invalid status transition; duplicate active return for same redemption
- `422` — Return eligibility failure (outside window, `isReturnable=false`, XTRM redemption, non-COMPLETED status)
- `429` — Rate limit exceeded

---

## Service Layer [BE]

_Path: `src/main/java/com/tenxengage/app/service/redemption/`_

### ReturnService

| Method | Return Type | Notes |
|---|---|---|
| `submitReturn(SubmitReturnRequest, userId, clientId)` | `ReturnDetailResponse` | `@Transactional` — validates eligibility; throws 422 on ineligibility; throws 409 on duplicate active return; copies amount from redemption |
| `cancelReturn(id, userId, clientId)` | `void` | `@Transactional` — verifies ownership via `findByIdAndClientIdAndPartnerUserId`; validates `PENDING_APPROVAL` state; transitions to `CANCELLED` |
| `approveReturn(id, reviewerId, clientId)` | `ReturnDetailResponse` | `@Transactional` — validates `PENDING_APPROVAL` state; sets `approvedAt`; records `reviewedBy`; fires async `ReturnVendorService.notifyXoxodayReturn()` |
| `rejectReturn(id, rejectionReason, reviewerId, clientId)` | `ReturnDetailResponse` | `@Transactional` — validates `PENDING_APPROVAL` state; transitions to `RETURN_REJECTED`; records `rejectedAt`, `reviewedBy`, `reviewNotes` |
| `processVendorConfirmation(vendorReturnReference, confirmed, failureReason)` | `void` | `@Transactional` — idempotency check first; on `confirmed=true`: calls `WalletMutationDelegate.doReturnCreditInTx()`; transitions to `RETURN_CONFIRMED`; on `confirmed=false`: transitions to `RETURN_REJECTED` |
| `resolveTimedOut(id, resolution, notes, reviewerId, clientId)` | `ReturnDetailResponse` | `@Transactional` — validates `RETURN_TIMED_OUT` state; CONFIRM → calls `doReturnCreditInTx()` + sets `RETURN_CONFIRMED`; REJECT → sets `RETURN_REJECTED` |
| `getPartnerReturns(userId, clientId, filters, pageable)` | `Page<ReturnSummaryResponse>` | `@Transactional(readOnly=true)` |
| `getAdminReturns(clientId, filters, pageable)` | `Page<ReturnQueueItemResponse>` | `@Transactional(readOnly=true)` |
| `getReturnById(id, userId, clientId, isAdmin)` | `ReturnDetailResponse` | `@Transactional(readOnly=true)` — partner: `findByIdAndClientIdAndPartnerUserId`; admin: `findByIdAndClientId` |

### ReturnVendorService

| Method | Return Type | Notes |
|---|---|---|
| `notifyXoxodayReturn(return)` | `String` (vendorReturnReference) | `@Async` — exponential backoff (5 attempts: 1s/2s/4s/8s/32s); persistent failure → DLQ + ops alert; return stays APPROVED |

### ReturnTimeoutScheduler

`@Scheduled(cron = "0 0 * * * *")` (hourly) — queries `APPROVED` returns where `approvedAt < NOW() - 7 days`; transitions each to `RETURN_TIMED_OUT`; publishes `RETURN_TIMED_OUT` event; notifies partner + CLIENT_ADMIN.

**Tenant isolation contract:** Every service method resolves `clientId` from `TenantContext.getCurrentClientId()` — never accepted from the API layer.

**Business rules:**
- Amount is always copied from `RedemptionRequest.amount` at submit time — caller cannot set it
- RETURN_CONFIRMED and RETURN_REJECTED are terminal — no further transitions
- CANCELLED is non-terminal — resubmission for the same redemption is allowed
- `doReturnCreditInTx()` idempotency guard prevents double-credit if webhook and admin resolve race

---

## Workflow / Status Transitions [BE + FE]

```
PENDING_APPROVAL → APPROVED          (action: approveReturn,              trigger: admin with action.redemption.return.review)
PENDING_APPROVAL → CANCELLED         (action: cancelReturn,               trigger: partner who submitted the return)
APPROVED → RETURN_CONFIRMED          (action: processVendorConfirmation,  trigger: Xoxoday webhook — confirmed=true)
APPROVED → RETURN_REJECTED           (action: processVendorConfirmation,  trigger: Xoxoday webhook — confirmed=false)
APPROVED → RETURN_TIMED_OUT          (action: ReturnTimeoutScheduler,     trigger: scheduler after 7 days with no webhook)
RETURN_TIMED_OUT → RETURN_CONFIRMED  (action: resolveTimedOut(CONFIRM),   trigger: admin manual override)
RETURN_TIMED_OUT → RETURN_REJECTED   (action: resolveTimedOut(REJECT),    trigger: admin manual override)
```

**Invalid transitions** (return 409):
- `RETURN_CONFIRMED` → any state — terminal
- `RETURN_REJECTED` → any state — terminal
- `CANCELLED` → any state — terminal
- `APPROVED → PENDING_APPROVAL` — no rollback
- `RETURN_TIMED_OUT → APPROVED` — must use `/resolve`

**Concurrent transition handling:** `@Version` on `RedemptionReturn`. Simultaneous approve + cancel → second writer receives 409 "This return was updated concurrently. Please refresh and try again."

---

## Security Design [BE]

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| `reason` | PII-adjacent (free text) | `@Size(max=500)` + service-layer Jsoup sanitization before persistence; excluded from logs; subject to GDPR anonymization |
| `review_notes` | Internal confidential | `@Size(max=1000)` + Jsoup sanitization; returned to admin role only, excluded from partner-facing responses |
| `amount`, `currency_id` | Confidential | Standard financial field handling |
| `vendor_return_reference` | Internal | Not exposed in partner-facing responses |
| `partner_user_id` | Pseudonymous | UUID — not PII by itself; retained for audit chain |
| `client_id` | Internal | NEVER returned in any API response |

### Rate Limiting

| Endpoint / Operation | Limit | Scope | Reason |
|---|---|---|---|
| `POST /api/v1/redemption/returns` | 5 req/min | Per tenant | Financial mutation — prevent return flood |
| `POST /admin/returns/{id}/approve` + `/reject` | 30 req/min | Per admin user | Prevent rapid status cycling |
| `POST /webhooks/redemption-returns/{vendor}` | 100 req/min | Per source IP | Prevent webhook replay flood |

### OWASP Risks & Mitigations

| Risk | Where | Mitigation |
|---|---|---|
| **Injection (A03)** | `reason`, `review_notes` free-text fields | `@Size` limits; Jsoup service-layer sanitization before persistence |
| **Broken Access Control (A01)** | `GET /{id}`, `DELETE /{id}` partner endpoints | `findByIdAndClientIdAndPartnerUserId` — wrong owner returns 404 |
| **IDOR (A01)** | `redemptionId` in `SubmitReturnRequest` body | Service validates `redemption.clientId == jwt.clientId && redemption.userId == jwt.userId` |
| **Webhook spoofing (A07)** | `POST /webhooks/redemption-returns/xoxoday` | HMAC-SHA256 signature validation (same mechanism as `RedemptionWebhookController`) |
| **Mass Assignment** | All request DTOs | Explicit Java records — only declared fields are bound |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `reason` | `@Size(max=500)`, nullable | 400 with field-level error |
| `rejectionReason` | `@NotBlank`, `@Size(max=1000)` | 400 with field-level error |
| `notes` (resolve) | `@Size(max=1000)`, nullable | 400 with field-level error |
| `resolution` (resolve) | `@NotNull`, `@ValidEnum(ReturnResolution.class)` | 400 — unknown enum value |
| `sort` query param | Allowlist: `["createdAt", "amount"]` | 400 — unknown sort column |
| `size` query param | `@Max(50)` | 400 |

---

## Audit Trail [BE]

_Path: `src/main/java/com/tenxengage/app/audit/` (existing `@Audited` infrastructure)_

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| SUBMIT return | `RedemptionReturn` | Full state snapshot: `redemptionId`, `amount`, `currencyId`, `reason`; `createdBy`, `createdAt`, source IP | `CLIENT_ADMIN` |
| APPROVE return | `RedemptionReturn` | `oldStatus=PENDING_APPROVAL → newStatus=APPROVED`, `reviewedBy`, `reviewedAt` | `CLIENT_ADMIN` |
| REJECT return (admin) | `RedemptionReturn` | `oldStatus=PENDING_APPROVAL → newStatus=RETURN_REJECTED`, `reviewedBy`, `reviewedAt`, `rejectionReason` | `CLIENT_ADMIN` |
| CANCEL return (partner) | `RedemptionReturn` | `oldStatus=PENDING_APPROVAL → newStatus=CANCELLED`, `cancelledBy`, `cancelledAt` | `CLIENT_ADMIN` |
| WEBHOOK CONFIRM | `RedemptionReturn` | `oldStatus=APPROVED → newStatus=RETURN_CONFIRMED`, `vendorReturnReference`, `confirmedAt` | `CLIENT_ADMIN` |
| WEBHOOK REJECT | `RedemptionReturn` | `oldStatus=APPROVED → newStatus=RETURN_REJECTED`, `vendorReturnReference`, `failureReason`, `rejectedAt` | `CLIENT_ADMIN` |
| TIMED OUT (scheduler) | `RedemptionReturn` | `oldStatus=APPROVED → newStatus=RETURN_TIMED_OUT`, `timedOutAt` | `CLIENT_ADMIN` |
| RESOLVE TIMED OUT | `RedemptionReturn` | `oldStatus=RETURN_TIMED_OUT → newStatus=RETURN_CONFIRMED or RETURN_REJECTED`, `reviewedBy`, `notes` | `CLIENT_ADMIN` |

### New Audit Enum Values

| Enum | New Value | Reason |
|---|---|---|
| `AuditResourceType` | `REDEMPTION_RETURN` | New entity type — all return audit records reference this type |

_`AuditAction` requires no new values — `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED`, `EXPIRED` all exist._

### `@Audited` Annotation Details (Non-CRUD Only)

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `POST /admin/returns/{id}/approve` | `APPROVED` | `REDEMPTION_RETURN` | `Approved return request` |
| `POST /admin/returns/{id}/reject` | `REJECTED` | `REDEMPTION_RETURN` | `Rejected return request` |
| `DELETE /returns/{id}` (partner cancel) | `CANCELLED` | `REDEMPTION_RETURN` | `Partner cancelled return request` |
| `POST /admin/returns/{id}/resolve` (CONFIRM) | `COMPLETED` | `REDEMPTION_RETURN` | `Manually confirmed timed-out return` |
| `POST /admin/returns/{id}/resolve` (REJECT) | `REJECTED` | `REDEMPTION_RETURN` | `Manually rejected timed-out return` |
| Webhook confirm | `COMPLETED` | `REDEMPTION_RETURN` | `Return confirmed by Xoxoday` |
| Webhook reject | `REJECTED` | `REDEMPTION_RETURN` | `Return rejected by Xoxoday` |
| Scheduler `RETURN_TIMED_OUT` | `EXPIRED` | `REDEMPTION_RETURN` | `Return approval window expired — no vendor response after 7 days` |

**Audit record retention:** 7 years. Append-only — never soft-deleted.

---

## Observability [BE]

### MDC Fields

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` from JWT | `TenantFilter` (existing) |
| `userId` | User ID from JWT | `JwtAuthenticationFilter` (existing) |
| `featureArea` | `"redemption-returns"` | Set in `ReturnService` |

### Key Log Events

| Event | Level | `step` value | Key Fields | Purpose |
|---|---|---|---|---|
| Return submitted | INFO | `return_submitted` | `returnId`, `redemptionId`, `userId` | Business event |
| Return approved | INFO | `return_approved` | `returnId`, `reviewerId` | Approval audit |
| Return rejected (admin) | INFO | `return_rejected_admin` | `returnId`, `reviewerId` | Rejection audit |
| Return cancelled | INFO | `return_cancelled` | `returnId`, `userId` | Cancellation audit |
| Xoxoday API call initiated | INFO | `return_vendor_notify_start` | `returnId`, `attempt` | Vendor call tracking |
| Xoxoday API call success | INFO | `return_vendor_notify_success` | `returnId`, `vendorReturnReference` | Vendor confirmation |
| Xoxoday API call retry | WARN | `return_vendor_notify_retry` | `returnId`, `attempt`, `error` | Retry tracking |
| Xoxoday API persistent failure → DLQ | ERROR | `return_vendor_notify_failed_dlq` | `returnId`, `attempts` | Ops alert trigger |
| Webhook received | INFO | `return_webhook_received` | `vendor`, `idempotencyKey` | Webhook audit |
| Webhook duplicate | INFO | `return_webhook_duplicate` | `vendor`, `idempotencyKey` | Deduplication |
| Return timed out | WARN | `return_timed_out` | `returnId`, `approvedAt`, `timedOutAt` | Scheduler event |
| Eligibility rejected | WARN | `return_eligibility_rejected` | `redemptionId`, `reason` | Track ineligible attempts |

### Metrics

| Metric Name | Type | Labels | Purpose |
|---|---|---|---|
| `returns.submitted.total` | Counter | `tenantId`, `currencyId` | Volume tracking |
| `returns.status_transition.total` | Counter | `fromStatus`, `toStatus` | Lifecycle funnel |
| `returns.vendor_call.duration_ms` | Histogram | `vendor`, `attempt` | Xoxoday API latency |
| `returns.timed_out.total` | Counter | `tenantId` | Timeout frequency monitoring |

---

## Domain Events [BE]

_Topic: `return-events` (new; 3 partitions, 1 replica)_
_Producer: `ReturnEventProducer` (`com.tenxengage.app.service`)_
_Event record: `ReturnEvent` (`com.tenxengage.app.event`)_
_Partition key: `clientId`_

### Events Produced

| Topic | Event Type | Trigger | Additional Payload Fields |
|---|---|---|---|
| `return-events` | `RETURN_REQUESTED` | Return submitted (`PENDING_APPROVAL`) | — |
| `return-events` | `RETURN_APPROVED` | Admin approves | `reviewedBy (UUID)` |
| `return-events` | `RETURN_CONFIRMED` | Xoxoday webhook confirms | `vendorReturnReference (String)` |
| `return-events` | `RETURN_REJECTED` | Admin rejects OR Xoxoday webhook rejects | `reviewedBy (UUID, nullable)` |
| `return-events` | `RETURN_CANCELLED` | Partner cancels | — |
| `return-events` | `RETURN_TIMED_OUT` | Scheduler fires at T+7d | — |

**Base payload schema:**
```json
{
  "eventId": "uuid",
  "eventType": "RETURN_REQUESTED",
  "occurredAt": "2026-06-12T10:00:00Z",
  "clientId": "uuid",
  "returnId": "uuid",
  "redemptionId": "uuid",
  "amount": 150.00,
  "currencyId": "points",
  "status": "PENDING_APPROVAL"
}
```

_No PII in payloads — no `reason`, no `reviewNotes`, no `partnerUserId`. Reference IDs only._
_Idempotency contract for consumers: deduplicate on `eventId` UUID._

---

## Frontend Specification [FE]

_TypeScript types live in `../tenxengage-contracts/` — copy from there, do not hand-write. Full FE file paths: see `technical.md`._

### Pages

| Page | Route | Layout | Permission | Notes |
|---|---|---|---|---|
| F-05 partner history (extended) | `/redemptions/history` (existing) | Partner layout | `action.redemption.return.request` | New "My Returns" tab added to existing history page shell (tabs-and-navigation pattern) |
| F-04 admin approval queue (extended) | `/admin/redemptions/approval-queue` (existing) | Admin layout | `action.redemption.return.review` | New "Returns" tab added to existing approval queue page, completing FR-04.6 stub |

_No new standalone pages — returns are embedded in existing F-04 and F-05 page shells._

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `RequestReturnDialog` | `redemptionId: UUID, amount: BigDecimal, currencyId: string, catalogItemName: string, onSuccess: () => void` | `useSubmitReturn()` mutation | **Sections:** Header — "Request Return" title, item name + amount display; Body — optional reason textarea (max 500 chars, character counter shown at 400/500); Footer — Cancel (left), Submit primary (right). **Interactions:** submit on Enter or button click; loading spinner on submit; inline error if API fails (dialog stays open — not a toast); success closes dialog and invalidates F-05 history. **A11y + responsive:** `DialogDescription` required per dialogs-and-modals pattern; focus traps; full-width on mobile. |
| `MyReturnsTab` | `userId: UUID` | `useMyReturns()` hook | **Sections:** Filter row — status filter popover + date range; Table — columns: Catalog Item, Amount, Status badge, Submitted, Actions. **Interactions:** row click → opens `ReturnDetailSheet`; "Cancel" row action (PENDING_APPROVAL rows only) → `AlertDialog` confirmation → `useCancelReturn()` mutation. Loading: skeleton table (5 rows). Empty: "You have no return requests yet." Error: inline retry. **A11y + responsive:** per frontend PROJECT-CONTEXT.md. |
| `ReturnDetailSheet` | `returnId: UUID, role: 'partner' \| 'admin'` | `useReturn(id)` hook | `Sheet` (right-side panel). **Sections:** Header — catalog item name, status badge, amount + currency; Timeline — status history: submitted → approved → confirmed/rejected/timed-out timestamps with labels; Return info — reason (if present), review notes (admin role only); Admin actions — Approve / Reject buttons gated by `PENDING_APPROVAL` + `action.redemption.return.review`; Resolve button gated by `RETURN_TIMED_OUT` + review permission. **Interactions:** Approve → `AlertDialog` confirm → `useApproveReturn()`; Reject → `RejectReturnDialog`; Resolve → `ResolveTimedOutDialog`. **A11y + responsive:** per frontend PROJECT-CONTEXT.md. |
| `RejectReturnDialog` | `returnId: UUID, onSuccess: () => void` | `useRejectReturn()` mutation | `AlertDialog` variant (destructive). **Sections:** Header — "Reject Return Request?"; Body — required rejection reason textarea (max 1000 chars); Footer — Cancel (left), Reject in destructive red (right). No escape dismissal while reason is empty. |
| `ResolveTimedOutDialog` | `returnId: UUID, onSuccess: () => void` | `useResolveTimedOutReturn()` mutation | `Dialog` variant. **Sections:** Header — "Resolve Timed-Out Return"; Body — radio group: "Confirm return (credit wallet)" / "Reject return (no credit)"; optional notes textarea (max 1000 chars); Footer — Cancel, Resolve primary. |
| `ReturnsApprovalTab` | `clientId: UUID` | `useAdminReturns()` hook | **Sections:** Filter row — status dropdown, date range pickers; Table — Catalog Item, Partner, Company, Amount, Status badge, Submitted, Actions. Actions: kebab dropdown — "View Details" (all), "Approve" (PENDING_APPROVAL), "Reject" (PENDING_APPROVAL), "Resolve" (RETURN_TIMED_OUT). **Interactions:** "View Details" → `ReturnDetailSheet`; "Approve" → `AlertDialog` → `useApproveReturn()`; "Reject" → `RejectReturnDialog`; "Resolve" → `ResolveTimedOutDialog`. Loading: skeleton table. Empty: "No return requests to review." Error: inline retry. **A11y + responsive:** per frontend PROJECT-CONTEXT.md. |

### Forms

| Form | Fields | Validation | Submit Action |
|---|---|---|---|
| Return submission (in `RequestReturnDialog`) | `reason (string, optional)` | `z.string().max(500).optional()` | `POST /api/v1/redemption/returns` |
| Rejection (in `RejectReturnDialog`) | `rejectionReason (string, required)` | `z.string().min(1).max(1000)` | `POST /api/v1/redemption/admin/returns/{id}/reject` |
| Resolve (in `ResolveTimedOutDialog`) | `resolution ('CONFIRM' \| 'REJECT')`, `notes (string, optional)` | `z.enum(['CONFIRM','REJECT'])`, `z.string().max(1000).optional()` | `POST /api/v1/redemption/admin/returns/{id}/resolve` |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `useMyReturns(filters)` | `['my-returns', userId, filters]` | `GET /api/v1/redemption/returns` | 2 min | On `useSubmitReturn`, `useCancelReturn` mutations |
| `useReturn(id)` | `['return', id]` | `GET /api/v1/redemption/returns/{id}` (partner) or `/admin/returns/{id}` (admin) | 2 min | On cancel, approve, reject, resolve mutations for this `id` |
| `useAdminReturns(filters)` | `['admin-returns', clientId, filters]` | `GET /api/v1/redemption/admin/returns` | 2 min | On approve, reject, resolve mutations |

_StaleTime is 2 min (not 5 min default) because return status can change via async vendor webhook — shorter staleness reduces stale-badge display._

---

## Data Retention & Compliance [BE]

### Soft Delete vs Hard Delete

**Decision: Soft delete** (`deleted = BOOLEAN` flag on `RedemptionReturn`).
- **Why**: Preserves audit trail for financial transactions; required for GDPR erasure via anonymization rather than deletion.
- **Hard delete**: N/A — no child entities.

### PII Handling

| Field | Entity | PII Type | GDPR Treatment |
|---|---|---|---|
| `reason` | `RedemptionReturn` | Free text (may contain personal info) | On data-subject deletion request: NULL out `reason`; preserve record shell for 7-year audit retention |
| `partner_user_id` | `RedemptionReturn` | Pseudonymous UUID | Retain — UUID not PII by itself; required for audit chain integrity |

### Data Retention Periods

| Data Type | Retention Period | Justification |
|---|---|---|
| `redemption_returns` records (active + soft-deleted) | 7 years | Financial audit / legal hold |
| Audit log entries | 7 years | Compliance requirement |
| `return-events` Kafka events | Per broker retention policy | Operational events — not primary audit record |
| PII fields after erasure request | Immediate anonymization (NULL) | GDPR Article 17 |

### Data Export (GDPR Article 20)

- `redemption_returns.reason` — include in subject export when non-null
- `redemption_returns` rows linked to the subject's `partner_user_id` — include `id`, `amount`, `status`, `createdAt`

---

## Edge Cases [BE + FE]

1. **Return window expired** — Partner tries to return a redemption past the return window: BE returns 422 "Return window for this redemption has expired"; FE hides "Request Return" for such entries (driven by `isReturnEligible` flag).
2. **Non-returnable item** — `isReturnable = false` on catalog item: BE returns 422 "This item is not eligible for return"; FE never shows "Request Return" for such items.
3. **XTRM cash redemption** — Partner tries to return a XTRM/cash redemption: BE returns 422 "Cash redemptions cannot be returned"; FE never shows "Request Return" for XTRM-category entries.
4. **Duplicate active return** — Second return submitted for a redemption with an existing `PENDING_APPROVAL` or `APPROVED` return: BE returns 409 "A return request is already active for this redemption"; FE hides "Request Return" for such redemptions.
5. **Partially-used gift card** — Xoxoday confirms a return on a partially-used card: TenXEngage issues the full original redemption amount as `RETURN_CREDIT`; partial-value reconciliation is Xoxoday's responsibility.
6. **RETURN_TIMED_OUT admin resolve — wallet credit** — Admin resolves a TIMED_OUT return to RETURN_CONFIRMED: `WalletMutationDelegate.doReturnCreditInTx()` called at resolve time (same path as webhook confirm); idempotency guard prevents double-credit if webhook arrives late.
7. **Webhook after RETURN_TIMED_OUT or RETURN_CONFIRMED** — Xoxoday sends a webhook for a return already in a terminal or TIMED_OUT state: idempotency check returns 200 with no state change; logged at WARN.
8. **Xoxoday unavailable at approval time** — `ReturnVendorService` exhausts all 5 retries: DLQ + ops alert; return stays APPROVED; RETURN_TIMED_OUT scheduler fires at T+7d as safety net.
9. **Cross-tenant access** — Any request where resolved return's `client_id ≠ TenantContext.getCurrentClientId()`: returns 404.
10. **Concurrent approve + cancel** — Partner cancels while admin approves simultaneously: `@Version` optimistic lock; second operation receives 409 "This return was updated concurrently. Please refresh."
11. **Empty states** — `MyReturnsTab`: "You have no return requests yet." `ReturnsApprovalTab`: "No return requests to review."
12. **Pagination edge case** — `page=999` with 2 pages of data: BE returns `200` with `content: []`.
13. **Status badge coverage** — `ReturnStatusBadge` must render all 6 `ReturnStatus` values: `PENDING_APPROVAL` (muted yellow), `APPROVED` (blue), `RETURN_CONFIRMED` (green), `RETURN_REJECTED` (red), `CANCELLED` (muted gray), `RETURN_TIMED_OUT` (orange warning).

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, `@WebMvcTest`, Vitest, E2E Playwright) — in each `stories/US-NN-*.md` file
- **Cross-story integration tests** — in [test-plan.md](test-plan.md)

_Key integration test classes (from step 10): `RedemptionReturnSubmitIT`, `RedemptionReturnLifecycleIT`, `RedemptionReturnPermissionsIT`, `RedemptionReturnTenantIsolationIT`, `RedemptionReturnIdempotencyIT`, `RedemptionReturnContractIT`._
_New fixture file: `RedemptionReturnFixtures.java`. Reuses: `RedemptionRequestFixtures`, `PartnerFixtures`, `ClientFixtures`, `LedgerEntryFixtures`, `RewardWalletFixtures`._

---

## Modified Existing Endpoints [BE + FE]

| Endpoint | Change | Reason | Breaking? |
|---|---|---|---|
| F-05 `GET /api/v1/redemption/requests/personal` + `/company` | Add `isReturnEligible (boolean)` to `RedemptionRequestResponse` | Enables FE to show/hide "Request Return" button per transaction entry without a separate call | No — additive field |
| F-04 Approval Queue FE page | New "Returns" tab (`ReturnsApprovalTab`) pointing to `GET /api/v1/redemption/admin/returns` | Completes FR-04.6 stub — returns now have a real admin queue view | No — new tab addition |

---

## Planning Seeds (from feature brief)

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Submit a return request | Partner initiates a return from their transaction history for an eligible completed non-cash redemption | workflow | F-05.S-01 |
| S-02 | Review and decide on return requests | Client Admin / Approver reviews pending returns and approves or rejects from the approval queue | admin | S-01, F-04.S-01 |
| S-03 | Notify Xoxoday of approved return | Platform sends the return notification to Xoxoday after admin approval and waits for vendor confirmation | integration | S-02 |
| S-04 | Credit wallet on Xoxoday confirmation | When Xoxoday confirms, the partner's balance is restored and they receive notification | workflow | S-03 |
| S-05 | Handle return rejection paths | Both admin rejections and vendor rejections close the return without wallet credit, with appropriate notification | workflow | S-02 |

---

## Out of Scope

- Partial returns — Phase 2 per BRD
- Cash (XTRM) redemption returns — not supported under any circumstances in this platform
- Return analytics dashboard — Phase 2
- Bulk return approval — Phase 2
- Return SLA breach monitoring — Phase 2
- Reward balance expiration (affects returned balance) — Phase 2 per roadmap

---

## Verification Steps

### Backend Verification
1. `./gradlew bootRun` — app starts; Flyway V25 and V26 apply without errors
2. `./gradlew test` — all new and existing tests pass
3. Security: cross-tenant `GET /redemption/returns/{id}` → 404; partner cancel another partner's return → 404; approve without permission → 403; webhook with invalid HMAC → 403
4. State machine: `DELETE` on `RETURN_CONFIRMED` return → 409; submit second return for same active redemption → 409; submit for XTRM redemption → 422
5. Observability: tail logs on `POST /api/v1/redemption/returns`; verify `step=return_submitted`, `tenantId`, `userId` appear

### Frontend Verification
1. `npm run build` — no TypeScript errors
2. `npm run test` — Vitest passes; `npx playwright test` — E2E passes
3. UI: "Request Return" shows only on eligible history entries; dialog validates reason length; admin Returns tab renders on F-04 page
4. All 6 `ReturnStatus` values render correct badges

---

### File: features/redemption-returns/technical.md

> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V25__create_redemption_returns_table.sql

```sql
-- ============================================================
-- F-06 Non-Cash Returns: RedemptionReturn entity table
-- ============================================================
CREATE TYPE return_status AS ENUM (
    'PENDING_APPROVAL',
    'APPROVED',
    'RETURN_CONFIRMED',
    'RETURN_REJECTED',
    'CANCELLED',
    'RETURN_TIMED_OUT'
);

CREATE TABLE redemption_returns (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id               UUID          NOT NULL REFERENCES clients(id),
    redemption_id           UUID          NOT NULL REFERENCES redemption_requests(id),
    partner_user_id         UUID          NOT NULL,
    status                  return_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    reason                  TEXT          NULL,
    reviewed_by             UUID          NULL,
    reviewed_at             TIMESTAMPTZ   NULL,
    review_notes            TEXT          NULL,
    vendor_return_reference VARCHAR(255)  NULL,
    amount                  NUMERIC(19,4) NOT NULL,
    currency_id             VARCHAR(50)   NOT NULL,
    approved_at             TIMESTAMPTZ   NULL,
    timed_out_at            TIMESTAMPTZ   NULL,
    confirmed_at            TIMESTAMPTZ   NULL,
    rejected_at             TIMESTAMPTZ   NULL,
    cancelled_at            TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted                 BOOLEAN       NOT NULL DEFAULT false,
    version                 BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_redemption_returns_client_id     ON redemption_returns(client_id);
CREATE INDEX idx_redemption_returns_client_status ON redemption_returns(client_id, status);
CREATE INDEX idx_redemption_returns_redemption_id ON redemption_returns(redemption_id);
CREATE INDEX idx_redemption_returns_partner_user  ON redemption_returns(client_id, partner_user_id);
CREATE INDEX idx_redemption_returns_vendor_ref    ON redemption_returns(vendor_return_reference)
    WHERE vendor_return_reference IS NOT NULL;
```

### V26__seed_redemption_return_permissions.sql

```sql
-- ============================================================
-- F-06 Non-Cash Returns: Permission catalog
-- Note: module.redemption_store already seeded in F-01 V8.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.return.request',
   'Request Redemption Return',
   'Submit, view, and cancel return requests for completed non-cash redemptions',
   'REDEMPTION_ACTIONS', 'ACTION', 410, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(),
   'action.redemption.return.review',
   'Review Return Requests',
   'View, approve, reject, and resolve return requests in the admin queue',
   'REDEMPTION_ACTIONS', 'ACTION', 411, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- F-06: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (
    gen_random_uuid(),
    'redemption_non_cash_returns',
    'Enable non-cash gift-card / prepaid return requests for partners',
    false, true, true, NOW(), NOW(), 'REDEMPTION'
)
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- PARTNER_ADMIN → action.redemption.return.request
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN ('action.redemption.return.request')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- PARTNER_SELLER → action.redemption.return.request
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN ('action.redemption.return.request')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- CLIENT_ADMIN → action.redemption.return.review
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.return.review')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- ACTIVITY_APPROVER → action.redemption.return.review
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'ACTIVITY_APPROVER'
  AND p.permission_key IN ('action.redemption.return.review')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.return.request',
    'action.redemption.return.review'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

---

## Package Layout [BE]

_All paths relative to `../tenxengage-backend/src/main/java/com/tenxengage/app/`_

| Responsibility | File Path |
|---|---|
| `RedemptionReturn` entity | `entity/RedemptionReturn.java` (extends BaseEntity, implements TenantAware) |
| `ReturnStatus` enum | `entity/enums/ReturnStatus.java` |
| `ReturnResolution` enum | `entity/enums/ReturnResolution.java` |
| `RedemptionReturn` repository | `repository/RedemptionReturnRepository.java` |
| Return business service | `service/redemption/ReturnService.java` |
| Xoxoday vendor call service | `service/redemption/ReturnVendorService.java` |
| Timeout scheduler | `service/redemption/ReturnTimeoutScheduler.java` |
| Partner return controller | `controller/redemption/ReturnController.java` |
| Admin return controller | `controller/redemption/ReturnAdminController.java` |
| Return webhook controller | `controller/ReturnWebhookController.java` |
| Kafka event record | `event/ReturnEvent.java` |
| Kafka event producer | `service/ReturnEventProducer.java` |
| Submit return request DTO | `dto/request/redemption/SubmitReturnRequest.java` |
| Reject return request DTO | `dto/request/redemption/RejectReturnRequest.java` |
| Resolve TIMED_OUT request DTO | `dto/request/redemption/ResolveTimedOutReturnRequest.java` |
| Partner list response DTO | `dto/response/redemption/ReturnSummaryResponse.java` |
| Detail response DTO (partner + admin) | `dto/response/redemption/ReturnDetailResponse.java` |
| Admin queue item response DTO | `dto/response/redemption/ReturnQueueItemResponse.java` |
| `AuditResourceType` update | `entity/enums/AuditResourceType.java` — add `REDEMPTION_RETURN` |
| `KafkaConfig` update | `config/KafkaConfig.java` — add `return-events` topic bean (3 partitions, 1 replica) |
| Flyway schema migration | `resources/db/migration/V25__create_redemption_returns_table.sql` |
| Flyway permission seed | `resources/db/migration/V26__seed_redemption_return_permissions.sql` |
| Service unit test | `test/.../service/redemption/ReturnServiceTest.java` |
| Partner controller test | `test/.../controller/redemption/ReturnControllerTest.java` |
| Admin controller test | `test/.../controller/redemption/ReturnAdminControllerTest.java` |
| Fixtures | `test/.../testdata/RedemptionReturnFixtures.java` (builder-return pattern; mandatory) |

---

## Repository Queries [BE]

_`RedemptionReturnRepository extends JpaRepository<RedemptionReturn, UUID>`_

All methods include `clientId` for tenant isolation.

- `findByIdAndClientId(UUID id, UUID clientId)` → `Optional<RedemptionReturn>` — admin single fetch
- `findByIdAndClientIdAndPartnerUserId(UUID id, UUID clientId, UUID partnerUserId)` → `Optional<RedemptionReturn>` — partner ownership check
- `findByClientIdAndPartnerUserIdAndDeletedFalse(UUID clientId, UUID partnerUserId, Pageable pageable)` → `Page<RedemptionReturn>` — partner list
- `findByClientIdAndDeletedFalse(UUID clientId, Pageable pageable)` → `Page<RedemptionReturn>` — admin list all
- `existsByRedemptionIdAndClientIdAndStatusNotIn(UUID redemptionId, UUID clientId, List<ReturnStatus> excludedStatuses)` → `boolean` — duplicate active return check (exclude CANCELLED and RETURN_REJECTED to allow resubmission)
- `findByVendorReturnReference(String vendorReturnReference)` → `Optional<RedemptionReturn>` — webhook idempotency lookup
- `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT r FROM RedemptionReturn r WHERE r.id = :id") findByIdForUpdate(@Param("id") UUID id)` → `Optional<RedemptionReturn>` — concurrent state transition guard
- `@Query("SELECT r FROM RedemptionReturn r WHERE r.clientId = :clientId AND r.status = 'APPROVED' AND r.approvedAt < :cutoff AND r.deleted = false") findApprovedTimedOut(@Param("clientId") UUID clientId, @Param("cutoff") Instant cutoff, Pageable pageable)` → `Page<RedemptionReturn>` — scheduler query (paginated to avoid bulk timeout)

---

## Package Layout [FE]

_All paths relative to `../tenxengage-frontend/src/`_

| Responsibility | File Path |
|---|---|
| TypeScript types | `types/redemption-returns.types.ts` (copy from contracts repo — do not hand-write) |
| API service | `services/redemption-returns.service.ts` |
| Partner list hook | `hooks/useMyReturns.ts` |
| Return detail hook | `hooks/useReturn.ts` |
| Admin list hook | `hooks/useAdminReturns.ts` |
| Submit mutation hook | `hooks/useSubmitReturn.ts` |
| Cancel mutation hook | `hooks/useCancelReturn.ts` |
| Approve mutation hook | `hooks/useApproveReturn.ts` |
| Reject mutation hook | `hooks/useRejectReturn.ts` |
| Resolve mutation hook | `hooks/useResolveTimedOutReturn.ts` |
| Submit dialog | `components/redemption-returns/RequestReturnDialog.tsx` |
| Partner returns tab | `components/redemption-returns/MyReturnsTab.tsx` |
| Return detail sheet | `components/redemption-returns/ReturnDetailSheet.tsx` |
| Reject dialog | `components/redemption-returns/RejectReturnDialog.tsx` |
| Resolve TIMED_OUT dialog | `components/redemption-returns/ResolveTimedOutDialog.tsx` |
| Admin approval tab | `components/redemption-returns/ReturnsApprovalTab.tsx` |
| Status badge | `components/redemption-returns/ReturnStatusBadge.tsx` |
| Component tests | `components/redemption-returns/__tests__/RequestReturnDialog.test.tsx` |
| Component tests | `components/redemption-returns/__tests__/ReturnsApprovalTab.test.tsx` |
| Component tests | `components/redemption-returns/__tests__/ReturnDetailSheet.test.tsx` |

**Route changes:** No new entries in `App.tsx` — returns are embedded in existing F-04 and F-05 page routes.

**F-05 integration point:** add `MyReturnsTab` as a new tab in the existing redemption history page shell (alongside existing "My Redemptions" tab).

**F-04 integration point:** add `ReturnsApprovalTab` as a new tab in the existing admin approval queue page shell (alongside existing "Redemptions" tab).

---

## Hook Specs [FE]

### `useMyReturns` (partner list hook)

```ts
queryKey: ['my-returns', userId, { status, page, size, sort }]
staleTime: 2 * 60 * 1000   // 2 min — async webhook can change status
```

Invalidate on: `useSubmitReturn`, `useCancelReturn` mutations.

### `useReturn(id)` (detail hook)

```ts
queryKey: ['return', id]
staleTime: 2 * 60 * 1000
```

Invalidate on: `useCancelReturn`, `useApproveReturn`, `useRejectReturn`, `useResolveTimedOutReturn` mutations for this `id`.

### `useAdminReturns` (admin list hook)

```ts
queryKey: ['admin-returns', clientId, { status, startDate, endDate, page, size, sort }]
staleTime: 2 * 60 * 1000
```

Invalidate on: `useApproveReturn`, `useRejectReturn`, `useResolveTimedOutReturn` mutations.

---

## Audit Annotations [BE]

**New enum value — add to `entity/enums/AuditResourceType.java`:**
- `REDEMPTION_RETURN`

No new `AuditAction` values needed.

| Operation | `action` value | `resourceType` value | `description` |
|---|---|---|---|
| `POST /admin/returns/{id}/approve` | `APPROVED` | `REDEMPTION_RETURN` | `Approved return request` |
| `POST /admin/returns/{id}/reject` | `REJECTED` | `REDEMPTION_RETURN` | `Rejected return request` |
| `DELETE /returns/{id}` (partner cancel) | `CANCELLED` | `REDEMPTION_RETURN` | `Partner cancelled return request` |
| `POST /admin/returns/{id}/resolve` (CONFIRM) | `COMPLETED` | `REDEMPTION_RETURN` | `Manually confirmed timed-out return` |
| `POST /admin/returns/{id}/resolve` (REJECT) | `REJECTED` | `REDEMPTION_RETURN` | `Manually rejected timed-out return` |
| Webhook confirm | `COMPLETED` | `REDEMPTION_RETURN` | `Return confirmed by Xoxoday` |
| Webhook reject | `REJECTED` | `REDEMPTION_RETURN` | `Return rejected by Xoxoday` |
| Scheduler `RETURN_TIMED_OUT` | `EXPIRED` | `REDEMPTION_RETURN` | `Return approval window expired — no vendor response after 7 days` |
