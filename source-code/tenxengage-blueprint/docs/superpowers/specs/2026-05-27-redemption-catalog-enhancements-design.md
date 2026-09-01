# Design: Redemption Catalog Enhancements
**Date:** 2026-05-27
**Status:** Approved
**Feature:** Amendments to `features/redemption-catalog` (Robert's review changes)

---

## Overview

Two-phase change set amending the existing redemption-catalog feature:

- **Phase 1 (FE only):** Merge "Global Catalog" and "Redemption Catalog" sidebar links into a single "Redemption Catalog" tab inside Platform Settings.
- **Phase 2 (BE + FE + Contracts):** Enhance the create/edit catalog item form with image upload, currency dropdown from DB, and geographic scope multiselect from DB.

---

## Branches

| Repo | Branch |
|---|---|
| FE | `features/redemption-catalog` — merge `roadmaps/redemption-store` first |
| BE | `features/redemption-catalog` — merge `roadmaps/redemption-store` first |
| Contracts | `features/redemption-catalog` — regenerate after spec amendment |

Sub-branches per session: `work/redemption-catalog-{unit-id}` (local only, squash-merged back).
Changes push to open MRs: FE !2, BE !7.

---

## Phase 1 — Navigation Restructure (FE only)

### Current state
- "Global Catalog" — sidebar link → standalone page (catalog item CRUD, activate/deactivate, Xoxoday sync)
- "Redemption Catalog" — sidebar link → standalone page (tenant config: enable/disable items, regional overrides)

### After change
- Both sidebar links removed
- Platform Settings gains a new tab: **"Redemption Catalog"** positioned between "Manage Business Rules" and "Builder Config"
- Tab bar: Integrations | Manage Data | Manage Business Rules | **Redemption Catalog** | Builder Config | Branding
- "Redemption Catalog" tab has two sub-tabs (same pattern as "Manage Business Rules"):
  - **Catalog Items** — full Global Catalog management UI (unchanged, just relocated)
  - **Tenant Config** — full Redemption Catalog tenant config UI (unchanged, just relocated)

### Routing
- Remove routes for `/global-catalog` and standalone `/redemption-catalog`
- New route: `/settings/platform` with `redemption-catalog` tab active
- Add redirects from old routes if needed

### Tasks
| # | Task |
|---|---|
| T1 | Remove "Global Catalog" + "Redemption Catalog" from sidebar nav |
| T2 | Add "Redemption Catalog" tab to Platform Settings tab bar |
| T3 | Move Global Catalog page content into "Catalog Items" sub-tab |
| T4 | Move Redemption Catalog page content into "Tenant Config" sub-tab |
| T5 | Update routing — remove old routes, add redirects |

---

## Phase 2 — Form Enhancements (BE + FE + Contracts)

### Changes to create/edit catalog item form

| Field | Before | After |
|---|---|---|
| Image | Not present | File picker with preview, optional |
| Currency | Static text input | Dropdown loaded from `GET /api/v1/currencies` |
| Geographic scope | Static text input | Multiselect loaded from `GET /api/v1/location-levels` |

---

### BE changes

#### Migration
```sql
-- V18__add_image_url_to_catalog_items.sql
ALTER TABLE redemption_catalog_items
    ADD COLUMN image_url VARCHAR(2000) NULL;
```
> **Note:** If approval-queue F2 (also targeting V18) merges into `roadmaps/redemption-store` before this, renumber to V19 at merge time.

#### Entity
Add `imageUrl String` (nullable) to `RedemptionCatalogItem`.

#### DTOs
- `RedemptionCatalogItemResponse` — add `String imageUrl` (nullable)
- `CreateRedemptionCatalogItemRequest` — add `@Nullable String imageUrl`
- `UpdateRedemptionCatalogItemRequest` — add `@Nullable String imageUrl`

**Image removal via update:** When `imageUrl` is explicitly `null` in `UpdateRedemptionCatalogItemRequest` and the item currently has an `image_url`, the update service method must delete the old file from storage before setting `image_url = null`. Use `@JsonInclude(JsonInclude.Include.ALWAYS)` on the field so null is serialised (distinguishes "remove image" from "field not sent").

#### Image upload endpoint
```
POST /api/v1/admin/redemption-catalog/{id}/image
Permission: action.redemption.catalog.manage
Consumes: multipart/form-data — file: MultipartFile
Returns: 200 RedemptionCatalogItemResponse (with imageUrl set)
```

Implementation (follows `BrandingController` / `FileStorageService` pattern):
1. Validate file not empty
2. Validate size ≤ 5 MB
3. Validate MIME type: `image/png`, `image/jpeg`, `image/webp`
4. Generate key: `catalog/{itemId}/image-{uuid}.{ext}`
5. Call `fileStorageService.upload(key, stream, size, contentType)`
6. If item had a previous `image_url`, delete old object from storage
7. Save new `image_url` to entity, return updated `RedemptionCatalogItemResponse`

#### No new endpoints needed
- Currency dropdown: `GET /api/v1/currencies` exists — returns `List<CurrencyResponse>` with `id`, `code`, `name`, `type`
- Geographic scope: `GET /api/v1/location-levels` exists — returns `LocationHierarchyResponse` with full tree including `code`, `name`, nested `children`

#### BE tasks
| # | Task |
|---|---|
| B1 | Flyway migration V18 — add `image_url` column |
| B2 | Update `RedemptionCatalogItem` entity with `imageUrl` |
| B3 | Update `RedemptionCatalogItemResponse` DTO |
| B4 | Update create + update request DTOs with optional `imageUrl` |
| B5 | Add `POST /api/v1/admin/redemption-catalog/{id}/image` endpoint |

---

### Contracts changes

All additive — no breaking changes.

| Change | Type |
|---|---|
| `RedemptionCatalogItemResponse` — add `imageUrl: string \| null` | Additive field |
| `CreateRedemptionCatalogItemRequest` — add `imageUrl?: string` | Additive optional field |
| `UpdateRedemptionCatalogItemRequest` — add `imageUrl?: string` | Additive optional field |
| `POST /api/v1/admin/redemption-catalog/{id}/image` — new endpoint | New endpoint |

**Workflow:**
1. Amend `features/redemption-catalog/spec.md` with new field + endpoint
2. `cd ../tenxengage-contracts && /generate-contracts redemption-catalog`
3. Commit contracts update — FE picks up new types

---

### FE changes

#### Currency dropdown
- Replace static currency input with shadcn `Select`
- Load options from `GET /api/v1/currencies` on form mount
- Display: `{name} ({type})` e.g. "USD Points (NON_MONETARY)"
- Save: `currency.code` into `currencyId` field

#### Geographic scope multiselect
- Replace static text input with a multiselect component
- Load options from `GET /api/v1/location-levels` on form mount
- Show tree structure: regions as groups, countries as items within each region
- Both regions and countries are selectable
- Save: array of `location_value.code` values into `geographicScope[]`

**Defensive handling for Xoxoday-synced items:**
Existing catalog items have ISO codes in `geographic_scope[]` (e.g., `"US"`, `"BR"`) that may not match tenant `location_value.code` values. On form load:
- Matched codes → pre-selected in picker
- Unmatched codes → shown as read-only chips labelled "From sync", removable (X) but not re-addable via picker
- On save: picker selection codes + any kept unmatched chips → saved to `geographic_scope[]`

#### Image upload component
- Optional file picker with preview thumbnail
- Accepts: png, jpeg, webp — max 5 MB (matches BE validation)
- On file select: call `POST /api/v1/admin/redemption-catalog/{id}/image`
- Show upload progress + preview thumbnail on success
- "Remove" option clears image (sends `imageUrl: null` on update)
- Disabled state during upload in-flight

#### FE tasks
| # | Task |
|---|---|
| F1 | Currency field → shadcn `Select` with API loading |
| F2 | Geographic scope → tree multiselect with API loading + defensive handling for unmatched codes |
| F3 | Image upload component with preview, validation, progress state |
| F4 | Wire all 3 into create/edit catalog item form |

#### FE tests (Vitest)
- Currency dropdown: renders options from API, saves correct `code` on select
- Geographic scope: renders tree, pre-selects matched codes, shows "From sync" chips for unmatched codes
- Image upload: shows preview on file select, calls upload endpoint, shows Remove button after upload, clears on Remove

---

## Spec files to amend

| File | What changes |
|---|---|
| `features/redemption-catalog/spec.md` | Add `image_url` to entity fields; add upload endpoint + permission; update DTOs; note currency + geo scope from existing APIs; note navigation restructure |
| `features/redemption-catalog/technical.md` | New migration version + column; updated entity fields |
| `features/redemption-catalog/stories/US-01-manage-global-catalog-items.md` | Add nav restructure FE tasks; add form enhancement FE + BE tasks |

---

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| V18 migration conflict with approval-queue F2 | Low | Renumber to V19 at merge time |
| Xoxoday-synced items have ISO codes not matching `location_value.code` | Medium | FE shows unmatched codes as read-only "From sync" chips — no silent data loss |
| `location_value.code` can be null for some values | Low | FE filters out null-code values from picker options |
