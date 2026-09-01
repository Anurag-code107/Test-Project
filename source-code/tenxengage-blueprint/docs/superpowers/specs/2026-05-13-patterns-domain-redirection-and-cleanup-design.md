---
title: Pattern files cleanup + domain-awareness redirection (blueprint + sibling repos)
date: 2026-05-13
status: design
authors: vijay@tenxengage.com (driver), Claude (drafting)
scope: tenxengage-blueprint + tenxengage-backend + tenxengage-frontend
related: docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md
---

# Pattern files cleanup + domain-awareness redirection

## 1. Background and motivation

The /create-spec domain-awareness work introduced a domain registry at `docs/patterns/domains/` and categorized the existing builder stack (`BuilderSectionConfig`, `BuilderFieldConfig`, `ParticipantEligibilityChecker`, `IncentiveAudienceRule`, etc.) as "incentive-legacy". A parallel platform-primitives layer is in-design; the first enablement feature is expected to build the actual code.

But the pattern files that an implementing engineer would naturally read — `docs/patterns/builder-widget.md`, `docs/patterns/builder-config.md` in blueprint, and their counterparts in `tenxengage-backend` and `tenxengage-frontend` — describe the incentive-shaped world *as if it were THE pattern*, with no acknowledgment of the new layer. A developer told to "follow the builder-config pattern" while implementing the first enablement feature would faithfully implement on `BuilderSectionConfig` — exactly the silent leakage the registry was designed to prevent.

Additionally, the blueprint's `docs/patterns/INDEX.md` has accumulated mechanical issues that pre-existed this work but are now compounded by the /create-spec renumbering:

- A broken pointer (`builder-wizard.md` is registered but the file is `builder-widget.md`).
- A naming inconsistency (file `builder-widget.md` but the H1 inside says `# Pattern: builder-wizard`).
- Stale step references throughout the registry (e.g., `step 09` for permissions when current permissions-analysis is at step 11; `step 12` for spec-generation when current generate-spec-content is at step 13; etc.).
- No mention of the `domains/` subdirectory in the parent INDEX.

This design cleans up both issues — the mechanical staleness in the patterns INDEX, and the missing redirection from legacy pattern files to the domain registry — across blueprint, backend, and frontend pattern files.

## 2. Summary of changes

| # | Change | Scope |
|---|---|---|
| 1 | Mechanical fixes to `tenxengage-blueprint/docs/patterns/INDEX.md` — broken pointer, stale step refs, domains pointer | blueprint |
| 2 | Rename `tenxengage-blueprint/docs/patterns/builder-widget.md → builder-wizard.md` + grep-and-update references | blueprint |
| 3 | Add substantive redirection blocks to five pattern files (blueprint × 2 + backend × 1 + frontend × 2) labelling them as incentive-legacy, pointing at the domain registry, and assigning the platform-primitives pattern-doc responsibility to the first enablement engineer | blueprint, backend, frontend |
| 4 | Per-file specifics — fix `SPIFF`/`REBATE` examples in backend builder-config.md; label "Adding a new builder type — checklist" as incentive-only | backend |

## 3. Change 1 — mechanical fixes to `docs/patterns/INDEX.md`

### 3.1 Broken pointer

Line 21 currently says:
```
| builder-wizard | builder-wizard.md | Feature has multi-step UI for create/edit | create-spec step 12 |
```
The pointer `builder-wizard.md` is invalid because the file is named `builder-widget.md`. After change 2 (rename), the pointer becomes valid automatically — no edit to this line's filename column is needed beyond what change 2 produces.

The step number also needs updating (see 3.2).

### 3.2 Stale step references

The pre-existing registry references step numbers that reflect the original /create-spec layout before the renumbering done for the new step 02 (now step 04) and the rest of the domain-awareness work. Current correct mappings:

