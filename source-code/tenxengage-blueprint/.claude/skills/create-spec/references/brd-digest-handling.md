# BRD digest handling

Reference loaded by step 02 (`load-brd-context`) when a digest applies. Defines how digest content flows into spec.md.

---

## Detection signals (already evaluated in step 02 procedure)

A digest applies when any of:
1. Input was a BRD identifier (Mode 1) — digest auto-loads.
2. User's prompt or `$ARGUMENTS` references a roadmap digest path.
3. User mentions a BRD slug or roadmap slice.
4. `roadmaps/` contains a `*/digest.md` whose subject matter matches the requested feature. (If multiple match, ask the user which to use.)

---

## Inheritance rules

When digest applies:

### Vision and scope boundaries
The spec's Overview must be consistent with the digest's vision. Don't widen scope beyond the digest unless explicitly approved by the user.

### Personas
Use digest's persona names verbatim. Do NOT invent new personas. If the spec needs a persona the digest didn't list, surface this to the user and flag as a follow-up (out of scope for create-spec).

### BRD-specific concepts
Carry forward concepts the digest establishes (e.g., agent contract format, attribution model, state-machine pattern, error-code convention) into the spec's design sections. The digest is the source of truth for these.

### Integration points
Reflect digest-stated integration points in the spec's API and Event sections.

### Phasing
Align spec's "Out of Scope" with the digest's phase plan. If the digest defers a capability to Phase 2, the spec must explicitly defer it (don't silently include it).

### Open ADRs
If the digest lists open ADRs and any block this feature, list them as Prerequisites in the spec. Do NOT spec around an unresolved ADR — flag it.

### Entity-shape decisions
If the digest contains a `## Entity-shape decisions` section, treat it as **inherited but refinable** during modeling. Step 12's `entity-shape-decisions.md` procedure reads prior decisions, surfaces overrides only when this feature's modeling context conflicts with them, and writes the resolved decisions back to `digest.md` for subsequent specs. Unlike Vision and Personas (which are inherited verbatim), entity-shape decisions can be overridden per feature when the modeling context demands it. See [entity-shape-decisions.md](./entity-shape-decisions.md).

---

## What the digest is NOT

- Authoritative for technical names. Names follow codebase conventions, not BRD vocabulary. (See `references/advisory-reconciliation.md`.)
- Authoritative for FRs at the technical-precision level. The spec refines wording for technical accuracy. (FRs from per-feature briefs are inherited verbatim with allowed refinements.)
- A replacement for project context. Step 03's project-context loading still happens after this step.

---

## Step-02 logic flow

```
if BRD identifier mode (Mode 1):
  read roadmaps/{slug}/digest.md
  if roadmaps/{slug}/digest-annex.md exists:
    read digest-annex
    read references/advisory-reconciliation.md
    run reconciliation per its rules
elif other digest signal:
  read the matching digest.md
  similarly handle digest-annex if present
else:
  no-op; route to step 03
```