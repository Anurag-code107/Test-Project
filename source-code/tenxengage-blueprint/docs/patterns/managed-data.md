# Pattern: managed-data

## When this applies

Use this pattern when a feature operates on a **configurable data object** — tenant-editable structured data managed under Platform Settings → Managed Data. The trigger:

1. `/create-spec`'s `entity-shape-decisions.md` procedure (run in step 12, at the start of Data Model authoring) records the entity as a configurable data object — either by user choice for a new entity in this feature, by inheritance from a prior decision in `digest.md`'s `## Entity-shape decisions` section, or by override of a prior decision when the modeling context conflicts with it.

The pattern is loaded JIT in step 12 if the entity-shape procedure marked any entity as configurable and `managed-data` is not already in the shape manifest from step 04.

Configurable data objects coexist with hardcoded entities and builder configs:

| Shape | When to use | Where defined | Where edited |
| --- | --- | --- | --- |
| Hardcoded JPA entity | Schema is fixed; same for every tenant | Java `@Entity` + Flyway | Code only |
| Configurable data object | Tenant admins customize fields at runtime | `data_objects` + `data_object_fields` rows | Platform Settings → Managed Data |
| Builder config | Multi-section wizard whose sections/fields are tenant-customizable | `builder_section_config` + `builder_field_config` | Platform Settings → Builder Config |

A data object often **backs** a builder field — when a field's `valueSource = DATA_OBJECT_FIELD`, the dropdown options come from rows in the linked data object. See `builder-config.md` for that coupling.

## Spec authoring guidance

For each configurable data object the feature introduces or extends, capture:

