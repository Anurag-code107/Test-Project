---
name: "execute-integration-tests"
description: "Run T1 cross-story integration tests for a feature: dispatches per-repo run-tests (BE + FE against real stack), manages docker-compose.test.yml lifecycle, captures Tier 1/2/3 learnings, flips T1 tracker row."
argument-hint: "<feature-slug> [--gate=<every|story|ready-check|feature-end>] [--from=<step>] [--soft-stages=<csv>] [--phase=<implement|merge>] [--model=<model-id>] [--dry-run] [--reuse-stack]"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

The first positional argument is `<feature-slug>` (e.g., `assessment-authoring`).

---

## Purpose

Run the **T1** row of a feature's `tracker.md` to completion. T1 is the cross-story
integration gate: BE integration tests + Playwright tests against the real running
stack + knowledge capture.

This skill is a thin orchestrator. It delegates test generation and execution to
per-repo `run-tests` via the v9 CLI-respawn pattern. It owns: parsing the test
plan, managing the test stack, attributing failures across stories, capturing
learnings to `docs/patterns/` and `docs/learnings.md`, and flipping the tracker.

**Design reference:** `docs/superpowers/specs/2026-05-18-execute-integration-tests-design.md`.

---

## Flags

| Flag | Default | Purpose |
|---|---|---|
| `--gate=<every\|story\|ready-check\|feature-end>` | `every` | Whether Step 9 (flip-t1) pauses for chat approval before committing the tracker flip. **Functionally binary:** `every` pauses; `story`, `ready-check`, and `feature-end` all auto-flip on green (no semantic difference among the three at T1). The four-mode vocabulary mirrors `/load-story` and `/execute-foundation` so `/run-feature` can plumb a single `--gate` value through every unit. T1 is the final implementation unit — there is no further `/run-feature` pause downstream of T1 — so any non-`every` mode means "trust the pipeline through to the end." |
| `--from=<step-name>` | unset | Re-entry after a halt. Valid values: `parse`, `dispatch-be`, `gate-be`, `spin-stack`, `dispatch-fe`, `gate-fe`, `teardown`, `capture`, `flip-t1`. |
| `--soft-stages=<csv>` | unset | Accepted for `/run-feature` dispatch compatibility. No behavioral effect — T1 does not internally invoke `ready-check`. |
| `--phase=<implement\|merge>` | unset | `/run-feature` dispatch compatibility. `--phase=implement` runs Steps 1–8 and exits with `ORCHESTRATOR_RETURN` summary (no Step 9 flip). `--phase=merge` skips to Step 9. Unset → run all steps. |
| `--dry-run` | off | Preview manifest and dispatch plan without executing. |
| `--reuse-stack` | off | Skip Step 4 (spin-stack) and Step 7 (teardown). Use when the developer already has the test stack running. |
| `--model=<model-id>` | `claude-sonnet-4-6` | Model ID passed to the inner `claude -p` dispatches in Steps 2 and 5 (BE and FE `run-tests`). Sonnet is sufficient for the bounded scaffolding + mechanical auto-fix work those inner sessions do. Override to `claude-opus-4-7` if a feature's T1 keeps halting on judgment calls that the cap-to-mechanical fix gate gets wrong. |

---

## Steps

1. **parse** — read test-plan.md, build manifest, detect v1/v2 format
2. **dispatch-be** — invoke `/run-tests` in backend repo for T1 BE classes
3. **gate-be** — interpret BE result; halt with t1-report.md if red
4. **spin-stack** — `docker compose -f ../tenxengage-backend/docker-compose.test.yml up -d --wait`
5. **dispatch-fe** — invoke `/run-tests --real-backend` in frontend repo for T1 specs
6. **gate-fe** — interpret FE result; halt with t1-report.md if red
7. **teardown** — `docker compose down -v`
8. **capture** — Tier 1/2/3 knowledge capture, append to learnings.md, commit
9. **flip-t1** — gate-conditional pause, then flip tracker T1 row to done, commit

Each step is independently re-enterable via `--from=<step>`.

---

## Step 1 — parse

