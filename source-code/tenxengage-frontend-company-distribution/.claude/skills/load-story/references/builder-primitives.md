# Builder Primitives — load-story FE

Read by subagent/step-06-implement-tasks.md when the story touches a builder screen.
Conditional load: only read this file if the story's FE tasks include a builder screen type.

**Builder primitives consumption (non-negotiable).** When implementing a builder feature:

- **Builder wizard screens (matching `Builder — wizard body` Mirror row):**
  - MUST import and use `useBuilderConfig` from `src/hooks/useBuilderConfig.ts` to fetch the section/field config.
  - MUST render wizard fields via `DynamicFieldRenderer` from `src/components/incentive-builder/DynamicFieldRenderer.tsx`.
  - MUST NOT declare wizard step fields inline as TSX. The fields are runtime-driven by the config returned from `useBuilderConfig`.
  - MUST use `BuilderLayout` from `src/components/incentive-builder/BuilderLayout.tsx` as the page shell, with the same 40/60 layout, PageBanner theme (`builder-ai` / `builder-manual`), and BuilderAccordion step structure as the existing incentive builder.

- **Builder Config admin screens (matching `Platform Settings — Builder Config tab` or `Builder Config — standalone page` Mirror row):**
  - MUST use `BuilderConfigTab` / `BuilderConfigSection` / `BuilderFieldEditor` from `src/components/settings/` as the editing primitives. Do not build new section/field editor components from raw inputs.
  - MUST persist section + field config to the existing `BuilderSectionConfig` + `BuilderFieldConfig` tables via the existing admin endpoints — never create parallel state.

- **Builder entry / type selector / template picker / existing-item picker screens** (matching their respective Mirror rows): no architectural primitive to consume (these are pre-builder navigation screens) — fidelity is governed entirely by Step 5b mockup + Step 5c Mirror reference + the Fidelity rule above.

Verify Builder Config feature flag (`module.settings.tenx`) gating where applicable per the existing pattern in `BuilderConfigPage.tsx`.
