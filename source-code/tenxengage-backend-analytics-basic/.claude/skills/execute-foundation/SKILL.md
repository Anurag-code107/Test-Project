---
name: "execute-foundation"
description: "Implement one foundation task for a feature (tasks are defined in foundation.md and run in sequential order): claim the tracker row, create a local sub-branch, implement the task per foundation.md, run tests, pause for developer approval, squash-merge locally into the feature branch, flip the tracker to done."
argument-hint: "feature-slug + task ID (e.g., `rate-course F1`); optional: --tdd"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

Expected forms:
- `rate-course F1`

**Flag:** if `--tdd` appears anywhere in `$ARGUMENTS`, set `$USE_TDD = true` and strip it before normalizing feature-id + task-id. Otherwise `$USE_TDD = false`. When `$USE_TDD = true`, both TDD discipline and superpowers skills are active for this session.

Normalize to feature-slug + task-id. If only a task-id is given (e.g., `F1`), infer the slug from the current branch (`git branch --show-current` → `features/<slug>`). If inference fails (not on a `features/*` branch and no slug in arguments), error: "No feature selected. Pass a slug as argument (e.g. `/execute-foundation quiz-engine F1`) or run from a `features/<slug>` branch."

**Determine roadmap base branch:** Read YAML frontmatter of `../tenxengage-blueprint/features/{feature-slug}/spec.md` — extract `roadmap` as `$ROADMAP_SLUG` (null if absent).
- If `$ROADMAP_SLUG` is non-empty/non-null: `BASE_BRANCH="roadmaps/{ROADMAP_SLUG}"`
- Otherwise: `BASE_BRANCH="${FEATURE_BASE_BRANCH:-main}"`

---

## Purpose

Implement a single foundation task end-to-end:
- Claim the row in `tracker.md` (blueprint repo) so no other session races on it
- Create a **local-only** sub-branch off the feature branch
- Implement per `tasks/foundation.md`
- Run tests
- **Pause for developer approval** (mandatory — never auto-merge)
- On approval: local `git merge --squash` into the feature branch, push the feature branch
- Flip tracker → `done`, stamp the squash-merge commit SHA

**There is no GitHub PR for a foundation task.** The only GitHub PR per feature is the final `features/{feature-slug}` → `roadmaps/{slug}` PR (for BRD-derived features) or `features/{feature-slug}` → `main` PR (for standalone features), opened manually when the tracker is all-green. The roadmap branch itself is merged to `main` when all features in the roadmap are done.

---

## Steps

### 1. Pre-flight — feature branch + latest contracts

