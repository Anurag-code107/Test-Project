# /create-spec Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add open-questions resolution, interactive ambiguity, Claude-only execution timing, and domain awareness to `/create-spec`; stand up the `docs/patterns/domains/` registry; propagate to sibling-repo skills (backend, frontend, contracts) and blueprint downstream skills (`/create-stories`, `/review-spec`, `bug-fixer`).

**Architecture:** Edits live in the existing `/create-spec` skill under `.claude/skills/create-spec/` and in sibling-repo skill files. A new top-level directory `docs/patterns/domains/` holds the domain registry. No new slash command. Incentive code remains untouched; platform primitives are documented as a new layer that the first enablement spec will build out.

**Tech Stack:** Markdown skill files (no compiled code), shell init/wait bash inside step files, git for branch/commit/push operations.

---

## Source design

All decisions in this plan derive from `docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md`. Section references in tasks point to that doc.

## File structure

**Files created**
- `docs/patterns/domains/INDEX.md`
- `docs/patterns/domains/platform-primitives.md`
- `docs/patterns/domains/incentive.md`
- `.claude/skills/create-spec/steps/step-02-resolve-open-questions.md` (NEW)

**Files renamed (git mv, in reverse to avoid collisions)**
- `.claude/skills/create-spec/steps/step-15-…` → `step-16-…`
- `step-14-…` → `step-15-…`
- … through `step-02-load-brd-context.md` → `step-03-load-brd-context.md`

**Files modified in blueprint**
- `.claude/skills/create-spec/SKILL.md` (resumption step list)
- `.claude/skills/create-spec/steps/step-01-parse-input.md`
- `.claude/skills/create-spec/steps/step-13-generate-spec-content.md` (renumbered from 12)
- `.claude/skills/create-spec/steps/step-14-generate-technical-content.md` (renumbered from 13)
- `.claude/skills/create-spec/steps/step-15-write-plan-file.md` (renumbered from 14)
- `.claude/skills/create-spec/steps/step-16-branch-write-review-finalize.md` (renumbered from 15)
- `.claude/skills/create-spec/templates/spec-template.md`
- All routing/boundary lines inside renamed step files
- `.claude/skills/create-stories/SKILL.md`
- `.claude/skills/review-spec/SKILL.md`
- `.claude/skills/bug-fixer/SKILL.md`

**Files modified in siblings**
- `../tenxengage-backend/CLAUDE.md`
- `../tenxengage-backend/.claude/skills/load-spec/SKILL.md`
- `../tenxengage-backend/.claude/skills/load-story/SKILL.md`
- `../tenxengage-backend/.claude/skills/execute-foundation/SKILL.md`
- `../tenxengage-frontend/CLAUDE.md`
- `../tenxengage-frontend/.claude/skills/load-spec/SKILL.md`
- `../tenxengage-frontend/.claude/skills/load-story/SKILL.md`
- `../tenxengage-frontend/.claude/skills/create-mockups/SKILL.md`
- `../tenxengage-contracts/CLAUDE.md`
- `../tenxengage-contracts/.claude/skills/generate-contracts/SKILL.md`

Admin-backend / admin-frontend repos have no CLAUDE.md and no relevant skills today. Their inclusion is deferred per design section 14.1 ("to be confirmed during implementation").

## Task ordering rationale

This plan follows section 16 of the design. Each task is independently verifiable against one or more acceptance criteria in section 15. Renaming (Task 5) happens AFTER text-only edits to step-12/13/14 (Task 4) so those edits ride along with the renames.

---

## Task 1: Registry skeleton (INDEX, platform-primitives, incentive)

**Acceptance criterion:** AC6 — three registry files exist; each fits ≤1 page.

**Files:**
- Create: `docs/patterns/domains/INDEX.md`
- Create: `docs/patterns/domains/platform-primitives.md`
- Create: `docs/patterns/domains/incentive.md`

- [ ] **Step 1.1: Create directory**

```bash
mkdir -p docs/patterns/domains
```

- [ ] **Step 1.2: Write `docs/patterns/domains/INDEX.md`**

```markdown
# Domain Registry

Source of truth for domain-defining primitives. Read by `/create-spec`,
`/create-stories`, `/review-spec` (blueprint); `/load-spec`, `/load-story`,
`/execute-foundation` (backend); `/load-spec`, `/load-story`, `/create-mockups`
(frontend); `/generate-contracts` (contracts); `bug-fixer` (blueprint).

## Domains

| File | Status | Anchored on |
|---|---|---|
| [platform-primitives.md](platform-primitives.md) | in-design | — |
| [incentive.md](incentive.md)                     | active-legacy | own bespoke stack |
| [enablement.md](enablement.md)                   | planned (authored on first enablement feature) | platform-primitives |

## Slot list (canonical, 8 slots)

1. Core aggregate
2. Audience-rule entity
3. Eligibility engine contract
4. Completion/participation entity
5. Budget model (or "not applicable")
6. Approval workflow entity (or "not applicable")
7. Builder discriminator
8. Section/field storage entity

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
`{domain}/{builder-type}.md`) > domain file (`{domain}.md`) > `platform-primitives.md`.

## Governance — migration trigger

When a third domain (second non-incentive domain) lands on platform primitives,
the engineer landing that third domain runs an incentive-migration audit as a
merge prerequisite before the fourth domain ships. Migration is a scheduled
project, not implicit drift. Weaker fallback if the trigger is ever softened:
annual review of `incentive.md` vs `platform-primitives.md`, owned by the
platform architect role.
```

- [ ] **Step 1.3: Write `docs/patterns/domains/platform-primitives.md`**

