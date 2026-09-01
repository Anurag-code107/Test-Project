---
name: "load-story"
description: "Implement the FE half of one user story (US-NN) for a story-sliced feature: claim the tracker FE cell, create a local sub-branch, implement the story's FE tasks + Playwright E2E, run tests against real BE, pause for developer approval, squash-merge locally, flip tracker to done. If BE is not yet done, scaffolds against contracts + mocks and stays in-progress. For story-sliced features only."
argument-hint: "feature-slug + story ID (e.g., `rate-course US-01`); optional: --tdd"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

Expected forms:
- `rate-course US-01`

**Flag:** if `--tdd` appears anywhere in `$ARGUMENTS`, set `$USE_TDD = true` and strip it before normalizing feature-id + story-id. Otherwise `$USE_TDD = false`.
- When `$USE_TDD = true`: the Red/Green/Refactor cycle and the four superpowers skills referenced in Steps 6/7/10 are active for this session.
- When `$USE_TDD = false`: do NOT follow Red/Green/Refactor. Implement per the story's task order. No other active superpowers skill implies TDD discipline — `--tdd` is the sole switch.

**Wall-clock start:** Run `date +%s` and store the result as `$SKILL_START_EPOCH`. This is the wall-time origin for the final report — captured once, here, before any other work begins.

Normalize to feature-slug + story-id. If only a story-id is given, infer the slug from the current branch (`features/<slug>`). If inference fails (not on a `features/*` branch and no slug in arguments), error: "No feature selected. Pass a slug as argument (e.g. `/load-story quiz-engine US-01`) or run from a `features/<slug>` branch."

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
- **Pause for developer approval** (mandatory)
- On approval: local `git merge --squash` into the feature branch, push
- Flip the FE cell → `done`, stamp the squash-merge commit SHA in `Commit (FE)`

**Scaffold-and-wait model:** if the BE cell for this story is not yet `done`, complete FE scaffolding + Vitest, then stop without merging. The sub-branch stays local. Re-invoke this skill after the BE is done to run Playwright against real BE and finish the merge.

**There is no GitHub PR for this sub-branch.** The only GitHub PR per feature is the final `features/{feature-slug}` → `roadmaps/{slug}` PR (for BRD-derived features) or `features/{feature-slug}` → `main` PR (for standalone features), opened manually when the tracker is all-green. The roadmap branch itself is merged to `main` when all features in the roadmap are done.

---

## Steps

### 1. Pre-flight — feature branch

1. `git branch --show-current` → if on `features/{feature-id}`, skip to 1c
1a. If `$ROADMAP_SLUG` is non-empty, ensure `roadmaps/{ROADMAP_SLUG}` exists locally. Run this **single compound command verbatim** — do not split it:
    ```bash
    git checkout roadmaps/{ROADMAP_SLUG} 2>/dev/null || git fetch origin roadmaps/{ROADMAP_SLUG}:roadmaps/{ROADMAP_SLUG} 2>/dev/null && git checkout roadmaps/{ROADMAP_SLUG} || _b="${FEATURE_BASE_BRANCH:-main}" && echo "Creating roadmap branch from: $_b" && git checkout -b roadmaps/{ROADMAP_SLUG} "$_b"
    ```
1b. Ensure the feature branch exists locally. Run this **single compound command verbatim** — do not split it:
    ```bash
    git checkout features/{feature-id} 2>/dev/null || git fetch origin features/{feature-id}:features/{feature-id} 2>/dev/null && git checkout features/{feature-id} || echo "Creating branch from: $BASE_BRANCH" && git checkout -b features/{feature-id} "$BASE_BRANCH"
    ```
1c. Sync from remote only if all three guards pass:
    - Guard 1 (remote exists): `git ls-remote --exit-code origin features/{feature-id}` → non-zero exit: skip entirely
    - Guard 2 (no uncommitted changes): `git status --porcelain` → non-empty: skip, print "Skipping remote sync — uncommitted changes present"
    - Guard 3 (no unpushed commits): `git log origin/features/{feature-id}..HEAD --oneline` → non-empty: skip, print "Skipping remote sync — unpushed commits present"
    - All guards pass: `git pull --rebase origin features/{feature-id}`
