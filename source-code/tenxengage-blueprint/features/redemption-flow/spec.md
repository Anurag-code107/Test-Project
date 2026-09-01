---
slug: redemption-flow
name: Redemption Flow
status: reviewed
format: story-sliced
roadmap: redemption-store
domain: null
builder_type: null
created: 2026-05-21
contract: null
---

> **Reviewed**: 2026-05-21

# Feature: Redemption Flow

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

The Redemption Flow enables PARTNER_SELLER and PARTNER_ADMIN to convert earned rewards into tangible value by selecting a catalog item and submitting a redemption from their personal or company wallet. The platform immediately reserves the redemption amount, routes cash requests to XTRM and non-cash orders to Xoxoday transparently, and finalizes the ledger when the vendor confirms fulfillment or releases the reservation on failure. Three processing modes (Instant, Batch, Approval Required) are supported; the partner sees a single unified experience regardless of mode or vendor. Idempotent vendor webhook processing, exponential-backoff retry, and a dead-letter queue guard against double-debit and vendor outages.

### Naming reconciliation (digest-annex.md advisory → spec decision)

| BRD / digest-annex name | Spec name | Decision |
|---|---|---|
| `RedemptionTransaction` | `RedemptionRequest` | **Overridden** — "Request" is more precise: this entity tracks the partner's submission lifecycle, not a completed financial transaction. `RedemptionTransaction` conflicts with the existing `RewardTransaction` entity naming pattern and implies finality. |
| `redemption_requested` (snake_case event) | `REDEMPTION_REQUESTED` (UPPER_SNAKE_CASE) | **Overridden** — matches Kafka event naming convention used across the platform. |
| `CurrencyType` enum values CASH/POINTS/CREDITS/TICKETS | `currencyId` string field | **Overridden** — the platform `CurrencyType` enum has `MONETARY/NON_MONETARY` only; per-currency identification uses string IDs ("cash", "points", etc.) consistent with `RewardWallet.currencyId` and `RedemptionCatalogItem.currencyId`. |

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-03.1 | PARTNER_SELLER can submit a redemption from their personal wallet by selecting a catalog item and specifying an amount; the submission immediately reserves the amount from available balance regardless of processing mode |
| FR-03.2 | PARTNER_ADMIN can submit a redemption from the company wallet by selecting a catalog item; the amount is reserved from the company wallet on submission |
| FR-03.3 | The platform routes cash redemptions (category=CASH) to XTRM and non-cash redemptions (category=NON_CASH) to Xoxoday automatically; the partner sees a single unified experience with no vendor branding |
| FR-03.4 | For INSTANT processing mode, the redemption request is sent to the vendor immediately after submission (and after any required approval gate); the ledger DEBIT is recorded when the vendor webhook confirms completion |
| FR-03.5 | For BATCH processing mode, the redemption is queued (status=RESERVED) and processed on the client-configured `batchCadence` schedule; the partner is shown the next scheduled processing date at submission time |
| FR-03.6 | For APPROVAL_REQUIRED processing mode, the redemption is held in PENDING_APPROVAL status and is not sent to the vendor until a Client Admin or Approver approves it (approval actions covered in F-04); the balance remains reserved during the approval window |
| FR-03.7 | When a vendor webhook confirms fulfillment completion, the platform records a permanent DEBIT ledger entry, transitions the redemption to COMPLETED status, and notifies the partner via the notification framework |
| FR-03.8 | When a vendor webhook signals failure or cancellation, the platform records a RELEASE ledger entry restoring the full reserved amount to available balance, transitions the redemption to FAILED or CANCELLED status, and notifies the partner |
| FR-03.9 | All inbound vendor webhooks are authenticated via HMAC-SHA256 using vendor-specific signing secrets; unauthenticated or malformed webhook requests are rejected with 401; webhook processing is idempotent (duplicate delivery → log and discard) |
| FR-03.10 | Transient vendor API failures trigger automatic retry with exponential backoff; webhook events that cannot be processed after all retries are routed to a dead-letter queue |
| FR-03.11 | The redemption confirmation screen displays the estimated payout timeline for the selected processing mode; for BATCH mode, the next scheduled run date is shown |

---

## Planning seeds (from feature brief)

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Submit personal wallet redemption | Partner Seller selects a catalog item and submits a redemption with immediate balance reservation | workflow | F-02.S-04 |
| S-02 | Submit company wallet redemption | Partner Admin redeems from the company wallet with the same flow as personal wallet | workflow | S-01 |
| S-03 | Route cash redemptions to XTRM | Cash redemption requests are submitted to XTRM with partner identity; ledger finalized on webhook | integration | S-01 |
| S-04 | Route non-cash redemptions to Xoxoday | Non-cash orders are placed with Xoxoday; ledger finalized on fulfillment webhook | integration | S-01 |
| S-05 | Queue and process batch redemptions | Redemptions in batch mode are queued and processed on the client-configured cadence | workflow | S-01 |
| S-06 | Process vendor webhooks idempotently | XTRM and Xoxoday status updates authenticate and apply to ledger and transaction state without double-processing | integration | S-03, S-04 |
| S-07 | Notify partners of redemption lifecycle | Partners receive timely notifications at submission, completion, and failure | workflow | S-01 |

