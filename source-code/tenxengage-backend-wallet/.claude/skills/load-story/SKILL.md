---
name: "load-story"
description: "Implement the BE half of one user story (US-NN) for a story-sliced feature: claim the tracker BE cell, create a local sub-branch, implement the story's BE tasks, run tests, pause for developer approval, squash-merge locally, flip tracker to done. For story-sliced features only (per spec.md frontmatter `format: story-sliced`)."
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
- When `$USE_TDD = true`: the Red/Green/Refactor cycle and the four superpowers skills referenced in Steps 6–8 are active for this session.
- When `$USE_TDD = false`: do NOT follow Red/Green/Refactor. Implement per the story's task order. No other active superpowers skill implies TDD discipline — `--tdd` is the sole switch.

**Wall-clock start:** Run `date +%s` and store the result as `$SKILL_START_EPOCH`. This is the wall-time origin for the final report — captured once, here, before any other work begins.

Normalize to feature-slug + story-id. If only a story-id is given, infer the slug from the current branch (`features/<slug>`). If inference fails (not on a `features/*` branch and no slug in arguments), error: "No feature selected. Pass a slug as argument (e.g. `/load-story quiz-engine US-01`) or run from a `features/<slug>` branch."

**Legacy check:** Read YAML frontmatter of `../tenxengage-blueprint/features/{feature-slug}/spec.md`. If `format: single-file`, abort — `/load-story` is for story-sliced features only. Direct the user to `/load-spec` for single-file features.

**Determine roadmap base branch:** Read YAML frontmatter of `../tenxengage-blueprint/features/{feature-slug}/spec.md` — extract `roadmap` as `$ROADMAP_SLUG` (null if absent).
- If `$ROADMAP_SLUG` is non-empty/non-null: `BASE_BRANCH="roadmaps/{ROADMAP_SLUG}"`
- Otherwise: `BASE_BRANCH="${FEATURE_BASE_BRANCH:-main}"`

---

## Purpose

Implement the **BE half** of one user story end-to-end:
- Claim the `BE` cell of the story row in `tracker.md` so no other BE session races on it
- Create a **local-only** sub-branch off the feature branch
- Implement the `## BE tasks [BE]` section of `stories/US-NN-*.md`
- Run BE tests
- **Pause for developer approval** (mandatory — never auto-merge)
- On approval: local `git merge --squash` into the feature branch, push
- Flip the BE cell → `done`, stamp the squash-merge commit SHA in `Commit (BE)`

The FE half runs separately in the frontend repo via its own `/load-story`.

**There is no GitHub PR for this sub-branch.** The only GitHub PR per feature is the final `features/{feature-slug}` → `roadmaps/{slug}` PR (for BRD-derived features) or `features/{feature-slug}` → `main` PR (for standalone features), opened manually when the tracker is all-green. The roadmap branch itself is merged to `main` when all features in the roadmap are done.

---

## Steps

### 1. Pre-flight — feature branch + latest contracts

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
2. Parse frontmatter `layers` field
3. If `layers` does NOT include `"BE"` → **abort** with: "Story {US-NN} is FE-only (`layers: {value}`). Run this in the frontend repo: `cd ../tenxengage-frontend && /load-story {feature-id} {US-NN}`."
4. Capture: `id`, `title`, `touches_entities`, `depends_on_stories`

### 3. Claim the tracker `BE` cell (blueprint repo)

1. Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current`
   - If on `features/{feature-id}`: continue.
   - If not (edge case — something switched it): `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull — step 2 already resolved the state).
