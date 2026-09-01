# ApprovalQueueItem

Lightweight read model for the admin approval queue list view. Constructed at query time from
`RedemptionRequest` with JOIN FETCH on `User` and `RedemptionCatalogItem`. Not a persisted entity —
returned only by `GET /api/v1/redemption/requests/approval-queue`.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | `RedemptionRequest.id` — used for approve/reject actions |
| `requestingUserDisplayName` | string | Yes | Joined from `users.display_name` at query time; no per-item lookup |
| `catalogItemId` | UUID | Yes | FK → `redemption_catalog_items.id` — used for filter and FE linking |
| `catalogItemName` | string | Yes | Joined from `redemption_catalog_items.name` at query time |
| `currencyId` | string | Yes | Currency code (e.g. "cash", "points") |
| `amount` | decimal string | Yes | Redemption amount; decimal string to avoid floating-point issues |
| `walletType` | WalletType | Yes | `INDIVIDUAL` or `COMPANY` |
| `submittedAt` | datetime | Yes | ISO 8601 timestamp of partner submission |
| `createdAt` | datetime | Yes | Inherited from BaseEntity |
| `updatedAt` | datetime | Yes | Inherited from BaseEntity |

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Tenant isolation — resolved server-side via Hibernate filter |
| `userId` | Internal reference — `requestingUserDisplayName` is the API surface |
| `status` | Always `PENDING_APPROVAL` in the queue context — omitted to avoid redundancy |

## Notes

- `requestingUserDisplayName` and `catalogItemName` are sourced from JOIN FETCH on
  `r.user.displayName` and `r.catalogItem.name` — never per-item secondary queries
- Callers requiring the full redemption detail, including review decision fields
  (`reviewedBy`, `reviewedAt`, `rejectionReason`), must call
  `GET /api/v1/redemption/requests/{id}` which returns `RedemptionRequestDetailResponse`

## Relationships

- Derives from: `RedemptionRequest` (F-03, `com.tenxengage.app.entity.RedemptionRequest`)
- Joins: `User.displayName` via `RedemptionRequest.userId`
- Joins: `RedemptionCatalogItem.name` via `RedemptionRequest.catalogItemId`