1. `git branch --show-current` → if already on `features/{feature-id}`, skip to step 1c
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
      git submodule update --init contracts || { echo "Contracts submodule failed to initialize. Run 'git submodule update --init contracts' manually and re-run /execute-foundation."; exit 1; }
    fi
    ```

    **(ii) Select the target contracts branch.** Prefer `features/{feature-id}`; fall back to `$BASE_BRANCH` (computed in pre-flight). Records the chosen branch in `$CONTRACTS_BRANCH`. Use `--track origin/` explicitly to avoid ambiguity when the submodule has multiple remotes with the same branch name — a bare `checkout <branch>` will fail silently if two remotes both have it:
    ```bash
    # Fetch origin first so remote refs are current
    git -C contracts fetch origin

    # Try to checkout the feature branch, explicitly from origin to avoid multi-remote ambiguity
    _CHECKOUT_ERR=$(git -C contracts checkout --track origin/features/{feature-id} 2>&1)
    _CHECKOUT_EXIT=$?

    if [ $_CHECKOUT_EXIT -eq 0 ]; then
      CONTRACTS_BRANCH="features/{feature-id}"
    elif git -C contracts rev-parse --verify features/{feature-id} >/dev/null 2>&1; then
      # Local branch already exists — switch to it
      git -C contracts checkout features/{feature-id}
      CONTRACTS_BRANCH="features/{feature-id}"
    else
      echo "Contracts feature branch not found — using base branch: $BASE_BRANCH"
      git -C contracts checkout --track "origin/$BASE_BRANCH" 2>/dev/null \
        || git -C contracts checkout "$BASE_BRANCH" 2>/dev/null \
        || { echo "Contracts submodule cannot reach any usable branch. Aborting."; exit 1; }
      CONTRACTS_BRANCH="$BASE_BRANCH"
    fi
    ```

    **(iii) Conditional pull.** Pull only when all three guards pass — otherwise print a one-line skip reason and continue (do NOT abort):
    - Guard 1 — remote branch exists: `git -C contracts ls-remote --exit-code origin "$CONTRACTS_BRANCH"` exits 0. If not: print `Skipping contracts pull — remote branch not found`.
    - Guard 2 — no uncommitted changes in the submodule working tree: `git -C contracts status --porcelain` is empty. If not: print `Skipping contracts pull — uncommitted changes in submodule`.
    - Guard 3 — no unpushed commits: `git -C contracts log "origin/$CONTRACTS_BRANCH..HEAD" --oneline` is empty. If not: print `Skipping contracts pull — unpushed commits in submodule`.
    - All pass → `git -C contracts pull --rebase origin "$CONTRACTS_BRANCH"`.

    **(iv) Commit pointer bump on the feature branch.** If the submodule pointer advanced, commit it on the feature branch (no push — later steps push the feature branch). This keeps step 1c's "no uncommitted changes" guard clean on the next invocation, and records what contracts SHA each task consumed in `git log features/{feature-id} -- contracts`:
    ```bash
    if git status --porcelain contracts | grep -q .; then
      CONTRACTS_SHA=$(git -C contracts rev-parse --short=12 HEAD)
      git add contracts
      git commit -m "chore: bump contracts pointer to ${CONTRACTS_SHA} for {task-id} pre-flight"
    fi
    ```

### 2. Claim the tracker row (blueprint repo)

Do this **before** touching any code. Claiming is the first commit of the session.

**Blueprint branch guard (before reading any tracker files):**
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
e. Blueprint repo is now on `features/{feature-id}`. Continue reading tracker.

1. Read `features/{feature-id}/tracker.md`
3. Locate the Foundation row with `#` = `{task-id}` (e.g., `F1`)
4. Validate:
   - Status = `not-started`. If `in-progress` by another session → **abort** with "Row {task-id} is already in-progress by session {id}. Pick a different row or resume that session."
   - All rows with `#` < `{task-id}` must be `done` (or `N/A`). Foundation tasks run strictly in order. If not, abort with "F{n-1} is not done — foundation runs sequentially."
5. Edit the tracker row: `Status = in-progress`, `Session = {new session id — use timestamp + random suffix}`, `Started = {ISO 8601 now}`. Record the current timestamp as `$CLAIM_TIME` (used to compute duration at step 10).
6. `git add features/{feature-id}/tracker.md && git commit -m "tracker: {feature-slug} F{n} → in-progress"`
7. `git push` — on reject: `git pull --rebase && git push`. Retry up to 3 times. If the row was flipped by another session in the meantime (re-read shows not-started → in-progress by someone else), abort.
8. **No `cd` was performed.** All blueprint ops in this step used `git -C ../tenxengage-blueprint`. Your CWD is still the backend repo. Do **not** run `cd -` or `cd ../tenxengage-blueprint` — both are bugs. If you accidentally `cd`ed into blueprint anywhere above, `cd` back to the backend repo now before continuing.

### 3. Create the sub-branch (local only)

**CWD guard — mandatory before any work-branch creation.** Run:

```bash
basename "$(git rev-parse --show-toplevel)"
```

The output **must** be `tenxengage-backend`. If it is `tenxengage-blueprint` (or anything else), **stop**: `cd` back to the backend repo and re-run the guard. Work branches must only ever be created in the backend repo — creating `work/*` in the blueprint repo is a known bug class this guard exists to prevent.

