---
name: "load-story"
description: "Implement the FE half of one user story (US-NN) for a story-sliced feature: claim the tracker FE cell, create a local sub-branch, implement the story's FE tasks + Playwright E2E, run tests against real BE, pause for developer approval, squash-merge locally, flip tracker to done. If BE is not yet done, scaffolds against contracts + mocks and stays in-progress. For story-sliced features only."
argument-hint: "feature-slug + story ID (e.g., `rate-course US-01`); optional flags: --tdd, --gate=<every|story|ready-check|feature-end>, --phase=<implement|merge|revise>, --instruction=<text>, --soft-stages=<csv>"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

Expected forms:
- `rate-course US-01`

> **Naming aliases:** Throughout this skill, `{feature-id}`, `{feature-slug}`, and `$FEATURE_SLUG` refer to the same value — the slug of the feature being implemented (e.g., `course-authoring`). Similarly `{US-NN}` and `$STORY_ID` are aliases for the story identifier (e.g., `US-01`).

**Flags:** Parse the following flags out of `$ARGUMENTS` before normalizing feature-id + story-id. Strip each flag (and its value) from `$ARGUMENTS` after parsing.

- `--tdd` → set `$USE_TDD = true` (default: `false`).
  - When `true`: the Red/Green/Refactor cycle and the four superpowers skills referenced in Steps 6/7/10 are active for this session.
  - When `false`: do NOT follow Red/Green/Refactor. Implement per the story's task order. No other active superpowers skill implies TDD discipline — `--tdd` is the sole switch.
- `--gate=<every|story|ready-check|feature-end>` → set `$GATE` (default: `every`).
  - `every` — pause for developer approval before merge.
  - `story` / `ready-check` / `feature-end` — auto-merge on green ready-check (no chat pause).
- `--phase=<implement|merge|revise>` → set `$PHASE` (default: unset, meaning both phases inlined).
  - `implement` — run through Step 10 but STOP before merge; emit structured return summary. Exit cleanly.
  - `merge` — assume Step 10 already produced a green ready sub-branch; jump to Step 10.5.
  - `revise` — apply `--instruction=<text>` change to the sub-branch, re-run tests, return to phase `implement` return shape.
- `--instruction=<text>` → revision text used when `$PHASE=revise`. Quoted text after `=`.
- `--soft-stages=<comma-list>` → passed through to `ready-check` in Step 9.5 (default: empty).

When `$GATE != every`, the skill must NOT prompt for chat approval in Step 10; instead it auto-merges on green ready-check. When `$GATE == every` (default), behavior is unchanged.

**Wall-clock start:** Run `date +%s` and store the result as `$SKILL_START_EPOCH`. Captured once, here, before any other work begins.

Normalize to feature-slug + story-id. If only a story-id is given, infer the slug from the current branch (`features/<slug>`). If inference fails, error: "No feature selected. Pass a slug as argument (e.g. `/load-story quiz-engine US-01`) or run from a `features/<slug>` branch."

**Legacy check:** Read YAML frontmatter of `../tenxengage-blueprint/features/{feature-slug}/spec.md`. If `format: single-file`, abort — `/load-story` is for story-sliced features only. Direct the user to `/load-spec` for single-file features.

**Determine roadmap base branch:** Read YAML frontmatter of `../tenxengage-blueprint/features/{feature-slug}/spec.md` — extract `roadmap` as `$ROADMAP_SLUG` (null if absent).
- If `$ROADMAP_SLUG` is non-empty/non-null: `BASE_BRANCH="roadmaps/{ROADMAP_SLUG}"`
- Otherwise: `BASE_BRANCH="${FEATURE_BASE_BRANCH:-main}"`

---

## Purpose

Implement the **FE half** of one user story end-to-end:
- Claim the `FE` cell of the story row in `tracker.md`
- Create a **local-only** sub-branch off the feature branch
- Implement the `## FE tasks [FE]` + `## E2E test [FE]` sections of `stories/US-NN-*.md`
- Run Vitest (scoped, full) + Playwright against a real BE
- **Pause for developer approval** (mandatory when `$GATE == every`; auto-merges on green when orchestrator-driven with `$GATE != every`)
- On approval: local `git merge --squash` into the feature branch, push
- Flip the FE cell → `done`, stamp the squash-merge commit SHA in `Commit (FE)`

**Scaffold-and-wait model:** if the BE cell for this story is not yet `done`, complete FE scaffolding + Vitest, then stop without merging. The sub-branch stays local. Re-invoke this skill after the BE is done to run Playwright against real BE and finish the merge.

**There is no GitHub PR for this sub-branch.** The only GitHub PR per feature is the final `features/{feature-slug}` → `roadmaps/{slug}` PR (for BRD-derived features) or `features/{feature-slug}` → `main` PR (for standalone features), opened manually when the tracker is all-green. The roadmap branch itself is merged to `main` when all features in the roadmap are done.

---

## Time accounting

See `references/time-accounting.md` for the full convention.

Variables set at claim point (Step 3): `CLAIM_TIME_EPOCH`, `HUMAN_PAUSE_TOTAL_SECS=0`.
Pause-wrap every post-claim interactive prompt (Step 10 approval pause is the main one).
At done flip (Step 13): `ACTIVE_SECS=$(( NOW_EPOCH - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))`.

---

## Rules

See `references/rules.md`.

---

## Phase routing

If `$PHASE` is set, route directly:
- `$PHASE == merge` → read `steps/step-10.5-rc-report-commit.md` (assumes sub-branch exists and ready-check has run; then proceed to step-11-squash-merge.md).
- `$PHASE == revise` → read `steps/step-04.5-dispatch-subagent.md` (with `$INSTRUCTION` already parsed).
- `$PHASE == implement` (or unset) → read `steps/step-01-pre-flight.md`.

If `$PHASE` is unset, the skill runs end-to-end. Manual invocation never sets `$PHASE`.

## Begin

Read `steps/step-01-pre-flight.md`.