---

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (reads)** | P95 < 300ms | List and detail endpoints |
| **Response time (writes)** | P95 < 500ms | Submission endpoints (excludes vendor call latency) |
| **Peak concurrent users** | No application-level limit | System scales horizontally via infrastructure auto-scaling (Pushpendra, 2026-05-21) |
| **Max page size** | 50 items | Hard cap on `size` query param |
| **Availability** | 99.9% | Core user-facing flow |
| **Data sensitivity** | CONFIDENTIAL | Financial transaction records; no PII stored directly in redemption entities |
| **Compliance** | None (v1) | XTRM handles KYC/AML/OFAC natively; no additional platform compliance obligation |
| **Audit retention** | Indefinite | No deletion policy on redemption records (Pushpendra, 2026-05-21) |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec` (status must be `reviewed`)
- [ ] Contracts generated via `/generate-contracts` in the backend repo
- [ ] Next Flyway migration number confirmed (current latest: V15)
- [ ] F-01 Wallet & Ledger Foundation deployed (wallet, ledger, balance reservation)
- [ ] F-02 Redemption Catalog deployed (catalog items, tenant config, currency-aware browse)
- [ ] XTRM API credentials configured (BLOCKED — TransferFund API broken; S-03 and S-06 parked)
- [ ] Xoxoday API credentials configured (BLOCKED — agreement not signed; S-04 and S-06 parked)

---

## New Enums [BE]

| Enum Class | Values | Notes |
|---|---|---|
| `RedemptionStatus.java` | `PENDING_APPROVAL, RESERVED, PROCESSING, COMPLETED, FAILED, CANCELLED` | Lifecycle status of a redemption request |
| `WebhookStatus.java` | `RECEIVED, PROCESSED, DUPLICATE, FAILED, DEAD_LETTERED` | Processing state of an inbound vendor webhook event |

_Path: `src/main/java/com/tenxengage/app/entity/enums/`_

Existing enums used (no changes): `RedemptionCategory`, `RedemptionProcessingMode`, `WalletType`, `LedgerEntryType` (RESERVE, DEBIT, RELEASE all exist).

> **Naming note:** `CurrencyType.java` (values: `MONETARY / NON_MONETARY`) is the platform's high-level currency bucket enum and is NOT used here. Currency identification in the redemption domain uses the string `currencyId` (e.g., `"cash"`, `"points"`, `"credits"`, `"tickets"`) — consistent with `RewardWallet.currencyId`, `RedemptionCatalogItem.currencyId`, and `RewardTransaction.currencyId`.

---

## Data Model / Entities [BE]

### Entity-shape decisions

| Entity | Shape | Source |
|---|---|---|
| `RedemptionRequest` | Hardcoded JPA entity | This spec |
| `RedemptionWebhookEvent` | Hardcoded JPA entity | This spec |

### RedemptionRequest (table: `redemption_requests`)

_Path: `src/main/java/com/tenxengage/app/entity/`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_
_Carries `@Version` on `version` field for optimistic locking_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited from BaseEntity |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation — never expose in API responses |
| `wallet_id` | `UUID` | NOT NULL, FK → reward_wallets | Wallet funds were reserved from |
| `user_id` | `UUID` | NOT NULL, FK → users | Partner who submitted the redemption |
| `catalog_item_id` | `UUID` | NOT NULL, FK → redemption_catalog_items | Selected catalog item |
| `amount` | `NUMERIC(19,4)` | NOT NULL, CHECK (amount > 0) | Redemption amount in the wallet's currency |
| `currency_id` | `VARCHAR(50)` | NOT NULL | String currency ID — "cash", "points", "credits", "tickets" — matches `RewardWallet.currencyId` and `RedemptionCatalogItem.currencyId` |
| `wallet_type` | `VARCHAR(20)` | NOT NULL | WalletType enum (INDIVIDUAL/COMPANY) |
| `status` | `VARCHAR(30)` | NOT NULL | RedemptionStatus enum — state machine field |
| `processing_mode` | `VARCHAR(30)` | NOT NULL | RedemptionProcessingMode enum — captured at submission time |
| `category` | `VARCHAR(20)` | NOT NULL | RedemptionCategory enum (CASH/NON_CASH) — determines vendor routing |
| `vendor_reference_id` | `VARCHAR(255)` | nullable | Vendor's transaction ID — set when submitted to XTRM/Xoxoday |
| `reserve_ledger_entry_id` | `UUID` | nullable, FK → ledger_entries | RESERVE entry created at submission |
| `debit_ledger_entry_id` | `UUID` | nullable, FK → ledger_entries | DEBIT entry created on completion |
| `release_ledger_entry_id` | `UUID` | nullable, FK → ledger_entries | RELEASE entry created on failure/cancellation |
| `scheduled_batch_date` | `DATE` | nullable | Next scheduled batch processing date (BATCH mode only) |
| `submitted_at` | `TIMESTAMPTZ` | NOT NULL | Timestamp of partner submission |
| `processing_started_at` | `TIMESTAMPTZ` | nullable | Timestamp when sent to vendor |
| `completed_at` | `TIMESTAMPTZ` | nullable | Timestamp of vendor confirmation or failure |
| `failure_reason` | `VARCHAR(500)` | nullable | Vendor-reported failure reason (never surfaced verbatim to partner) |
| `version` | `BIGINT` | NOT NULL, DEFAULT 0 | Optimistic locking (`@Version`) |
| `deleted` | `BOOLEAN` | NOT NULL, DEFAULT false | Soft delete flag |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |

**PII Fields:** None — `user_id` is a UUID reference, not PII. No name, email, or payment data stored.

**Relationships:**
- `@ManyToOne` → `RewardWallet` (FK: `wallet_id`) — non-lazy load for balance checks
- `@ManyToOne` → `RedemptionCatalogItem` (FK: `catalog_item_id`) — for item metadata

**Indexes:**
- `idx_redemption_requests_client_id` on `client_id`
- `idx_redemption_requests_client_status` on `(client_id, status)` — in-flight count and filtered list queries
- `idx_redemption_requests_user_id` on `(client_id, user_id)` — partner's personal history
- `idx_redemption_requests_wallet_id` on `wallet_id` — wallet balance check queries

---

### RedemptionWebhookEvent (table: `redemption_webhook_events`)

_Path: `src/main/java/com/tenxengage/app/entity/`_
_Extends `BaseEntity`, implements `TenantAware`_
_No tenant filter applied at webhook receipt — client_id resolved from redemption_request_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited from BaseEntity |
| `client_id` | `UUID` | NOT NULL, FK → clients | Resolved from redemption_request; set before persist |
| `vendor` | `VARCHAR(20)` | NOT NULL | `XTRM` or `XOXODAY` — set from URL path |
| `redemption_request_id` | `UUID` | NOT NULL, FK → redemption_requests | The redemption this webhook applies to |
| `idempotency_key` | `VARCHAR(255)` | NOT NULL, UNIQUE | Vendor's event ID — used to detect duplicate delivery |
| `payload` | `JSONB` | NOT NULL | Raw webhook payload |
| `status` | `VARCHAR(20)` | NOT NULL | WebhookStatus enum |
| `received_at` | `TIMESTAMPTZ` | NOT NULL | When webhook arrived |
| `processed_at` | `TIMESTAMPTZ` | nullable | When processing completed |
| `failure_reason` | `VARCHAR(1000)` | nullable | Processing error detail (for DLQ investigation) |
| `version` | `BIGINT` | NOT NULL, DEFAULT 0 | Optimistic locking |
| `deleted` | `BOOLEAN` | NOT NULL, DEFAULT false | Soft delete flag |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |

**Relationships:**
- `@ManyToOne` → `RedemptionRequest` (FK: `redemption_request_id`)

**Indexes:**
- `uq_webhook_events_idempotency_key` UNIQUE on `idempotency_key` — fast duplicate check
- `idx_webhook_events_client_id` on `client_id`
- `idx_webhook_events_redemption_request_id` on `redemption_request_id`
- `idx_webhook_events_status` on `status` — DLQ admin queries

---

### Modified Entity: TenantRedemptionSettings (table: `tenant_redemption_settings`)

Add one column via V16 migration:

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `max_in_flight_redemptions` | `INTEGER` | NOT NULL, DEFAULT 10 | Per ADR-03 (Vijay, 2026-05-21) — configurable in-flight cap per partner |

---

## Permissions & Feature Flags [BE + FE]

### Permission Matrix

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `module.redemption_store` _(V8, existing)_ | Redemption Store | MODULE | `ALL` | MODULE_ACCESS | Y | — | Y | Y |
| `action.redemption.view_history` _(V8, existing)_ | View Redemption History | ACTION | `EXTERNAL` | REDEMPTION_ACTIONS | — | — | Y | Y |
| `action.redemption.view_all_history` _(V8, existing)_ | View All Redemption History | ACTION | `INTERNAL` | REDEMPTION_ACTIONS | Y | Y | — | — |
| **`action.redemption.redeem`** _(NEW — V17)_ | Redeem from Personal Wallet | ACTION | `EXTERNAL` | REDEMPTION_ACTIONS | — | — | — | Y |
| **`action.redemption.redeem_company`** _(NEW — V17)_ | Redeem from Company Wallet | ACTION | `EXTERNAL` | REDEMPTION_ACTIONS | — | — | Y | — |

`action.redemption.approve` deferred to F-04. `action.redemption.export` deferred to F-05.

### Feature Flag

| Feature Key | Description | Starter | Professional | Enterprise | Category |
|---|---|---|---|---|---|
| `redemption_store` _(V8, existing)_ | Enables Redemption Store — wallet, catalog, and redemption flow | `true` | `true` | `true` | `REWARDS` |

_No new feature flag for F-03 — `redemption_store` flag (seeded in F-01 V8) covers this feature._

---

## DTOs [BE]

### Request DTOs

_Path: `src/main/java/com/tenxengage/app/dto/request/`_

| Record | Key Fields | Validation |
|---|---|---|
| `SubmitPersonalRedemptionRequest` | `catalogItemId, amount, currencyId` | `@NotNull catalogItemId`, `@NotNull @Positive @DecimalMin("0.01") amount`, `@NotBlank @Size(max=50) currencyId` |
| `SubmitCompanyRedemptionRequest` | `catalogItemId, amount, currencyId, companyId` | Same as personal + `@NotNull companyId` |

**Validation rules:**
- `amount`: `@NotNull`, `@Positive`, `@DecimalMin("0.01")` — prevents zero or negative redemptions
- `catalogItemId`: `@NotNull` — validated against active catalog items for tenant in service layer
- `currencyId`: `@NotBlank`, `@Size(max=50)` — format validated here; business validation (known currency ID, matches catalog item's `currencyId`) performed in service layer (returns 422 on mismatch)
- `companyId` (company only): validated in service layer against partner companies belonging to caller's tenant

### Response DTOs

_Path: `src/main/java/com/tenxengage/app/dto/response/`_

| Record | Static Factory | Notes |
|---|---|---|
| `RedemptionRequestResponse` | `from(RedemptionRequest)` | List view — id, status, amount, currencyId, catalogItemId, processingMode, submittedAt, scheduledBatchDate, estimatedDelivery |
| `RedemptionRequestDetailResponse` | `from(RedemptionRequest, CatalogItemSummary)` | Detail view — adds catalogItemName, vendorReferenceId (only when COMPLETED), completedAt, failureReason (generic, not vendor-specific) |
| `RedemptionSubmissionConfirmationResponse` | `from(RedemptionRequest, String estimatedDelivery)` | Returned on POST — includes estimated delivery string, scheduledBatchDate for BATCH mode |

**Never include in responses:** `client_id`, `vendor_reference_id` when status is not COMPLETED, raw `failure_reason` vendor text, `deleted`, `version`.

_Full DTO shapes (including `currencyId` field definitions): see `../tenxengage-contracts/`._

_Full DTO shapes: see `../tenxengage-contracts/`._

---

## API Endpoints [BE + FE]

### Redemption Request Endpoints

_Base path: `/api/v1/redemption/requests`_
_Tag: `Redemption Flow`_

| Method | Path | Request Body | Response | Status | Permission | Audit | Location Header |
|---|---|---|---|---|---|---|---|
| `POST` | `/api/v1/redemption/requests` | `SubmitPersonalRedemptionRequest` | `RedemptionSubmissionConfirmationResponse` | 201 | `action.redemption.redeem` | `@Audited` | `/api/v1/redemption/requests/{id}` (absolute, via `ServletUriComponentsBuilder`) |
| `POST` | `/api/v1/redemption/requests/company` | `SubmitCompanyRedemptionRequest` | `RedemptionSubmissionConfirmationResponse` | 201 | `action.redemption.redeem_company` | `@Audited` | `/api/v1/redemption/requests/{id}` (absolute, via `ServletUriComponentsBuilder`) |
| `GET` | `/api/v1/redemption/requests` | — | `PaginatedResponse<RedemptionRequestResponse>` | 200 | `action.redemption.view_history` | — |
| `GET` | `/api/v1/redemption/requests/{id}` | — | `RedemptionRequestDetailResponse` | 200 | `action.redemption.view_history` | — |

**Query parameters for list endpoint:**
- `status` (optional, `RedemptionStatus` enum filter)
- `currencyId` (optional string filter — "cash", "points", "credits", "tickets")
- `page`, `pageSize` (max 50, `@Max(50)` enforced), `sortBy` (allowlist: `submittedAt`, `amount`, `status`), `sortDirection` (ASC / DESC, default DESC)

### Vendor Webhook Endpoints

_No JWT auth — HMAC-SHA256 signature verification only. Excluded from JWT filter chain._
_Tag: `Redemption Webhooks`_

| Method | Path | Auth | Response | Status | Notes |
|---|---|---|---|---|---|
| `POST` | `/api/v1/redemption/webhook/xtrm` | HMAC-SHA256 | — | 200 | Invalid sig → 401; unknown redemptionId → 404 |
| `POST` | `/api/v1/redemption/webhook/xoxoday` | HMAC-SHA256 | — | 200 | Invalid sig → 401; unknown redemptionId → 404 |

**Error responses (standard):**
- `400` — Input validation failure (missing required field, invalid enum value, malformed UUID)
- `401` — Not authenticated (missing JWT or invalid HMAC signature)
- `403` — Insufficient permissions
- `404` — Entity not found or belongs to a different tenant
- `409` — In-flight limit exceeded (`maxInFlightRedemptions`) / optimistic lock conflict
- `422` — Business rule violation (insufficient available balance, amount below catalog minimum, currency type mismatch)
- `429` — Rate limit exceeded

---

## Service Layer [BE]

_Path: `src/main/java/com/tenxengage/app/service/`_

### RedemptionSubmissionService

| Method | Return Type | Notes |
|---|---|---|
| `submitPersonalRedemption(request)` | `RedemptionSubmissionConfirmationResponse` | `@Transactional` — validates, reserves, persists RedemptionRequest |
| `submitCompanyRedemption(request)` | `RedemptionSubmissionConfirmationResponse` | `@Transactional` — same as personal but uses company wallet |

**Business rules:**
- Validate amount ≥ catalog item's `minTransactionAmount`
- Validate available balance ≥ client's minimum wallet balance threshold
- Check in-flight count: `COUNT(status IN ('PENDING_APPROVAL','RESERVED','PROCESSING') AND userId=caller) < maxInFlightRedemptions`
- Write RESERVE ledger entry atomically with persisting `RedemptionRequest` in a single transaction
- After reservation: if INSTANT mode → immediately call `RedemptionOrchestrationService.initiateVendorSubmission()`; if BATCH → compute and store `scheduledBatchDate`; if APPROVAL_REQUIRED → set status=PENDING_APPROVAL
- Publish `REDEMPTION_REQUESTED` event and partner notification on all paths

### RedemptionOrchestrationService

| Method | Return Type | Notes |
|---|---|---|
| `initiateVendorSubmission(redemptionRequestId)` | `void` | Resolves vendor from category; submits to XTRM or Xoxoday; transitions to PROCESSING |
| `processWebhookCompletion(webhookEventId)` | `void` | `@Transactional` — writes DEBIT ledger entry; transitions to COMPLETED; publishes notification |
| `processWebhookFailure(webhookEventId)` | `void` | `@Transactional` — writes RELEASE ledger entry; transitions to FAILED; publishes notification |

### RedemptionWebhookService

| Method | Return Type | Notes |
|---|---|---|
| `handleXtrmWebhook(payload, signature)` | `void` | Verifies HMAC; checks idempotency key; delegates to orchestration |
| `handleXoxodayWebhook(payload, signature)` | `void` | Verifies HMAC; checks idempotency key; delegates to orchestration |

**Idempotency rule:** Before processing, check `idempotency_key` in `redemption_webhook_events`. If found with status=PROCESSED or DUPLICATE → log + return 200 (discard). Never re-apply ledger mutations.

**Late/stale webhook:** Webhook arriving for a COMPLETED or FAILED redemption → log to audit trail (step=`webhook_stale_received`) + return 200. No state change.

### BatchRedemptionProcessor

Spring `@Scheduled` job. Reads all `RedemptionRequest` where `status=RESERVED AND processingMode=BATCH AND scheduledBatchDate <= today`. For each: calls `initiateVendorSubmission()`. Individual item failure → mark FAILED + write RELEASE ledger entry. Does not abort remaining batch items.

**Tenant isolation:** All service write methods resolve `clientId` from `tenantValidator.getCurrentClientId()` (reads from JWT via `SecurityContextHolder` — never the `X-Client-Subdomain` header, which is spoofable). Read methods use the Hibernate `@Filter` tenant filter. `RedemptionWebhookService` resolves `clientId` from the `RedemptionRequest` found by `redemptionId` in the webhook payload (no JWT context available for vendor callbacks).

---

## Workflow / Status Transitions [BE + FE]

```
[SUBMISSION] → PENDING_APPROVAL  (trigger: partner submits; mode=APPROVAL_REQUIRED; balance reserved)
[SUBMISSION] → RESERVED          (trigger: partner submits; mode=BATCH; balance reserved; awaiting batch)
[SUBMISSION] → RESERVED→PROCESSING  (trigger: partner submits; mode=INSTANT; balance reserved then vendor called)
PENDING_APPROVAL → RESERVED      (trigger: approver approves — handled in F-04; vendor submission begins)
RESERVED → PROCESSING            (trigger: batch processor runs, or INSTANT transition)
PROCESSING → COMPLETED           (trigger: vendor webhook confirms fulfillment; DEBIT ledger entry written)
PROCESSING → FAILED              (trigger: vendor webhook signals failure; RELEASE ledger entry written)
PENDING_APPROVAL → CANCELLED     (trigger: approver rejects — handled in F-04; RELEASE ledger entry written)
RESERVED → CANCELLED             (trigger: admin/system cancels before vendor submission; RELEASE ledger entry written)
```

**Invalid transitions** (return 400 with descriptive message):
- `COMPLETED → any` — final state; returns handled by F-06
- `FAILED → any` — final state; balance already released
- `CANCELLED → any` — final state; balance already released

**Concurrent transition handling:** `@Version` on `RedemptionRequest`. Concurrent status transitions → `409 Conflict`. FE must show "This redemption was updated. Please refresh."

---

## Security Design [BE]

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| `amount`, `currency_type` | Confidential | Returned only to owning user or CLIENT_ADMIN; never cross-tenant |
| `vendor_reference_id` | Confidential | Not returned to partner until COMPLETED |
| `failure_reason` (raw vendor text) | Internal | Never returned verbatim — mapped to generic user-friendly message |
| XTRM identity payload (name, email, country) | PII | Passed to XTRM at call time only; NOT stored in TenXEngage |
| `user_id` | Pseudonymous | UUID — not PII by itself; retained indefinitely |
| `payload` in RedemptionWebhookEvent | Confidential | JSONB stored for audit/DLQ; not exposed via API |

### Rate Limiting

_(Reference `RateLimitFilter` at `com.tenxengage.app.security.RateLimitFilter`)_

| Endpoint / Operation | Limit | Scope | Reason |
|---|---|---|---|
| `POST /api/v1/redemption/requests` | 10 req/min | Per user | Prevents rapid submission abuse / double-click |
| `POST /api/v1/redemption/requests/company` | 10 req/min | Per user | Same as personal |
| `POST /api/v1/redemption/webhook/xtrm` | 100 req/min | Per IP | Vendor delivery rate; spike may indicate replay attack |
| `POST /api/v1/redemption/webhook/xoxoday` | 100 req/min | Per IP | Same as XTRM |

### OWASP Risks & Mitigations

| Risk | Where | Mitigation |
|---|---|---|
| **Broken Access Control (A01)** | `GET /requests/{id}` | `findByIdAndClientId` — wrong-tenant ID returns 404, never 403 |
| **IDOR (A01)** | `wallet_id` in request | Validate wallet belongs to caller (`wallet.userId == caller.userId` for personal; `wallet.companyId` for company) |
| **Webhook replay (A07)** | POST webhook endpoints | HMAC-SHA256 verification + idempotency key in `redemption_webhook_events` |
| **Broken Access Control (A01)** | `GET /requests` list | Partners only see their own requests; CLIENT_ADMIN all-tenant view is F-05 |
| **Mass Assignment** | POST bodies | DTO records with explicit fields only — no dynamic property binding |
| **Injection (A03)** | `sort`, `status` filter params | Allowlist validation; unknown sort fields return 400 |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `amount` | `@NotNull`, `@Positive`, `@DecimalMin("0.01")` | 400 with field-level error |
| `catalogItemId` | `@NotNull` | 400 — null UUID |
| `currencyId` | `@NotBlank`, `@Size(max=50)` | 400 — blank or too long; unknown currency ID validated in service → 422 |
| Available balance check | Service: `availableBalance >= minWalletBalanceThreshold` | 422 — business rule violation |
| Amount minimum check | Service: `amount >= catalogItem.minTransactionAmount` | 422 — business rule violation |
| Currency mismatch | Service: `currencyId == catalogItem.currencyId` | 422 — business rule violation |
| `sortBy` query param | Allowlist: `["submittedAt", "amount", "status"]` | 400 — unknown sort column |
| `sortDirection` query param | `ASC` or `DESC` only | 400 — invalid value |
| `pageSize` query param | `@Max(50)` | 400 |
| Webhook `Content-Type` | Must be `application/json` | 400 |
| Webhook HMAC signature | Header `X-Webhook-Signature` must match | 401 |

---

## Audit Trail [BE]

_Path: `src/main/java/com/tenxengage/app/audit/` (existing `@Audited` infrastructure)_

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| SUBMIT redemption | `RedemptionRequest` | `id`, `userId`, `walletId`, `catalogItemId`, `amount`, `currencyType`, `processingMode`, `status`, `submittedAt` | `CLIENT_ADMIN` |
| STATUS CHANGE | `RedemptionRequest` | `oldStatus` → `newStatus`, `changedBy`, `changedAt`, `vendorReferenceId` (when set) | `CLIENT_ADMIN` |
| WEBHOOK RECEIVED | `RedemptionWebhookEvent` | `vendor`, `idempotencyKey`, `redemptionRequestId`, `status`, `receivedAt` | `CLIENT_ADMIN` |
| WEBHOOK PROCESSED | `RedemptionWebhookEvent` | `status=PROCESSED`, `processedAt`, ledger entry IDs applied | `CLIENT_ADMIN` |
| WEBHOOK DUPLICATE | `RedemptionWebhookEvent` | `status=DUPLICATE`, reason: "idempotency key already processed" | `CLIENT_ADMIN` |

### New Audit Enum Values

| Enum | New Value | Reason |
|---|---|---|
| `AuditAction` | `COMPLETED` | Vendor confirms fulfillment |
| `AuditAction` | `FAILED` | Vendor reports failure or processing error |
| `AuditAction` | `CANCELLED` | Redemption cancelled (rejection or admin action) |
| `AuditResourceType` | `REDEMPTION_REQUEST` | New entity type for audit tracking |
| `AuditResourceType` | `REDEMPTION_WEBHOOK_EVENT` | Webhook processing audit |

_These are Java enum values stored as varchar(50) — no Flyway migration needed; update the Java enum files directly._

### `@Audited` Annotation Details (Non-CRUD)

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `POST /redemption/requests` | `SUBMITTED` | `REDEMPTION_REQUEST` | `Partner submitted personal wallet redemption` |
| `POST /redemption/requests/company` | `SUBMITTED` | `REDEMPTION_REQUEST` | `Partner Admin submitted company wallet redemption` |
| Webhook XTRM completion | `COMPLETED` | `REDEMPTION_REQUEST` | `XTRM confirmed fulfillment` |
| Webhook XTRM failure | `FAILED` | `REDEMPTION_REQUEST` | `XTRM reported failure` |
| Webhook Xoxoday completion | `COMPLETED` | `REDEMPTION_REQUEST` | `Xoxoday confirmed fulfillment` |
| Webhook Xoxoday failure | `FAILED` | `REDEMPTION_REQUEST` | `Xoxoday reported failure` |

**Audit record retention:** Indefinite. Audit records are append-only.

---

## Observability [BE]

### MDC Fields

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` header | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` from JWT (or resolved from webhook payload) | `TenantFilter` (existing) + webhook resolver |
| `userId` | User ID from JWT | `JwtAuthenticationFilter` (existing) |
| `featureArea` | `"redemption-flow"` | Set in `RedemptionSubmissionService` constructor |

### Key Log Events

| Event | Level | `step` value | Key Fields | Purpose |
|---|---|---|---|---|
| Redemption submitted | INFO | `redemption_submitted` | `redemptionId`, `userId`, `walletId`, `amount`, `processingMode` | Business tracking |
| Balance reserved | INFO | `balance_reserved` | `redemptionId`, `ledgerEntryId`, `amount` | Ledger audit trail |
| Vendor submission sent | INFO | `vendor_submission_sent` | `redemptionId`, `vendor`, `vendorReferenceId` | Integration tracking |
| Webhook received | INFO | `webhook_received` | `vendor`, `idempotencyKey`, `redemptionId` | Integration audit |
| Webhook duplicate discarded | WARN | `webhook_duplicate_discarded` | `vendor`, `idempotencyKey` | Detect vendor retry storms |
| Webhook DLQ routed | ERROR | `webhook_dead_lettered` | `vendor`, `webhookEventId`, `failureReason` | Ops alert — manual investigation needed |
| In-flight limit reached | WARN | `in_flight_limit_reached` | `userId`, `clientId`, `currentCount`, `limit` | Detect limit breaches |
| Optimistic lock conflict | WARN | `optimistic_lock_conflict` | `redemptionId`, `expectedVersion` | Detect concurrent-edit issues |
| Vendor retry exhausted | ERROR | `vendor_retry_exhausted` | `redemptionId`, `vendor`, `attemptCount` | Ops alert |

### Metrics

| Metric Name | Type | Labels | Purpose |
|---|---|---|---|
| `redemption.submitted.total` | Counter | `tenantId`, `processingMode`, `category` | Submission volume by mode and vendor route |
| `redemption.completed.total` | Counter | `tenantId`, `vendor`, `category` | Completion funnel |
| `redemption.failed.total` | Counter | `tenantId`, `vendor` | Failure rate monitoring |
| `redemption.webhook.processing_duration_ms` | Histogram | `vendor` | Webhook processing latency |
| `redemption.in_flight.count` | Gauge | `tenantId` | Current in-flight redemptions per tenant |

---

## Domain Events [BE]

### Events Produced

Topic: `redemption-events`
Partition key: `clientId.toString()`
Producer pattern: direct `KafkaTemplate.send()` (no transactional outbox — matches existing `NotificationEventProducer` pattern)

> **Implementation prerequisite:** `redemption-events` must be registered in `com.tenxengage.app.config.KafkaConfig.java` as a named topic constant before any producer code is written. Never create Kafka topics ad-hoc.

| Topic | Event Type | Trigger |
|---|---|---|
| `redemption-events` | `REDEMPTION_REQUESTED` | `RedemptionSubmissionService` on successful submission |
| `redemption-events` | `REDEMPTION_PROCESSING` | `RedemptionOrchestrationService` on vendor submission |
| `redemption-events` | `REDEMPTION_COMPLETED` | `RedemptionOrchestrationService` on webhook completion |
| `redemption-events` | `REDEMPTION_FAILED` | `RedemptionOrchestrationService` on webhook failure |
| `redemption-events` | `REDEMPTION_CANCELLED` | `RedemptionOrchestrationService` on cancellation |

**Message schema:**
```json
{
  "eventId": "uuid",
  "eventType": "REDEMPTION_REQUESTED",
  "occurredAt": "2026-05-21T10:00:00Z",
  "clientId": "uuid",
  "redemptionRequestId": "uuid",
  "userId": "uuid",
  "amount": 150.00,
  "currencyType": "CASH",
  "processingMode": "INSTANT",
  "status": "PROCESSING"
}
```

**No PII in event payload.** XTRM identity fields (name, email, country) are passed to vendor at call time only — never published to Kafka.

**Partner notifications** (via existing `NotificationEventProducer` → `notification-events` topic):
- On submission: "Your redemption of [amount] [currencyType] has been received and is [processing | queued for [date] | awaiting approval]"
- On `REDEMPTION_COMPLETED`: "Your redemption of [amount] [currencyType] has been fulfilled"
- On `REDEMPTION_FAILED`: "Your redemption of [amount] [currencyType] could not be processed. Your balance has been restored."

Phase 2 PAS pipeline will subscribe to `redemption-events`. No consumer in this spec.

---

## Frontend Specification [FE]

_TypeScript types live in `../tenxengage-contracts/` — copy from there, do not hand-write. Full FE file paths and hook specs: see `technical.md`._

### Pages

| Page | Route | Layout | Permission | Notes |
|---|---|---|---|---|
| Redemption Submit Flow | Modal overlay on `/redemption/catalog/:itemId` | `AppLayout` | `action.redemption.redeem` or `action.redemption.redeem_company` | Not a standalone route — invoked from catalog item detail |
| Redemption Confirmation | `/redemption/confirmation/:id` | `AppLayout` | `action.redemption.view_history` | Post-submission success screen with estimated delivery |

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `RedemptionSubmitModal` | `catalogItemId, walletType` | `useRedemptionSubmit()` hook | Amount input for cash; confirm button for non-cash; shows estimated delivery before confirm |
| `RedemptionConfirmationCard` | `redemptionId` | `useRedemptionRequest(id)` hook | Shows status, amount, estimatedDelivery, scheduledBatchDate for BATCH mode |
| `InFlightLimitBanner` | `currentCount, limit` | From wallet state | Shown inline when partner has reached `maxInFlightRedemptions` |

### Forms

| Form | Fields | Validation | Submit Action |
|---|---|---|---|
| `RedemptionSubmitForm` | `amount` (cash only), `currencyId`, `catalogItemId` | `redemptionSubmitSchema` (zod): amount > 0, amount ≤ availableBalance, amount ≥ item.minTransactionAmount | `POST /api/v1/redemption/requests` or `.../company` |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `useRedemptionRequest(id)` | `['redemption-request', id]` | `GET /api/v1/redemption/requests/{id}` | 30s | On status change mutation |
| `useRedemptionRequests()` | `['redemption-requests', { userId, status, currencyId, page, pageSize }]` | `GET /api/v1/redemption/requests` | 1 min | On new submission |
| `useRedemptionSubmit()` | — (mutation) | `POST /api/v1/redemption/requests` | — | Invalidates wallet balance + redemption list on success |

_Service file path and hook query keys: see `technical.md`._

---

## Caching Strategy [BE]

No server-side caching applied to redemption requests. Data changes frequently (status transitions, balance updates) and stale reads would cause incorrect in-flight counts and balance displays. TanStack Query handles client-side caching with 30-second stale time for active redemptions.

---

## Data Retention & Compliance [BE]

### Soft Delete vs Hard Delete

**Decision: Soft delete** (`deleted = BOOLEAN` flag). Redemption records are financial transaction records — preserved indefinitely for audit and legal hold.

### PII Handling

No PII is stored in `redemption_requests` or `redemption_webhook_events`. XTRM identity fields (name, email, country) are assembled from the user profile at call time and passed to XTRM's API — never persisted to TenXEngage's database.

| Field | Entity | Notes |
|---|---|---|
| `user_id` | `RedemptionRequest` | UUID reference — pseudonymous; retained indefinitely |

### Data Retention Periods

| Data Type | Retention Period | Justification |
|---|---|---|
| `redemption_requests` | Indefinite | Financial records — no deletion policy (Pushpendra, 2026-05-21) |
| `redemption_webhook_events` | Indefinite | Part of webhook audit trail |
| Audit log entries | Indefinite | Consistent with entity retention |

---

## Configurable Dimensions [BE]

| Dimension | Storage | Default | Notes |
|---|---|---|---|
| `maxInFlightRedemptions` | `tenant_redemption_settings.max_in_flight_redemptions` | `10` | Per ADR-03 (Vijay, 2026-05-21) — max concurrent PENDING_APPROVAL + RESERVED + PROCESSING per partner |

---

## Edge Cases [BE + FE]

1. **In-flight limit reached** — `POST /redemption/requests` when `inFlightCount >= maxInFlightRedemptions` returns `409 Conflict` with message "Maximum in-flight redemptions reached. Wait for a current redemption to complete before submitting another." FE shows `InFlightLimitBanner` when limit is reached.
2. **Available balance below minimum threshold** — `POST` returns `422` with message "Insufficient available balance. Minimum wallet balance of [threshold] must be maintained." FE shows inline validation before submit.
3. **Amount below catalog minimum** — `POST` returns `422` with message "Amount must be at least [minTransactionAmount]." FE validates inline.
4. **Duplicate webhook delivery** — `RedemptionWebhookService` finds existing `idempotency_key` with status=PROCESSED → logs `webhook_duplicate_discarded` + returns 200. No ledger mutation.
5. **Webhook for already-COMPLETED/FAILED redemption** — log to audit trail (step=`webhook_stale_received`) + return 200. No state change.
6. **Vendor unavailable (INSTANT mode)** — exponential backoff retries; if all retries exhausted, status stays PROCESSING, webhook event marked FAILED, routed to DLQ, ops alerted via `vendor_retry_exhausted`.
7. **Batch processor: individual vendor failure** — that redemption marked FAILED (RELEASE ledger entry); remaining batch items continue processing normally.
8. **Company wallet redemption** — `companyId` must belong to caller's tenant; wallet type must be COMPANY; `action.redemption.redeem_company` permission required.
9. **Currency mismatch** — requested `currencyId` must match the catalog item's `currencyId` and the wallet's `currencyId`; mismatch returns 422 (business rule violation, not a format error).
10. **Cross-tenant webhook** — webhook payload's `redemptionId` not found in caller's resolved tenant → 404 (never reveals tenant boundary).
11. **Concurrent submission** — two simultaneous submissions from same user: second may exceed in-flight limit → 409; balance reservation uses optimistic lock on wallet.

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, `@WebMvcTest`, Vitest, E2E Playwright) — live inside each `stories/US-NN-*.md` file alongside the code they verify
- **Cross-story integration tests** (Testcontainers full-lifecycle, multi-entity workflows, tenant isolation, audit/events) — in [test-plan.md](test-plan.md)

---

## Out of Scope

- Approval review actions (approving/rejecting PENDING_APPROVAL) — covered in F-04
- Redemption transaction history and export — covered in F-05
- Non-cash returns flow — covered in F-06
- Analytics and reporting — covered in F-07
- PAS Commercial Intent pipeline integration — Phase 2
- Batch processing scheduler UI — Phase 2
- XTRM vendor integration (blocked — TransferFund API broken; S-03, S-06 parked)
- Xoxoday vendor integration (blocked — agreement not signed; S-04, S-06 parked)
- KYC/AML processing — delegated to XTRM natively
- Cross-currency redemptions — not in v1

---

## Verification Steps

### Backend Verification
1. `./gradlew bootRun` — app starts; Flyway V16 and V17 migrations apply without errors
2. `./gradlew test` — all new and existing tests pass
3. Security: cross-tenant `GET /requests/{id}` → `404`; `POST /requests` without `action.redemption.redeem` → `403`; no JWT → `401`; invalid HMAC on webhook → `401`
4. Observability: tail logs on `POST /requests`; verify `step=redemption_submitted`, `tenantId`, `userId`, `redemptionId` appear
5. In-flight limit: submit 10 redemptions for same user, 11th returns `409`

### Frontend Verification
1. `npm run build` — no TypeScript errors
2. `npm run test` — Vitest passes; `npx playwright test` — E2E passes
3. UI: catalog item detail shows "Redeem" button; submit modal validates amount; confirmation screen shows estimated delivery and/or scheduled batch date; in-flight limit banner appears when limit reached