2. Read `features/{feature-id}/tracker.md`
3. Locate the Stories row for `US-{NN}`
4. Validate:
   - `BE` cell = `not-started`. If `in-progress` by another session → **abort** with "US-{NN} BE is already in-progress by session {id}."
   - `BE` cell is not `N/A` (sanity check — would mean layers didn't include BE; the step-2 validation should have caught this).
   - **All foundation tasks** listed in `Depends on` are `done`. Abort with specific missing deps if not.
   - **All prior stories** in `Depends on` have BE = `done` (or `N/A`). FE status of dependency stories is irrelevant when claiming a BE session. Abort with specific missing deps if not.
5. Edit the row: set `BE` → `in-progress`. Record the current ISO8601 timestamp as `$BE_CLAIM_TIME` (used for duration at step 11). Write `session={id} started={ISO8601}` into the `Notes` column.
6. `git add features/{feature-id}/tracker.md && git commit -m "tracker: {feature-slug} {US-NN} BE → in-progress"`
7. `git push` — on reject: `git pull --rebase && git push`. Retry up to 3 times. If the cell was flipped by another session, abort.
8. `cd -` back to backend

### 4. Create the sub-branch (local only)

```
git checkout -b work/{feature-slug}-{US-NN}-be
```

Do NOT push.

### 5. Read the story BE section

Read `../tenxengage-blueprint/features/{feature-id}/stories/US-{NN}-*.md`. Extract only these sections:
- `## Description` — actor, trigger, expected outcome, negative paths
- `## Depends on` — foundation + prior stories
- `## Spec references` — the list of sections to read (entries may point to `spec.md` for decisions or `technical.md` for file paths, Flyway SQL, and repository queries — follow each pointer exactly)
- `## BE tasks [BE]` — the complete section
- `## Execution checklist` — the **BE session** block only

Also read each referenced section (from `spec.md` or `technical.md` as indicated) — do not guess DTO fields, endpoint shapes, file paths, audit entries, or permission strings.

**Read domain registry** (only if `spec.md` frontmatter has `domain:` non-null):
- Read `../tenxengage-blueprint/docs/patterns/domains/INDEX.md`.
- Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`.
- If `builder_type:` is set and `{domain}/{builder-type}.md` exists, read it too.
- Use these slot fillers for entity names, service names, and types. Story-level deviations from these fillers must be flagged to the user before writing code.

Read these BE reference files for pattern consistency (one of each — do not read exhaustively):
- One existing controller in `src/main/java/com/tenxengage/app/controller/` — matches the closest pattern
- One existing service in `src/main/java/com/tenxengage/app/service/`
- One existing `*ControllerTest.java` using `@WebMvcTest`
- One existing `*ServiceTest.java` using `@ExtendWith(MockitoExtension.class)`

### 6. Implement the BE tasks

**TDD discipline.** If `$USE_TDD = true`, invoke `superpowers:test-driven-development` at the start of this step and follow it for every production class or method. Project-specific glue (the skill's examples are generic):

- Scoped test command for the Red/Green verify steps: `./gradlew test --tests '*{Class}Test.{method}*'`
- Tests are either service tests (`@ExtendWith(MockitoExtension.class)`) or `@WebMvcTest` controller tests.

If `$USE_TDD = false`, do NOT enter a Red/Green/Refactor loop. Write production code and tests in the order the story task block specifies; tests are still required by each task's acceptance criteria, just not test-first.

Iterate the `## BE tasks [BE]` section **in the order written in the story file**. Each task block specifies its own `**Files:**`, its acceptance criteria, and any test expectations — do not guess or supplement. The story file is the source of truth for which tasks exist and what each entails.

Do NOT assume a fixed count. Some stories have two BE tasks, some have five. The numbering (`BE-1`, `BE-2`, …) is a readable label, not a contract — read them as a list, not as slots.

As each task's work lands, check the matching items in the **BE session** block of `## Execution checklist` (which is expressed in terms of concrete deliverables, e.g. `{Entity}ServiceTest unit tests pass`, not by task number). Commit checklist updates alongside the code.

Typical tasks you'll encounter in a CRUD-shaped story — for orientation only; your actual story drives what gets implemented:
- DTOs in `src/main/java/com/tenxengage/app/dto/`
- Service method + unit test (`@ExtendWith(MockitoExtension.class)`)
- Controller endpoint + `@WebMvcTest`
- `@Audited` annotation on write operations
- **Kafka producer unit test** (Mockito) — if the story publishes a domain event, a producer unit test asserting the correct topic name and payload fields is required in the same story session. Full round-trip consumer tests go in `test-plan.md → Audit & Events`, not here.

**Contract-change rule:** DTO field names and types must match the generated contract (`../tenxengage-contracts/endpoints/`). If a mid-story change is needed, follow the **contract-change ritual** before writing code:
1. Edit the relevant section of `../tenxengage-blueprint/features/{feature-id}/spec.md`
2. Re-run `cd ../tenxengage-contracts && /generate-contracts {feature-id}` (idempotent)
3. Note in the story row's Notes column in `tracker.md`: "contract updated — refetch types"
Then continue from the updated contract.

Commit granularity: one commit per logical unit. All squashed at merge.

### 7. Run tests

- **Inner loop** (inside Step 6): scoped test runs that follow each unit of production code. Step 6 drives this — see L189 for the scoped command. When `$USE_TDD = true`, this is the TDD Green phase; otherwise it's just incremental verification.
- **Outer loop** (before approval pause): `./gradlew test` — full suite, must be green

**If any test is red:** If `$USE_TDD = true`, invoke `superpowers:systematic-debugging` before proposing fixes; otherwise diagnose directly. Do not proceed to the approval pause until the full suite is green.

### 8. Approval pause — **mandatory**

**Before presenting to the developer:**
1. Run `./gradlew test` fresh in this message, read full output, confirm exit code 0 and 0 failures. Do not claim "green" without this evidence. If `$USE_TDD = true`, also invoke `superpowers:verification-before-completion`.
2. Structure your approval-pause message with diff stat, summary of what was added, and explicit `merge` prompt. If `$USE_TDD = true`, also invoke `superpowers:requesting-code-review`.

Once the full suite is verified green, STOP and show:

```
{US-NN} BE ({title}) — ready for review

Summary:
{2–3 sentence summary: DTOs added, service method {X}, endpoint {METHOD} {path}, audit entry}

Checklist: all BE items checked
Tests: ./gradlew test — green ({N} new tests)

git diff --stat features/{feature-id}..work/{feature-slug}-{US-NN}-be:
{output}

Reply 'merge' to squash-merge into features/{feature-id}, or 'change X' to revise.
```

- On `merge` → step 9
- On `change X` → implement on the **same** sub-branch, rerun tests, return here
- On ambiguous reply → ask again; never merge on silence

### 9. Local squash-merge into the feature branch

```
git checkout features/{feature-id}
# only if remote branch exists: git pull --rebase origin features/{feature-id}
git merge --squash work/{feature-slug}-{US-NN}-be
git commit -m "{US-NN} BE: {title}"
```

Capture SHA: `git rev-parse HEAD`.

### 10. Clean up

`git branch -D work/{feature-slug}-{US-NN}-be`

### 11. Flip tracker BE → done (blueprint repo)

1. Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current`
   - If on `features/{feature-id}`: continue.
   - If not (edge case — something switched it): `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull).
