---
slug: redemption-history
name: Transaction History & Export
status: reviewed
format: story-sliced
roadmap: redemption-store
domain: null
builder_type: null
created: 2026-06-03
contract: null
---

> **Reviewed**: 2026-06-03

# Feature: Transaction History & Export

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

Transaction History & Export gives every partner user a permanent, filterable receipt book for all their redemptions.

### Naming reconciliation

| BRD name | Codebase name | Decision | Reason |
|---|---|---|---|
| `RedemptionTransaction` | `RedemptionRequest` | Use codebase name | `RedemptionRequest` is the established entity in `tenxengage-backend` and `tenxengage-contracts/models/redemption-request.md`; BRD vocabulary pre-dates the codebase |
| `GET /api/v1/redemption/transactions` | `GET /api/v1/redemption/requests` | Use codebase path | Existing controller already at `/api/v1/redemption/requests`; renaming would be a breaking change |
| `action.redemption.view_history` | `action.redemption.view_history` | Adopted verbatim | Permission key matches codebase exactly (seeded V8) |
| `action.redemption.view_all_history` | `action.redemption.view_all_history` | Adopted verbatim | Matches codebase exactly (seeded V8) |
| `action.redemption.export` | `action.redemption.export` | BRD candidate adopted | Not yet in codebase; BRD name follows platform convention — seeded in V11 | Partner Sellers and Partner Admins can browse, filter by date range, status, and redemption type, and export their own transaction data as CSV or XLSX. Client Admins get a complete tenant-wide view across all users and companies — with additional filters by user and company — for program reporting and audits. No redemption record is ever lost or hard to find.

Export uses a threshold strategy to balance simplicity with scale safety: requests that would return ≤ 1,000 rows stream the file directly in the HTTP response; larger requests create an async `RedemptionExportJob` that the frontend polls until ready for download. History retention is indefinite in v1 — no rolling window, no record expiry.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-05.1 | `PARTNER_SELLER` and `PARTNER_ADMIN` can view their own redemption transaction history, paginated and returning: transaction ID, catalog item name, currency type, amount, status, submission timestamp, completion timestamp (nullable), and vendor reference ID (only when status = COMPLETED). |
| FR-05.2 | Transaction history is filterable by date range (`dateFrom`, `dateTo`), transaction status (`RedemptionStatus` enum), and redemption type (`RedemptionCategory`: CASH / NON_CASH). Filters are all optional and combinable. |
| FR-05.3 | Return transactions in the history are linked to their originating redemption via `linkedReturnId` (nullable; populated once F-06 is deployed). The link is preserved even if the return is rejected. |
| FR-05.4 | `PARTNER_SELLER` and `PARTNER_ADMIN` can export their own transaction history as CSV or XLSX. Exports ≤ 1,000 rows are returned synchronously; exports > 1,000 rows trigger an async job that the user polls and downloads when complete. |
| FR-05.5 | `PARTNER_ADMIN` can view the company wallet redemption history — all redemptions originating from the partner company's COMPANY wallet — with the same filter capabilities as personal history. |
| FR-05.6 | `CLIENT_ADMIN` can view the full tenant-wide redemption history across all partner users and companies, with the standard filters plus optional `userId` and `companyId` filters. |
| FR-05.7 | Individual transaction detail displays all lifecycle timestamps (`submittedAt`, `processingStartedAt`, `completedAt`), vendor reference ID (status = COMPLETED only), failure reason (mapped to a user-friendly message when status = FAILED), processing mode, wallet type, and linked return request ID (nullable). |
| FR-05.8 | `CLIENT_ADMIN` can export the full tenant transaction history as CSV or XLSX. The export includes: requesting user, company, all transaction detail fields, and `linkedReturnId`. Vendor names (XTRM / Xoxoday) are included in admin-scope exports as an internal label; they are not shown in partner-facing UI. The same async threshold (> 1,000 rows) applies. |

