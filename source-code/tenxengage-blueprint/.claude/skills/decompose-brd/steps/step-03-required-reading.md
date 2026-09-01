# Step 03: required-reading

**Goal:** Load project standards so cross-cutting platform patterns aren't asked again as clarifying questions in step 04. Glob existing feature specs for overlap detection. No user interaction.

**Inputs:** Locked persona/partner-type decisions from step 02.

> **Phase 1 — Reading project context.** Loading project standards so cross-cutting platform patterns aren't asked again as clarifying questions.

Step 02 already read the role + partner-type catalog from `references/system-catalog.md`; do not re-read it here.

## Project Standards (skip these topics in step 04 — already covered)

1. Read `./PROJECT-CONTEXT.md` — full platform standards (multi-tenancy, permission model, feature flags, currency, IDs, dates, API versioning).
2. Read `./docs/patterns/INDEX.md` — pattern registry. For each pattern whose gate signal matches this BRD's feature shapes, read that pattern file. At minimum, always read `permissions-and-feature-flags.md` and `tenant-isolation.md`.

## Cross-repo standards

3. Read `../tenxengage-backend/PROJECT-CONTEXT.md` — backend standards.
4. Read `../tenxengage-frontend/PROJECT-CONTEXT.md` — frontend standards.
5. Read `../tenxengage-contracts/PROJECT-CONTEXT.md` — API conventions.
6. Read `../tenxengage-contracts/enums-index.md` — enum registry (compact index; deep-read `enums.md` section only when reusing a specific enum).

## Blueprint state

8. Glob `./features/*/spec.md` — read YAML frontmatter (`name`, `slug`) of each existing feature for overlap detection.
9. Glob `./roadmaps/*/roadmap.md` (if any exist) — prior roadmap format precedent.

## Topics now off-limits for clarifying questions in step 04

After reading the above, do **NOT** ask the user about:
- Tenant isolation, multi-tenancy
- Permission model, feature flag tiers
- API versioning, JSON conventions, error shape
- Audit infrastructure, OWASP basics
- Package structure, Spring Boot conventions, React/TanStack patterns

These are platform givens. Inherit them into every spec.

## Rules in scope for this step

- **Skip project-level patterns** — never ask about tenant isolation, permission model, audit, OWASP, package structure. Inherit them from `PROJECT-CONTEXT.md`.

## Routing

Reading complete → auto-proceed to `steps/step-04-probe-extract.md`. No user prompt between these steps.