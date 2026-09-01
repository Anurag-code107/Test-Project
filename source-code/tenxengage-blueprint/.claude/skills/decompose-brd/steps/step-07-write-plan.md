# Step 07: write-plan

**Goal:** Write the plan file with full verbatim content for every output file. The user reviews these as the actual artifacts (not summaries). After approval, step 08 copies content verbatim to disk.

**Inputs:** Slicing/FRs/seeds (step 05), Strategic Notes + validation pass (step 06), all classified content (step 04).

> **Step 07 — Plan file with full verbatim content. No summaries.**

## Where to write

This skill runs in plan mode. The plan file path is provided by the plan mode system message (look for "You should create your plan at ..."). Write the plan there.

Add `stepsCompleted` and `filesWritten` frontmatter so a resumed run from step 01 can detect prior progress:

```yaml
---
slug: {slug}
stepsCompleted: [parse-brd, catalog-reconcile, required-reading, probe-extract, completeness-audit, slice, challenge-validate, write-plan]
filesWritten: []
---
```

## Plan file schema

```markdown
# Roadmap Plan: {brd-name}

## BRD
- **Slug**: `{slug}`
- **Source**: `{file path or "pasted text"}`
- **Module / quadrant**: `{module}`
- **BRD version**: `{version}`

## Phase 0 + 0.5 answers (locked)
[Every user confirmation from step 01 + persona/partner-type reconciliation
decisions from step 02 + clarifying answers from step 04. Pin these so a
reviewer can see what was assumed.]

## Scope summary
- **Vision**: {one sentence}
- **Owns**: {scope boundaries}
- **Does NOT own**: {non-goals / referenced-but-not-implemented}
- **Estimated features**: N
- **Phase plan**: {synthesized phase plan summary; deviations from BRD phasing noted inline}
- **ADRs surfaced**: {N — listed below}

## Strategic notes
[3-7 bullets from step 06 challenge pass — hidden assumptions, scope creep
candidates, riskiest unknown, missing requirements, phasing deviations with reasoning,
alternatives proposed, explicit deferrals from BRD v1 acceptance, system-catalog prerequisites.]

## Requirement inventory
[Compact format — see ../references/req-inventory-format.md.
One REQ-NNN per line, ||-delimited, five positional fields. Pinned verbatim
from the inventory built across step 04 (BRD-verbatim items) and step 04c
(gap-resolution items, identifiable by gap:{slug} in field 5). No
per-field rendering; the inventory is already in the target format by
the time step 07 runs.]

## Slicing summary
[Table of all proposed features, one row each:
| F-NN | Name | Slug | Persona | Phase | Blockers (ADR/feature) | Recommended start? |
]

## Recommended start-here
[One feature pick with reasoning — usually foundation entity or
the riskiest-unknown de-risking slice.]

---

### File: roadmaps/{slug}/digest.md

[FULL digest content — verbatim. Use ../templates/digest.md.
Business-truth only. Target under 90 seconds to read.]

---

### File: roadmaps/{slug}/digest-annex.md

[FULL annex content — verbatim. Use ../templates/digest-annex.md.
Advisory technical artifacts. Stub mode if BRD has no technical-truth content.]

---

### File: roadmaps/{slug}/roadmap.md

[FULL roadmap content — verbatim. Use ../templates/roadmap.md.
At-a-glance table + Strategic Notes + ADRs + phase lists with links to feature files.]

---

### File: roadmaps/{slug}/features/F-01-{slug}.md

[FULL per-feature brief — verbatim. Use ../templates/feature.md.
Business outcome, persona, journey, FR list, business rules, story seeds.]

### File: roadmaps/{slug}/features/F-02-{slug}.md

[... repeat for every feature ...]

---

### File: roadmaps/{slug}/backlog-seeds.csv

[FULL CSV — header row + one row per seed.
Generated mechanically from the per-feature briefs above.
Columns: feature_id, feature_name, feature_phase, seed_id, title, business_outcome, type, depends_on]
```

## Templates

| Template | Use for |
|---|---|
| **[../templates/digest.md](../templates/digest.md)** | `digest.md` — business-truth digest |
| **[../templates/digest-annex.md](../templates/digest-annex.md)** | `digest-annex.md` — advisory technical artifacts |
| **[../templates/roadmap.md](../templates/roadmap.md)** | `roadmap.md` — thin index |
| **[../templates/feature.md](../templates/feature.md)** | `features/F-NN-{slug}.md` — per-feature brief |
| **[../templates/backlog-seeds.csv](../templates/backlog-seeds.csv)** | `backlog-seeds.csv` — PM-friendly export |

## After writing

Ask the user to review the plan. Wait for explicit plan approval.

## Rules in scope for this step

- **No code generation** — plan and downstream files are markdown + CSV only. No Java code, no Flyway SQL, no TypeScript types.
- **One file per feature** — per-feature briefs live in `roadmaps/{slug}/features/F-NN-{slug}.md`.
- **PM-friendly export always emitted** — `backlog-seeds.csv` is part of every plan and every run.
- **Shape-agnostic templates** — conditional sections only emit when the BRD has matching content.

## Routing

User approves the plan → load `steps/step-08-write-files.md`.