| Pattern | Current INDEX step | Correct step |
|---|---|---|
| permissions-and-feature-flags | step 09 | step 11 (permissions-analysis) |
| package-structure | steps 12, 13 | steps 13, 14 (generate-spec-content + generate-technical-content) |
| new-entities | step 13 | step 13 (generate-spec-content — already correct) |
| managed-data | step 13 | step 13 (already correct) |
| location-hierarchy | step 12 | step 13 |
| tenant-isolation | step 13 | step 13 (already correct) |
| builder-wizard | step 12 | step 13 |
| builder-config | step 12 | step 13 |
| ai-copilot | step 12 | step 13 |
| html-content | step 12 | step 13 |
| sse-streaming | step 12 | step 13 |
| currency-handling | step 12 | step 13 |
| rate-limit-sensitive | step 07 | step 08 (security-analysis) |
| event-publishing | step 08 | step 09 (events-analysis) |
| event-consuming | step 08 | step 09 |

Plus the bottom-of-file paragraph reference:
- "All matching pattern files are loaded once in `create-spec` step 05 (`load-shape-references`)" → step 06 (current load-shape-references is at position 06).

### 3.3 Domains subregistry pointer

Add a new entry to the patterns INDEX immediately after the table introduction, so the domain registry is discoverable as a first-class artifact:

```markdown
**Domain registry:** Pattern files describe feature-shape conventions. For
**builder-shaped features**, the [domain registry](domains/INDEX.md) is the
structural authority (slot fillers, primitive names, parallel-rails strategy).
Read it alongside this index when the feature is slot-filling.

| Registry | File | Gate | Consumed by step |
|---|---|---|---|
| domain-registry | domains/INDEX.md | ALWAYS for slot-filling features | create-spec step 04 |
```

This is two paragraphs + a one-row mini-table inserted before the main pattern registry table. It surfaces the domain registry without conflating it with the pattern files themselves (the domain registry is a different kind of artifact — structural primitives, not feature-shape patterns).

## 4. Change 2 — wizard/widget rename

### 4.1 Why rename

The pattern file's H1 says `# Pattern: builder-wizard`. The pattern's content (multi-step accordion, type dispatcher, step progress bar, entry flow) describes a *wizard* — a multi-step guided creation flow — not a widget. The registry entry in INDEX.md says `builder-wizard`. Only the file system name (`builder-widget.md`) is the outlier.

Renaming the file aligns all three places (file, registry, H1) on one canonical name.

### 4.2 Mechanics

1. `git mv tenxengage-blueprint/docs/patterns/builder-widget.md tenxengage-blueprint/docs/patterns/builder-wizard.md` — preserves git history as a rename.
2. No edit to the file's H1 (already says `builder-wizard`).
3. No edit to the INDEX.md row's filename column (already says `builder-wizard.md` — currently broken, becomes valid after the rename).

### 4.3 Reference sweep

Grep across all sibling repos plus auto-memory for any remaining reference to `builder-widget.md`:

- `tenxengage-blueprint/.claude/skills/` — especially `create-spec/steps/`
- `tenxengage-blueprint/PROJECT-CONTEXT.md`
- `tenxengage-blueprint/CLAUDE.md`
- `tenxengage-backend/CLAUDE.md`, `tenxengage-frontend/CLAUDE.md`, etc. (sibling CLAUDE.md files in case they cross-reference)
- `~/.claude/projects/-Users-vijayanandkandiraju-WorkWorkWork-VSCode-tenxengage-application-tenxengage-blueprint/memory/` — auto-memory

Each hit gets updated to `builder-wizard.md`.

### 4.4 Why only blueprint renames

The frontend has its own `docs/patterns/builder-widget.md` (101 lines). Its file name and internal H1 both say "Widget" — internally consistent. The frontend's file describes the frontend implementation pattern (component shell, reducer, animations), while the blueprint's file describes the cross-cutting pattern shape. They are different concerns; different (each internally consistent) names is acceptable. Renaming the frontend file would be churn without benefit.

## 5. Change 3 — substantive redirection blocks

### 5.1 Files receiving the block