```markdown
---
layer: platform-infra
status: in-design
authored: 2026-05-12
---

# Platform Builder Primitives

Shared infrastructure for builder-shaped domains. New domains anchor here.
Incentive does NOT use this layer — see `incentive.md`.

## Package roots

- Backend:   `com.tenxengage.app.platform.builder.*`
- Frontend:  `src/platform/builder/*`
- Contracts: `tenxengage-contracts/platform/builder/`

## Naming convention

Platform code uses the `Definition` suffix where legacy incentive uses `Config`.
This prevents visual collision between the two layers in mixed code reads.

- `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
- `AudienceRule` / `EligibilityChecker` (interface)

## Slot fillers provided by platform

| Slot | Filler | Location |
|---|---|---|
| Audience-rule entity        | `AudienceRule` (polymorphic via `owner_type`, `owner_id`) | `platform.builder.AudienceRule` |
| Eligibility engine contract | `EligibilityChecker` (interface)                          | `platform.builder.EligibilityChecker` |
| Builder discriminator       | `(builder_type, domain)` columns                          | on `BuilderDefinition` |
| Section/field storage       | `BuilderSectionDefinition` / `BuilderFieldDefinition`     | `platform.builder.*` |

## Slots each domain must declare itself

- Core aggregate
- Completion/participation entity
- Budget model (or "not applicable")
- Approval workflow entity (or "not applicable")

## Notes

The actual `BuilderDefinition`, `AudienceRule`, and `EligibilityChecker` Java
entities are built by the first enablement feature's spec → implementation flow,
not by this registry. This file documents intent and naming; the code lands
with the first enablement feature.
```

- [ ] **Step 1.4: Write `docs/patterns/domains/incentive.md`**

```markdown
---
domain: incentive
status: active-legacy
anchored-on: own-bespoke-stack
authored: 2026-05-12
note: |
  Predates platform primitives. Not migrated. Do not adopt this as the pattern
  for a new domain — use platform-primitives.md instead.
---

# Incentive Domain (legacy bespoke)

## Slot fillers

| Slot | Filler | Location |
|---|---|---|
| Core aggregate              | `Incentive`                                                 | `entity/Incentive.java` |
| Audience-rule entity        | `IncentiveAudienceRule`                                     | `entity/IncentiveAudienceRule.java` |
| Eligibility engine contract | `ParticipantEligibilityChecker.matchesUserEligibility(Incentive, …)` | `service/ParticipantEligibilityChecker.java:91` |
| Completion entity           | `UserIncentiveCompletion`                                   | `entity/UserIncentiveCompletion.java` |
| Budget model                | `IncentiveBudget`                                           | `entity/IncentiveBudget.java` |
| Approval workflow           | `IncentiveApprover`                                         | `entity/IncentiveApprover.java` |
| Builder discriminator       | `incentive_type` column                                     | on `BuilderSectionConfig` |
| Section/field storage       | `BuilderSectionConfig` / `BuilderFieldConfig`               | `entity/Builder*Config.java` |

## Section keys per `incentive_type`

| `incentive_type` | `section_keys` (ordered)                                              | lock summary |
|------------------|-----------------------------------------------------------------------|--------------|
| SALES            | `basics, schedule, audience, budget, criteria, approval`              | all locked except `audience` |
| TRAINING         | `basics, schedule, audience, budget, criteria (Training Courses), approval` | locked except `audience` and `criteria` |
| ACTIVITY         | `basics, schedule, audience, budget, criteria (Activity Setup), approval` | locked except `audience` and `criteria` |
| JOURNEY          | `basics, schedule, audience, budget, criteria (Journey Stages), approval` | all locked except `audience` |

## Domain conventions (prose, not slot-filled)

- Eligibility is lazy / company-mediated. No `incentive_participant` join table.
  Computed at query time from the user's current `PartnerCompany.locationAssignments`
  and `ClientRole`.
- No auto-tagging on user onboarding. No company-switch handler. **KNOWN GAPS.**
- Completion event topic: `completion-events` (Kafka).

## Notes on adjacent classes

- `EligibilityRule.java` and `EligibilityRuleGroup.java` are unprefixed but
  sit alongside `IncentiveAudienceRule` in the incentive package. They are
  considered incentive-internal pending confirmation. If a later audit
  determines they are domain-neutral, they may seed platform primitives.
```

- [ ] **Step 1.5: Verify each file is ≤1 page (≈60 lines)**

```bash
for f in docs/patterns/domains/*.md; do echo "$f: $(wc -l < "$f") lines"; done
```

Expected: each file under ~80 lines.

- [ ] **Step 1.6: Commit**

```bash
git add docs/patterns/domains/
git commit -m "docs(domains): seed domain registry — INDEX, platform-primitives, incentive (legacy)"
```

---

## Task 2: Sibling CLAUDE.md pointer lines

**Acceptance criterion:** AC8 — sibling-repo CLAUDE.md files reference the migration trigger and the registry location.

**Files:**
- Modify: `../tenxengage-backend/CLAUDE.md`
- Modify: `../tenxengage-frontend/CLAUDE.md`
- Modify: `../tenxengage-contracts/CLAUDE.md`

- [ ] **Step 2.1: Insert pointer line in `../tenxengage-backend/CLAUDE.md` after existing "Skills must read … `../tenxengage-blueprint/docs/patterns/`" line (around line 44)**

Add this paragraph immediately below that line:

```markdown
**Domain registry** (load-bearing for all builder-shaped feature work) lives at
`../tenxengage-blueprint/docs/patterns/domains/`. Read `INDEX.md` first; then the
relevant `{domain}.md` when working a slot-filling feature. Slot fillers that
differ from the registry must be flagged interactively by skills, not silently
accepted.
```

- [ ] **Step 2.2: Same insertion in `../tenxengage-frontend/CLAUDE.md`** (after the "Skills must read this repo's `PROJECT-CONTEXT.md`…" line around line 38)

Same paragraph as Step 2.1.

- [ ] **Step 2.3: Add the same pointer paragraph to `../tenxengage-contracts/CLAUDE.md`** in the analogous "Skills must read …" or pattern-source section. If no such anchor exists, append under a new `## Domain registry` heading.

- [ ] **Step 2.4: Verify**

```bash
grep -l "docs/patterns/domains" ../tenxengage-backend/CLAUDE.md ../tenxengage-frontend/CLAUDE.md ../tenxengage-contracts/CLAUDE.md
```

Expected: all three paths printed.

- [ ] **Step 2.5: Commit (one commit per repo)**

```bash
git -C ../tenxengage-backend add CLAUDE.md && git -C ../tenxengage-backend commit -m "docs(claude): point to blueprint domain registry"
git -C ../tenxengage-frontend add CLAUDE.md && git -C ../tenxengage-frontend commit -m "docs(claude): point to blueprint domain registry"
git -C ../tenxengage-contracts add CLAUDE.md && git -C ../tenxengage-contracts commit -m "docs(claude): point to blueprint domain registry"
```

---

## Task 3: Change 3 — Claude-only execution timing

**Acceptance criterion:** AC4 — `/create-spec` run prints execution time excluding human-wait.

Design refs: section 5; new `/tmp/create_spec_wait` file; bracket each user-gate.

**Files:**
- Modify: `.claude/skills/create-spec/SKILL.md` (Initialization block)
- Modify: `.claude/skills/create-spec/steps/step-01-parse-input.md` (FR/NFR gate)
- Modify: `.claude/skills/create-spec/steps/step-15-branch-write-review-finalize.md` (final print + commit/push gate)

(Note: this task runs BEFORE step renumbering. File names refer to current state. The renaming task carries these edits along.)

- [ ] **Step 3.1: Update SKILL.md initialization to also init the wait counter**

In `.claude/skills/create-spec/SKILL.md`, replace the existing init bash:

```bash
date +%s > /tmp/create_spec_start && echo "create-spec started: $(date '+%H:%M:%S')"
```

with:

```bash
date +%s > /tmp/create_spec_start && echo 0 > /tmp/create_spec_wait && echo "create-spec started: $(date '+%H:%M:%S')"
```

- [ ] **Step 3.2: In step-01-parse-input.md, bracket the FR/NFR confirmation gate**

Find the "User interaction" section (around line 42–43). Before "Present Part A + Part B to the user", insert:

```bash
# Mark start of human wait
date +%s%3N > /tmp/create_spec_wait_started
```

Immediately after "Wait for confirmation: …", and before any further action, add:

```bash
# Accumulate wait time on resume
echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
```

At the very end of step-01, add the reliability instruction:

> **On resume from any user-gate in this step, FIRST run the wait-accumulation command above before doing anything else.**

- [ ] **Step 3.3: In step-15-branch-write-review-finalize.md, bracket the commit/push prompt (section 4 step 3)**

Same pattern — `wait_started` immediately before the prompt; wait-accumulation immediately on resume.

- [ ] **Step 3.4: In step-15, replace the final wall-time print (lines 77–80)**

Replace:

```bash
echo "create-spec wall time: $(($(date +%s) - $(cat /tmp/create_spec_start)))s"
```

with:

```bash
wall=$(( $(date +%s) - $(cat /tmp/create_spec_start) ))
wait_s=$(( $(cat /tmp/create_spec_wait) / 1000 ))
exec_s=$(( wall - wait_s ))
echo "create-spec execution time: ${exec_s}s (wall=${wall}s, human-wait=${wait_s}s)"
```

- [ ] **Step 3.5: Verify**

```bash
grep -n "create_spec_wait" .claude/skills/create-spec/SKILL.md .claude/skills/create-spec/steps/step-01-parse-input.md .claude/skills/create-spec/steps/step-15-branch-write-review-finalize.md
```

Expected: matches in all three files.

- [ ] **Step 3.6: Commit**

```bash
git add .claude/skills/create-spec/
git commit -m "feat(create-spec): track Claude-only execution time excluding human wait"
```

---

## Task 4: Change 2 — interactive ambiguity model

**Acceptance criterion:** AC3 — ambiguities surface interactively with no cap; only deferred items become `NEEDS_CLARIFICATION` markers.

Design refs: section 4.

**Files:**
- Modify: `.claude/skills/create-spec/steps/step-12-generate-spec-content.md`
- Modify: `.claude/skills/create-spec/steps/step-14-write-plan-file.md`

(Pre-renumbering names; rename task carries edits forward.)

- [ ] **Step 4.1: In step-12-generate-spec-content.md (line 45), replace the cap rule**

Replace:

```markdown
3. Use at most 3 `NEEDS_CLARIFICATION` markers for genuine ambiguities:
   ```
   > NEEDS_CLARIFICATION: {specific question}
   ```
```

with:

```markdown
3. **Resolve ambiguities interactively.** When an ambiguity surfaces during a section's generation, raise it as a focused question to the user immediately. There is NO cap on count. Bracket each user-gate with the wait-accumulation pattern:

   ```bash
   date +%s%3N > /tmp/create_spec_wait_started
   ```

   (then ask the question; on resume:)

   ```bash
   echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
   ```

   - If the user answers: fold the answer into the current section and continue. Do NOT write a marker.
   - If the user defers ("TBD", "ask offline", "I don't know"): write a `NEEDS_CLARIFICATION` marker inline at the point of ambiguity:
     ```
     > NEEDS_CLARIFICATION: {specific question}
     ```

   The "only deferred items become markers" rule is what enforces no-cap without inflating the spec.
```

- [ ] **Step 4.2: In step-13-generate-technical-content.md, apply the same interactive resolution**

If step-13 has any ambiguity language (none currently — verified via grep earlier), append a single line after its existing "Use at most" guidance (or its rules block):

```markdown
- Ambiguities in technical content follow the same interactive resolution pattern as step 12 (renumbered 13): raise → user answers → fold in; or user defers → write `NEEDS_CLARIFICATION` inline. No cap on count.
```

- [ ] **Step 4.3: In step-14-write-plan-file.md (line 29), remove the cap line**

Replace:

```markdown
   - `## NEEDS_CLARIFICATION` (if any) — at most 3 items
```

with:

```markdown
   - `## NEEDS_CLARIFICATION` (if any) — list every deferred item from steps 13 and 14 (renumbered). No cap.
```

- [ ] **Step 4.4: Verify the cap is gone**

```bash
grep -n "at most 3\|3 NEEDS_CLARIFICATION\|3 markers\|3 items" .claude/skills/create-spec/
```

Expected: no matches (or only matches in unrelated contexts — confirm visually).

- [ ] **Step 4.5: Commit**

```bash
git add .claude/skills/create-spec/
git commit -m "feat(create-spec): interactive ambiguity resolution; remove 3-marker cap"
```

---

## Task 5: Step renumbering (current 02–15 → 03–16)

**Acceptance criterion:** none directly — enables AC1 (new step 02 placement).

This task ONLY renames files and updates routing references. Content edits happen in later tasks.

- [ ] **Step 5.1: Rename in reverse order to avoid collisions**

```bash
cd .claude/skills/create-spec/steps
git mv step-15-branch-write-review-finalize.md step-16-branch-write-review-finalize.md
git mv step-14-write-plan-file.md             step-15-write-plan-file.md
git mv step-13-generate-technical-content.md  step-14-generate-technical-content.md
git mv step-12-generate-spec-content.md       step-13-generate-spec-content.md
git mv step-11-derive-slug.md                 step-12-derive-slug.md
git mv step-10-permissions-analysis.md        step-11-permissions-analysis.md
git mv step-09-test-strategy.md               step-10-test-strategy.md
git mv step-08-events-analysis.md             step-09-events-analysis.md
git mv step-07-security-analysis.md           step-08-security-analysis.md
git mv step-06-scope-decomposition.md         step-07-scope-decomposition.md
git mv step-05-load-shape-references.md       step-06-load-shape-references.md
git mv step-04-detect-feature-shape.md        step-05-detect-feature-shape.md
git mv step-03-load-project-context.md        step-04-load-project-context.md
git mv step-02-load-brd-context.md            step-03-load-brd-context.md
cd -
```

- [ ] **Step 5.2: Verify the rename**

```bash
ls .claude/skills/create-spec/steps/
```

Expected: `step-01-parse-input.md`, then a gap (no `step-02-*`) — Task 6 fills it. Files `step-03-load-brd-context.md` through `step-16-branch-write-review-finalize.md` present.

- [ ] **Step 5.3: For each renamed step file, bump the "route to step NN" boundary at the bottom by one**

The pattern is: `route to step NN: read steps/step-NN-*.md`. For each renamed file, the target boundary number = old number + 1, and the source step's number also bumps.

Concretely, do this with sed (one file at a time to keep the diff reviewable):

```bash
cd .claude/skills/create-spec/steps
# In step-03 (was step-02), bump "step 03" → "step 04" in the boundary
sed -i '' -E 's|route to step 03:.*step-03-load-project-context.md|route to step 04: read steps/step-04-load-project-context.md|' step-03-load-brd-context.md
# In step-04 (was step-03), boundary target 04 → 05
sed -i '' -E 's|route to step 04:.*step-04-detect-feature-shape.md|route to step 05: read steps/step-05-detect-feature-shape.md|' step-04-load-project-context.md
# In step-05 (was step-04), 05 → 06
sed -i '' -E 's|route to step 05:.*step-05-load-shape-references.md|route to step 06: read steps/step-06-load-shape-references.md|' step-05-detect-feature-shape.md
# In step-06 (was step-05), 06 → 07
sed -i '' -E 's|route to step 06:.*step-06-scope-decomposition.md|route to step 07: read steps/step-07-scope-decomposition.md|' step-06-load-shape-references.md
# In step-07 (was step-06), 07 → 08
sed -i '' -E 's|route to step 07:.*step-07-security-analysis.md|route to step 08: read steps/step-08-security-analysis.md|' step-07-scope-decomposition.md
# step-08 → step-09
sed -i '' -E 's|route to step 08:.*step-08-events-analysis.md|route to step 09: read steps/step-09-events-analysis.md|' step-08-security-analysis.md
# step-09 → step-10
sed -i '' -E 's|route to step 09:.*step-09-test-strategy.md|route to step 10: read steps/step-10-test-strategy.md|' step-09-events-analysis.md
# step-10 → step-11
sed -i '' -E 's|route to step 10:.*step-10-permissions-analysis.md|route to step 11: read steps/step-11-permissions-analysis.md|' step-10-test-strategy.md
# step-11 → step-12
sed -i '' -E 's|route to step 11:.*step-11-derive-slug.md|route to step 12: read steps/step-12-derive-slug.md|' step-11-permissions-analysis.md
# step-12 → step-13
sed -i '' -E 's|route to step 12:.*step-12-generate-spec-content.md|route to step 13: read steps/step-13-generate-spec-content.md|' step-12-derive-slug.md
# step-13 → step-14
sed -i '' -E 's|route to step 13:.*step-13-generate-technical-content.md|route to step 14: read steps/step-14-generate-technical-content.md|' step-13-generate-spec-content.md
# step-14 → step-15
sed -i '' -E 's|route to step 14:.*step-14-write-plan-file.md|route to step 15: read steps/step-15-write-plan-file.md|' step-14-generate-technical-content.md
# step-15 → step-16
sed -i '' -E 's|route to step 15:.*step-15-branch-write-review-finalize.md|route to step 16: read steps/step-16-branch-write-review-finalize.md|' step-15-write-plan-file.md
cd -
```

(step-16 is terminal — no boundary update needed.)

- [ ] **Step 5.4: Also bump in-prose intra-step references**

```bash
grep -rn "step 0[2-9]\|step 1[0-5]\|step-0[2-9]\|step-1[0-5]" .claude/skills/create-spec/steps/
```

Review each match. Where text says e.g. "(that's step 06)" or "(see step 12)", increment by 1 if the referenced step was renumbered. Edit each one manually with the Edit tool. Common references to watch for: "that's step 03", "step 06", "step 07", "step 10", "step 12", "step 13", "step 14", "step 15".

- [ ] **Step 5.5: Update SKILL.md resumption list**

`.claude/skills/create-spec/SKILL.md` references `stepsCompleted` count "14 step names" implicitly (the plan file schema). Look in the resumption section (lines 22–28) and any references to the step count; update to 15 (now 15 stepsCompleted before step 16, since new step-02 is one of them). Specifically: line 24 — "after step 14 but didn't write files. Resume at step 15." becomes "after step 15 but didn't write files. Resume at step 16."

- [ ] **Step 5.6: Update `references/plan-file-schema.md` if it mentions step counts**

```bash
grep -n "step 1[2-5]\|step-1[2-5]\|14 step\|15 step" .claude/skills/create-spec/references/plan-file-schema.md
```

For each match, bump by 1 where it refers to a renumbered step.

- [ ] **Step 5.7: Verify routing graph**

```bash
for f in .claude/skills/create-spec/steps/step-*.md; do
  echo "=== $f ==="
  grep -E "route to step" "$f" | tail -1
done
```

Expected: each non-terminal step ends with `route to step NN: read steps/step-NN-...md` where NN is current_file_number + 1, except step-01 (still routes to step-02 — which will be created in Task 6) and step-16 (terminal).

- [ ] **Step 5.8: Commit**

```bash
git add .claude/skills/create-spec/
git commit -m "refactor(create-spec): renumber steps 02–15 → 03–16 to make room for new step 02"
```

---

## Task 6: New step 02 — resolve open questions, gather additions, draft builder structure

**Acceptance criterion:** AC1 — open-questions surface as user-gates; AC2 — Phase-2 Part B draft of builder structure.

Design refs: section 3 (full Phase 1 + Phase 2 spec); section 10 (draft-and-present pattern).

**Files:**
- Create: `.claude/skills/create-spec/steps/step-02-resolve-open-questions.md`
- Modify: `.claude/skills/create-spec/steps/step-01-parse-input.md` (boundary)

- [ ] **Step 6.1: Write `step-02-resolve-open-questions.md`**

```markdown
# Step 02: resolve-open-questions-and-additions

## Goal
For Mode 1 (BRD identifier) only: resolve every open question from the feature brief, gather any net-new additions from the user, and (if the feature is builder-shaped) present a draft builder structure for review.

## Inputs (from prior steps)
- Locked FR/NFR set (step 01)
- Input mode flag
- For Mode 1: BRD slug and feature ID; the feature brief content already in conversation

## Mode gate
- If input mode is Mode 2, 3, or 4 → SKIP this step entirely. Route to step 03.
- If Mode 1 → continue.

## Procedure

### Phase 1 — derive draft (no user interaction)

Re-read the feature brief (already in context from step 01) and extract:

1. **Open questions** — every bullet under the brief's `## Edge cases / open questions` section, verbatim. If the section is missing or empty, record "none".

2. **Slot-filling detection.** The feature is *slot-filling* if its brief introduces or modifies any of: a builder (multi-section wizard producing a configurable entity), an audience model, an eligibility decision, a budget concept, an approval workflow, or a completion semantic. Set `$SLOT_FILLING = true | false`.

3. **Builder-shape detection.** The feature is *builder-shaped* if Phase-1 detected a builder. Set `$BUILDER_SHAPED = true | false`.

4. **Builder draft (only if `$BUILDER_SHAPED`).** From the brief's FRs, business rules, and user journey, derive a first-pass draft:
   - Section list with lock flags (`locked` | `customizable`).
   - Per-section sub-entities and fields, where derivable from the brief.
   - Mark items the brief does not specify as **"needs your input"** — they become focused questions in Phase 2.

5. **Domain awareness (only if `$SLOT_FILLING`).** See step 02-domain-awareness procedure below.

### Phase 2 — present draft and gather input

This phase has THREE parts, presented in one consolidated message to the user.

**Bracket the gate with wait-accumulation:**
```bash
date +%s%3N > /tmp/create_spec_wait_started
```
(present Phase 2; on resume:)
```bash
echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
```

**Part A — open questions from the brief.**
For each open question:
- If the brief gives context (e.g., "ADR-07 must be resolved"), include it.
- Ask the user. The user answers, or defers with "TBD" / "ask offline".
- Resolved answers → carried to step 13 for inlining into the appropriate spec section.
- Deferred items → list of `(question, target_section)` for `NEEDS_CLARIFICATION` placement in step 13.

**Part B — draft review (builder sub-flow, only if `$BUILDER_SHAPED`).**
Present the draft plainly:

```
## Proposed {Feature} Builder structure

Sections:
  1. {section-key}    {locked|customizable}
       {SubEntity}:
         - {field}
         - {field}                                ← need your input

Items that need your input:
  • {focused question 1}
  • {focused question 2}

Anything in the draft that's missing or wrong?
```

The user confirms, edits, or pushes back. "Needs your input" items become focused sub-prompts; the "missing or wrong?" prompt gives them the floor.

**Part C — anything else?**
Final prompt — verbatim:
> "Anything else to add or change beyond what's captured in the feature brief?"

Each net-new item is **classified by the skill into a target spec section** (FRs, NFRs, Business Rules, Domain Concepts, Domain Events, Permissions, Data Retention, Caching Strategy, Observability, Edge Cases, Out of Scope). Present the classification back to the user and confirm.

### Domain awareness procedure (only if `$SLOT_FILLING`)

1. Read `docs/patterns/domains/INDEX.md`.

2. Prompt the user:
   ```
   This feature is filling builder-shaped slots. Which domain does it belong to?
     • incentive  (existing legacy bespoke domain — uses IncentiveAudienceRule etc.)
     • enablement (new domain — anchored on platform primitives)
     • A new domain not listed above
   ```

3. Load the domain file based on selection:
   - `incentive` → read `docs/patterns/domains/incentive.md`.
   - `enablement` → if `docs/patterns/domains/enablement.md` exists, read it; otherwise enter bootstrap flow (interactively author `enablement.md` with the user, anchored on `platform-primitives.md`; the new file is committed alongside the spec in step 16).
   - new domain → bootstrap flow: interactively author `{new-domain}.md` anchored on `platform-primitives.md`.

4. Resolve slot fillers in this order: per-`builder_type` override file > domain file > `platform-primitives.md`. Hold the resolved slot fillers in conversation context for drift detection in step 13.

5. Set `$DOMAIN` and (if applicable) `$BUILDER_TYPE` for spec frontmatter (step 13 / step 15).

6. **Slot addition guard.** If during Phase 1 derivation you identified that the canonical 8-slot list is insufficient for this feature, DO NOT propose a slot addition mid-feature. Emit `NEEDS_GOVERNANCE_DECISION: {description}` to the user and abort the run. The user resolves out-of-band before re-running.

## Output for downstream steps

- Resolved-question decisions → `{question, answer, target_section}` list
- Deferred questions → `{question, target_section}` list
- Builder structure (if applicable) → `{sections: [{key, lock, sub_entities, fields}]}`
- Net-new "anything else?" additions → `{section_name → [items]}`
- `$DOMAIN`, `$BUILDER_TYPE` (when set)
- Resolved slot fillers (when slot-filling)

## Boundary
All inputs gathered → route to step 03: read `steps/step-03-load-brd-context.md`.
```

- [ ] **Step 6.2: Update step-01-parse-input.md boundary**

Replace the existing boundary line:

```markdown
User confirms understanding → route to step 02: read `steps/step-02-load-brd-context.md`.
```

with:

```markdown
User confirms understanding → route to step 02: read `steps/step-02-resolve-open-questions.md`.
```

- [ ] **Step 6.3: Apply draft-and-present pattern to step-01 FR/NFR confirmation**

In step-01's "User interaction" section, replace the bare "Wait for confirmation: 'Is this understanding correct? Anything to add or change?'" with this draft-and-present version:

```markdown
Present Part A + Part B to the user. After the draft:
- Itemize anything the skill could NOT derive from the brief as a separate "Items that need your input" list.
- Ask: "Anything in the FR/NFR draft that's missing or wrong?"
- Wait for the user to confirm, edit, or push back. If they push back, regenerate the draft and re-present.
```

- [ ] **Step 6.4: Verify step routing graph end-to-end**

```bash
for f in .claude/skills/create-spec/steps/step-*.md; do
  echo "=== $(basename $f) ==="
  grep -E "route to step" "$f" | tail -1
done
```

Expected: step-01 → step-02 (resolve-open-questions); step-02 → step-03; step-03 → step-04; … step-15 → step-16; step-16 terminal.

- [ ] **Step 6.5: Update SKILL.md `stepsCompleted` enumeration**

If `.claude/skills/create-spec/SKILL.md` lists specific step names anywhere (resumption check), add `resolve-open-questions` between `parse-input` and `load-brd-context`. Also update step counts (now 16 total).

- [ ] **Step 6.6: Commit**

```bash
git add .claude/skills/create-spec/
git commit -m "feat(create-spec): add step 02 — resolve open questions + additions + builder draft"
```

---

## Task 7: Change 4 — domain awareness in step 13 (drift detection) + spec frontmatter

**Acceptance criterion:** AC5 — slot-filling spec writes `domain:` to frontmatter and prompts on slot-filler difference.

Design refs: section 6.4 (drift detection), section 12 (frontmatter additions), section 6.6 (bottom-up promotion).

**Files:**
- Modify: `.claude/skills/create-spec/templates/spec-template.md`
- Modify: `.claude/skills/create-spec/steps/step-13-generate-spec-content.md`
- Modify: `.claude/skills/create-spec/steps/step-15-write-plan-file.md`

- [ ] **Step 7.1: Add `domain` and `builder_type` to spec-template.md frontmatter**

In `.claude/skills/create-spec/templates/spec-template.md` lines 1–9, change:

```yaml
---
slug: {{slug}}
name: {{FEATURE_NAME}}
status: draft
format: story-sliced
roadmap: {{roadmap_or_null}}
created: {{DATE}}
contract: null
---
```

to:

```yaml
---
slug: {{slug}}
name: {{FEATURE_NAME}}
status: draft
format: story-sliced
roadmap: {{roadmap_or_null}}
domain: {{domain_or_null}}           # incentive | enablement | {new-domain} | null (non-slot-filling)
builder_type: {{builder_type_or_null}}  # e.g., COURSE | LEARNING_PATH | SALES | null (non-builder-shaped)
created: {{DATE}}
contract: null
---
```

- [ ] **Step 7.2: In step-13-generate-spec-content.md, add drift-detection sub-procedure**

After the existing Data Model bullet (around the entity-shape-decisions block), insert a new sub-section:

```markdown
   - **Domain slot-filler drift check** (only if `$SLOT_FILLING`):
     For each slot the spec proposes a filler for (entity name, interface name, table column, topic name), compare against the registry filler resolved in step 02. If different:

     Bracket the gate:
     ```bash
     date +%s%3N > /tmp/create_spec_wait_started
     ```

     Prompt:
     > "This spec fills slot `{slot-name}` with `{proposed}`. The {domain} registry currently lists `{registry-filler}` (from {layer: platform | domain | builder-type override}). Choose:
     >   A) Add `{proposed}` as a domain-specific override (writes to `docs/patterns/domains/{domain}.md`)
     >   B) Replace the registry filler with `{proposed}` (writes to `{domain}.md`)
     >   C) Mark as a deviation for this feature only (no registry write; spec carries an inline note)"

     On resume:
     ```bash
     echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
     ```

     Record the decision for the plan file (step 15).

   - **Bottom-up primitive promotion check** (only if `$SLOT_FILLING`): If a sub-entity referenced by the spec (e.g., `Lesson`) already appears in another spec's `features/*/spec.md` within the same domain, prompt:
     > "`{sub-entity}` is now referenced by {existing-features} and this feature. Promote to a shared `{domain}` primitive?"
     On YES, plan-file records an addition to `{domain}.md` under "Shared sub-entities".
```

