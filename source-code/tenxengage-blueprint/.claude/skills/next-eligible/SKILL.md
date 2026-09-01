---
name: "next-eligible"
description: "Print a punch list of foundation tasks and story-layers that are eligible to pick up next (status = not-started AND all deps = done). Read-only helper for coordination."
argument-hint: "feature-slug (e.g., rate-course)"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Purpose

This skill is **read-only**. It does not claim work, create branches, or touch git state. It reads `features/{feature-slug}/tracker.md`, analyzes dependency chains, and prints a punch list so a human can decide what to pick next. Picking is always explicit via `/execute-foundation` or `/load-story` in the sibling repo.

---

## Steps

### 1. Resolve the feature

1. Parse the feature slug from user input (e.g., `rate-course`)
2. Read `features/{feature-slug}/tracker.md`
3. If not found: error "No feature found at features/<slug>/. Pass a valid slug." — do not list features.

### 2. Parse the tracker

Extract three structured views from the tracker markdown:

- **Foundation table** — one row per F-id: `#, Task, Status, Deps (from foundation.md if needed)`
- **Stories table** — one row per US-NN with `Layers`, `Depends on`, `BE status`, `FE status`
- **Blocked / needs attention** section — as written

### 3. Compute eligibility

A row is **eligible** when:
- Its status = `not-started`
- All rows listed in its `Depends on` have status = `done` (or `N/A`)

For stories with `Layers: BE + FE`, BE and FE are evaluated independently. A story with `BE = done, FE = not-started` and `Foundation done` → FE is eligible.

**Foundation sequencing rule:** F-tasks run strictly in order (F1 → F2 → F3 → F4 → F5). Only the lowest-numbered `not-started` foundation row is eligible.

**Story deps:** a story's `Depends on` cell may list foundation tasks (e.g., `Foundation`, `F1, F2, F3`) or prior stories (e.g., `US-01`). All must be `done` (or `N/A`) for the story to be eligible. Dependency resolution is layer-aware:
- **BE eligibility**: a prior story's BE cell must be `done` (or `N/A`)
- **FE eligibility**: a prior story's BE **and** FE cells must both be `done` (or `N/A`)

### 4. Print the punch list

Output in this exact shape (human-readable, grouped):

```
## Next eligible — rate-course

### Foundation
- F2: Flyway migrations — BE (not-started, deps: F1 done)
  → cd ../tenxengage-backend && /execute-foundation rate-course F2

### Stories — BE
- US-01 Create rating — BE (not-started, deps: Foundation done)
  → cd ../tenxengage-backend && /load-story rate-course US-01

### Stories — FE
- US-01 Create rating — FE (not-started, deps: Foundation done)
  → cd ../tenxengage-frontend && /load-story rate-course US-01

### In progress (claimed, not yet done)
- F1 Enums — BE (session sess_abc123, started 2026-04-19T10:15Z)

### Blocked
- (none)

### Waiting (deps not yet met)
- US-02 Edit rating — BE (waiting on US-01 BE)
- US-02 Edit rating — FE (waiting on US-01 BE + FE)
```

If a row is in `blocked` status, include it in `### Blocked` with the Notes reason, not in eligible.

### 5. Do not modify anything

This skill performs **zero writes**. No git operations, no tracker updates, no branch creation. If the user wants to actually pick a row, they must invoke `/execute-foundation` or `/load-story` in the sibling repo themselves.

---

## Notes

- If the tracker is malformed (missing tables, bad status values), print a clear diagnostic and the problematic line — do not attempt to guess.
- If all rows are `done` or `N/A`, print: "Feature {feature-slug} is tracker-complete. Open the final `features/{feature-slug}` → `main` PR in each repo (`gh pr create --base main`)."
- If nothing is eligible but some rows are `in-progress` or `blocked`, surface that — the user may be waiting on their own earlier work.
