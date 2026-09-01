# Pattern: builder-config

## When this applies

A feature uses **dynamic builder configuration** — sections, fields, their ordering, and behavioral flags are fetched from the server rather than hardcoded in the UI. This co-occurs with the [builder-wizard](builder-wizard.md) pattern; it's registered separately because a feature may add new fields to an existing builder without introducing a new wizard UI.

## Spec authoring guidance

For each new builder, the spec must declare:

1. **Classification:** `builder_type` + `builder_domain` (e.g. `COURSE` + `ENABLEMENT`). Legacy incentive variants use `incentive_type` instead — the row uses one classification or the other, never both. Enforced by DB CHECK.
2. **Sections:** ordered list of `BuilderSectionConfig` rows. Each has `section_key`, `display_name`, ordinal, `info_message`, optional `is_audience_section` flag.
3. **Fields per section:** `BuilderFieldConfig` rows. Each carries `field_key`, `display_name`, `helper_text`, `field_type` (TEXT/NUMBER/ENUM/DATE/BOOLEAN/LOCATION), `data_object_field_id` (the underlying tenant-defined field; nullable for hardcoded fields), `value_source` + `value_source_config` for the `/values` endpoint, `is_audience_field`, `is_mandatory`, `supports_excel_upload`.
4. **Audience eligibility:** if any section has `is_audience_section = true`, each field's row must satisfy the audience contract (`is_audience_field = true`, valid `data_object_field_id`, operator metadata). See [audience-rules.md](audience-rules.md).
5. **Cascading hierarchy fields:** for LOCATION_HIERARCHY value sources, cascading is **inferred** from the underlying `DataObjectField.location_level_id` paired with `LocationLevel.depth`. No `parent_field_id` column. See [location-hierarchy.md](location-hierarchy.md).

## Read endpoints

One row carries exactly one classification, and each is read by a matching route:

- **Enablement builders** — `GET /builder-config/enablement/{builderType}` (discriminated by `builder_type` within `builder_domain=ENABLEMENT`), backed by `BuilderConfigService.loadForBuilder(clientId, builderType, domain)`. COURSE keeps `GET /builder-config/course` as a back-compat alias. The FE consumes this via `useBuilderDefinition(builderType)`.
- **Incentive builders** — `GET /builder-config/{incentiveType}` (discriminated by `incentive_type`), unchanged.

## Implementation guidance

### Schema (one table per concept; both classifications coexist)

```sql
builder_section_configs (
  id, client_id,
  section_key, display_name, ordinal, is_audience_section, info_message,
  -- exactly one classification:
  incentive_type     VARCHAR(20)  NULL,   -- incentive callers
  builder_type       VARCHAR(50)  NULL,   -- course/future
  builder_domain     VARCHAR(50)  NULL,   -- course/future
  version, deleted, created_at, updated_at,
  CHECK (
    (incentive_type IS NOT NULL AND builder_type IS NULL AND builder_domain IS NULL)
    OR
    (incentive_type IS NULL AND builder_type IS NOT NULL AND builder_domain IS NOT NULL)
  )
);

builder_field_configs (
  id, client_id, builder_section_config_id,
  field_key, display_name, helper_text,
  field_type, ordinal, is_mandatory, is_audience_field, supports_excel_upload,
  value_source, value_source_config,
  data_object_field_id,           -- FK to data_object_fields, nullable
  version, deleted, created_at, updated_at
);
```

Soft-delete via `@SQLRestriction("deleted = false")` (see [new-entities.md](new-entities.md) for the standard soft-delete setup).

### Value resolution (`GET /builder-config/fields/{fieldId}/values`)

The endpoint reads `value_source` + `value_source_config` from `builder_field_configs` and dispatches to a resolver:

- `STATIC` → returns the inline list in `value_source_config.values`.
- `LOCATION_HIERARCHY` → resolves the level (`value_source_config.locationLevelId`) and returns the tenant's locations at that level.
- `CLIENT_ROLES` → returns the tenant's roles.

The same per-builder override pattern lets the admin pick a different resolution than the underlying `DataObjectField.value_source` provides (e.g. a STATIC override for a field whose underlying DataObjectField is LOCATION_HIERARCHY).

### How incentive uses it

Incentive admin endpoints (`/api/v1/builder-config/incentive/{incentiveType}`) read rows with `incentive_type IS NOT NULL`. The shipped admin UI (`BuilderFieldEditor.tsx`) is unchanged. Untouched.

### How course / future builders use it

Course admin endpoints (`/api/v1/builder-config/course`) read rows with `builder_type = COURSE AND builder_domain = ENABLEMENT`. Adding a new builder type means:

1. Add the enum value to `BuilderType` / `BuilderDomain`.
2. Seed `BuilderSectionConfig` + `BuilderFieldConfig` rows in a Flyway migration.
3. Wire a new admin endpoint that delegates to `BuilderConfigService.loadFor(builderType, builderDomain)`.

No platform-primitives "Definition" classes — there is one unified stack.

### Locked (system-managed) sections

A section row carries two fields that together mark it **system-managed**: `is_locked` (boolean) and `info_message` (text). A locked section is one whose content is authored elsewhere (a dedicated full-panel editor, a platform widget, or a terminal action) rather than by free-form field editing in the accordion — e.g. `basics`, `tags`, `publish`, and module-specific system sections like LEARNING_PATH `composition`.

The contract has two halves:

1. **Seed-time (backend):** every locked section MUST be seeded with `is_locked = TRUE` and a meaningful `info_message`. This is **not optional** — see [domains/enablement.md](domains/enablement.md) § "Universal sections" for the authoritative `is_locked`/`info_message`/`display_name` matrix and the V61 root-cause note (V57 shipped without these flags because the spec omitted them). The `info_message` template: *"[Section content] are managed via the [Module] Builder. [Field] configuration is not available here."*

2. **Render-time (frontend):** the builder must **surface the locked state inside the section** — a lock affordance plus the `info_message` as the section's body/subtitle — so authors understand why a section has no editable fields, instead of seeing an empty or "Coming soon" panel. The section config (`isLocked`, `infoMessage`) is already typed FE-side (`types/builder-config.types.ts`, `types/platform/builder/builder.types.ts`) and consumed by the admin config editor (`BuilderConfigSection.tsx`).

> **Known gap (2026-06-03):** the generalized `EnablementBuilderAccordion` shell does **not yet** read `section.isLocked` / `section.infoMessage` — it renders only `displayName`/`subtitle`. Wiring the lock affordance + `info_message` into the shell accordion is the adopted-but-pending half of this contract. New modules must not assume it renders automatically yet; see the frontend `builder-widget-platform.md` § "Locked sections".

## Examples in codebase

- Backend: `service/BuilderConfigService.java`, `entity/BuilderSectionConfig.java`, `entity/BuilderFieldConfig.java`.
- Frontend (course admin): `pages/platform-settings/CourseAudienceFieldsAdmin.tsx`.
- Frontend (incentive admin, untouched): `components/settings/BuilderFieldEditor.tsx`.

## Common gotchas

- **CHECK constraint:** a row with both `incentive_type` AND `builder_type` set will fail insert. Always pick one classification.
- **`is_audience_field` rename:** the column was `is_eligibility` before the 2026-05-25 cleanup. Old grep hits should be renamed.
- **`value_source` lives on both `data_object_fields` and `builder_field_configs`:** these are intentionally separate — the field row records a tenant-wide classification; the config row records a per-builder override. Don't merge.
