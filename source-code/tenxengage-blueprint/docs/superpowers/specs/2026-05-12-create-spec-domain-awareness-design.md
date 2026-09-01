---
title: /create-spec enhancements — open-questions resolution, interactive ambiguity, Claude-only timing, and domain awareness
date: 2026-05-12
status: design
authors: vijay@tenxengage.com (driver), Claude (drafting)
scope: tenxengage-blueprint + sibling repos
---

# /create-spec enhancements — design

## 1. Background and motivation

`/create-spec` today reads a feature brief from `roadmaps/{slug}/features/F-NN-*.md` and produces a spec at `specs/{slug}/spec.md`. Through use, four classes of gap have surfaced:

1. Feature briefs include an `## Edge cases / open questions` section, but `/create-spec` ignores it. Questions the brief explicitly flagged for the spec author go un-asked at speccing time and surface only during implementation.
2. The skill caps clarifications at three (`NEEDS_CLARIFICATION` markers). The cap encourages hiding genuine ambiguity instead of resolving it.
3. The skill prints wall time at the end of a run, which includes every minute the user spent answering prompts. The metric is unusable for tracking Claude's own execution cost.
4. The skill has no notion of *domain*. The codebase currently has a builder stack with "incentive" baked into entity names, table columns, and engine signatures. The first enablement (course, learning-path, certification) feature speccing today would silently inherit incentive-shaped primitives because the only "existing pattern" available to Claude is incentive. There is no signal in the source code that this pattern is legacy and should not be adopted by new domains.

This design addresses all four, plus the cross-repo propagation needed to make domain awareness usable by `/create-stories`, `/load-spec`, `/load-story`, `/create-mockups`, and `bug-fixer`.

## 2. Summary of changes

| # | Change | Scope |
|---|---|---|
| 1 | New step 04 — "Resolve open questions, gather additions, and (when applicable) draft builder structure" — runs after BRD context and project context are loaded | `/create-spec` only |
| 2 | Interactive ambiguity model — no cap; raise when found; only unresolvable items become `NEEDS_CLARIFICATION` markers | `/create-spec` steps 13–15 |
| 3 | Claude-only execution time — wall − human_wait, bracketed at every user-gate | `/create-spec` all steps with gates |
| 4 | Domain awareness — `spec.domain` frontmatter; drift detection on a fixed slot list | `/create-spec` step 04; `/create-stories`; `/review-spec`; sibling-repo skills |
| 5 | Domain registry at `docs/patterns/domains/` — INDEX, platform-primitives, incentive (legacy), enablement (on-demand) | New files in blueprint |
| 6 | Parallel-rails migration strategy — incentive untouched; platform primitives are new code; migration trigger = third domain landing on platform primitives | Strategy/governance |
| 7 | Cross-repo surfacing — sibling skills read `../tenxengage-blueprint/docs/patterns/domains/` directly; sibling CLAUDE.md files updated | backend, frontend, admin-*, contracts |
| 8 | Draft-and-present interaction — skill drafts from the brief, presents plainly, lists items needing input as specific questions, asks "anything missing or wrong?" | `/create-spec` steps 01, 02, 12, 13 |

## 3. Change 1 — new step 04: resolve open questions, gather additions, draft builder structure

**Placement.** Position 04 in the step chain, *after* load-brd-context (now step 02) and load-project-context (now step 03). The original design placed this step at position 02, which meant it ran before the digest, PROJECT-CONTEXT, entity-filename globs, and the domain registry were loaded — leaving slot-filling detection blind to the codebase and forcing the user to answer ADR-referencing open questions without the digest content available. The corrected ordering is:

| Position | Step file | Note |
|---|---|---|
| 01 | step-01-parse-input.md | unchanged |
| 02 | step-02-load-brd-context.md | moved up from previous position 03 |
| 03 | step-03-load-project-context.md | moved up from previous position 04; gains `docs/patterns/domains/INDEX.md` in its always-load list so step 04 can read the registry without an on-demand load |
| 04 | step-04-resolve-open-questions.md | this step — moved down from previous position 02 |
| 05–16 | unchanged | feature-shape detection through finalize stay where they are |