- [ ] **Step 7.3: In step-13-generate-spec-content.md, ensure frontmatter is populated**

Find the section that generates the spec.md preamble (frontmatter). Add a rule:

```markdown
- **Frontmatter `domain`:** populate with `$DOMAIN` from step 02. Set to `null` if the feature is not slot-filling.
- **Frontmatter `builder_type`:** populate with `$BUILDER_TYPE` from step 02. Set to `null` if not builder-shaped.
```

- [ ] **Step 7.4: In step-15-write-plan-file.md, include registry edits in the plan**

After the existing `## NEEDS_CLARIFICATION` line in the section list, add:

```markdown
   - `## Registry edits` (if any) — list of `{file, change-description}` pairs for `docs/patterns/domains/{domain}.md` and/or new `{domain}/{builder-type}.md` override files. Step 16 applies these edits in the same commit as the spec.
```

- [ ] **Step 7.5: In step-16-branch-write-review-finalize.md, apply registry edits before commit**

In section 4 (after review completes, before the commit/push prompt), insert a new sub-step:

```markdown
0. **Apply registry edits.** If the plan file includes a `## Registry edits` section, write each listed change to `docs/patterns/domains/...` files. Stage them so they ship in the same commit as `features/{slug}/spec.md`.
```

- [ ] **Step 7.6: Verify**

```bash
grep -n "domain:\|builder_type:" .claude/skills/create-spec/templates/spec-template.md
grep -n "drift\|registry filler\|Registry edits" .claude/skills/create-spec/steps/step-13-generate-spec-content.md .claude/skills/create-spec/steps/step-15-write-plan-file.md .claude/skills/create-spec/steps/step-16-branch-write-review-finalize.md
```

Expected: matches in each file.

- [ ] **Step 7.7: Commit**

```bash
git add .claude/skills/create-spec/
git commit -m "feat(create-spec): domain awareness — frontmatter, drift detection, registry edits in plan"
```

---

## Task 8: Sibling skill updates — backend

**Acceptance criterion:** AC7 — sibling `/load-spec`, `/load-story`, `/execute-foundation` read the domain registry.

Each sibling skill gets a single new read step inserted after the existing "read spec.md" step.

**Files:**
- Modify: `../tenxengage-backend/.claude/skills/load-spec/SKILL.md`
- Modify: `../tenxengage-backend/.claude/skills/load-story/SKILL.md`
- Modify: `../tenxengage-backend/.claude/skills/execute-foundation/SKILL.md`

- [ ] **Step 8.1: In `load-spec/SKILL.md`, after step 3 ("Read the spec"), insert a new step 3c**

```markdown
3c. **Read domain registry** (only if the spec frontmatter has `domain:` non-null):
   - Read `../tenxengage-blueprint/docs/patterns/domains/INDEX.md` (slot list + drift policy).
   - Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md` (slot fillers + section keys).
   - If `builder_type:` is set AND `../tenxengage-blueprint/docs/patterns/domains/{domain}/{builder-type}.md` exists, read it too — it overrides slot fillers from the domain file.
   - These slot fillers (entity names, interface names, column names) are load-bearing for any code this session writes. Use them verbatim — do not invent or rename.
```

