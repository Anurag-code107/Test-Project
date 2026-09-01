# Step 02: load-brd-context

## Goal
If this feature was sliced from a BRD, inherit cross-cutting BRD context (vision, scope, personas, BRD-specific concepts, integration points, phasing, ADRs) without re-extracting from the full BRD. Run advisory reconciliation if a `digest-annex.md` exists.

## Inputs (from prior steps)
- Locked input mode flag (from step 01)
- BRD slug (if Mode 1)

## Loads (just-in-time)
- Conditional:
  - If digest applies → Read `roadmaps/{slug}/digest.md` in full
  - If `roadmaps/{slug}/digest-annex.md` exists → Read it
  - If digest applies → Read `references/brd-digest-handling.md` for inheritance rules
  - If digest-annex was loaded → Read `references/advisory-reconciliation.md` for reconciliation logic

## Procedure

1. **Detect whether a digest applies:**
   - Mode 1 (BRD identifier from step 01) → digest applies; load it
   - Otherwise, look for signals: user message references a roadmap digest path, mentions a BRD slug, or `roadmaps/` contains a digest whose subject matter matches the requested feature
   - If no digest applies → skip ahead to the boundary (route to step 03)

2. **Read `roadmaps/{slug}/digest.md` in full.** Read `references/brd-digest-handling.md` for the inheritance rules:
   - Vision and scope → spec's Overview must be consistent
   - Personas → use digest's persona names verbatim; do not invent new ones
   - BRD-specific concepts (agent contract formats, attribution models, etc.) → carry into spec design sections
   - Integration points → reflect in API/Event sections
   - Phasing → align spec's "Out of Scope" with digest's phase plan
   - Open ADRs → if any block this feature, list as Prerequisites; do NOT spec around an unresolved ADR

3. **If `roadmaps/{slug}/digest-annex.md` exists, run advisory reconciliation.** Read `references/advisory-reconciliation.md` for the procedure. For each entity / event / API op / error code in the annex that's likely relevant:
   - Search contracts repo (`tenxengage-contracts/models/*.md`, `enums.md`) for an existing equivalent
   - Search backend for existing types
   - Apply the decision rule:
     - Same name → adopt
     - Different name → prefer codebase, record reconciliation note for the spec
     - No equivalent → BRD name is candidate (but final name follows platform conventions)
     - Unresolvable → surface to user before proceeding

## Rules (scoped to this step)
- Treat digest contents as cross-cutting GIVENS — inherit, do not re-derive.
- Treat digest-annex contents as ADVISORY — they are inputs to reconciliation, not authoritative.
- Do NOT silently adopt BRD-stated names over codebase names.
- This step does NOT replace step 03 (project context); it adds BRD-specific context on top.

## User interaction
- For unresolvable reconciliation, surface to user and wait for direction.
- Otherwise, no user gate — auto-proceed.

## Output for downstream steps
- Loaded digest content in conversation context (or "no digest applied" flag)
- Reconciliation notes (if any) — to be incorporated into spec.md's Naming Reconciliation sub-section in step 13

## Boundary
Cross-cutting context loaded (or skipped) → route to step 03: read steps/step-03-load-project-context.md`.