**Fires for.** The step always runs (for all modes). Only the **open-questions sub-phase** (Phase 2, Part A) is Mode-1-gated — because open questions live in a feature brief, which exists only in Mode 1. The rest of the step — slot-filling detection, domain awareness, the builder sub-flow, the "anything else?" prompt, and classification of additions — runs for all modes.

This is a correction from the original design, which gated the entire step on Mode 1. That gate silently bypassed domain awareness for any slot-filling feature submitted via Mode 2/3/4 (direct prompt / single file / multi-file) — the exact failure mode the domain-awareness layer is designed to prevent. Mode-2 PRDs and Mode-3 file-pastes are common ad-hoc paths; leakage there is exactly what the registry is supposed to guard against.

**Phases.**

### Phase 1 — derive draft (no user interaction)

The skill reads the feature brief end-to-end (already loaded in step 01) and derives:

- The list of open questions from the `## Edge cases / open questions` section (verbatim).
- A first-pass draft of slot fillers if the feature is slot-filling (see change 4 for the trigger).
- If the feature is builder-shaped: a draft section list, lock flags, and per-section internal structure (entities, fields, optional sub-entities) — derived from FRs, business rules, and user journey lines in the brief.

Items the skill could not derive from the brief content are marked internally as "needs input" — they become explicit questions in Phase 2.

### Phase 2 — present draft and gather user input

Single presentation with three parts:

**Part A — open questions from the brief.** Each open question is presented in order. For each:
- If the brief includes context (e.g., "ADR-07 must be resolved"), include it.
- User answers, or defers with "TBD" / "ask offline".
- Resolved answers are recorded for inlining into the spec by step 13 (renumbered).
- Deferred items carry forward to become `NEEDS_CLARIFICATION` markers in the appropriate spec section.

**Part B — draft review (builder sub-flow, when applicable).** The skill presents its draft plainly using the simplified pattern from change 8:

```
## Proposed Course Builder structure

Sections:
  1. basics       locked
  2. lessons      locked
       Lesson:
         - title
         - content_type (video / document / text)
         - content_data
         - inline_quiz (optional, per FR-3)
            Quiz:
              - questions, options, correct_answer
              - scoring rule                              ← need your input
  3. audience     customizable
  4. approval     locked

Items that need your input:
  • Quiz scoring rule — pass-threshold? per-question points? Other?
  • Content type list — is video/document/text the right set?

Anything in the draft that's missing or wrong?
```

The user confirms, edits, or pushes back. Items in the "need your input" list become focused sub-prompts. The general "anything missing or wrong?" gives the user the floor to challenge the draft as a whole.

**Part C — anything else?** Final prompt:
> "Anything else to add or change beyond what's captured in the feature brief?"

User adds free-text items. Each net-new item gets **classified into the appropriate spec section** (FRs, NFRs, Business Rules, Domain Concepts, Domain Events, Permissions, Data Retention, Caching Strategy, Observability, Edge Cases, Out of Scope, etc.) based on its nature. The classification is performed by the skill and shown to the user for confirmation.

### Outputs to downstream steps

- Resolved-question decisions → folded into the spec's "Inherited from feature brief" or appropriate sections by step 13 (generate-spec-content).
- Builder structure (when present) → recorded as a structured object: `{sections: [{key, lock, sub_entities, fields}]}`. Step 13 inlines this into spec.md's Domain Entities / Builder Configuration sections.
- Net-new "anything else?" additions → mapped to `{section_name → list_of_items}` for inlining.
- Deferred items → list of `(question, target_section)` for `NEEDS_CLARIFICATION` marker placement.
- Domain detection state (see change 4) → carried forward.

## 4. Change 2 — interactive ambiguity model

**What changes.**

- [step-13-generate-spec-content.md](.claude/skills/create-spec/steps/step-13-generate-spec-content.md) — remove the "at most 3 NEEDS_CLARIFICATION markers" rule.
- [step-15-write-plan-file.md](.claude/skills/create-spec/steps/step-15-write-plan-file.md) — remove the matching "at most 3 items" line.
- Both steps adopt: *when an ambiguity is identified during section generation, raise it interactively; if the user can answer, fold the answer into the section being generated; only items the user explicitly defers become `NEEDS_CLARIFICATION` markers in the spec.*