### Inputs
- `<feature-slug>` from `$ARGUMENTS`
- `features/<slug>/test-plan.md`

### Actions

> **Dry-run guard.** If `--dry-run` is set, Actions **3**, **6**, and **7** below are no-ops — do not write to `tracker.md`, do not write `.t1-manifest.json` to disk, do not modify `.gitignore`, and do not create any commits. Compute the manifest in memory (Action 6's parse logic still runs, but the output is held in memory only), then jump to Action 8. The format-detection and upgrade-decision flow (Actions 4–5) still runs in `--dry-run` for visibility, but under dry-run the **upgrade** branch in Action 5 must print the diff it *would* write and exit instead of actually editing `test-plan.md` or committing.

1. **Verify branch.** The current branch should be `features/<slug>` (or a `work/<slug>-T1` sub-branch if recovering). If not, STOP: "Run this skill from the feature branch."

2. **Capture epochs.** Set:
   - `SKILL_START_EPOCH = $(date +%s)`
   - `CLAIM_TIME_EPOCH = $SKILL_START_EPOCH` (only if T1 row not yet `in-progress`)
   - `HUMAN_PAUSE_TOTAL_SECS = 0`

3. **Claim the T1 row** *(skipped under `--dry-run`)*. If `features/<slug>/tracker.md` has T1 status `not-started`, flip it to `in-progress` with `Session=<this skill's session id>` and `Started=<ISO 8601 UTC>`. Commit as `tracker: <slug> T1 → in-progress`.

4. **Detect test-plan format.** Read `features/<slug>/test-plan.md`. Check for presence of both headings:
   - `## Query Correctness at Scale`
   - `## E2E Cross-Story Scenarios (Real Stack)`

   If both are absent → `test_plan_format = v1`. Otherwise → `v2`.

5. **If v1, decide upgrade path.** Surface to developer:

   ```
   Test plan for <slug> is pre-cluster format (v1).
   Missing sections: Query Correctness at Scale, E2E Cross-Story Scenarios (Real Stack).

   Options:
     [u]pgrade  Re-generate test-plan.md with the v2 template (shows diff for approval)
     [s]kip     Proceed with only sections present in v1; flag missing dimensions in t1-report
     [a]bort    Stop. Manually upgrade and re-run.
   ```

   Wrap the prompt with `HUMAN_PAUSE_TOTAL_SECS += <wait_seconds>`.

   - **upgrade:** Read `features/<slug>/spec.md` and `features/<slug>/stories.md`. Apply the v2 template (`.claude/skills/create-stories/templates/test-plan-template.md`) by adding the missing sections to `test-plan.md` with feature-specific scenarios. Show diff. Await `accept` reply. Commit as `test-plan: <slug> upgrade to v2 format`. Proceed.
   - **skip:** Continue. Set `manifest.format = "v1-degraded"` so the final report can flag missing dimensions.
   - **abort:** Exit cleanly with `ORCHESTRATOR_RETURN status=failure failed_step=parse failed_reason="test-plan v1, developer aborted upgrade"`.

6. **Build manifest** *(under `--dry-run`: compute in memory only, do not write to disk)*. Parse the test-plan.md sections into a structured manifest. On a real run, persist to `features/<slug>/.t1-manifest.json` (gitignored). Schema:

   ```json
   {
     "feature": "<slug>",
     "format": "v1-degraded|v2",
     "be": {
       "test_classes": ["AssessmentLifecycleIT", "..."],
       "categories_present": ["lifecycle", "cascades", "state-machine", "..."]
     },
     "fe": {
       "spec_files": ["e2e/<slug>/full-happy-path.spec.ts", "..."],
       "scenarios": [
         {"file": "e2e/<slug>/full-happy-path.spec.ts", "scenarios": ["scenario 1 name", "..."]}
       ]
     },
     "metadata": {
       "total_be_scenarios": 42,
       "total_fe_scenarios": 8,
       "stories_referenced": ["US-01", "US-02", "..."]
     }
   }
   ```

   For BE: extract every value in the `Test Class` column across all BE-flavored sections (Lifecycle, Cascades, State Machine, Business Rules, Multi-Entity, Contract Conformance, Tenant Isolation & Security, Audit & Events, Query Correctness if present, Cross-Cutting Checks, plus any feature-specific sections). Deduplicate.

   For FE: extract every value in the `Spec File` column from the `E2E Cross-Story Scenarios (Real Stack)` section. If section is absent (v1-degraded), `fe.spec_files = []`.

7. **Ensure `.t1-manifest.json` is gitignored** *(skipped under `--dry-run`)*. Append `**/.t1-manifest.json` to `.gitignore` at repo root if not already present.

8. **Dry-run early exit.** If `--dry-run`, print the in-memory manifest and the planned dispatch commands for Steps 2, 4, 5, and 7. Emit `ORCHESTRATOR_RETURN status=success` and `ORCHESTRATOR_RETURN summary=dry-run` and exit. Per the Dry-run guard at the top of this Actions list, Actions 3, 6, and 7 above have already been skipped — no files on disk, no commits, no tracker mutation.

### Output of Step 1
- `features/<slug>/.t1-manifest.json` exists and is readable
- T1 tracker row is `in-progress` (if not already)
- Format known (v1-degraded or v2)
- Test plan upgrade applied if developer chose `upgrade`

---

## Step 2 — dispatch-be

### Inputs
- `features/<slug>/.t1-manifest.json` (read `be.test_classes`)

### Actions

Dispatch backend `run-tests` via the v9 CLI-respawn pattern. The `Agent` tool wraps a single bash command that `cd`s into the sibling repo and runs `claude -p --output-format=json`.

Construct the test class list:
```
BE_CLASSES=$(jq -r '.be.test_classes | join(" ")' features/<slug>/.t1-manifest.json)
```

Dispatch:

```
Agent({
  description: "T1 BE dispatch",
  subagent_type: "general-purpose",
  run_in_background: false,
  prompt: """
    Run this single bash command and capture stdout:

      cd c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-backend && \
        claude -p --output-format=json --model=<MODEL> \
          "/run-tests <BE_CLASSES>"

    Parse the JSON. From the 'result' field, extract every line starting
    with 'ORCHESTRATOR_RETURN ' and emit those lines verbatim as your final
    output (one per line). Also emit:
      INNER_SESSION_ID=<JSON 'session_id' field>
      INNER_COST_USD=<JSON 'total_cost_usd' field>

    Do not summarize or interpret. Pure passthrough.
  """
})
```

Parse the subagent's output. Collect:
- All `ORCHESTRATOR_RETURN key=value` lines → `be_return` dict
- `INNER_SESSION_ID` → `be_inner_session`
- `INNER_COST_USD` → `be_inner_cost`

Persist to `features/<slug>/.t1-run/be-result.json` (gitignored under `.t1-run/`).

## Step 3 — gate-be

### Inputs
- `be_return` dict from Step 2

### Actions

- If `be_return["status"] == "success"`:
  - Print developer-facing summary:
    ```
    ✓ BE T1 passed: <tests_passed> tests, <tests_generated> auto-generated, <fix_cycles_used> fix cycle(s) used.
    ```
  - Proceed to Step 4.

- If `be_return["status"] == "failure"`:
  - Write `features/<slug>/t1-report.md` (see §Failure Handling below).
  - Flip the T1 tracker row from `in-progress` to `blocked` with a note: `BE T1 halted at <failed_class>.<failed_method>; see t1-report.md`.
  - Emit `ORCHESTRATOR_RETURN` block (see §Return Protocol) with `failed_step=gate-be`.
  - Exit cleanly. Do **not** proceed to Step 4; the test stack must not run if BE is red.

---

## Step 4 — spin-stack

### Skip if
- `--reuse-stack` flag is set, OR
- BE result from Step 3 was `failure` (already exited)

### Actions

1. **Pre-flight: confirm no port conflicts.** Check ports 5433, 9093, 8081 are free:

   ```bash
   for port in 5433 9093 8081; do
     if lsof -i ":$port" >/dev/null 2>&1; then
       echo "Port $port is in use. Stop the conflicting process or use --reuse-stack."
       exit 1
     fi
   done
   ```

   If any port is busy, HALT with the message above; do NOT proceed.

2. **Spin up the test stack.** Wrap the call in the time-pause accumulator (this is not an interactive pause, but it is long):

   ```bash
   docker compose -f ../tenxengage-backend/docker-compose.test.yml up -d --wait --wait-timeout 180
   ```

   If the command times out or returns non-zero:
   - Capture the last 50 lines of `docker compose logs backend-test`
   - Write to `features/<slug>/t1-report.md` (Stack startup failure section)
   - Run teardown (`docker compose -f ../tenxengage-backend/docker-compose.test.yml down -v`)
   - Flip T1 to `blocked` with note `T1 halted at spin-stack`
   - Emit `ORCHESTRATOR_RETURN status=failure failed_step=spin-stack failed_reason="<stderr summary>"`
   - Exit cleanly

3. **Verify health.** `curl -s --max-time 10 http://localhost:8081/actuator/health | jq -e '.status == "UP"'`

   If health is not UP after 30s of retries, treat as failure (same flow as step 2).

4. **Export env for Step 5.** Set `TEST_BACKEND_URL=http://localhost:8081` for the dispatch subagent.

### Output of Step 4
- Test stack is up; backend healthy at `http://localhost:8081`
- `TEST_BACKEND_URL` set for downstream steps

---

## Step 5 — dispatch-fe

### Inputs
- `features/<slug>/.t1-manifest.json` (read `fe.spec_files`)
- `TEST_BACKEND_URL` from Step 4

### Skip if
- `fe.spec_files` is empty (v1-degraded test-plan with no E2E section). Set `fe_return = {"status": "not-run", "tests_passed": 0, "tests_failed": 0}` and proceed to Step 6 → which will skip to Step 7.

### Actions

Construct the spec file list:
```
FE_SPECS=$(jq -r '.fe.spec_files | join(" ")' features/<slug>/.t1-manifest.json)
```

Dispatch:

```
Agent({
  description: "T1 FE dispatch",
  subagent_type: "general-purpose",
  run_in_background: false,
  prompt: """
    Run this single bash command and capture stdout:

      cd c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend && \
        TEST_BACKEND_URL=http://localhost:8081 \
          claude -p --output-format=json --model=<MODEL> \
            "/run-tests --real-backend <FE_SPECS>"

    Parse the JSON. From the 'result' field, extract every line starting
    with 'ORCHESTRATOR_RETURN ' and emit those lines verbatim. Also emit:
      INNER_SESSION_ID=<JSON 'session_id' field>
      INNER_COST_USD=<JSON 'total_cost_usd' field>

    Pure passthrough. No interpretation.
  """
})
```

Parse output into `fe_return` dict. Persist `features/<slug>/.t1-run/fe-result.json`.

## Step 6 — gate-fe

### Inputs
- `fe_return` dict from Step 5

### Actions

- If `fe_return["status"] == "success"` (or `"not-run"`):
  - Print: `✓ FE T1 passed: <tests_passed> specs, <tests_generated> auto-generated, <fix_cycles_used> fix cycle(s).`
  - Proceed to Step 7.

- If `fe_return["status"] == "failure"`:
  - Append FE failure to `features/<slug>/t1-report.md` (see §Failure Handling).
  - Flip T1 tracker row to `blocked` with note: `FE T1 halted at <failed_spec>; see t1-report.md`.
  - Proceed to Step 7 (teardown) — we still want to clean up the stack on failure.
  - After teardown, emit `ORCHESTRATOR_RETURN failed_step=gate-fe` and exit cleanly.

---

## Step 7 — teardown

### Skip if
- `--reuse-stack` flag is set

### Actions

```bash
docker compose -f ../tenxengage-backend/docker-compose.test.yml down -v
```

The `-v` removes volumes so the next T1 run starts from a clean DB. We do **not** pass `--remove-orphans` — `docker-compose.test.yml` declares `name: tenxengage-test`, which scopes this `down` to that project, but the flag was a footgun even with the project scope (it would remove anything not in this file but still in the project — including any future helper services). The test compose's own services are removed cleanly by `down` alone.

Always run this step, even if Step 5 or 6 failed. Do not leak containers between runs.

If teardown itself fails (rare):
- Print warning: `Teardown failed; manual cleanup may be required: docker ps -a`
- Do NOT halt the skill on teardown failure — proceed (or exit if we were already exiting from gate-fe failure)

---

## Step 8 — capture

### Skip if
- Either gate (Step 3 or Step 6) halted with failure. Knowledge capture only runs after green BE + green FE (or FE skipped because v1-degraded).

### Actions

1. **Collect findings from both repos.** Read the auto-fix commits made by `run-tests` in both repos for this T1 run:
   - BE: `git -C ../tenxengage-backend log --since="$SKILL_START" --pretty=format:"%h|%s" --diff-filter=ACMR --name-only`
   - FE: `git -C ../tenxengage-frontend log --since="$SKILL_START" --pretty=format:"%h|%s" --diff-filter=ACMR --name-only`
   - Plus the structured findings from `.t1-run/be-result.json` and `.t1-run/fe-result.json` (`tests_generated_files` field shows what was auto-generated).

2. **Curation gate.** For each finding (auto-fix or auto-generated test), apply the question:
   > Would a competent developer following our existing docs still make this mistake?

   If yes → promote. If no (one-off, file-specific, already documented) → skip.

3. **Tier classification.** For each promoted finding:
   - **Tier 1** (domain-specific, generalizable): append a bullet to the "Common gotchas" section of `docs/patterns/<domain>.md`. Pick the domain by matching the finding's keywords against pattern file headings. If no pattern file matches, create one and register it in `CLAUDE.md` and `PROJECT-CONTEXT.md`.
   - **Tier 2** (cross-cutting convention gap): add a single generalized line to `PROJECT-CONTEXT.md` under the relevant existing section.
   - **Tier 3** (one-off, already documented): no permanent doc write. Stays in `t1-report.md` for the run.

4. **Update learnings log.** For each Tier 1 and Tier 2 promotion, append a row to `docs/learnings.md` under a new dated section if today's section doesn't exist:

   ```markdown
   ## YYYY-MM-DD — <feature-slug>

   | Rule | Category | Applied to |
   |---|---|---|
   | <generalized rule, one line> | <domain or "cross-cutting"> | <target file path> |
   ```

   Tag the section with `T1/<slug>` either inline (e.g., `## 2026-05-18 — assessment-authoring (T1)`) or in a trailing note so we can grep for T1 contributions over time.

5. **Commit knowledge capture (single commit).**

   ```bash
   git add docs/patterns/ docs/learnings.md PROJECT-CONTEXT.md CLAUDE.md
   git commit -m "$(cat <<'EOF'
   chore(T1/<slug>): capture <N> learning(s) from cross-story integration tests

   <one-line per promoted finding>
   EOF
   )"
   ```

   If nothing was promoted (no fixes were learning-worthy), skip the commit and proceed.

### Output of Step 8
- `docs/learnings.md`, `docs/patterns/*.md`, `PROJECT-CONTEXT.md`, and/or `CLAUDE.md` updated (single commit) if any promotions occurred
- Counts collected: `tier1_promotions`, `tier2_promotions`

---

## Step 9 — flip-t1

### Gate-conditional

#### `--gate=every`

Show approval message:

```
T1 Integration Tests — Ready to Flip <slug> Tracker

BE: <be_tests_passed> tests passed, <be_auto_generated> auto-generated, <be_fix_cycles> fix cycle(s)
FE: <fe_tests_passed> specs passed, <fe_auto_generated> auto-generated, <fe_fix_cycles> fix cycle(s)
Knowledge capture: <tier1_promotions> Tier 1, <tier2_promotions> Tier 2 promotion(s)

Diff in docs/ (knowledge capture):
<output of `git diff --stat HEAD~1 -- docs/`>

Reply:
  done       — flip T1 row to done and commit
  change X   — roll back capture commits, re-run from step X (e.g., change capture)
```

Wrap the wait for reply with `HUMAN_PAUSE_TOTAL_SECS += <wait_seconds>`.

- On `done` reply → proceed with the flip below.
- On `change X` → `git reset --hard HEAD~N` to undo any uncommitted Step 8 work, then re-run from step X with `--from=<X>`.

#### `--gate in {story, ready-check, feature-end}`

Auto-flip without pause.

#### `--phase=implement`

Do NOT flip. Emit `ORCHESTRATOR_RETURN status=awaiting-approval` and exit, leaving the T1 row `in-progress`. The orchestrator (`/run-feature`) will re-dispatch with `--phase=merge` after developer approval.

### Flip actions

1. **Compute Duration.**
   ```
   NOW=$(date +%s)
   DURATION_SECS=$(( NOW - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))
   DURATION_FORMATTED=$(printf "%dh %dm" $(( DURATION_SECS / 3600 )) $(( (DURATION_SECS % 3600) / 60 )))
   ```

2. **Update tracker row.** Edit `features/<slug>/tracker.md` T1 row:
   - Status: `in-progress` → `done`
   - Commit: `<short SHA of HEAD>` (HEAD is the Step 8 capture commit, or the prior commit if Step 8 was a no-op)
   - Duration: `$DURATION_FORMATTED`
   - Completed: `$(date -u +"%Y-%m-%dT%H:%M:%SZ")`

3. **Commit tracker flip.**
   ```bash
   git add features/<slug>/tracker.md
   git commit -m "$(cat <<'EOF'
   tracker: <slug> T1 → done @ <sha>

   T1 integration tests passed:
   - BE: <be_tests_passed> tests, <be_auto_generated> auto-generated, <be_fix_cycles> auto-fixes
   - FE: <fe_tests_passed> specs, <fe_auto_generated> auto-generated, <fe_fix_cycles> auto-fixes
   - Knowledge capture: <tier1_promotions> Tier 1, <tier2_promotions> Tier 2 promotion(s)
   - Duration (Claude-active): <DURATION_FORMATTED>
   EOF
   )"
   ```

4. **Emit success return.** See §Return Protocol below.

---

## Failure Handling

### When to halt vs. fix

The fix loop lives inside `run-tests` (max 3 cycles per repo). The orchestrator does not retry — by the time Step 3 or Step 6 sees `status=failure`, `run-tests` has already exhausted its budget. The orchestrator's role is to attribute and report.

`run-tests` itself follows the aggressive auto-fix gate:

**Fix autonomously** when the failure is one of:
- Single-file mechanical change (rename, missing field, wrong status code constant)
- Test scaffold drift (Playwright selector renamed in FE, fixture missing a new field)
- Assertion mismatch where the expected value is unambiguously derivable from the spec

**Halt and surface** when the failure is:
- Design conflict between two stories (e.g., contradictory state assumptions)
- Schema change requiring a new Flyway migration
- Two equally-valid interpretations of a spec requirement
- Any failure spanning more than 3 files

### t1-report.md format

Written to `features/<slug>/t1-report.md` when the skill halts at Step 3 or Step 6.

```markdown
# T1 Report: <slug>

**Run:** <ISO timestamp>
**Halted at:** Step <N> — <step-name>
**Skill session:** <session id>
**Inner BE session:** <be_inner_session>
**Inner FE session:** <fe_inner_session>

## Failures

### BE — <failed_class>.<failed_method>
**Status:** Red after <fix_cycles_used> fix cycle(s).
**Likely-responsible story:** <story-id> (<title>)
**Diagnosis:** <one paragraph derived from failed_reason + report contents>
**Gradle report:** <report_path>

**Suggested fix:** <concrete action — file, method, change>

### FE — <failed_spec> → <failed_test>
**Status:** Red after <fix_cycles_used> fix cycle(s).
**Likely-responsible story:** <story-id> (<title>)
**Diagnosis:** <one paragraph>
**Screenshot:** <screenshot_path>
**Trace:** <trace_path>

**Suggested fix:** <concrete action>

## Re-entry

Fix the issues above on the feature branch directly (sub-branches are already
merged at this point in the pipeline). Then re-run:

  /execute-integration-tests <slug> --from=dispatch-be

To skip BE if BE was green and only FE failed:

  /execute-integration-tests <slug> --from=spin-stack
```

### Cross-story attribution heuristic

When attributing a failure to a likely-responsible story:

1. Parse the `Depends on Stories` column of the row in `test-plan.md` that contains the failed test class or spec file.
2. For each candidate story:
   - Weight by recency: most recent commit touching files owned by the story (more recent = more likely)
   - Weight by AC match: if the failed assertion text matches any AC ID's keywords in that story file, +50%
   - Weight by file overlap: if commits in that story modified files that appear in the test's import graph, +30%
3. Rank candidates by total weight. Surface the top-1 as "Likely-responsible story" with a brief weight breakdown in `t1-report.md`.

Heuristic only; developer can override. Never fail the build because attribution was wrong.

---

## Time Accounting

Conforms to v9 convention used by `/load-story` and `/execute-foundation`.

**Wall-clock** (display only):
- `SKILL_START_EPOCH` set at skill entry
- Wall time at end = `now - SKILL_START_EPOCH`

**Claude-active time** (tracker `Duration` column):
- `CLAIM_TIME_EPOCH` set when T1 row first claimed (Step 1)
- `HUMAN_PAUSE_TOTAL_SECS` accumulated across all interactive prompts after the claim:
  - Step 1 v1 upgrade prompt
  - Step 9 `--gate=every` approval pause
- `Duration = (now - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS)`, formatted `Xh Ym`

---

## Return Protocol (when dispatched by /run-feature)

Emit before exit:

```
ORCHESTRATOR_RETURN status=<success|failure|awaiting-approval>
ORCHESTRATOR_RETURN unit_id=T1
ORCHESTRATOR_RETURN be_result=<green|red|not-run>
ORCHESTRATOR_RETURN fe_result=<green|red|not-run>
ORCHESTRATOR_RETURN be_tests_passed=<N>
ORCHESTRATOR_RETURN fe_tests_passed=<N>
ORCHESTRATOR_RETURN be_auto_generated=<N>
ORCHESTRATOR_RETURN fe_auto_generated=<N>
ORCHESTRATOR_RETURN be_fix_cycles=<0..3>
ORCHESTRATOR_RETURN fe_fix_cycles=<0..3>
ORCHESTRATOR_RETURN tier1_promotions=<N>
ORCHESTRATOR_RETURN tier2_promotions=<N>
ORCHESTRATOR_RETURN summary=<2-3 sentences>
ORCHESTRATOR_RETURN duration_seconds=<int>
```

Failure-only fields:

```
ORCHESTRATOR_RETURN failed_step=<dispatch-be|gate-be|spin-stack|dispatch-fe|gate-fe>
ORCHESTRATOR_RETURN failed_reason=<short reason>
ORCHESTRATOR_RETURN report_path=features/<slug>/t1-report.md
```

`awaiting-approval` is emitted only when `--phase=implement` and the implementation completed without halt; the orchestrator will re-dispatch with `--phase=merge`.

---

## Rules

- **Never write to tracker.md from within Step 2 or Step 5.** Only Step 1 (claim) and Step 9 (flip) modify the tracker.
- **Always tear down the stack** at Step 7 unless `--reuse-stack`. No exceptions, even on failure.
- **Step 8 (capture) is atomic** — single commit per T1 run, or no commit at all.
- **Step 9 is the only place that flips T1 to `done`.** Failure at any step before Step 9 flips T1 to `blocked` instead.
- **The fix loop budget belongs to `run-tests`**, not this skill. Do not retry dispatch on failure.
- **Use `Agent` tool, not bare `Bash`, for cross-repo dispatch.** The `Agent` wrapping is what enables foreground capture, timeouts, and `INNER_SESSION_ID` extraction.