---

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (list reads)** | P95 < 300ms | Paginated list with filters; indexed queries |
| **Response time (sync export)** | P95 < 5s for ≤ 1,000 rows | Direct file stream |
| **Response time (async export trigger)** | P95 < 200ms | Job row creation only; file generation is background |
| **Peak concurrent users** | SaaS defaults | Not provided — tune connection pool for 100 concurrent history page loads |
| **Max page size** | 50 items | Hard cap via `@Max(50)` |
| **Availability** | 99.9% | Core user-facing flow |
| **Data sensitivity** | CONFIDENTIAL | Financial transaction records; export files contain financial PII |
| **Compliance** | GDPR | Export files contain transaction data linked to individuals |
| **Audit retention** | Indefinite | Transaction records never deleted in v1 |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec` (status must be `reviewed`)
- [ ] Contracts generated via `/generate-contracts` in `tenxengage-contracts`
- [ ] F-01 (Wallet & Ledger Foundation) implemented — `RewardWallet` with `partnerCompanyId` needed for company history resolution
- [ ] F-03 (Redemption Flow) implemented — `RedemptionRequest` rows are the data source
- [ ] Next Flyway migration number confirmed: **V10** (latest is V9)
- [ ] `action.redemption.export` permission not yet seeded — V11 migration required

---

## New Enums [BE]

| Enum Class | Values | Notes |
|---|---|---|
| `ExportFormat.java` | `CSV, XLSX` | Format of the generated export file |
| `RedemptionExportStatus.java` | `PENDING, PROCESSING, COMPLETED, FAILED` | Lifecycle state of a `RedemptionExportJob` |

_Path: `src/main/java/com/tenxengage/app/entity/enums/redemption/`_

---

## Data Model / Entities [BE]

### Entity-shape decisions

| Entity | Shape | Source |
|---|---|---|
| `RedemptionRequest` | Hardcoded JPA entity | Inherited from F-03 spec |
| `RedemptionExportJob` | Hardcoded JPA entity | This spec |

### RedemptionExportJob (table: `redemption_export_jobs`)

_Path: `src/main/java/com/tenxengage/app/entity/redemption/RedemptionExportJob.java`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_
_Carries `@Version` on `version` field_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited from BaseEntity |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation — never expose in API responses |
| `requested_by` | `UUID` | NOT NULL, FK → users | Owner of this export job; used for download access control |
| `status` | `VARCHAR(50)` | NOT NULL, DEFAULT `'PENDING'` | `RedemptionExportStatus` enum value |
| `format` | `VARCHAR(10)` | NOT NULL | `ExportFormat` enum value — CSV or XLSX |
| `scope` | `VARCHAR(20)` | NOT NULL | `PERSONAL`, `COMPANY`, or `ALL_TENANT` — determines query scope at generation time |
| `filter_snapshot` | `JSONB` | NOT NULL, DEFAULT `'{}'` | Filters applied at trigger time (dateFrom, dateTo, status, category, userId, companyId) |
| `row_count` | `INTEGER` | NULL | Populated on COMPLETED; null while pending/processing/failed |
| `file_key` | `VARCHAR(500)` | NULL | Object storage key for the generated file; null until COMPLETED |
| `expires_at` | `TIMESTAMPTZ` | NULL | Download link expiry; set to NOW() + 24h on COMPLETED; null until then |
| `failure_reason` | `VARCHAR(500)` | NULL | Generic failure description; populated on FAILED |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `deleted` | `BOOLEAN` | NOT NULL, DEFAULT false | Soft delete |
| `version` | `BIGINT` | NOT NULL, DEFAULT 0 | Optimistic locking |

**PII Fields:** None — `requested_by` is a UUID (pseudonymous, not PII by itself). The export _file_ contains financial PII but is stored in object storage, not in this table.

**Relationships:**
- `@ManyToOne` → `User` (FK: `requested_by`) — non-lazy for ownership checks

**Indexes:**
- `idx_redemption_export_jobs_client_id` on `(client_id)`
- `idx_redemption_export_jobs_client_requester` on `(client_id, requested_by)`
- `idx_redemption_export_jobs_client_status` on `(client_id, status)`
- `idx_redemption_export_jobs_client_created` on `(client_id, created_at DESC)`

---

## Permissions & Feature Flags [BE + FE]

### Permission Matrix

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `module.redemption_store` | Redemption Store | MODULE | ALL | MODULE_ACCESS | Y | — | Y | Y |
| `action.redemption.view_history` | View Redemption History | ACTION | EXTERNAL | REDEMPTION_ACTIONS | — | — | Y | Y |
| `action.redemption.view_all_history` | View All Redemption History | ACTION | INTERNAL | REDEMPTION_ACTIONS | Y | — | — | — |
| `action.redemption.export` | Export Redemption History | ACTION | ALL | REDEMPTION_ACTIONS | Y | — | Y | Y |

_`module.redemption_store`, `action.redemption.view_history`, and `action.redemption.view_all_history` were seeded in V8. `action.redemption.export` is new — seeded in V11._

### Feature Flag

| Feature Key | Description | Starter | Professional | Enterprise | Category |
|---|---|---|---|---|---|
| `redemption_store` | Enables Redemption Store — wallet, catalog, and redemption flow | `true` | `true` | `true` | REWARDS |

_Flag already seeded in V8. No new flag needed for F-05._

_Flyway seed SQL for `action.redemption.export` lives in `technical.md → ## Flyway Migrations [BE]`._

