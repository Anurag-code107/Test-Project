# Location Hierarchy Pattern — Backend

Storage model, entity shapes, and service contracts for the tenant-scoped location hierarchy tree.

## Overview

Two tables: `LocationLevel` (metadata) and `LocationValue` (tree nodes). Hierarchy is tenant-scoped via `client_id`. Storage is plain `parent_id` self-FK — no closure table, no materialized path. **Key implication:** no SQL ancestor/descendant traversal. Tree traversal happens at DTO serialization.

## Entities

Read the canonical source: `src/main/java/com/tenxengage/app/entity/LocationLevel.java` and `LocationValue.java`

**Critical fields:**

### LocationLevel
- `depth` (int >= 0) — ordinal position in tree (0 = root). Auto-incremented on level creation
- `useInBuilder`, `useInFilters` (boolean) — independent feature flags. Control which UI surfaces show this level
- `isRequired` (boolean) — must user select a value at this level? **Invariant: only depth 0 is required**
- Name uniqueness: `(client_id, name)` unique. Tenants can't create two levels with the same name

### LocationValue
- `parent_id` (UUID, nullable) — self-reference. Null = root level. No descendants computed; children are fetched via lazy `@OneToMany` at serialization time
- `level_id` (UUID) — which level this value belongs to (FK)
- `code` (varchar, optional) — short identifier for bulk uploads
- Name uniqueness: `(client_id, level_id, name)` unique **only at root** (where parent_id IS NULL). Siblings under a parent can share names

## Service layer

`LocationService` key methods:

| Method | Notes |
|---|---|
| `getHierarchy()` | Full tree: all levels + all root values with recursively-loaded children. **Use this for complete initialization.** No filtering by flags |
| `getBuilderOptions()` | Levels where `useInBuilder = true` + their values. Called by AiChatService to build copilot prompt |
| `getFilterOptions()` | Levels where `useInFilters = true` + flat (non-recursive) values with `parentId` included. Called by admin filter widgets |
| `createLevel(request)`, `updateLevel(levelId, ...)`, `deleteLevel(levelId)` | Standard CRUD. **Invariant:** `isRequired` can only be toggled to true at depth 0; never allow depth > 0 to be required |

See `src/main/java/com/tenxengage/app/service/LocationService.java` for signatures and full logic

## API endpoints

All tenant-scoped. Require `action.location.view` (read) or `action.location.manage` (write).

| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/location-levels` | `LocationHierarchyResponse` (full tree) |
| GET | `/api/v1/location-levels/builder-options` | `List<LocationLevelResponse>` (metadata only, values separate) |
| GET | `/api/v1/location-levels/filter-options` | `LocationFilterOptionsResponse` (flat values + parentId) |
| POST | `/api/v1/location-levels` | Create level |
| POST | `/api/v1/location-levels/values` | Create value |
| PATCH | `/api/v1/location-levels/{id}/settings` | Update flags (useInBuilder, useInFilters, isRequired) |
| DELETE | `/api/v1/location-levels/{id}` | Delete level |
| DELETE | `/api/v1/location-levels/values/{id}` | Delete value |

## AI copilot integration

`AiChatService.formatLocationHierarchyBlock()` (lines ~692–717) injects the location hierarchy into the copilot system prompt. Format includes level names, value names, parent relationships, and depth. This allows the AI to emit valid `locationSelections` and `locationAllocations` in UPDATE_AUDIENCE and UPDATE_BUDGET payloads

See `src/main/java/com/tenxengage/app/service/AiChatService.java:646–717`

## Anti-patterns

- ❌ Write a `WITH RECURSIVE` query to fetch all descendants — the storage model doesn't support it efficiently. Use `LocationService.getHierarchy()` and filter in Java
- ❌ Bypass `client_id` scoping when writing repository queries — all location queries must be tenant-scoped
- ❌ Allow `isRequired = true` for depth > 0 — only root level can be required. Toggle methods must validate this
- ❌ Assume a fixed set of levels (Region/Country) — always query from the database; tenants may customize depth and naming
