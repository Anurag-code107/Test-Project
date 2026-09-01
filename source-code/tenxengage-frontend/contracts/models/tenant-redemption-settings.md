# TenantRedemptionSettings

Tenant-scoped entity. One row per tenant (`UNIQUE` on `client_id`). Stores tenant-wide redemption configuration. Auto-created with `batchCadence=DAILY` on first access (find-or-create with `SELECT FOR UPDATE`).

Tenant-isolated via Hibernate `@Filter(tenantFilter)`.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated |
| `batchCadence` | BatchCadence | Yes | Default: `DAILY`. Governs when BATCH-mode redemptions are processed (F-03). |
| `createdAt` | datetime | Yes | Inherited |
| `updatedAt` | datetime | Yes | Inherited |

## Business Rules

- Auto-created on `GET /api/v1/redemption/settings` if no row exists — never returns 404
- Changing `batchCadence` mid-batch does NOT affect in-flight redemptions — only the next scheduled batch run uses the new cadence
- Race condition on first access handled by `SELECT FOR UPDATE` — only one row created per tenant

## Relationships

- One per tenant (`client_id` UNIQUE FK → clients)
- `batchCadence` is read by `RedemptionCatalogBrowseService` when computing `estimatedPayoutTimeline` for BATCH items