---

## DTOs [BE]

### Modified Existing DTOs

The following existing DTOs gain new fields (additive — non-breaking):

**`RedemptionRequestResponse`** (existing, root `dto/response/`) — add:
- `catalogItemName: String` — display name of the redeemed catalog item (joined at query time)
- `completedAt: Instant` — null until vendor confirms; non-null on COMPLETED or FAILED

**`RedemptionRequestDetailResponse`** (existing, root `dto/response/`) — add:
- `linkedReturnId: UUID` — nullable; FK to `RedemptionReturn.id` (F-06 entity); null until F-06 is deployed

### New Response DTOs

_Path: `src/main/java/com/tenxengage/app/dto/response/redemption/`_

| Record | Static Factory | Notes |
|---|---|---|
| `RedemptionExportJobResponse` | `from(RedemptionExportJob)` | Poll response — status, rowCount, expiresAt |
| `RedemptionExportJobDetailResponse` | `from(RedemptionExportJob, String presignedUrl)` | Adds `downloadUrl` (presigned, valid 15 min from fetch) when status = COMPLETED |
| `RedemptionAdminHistoryResponse` | `from(RedemptionRequest, String userName, String companyName)` | All-tenant view only — extends list fields with `userId`, `userDisplayName`, `partnerCompanyId`, `partnerCompanyName` for Client Admin context |

### New Request DTOs

_Path: `src/main/java/com/tenxengage/app/dto/request/redemption/`_

| Record | Key Fields | Validation |
|---|---|---|
| `TriggerExportRequest` | `format: ExportFormat`, `dateFrom: LocalDate` (optional), `dateTo: LocalDate` (optional), `status: RedemptionStatus` (optional), `category: RedemptionCategory` (optional), `userId: UUID` (optional — CLIENT_ADMIN only), `companyId: UUID` (optional — CLIENT_ADMIN only) | `format` required; date range optional but `dateFrom` must be ≤ `dateTo` if both provided (422 on violation); `userId` and `companyId` ignored for non-CLIENT_ADMIN callers |

**Never include in responses:** `clientId`, `deleted`, `version`, `fileKey` (internal storage reference).

---

## API Endpoints [BE + FE]

_Base path: `/api/v1/redemption/requests`_
_Tag: `Redemption History`_

### Existing Endpoints — Enhanced

| Method | Path | Change | Breaking? |
|---|---|---|---|
| `GET` | `/api/v1/redemption/requests` | Add filter params: `dateFrom` (LocalDate), `dateTo` (LocalDate), `status` (RedemptionStatus enum), `category` (RedemptionCategory enum); response gains `catalogItemName` + `completedAt` | No — additive |
| `GET` | `/api/v1/redemption/requests/{id}` | Response gains `linkedReturnId` (nullable UUID) | No — additive |

### New Endpoints