1. From `features/{feature-id}` (in the backend repo): `git checkout -b work/{feature-slug}-{task-id}-{title-slug}`
   - `{title-slug}` is derived from the task title, lowercase-kebab-case (e.g., `work/quiz-engine-F1-enums` for "Enums"; `work/quiz-engine-F2-migrations` for "Flyway migrations")
   - Immediately after creation, re-verify with `basename "$(git rev-parse --show-toplevel)"` → must be `tenxengage-backend`. If it is `tenxengage-blueprint`, delete the misplaced branch (`git -C ../tenxengage-blueprint branch -D work/{feature-slug}-{task-id}-{title-slug}`), `cd` to the backend repo, and re-run step 3.
2. **Do NOT push this branch.** It lives locally only.

### 4. Read the task definition

Read `../tenxengage-blueprint/features/{feature-id}/tasks/foundation.md` and locate the `### F{n}` section. Capture:
- Files to create/modify (concrete paths)
- Execution checklist
- Done when verification

Also read these reference files per task type:
- **F1 Enums**: `src/main/java/com/tenxengage/app/enums/` — existing enum patterns; `src/main/java/com/tenxengage/app/audit/AuditAction.java` and `AuditResourceType.java`
- **F2 Flyway migrations**: `src/main/resources/db/migration/` — determine the next `V{N}__` prefix
- **F3 Entities + repositories + fixtures**: 1 existing entity + 1 existing repository + 1 existing `*Fixtures.java` for pattern match
- **F4 Permissions + feature flags seed**: existing permission seed SQL migration for pattern match
- **F5 BE-only plumbing**: existing Kafka consumer/producer for pattern match

**Read domain registry** (only if `spec.md` frontmatter has `domain:` non-null):
- Read `../tenxengage-blueprint/docs/patterns/domains/INDEX.md`.
- Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`.
- If `builder_type:` set and `{domain}/{builder-type}.md` exists, read it too.
- Foundation tasks for a slot-filling feature MUST use registry primitives. If `foundation.md` references a name that conflicts, abort and flag.

### 5. Adopt TDD for implementation

**If `$USE_TDD = true`:** Invoke `superpowers:test-driven-development` now, before writing any production code. That skill owns the RED → GREEN → REFACTOR cycle; the rules below are the project-specific bits it does **not** cover.

Apply the cycle to every execution-checklist item in `foundation.md` (one enum constant, one migration, one entity field, one permission seed row, etc.):
- **Scoped test run** — use `./gradlew test --tests '*{ThingTest}'` for the inner loop, not the full suite.
- **Commit cadence** — when an item goes green and is refactored, commit the test + production code together **and** tick the corresponding `[x]` in `foundation.md` in the same commit.
- **Hard rule** — never write production code before a failing test exists for it.

### 6. Implement

Work through the task's execution checklist in `foundation.md`. Check items `[x]` in the file as you complete them — **commit the checklist update alongside the code** in the same commit so the sub-branch always reflects true progress.

Commit granularity on the sub-branch: one commit per logical unit (e.g., "add {Enum}", "add V{N}__migration.sql"). These commits will be squashed at merge time, so commit freely.

### 7. Run tests

- **Inner loop** — follow the RED→GREEN→REFACTOR cycle from Step 5 for every checklist item. Scoped run: `./gradlew test --tests '*{ThingTest}'`
- **Outer loop** (before approval pause): `./gradlew test` — the full test suite. Must be green.

**If any test is red:** If `$USE_TDD = true`, invoke `superpowers:systematic-debugging` before proposing fixes; otherwise diagnose directly. Do not proceed to the approval pause until the full suite is green.

### 8. Approval pause — **mandatory**

**Before presenting to the developer:**
1. Run `./gradlew test` fresh in this message, read full output, confirm exit code 0 and 0 failures. Do not claim "green" without this evidence. If `$USE_TDD = true`, also invoke `superpowers:verification-before-completion`.
2. Structure your approval-pause message with diff stat, summary of what was added, and explicit `merge` prompt. If `$USE_TDD = true`, also invoke `superpowers:requesting-code-review`.

Once the full suite is verified green, STOP and show the developer:

```
F{n} ({slug}) — ready for review

Summary:
{2–3 sentence summary of what was added}

git diff --stat features/{feature-id}..work/{feature-slug}-{task-id}-{title-slug}:
{output of that command}