**Why interactive, not batched.**

Batching ambiguities until the end of generation means the spec has to be partially regenerated when answers flip earlier decisions. Resolving when found keeps generation linear and avoids re-derivation work.

**No cap rationale.** The cap discouraged genuine ambiguity surfacing. The interactive model addresses the original concern (skill punting on decisions it could make itself) by making each ambiguity a real conversation rather than an absorbed marker — punting now requires the user to confirm "yes, I can't answer that either."

## 5. Change 3 — Claude-only execution time

**What changes.**

- [step-01-parse-input.md:18-20](.claude/skills/create-spec/steps/step-01-parse-input.md) and skill-level initialization — in addition to `/tmp/create_spec_start`, initialize `/tmp/create_spec_wait` to `0` (milliseconds).
- At every user-gate (the FR/NFR confirmation in step 01; the three sub-gates in step 04; the interactive clarifications in step 13; the commit/push prompt in step 16): bracket with timestamps.
  - **Before the prompt:** `date +%s%3N > /tmp/create_spec_wait_started`
  - **First action on resume:** `echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait`
- [step-16-branch-write-review-finalize.md](.claude/skills/create-spec/steps/step-16-branch-write-review-finalize.md) — replace the wall-time print with:
  ```bash
  wall=$(( $(date +%s) - $(cat /tmp/create_spec_start) ))
  wait_s=$(( $(cat /tmp/create_spec_wait) / 1000 ))
  exec_s=$(( wall - wait_s ))
  echo "create-spec execution time: ${exec_s}s (wall=${wall}s, human-wait=${wait_s}s)"
  ```

**Caveat.** This excludes time the user spent answering prompts. It still includes Claude's own per-turn thinking time, which is the right inclusion for "what did Claude actually cost to run this skill."

**Failure mode.** If the model forgets to write the wait-accumulation bash on resume, the tally drifts low. Mitigation: each user-gate's step file ends with explicit routing instruction `"On resume, FIRST run the wait-accumulation command above."` This is a reliability-by-instruction approach; if drift becomes a real problem, the bracketing can be moved into a hook later.

## 6. Change 4 — domain awareness

### 6.1 Trigger

Domain detection fires only when the feature is **slot-filling**. A feature is slot-filling if its brief introduces or modifies any of:
- A builder (multi-section wizard producing a configurable entity).
- An audience model.
- An eligibility decision.
- A budget concept.
- An approval workflow.
- A completion semantic.

Features that don't touch any slot (e.g., "add CRM sync to deal-registration", "improve the deal-list pagination") skip domain detection entirely. Step 04 still runs for them — to resolve open questions (Mode 1) and gather additions (all modes) — but the builder sub-flow and the domain-detection prompt are bypassed.

### 6.2 Detection flow

When slot-filling is detected (during Phase 1 of step 04):

```
"This feature is filling builder-shaped slots. Which domain does it belong to?
  • incentive (existing legacy bespoke domain — uses IncentiveAudienceRule etc.)
  • enablement (new domain — anchored on platform primitives)
  • A new domain not listed above"
```

- **incentive selected:** the skill loads `docs/patterns/domains/incentive.md` and uses its slot fillers for drift detection.
- **enablement selected:** the skill loads `docs/patterns/domains/enablement.md` (creating it interactively if this is the first enablement feature) layered on `platform-primitives.md`.
- **new domain selected:** the skill enters a bootstrap flow that interactively authors a new `{domain}.md` file with the user, anchored on `platform-primitives.md`, committed alongside the spec.

### 6.3 Slot list (canonical, 8 slots)