| Method | Path | Request | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/redemption/requests/company` | Query params: `dateFrom`, `dateTo`, `status`, `category`, `page`, `pageSize`, `sortBy`, `sortDirection` | `PaginatedResponse<RedemptionRequestResponse>` | 200 | `action.redemption.view_history` | — |
| `GET` | `/api/v1/redemption/requests/all` | Query params above + `userId` (optional), `companyId` (optional) | `PaginatedResponse<RedemptionAdminHistoryResponse>` | 200 | `action.redemption.view_all_history` | — |
| `POST` | `/api/v1/redemption/requests/export` | `TriggerExportRequest` | Sync: file bytes (`Content-Disposition: attachment`) / Async: `RedemptionExportJobResponse` | 200 (sync) or 202 (async) | `action.redemption.export` | `@Audited` |
| `GET` | `/api/v1/redemption/requests/export/{jobId}` | — | `RedemptionExportJobResponse` | 200 | `action.redemption.export` | — |
| `GET` | `/api/v1/redemption/requests/export/{jobId}/download` | — | `RedemptionExportJobDetailResponse` (with presigned URL) | 200 | `action.redemption.export` | — |

**Query parameters for list endpoints:**
- `dateFrom`, `dateTo` — ISO 8601 date strings (`LocalDate`); both optional; `dateFrom` ≤ `dateTo` enforced (422 if violated)
- `status` — `RedemptionStatus` enum; unknown value → 400
- `category` — `RedemptionCategory` enum; unknown value → 400
- `page` — default 0 (zero-based)
- `pageSize` — default 20, `@Max(50)`
- `sortBy` — allowlist: `["submittedAt", "amount", "status"]`; unknown value → 400
- `sortDirection` — `ASC` or `DESC`; default `DESC`
- `userId` (all-tenant only) — UUID; ignored for non-CLIENT_ADMIN
- `companyId` (all-tenant only) — UUID; ignored for non-CLIENT_ADMIN

**Error responses:**
- `400` — Invalid enum value, unknown sort field, invalid date format, dateFrom > dateTo
- `401` — Missing or expired JWT
- `403` — Insufficient permissions
- `404` — Export job not found or belongs to different tenant / different user (non-admin)
- `422` — Export with zero matching records
- `429` — Rate limit exceeded (export trigger)

---

## Service Layer [BE]

### RedemptionHistoryService

_Path: `src/main/java/com/tenxengage/app/service/redemption/RedemptionHistoryService.java`_

_This service owns ALL history query logic including personal history. `RedemptionSubmissionService.getPersonalRedemptions()` is replaced by a call to `getPersonalHistory()` — update `RedemptionRequestController.listRedemptions()` to delegate here instead._

| Method | Return Type | Notes |
|---|---|---|
| `getPersonalHistory(userId, filters, pageable)` | `Page<RedemptionRequestResponse>` | `@Transactional(readOnly=true)` — queries by userId + clientId; replaces `RedemptionSubmissionService.getPersonalRedemptions()` |
| `getCompanyHistory(userId, filters, pageable)` | `Page<RedemptionRequestResponse>` | `@Transactional(readOnly=true)` — resolves COMPANY wallet IDs from userId's partnerCompanyId |
| `getTenantHistory(filters, pageable)` | `Page<RedemptionAdminHistoryResponse>` | `@Transactional(readOnly=true)` — CLIENT_ADMIN only; response includes `userId`, `userDisplayName`, `partnerCompanyId`, `partnerCompanyName` |
| `getRedemptionDetail(id, userId)` | `RedemptionRequestDetailResponse` | `@Transactional(readOnly=true)` — existing logic extended with linkedReturnId |

### RedemptionExportService

_Path: `src/main/java/com/tenxengage/app/service/redemption/RedemptionExportService.java`_

| Method | Return Type | Notes |
|---|---|---|
| `triggerExport(request, userId)` | `ExportResult` | `@Transactional` — runs COUNT query; if ≤ 1,000 generates file synchronously and returns bytes; if > 1,000 creates RedemptionExportJob and returns jobId |
| `getExportJob(jobId, userId)` | `RedemptionExportJobResponse` | `@Transactional(readOnly=true)` — validates ownership or view_all_history permission |
| `getExportJobWithDownloadUrl(jobId, userId)` | `RedemptionExportJobDetailResponse` | `@Transactional(readOnly=true)` — same ownership check; generates presigned URL valid 15 minutes if COMPLETED |
| `processExportJob(jobId)` | `void` | `@Async`, `@Transactional` — called internally after job creation; generates file, uploads to storage, updates job to COMPLETED (or FAILED) |

**Business rules:**
- `triggerExport`: row count estimated via COUNT query before file generation; threshold is 1,000 (inclusive — ≤ 1,000 → sync)
- `triggerExport` with zero results → throws `BusinessRuleException` with message "No records match the selected filters" (422)
- `getExportJob` / `getExportJobWithDownloadUrl`: if `job.requestedBy != currentUserId` AND caller lacks `action.redemption.view_all_history` → 404 (not 403)
- Export job download only valid when `status = COMPLETED`; if PENDING/PROCESSING → return job status with no URL; if FAILED → return job status with `failureReason`
- Export files are deleted from object storage after `expires_at` (24 hours post-completion); job row is retained indefinitely for audit
- `scope` field on the job captures whether the export was PERSONAL, COMPANY, or ALL_TENANT — used to replay the query during async generation

**Tenant isolation contract:** Every service method resolves `clientId` from `TenantContext.getCurrentClientId()` — never accepted as an API parameter.

---

## Workflow / Status Transitions [BE + FE]

```
[ASYNC TRIGGER] → PENDING        (job created; async task queued)
PENDING → PROCESSING             (async task picks up job; begins query + file generation)
PROCESSING → COMPLETED           (file written to storage; fileKey + expiresAt populated)
PROCESSING → FAILED              (exception during generation; failureReason populated)
```

**Invalid transitions:** COMPLETED → any, FAILED → any. Both are terminal. Any incoming transition returns 409.

**Who triggers:**
- `PENDING → PROCESSING` — background async task (system-initiated)
- `PROCESSING → COMPLETED / FAILED` — background async task (system-initiated)

**Concurrent transition handling:** `@Version` on `RedemptionExportJob`. Duplicate async task execution returns 409 — safe to discard.

---

## Security Design [BE]

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| Transaction amounts, currency types | Confidential — financial data | Tenant-filtered queries; never cross-tenant |
| Export file (CSV/XLSX) | Confidential — financial PII | Stored in object storage with tenant-scoped paths; presigned URL expires in 15 min; file deleted after 24h |
| `requested_by` (UUID) | Internal — pseudonymous | Not exposed in responses; used only for ownership checks |
| `vendorReferenceId` | Confidential — internal | Returned in API only when `status = COMPLETED`; omitted otherwise |
| `failureReason` (raw) | Internal — vendor text | Mapped to a generic user-friendly message in responses; raw text never returned |

### Rate Limiting

| Endpoint | Limit | Scope | Reason |
|---|---|---|---|
| `POST /api/v1/redemption/requests/export` | 5 requests / user / hour | Per user | Expensive — triggers large DB query + file generation |
| `GET /api/v1/redemption/requests/all` | 30 requests / min | Per tenant | Large dataset scan for Client Admin all-tenant view |

_Implemented via `RateLimitFilter` (`com.tenxengage.app.security.RateLimitFilter`). Returns 429 with `Retry-After` header._

### OWASP Risks & Mitigations

| Risk | Where | Mitigation |
|---|---|---|
| **Broken Access Control (A01)** | Export job `/{jobId}` endpoints | Service validates `job.requestedBy == currentUserId` OR `view_all_history` permission; returns 404 on mismatch |
| **IDOR (A01)** | All `/{id}` and `/{jobId}` endpoints | All queries include `clientId` via Hibernate tenant filter; cross-tenant access returns 404 |
| **Injection (A03)** | `status`, `category` filter params | Typed as Java enums in `@RequestParam`; Spring rejects unknown values with 400 automatically |
| **Injection (A03)** | `sortBy` query param | Validated against `Set.of("submittedAt", "amount", "status")` allowlist; unknown value → 400 |
| **Mass Assignment** | `TriggerExportRequest` | Java record — only declared fields bound; `userId`/`companyId` scope override enforced in service (not request DTO) |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `status` query param | `RedemptionStatus` enum | 400 — unknown value |
| `category` query param | `RedemptionCategory` enum | 400 — unknown value |
| `format` in `TriggerExportRequest` | `ExportFormat` enum, `@NotNull` | 400 — missing or unknown value |
| `sortBy` query param | Allowlist: `["submittedAt", "amount", "status"]` | 400 — unknown sort field |
| `pageSize` | `@Max(50)` | 400 — capped |
| `dateFrom`, `dateTo` | `LocalDate`; if both present `dateFrom ≤ dateTo` | 422 — date range invalid |
| `{jobId}` path variable | `@Pattern(regexp="[0-9a-fA-F\\-]{36}")`, `@Size(min=36, max=36)` | 400 — malformed UUID |

---

## Audit Trail [BE]

_Path: `src/main/java/com/tenxengage/app/audit/` (use existing `@Audited` infrastructure)_

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| Export triggered | `RedemptionExportJob` | `jobId`, `requestedBy`, `scope`, `format`, `filterSnapshot` | `CLIENT_ADMIN` |

### New Audit Enum Values

| Enum | New Value | Reason |
|---|---|---|
| `AuditResourceType` | `REDEMPTION_EXPORT_JOB` | New entity type for export job audit tracking |

`AuditAction.DATA_EXPORTED` already exists — reused for the export trigger event.

### `@Audited` Annotation Details (Non-CRUD)

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `POST /api/v1/redemption/requests/export` (async path) | `DATA_EXPORTED` | `REDEMPTION_EXPORT_JOB` | `"Redemption export job triggered"` |
| `POST /api/v1/redemption/requests/export` (sync path) | `DATA_EXPORTED` | `REDEMPTION_REQUEST` | `"Redemption history exported synchronously"` |

_Read-only list and detail endpoints are NOT audited — log volume would be prohibitive with no security value._

_Export job status transitions (PENDING → PROCESSING → COMPLETED / FAILED) are system-driven background operations — not user actions — and are intentionally tracked via observability logs only (see `step=redemption_export_job_completed` etc.), not via `@Audited`._

**Audit record retention:** Indefinite. Audit records are append-only and never soft-deleted.

---

## Observability [BE]

### MDC Fields

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` header | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` from JWT | `TenantFilter` (existing) |
| `userId` | User ID from JWT | `JwtAuthenticationFilter` (existing) |

### Key Log Events

| Event | Level | `step` value | Key Fields | Purpose |
|---|---|---|---|---|
| Export job created | INFO | `redemption_export_job_created` | `jobId`, `scope`, `format`, `userId` | Track export volume |
| Export job processing started | INFO | `redemption_export_job_processing` | `jobId` | Background task tracing |
| Export job completed | INFO | `redemption_export_job_completed` | `jobId`, `rowCount`, `durationMs` | Performance monitoring |
| Export job failed | ERROR | `redemption_export_job_failed` | `jobId`, `failureReason` | On-call alert trigger |
| Sync export completed | INFO | `redemption_sync_export_completed` | `rowCount`, `format`, `durationMs` | Performance monitoring |
| Export access denied | WARN | `redemption_export_access_denied` | `jobId`, `requestedBy`, `callerId` | Security alert |
| Rate limit exceeded | WARN | `rate_limit_exceeded` | `endpoint`, `userId` | Detect abuse patterns |

**Sensitive data in logs:** Never log financial amounts, catalog item details, or user names. Log only entity IDs and UUIDs.

### Metrics

| Metric Name | Type | Labels | Purpose |
|---|---|---|---|
| `redemption.export.triggered.total` | Counter | `scope`, `format`, `mode` (sync/async) | Export volume by type |
| `redemption.export.sync.duration_ms` | Histogram | `format` | Latency monitoring (alert if P95 > 5s) |
| `redemption.export.async.duration_ms` | Histogram | `format`, `scope` | Background job performance |
| `redemption.history.list.duration_ms` | Histogram | `scope` (personal/company/tenant) | List endpoint latency |

---

## Frontend Specification [FE]

_TypeScript types live in `../tenxengage-contracts/` — copy from there, do not hand-write. Full FE file paths and hook specs: see `technical.md`._

### Pages

| Page | Route | Layout | Permission | Sidebar Entry |
|---|---|---|---|---|
| `TransactionHistoryPage` | `/redemption/history` | Partner layout | `module.redemption_store` | Yes — under "Redemption" |
| `TenantTransactionHistoryPage` | `/redemption/admin/history` | `ClientAdminLayout` | `action.redemption.view_all_history` | Yes — under "Redemption" |

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `TransactionHistoryTable` | `scope: 'personal' \| 'company' \| 'all-tenant'`, `filters` | `usePersonalRedemptions / useCompanyRedemptions / useTenantRedemptions` | Tabs for personal/company shown to PARTNER_ADMIN; single view for PARTNER_SELLER |
| `TransactionDetailSheet` | `redemptionId: UUID`, `open: boolean`, `onClose` | `useRedemptionDetail(id)` | Slide-out panel; shows all lifecycle timestamps, vendor ref, linked return |
| `HistoryFilterBar` | `filters`, `onChange` | — | Date range picker (`react-day-picker`), status select, category select |
| `ExportDialog` | `scope`, `filters`, `open`, `onClose` | `useTriggerExport`, `useExportJob` | Shows format selector; switches to polling view for async jobs; shows download button when COMPLETED |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `usePersonalRedemptions(filters, page)` | `['redemption-history', 'personal', { filters, page }]` | `GET /api/v1/redemption/requests` | 2 min | None (read-only data) |
| `useCompanyRedemptions(filters, page)` | `['redemption-history', 'company', { filters, page }]` | `GET /api/v1/redemption/requests/company` | 2 min | None |
| `useTenantRedemptions(filters, page)` | `['redemption-history', 'all-tenant', { filters, page }]` | `GET /api/v1/redemption/requests/all` | 2 min | None |
| `useRedemptionDetail(id)` | `['redemption-history', 'detail', id]` | `GET /api/v1/redemption/requests/{id}` | 5 min | None |
| `useTriggerExport()` | mutation | `POST /api/v1/redemption/requests/export` | — | Stores returned jobId in component state |
| `useExportJob(jobId)` | `['redemption-history', 'export-job', jobId]` | `GET /api/v1/redemption/requests/export/{jobId}` | 0 (always fresh) | Auto-poll every 3s while status PENDING or PROCESSING |

_Note: `useExportJob` uses `refetchInterval: 3000` while status is PENDING or PROCESSING; stops polling when COMPLETED or FAILED._

---

## Caching Strategy [BE]

No server-side caching applied. Transaction history data changes as new redemptions are submitted and vendor webhooks update statuses. Stale reads would display incorrect transaction statuses to users. TanStack Query handles client-side caching with 2-minute stale time for history lists.

---

## Data Retention & Compliance [BE]

### Soft Delete vs Hard Delete

**`RedemptionExportJob`: soft delete** — job records are never hard-deleted; `deleted = true` is the mechanism. Export _files_ in object storage are deleted (hard) after 24 hours (`expires_at`), but the job row is retained.

**`RedemptionRequest`: no change** — retention policy defined by F-03. Indefinite in v1.

### PII Handling

| Field | Entity | PII Type | GDPR Treatment |
|---|---|---|---|
| `requested_by` (UUID) | `RedemptionExportJob` | Pseudonymous ID | Retain — UUID is not PII by itself |
| Transaction records | `RedemptionRequest` | Financial data linked to a user | No change from F-03 policy; indefinite retention in v1 |
| Export file contents | Object storage | Financial PII | Files auto-deleted after 24h (`expires_at`); not subject to GDPR erasure requests (ephemeral) |

### Data Retention Periods

| Data Type | Retention Period | Justification |
|---|---|---|
| `RedemptionExportJob` records | Indefinite | Audit trail of who exported what and when |
| Export files (object storage) | 24 hours post-completion | Ephemeral download artifact; financial PII minimized |
| Audit log entries | Indefinite | Compliance requirement |

### Data Export (GDPR Article 20)

- `redemption_requests.*` — include all fields (excluding `client_id`, `deleted`, `version`) in data subject export
- `redemption_export_jobs.requested_by` — include job records associated with the user (export metadata only; not the file contents)

---

## Modified Existing Endpoints [BE + FE]

| Endpoint | Change | Reason | Breaking? |
|---|---|---|---|
| `GET /api/v1/redemption/requests` | Add optional filter params: `dateFrom` (LocalDate), `dateTo` (LocalDate), `status` (RedemptionStatus), `category` (RedemptionCategory) | FR-05.2 filter requirement | No — additive params with null defaults |
| `GET /api/v1/redemption/requests` | `RedemptionRequestResponse` gains `catalogItemName: String` and `completedAt: Instant` | FR-05.1 requires catalog item name and completion timestamp in list view | No — additive fields |
| `GET /api/v1/redemption/requests/{id}` | `RedemptionRequestDetailResponse` gains `linkedReturnId: UUID` (nullable) | FR-05.3 return linkage | No — additive nullable field |

---

## Planning seeds (from feature brief)

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | View personal redemption history | Partner sees a filterable, status-tracked list of all their redemptions with full detail | UI | F-03.S-01 |
| S-02 | View company redemption history | Partner Admin sees redemption activity for the company wallet separately | UI | F-03.S-02 |
| S-03 | Export personal transaction data | Partner downloads their own redemption history as CSV or XLSX | reporting | S-01 |
| S-04 | View and export tenant-wide history | Client Admin sees and exports redemption activity across all users and companies | reporting | S-01 |

---

## Edge Cases [BE + FE]

1. **Empty history** — 200 with `content: []` and `totalElements: 0`; FE shows `<EmptyState message="No transactions yet" />`.
2. **Export with zero matching records** — 422 with message "No records match the selected filters"; FE shows inline error in ExportDialog.
3. **Sync vs async boundary** — row count is estimated via COUNT query before file generation; ≤ 1,000 → sync path; 1,001+ → async path.
4. **Async export file generation failure** — job transitions to FAILED; `failureReason` set to generic message; FE shows error state in ExportDialog with "Export failed — please try again" and a retry button (re-triggers a new POST).
5. **Polling expired/missing job** — `GET /export/{jobId}` for an expired or unknown job → 404; FE dismisses the ExportDialog.
6. **Non-owner polling another user's export job** — 404 (not 403); same behavior regardless of whether the job exists or belongs to another user.
7. **CLIENT_ADMIN downloading another user's export job** — 200 (allowed via `view_all_history`).
8. **Download endpoint called when job status is PENDING or PROCESSING** — 200 with `RedemptionExportJobDetailResponse` but `downloadUrl: null`; FE remains in polling state.
9. **`linkedReturnId` null (F-06 not yet deployed)** — FE renders the detail sheet without a "View Return" link; no error.
10. **Date filter timezone** — `dateFrom` and `dateTo` are `LocalDate` (no timezone); backend applies them as `submittedAt >= dateFrom T00:00:00Z` and `submittedAt <= dateTo T23:59:59.999Z` using UTC. FE must not use `Date.toISOString()` — use local calendar fields per FE anti-pattern rules.
11. **pageSize > 50** — 400 with message "Page size must not exceed 50"; enforced via `@Max(50)` on controller.
12. **Cross-tenant access** — any entity whose `client_id ≠ TenantContext.getCurrentClientId()` returns 404, never 403.
13. **Concurrent export jobs** — a user may have multiple in-flight export jobs simultaneously (rate limit is time-based, not concurrency-based). Each job is independent. The FE `ExportDialog` tracks the most recently triggered jobId; older in-flight jobs are not surfaced in the UI but remain queryable by jobId.
14. **No filter results vs no transactions** — FE must distinguish two empty states: (a) "No transactions yet" when history is empty with no filters applied; (b) "No transactions match your filters" when filters are active and return zero results. Differentiated by whether any filter param is set.

---

## Out of Scope

- Real-time streaming of new transactions into the history page (Kafka consumer + SSE push) — Phase 2
- Wallet statement view (combined earn + redeem ledger timeline) — Phase 2
- Return transaction submission from history detail (trigger point exists; return workflow is F-06)
- Advanced redemption analytics — by item, tier, region, cohort — Phase 3
- Native mobile export experience — Phase 3
- Streaming export for arbitrarily large datasets (beyond async job approach) — deferred
- Partial return display in history (partial returns are out of scope for v1 per BRD)

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, `@WebMvcTest`, Vitest, E2E Playwright) — generated by `/create-stories` into individual `stories/US-NN-*.md` files alongside the code they verify
- **Cross-story integration tests** (Testcontainers full-lifecycle, tenant isolation, export workflow) — in [`test-plan.md`](test-plan.md)

Key integration scenarios:

| # | Test Class | Scenario | Expected Outcome |
|---|---|---|---|
| T-01 | `RedemptionHistoryControllerTest` | PARTNER_SELLER lists own history — only own records, correct fields | 200, pagination envelope, `catalogItemName` present |
| T-02 | `RedemptionHistoryControllerTest` | Filter by `status=COMPLETED` | 200, all rows have status=COMPLETED |
| T-03 | `RedemptionHistoryControllerTest` | Filter by date range — records outside range excluded | 200, only in-range records |
| T-04 | `RedemptionHistoryControllerTest` | Filter by `category=CASH` | 200, all rows category=CASH |
| T-05 | `RedemptionHistoryControllerTest` | PARTNER_SELLER calls all-tenant endpoint | 403 |
| T-06 | `RedemptionHistoryControllerTest` | CLIENT_ADMIN calls all-tenant endpoint | 200, records from multiple users |
| T-07 | `RedemptionHistoryControllerTest` | Tenant isolation: query as Tenant B | Zero results from Tenant A |
| T-08 | `RedemptionExportServiceTest` | Sync export ≤ 1,000 rows — file returned directly | 200, `Content-Disposition: attachment`, valid file bytes |
| T-09 | `RedemptionExportServiceTest` | Async export > 1,000 rows — job created | 202, jobId in response |
| T-10 | `RedemptionExportControllerTest` | Poll export job PENDING → COMPLETED lifecycle | Correct status at each poll; `downloadUrl` present when COMPLETED |
| T-11 | `RedemptionExportControllerTest` | Different user attempts download of another's export | 404 |
| T-12 | `RedemptionExportControllerTest` | CLIENT_ADMIN downloads another user's export | 200 |
| T-13 | `RedemptionHistoryControllerTest` | Filter with `dateFrom` > `dateTo` | 422 with validation message |
| T-14 | `RedemptionHistoryControllerTest` | Filter with unknown status value | 400 |
| T-15 | `RedemptionHistoryControllerTest` | `pageSize=51` on list endpoint | 400 |
| T-16 | `RedemptionExportServiceTest` | Export triggered with zero matching records | 422 "No records match the selected filters" |

---

## Verification Steps

### Backend Verification
1. `./gradlew bootRun` — app starts; V10 + V11 migrations apply without errors
2. `./gradlew test` — all new and existing tests pass; JaCoCo ≥ 60% line / 50% branch
3. Security spot-checks: cross-tenant `GET /redemption/requests` → 0 results from other tenant; `GET /redemption/requests/all` as PARTNER_SELLER → 403; `GET /redemption/requests/export/{jobId}` as different user → 404; no JWT → 401
4. Export threshold: seed 1,001 redemption records; trigger export → 202 returned; poll until COMPLETED; download URL resolves to valid file
5. Observability: tail logs on export trigger; verify `step=redemption_export_job_created`, `jobId`, `tenantId`, `userId` appear

### Frontend Verification
1. `npm run build` — no TypeScript errors
2. `npm run test` — Vitest passes
3. UI: history list renders with filters; empty state shows when no records; export dialog opens, format selector works, sync export downloads immediately, async export shows polling state then download button; detail sheet shows all lifecycle fields