2. Edit `features/{feature-id}/tracker.md`:
   - Stories row `US-{NN}`: `BE` → `done`
   - `BE Tests` → `green @ {first 12 chars of SHA from step 9}`
   - `Commit (BE)` → first 12 chars of the SHA from step 9
   - `Duration (BE)` → elapsed time from `$BE_CLAIM_TIME` to now; format as `Xh Ym` (e.g., `2h 05m`); omit `0h ` prefix if under 1 hour (e.g., `47m`)
   - `Notes`: append `BE completed {ISO8601}`
3. `git add … && git commit -m "tracker: {feature-slug} {US-NN} BE → done ({short-sha})"`
4. `git push` (retry-on-reject)

### 12. Report

```
{US-NN} BE complete — {title}
Squash-merge: {short-sha} on features/{feature-id}
Tracker: BE cell = done
Wall time: {run `date +%s`, subtract $SKILL_START_EPOCH, format as Xh Ym; omit `0h ` if under 1 hour}
FE: waiting on frontend repo's /load-story {feature-id} {US-NN}
```

If the story is `layers: ["BE", "FE"]` and FE status is `not-started`, remind the developer that FE work for this story can now proceed.

Check whether **all US-NN rows** in the tracker have BE = `done` or `N/A`. If so, check the **T1 row (Cross-story integration tests)** — if T1 = `not-started`, note: "All BE stories done. T1 (cross-story integration tests in `test-plan.md`) must pass before the feature PR is opened. Run that session after all FE stories complete too."

---

## Failure handling

Identical to `/execute-foundation`: on any error, flip the `BE` cell to `blocked` with a one-line Notes reason, commit + push tracker, surface to the developer, stop. Leave the sub-branch local and un-deleted for resumption.

Never flip to `done` without (a) green full test suite and (b) explicit `merge` approval.

---

## Rules

- **One story-layer per invocation.** `/load-story 002 US-01` does the BE half. A separate invocation in the frontend repo does the FE half.
- **Never push the sub-branch.** Local only.
- **Never auto-merge.** Mandatory developer approval pause.
- **Tracker claims come first.** Flip to `in-progress` before touching code.
- On `change X`: stay on the same sub-branch. The feature-branch history stays clean via squash.
- On resume (previously in-progress or blocked): read tracker, check out existing sub-branch, continue from first unchecked execution-checklist BE item.
- If a prior story's BE is not yet `done`, abort with a clear dep message — do not implement out of order. FE status of dependencies does not gate BE work.
- **TDD-only invocations.** `--tdd` is the sole switch for Red/Green/Refactor and the four conditional skill invocations. When `$USE_TDD = true`, follow RGR and invoke the four superpowers skills at the points noted in Steps 6–8: `test-driven-development` (start of Step 6), `systematic-debugging` (red test in Step 7), `verification-before-completion` (start of Step 8), `requesting-code-review` (Step 8's approval-pause message). When `$USE_TDD = false`, none of the above applies — no RGR, no auto-invocation of those skills, regardless of any other superpowers skill that may be active.