| # | Slot | Definition |
|---|---|---|
| 1 | Core aggregate | Root entity for the domain (e.g., `Incentive`, `Course`). |
| 2 | Audience-rule entity | Entity capturing who is in the audience. |
| 3 | Eligibility engine contract | Interface name + signature for eligibility decisions. Not the implementation logic. |
| 4 | Completion/participation entity | Entity recording user completion. Not the completion *rule* — that lives in spec.md. |
| 5 | Budget model | Budget entity, or explicit "not applicable". |
| 6 | Approval workflow entity | Approval entity, or explicit "not applicable". |
| 7 | Builder discriminator | Column or columns identifying which builder variant (e.g., `incentive_type`, or `(builder_type, domain)`). |
| 8 | Section/field storage entity | Entity persisting builder section/field config (e.g., `BuilderSectionConfig`, `BuilderSectionDefinition`). |

**Plus one supplementary table per domain file:** ordered `section_keys` per `builder_type`. Not a formal slot — does not trigger drift detection. Sections vary all the time; slots vary rarely.

A **primitive** is the concrete filler that occupies a slot. Primitives are **structural elements only** — entity name, interface name, table column, topic name, service class name. They are **never business rules**. Business rules belong in `spec.md`.

### 6.4 Drift detection

When a spec is generated for a slot-filling feature:

1. The skill identifies the domain (from user selection in 6.2).
2. The skill resolves slot fillers from the resolution order: per-builder-type override file > domain file > platform-primitives.
3. For each slot the spec fills, compare against the registry filler.
4. If the spec fills a slot with a *different* value than the registry, prompt the user interactively:
   > "This spec fills slot 'Audience-rule entity' with `EnablementAudienceRule`. The enablement registry currently lists `AudienceRule` (from platform). Add `EnablementAudienceRule` as a domain-specific override / replace the existing filler / mark as deviation for this feature only?"
5. The skill's response writes the registry change to the same plan file alongside the spec, so registry edits ship in the same commit as the spec.

Per-spec slot-filler differences are surfaced; non-slot differences (helper services, DTOs, repositories) are not.

### 6.5 Slot additions are governance events

Adding a new slot to the canonical list is a **registry-wide change** affecting INDEX.md, platform-primitives.md, and every active domain file. `/create-spec` MUST NOT silently propose a slot addition mid-feature. If the skill identifies that the current slot list is insufficient for the feature being specced, it surfaces a `NEEDS_GOVERNANCE_DECISION` marker and stops; the user resolves out-of-band before re-running.

### 6.6 Bottom-up primitive promotion

Entities like `Lesson`, which start as feature-internal in the spec for the first Course feature, are not registry primitives initially. If a second feature in the same domain (e.g., LearningPath) also uses `Lesson`, drift detection in `/create-spec` for that second feature notices the reuse and prompts:
> "`Lesson` is now referenced by Course and LearningPath. Promote to an enablement-domain shared primitive?"

If yes, `enablement.md` gains a "Shared sub-entities" row. Registry growth is driven by observed reuse, not speculative design.

## 7. Change 5 — domain registry

### 7.1 Files

```
docs/patterns/domains/
  INDEX.md                  ← canonical slot list, governance, drift policy
  platform-primitives.md    ← shared infra layer (new; for enablement and future domains)
  incentive.md              ← legacy bespoke (existing code, untouched)
  enablement.md             ← authored on first enablement feature
  enablement/               ← only if a builder_type overrides a domain-level slot
    learning-path.md        ← e.g., introduces LearningPathBudget where enablement says "not applicable"
```

Per-builder-type override files are exceptions, not the default. Course and Certification, if they fully inherit `enablement.md` defaults, do not get their own files.

### 7.2 File size discipline

Each file must be readable in under five minutes. Target: ≤ 1 page. If a domain file grows past one page, it's eating its margin — either it's accumulated business rules (which belong in specs) or the slot list itself has grown too detailed.

### 7.3 INDEX.md skeleton

