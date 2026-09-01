# ClientCatalogItemConfig

Tenant-scoped entity. One row per `(client_id, redemption_catalog_item_id)`. CLIENT_ADMIN creates or updates this to enable/disable and override settings for a specific catalog item within their tenant.

Tenant-isolated via Hibernate `@Filter(tenantFilter)`.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated |
| `redemptionCatalogItemId` | UUID (FK) | Yes | References `RedemptionCatalogItem` |
| `enabled` | boolean | Yes | Whether item is visible in partner browse |
| `processingModeOverride` | RedemptionProcessingMode | No | Null = inherit `defaultProcessingMode` |
| `minTransactionAmountOverride` | decimal string | No | Must be ≥ global `defaultMinRedemptionAmount`; null = inherit |
| `maxTransactionAmountOverride` | decimal string | No | Must be ≤ global `defaultMaxRedemptionAmount` when the item has one; null = inherit |
| `minWalletBalanceOverride` | decimal string (≥ 0) | No | Balance check at submit time; null = 0 |
| `returnWindowDaysOverride` | integer (≥ 0) | No | Null = inherit `defaultReturnWindowDays`; 0 = disabled |
| `version` | integer | Yes | Optimistic lock; 409 on concurrent update conflict |
| `createdAt` | datetime | Yes | Inherited |
| `updatedAt` | datetime | Yes | Inherited |

## Effective Value Resolution (consumed by browse + F-03 redemption)

| Field | Formula |
|---|---|
| `effectiveProcessingMode` | `COALESCE(processingModeOverride, item.defaultProcessingMode)` |
| `effectiveMinTransactionAmount` | `COALESCE(minTransactionAmountOverride, item.defaultMinRedemptionAmount)` |
| `effectiveMaxTransactionAmount` | `COALESCE(maxTransactionAmountOverride, item.defaultMaxRedemptionAmount)` — null = no ceiling |
| `effectiveMinWalletBalance` | `COALESCE(minWalletBalanceOverride, 0)` |
| `effectiveReturnWindowDays` | `COALESCE(returnWindowDaysOverride, item.defaultReturnWindowDays)` |

## Business Rules

- Amount overrides may only **narrow** the item's range, never widen it:
  - `minTransactionAmountOverride` cannot be less than `RedemptionCatalogItem.defaultMinRedemptionAmount` → 422
  - `maxTransactionAmountOverride` cannot be greater than `RedemptionCatalogItem.defaultMaxRedemptionAmount` → 422 (an item with a null ceiling accepts any positive override, since a finite ceiling still narrows)
  - the resulting minimum cannot exceed the resulting maximum → 422 (this is what stops a FIXED denomination being squeezed out of its own window)
- Both bounds are re-checked at submit time in `RedemptionSubmissionService` (personal + company paths), which is authoritative; the FE mirrors them for inline field errors
- `PUT` with `enabled=true` while `RedemptionCatalogItem.isActive=false` → 404
- Rows are preserved when the parent `RedemptionCatalogItem` is deactivated — not cascaded
- Concurrent updates detected by `@Version` → 409 "Configuration was updated concurrently"

## Relationships

- References: `RedemptionCatalogItem.id`
- Referenced by: `ClientCatalogRegionConfig` (regional overrides layer on top of this)