| Repo | File |
|---|---|
| tenxengage-blueprint | `docs/patterns/builder-wizard.md` (post-rename) |
| tenxengage-blueprint | `docs/patterns/builder-config.md` |
| tenxengage-backend | `docs/patterns/builder-config.md` |
| tenxengage-frontend | `docs/patterns/builder-config.md` |
| tenxengage-frontend | `docs/patterns/builder-widget.md` (no rename) |

### 5.2 Blueprint variant — block content

Inserted immediately under the file's H1, before any existing content:

```markdown
> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes the `BuilderSectionConfig` / `BuilderFieldConfig` /
> `BuilderConfigService` stack which serves the incentive domain. Status per
> the [domain registry](domains/INDEX.md): `active-legacy` (see
> [domains/incentive.md](domains/incentive.md)). The code stays in production;
> the file stays for reference. **New code should not adopt this pattern.**
>
> **Implementing a feature for a new domain (enablement, future)?**
> Do NOT follow this pattern. New domains use **platform primitives**:
> `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
> per [domains/platform-primitives.md](domains/platform-primitives.md). The
> platform-primitives implementation does not exist in code yet — the first
> feature landing on platform primitives builds it, guided by:
> - The slot list and naming convention in [domains/INDEX.md](domains/INDEX.md)
> - The design at [../superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md](../superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md)
>
> **First engineer to land platform primitives:** as a final deliverable of
> your feature, write `builder-config-platform.md` / `builder-wizard-platform.md`
> (or naturally-named equivalents once implementation has clarified them) and
> register them in [INDEX.md](INDEX.md). This redirection block points at
> your files once they exist.
>
> ---
```

For `builder-wizard.md`, the same block with the opening lines adapted to "describes the multi-step builder UI pattern as implemented by the incentive builder" instead of mentioning `BuilderSectionConfig`.

### 5.3 Sibling repo variant — path adjustments

Same block, with relative paths adjusted to reach the blueprint registry from a sibling repo's `docs/patterns/` directory:

- `domains/INDEX.md` → `../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md`
- `domains/incentive.md` → `../../../tenxengage-blueprint/docs/patterns/domains/incentive.md`
- `domains/platform-primitives.md` → `../../../tenxengage-blueprint/docs/patterns/domains/platform-primitives.md`
- `../superpowers/specs/2026-05-12-...` → `../../../tenxengage-blueprint/docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md`
- `INDEX.md` (where the platform pattern doc will be registered) — in sibling repos this becomes the local `docs/patterns/` directory if/when they grow an INDEX.md; if no INDEX exists, the block notes that the platform pattern doc registers in the blueprint's INDEX

### 5.4 Per-file deliverable naming

The redirection block intentionally hedges on the exact file name (e.g., "builder-config-platform.md / builder-wizard-platform.md or naturally-named equivalents"). The first-enablement engineer picks the natural names once implementation has clarified the structure. The blueprint may end up with one umbrella file, the backend with its own, the frontend with its own — that's the natural pattern-file shape, with each repo owning the pattern docs for its own concerns.

## 6. Per-file specifics

### 6.1 `tenxengage-backend/docs/patterns/builder-config.md` — stale data fix

The file references `incentiveType` example values `SPIFF`, `REBATE`. These don't match current code, which uses `SALES`, `TRAINING`, `ACTIVITY`, `JOURNEY` (per `BuilderSectionConfig.incentive_type` column values and the V3__baseline_tenant_and_config.sql seed). Replace `SPIFF`/`REBATE` references with the current values throughout the file.

### 6.2 `tenxengage-backend/docs/patterns/builder-config.md` — "Adding a new builder type" checklist

The file ends with a four-step checklist titled "Adding a new builder type — checklist". This is incentive-bespoke (Step 1 says "Flyway migration seeding BuilderSectionConfig and BuilderFieldConfig rows…", Step 2 says "Include the new incentive_type enum value…"). A new-domain developer who follows this checklist seeds incentive-legacy tables — exact silent leakage.

Fix: wrap the checklist with a clarifying header:

```markdown
## Adding a new INCENTIVE_TYPE — checklist