```markdown
# Domain Registry

Source of truth for domain-defining primitives. Read by /create-spec,
/create-stories, /review-spec (blueprint); /load-spec, /load-story,
/execute-foundation (backend); /load-spec, /load-story, /create-mockups
(frontend); /generate-contracts (contracts); bug-fixer (blueprint).

## Domains
| File | Status | Anchored on |
|---|---|---|
| [platform-primitives.md] | in-design | — |
| [incentive.md]           | active-legacy | own bespoke stack |
| [enablement.md]          | planned (authored on first enablement feature) | platform-primitives |

## Slot list (canonical, 8 slots)
1. Core aggregate
2. Audience-rule entity
3. Eligibility engine contract
4. Completion/participation entity
5. Budget model (or "not applicable")
6. Approval workflow entity (or "not applicable")
7. Builder discriminator
8. Section/field storage entity

## Drift policy
/create-spec and /create-stories interactively prompt when a spec or story
fills a slot with a value different from the registry. Slot additions are
governance events — not proposed mid-feature.

## Governance
Migration trigger: when a third domain (second non-incentive) lands on
platform primitives, an incentive-migration audit is scheduled before the
fourth domain ships. The engineer landing the third domain runs the audit
as a merge prerequisite.
```

### 7.4 platform-primitives.md skeleton

```markdown
---
layer: platform-infra
status: in-design
authored: 2026-05-12
---

# Platform Builder Primitives

Shared infrastructure for builder-shaped domains. New domains anchor here.
Incentive does NOT use this layer — see incentive.md.

## Package roots
- Backend:   com.tenxengage.app.platform.builder.*
- Frontend:  src/platform/builder/*
- Contracts: tenxengage-contracts/platform/builder/

## Naming convention
Platform code uses "Definition" suffix where legacy incentive uses "Config".
This prevents visual collision between the two layers in mixed code reads.
- BuilderDefinition / BuilderSectionDefinition / BuilderFieldDefinition
- AudienceRule / EligibilityChecker (interface)

## Slot fillers provided by platform
| Slot | Filler | Location |
|---|---|---|
| Audience-rule entity      | AudienceRule (polymorphic via owner_type, owner_id) | platform.builder.AudienceRule |
| Eligibility engine contract | EligibilityChecker (interface) | platform.builder.EligibilityChecker |
| Builder discriminator     | (builder_type, domain) columns | on BuilderDefinition |
| Section/field storage     | BuilderSectionDefinition / BuilderFieldDefinition | platform.builder.* |

## Slots domains must declare themselves
- Core aggregate
- Completion/participation entity
- Budget model (or "not applicable")
- Approval workflow entity (or "not applicable")
```

### 7.5 incentive.md skeleton (excerpt — for full table, see prior conversation)

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
| Core aggregate              | Incentive                            | entity/Incentive.java |
| Audience-rule entity        | IncentiveAudienceRule                | entity/IncentiveAudienceRule.java |
| Eligibility engine contract | ParticipantEligibilityChecker.matchesUserEligibility(Incentive, …) | service/ParticipantEligibilityChecker.java:91 |
| Completion entity           | UserIncentiveCompletion              | entity/UserIncentiveCompletion.java |
| Budget model                | IncentiveBudget                      | entity/IncentiveBudget.java |
| Approval workflow           | IncentiveApprover                    | entity/IncentiveApprover.java |
| Builder discriminator       | incentive_type column                | on BuilderSectionConfig |
| Section/field storage       | BuilderSectionConfig / BuilderFieldConfig | entity/Builder*Config.java |

## Section keys per incentive_type
| incentive_type | section_keys (ordered)                                              | lock summary |
|----------------|---------------------------------------------------------------------|--------------|
| SALES          | basics, schedule, audience, budget, criteria, approval              | all locked except audience |
| TRAINING       | basics, schedule, audience, budget, criteria (Training Courses), approval | locked except audience and criteria |
| ACTIVITY       | basics, schedule, audience, budget, criteria (Activity Setup), approval | locked except audience and criteria |
| JOURNEY        | basics, schedule, audience, budget, criteria (Journey Stages), approval | all locked except audience |

## Domain conventions (prose, not slot-filled)
- Eligibility is lazy / company-mediated. No incentive_participant join table.
  Computed at query time from the user's current PartnerCompany.locationAssignments
  and ClientRole.
