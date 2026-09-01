# Builder Configuration Pattern — Backend

> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes the `BuilderSectionConfig` / `BuilderFieldConfig` /
> `BuilderConfigService` stack which serves the incentive domain. Status per
> the [domain registry](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md):
> `active-legacy` (see [incentive.md](../../../tenxengage-blueprint/docs/patterns/domains/incentive.md)).
> The code stays in production; the file stays for reference. **New code should
> not adopt this pattern.**
>
> **Implementing a feature for a new domain (enablement, future)?**
> Do NOT follow this pattern. New domains use **platform primitives**:
> `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
> per [platform-primitives.md](../../../tenxengage-blueprint/docs/patterns/domains/platform-primitives.md).
> The platform-primitives implementation does not exist in code yet — the
> first feature landing on platform primitives builds it, guided by:
> - The slot list and naming convention in [domains/INDEX.md](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md)
> - The design at [docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md](../../../tenxengage-blueprint/docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md)
>
> **First engineer to land platform primitives:** as a final deliverable of
> your feature, write `builder-config-platform.md` in this directory (or a
> naturally-named equivalent once implementation has clarified the structure)
> and register it in the blueprint's [patterns INDEX](../../../tenxengage-blueprint/docs/patterns/INDEX.md).
> This redirection block points at your file once it exists.

---

How the backend manages dynamic, tenant-scoped builder configuration. Tenants customize which sections and fields appear in the incentive builder, their order, and how field values resolve at runtime.

## Overview

Two-level hierarchy: **sections contain fields**. Each section belongs to one incentive type (`SALES`, `TRAINING`, `ACTIVITY`, `JOURNEY`, etc.) and is tenant-scoped via `client_id`. Fields define the form controls — type, validation, value source for selectable options.

New incentive types need Flyway-seeded baseline configuration so every tenant starts with usable defaults.

## Entities

Both extend `BaseEntity` and the section is `TenantAware`. Read [BuilderSectionConfig.java](../../src/main/java/com/tenxengage/app/entity/BuilderSectionConfig.java) and [BuilderFieldConfig.java](../../src/main/java/com/tenxengage/app/entity/BuilderFieldConfig.java) for the canonical column list — the conceptually-important fields are:

### `BuilderSectionConfig`

| Field | Purpose |
|---|---|
| `incentiveType` | Which builder this section belongs to (`SALES`, `TRAINING`, `ACTIVITY`, `JOURNEY`) |
| `sectionKey` / `displayName` / `subtitle` | Identifier + UI strings |
| `sortOrder` | Display order within the builder |
| `isLocked` | When true: cannot add/remove fields. Used for system-defined sections. |
| `isVisible` | Hide section without deleting it |
| `fields` | `@OneToMany` cascade-all + orphan-removal, `@OrderBy("sortOrder ASC")` |

### `BuilderFieldConfig`

| Field | Purpose |
|---|---|
| `fieldKey` / `displayName` / `helperText` | Identifier + UI strings |
| `fieldType` | One of: `TEXT_BOX`, `DROPDOWN`, `MULTI_SELECT`, `DATE_PICKER`, `NUMBER_INPUT`, `TOGGLE`, `TEXT_AREA` |
| `isMandatory` | Required at submit time |
| `isSystem` | True for Flyway-seeded fields — only safe properties are editable, deletion blocked |
| `valueSource` | Strategy key — see Value Resolution below |
| `valueSourceConfig` | JSONB; source-specific parameters (e.g. location depth, role-type filter) |
| `dataObjectField` | Optional FK to `DataObjectField` for sample-value-based dropdowns |
| `sectionConfig` | Parent section FK |

## Service operations

`BuilderConfigService` is tenant-scoped — every operation reads `TenantContext.getClientId()`.

- **`getBuilderConfig(incentiveType)`** — `@Transactional(readOnly = true)`. Returns the full section→field tree for the current tenant + incentive type, ordered by `sortOrder`.
- **`addField(sectionId, request)`** — Validates section is not locked. Auto-assigns next `sortOrder`. Forces `isSystem = false` (custom fields cannot be created as system).
- **`updateField(fieldId, request)`** — Branches on `isSystem`: system fields only allow `displayName`, `helperText`, `isMandatory`, `isEligibility`. Custom fields allow all properties including `valueSource`, `dataObjectField`.
- **`removeField(fieldId)`** — Throws `IllegalStateException` if `isSystem` is true; otherwise deletes.

System-vs-custom rule: **system fields are baseline configuration that came from Flyway seed; tenants can tweak presentation but not structure or deletion. Custom fields are tenant-created and fully editable.**

## Value Resolution

`resolveFieldValues(fieldId, context)` dispatches on `field.getValueSource()`:

| Source | Resolution |
|---|---|
| `LOCATION_HIERARCHY` | Reads `valueSourceConfig` for `depth` (top-level) or `parentField` (cascading children). Queries `LocationValue` scoped to tenant. See [location-hierarchy.md](location-hierarchy.md) for full pattern. |
| `CLIENT_ROLES` | Tenant's roles, optionally filtered by `roleType` from `valueSourceConfig`. |
| `DATA_OBJECT_FIELD` | `sampleValues` JSON from the linked `DataObjectField`. |
| `ACTIVITY_CATEGORIES` | All `ActivityCategory` for the tenant, ordered by `sortOrder`. |
| `STATIC` | Parses `valueSourceConfig.values` as a JSON array of `{value, label}` objects or plain strings. |

The `context` parameter supports cascading: a "City" dropdown depending on a "Region" dropdown passes selected region IDs in `context`, and the location hierarchy resolver uses them to fetch child values.

## Controller permissions

`BuilderConfigController` is thin; permissions are the load-bearing detail:

| Endpoint | Permission |
|---|---|
| `GET  /api/v1/builder-config/{incentiveType}` | `action.incentive.view` |
| `GET  /api/v1/builder-config/fields/{fieldId}/values` | `action.incentive.view` |
| `POST /api/v1/builder-config/sections/{sectionId}/fields` | `action.builder.manage` |
| `PUT  /api/v1/builder-config/fields/{fieldId}` | `action.builder.manage` |
| `DELETE /api/v1/builder-config/fields/{fieldId}` | `action.builder.manage` |

POST returns `201 Created`, DELETE returns `204 No Content` (per CLAUDE.md API conventions).

## Adding a new INCENTIVE_TYPE — checklist

> ⚠️ **This checklist applies only to adding a new variant within the
> incentive domain** (e.g., a fifth value beyond `SALES`, `TRAINING`,
> `ACTIVITY`, `JOURNEY`). For a new DOMAIN (enablement, future), see the
> redirection block at the top of this file — do not follow this checklist.


1. **Flyway migration** seeding `BuilderSectionConfig` and `BuilderFieldConfig` rows **for every existing tenant** (loop over `clients`). System fields: `is_system = true`. Locked sections: `is_locked = true`.
2. Include the new `incentive_type` enum value so the frontend can `GET /builder-config/{type}`.
3. **Every seed row must include `client_id`** — tenant isolation does not apply to seeds; you populate it explicitly.
4. If the new type uses a new value source (rare), add a branch to the `resolveFieldValues` switch in `BuilderConfigService`.