1d. Sync the contracts submodule to the latest feature-branch files. Four substeps:

    **(i) Init guard.** If the submodule was never initialized (fresh clone), materialize it:
    ```bash
    if [ ! -f contracts/.git ] || ! git -C contracts rev-parse HEAD >/dev/null 2>&1; then
      git submodule update --init contracts || { echo "Contracts submodule failed to initialize. Run 'git submodule update --init contracts' manually and re-run /load-story."; exit 1; }
    fi
    ```

    **(ii) Select the target contracts branch.** Prefer `features/{feature-id}`; fall back to `$BASE_BRANCH` (computed in pre-flight). Records the chosen branch in `$CONTRACTS_BRANCH`:
    ```bash
    if git -C contracts checkout features/{feature-id} 2>/dev/null \
       || (git -C contracts fetch origin && git -C contracts checkout features/{feature-id} 2>/dev/null); then
      CONTRACTS_BRANCH="features/{feature-id}"
    else
      echo "Contracts feature branch not found — using base branch: $BASE_BRANCH"
      git -C contracts checkout "$BASE_BRANCH" 2>/dev/null \
        || (git -C contracts fetch origin && git -C contracts checkout "$BASE_BRANCH") \
        || { echo "Contracts submodule cannot reach any usable branch. Aborting."; exit 1; }
      CONTRACTS_BRANCH="$BASE_BRANCH"
    fi
    ```

    **(iii) Conditional pull.** Pull only when all three guards pass — otherwise print a one-line skip reason and continue (do NOT abort):
    - Guard 1 — remote branch exists: `git -C contracts ls-remote --exit-code origin "$CONTRACTS_BRANCH"` exits 0. If not: print `Skipping contracts pull — remote branch not found`.
    - Guard 2 — no uncommitted changes in the submodule working tree: `git -C contracts status --porcelain` is empty. If not: print `Skipping contracts pull — uncommitted changes in submodule`.
    - Guard 3 — no unpushed commits: `git -C contracts log "origin/$CONTRACTS_BRANCH..HEAD" --oneline` is empty. If not: print `Skipping contracts pull — unpushed commits in submodule`.
    - All pass → `git -C contracts pull --rebase origin "$CONTRACTS_BRANCH"`.

    **(iv) Commit pointer bump on the feature branch.** If the submodule pointer advanced, commit it on the feature branch (no push — later steps push the feature branch). This keeps step 1c's "no uncommitted changes" guard clean on the next invocation, and records what contracts SHA each story consumed in `git log features/{feature-id} -- contracts`:
    ```bash
    if git status --porcelain contracts | grep -q .; then
      CONTRACTS_SHA=$(git -C contracts rev-parse --short=12 HEAD)
      git add contracts
      git commit -m "chore: bump contracts pointer to ${CONTRACTS_SHA} for {US-NN} pre-flight"
    fi
    ```

### 2. Validate story + layer

**Blueprint branch guard (before reading any files):**
a. Check if `../tenxengage-blueprint/features/{feature-id}/` exists — if YES, blueprint is on the right branch; skip to (e).
b. `git -C ../tenxengage-blueprint branch --show-current` → note `{current-branch}`
c. Check for uncommitted changes: `git -C ../tenxengage-blueprint status --porcelain`
   - If changes exist, ask the user:
     ```
     Blueprint repo has uncommitted changes on branch {current-branch}.
     A) Commit them to {current-branch} (supply a commit message)
     B) Stash them
     C) Abort — I'll switch branches manually
     ```
     - On A: commit with the user-supplied message, then proceed to (d)
     - On B: `git -C ../tenxengage-blueprint stash`, then proceed to (d)
     - On C: **abort**
d. Checkout the feature branch:
   - Try local: `git -C ../tenxengage-blueprint checkout features/{feature-id}`
     - If successful: check if behind remote:
       `git -C ../tenxengage-blueprint log HEAD..origin/features/{feature-id} --oneline 2>/dev/null`
       - If behind: ask user: "Blueprint branch is behind origin by N commits. A) Pull with rebase  B) Continue with local version"
         - On A: `git -C ../tenxengage-blueprint pull --rebase origin features/{feature-id}`, then proceed to (e)
         - On B: branch already checked out — proceed to (e)
       - If not behind: proceed to (e)
     - If local branch not found: `git -C ../tenxengage-blueprint fetch origin features/{feature-id}:features/{feature-id} && git -C ../tenxengage-blueprint checkout features/{feature-id}`, then proceed to (e)
     - If remote also not found: **abort** — "Blueprint feature branch not found locally or on origin. Run `/create-spec` from the blueprint repo first."
