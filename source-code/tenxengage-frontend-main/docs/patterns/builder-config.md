# Builder Configuration Pattern — Frontend

> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes how the frontend consumes the `BuilderSectionConfig` /
> `BuilderFieldConfig` API which serves the incentive domain. Status per the
> [domain registry](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md):
> `active-legacy` (see [incentive.md](../../../tenxengage-blueprint/docs/patterns/domains/incentive.md)).
> The code stays in production; the file stays for reference. **New code
> should not adopt this pattern.**
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

This document describes how the frontend consumes and renders dynamic builder configurations. Builder configuration makes the steps, sections, and fields within a builder data-driven rather than hardcoded, allowing administrators to customize what fields appear, their order, and their behavior.

## Configuration API

Builder configuration is fetched from the backend via `GET /api/v1/builder-config/{incentiveType}`. The incentive type parameter determines which configuration to load — current values are `SALES`, `TRAINING`, `ACTIVITY`, `JOURNEY` (uppercase, defined in `src/types/incentive.types.ts`). The response contains the full section and field hierarchy for that builder type.

The frontend should fetch this configuration early in the builder lifecycle — typically in the builder page component or context initialization. The configuration drives what sections and fields render, so it must be available before the accordion steps are displayed. Use a TanStack Query hook (`useBuilderConfig(type)`) to fetch and cache this data.

## Configuration Structure

The API returns a `BuilderConfig` object containing a `sections[]` array. Each section represents a logical grouping of fields that maps to a builder step or a portion of a step. Each section contains a `fields[]` array describing the individual form fields within that section.

Every section and field includes a `sortOrder` property that determines rendering order. Behavioral flags differ by level:

- **Section flags** (`BuilderSectionConfigResponse`): `isLocked` (section cannot have fields added/removed — system sections), `isVisible` (whether the section renders).
- **Field flags** (`BuilderFieldConfigResponse`): `isMandatory` (required for step completion), `isSystem` (platform-defined, locked vs tenant-defined editable), `isEligibility` (field participates in eligibility evaluation for the entity).

Some fields also carry `supportsExcelUpload` (only meaningful for `DROPDOWN` / `MULTI_SELECT`) to enable a bulk-upload UX in the admin tab. The frontend must respect all of these flags when rendering and validating.

## Field Types

The configuration supports the following field types, each mapping to a specific UI component:

- **TEXT_BOX** — Single-line text input for short strings like names and titles.
- **DROPDOWN** — Single-select dropdown, options loaded from a value source or static list.
- **MULTI_SELECT** — Multi-select component allowing multiple values, typically rendered as a combobox with chips.
- **DATE_PICKER** — Date selection component, may include range picking depending on field configuration.
- **NUMBER_INPUT** — Numeric input with optional min/max validation and formatting.
- **TOGGLE** — Boolean toggle switch for on/off settings.
- **TEXT_AREA** — Multi-line text input for descriptions and longer content.

The DynamicFieldRenderer component accepts a field configuration object and renders the appropriate component. All field types follow the same interface: they receive their current value from builder state and dispatch an UPDATE action on change.

## Value Sources

Dropdown and multi-select fields load their options from configurable value sources. The `valueSource` property on a field configuration determines where options come from:

- **STATIC** — Options are defined inline in the field configuration. Used for simple, fixed lists.
- **LOCATION_HIERARCHY** — Options are loaded from the tenant's location hierarchy (regions, areas, territories). Use the `getLocationValuesForLevel()` helper exported from `useBuilderConfig.ts`. Full pattern at [location-hierarchy.md](location-hierarchy.md).
- **CLIENT_ROLES** — Options come from the tenant's defined roles. Use the `useExternalRoles()` hook (with `mapExternalRolesToOptions()` helper).
- **DATA_OBJECT_FIELD** — Options are derived from a linked data object's field values. The field configuration includes the data object reference.
- **ACTIVITY_CATEGORIES** — Options come from the tenant's activity category configuration. Use the `useActivityCategories()` hook.

The `useFieldValues(fieldId, context)` hook encapsulates the logic for loading options based on the field's value source. It examines the field configuration, determines the appropriate data source, and returns the options in a standardized format. Components should use this hook rather than implementing value-loading logic directly.

## System vs Custom Fields

Fields are categorized as either system or custom. System fields (`isSystem: true`) are defined by the platform and present in every tenant's configuration. They cannot be deleted, and their core structural properties (field type, value source) cannot be changed by administrators. The backend's `updateField` enforces this — only `displayName`, `helperText`, `isMandatory`, and `isEligibility` are mutable on system fields.

Custom fields (`isSystem: false`) are added by tenant administrators and are fully editable. They can be reordered, made mandatory or optional, have their field type, label, value source, or other properties changed, or be deleted entirely. The frontend must check the `isSystem` flag before enabling edit or delete controls in the admin configuration UI.

## Admin Configuration UI

Two routes expose the configuration management interface today:

- `/settings/platform` → **Platform Settings > Builder Config tab** (`PlatformSettingsPage` rendering `BuilderConfigTab`) — the primary entry point, gated by the `module.settings.tenx` permission.
- `/settings/builder-config` → standalone **`BuilderConfigPage`** — direct deep-link, gated by the stricter `action.builder.manage` permission.

Both surfaces expose the same underlying admin UI, which displays sections as expandable panels with their fields listed in sort order.

For each field, the admin can toggle visibility, toggle mandatory status, change sort order (via drag-and-drop or up/down controls), and — for custom fields — edit the field type, label, and other properties. An "Add Field" button allows creating new custom fields within a section. The UI must clearly distinguish system fields (showing a lock icon and disabling destructive actions) from custom fields.

## Hooks

The frontend provides several hooks for working with builder configuration:

- **`useBuilderConfig(type)`** — Fetches and caches the full builder configuration for a given incentive type. Returns the sections and fields hierarchy. This is the primary hook used by the builder to get its configuration.
- **`useFieldValues(fieldId, context)`** — Resolves and returns the options for a dropdown or multi-select field based on its value source configuration. Handles all value source types transparently by delegating to the helpers below.
- **`getLocationValuesForLevel()`** — Helper (not a hook) exported from `useBuilderConfig.ts` for `LOCATION_HIERARCHY` value sources.
- **`useExternalRoles()`** — TanStack Query hook for `CLIENT_ROLES`; pair with the `mapExternalRolesToOptions()` helper.
- **`useActivityCategories()`** — TanStack Query hook for `ACTIVITY_CATEGORIES`.

The builder-aware field-type dispatcher is `src/components/incentive-builder/DynamicFieldRenderer.tsx`. A separate `src/components/DynamicFieldRenderer.tsx` exists for the data-objects feature (it dispatches on `DataType`, not `FieldType`) and is unrelated.

## Rules

Steps and sections within a builder must always be driven by configuration data, never hardcoded. If a new builder type is introduced, its configuration must be seeded in the backend (via Flyway migration) before the frontend can render it. The frontend should gracefully handle missing or empty configurations by showing an appropriate empty state rather than crashing.

When rendering fields, always iterate over the `fields[]` array in `sortOrder` and check `isVisible` before rendering each field. When validating a step for completion, only check fields where `isMandatory` is true. Never assume a specific field exists at a specific index — always look up fields by their identifier.
