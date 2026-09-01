# ClientCatalogRegionConfig

Tenant-scoped entity. One row per `(client_id, redemption_catalog_item_id, region_code)`. Provides per-region availability overrides on top of the tenant-level `ClientCatalogItemConfig`.

Absence of a row = fall back to `ClientCatalogItemConfig.enabled`.

Tenant-isolated via Hibernate `@Filter(tenantFilter)`.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated |
| `redemptionCatalogItemId` | UUID (FK) | Yes | References `RedemptionCatalogItem` |
| `regionCode` | string (max 10) | Yes | ISO 3166-1 alpha-2; must be in item's `geographicScope` |
| `enabled` | boolean | Yes | Regional enable/disable override |
| `createdAt` | datetime | Yes | Inherited |
| `updatedAt` | datetime | Yes | Inherited |

## Three-Tier Regional Fallback

For a given `(tenant, item, region)` combination:

1. `ClientCatalogRegionConfig` row present → use its `enabled`
2. Row absent → use `ClientCatalogItemConfig.enabled`
3. Neither row exists → item not visible

## Business Rules

- `regionCode` must be a member of `RedemptionCatalogItem.geographicScope` → 422 if not
- Hard delete (no soft delete) — `DELETE` removes the row; absence = fallback to tenant-level
- `DELETE` is idempotent: 204 even if row doesn't exist
- Platform Admin geographicScope narrowing is blocked if rows exist for removed regions (checked cross-tenant)

## Relationships

- References: `RedemptionCatalogItem.id`
- Belongs to tenant via `client_id` (FK → clients)
