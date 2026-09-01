# RedemptionWebhookEvent

Inbound vendor webhook event record. Enables idempotent webhook processing and dead-letter queue investigation. No tenant filter applied at webhook receipt — `clientId` is resolved from the referenced `RedemptionRequest`.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated |
| `vendor` | string | Yes | `XTRM` or `XOXODAY` — set from URL path |
| `redemptionRequestId` | UUID | Yes | FK → redemption_requests |
| `idempotencyKey` | string (max 255) | Yes | Vendor's event ID — UNIQUE constraint — used to detect duplicate delivery |
| `status` | WebhookStatus | Yes | Processing state |
| `receivedAt` | datetime | Yes | ISO 8601 timestamp when webhook arrived |
| `processedAt` | datetime | No | Timestamp when processing completed |
| `createdAt` | datetime | Yes | Inherited from BaseEntity |
| `updatedAt` | datetime | Yes | Inherited from BaseEntity |

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Resolved from `RedemptionRequest`; internal |
| `payload` | Raw JSONB — stored for audit/DLQ investigation only; never returned via API |
| `failureReason` | Processing error detail for DLQ investigation; internal |
| `version` | Optimistic lock counter; internal |
| `deleted` | Soft delete flag; internal |

## Business Rules

- Before processing: check `idempotencyKey` in existing records
  - If found with status=`PROCESSED` or `DUPLICATE` → log `webhook_duplicate_discarded` + return 200 (discard); no ledger mutation
- Webhook arriving for a `COMPLETED` or `FAILED` redemption → log `webhook_stale_received` audit step + return 200; no state change
- `clientId` resolved from `RedemptionRequest.clientId` at receipt time (no JWT context for vendor callbacks)
- HMAC-SHA256 signature verified before any processing; invalid signature returns 401

## Multi-Tenancy

- `clientId` set from `RedemptionRequest.clientId` before persist; not from JWT (vendor callbacks have no JWT)

## Relationships

- `@ManyToOne` → `RedemptionRequest` (FK: `redemption_request_id`)
