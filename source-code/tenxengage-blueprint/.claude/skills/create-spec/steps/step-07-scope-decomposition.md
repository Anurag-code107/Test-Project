# Step 07: scope-decomposition

## Goal
Decide whether this is a single spec or needs to be decomposed into multiple specs. Lock the scope.

## Inputs (from prior steps)
- Locked FRs (step 01)
- Project context including entity / model / feature listings (step 03)
- Shape manifest (step 05)

## Loads (just-in-time)
- None (uses already-loaded project context)

## Procedure

### 6a — Assess scope size

Evaluate:
- How many new entities / tables?
- How many new API endpoints?
- How many new frontend pages or major components?
- Multiple bounded contexts or domains?
- Are there dependencies between parts?

### 6b — Decide single spec or decomposed

- **Focused requirement** (1–3 entities, 5–10 endpoints, 1–2 pages): single spec, no user interaction. Lock scope and proceed.
- **Large requirement** (4+ entities, 10+ endpoints, 3+ pages, or multiple domains): use Domain-Driven Design thinking. Identify bounded contexts, natural implementation boundaries, dependencies. Present the decomposition and ask: "Should I create one spec covering everything, or separate specs for each sub-requirement?"

## Rules (scoped to this step)
- A single spec covering 4+ entities + 10+ endpoints is almost always too big — push back hard on "do it all in one spec" requests for large scopes.
- For decomposition, the slicing principle is the same as `/decompose-brd`: vertical slices, each independently shippable, dependency-ordered.
- Do NOT decide individual entity / endpoint design here. That's spec content (step 13).

## User interaction
- Focused: no gate.
- Decomposed: present decomposition, ask user how to proceed. If they pick "separate specs", this run handles the first sub-spec; the user can re-invoke `/create-spec` for subsequent ones.

## Output for downstream steps
- Locked scope (single or one piece of decomposed)
- Scope summary line: "N entities, M endpoints, P pages. Single spec." (or decomposition table)

## Boundary
Scope locked → route to step 08: read steps/step-08-security-analysis.md`.