- **Data object name** — the tenant-visible label and the canonical `name` stored on the `data_objects` row.
- **Default field schema** — table of fields seeded for every tenant, each with: field name, field type (`TEXT`, `NUMBER`, `DROPDOWN`, `DATE`, etc.), `isMandatory`, `isSystem` (platform-defined / locked vs tenant-customizable), default value (if any).
- **Value sources** — for fields that present options, name the value source (`STATIC`, `LOCATION_HIERARCHY`, `CLIENT_ROLES`, `ACTIVITY_CATEGORIES`, or another data object's field). Reuse the taxonomy from `builder-config.md`. For location hierarchy details, see [location-hierarchy.md](../patterns/location-hierarchy.md).
- **Tenant scoping** — explicit confirmation that the data object and its field rows are tenant-scoped (carry `client_id`). Every tenant gets a per-tenant copy of the seed schema.
- **Seed Flyway expectations** — a Flyway migration that inserts the seed `data_objects` row + `data_object_fields` rows per tenant, NOT global. Spec must call out the expected migration name and which tenants it seeds for (existing tenants on first deploy + new-tenant provisioning hook).
- **Builder-config coupling** — if this data object backs a builder field via `DATA_OBJECT_FIELD` value source, name the builder field and which `BuilderFieldConfig` row links to it. Cross-reference `builder-config.md`.
- **Permissions** — read access (typically `action.{module}.view`), edit access (typically `action.dataobject.manage` or feature-specific equivalent). Confirm against the existing controller's required permissions.
- **Audit policy** — what audit events fire on field add/update/delete (typically through the standard `@Audited` policy on `DataObjectField`).

## Implementation guidance

### Backend

#### Entities

- `DataObject` (`../tenxengage-backend/src/main/java/com/tenxengage/app/entity/DataObject.java`) — tenant-scoped (`client_id` NOT NULL); stores the data object's name, label, and metadata.
- `DataObjectField` (`../tenxengage-backend/src/main/java/com/tenxengage/app/entity/DataObjectField.java`) — individual field on a data object; stores type, value source, mandatory/system flags, sort order. Both inherit from `BaseEntity` (UUID id, `client_id`, audit timestamps).

#### DataObjectField extensions (2026-05-25)

Five columns extend `data_object_fields` to make tenant-defined fields drive both the data-uploader and the audience-rule engine without a separate catalog:

| Column | Meaning |
| --- | --- |
| `mapped_column` | When set, the field's runtime value is read from this named column on the parent entity (resolved via `DataObject.maps_to_entity`). When null, the value is read from the entity's `metadata` JSONB at the field's `name` key. |
| `is_system` | System-seeded row (`status`, `email`, `role`, location-level fields). Cannot be deleted or have `name` / `mapped_column` / `value_source` / `location_level_id` changed by admins. |
| `value_source` | `STATIC` (use `sampleValues`), `LOCATION_HIERARCHY` (resolve via `location_level_id`), `CLIENT_ROLES` (resolve to the tenant's role list). Required for fields exposed as audience-rule predicates. |
| `location_level_id` | FK to `location_levels`. Set only when `value_source = LOCATION_HIERARCHY`. |
| (`DataObject` level) `maps_to_entity` | Enum: PARTNER_COMPANY, USER (future CUSTOMER, etc.). Always set. |
| (`DataObject` level) `is_audience_eligible` | When true, the DataObject's fields can be picked as audience-rule predicates. Default false. |

A DB CHECK enforces that `value_source = 'LOCATION_HIERARCHY'` always pairs with a non-null `location_level_id`, and vice versa.

System rows are seeded via Flyway and are skipped by the tenant-admin "Add Field" dialog (which always inserts `is_system = false`, `mapped_column = null`).

#### Service Layer

- `DataObjectService` (`../tenxengage-backend/src/main/java/com/tenxengage/app/service/DataObjectService.java`) — CRUD for data objects and their fields, scoped to the tenant resolved from `TenantContext`. Validates that system fields cannot be deleted or have their `name`/`type` mutated (only `displayName` and a small set of behavioral flags).

#### Controller

- `DataObjectController` (`../tenxengage-backend/src/main/java/com/tenxengage/app/controller/DataObjectController.java`) — HTTP surface for the admin UI. Confirm required permissions when extending.

#### Repositories

- `DataObjectRepository`, `DataObjectFieldRepository` — every query method MUST include `clientId`. Cross-tenant reads are a tenant-isolation breach.

#### Seed Data

When introducing a new data object, seed the platform-defined schema via a Flyway migration in `tenxengage-backend/src/main/resources/db/migration/`. Seed must:

- Be tenant-scoped — INSERT one `data_objects` row per existing tenant (loop or `INSERT ... SELECT FROM clients`).
- INSERT all default `data_object_fields` rows per tenant, with `is_system = TRUE` for fields that cannot be deleted.
- Be idempotent — guard with `WHERE NOT EXISTS` so re-runs do not duplicate seeds.

For new-tenant provisioning, ensure the tenant-creation flow (typically in `ClientService` or equivalent) seeds the default schema for the newly created tenant.

### Frontend

#### Admin surface

- `/settings/platform` → **Managed Data tab** — primary entry point for admins to view, add, and edit data objects.
- `DataObjectDetail.tsx` (`../tenxengage-frontend/src/components/settings/DataObjectDetail.tsx`) — detail / edit screen for a single data object, including its field list.

#### API hook

- `useDataObjectApi.ts` (`../tenxengage-frontend/src/hooks/useDataObjectApi.ts`) — TanStack Query wrapper around the data-object endpoints. Reuse for any feature reading data-object values; do not introduce a parallel hook.

#### Field rendering

- `DynamicFieldRenderer.tsx` (`../tenxengage-frontend/src/components/DynamicFieldRenderer.tsx`) — the data-object field renderer. **Do not confuse this with `../tenxengage-frontend/src/components/incentive-builder/DynamicFieldRenderer.tsx`**, which is the builder-config field renderer. They are similarly named but are independent components serving different shapes.

#### Reading values

When a feature needs to read a data object's values (e.g., to populate a dropdown), use `useDataObjectApi`'s field-values endpoint rather than fetching the raw `data_object_fields` table. The hook handles tenant-scoping, caching, and pagination.

## Examples in codebase

- Backend entities: `../tenxengage-backend/src/main/java/com/tenxengage/app/entity/DataObject.java`, `DataObjectField.java`
- Backend service: `../tenxengage-backend/src/main/java/com/tenxengage/app/service/DataObjectService.java`
- Backend controller: `../tenxengage-backend/src/main/java/com/tenxengage/app/controller/DataObjectController.java`
- Frontend hook: `../tenxengage-frontend/src/hooks/useDataObjectApi.ts`
- Frontend admin UI: `../tenxengage-frontend/src/components/settings/DataObjectDetail.tsx`
- A real production example: the `Partner Type` lookup values (`Reseller`, `Distributor`, `OEM`) are stored as rows in a `data_object_fields` table — see `.claude/skills/decompose-brd/references/system-catalog.md` for the canonical list.
- For audience-rule eligibility use of data objects, see [audience-rules.md](audience-rules.md).

## Common gotchas

- **Confusing `DataObject` (this pattern) with `BuilderFieldConfig` (the `builder-config.md` pattern).** They look similar (both store fields with type + value source) but serve different purposes: data objects are tenant-editable structured data records; builder configs are sections/fields in a multi-step wizard. A builder field can REFERENCE a data object via `valueSource = DATA_OBJECT_FIELD`, but they are not the same construct.
- **Forgetting `client_id` on a query.** Tenant-isolation breach. Every repository method MUST filter by `clientId`.
- **Seeding globally instead of per-tenant.** A Flyway that does `INSERT INTO data_objects (...)` without tying each row to a `client_id` will fail or create shared state. Use `INSERT ... SELECT FROM clients` (or a loop) to seed one row per tenant.
- **System fields are immutable via API.** `isSystem = TRUE` fields can have `displayName` and a few flags edited but cannot be deleted or have their `name`/`type` changed. Don't expose full editability for system fields in new endpoints.
- **New-tenant provisioning gap.** A Flyway migration only seeds existing tenants at deploy time. New tenants created later must get the schema from the tenant-provisioning code path. Verify both paths are wired before shipping.
- **`DynamicFieldRenderer` collision.** Two components with the same name exist (`src/components/DynamicFieldRenderer.tsx` for data objects, `src/components/incentive-builder/DynamicFieldRenderer.tsx` for builder-config). Import the right one — the path-level distinction is the only signal; the symbol name itself collides.