e. Blueprint repo is now on `features/{feature-id}`. Continue reading story files.

1. Read `../tenxengage-blueprint/features/{feature-id}/stories/US-{NN}-*.md`
2. Parse frontmatter `layers`
3. If `layers` does NOT include `"FE"` → **abort** with: "Story {US-NN} is BE-only (`layers: {value}`). Run this in the backend repo: `cd ../tenxengage-backend && /load-story {feature-id} {US-NN}`."
4. Check if `contracts/endpoints/` contains at least one YAML file for this feature (e.g. glob `contracts/endpoints/{resource}*.yaml`). If not → **abort** with "Contracts not yet generated. Run `cd ../tenxengage-contracts && /generate-contracts {feature-id}` first."
5. Capture: `id`, `title`, `touches_entities`, `depends_on_stories`

### 3. Claim the tracker `FE` cell (blueprint repo)

1. Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current`
   - If on `features/{feature-id}`: continue.
   - If not (edge case — something switched it): `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull — step 2 already resolved the state).
2. Read `features/{feature-id}/tracker.md`
3. Locate the Stories row for `US-{NN}`. **Note the `BE` cell value** — you will need it to decide scaffold-only vs full-E2E at step 8.
4. Validate:
   - `FE` cell = `not-started` OR `in-progress` where the in-progress session matches this one (resume case). If in-progress by a different session → **abort**.
   - `FE` cell is not `N/A`.
   - All foundation tasks in `Depends on` are `done`.
   - All prior stories in `Depends on` have both BE = `done` and FE = `done` (or `N/A`).
5. If fresh start: flip `FE` → `in-progress`. Record the current ISO8601 timestamp as `$FE_CLAIM_TIME` (used for duration at step 13). Write `session={id} started={ISO8601}` into the `Notes` column.
6. `git add … && git commit -m "tracker: {feature-slug} {US-NN} FE → in-progress"`
7. `git push` (retry-on-reject up to 3 times)
8. `cd -` back to frontend

### 4. Create (or reuse) the sub-branch (local only)

- If `work/{feature-slug}-{US-NN}-fe` already exists locally (resume case): `git checkout work/{feature-slug}-{US-NN}-fe && git rebase features/{feature-id}`
- Otherwise: `git checkout -b work/{feature-slug}-{US-NN}-fe`

Do NOT push.

### 5. Read the story FE section + contracts

Read `../tenxengage-blueprint/features/{feature-id}/stories/US-{NN}-*.md`. Extract only:
- `## Description`, `## Depends on`, `## Spec references`
- `## FE tasks [FE]` — the complete section
- `## E2E test [FE]` — the complete section
- `## Execution checklist` — the **FE session** block only

Also read:
- `contracts/endpoints/{resource}*.yaml` and `contracts/models/{model-name}.md` — to derive TypeScript types + endpoint shapes. The relevant resource name(s) are recorded in the spec.md `contract` frontmatter field for this feature.
- The referenced sections from `spec.md` (decisions: page intent, user flows, component intent) and `technical.md` (artifacts: file paths, hook specs) as indicated in the story's `## Spec references`