- [ ] **Step 8.2: In `load-story/SKILL.md`, find the analogous "Read the spec" step and insert the same domain-registry read step**

```markdown
**Read domain registry** (only if `spec.md` frontmatter has `domain:` non-null):
- Read `../tenxengage-blueprint/docs/patterns/domains/INDEX.md`.
- Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`.
- If `builder_type:` set and `{domain}/{builder-type}.md` exists, read it too.
- Use these slot fillers for entity names, service names, and types. Story-level deviations from these fillers must be flagged to the user before writing code.
```

Insert after the existing spec-read step.

- [ ] **Step 8.3: In `execute-foundation/SKILL.md`, insert the same domain-registry read step after the spec read**

Same content as Step 8.2, with the closing note: "Foundation tasks for a slot-filling feature MUST use registry primitives. If `foundation.md` references a name that conflicts, abort and flag."

- [ ] **Step 8.4: Verify**

```bash
grep -l "docs/patterns/domains" ../tenxengage-backend/.claude/skills/{load-spec,load-story,execute-foundation}/SKILL.md
```

Expected: all three files print.

- [ ] **Step 8.5: Commit**

```bash
git -C ../tenxengage-backend add .claude/skills/
git -C ../tenxengage-backend commit -m "feat(skills): load domain registry from blueprint for slot-filling specs"
```

---

## Task 9: Sibling skill updates — frontend

**Acceptance criterion:** AC7 — frontend `/load-spec`, `/load-story`, `/create-mockups` read the domain registry; `/create-mockups` additionally reads `section_keys` + lock flags.

**Files:**
- Modify: `../tenxengage-frontend/.claude/skills/load-spec/SKILL.md`
- Modify: `../tenxengage-frontend/.claude/skills/load-story/SKILL.md`
- Modify: `../tenxengage-frontend/.claude/skills/create-mockups/SKILL.md`

- [ ] **Step 9.1: In `load-spec/SKILL.md`, insert domain-registry read step**

Same pattern as Step 8.1 — after step 3 ("Read the spec").

- [ ] **Step 9.2: In `load-story/SKILL.md`, same insertion**

Same pattern as Step 8.2.

- [ ] **Step 9.3: In `create-mockups/SKILL.md`, insert a builder-shaped read step**

Find the section that reads story files (around step "Read stories"). After it, insert:

```markdown
**Read domain registry for builder structure** (only if the feature is slot-filling — spec.md frontmatter has `domain:` non-null AND `builder_type:` non-null):
- Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`.
- Find the table that maps `builder_type` to `section_keys` (ordered) and per-section lock flags.
- If `../tenxengage-blueprint/docs/patterns/domains/{domain}/{builder-type}.md` exists, prefer its section keys over the domain default.
- Use these `section_keys` to determine mockup section ORDER. Use the lock flags to render locked sections distinctly (e.g., grayed/with a lock icon) from customizable sections.
- **Without this read, mockups silently drift from structural primitives — do not skip.**
```