> ⚠️ **This checklist applies only to adding a new variant within the
> incentive domain** (e.g., a fifth value beyond SALES, TRAINING, ACTIVITY,
> JOURNEY). For a new DOMAIN (enablement, future), see the redirection block
> at the top of this file — do not follow this checklist.

[existing 4-step checklist]
```

This keeps the useful checklist content intact while preventing misuse.

### 6.3 No INDEX.md added to sibling repos

The siblings don't currently have a `docs/patterns/INDEX.md`. Adding one is out of scope here — the blueprint INDEX is the canonical registry, and sibling skills (`load-spec`, `load-story`, `create-mockups`) load sibling pattern files directly by path or via the spec's references. If a sibling INDEX becomes useful later, it can be added separately.

## 7. Implementation order

Six commits, ordered for review-friendliness; each is independently reversible:

1. **Rename + reference sweep.** `git mv` the blueprint file; grep across all repos + auto-memory; update each hit. Atomic and small.
2. **Blueprint INDEX.md mechanical fixes.** Stale step refs, domains pointer entry, broken-pointer line (now valid post-rename — no filename column edit needed, just the step number). Single file.
3. **Blueprint redirection blocks.** Add to `builder-config.md` and `builder-wizard.md`. Two files, same content with minor opening-line adjustments.
4. **Backend redirection block + stale-data fix + checklist label.** All edits to `tenxengage-backend/docs/patterns/builder-config.md`. Single file, three concerns batched.
5. **Frontend `builder-config.md` redirection block.** Single file edit.
6. **Frontend `builder-widget.md` redirection block.** Single file edit.

Each commit is small, focused, and reviewable in isolation. Commits 4–6 can be reordered or parallelized if desired.

## 8. Acceptance criteria

The implementation is complete when:

1. `tenxengage-blueprint/docs/patterns/builder-wizard.md` exists; `builder-widget.md` no longer exists; the INDEX.md row points at the file and resolves correctly.
2. No reference to `builder-widget.md` remains anywhere in the workspace (verified by `grep -r "builder-widget.md"` across all sibling repos and auto-memory).
3. The patterns INDEX.md step references match the current /create-spec step numbers per section 3.2.
4. The domains pointer entry is present in the patterns INDEX.md.
5. All five files (blueprint × 2 + backend × 1 + frontend × 2) carry the redirection block; the block links resolve correctly (clickable in markdown viewers and skill-readable).
6. Backend `builder-config.md` no longer contains `SPIFF` / `REBATE` references.
7. Backend "Adding a new builder type — checklist" is renamed to "Adding a new INCENTIVE_TYPE — checklist" with the domain-scoping warning.
8. A developer opening any of the five files for the first time, looking for guidance on a new-domain feature, is unambiguously directed to the platform-primitives path within the first screen of reading.

## 9. Non-goals

- **No INDEX.md added to sibling repos.** Deferred — not needed for this work; can be added separately if sibling pattern registries become useful.
- **No platform-primitives pattern files written.** Pattern docs for platform primitives are bottom-up, written by the first engineer who builds the platform code. Writing them now would be speculative documentation. The redirection blocks plant the obligation; the implementation lands the artifact.
- **No refactoring of incentive-specific content inside the pattern files** (e.g., the reducer action names `UPDATE_BASICS`, `UPDATE_BUDGET` in the widget pattern). The redirection block at the top does the redirection; the body's existing "reference impl" callouts continue to label incentive-bound details. Rewriting body content is unnecessary and out of scope.
- **No automation in /create-spec to enforce pattern-doc generation** for first-enablement features. The redirection block plants the expectation; if it gets missed in practice, /create-spec can evolve to auto-add a pattern-doc deliverable to the plan. YAGNI for now.

## 10. Open items

None significant — the design is fully specified for implementation.