**Read domain registry** (only if `spec.md` frontmatter has `domain:` non-null):
- Read `../tenxengage-blueprint/docs/patterns/domains/INDEX.md`.
- Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`.
- If `builder_type:` is set and `{domain}/{builder-type}.md` exists, read it too.
- Use these slot fillers for entity names, hook names, and types. Story-level deviations from these fillers must be flagged to the user before writing code.

Read these FE reference files for hook + test pattern consistency (one of each — used for TanStack Query shape, Vitest conventions, Playwright spec shape; NOT for component visual fidelity, which is governed by Step 5c):
- One existing hook in `src/hooks/` using TanStack Query
- One existing component test in `src/components/**/__tests__/*.test.tsx`
- One existing Playwright spec in `e2e/*.spec.ts`

**Contract-change rule:** Do NOT hand-write TypeScript interfaces from scratch or add fields not in the contract. If you need a field that isn't in `../tenxengage-contracts/`, follow the **contract-change ritual** before writing code:
1. Edit the relevant section of `../tenxengage-blueprint/features/{feature-id}/spec.md`
2. Re-run `cd ../tenxengage-contracts && /generate-contracts {feature-id}` (idempotent)
3. Note in the story row's Notes column in `tracker.md`: "contract updated — refetch types"
Then continue from the updated contract.

### 5b. Mockup as verbatim fidelity anchor

Check the story frontmatter `mockup_file` field captured in Step 2:

- **If `mockup_file` is a real file path** (not `null`, `N/A`, or absent): read the file at that path. The mockup is the verbatim visual reference for Step 6 — **not a suggestion, not a starting sketch, the source of truth for visual fidelity**.

  When implementing components in Step 6:
  - **Copy the mockup's JSX structure** for each section the story creates. Preserve the outer container element, child element ordering, and section boundaries.
  - **Copy the mockup's Tailwind classes verbatim** for: outer container, spacing scale (`gap-*`, `space-*`, `p-*`, `m-*`), layout (`flex`, `grid`, `flex-row`, `flex-col`, `justify-*`, `items-*`), hover/transition (`hover:*`, `transition-*`, `duration-*`), and animation (`animate-*`, Framer Motion variants).
  - **Swap only**: hardcoded mock data for real query data from hooks; inline types for imported types from `src/types/`; mock event handlers for real service calls; mock copy for real i18n strings (if applicable, otherwise keep the mockup's copy verbatim — it is the spec).
  - If the mockup's JSX has comments at the top (`// Covers:`, `// Mirrors:`, etc., per the /create-mockups spec), DO NOT carry them into production code. The production component's own provenance comment (Step 6) replaces them.

  Announce: "Mockup found at `{mockup_file}` — adopting its JSX as the visual fidelity anchor."

- **If `mockup_file` is `null`, `N/A`, or absent:** fall back to the production references discovered in Step 5c. Announce only if there are no Mirror matches either (then this is a no-analog implementation — see the Step 6 provenance comment rule).

### 5c. Production reference discovery

Read the Screen Pattern Mirror in `.claude/skills/create-mockups/SKILL.md → Phase 3`. For each component or page this story creates (from `## FE tasks [FE]`), identify the matching Mirror row by screen type and read the referenced production file(s). The Mirror is the single source of truth — never invent screen-to-file mappings ad hoc.

For each FE task:
1. Determine the screen type: list / detail / settings / form / builder-entry-menu / builder-type-selector / builder-template-picker / builder-existing-picker / builder-wizard-body / builder-config-tab / builder-config-standalone / dashboard.
2. Find the matching Mirror row. Read the referenced file(s) in full.
3. If no Mirror row matches: declare "no production analog" for that task (used by the provenance comment in Step 6 and by the final report block in Step 14).

This replaces the existing "one existing hook, one component test, one Playwright spec" arbitrary-sampling rule for production-component discovery. The arbitrary-sampling rule remains useful for hook and test patterns (TanStack Query shape, Vitest conventions, Playwright spec shape) but is no longer the primary fidelity anchor.

### 6. Implement the FE tasks

**TDD discipline.** If `$USE_TDD = true`, invoke `superpowers:test-driven-development` at the start of this step and follow it for every component, hook, or service function. Project-specific glue (the skill's examples are generic):

- Scoped test command for the Red/Green verify steps: `npm run test -- {Component}.test.tsx`
- Tests live in `src/components/{feature}/__tests__/*.test.tsx` (components) or `src/hooks/__tests__/*.test.ts` (hooks).

If `$USE_TDD = false`, do NOT enter a Red/Green/Refactor loop. Write production code and tests in the order the story task block specifies; tests are still required by each task's acceptance criteria, just not test-first.

**Provenance comment (mandatory).** Every new `.tsx` file under `src/components/`, `src/pages/`, or `src/hooks/` that this session creates must begin with a one-line provenance comment as its first content line (before any imports). Format:

- If the component was adapted from a mockup: `// Adapted from: src/mockups/{feature-id}/{ScreenName}.tsx (mockup) + src/components/incentive-builder/EntryMenu.tsx (production analog from Mirror)`
- If from a mockup only (no Mirror match): `// Adapted from: src/mockups/{feature-id}/{ScreenName}.tsx (mockup); no production analog`
- If from a Mirror reference only (no mockup): `// Adapted from: src/components/incentive-builder/EntryMenu.tsx (production analog from Mirror)`
- If neither mockup nor Mirror analog exists: `// Adapted from: none — no production reference`

Hooks created in `src/hooks/` use the same format but reference the hook pattern source (e.g., `// Adapted from: src/hooks/useIncentives.ts (TanStack Query pattern)`).

This is the structural audit trail in production code. `grep -r '// Adapted from:' src/` lists every component's origin; `grep -r '// Adapted from: none' src/` finds every freely-designed component.

**Fidelity rule (production code).** When adapting from a mockup (Step 5b) or a Mirror reference (Step 5c):
- **Copy verbatim:** outer container, spacing scale (`gap-*`, `space-*`, `p-*`, `m-*`), layout classes (`flex`, `grid`, `justify-*`, `items-*`), hover/transition classes (`hover:*`, `transition-*`, `duration-*`), animation classes (`animate-*`, Framer Motion variants), CSS custom property references (HSL tokens like `text-primary`, `bg-card`).
- **Allowed to differ:** inner text content (will be real strings, not mock copy), icon choice (must still be from `lucide-react`), entity-specific labels, real data fields from hooks instead of mock arrays, real type imports.
- **If you're tempted to write a class string that doesn't appear in the mockup or in the Mirror reference, stop and re-read the source.** Do not paraphrase classes. Do not "improve" the production reference. Do not introduce new HSL tokens — only use what's defined in `src/index.css`.

This rule applies whether the source is a mockup file (Step 5b) or a production analog file (Step 5c).

**Builder primitives consumption (non-negotiable).** When implementing a builder feature:

- **Builder wizard screens (matching `Builder — wizard body` Mirror row):**
  - MUST import and use `useBuilderConfig` from `src/hooks/useBuilderConfig.ts` to fetch the section/field config.
  - MUST render wizard fields via `DynamicFieldRenderer` from `src/components/incentive-builder/DynamicFieldRenderer.tsx`.
  - MUST NOT declare wizard step fields inline as TSX. The fields are runtime-driven by the config returned from `useBuilderConfig`.
  - MUST use `BuilderLayout` from `src/components/incentive-builder/BuilderLayout.tsx` as the page shell, with the same 40/60 layout, PageBanner theme (`builder-ai` / `builder-manual`), and BuilderAccordion step structure as the existing incentive builder.

- **Builder Config admin screens (matching `Platform Settings — Builder Config tab` or `Builder Config — standalone page` Mirror row):**
  - MUST use `BuilderConfigTab` / `BuilderConfigSection` / `BuilderFieldEditor` from `src/components/settings/` as the editing primitives. Do not build new section/field editor components from raw inputs.
  - MUST persist section + field config to the existing `BuilderSectionConfig` + `BuilderFieldConfig` tables via the existing admin endpoints — never create parallel state.

- **Builder entry / type selector / template picker / existing-item picker screens** (matching their respective Mirror rows): no architectural primitive to consume (these are pre-builder navigation screens) — fidelity is governed entirely by Step 5b mockup + Step 5c Mirror reference + the Fidelity rule above.

Verify Builder Config feature flag (`module.settings.tenx`) gating where applicable per the existing pattern in `BuilderConfigPage.tsx`.

Iterate the `## FE tasks [FE]` section **in the order written in the story file**. Each task block specifies its own `**Files:**` and acceptance criteria — the story is the source of truth for what tasks exist and what each entails.

Do NOT assume a fixed count. Some stories have two FE tasks, some have five. The numbering (`FE-1`, `FE-2`, …) is a readable label, not a contract — read them as a list, not as slots.

As each task's work lands, check the matching items in the **FE session** block of `## Execution checklist` (expressed by concrete deliverables, e.g. `{Component}.test.tsx Vitest tests pass`, not by task number). Commit checklist updates alongside code.

Typical tasks you'll encounter in a standard page-building story — for orientation only; your actual story drives what gets implemented:
- TypeScript types in `src/types/{feature}.types.ts` + service call in `src/services/{feature}.service.ts`
- TanStack Query hook in `src/hooks/use{Entity}.ts`
- Component(s) + Vitest tests in `src/components/{feature}/**`
- Page wiring + route in `src/pages/{feature}/**` and `src/App.tsx`

### 7. Run frontend tests (scoped + full)

- **Inner loop** (inside Step 6): `npm run test -- {Component}.test.tsx` after each unit of production code. Step 6 drives this — this bullet is reference only. When `$USE_TDD = true`, this is the TDD Green phase; otherwise it's just incremental verification.
- Outer loop: `npm run test` — full Vitest suite, must be green

**If any test is red:** If `$USE_TDD = true`, invoke `superpowers:systematic-debugging` before proposing fixes; otherwise diagnose directly. Do not proceed until the full Vitest suite is green.

### 8. Decide: scaffold-and-wait vs run Playwright against real BE

Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current` — if not on `features/{feature-id}`, `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull). Read `features/{feature-id}/tracker.md`. Note the current `BE` cell for this story.

**If BE cell != `done`:**
- Stop here in **scaffold-and-wait** mode.
- Leave the sub-branch local and un-merged.
- Flip tracker: keep `FE` = `in-progress`, add/update Notes: `"BE endpoint pending — scaffolded against contracts+mocks, Vitest green. Resume when BE done."`
- Commit tracker update, push.
- Report to the developer:

  ```
  {US-NN} FE scaffolded — waiting on BE

  Vitest: green ({N} tests)
  Playwright: deferred — BE cell = {status}, need 'done' to run against real BE
  Sub-branch: work/{feature-slug}-{US-NN}-fe (local, un-merged)

  Re-run `/load-story {feature-id} {US-NN}` once BE completes to finish the merge.
  ```
- **Exit the skill here.** Do NOT enter the approval pause. Do NOT merge. The sub-branch is preserved for resume.

**If BE cell = `done`:** continue to step 9.

### 9. Run Playwright against a real BE

1. Determine how to get a real BE running locally:
   - Preferred: `cd ../tenxengage-backend && git checkout features/{feature-id} && git pull && ./gradlew bootRun &` (background). Wait for the app to be ready (poll `http://localhost:8080/actuator/health` until `UP`, max 60s).
   - If the developer already has BE running (ask them), skip the bootRun step.
2. Read the `## E2E test [FE]` section of the story file and collect **all scenario names** (each `**Scenario N:**` block). Run each one in turn:
   ```
   npx playwright test e2e/{feature}.spec.ts -g '{scenario test name}'
   ```
   All declared scenarios must pass. If any fail, if `$USE_TDD = true`, invoke `superpowers:systematic-debugging` before proposing fixes; otherwise diagnose directly. Then flip to `blocked` if unresolved.
3. If Playwright passes → proceed to step 10
4. If Playwright fails:
   - Flip tracker `FE` → `blocked` with Notes `"Playwright failed: {one-line summary}"`
   - Push tracker
   - Surface the failure to the developer. Stop.
5. Shut down any BE instance you started (`kill %1` or similar).

### 10. Approval pause — **mandatory**

**Before presenting to the developer:**
1. Run `npm run test` AND `npx playwright test e2e/{feature}.spec.ts` fresh in this message, read full output, confirm both pass with 0 failures. Do not claim "green" without this evidence. If `$USE_TDD = true`, also invoke `superpowers:verification-before-completion`.
2. Structure your approval-pause message with diff stat, summary of what was added, and explicit `merge` prompt. If `$USE_TDD = true`, also invoke `superpowers:requesting-code-review`.

Once Vitest + Playwright both verified green, STOP and show:

```
{US-NN} FE ({title}) — ready for review

Summary:
{2–3 sentence summary: types, hook, component, page wiring, E2E test}

Checklist: all FE items checked
Tests:
  - Vitest: green ({N} tests)
  - Playwright: green against real BE ({test name})

git diff --stat features/{feature-id}..work/{feature-slug}-{US-NN}-fe:
{output}

Reply 'merge' to squash-merge into features/{feature-id}, or 'change X' to revise.
```

- On `merge` → step 11
- On `change X` → stay on the same sub-branch, rerun tests (steps 7 + 9), return here
- On ambiguous reply → ask again; never merge on silence

### 11. Local squash-merge into the feature branch

```
git checkout features/{feature-id}
# only if remote branch exists: git pull --rebase origin features/{feature-id}
git merge --squash work/{feature-slug}-{US-NN}-fe
git commit -m "{US-NN} FE: {title}"
```

Capture SHA: `git rev-parse HEAD`.

### 12. Clean up

`git branch -D work/{feature-slug}-{US-NN}-fe`

### 13. Flip tracker FE → done (blueprint repo)

1. Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current`
   - If on `features/{feature-id}`: continue.
   - If not (edge case — something switched it): `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull).
2. Edit `features/{feature-id}/tracker.md`:
   - Stories row `US-{NN}`: `FE` → `done`
   - `FE Tests` → `green @ {first 12 chars of SHA from step 11}` (Vitest + Playwright against real BE both passed)
   - `Commit (FE)` → first 12 chars of the SHA from step 11
   - `Duration (FE)` → elapsed time from `$FE_CLAIM_TIME` to now; format as `Xh Ym` (e.g., `2h 05m`); omit `0h ` prefix if under 1 hour (e.g., `47m`)
   - `Notes`: append `FE completed {ISO8601}`, remove "BE endpoint pending" if present
3. `git add … && git commit -m "tracker: {feature-slug} {US-NN} FE → done ({short-sha})"`
4. `git push` (retry-on-reject)

### 14. Report

```
{US-NN} FE complete — {title}
Squash-merge: {short-sha} on features/{feature-id}
Tracker: FE cell = done
Wall time: {run `date +%s`, subtract $SKILL_START_EPOCH, format as Xh Ym; omit `0h ` if under 1 hour}

{If any new component file created in this session has `// Adapted from: none`:}
No production analog:
  src/components/{feature}/{Component1}.tsx  — {one-line description}
  src/pages/{feature}/{Page2}.tsx            — {one-line description}
{Add a Mirror row when the same shape appears repeatedly across stories. Omit this block entirely if no files have `// Adapted from: none`.}
```

If both BE and FE for this story are now done, note it. Check whether **all US-NN rows** in the tracker have BE and FE = `done` or `N/A`. If so, check the **T1 row (Cross-story integration tests)** — if T1 = `not-started`, remind the developer: "All stories done. T1 (cross-story integration tests in `test-plan.md`) must pass before the feature PR is opened. Run that session next."

---

## Failure handling

On any error: flip the `FE` cell to `blocked` with a one-line Notes reason, commit + push tracker, surface to the developer, stop. Leave the sub-branch local and un-deleted.

Never flip FE to `done` without (a) Vitest green, (b) Playwright green against real BE, (c) explicit `merge` approval.

---

## Rules

- **One story-layer per invocation.** This skill does the FE half only.
- **Scaffold-and-wait is correct.** If BE is not done, stopping at Vitest-green with the sub-branch preserved is the expected state — not a failure.
- **Never push the sub-branch.** Local only.
- **Never auto-merge.** Mandatory developer approval pause.
- **Tracker claims come first.** Flip to `in-progress` before touching code.
- On `change X`: stay on the same sub-branch.
- On resume (previously in-progress with BE now done): read tracker, check out existing sub-branch, rebase on latest feature branch, skip to step 9 (Playwright against real BE).
- **E2E must run against real BE, never mocks.** `done` requires a passing Playwright run against a BE built from the current feature branch.
- **TDD-only invocations.** `--tdd` is the sole switch for Red/Green/Refactor and the four conditional skill invocations. When `$USE_TDD = true`, follow RGR and invoke the four superpowers skills at the points noted in Steps 6/7/10: `test-driven-development` (start of Step 6), `systematic-debugging` (red Vitest in Step 7 or red Playwright in Step 9), `verification-before-completion` (start of Step 10), `requesting-code-review` (Step 10's approval-pause message). When `$USE_TDD = false`, none of the above applies — no RGR, no auto-invocation of those skills, regardless of any other superpowers skill that may be active.