- [ ] **Step 9.4: Verify and commit**

```bash
grep -l "docs/patterns/domains" ../tenxengage-frontend/.claude/skills/{load-spec,load-story,create-mockups}/SKILL.md
git -C ../tenxengage-frontend add .claude/skills/
git -C ../tenxengage-frontend commit -m "feat(skills): load domain registry; create-mockups uses section_keys + lock flags"
```

---

## Task 10: Sibling skill update — contracts

**Acceptance criterion:** AC7 — `/generate-contracts` reads `spec.domain` and uses domain-appropriate type naming.

**Files:**
- Modify: `../tenxengage-contracts/.claude/skills/generate-contracts/SKILL.md`

- [ ] **Step 10.1: Insert domain-registry read step after the existing spec.md read**

```markdown
**Read domain registry for type naming** (only if `spec.md` frontmatter has `domain:` non-null):
- Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`.
- Use the slot fillers for type naming in generated contracts. Examples:
  - `domain: incentive` → audience-rule contract type is `IncentiveAudienceRule`.
  - `domain: enablement` → audience-rule contract type is `AudienceRule` (from platform primitives — polymorphic over `owner_type`/`owner_id`).
- If the spec proposes a contract type that conflicts with the registry, flag it before writing.
```

- [ ] **Step 10.2: Verify and commit**

```bash
grep -n "docs/patterns/domains" ../tenxengage-contracts/.claude/skills/generate-contracts/SKILL.md
git -C ../tenxengage-contracts add .claude/skills/
git -C ../tenxengage-contracts commit -m "feat(generate-contracts): respect spec.domain for type naming"
```

---

## Task 11: Blueprint downstream — `/create-stories`, `/review-spec`, `bug-fixer`

**Acceptance criteria:** AC7 (skills read the registry) and the AC5 "review" half — `/review-spec` validates `spec.domain` is present + consistent.

**Files:**
- Modify: `.claude/skills/create-stories/SKILL.md`
- Modify: `.claude/skills/review-spec/SKILL.md`
- Modify: `.claude/skills/bug-fixer/SKILL.md`

- [ ] **Step 11.1: In `create-stories/SKILL.md`, add a domain-registry read step**

After the existing spec.md read, insert:

```markdown
**Read domain registry** (only if `spec.md` frontmatter has `domain:` non-null):
- Read `docs/patterns/domains/INDEX.md` and `docs/patterns/domains/{domain}.md`.
- Propagate `domain` and `builder_type` into per-story frontmatter (where stories declare scope).
- Story-level type, service, and repository names MUST conform to the slot fillers in `{domain}.md`. Flag any story that proposes a different name before writing the story file.
```

- [ ] **Step 11.2: In `review-spec/SKILL.md`, add a new check (Check 16: Domain registry conformance)**

After existing checks (the file has 15 + 2 conditional), append:

```markdown
### Check 16: Domain registry conformance (slot-filling features only)

