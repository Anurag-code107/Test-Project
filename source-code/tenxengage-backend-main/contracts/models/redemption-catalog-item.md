# RedemptionCatalogItem

Platform-level entity (no `client_id`). Represents a globally available redemption item managed by TENX_ADMIN. Analogous to `FeatureFlag` — no tenant isolation filter.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated |
| `name` | string (max 255) | Yes | Display name — never exposes vendor branding |
| `description` | string (max 2000) | No | Rich description |
| `category` | RedemptionCategory | Yes | `CASH` or `NON_CASH`; immutable after creation |
| `currencyId` | string | Yes | Single currency this item redeems from (e.g. `cash`, `points`) |
| `defaultMinRedemptionAmount` | decimal string | Yes | Must be > 0; tenant can only increase via override |
| `defaultProcessingMode` | RedemptionProcessingMode | Yes | Default: `INSTANT` |
| `geographicScope` | string[] | Yes | ISO 3166-1 alpha-2 codes; empty = global |
| `providerItemId` | string (max 255) | No (CASH) / required for NON_CASH activation | Xoxoday product ID or XTRM payout type |
| `isReturnable` | boolean | Yes | Always `false` for CASH (check constraint) |
| `defaultReturnWindowDays` | integer (≥ 0) | Yes | Default: 0 (returns disabled) |
| `isActive` | boolean | Yes | Default: `true`; set to `false` by admin or Xoxoday sync |
| `xoxodayLastSyncedAt` | datetime | No | Null for CASH items |
| `createdAt` | datetime | Yes | Inherited from BaseEntity |
| `updatedAt` | datetime | Yes | Inherited from BaseEntity |

> `syncMetadata` (JSONB) is an internal-only field — never returned in any API response.

## Business Rules

- CASH items: `isReturnable` is always `false` (enforced by DB check constraint and service layer)
- NON_CASH items: cannot be activated unless `providerItemId IS NOT NULL` → 422 on attempt
- `geographicScope` narrowing: rejected if tenant `ClientCatalogRegionConfig` rows exist for removed regions → 422
- Deactivation does NOT cascade to `ClientCatalogItemConfig` rows — tenant config is preserved

## Relationships

- Referenced by: `ClientCatalogItemConfig.redemptionCatalogItemId`
- Referenced by: `ClientCatalogRegionConfig.redemptionCatalogItemId`
