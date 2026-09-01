# RedemptionExportJob

Async export job for redemption transaction history. Created when a `POST /export` request
would return > 1,000 rows; callers poll until COMPLETED then fetch a presigned download URL.
Extends `BaseEntity`, implements `TenantAware`. Carries `@Filter(name="tenantFilter")` and
`@Version` for optimistic locking.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated — used as jobId in poll/download URLs |
| `requestedBy` | UUID | Yes | FK → `users.id` — owner of this export job; used for download access control |
| `status` | RedemptionExportStatus | Yes | Default `PENDING`; transitions: PENDING → PROCESSING → COMPLETED / FAILED |
| `format` | ExportFormat | Yes | `CSV` or `XLSX` — format of the generated file |
| `scope` | string | Yes | `PERSONAL`, `COMPANY`, or `ALL_TENANT` — determines query scope at async generation time |
| `filterSnapshot` | JSONB | Yes | Filters applied at trigger time (dateFrom, dateTo, status, category, userId, companyId); replayed during async generation |
| `rowCount` | integer | No | Populated on COMPLETED; null while PENDING / PROCESSING / FAILED |
| `fileKey` | string (max 500) | No | Internal object storage key for the generated file. **Never returned in API responses.** |
| `expiresAt` | datetime | No | Download link expiry — set to NOW() + 24h on COMPLETED; null until then. File is deleted from storage when this timestamp passes. |
| `failureReason` | string (max 500) | No | Generic failure description. Populated on FAILED; null otherwise. |
| `createdAt` | datetime | Yes | Inherited from BaseEntity |
| `updatedAt` | datetime | Yes | Inherited from BaseEntity |

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Tenant isolation — resolved server-side via Hibernate filter |
| `fileKey` | Internal storage reference — callers receive a presigned URL, never the raw key |
| `deleted` | Soft delete flag; internal |
| `version` | Optimistic lock counter; internal |

## Status Transitions

```
[TRIGGER] → PENDING              (job row created; async task queued)
PENDING → PROCESSING             (async task picks up; begins query + file generation)
PROCESSING → COMPLETED           (file written to storage; fileKey + rowCount + expiresAt set)
PROCESSING → FAILED              (exception during generation; failureReason set)
```

`COMPLETED` and `FAILED` are terminal — any incoming transition returns 409.
Concurrent task execution resolved via `@Version` optimistic locking.

## Business Rules

- Export job access: `requestedBy == currentUserId` OR caller has `action.redemption.view_all_history`; otherwise 404 (not 403)
- Download URL only available when `status = COMPLETED`; presigned URL generated fresh per request, valid 15 minutes
- Export file auto-deleted from object storage when `expiresAt` passes (24 hours post-completion); job row retained indefinitely for audit
- Job creation triggers `processExportJob(jobId)` asynchronously in the same request thread lifecycle (via `@Async`)
- `scope` is captured at trigger time and replayed during async file generation to ensure consistent results

## Multi-Tenancy

- Tenant-scoped via `client_id` (Hibernate `@Filter` — never exposed in responses)
- `clientId` resolved from `TenantContext.getCurrentClientId()` at trigger time

## Relationships

- `@ManyToOne` → `User` (FK: `requested_by`) — non-lazy for ownership checks

## Indexes

- `idx_redemption_export_jobs_client_id` on `(client_id)`
- `idx_redemption_export_jobs_client_requester` on `(client_id, requested_by)`
- `idx_redemption_export_jobs_client_status` on `(client_id, status)`
- `idx_redemption_export_jobs_client_created` on `(client_id, created_at DESC)`