Conditional: applies only when `spec.md` frontmatter has `domain:` non-null.

- `spec.domain` is non-null and matches one of the known domains in `docs/patterns/domains/INDEX.md` (or is being introduced as a new domain in this same commit — check the staged diff).
- `spec.builder_type` is non-null when the feature is builder-shaped (FRs reference a multi-section wizard or section-keyed builder).
- For each entity in the spec's Data Model that fills a canonical slot (audience rule, eligibility engine, completion entity, budget, approval, section/field storage), the entity name matches the registry filler in `docs/patterns/domains/{domain}.md` — OR the spec carries an explicit deviation note linking back to a Registry edits decision in the plan file.
- If `domain` is non-null but `docs/patterns/domains/{domain}.md` does not exist AND is not being introduced in this commit → CRITICAL.

Output one of PASSED / CRITICAL / WARNING / SUGGESTION.
```

Update the "15 Checks + 2 Conditional" line in the file header to "16 Checks + 2 Conditional".

- [ ] **Step 11.3: In `bug-fixer/SKILL.md`, add domain-registry read**

Find where the skill reads `spec.md` for the affected feature. After that read, insert:

```markdown
**Read domain registry** (only if `spec.md` frontmatter has `domain:` non-null):
- Read `docs/patterns/domains/INDEX.md` and `docs/patterns/domains/{domain}.md`.
- The fix MUST NOT introduce names from a different domain (e.g., don't introduce `IncentiveAudienceRule` into an `enablement` feature, or vice versa).
- If the bug stems from cross-domain leakage, flag this explicitly in the bug-fix MR description.
```

- [ ] **Step 11.4: Verify and commit**

```bash
grep -l "docs/patterns/domains" .claude/skills/create-stories/SKILL.md .claude/skills/review-spec/SKILL.md .claude/skills/bug-fixer/SKILL.md
git add .claude/skills/
git commit -m "feat(skills): create-stories, review-spec, bug-fixer read domain registry"
```

---

## Task 12: End-to-end smoke test (manual)

**Acceptance criterion:** all 8 criteria validated against a real run.

This is a manual integration test, not an automated check. The engineer chooses any Mode-1 feature brief that exists in `roadmaps/{slug}/features/` and runs `/create-spec {slug} F-NN`.

- [ ] **Step 12.1: Pick a real Mode-1 feature brief**

```bash
ls roadmaps/*/features/F-*.md | head -5
```

Note one with a non-empty `## Edge cases / open questions` section. Bonus if it's builder-shaped (to exercise AC2).

- [ ] **Step 12.2: Run `/create-spec` on that brief**

In a fresh Claude Code session: `/create-spec {slug} F-NN`.

Verify each acceptance criterion as the run progresses:

- AC1 — step 02 surfaces every open question as a user-gate.
- AC2 — for a builder-shaped brief, step 02 Part B prints the draft with locked/customizable flags and a "needs your input" list.
- AC3 — if an ambiguity surfaces during step 13, it's a real prompt; deferring writes one `NEEDS_CLARIFICATION` per deferred item (no cap).
- AC4 — terminal print is `execution time: Xs (wall=Ys, human-wait=Zs)`.
- AC5 — for a slot-filling brief: domain selection prompt fires; `spec.md` frontmatter has `domain:` set; any slot-filler difference prompts.
- AC6 — `wc -l docs/patterns/domains/*.md` confirms each is ≤80 lines.
- AC7 — `cd ../tenxengage-backend && /load-spec {slug}` reads the registry (mention in its output).
- AC8 — `grep "migration trigger\|docs/patterns/domains" ../tenxengage-*/CLAUDE.md` returns matches in backend, frontend, contracts.

- [ ] **Step 12.3: If any AC fails, file a punch-list and iterate**

Use the failing AC's design section to identify which step file to revise. Edit, commit, re-run.

- [ ] **Step 12.4: Note open items for follow-up**

From design section 14:
- Admin-backend/-frontend skill updates — confirm during this smoke test whether those repos have `/load-spec` / `/load-story` skills (currently they don't; defer if still absent).
- `EligibilityRule.java` / `EligibilityRuleGroup.java` classification — confirmed when authoring final `incentive.md`. Update `incentive.md` if classification changes.
- `/tmp/create_spec_wait` reliability — observe whether wait-accumulation drifts. If it does, defer to a hook-based bracket in a follow-up.

---

## Self-review notes

- Spec coverage: all 8 changes from section 2 of the design have at least one task (Change 1 → Tasks 6+7; Change 2 → Task 4; Change 3 → Task 3; Change 4 → Tasks 6+7; Change 5 → Task 1; Change 6 → Task 1 governance text; Change 7 → Tasks 2, 8, 9, 10, 11; Change 8 → Tasks 6+3).
- All 8 acceptance criteria from section 15 have a task that produces them (AC1 → T6, AC2 → T6, AC3 → T4, AC4 → T3, AC5 → T6+T7, AC6 → T1, AC7 → T8/9/10/11, AC8 → T2). AC validation happens in Task 12.
- Type consistency: `$DOMAIN`, `$BUILDER_TYPE`, `$SLOT_FILLING`, `$BUILDER_SHAPED` are introduced in Task 6 and consumed by Task 7 — names match.
- Renaming order in Task 5 step 5.1 is reverse (16→15→14…) to avoid `git mv` collisions.
- No placeholders: all step content blocks are concrete (the only `{...}` markers are deliberate template variables passed through to skill files).