Reply 'merge' to squash-merge into features/{feature-id}, or 'change X' to revise.
```

Wait for the developer's response. **Never proceed to merge without an explicit 'merge'.**

- On `merge` → step 9
- On `change X` → implement the change on the sub-branch, re-run tests (step 7), return to step 8
- On silence or ambiguous reply → ask again; do not merge

### 9. Local squash-merge into the feature branch

```
git checkout features/{feature-id}
# only if remote branch exists: git pull --rebase origin features/{feature-id}
git merge --squash work/{feature-slug}-{task-id}-{title-slug}
git commit -m "{task-id}: {short title}"
```

Capture the commit SHA: `git rev-parse HEAD` → this is the `Commit` value for the tracker.

### 10. Clean up the sub-branch

`git branch -D work/{feature-slug}-{task-id}-{title-slug}` — local delete. (Never pushed, so nothing to delete on remote.)

### 11. Flip tracker to done (blueprint repo)

1. Re-verify blueprint branch: `git -C ../tenxengage-blueprint branch --show-current`
   - If on `features/{feature-id}`: continue.
   - If not (edge case — something switched it): `git -C ../tenxengage-blueprint checkout features/{feature-id}` (local only; do NOT fetch or pull — step 2 already resolved the state).
2. Edit `features/{feature-id}/tracker.md`:
   - Set this task's `Status` → `done`
   - Set `Completed` → `{ISO 8601 now}`
   - Set `Duration` → elapsed time from `$CLAIM_TIME` to now; format as `Xh Ym` (e.g., `2h 05m`); omit `0h ` prefix if under 1 hour (e.g., `47m`)
   - Set `Commit` → the SHA captured in step 9 (first 12 chars)
3. `git -C ../tenxengage-blueprint add features/{feature-id}/tracker.md && git -C ../tenxengage-blueprint commit -m "tracker: {feature-slug} F{n} → done ({short-sha})"`
4. `git -C ../tenxengage-blueprint push` (with retry-on-reject as in step 2)

### 12. Report

```
F{n} complete — {slug}
Squash-merge: {short-sha} on features/{feature-id}
Tracker: features/{feature-id}/tracker.md updated
```

---

## Failure handling

If anything fails between steps 3–7:
1. Flip the tracker row → `blocked` with a one-line reason in Notes (e.g., "failing test: XyzTest.shouldValidate")
2. Commit + push the tracker change
3. Surface the failure to the developer and stop
4. Leave the sub-branch local and un-deleted so work isn't lost

**Never** flip to `done` without both (a) green full test suite and (b) explicit developer `merge` approval.

---

## Rules

- **One foundation task per invocation.** Do not bundle F1 + F2 in one session.
- **Working directory invariant.** This skill runs in the **backend** repo. All blueprint operations MUST use `git -C ../tenxengage-blueprint` — never `cd ../tenxengage-blueprint`. Work branches (`work/*`) must NEVER be created in the blueprint repo. The CWD guard in Step 3 enforces this; if it fires, fix CWD before continuing.
- **Never push the sub-branch to remote.** It is local-only.
- **Never auto-merge.** The approval pause is mandatory on every run, even if "it's just a trivial change".
- **Tracker writes go first.** Claim the row before any code. Flip to `done` only after merge is complete.
- If the developer says `change X`: do NOT create a new sub-branch. Continue on the same one so the feature branch history stays clean after squash.
- If resuming a previously-blocked or interrupted session: read the tracker, confirm the session ID matches (or offer to take it over), check out the existing sub-branch, continue from the first unchecked execution-checklist item.
- **TDD (when `--tdd` passed).** If `$USE_TDD = true`, invoke `superpowers:test-driven-development` at Step 5 and follow Red→Green→Refactor — a failing test must exist before any production code is written for each checklist item.
- **Debug systematically.** When tests fail, invoke `superpowers:systematic-debugging` (if `$USE_TDD = true`) before proposing any fix.
- **Verify before claiming done.** Invoke `superpowers:verification-before-completion` (if `$USE_TDD = true`) before the approval pause. Fresh evidence, not memory.
- **Review before merge.** Invoke `superpowers:requesting-code-review` (if `$USE_TDD = true`) when presenting for developer approval.