- No auto-tagging on user onboarding. No company-switch handler. KNOWN GAPS.
- Completion event topic: completion-events (Kafka).
```

### 7.6 enablement.md (authored on demand)

Not pre-authored. The first enablement feature through `/create-spec` triggers the bootstrap flow that creates this file interactively with the user, anchored on `platform-primitives.md`.

## 8. Change 6 — parallel-rails migration strategy

**Principle.** Incentive code stays exactly as it is — including `BuilderSectionConfig`, `IncentiveAudienceRule`, `ParticipantEligibilityChecker`, `IncentiveBudget`, `BuilderConfigController`, `BuilderConfigTab.tsx`, `BuilderAccordion.tsx`. None of these are touched by this design or the resulting implementation.

Platform primitives are a **new layer** of code under `com.tenxengage.app.platform.builder.*` (backend) and `src/platform/builder/*` (frontend). The first enablement feature lands on this new layer.

**Migration trigger.** When a third domain (second non-incentive domain) lands on platform primitives, the next merge prerequisite is an incentive-migration audit. The engineer landing the third domain runs the audit and produces a migration plan. Migration itself is a scheduled project, not implicit drift.

**Weaker fallback if the trigger is ever softened.** Annual review of `incentive.md` and `platform-primitives.md` divergence, owned by the platform architect role.

## 9. Change 7 — cross-repo skill updates

Each sibling-repo skill update is small (a read step). The footprint:

### blueprint
- `/create-spec` — main subject (changes 1, 2, 3, 4, 8).
- `/create-stories` — reads `spec.domain` from frontmatter; propagates to story contracts and tasks; validates story-level type/service/repository names conform to the domain registry.
- `/review-spec` — adds one check: if the feature is slot-filling, `spec.domain` frontmatter must be present and slot fillers must match the registry (or carry an explicit deviation note).
- `bug-fixer` — reads `spec.domain` when fixing a bug in a feature so the fix doesn't introduce cross-domain leakage.

### tenxengage-backend
- `/load-spec` — reads `../tenxengage-blueprint/docs/patterns/domains/INDEX.md` and the relevant `{domain}.md`; loads slot fillers into implementation context.
- `/load-story` — same load behavior; type/service naming follows domain primitives.
- `/execute-foundation` — same; foundation tasks for slot-filling features must use registry primitives.

### tenxengage-frontend
- `/load-spec` — same as backend.
- `/load-story` — same.
- **`/create-mockups`** — reads `section_keys` + lock flags from the domain file; renders sections in the correct order; marks locked sections distinctly. Without this read, mockups silently drift from the structural primitives.

### tenxengage-contracts
- `/generate-contracts` — reads `spec.domain` and uses domain-appropriate type naming (e.g., `AudienceRule` for enablement, `IncentiveAudienceRule` for incentive contracts).

### tenxengage-admin-backend / admin-frontend
- Same `/load-spec` / `/load-story` updates if those skills exist in those repos. To be confirmed during implementation.

### CLAUDE.md updates per sibling repo
Each sibling repo's `CLAUDE.md` adds a one-line pointer:
> Domain registry (load-bearing for all builder-shaped feature work) lives at `../tenxengage-blueprint/docs/patterns/domains/`.

## 10. Change 8 — draft-and-present interaction

**Principle.** The skill commits to a draft. Items it needed to interpret are shown plainly with a small inline citation only when that citation helps the user judge. Items it couldn't decide are listed once, at the bottom, as specific questions. A general "anything missing or wrong?" prompt gives the user the floor to challenge the draft as a whole.

**What this replaces.** An earlier draft of this design proposed a DERIVED/INFERRED/ASSUMED tagging system on every line of every draft, with mandatory source citations on each item. That pattern was rejected as overengineering — it adds noise to every line and depends on users carefully reading meta-tags, which fragile under skim-review.

**Where the pattern applies inside `/create-spec`.**
- Step 01 (parse-input) — FR/NFR confirmation already presents a draft; the change is to consistently list items needing input separately and ask "anything missing or wrong?" at the end.
- Step 04 (open-questions + additions + builder sub-flow) — primary application.
- Step 13 / step 14 — when ambiguities surface during spec / technical generation, they appear as focused questions rather than tagged markers.

**Example presentation.** See section 3 (step 04, Phase 2, Part B) for the Course builder example.

## 11. Step file changes summary

Final desired state of step numbering after all changes in this design (including the corrected ordering from the amendment):

| Position | Step file | Change vs. baseline `/create-spec` |
|---|---|---|
| 01 | step-01-parse-input.md | Use draft-and-present pattern (change 8); initialize `/tmp/create_spec_wait`; bracket the FR/NFR confirmation gate (change 3) |
| 02 | step-02-load-brd-context.md | Moved up from position 03. Content unchanged. |
| 03 | step-03-load-project-context.md | Moved up from position 04. **Add `docs/patterns/domains/INDEX.md` to the always-load list** so step 04 can read the registry without an on-demand load. |
| 04 | step-04-resolve-open-questions.md (**NEW**) | Phases 1+2 as described in section 3. The step always runs; only Phase 2 Part A (open-questions sub-phase) is Mode-1-gated. Slot-filling detection, domain awareness, builder sub-flow, "anything else?", classification all run for all modes. Bracket the consolidated user-gate for timing (change 3). |
| 05 | step-05-detect-feature-shape.md | Unchanged. |
| 06 | step-06-load-shape-references.md | Unchanged. |
| 07 | step-07-scope-decomposition.md | Unchanged. |
| 08 | step-08-security-analysis.md | Unchanged. |
| 09 | step-09-events-analysis.md | Unchanged. |
| 10 | step-10-test-strategy.md | Unchanged. |
| 11 | step-11-permissions-analysis.md | Unchanged. |
| 12 | step-12-derive-slug.md | Unchanged. |
| 13 | step-13-generate-spec-content.md | Remove "at most 3 NEEDS_CLARIFICATION" rule (change 2); interactive ambiguity resolution; bracket any new user-gates created by ambiguity prompts |
| 14 | step-14-generate-technical-content.md | Same change-2 application |
| 15 | step-15-write-plan-file.md | Remove "at most 3 items" line; include domain-registry changes (if any) in the same plan file |
| 16 | step-16-branch-write-review-finalize.md | Replace wall-time print with execution-time print (change 3); bracket commit/push prompt |

**Routing pointers to update** (one-line edits at the boundary of each step):

- step-01 → routes to step-02-load-brd-context.md (was step-02-resolve-open-questions.md)
- step-02 (load-brd-context) → routes to step-03-load-project-context.md (was step-04-...)
- step-03 (load-project-context) → routes to step-04-resolve-open-questions.md (was step-05-...)
- step-04 (resolve-open-questions) → routes to step-05-detect-feature-shape.md (was step-03-load-brd-context.md)

## 12. Spec frontmatter additions

Every spec produced for a slot-filling feature now includes:

```yaml
---
domain: incentive | enablement | {new-domain}
builder_type: SALES | TRAINING | COURSE | LEARNING_PATH | ... | null
---
```

`domain` is required when slot-filling; absent otherwise. `builder_type` is required only when the feature is builder-shaped (the more specific case within slot-filling).

`/review-spec` validates these fields are present and consistent with the registry.

## 13. Non-goals

- **No code refactoring of incentive.** This design does not change `BuilderSectionConfig`, `BuilderFieldConfig`, `IncentiveAudienceRule`, `ParticipantEligibilityChecker`, `IncentiveBudget`, `BuilderConfigController`, `BuilderConfigTab.tsx`, or `BuilderAccordion.tsx`. Incentive's "no auto-tagging" and "no company-switch handler" gaps are recorded in `incentive.md` as KNOWN GAPS; closing them is out of scope here.
- **No platform-primitives implementation.** This design specifies the platform-primitives *layer* and its naming convention, but the actual `BuilderDefinition` / `AudienceRule` / `EligibilityChecker` Java entities are built by the first enablement feature's spec → implementation flow, not by this design.
- **No automation of registry update from the codebase.** The registry is human-authored, drift-checked by skills at speccing/story time. There's no static-analysis job that scans the codebase to keep the registry in sync; that level of automation can come later if drift becomes a practical problem.
- **No new slash command.** All changes land inside existing skills.

## 14. Open items

These were either deferred during design discussion or genuinely TBD and would resolve during implementation:

1. **Admin-backend / admin-frontend skill updates** — whether those repos have `/load-spec` / `/load-story` skills with the same shape needs to be confirmed during implementation. If yes, same updates apply. If no, no action.
2. **`EligibilityRule.java` and `EligibilityRuleGroup.java` classification** — these unprefixed entities exist in the incentive package. They sit alongside `IncentiveAudienceRule` and are likely incentive-bound despite the unprefixed name. To be confirmed when authoring `incentive.md`'s final form; if they're genuinely domain-neutral, they may be a quiet seed of platform primitives that platform-primitives.md could adopt. If they're incentive-internal, `incentive.md` notes them under domain conventions.
3. **`/tmp/create_spec_wait` reliability** — the "instruct each step file to run wait-accumulation on resume" approach depends on model adherence. If drift becomes a real measurement problem in practice, the bracketing moves into a hook.

## 15. Acceptance criteria

The implementation is considered complete when:

1. A `/create-spec` run on a Mode-1 feature brief surfaces every item from the brief's `## Edge cases / open questions` section as a user-gate (change 1).
2. A `/create-spec` run on a builder-shaped feature presents a Phase-2 Part B draft of the builder structure with section-level locked/customizable flags, sub-entity structure (where derivable from the brief), and a specific "items that need your input" list (changes 1 + 8).
3. A `/create-spec` run that surfaces ambiguities during step 13 (renumbered) does so interactively, with no cap on count, and only deferred items appear as `NEEDS_CLARIFICATION` markers in the final spec (change 2).
4. A `/create-spec` run prints execution time excluding human-wait at completion (change 3).
5. A `/create-spec` run on a slot-filling feature writes `domain:` to the spec frontmatter and, on slot-filler difference from the registry, prompts the user before continuing (change 4).
6. `docs/patterns/domains/INDEX.md`, `platform-primitives.md`, and `incentive.md` exist and pass a one-page-each readability check (change 5).
7. Sibling-repo `/load-spec`, `/load-story`, `/create-mockups`, `/generate-contracts`, `/create-stories`, `/review-spec`, and `bug-fixer` skills read the domain registry alongside their existing inputs (change 7).
8. The migration-trigger governance text appears in `INDEX.md` and is referenced by sibling-repo `CLAUDE.md` files (change 6 + 7).
9. A `/create-spec` run on a Mode-2 / Mode-3 / Mode-4 input that describes a slot-filling feature triggers domain detection (proves the mode-gate is correctly scoped to the open-questions sub-phase only). A run on a Mode-2 input that describes a non-slot-filling feature produces no domain prompts (proves slot-filling detection is sound). The resolve-open-questions step runs at position 04 (after both context loaders), and `docs/patterns/domains/INDEX.md` is in load-project-context's always-load list (change 1, corrected ordering).

## 16. Implementation order

A high-level sequence for the writing-plans skill to refine:

1. Create the registry skeleton: `INDEX.md`, `platform-primitives.md`, and `incentive.md` (bootstrap content derived from this design). Commit.
2. Update sibling-repo CLAUDE.md files with the pointer line.
3. Implement change 3 (Claude-only timing) — small, contained, low-risk.
4. Implement change 2 (interactive ambiguity model) — text changes in step 13/14.
5. Implement change 1 + change 8 (new step 04, draft-and-present pattern) — biggest single piece. Includes moving step-02 to position 04 and renumbering load-brd-context / load-project-context up to positions 02 and 03.
6. Implement change 4 (domain awareness inside step 04) — depends on registry skeleton from step 1, and on `docs/patterns/domains/INDEX.md` being added to load-project-context's always-load list as part of step 5.
7. Update sibling-repo skills (change 7) — independent of order, can parallelize.
8. Update `/create-stories`, `/review-spec`, `bug-fixer` in blueprint to read registry — final piece.

Each step is independently testable on a real feature spec run.
