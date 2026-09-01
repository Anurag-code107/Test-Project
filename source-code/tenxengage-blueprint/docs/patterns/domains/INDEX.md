# Domain Registry

Source of truth for domain-defining primitives. Read by `/create-spec`,
`/create-stories`, `/review-spec` (blueprint); `/load-spec`, `/load-story`,
`/execute-foundation` (backend); `/load-spec`, `/load-story`, `/create-mockups`
(frontend); `/generate-contracts` (contracts); `bug-fixer` (blueprint).

## Registry files

| File | Status | Anchored on |
| --- | --- | --- |
| [incentive.md](incentive.md) | active | unified builder-config (shared) |
| [enablement.md](enablement.md) | active | unified builder-config (shared) |

Domain files carry frontmatter `domain: {name}`. Both domains share the `BuilderSectionConfig` / `BuilderFieldConfig` infrastructure; see [../builder-config.md](../builder-config.md). `incentive.md` uses `incentive_type` discrimination; `enablement.md` uses `builder_type` + `builder_domain`.

## Slot list (canonical, 8 slots)

1. Core aggregate
2. Audience-rule entity — Per-owner table (`{owner}_audience_rules`). See [../audience-rules.md](../audience-rules.md).
3. Eligibility engine contract
4. Completion/participation entity
5. Budget model (or "not applicable")
6. Approval workflow entity (or "not applicable")
7. Builder discriminator
8. Section/field storage entity — `BuilderSectionConfig` / `BuilderFieldConfig` (unified; distinguished by `incentive_type` vs `builder_type+builder_domain`).

A **primitive** is the concrete filler that occupies a slot. Primitives are
structural elements only — entity name, interface name, table column, topic
name, service class name. Primitives are never business rules. Business rules
belong in `spec.md`.

## Drift policy

`/create-spec` and `/create-stories` interactively prompt when a spec or story
fills a slot with a value different from the registry. Slot additions are
governance events — `/create-spec` MUST NOT silently propose a slot addition
mid-feature. If the slot list is insufficient, the skill surfaces a
`NEEDS_GOVERNANCE_DECISION` marker and stops.

## Resolution order

For a feature filling slots: per-`builder_type` override file (in
`{domain}/{builder-type}.md`) > domain file (`{domain}.md`) > shared builder-config defaults (see [../builder-config.md](../builder-config.md)).

## Governance — migration trigger

When a third domain (second non-incentive domain) lands on the unified builder-config stack,
the engineer landing that third domain runs an incentive-migration audit as a
merge prerequisite before the fourth domain ships. Migration is a scheduled
project, not implicit drift. Weaker fallback if the trigger is ever softened:
annual review of `incentive.md` vs `enablement.md`, owned by the
platform architect role.
