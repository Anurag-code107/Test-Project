---
name: "run-feature"
description: "Orchestrate the implementation phase of a feature end-to-end: contracts → foundation → story-layers (BE + FE) → cross-story integration tests (T1). Dispatches each unit to its own background `claude -p` session in the appropriate sibling repo. Five-question interactive startup, four gate modes (every/story/ready-check/feature-end), per-layer stop targets, pause/peek keywords, retry-once-then-stop failure policy. Tracker is the source of truth — the orchestrator only reads it."
argument-hint: "feature-slug (e.g., `assessment-authoring`); optional flags: --gate, --tdd, --stop-be-after, --stop-fe-after, --subagent-model, --no-prompt, --only, --from, --stop-after, --dry-run, --strict-adversarial, --max-parallel"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

## Purpose

Drive a single feature's implementation phase end-to-end without per-unit human intervention (except under `--gate=every`). The orchestrator:

1. Reads the feature tracker, stories index, foundation tasks, and test plan.
2. Asks 5 interactive questions (skippable with flags).
3. Loops: read tracker → compute eligible units → dispatch background `run-unit.sh` process(es) → process return summary → repeat.
4. Honors per-layer stop targets, gate modes, retry-once-then-stop failure policy.
5. Supports `pause` (clean halt) and `peek` (status snapshot) keywords from the user.

The orchestrator NEVER writes the tracker directly — all tracker writes come from the dispatched inner `claude -p` sessions.

See the design spec: `docs/superpowers/specs/2026-05-15-run-feature-orchestrator-design.md`.

## Step 0 — Parse arguments

**Slug resolution** (precedence):
1. First non-flag token in `$ARGUMENTS` → `$SLUG`.
2. If absent, run `git branch --show-current` from the blueprint repo.
   - If branch matches `features/<name>`: `$SLUG = <name>`.
   - If branch matches `roadmaps/<name>`: list features under `tenxengage-blueprint/features/` whose `spec.md` frontmatter `roadmap: <name>` and prompt the user to pick one.
3. If neither yields a slug: list directories under `tenxengage-blueprint/features/` and ask the user to pick (use `AskUserQuestion` with up to 4 options + Other).
4. Abort if the user provides nothing.

Verify `tenxengage-blueprint/features/$SLUG/tracker.md` exists; abort otherwise.

**Flag parsing** (all optional; defaults shown):

| Flag | Variable | Default |
|---|---|---|
| `--gate=<mode>` | `$GATE` | unset (Q1 will ask) |
| `--tdd` / `--no-tdd` | `$TDD` | unset (Q2 will ask) |
| `--stop-be-after=<id\|ALL\|NONE>` | `$STOP_BE` | unset (Q3 will ask) |
| `--stop-fe-after=<id\|ALL\|NONE>` | `$STOP_FE` | unset (Q4 will ask) |
| `--subagent-model=<sonnet\|opus\|haiku>` | `$SUBAGENT_MODEL` | unset (Q5 will ask) |
| `--no-prompt` | `$NO_PROMPT` | false. If true, defaults: gate=every, tdd=off, stop-be=ALL, stop-fe=ALL, model=sonnet. |
| `--stop-after=<id>` | (shortcut) | — Sets both `$STOP_BE` and `$STOP_FE` to the same value. |
| `--only=<csv>` | `$ONLY` | empty (no filter) |
| `--from=<id>` | `$FROM` | empty (no filter) |
| `--dry-run` | `$DRY_RUN` | false |
| `--strict-adversarial` | `$STRICT_ADV` | false |
| `--max-parallel=<n>` | `$MAX_PARALLEL` | 2 (default; matches "1 BE + 1 FE" policy) |

Parse with simple regex matching on `$ARGUMENTS`. After parsing, strip flags so `$SLUG` resolution sees only positional tokens.

## Step 1 — Pre-flight checks

Run these checks in order. If any fails, abort with a clear message — do NOT proceed to the interactive questions.

### 1.1 Resolve feature paths

```bash
BLUEPRINT="c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-blueprint"
BACKEND="c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-backend"
FRONTEND="c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-frontend"
CONTRACTS="c:/Users/TenXengage/Development/TenXengage-New/source-code/tenxengage-contracts"
FEATURE_DIR="$BLUEPRINT/features/$SLUG"
```

Verify each path exists:
- `$FEATURE_DIR/tracker.md` — abort if missing.
- `$FEATURE_DIR/stories.md` — abort if missing.
- `$FEATURE_DIR/tasks/foundation.md` — abort if missing.
- `$FEATURE_DIR/test-plan.md` — warn but continue (T1 stub handles this).
- `$BACKEND`, `$FRONTEND`, `$CONTRACTS` directories — abort if any missing.

### 1.2 Idempotency Layer 1 — completion check

Read `$FEATURE_DIR/tracker.md`. Parse foundation tasks and stories (see Step 2 below for the awk pattern). Compute:
- `total_units` = count of foundation rows + count of story-layer cells (BE + FE counted separately for full-stack stories) + 1 (for T1).
- `done_units` = count of those whose Status = `done`.

If `done_units == total_units` (adjusting for the `--only` filter if set):

```
Feature "$SLUG" is already complete ($done_units / $total_units). Nothing to do.
Exiting before any question is asked.
```

Exit cleanly. **This is the first idempotency guard.**

### 1.3 Sibling repo working-state checks

For each sibling repo (`$BACKEND`, `$FRONTEND`, `$CONTRACTS`):
```bash
git -C $repo status --porcelain | head -1
```
- If empty (clean): OK.
- If non-empty: warn and ask the user — proceed (the dispatched skill will handle), commit first, or abort.
- Also check for merge-in-progress: `[ -f $repo/.git/MERGE_HEAD ]` → abort if true.

### 1.4 Contracts generated status

Read `$FEATURE_DIR/tracker.md` for the "Contracts generated:" field. If the value is `no`, schedule `/generate-contracts $SLUG` as the very first dispatch (Step 6 below). If `yes`, skip.

## Step 2 — Tracker parsing

The tracker has two markdown tables of interest: **Foundation tasks** and **Stories**. Schema may evolve — read columns by header name, never by index.

### 2.1 Find the table region

```bash
FOUNDATION_START=$(grep -n "^## Foundation tasks" $FEATURE_DIR/tracker.md | head -1 | cut -d: -f1)
STORIES_START=$(grep -n "^## Stories$" $FEATURE_DIR/tracker.md | head -1 | cut -d: -f1)
```

### 2.2 Parse a markdown table — generic awk helper

```bash
parse_md_table() {
  local file="$1"
  local start="$2"
  awk -v start="$start" '
    NR < start { next }
    NR == start { next }
    /^## / && NR > start { exit }
    /^$/ && header_seen { exit }
    /^\| *# *\|/ || /^\| *US *\|/ {
      n = split($0, hdr, "|")
      for (i = 2; i < n; i++) {
        gsub(/^ +| +$/, "", hdr[i])
        cols[i] = hdr[i]
      }
      header_seen = 1
      next
    }
    /^\|[ -|]+\|$/ { next }
    header_seen && /^\|/ {
      n = split($0, vals, "|")
      out = ""
      for (i = 2; i < n; i++) {
        gsub(/^ +| +$/, "", vals[i])
        out = out cols[i] "=" vals[i] "\t"
      }
      print out
    }
  ' "$file"
}
```

### 2.3 Foundation row schema

Expected columns: `#`, `Task`, `Status`, `Session`, `Started`, `Completed`, `Duration`, `Commit`, `Notes`.

The orchestrator uses: `#` (e.g., `F1`), `Task` (title), `Status` (`not-started` / `in-progress` / `done` / `blocked`), `Commit`.

### 2.4 Story row schema

Expected columns: `US`, `Title`, `Seed`, `Layers`, `Depends on`, `BE`, `BE Tests`, `FE`, `FE Tests`, `Mockup`, `Commit (BE)`, `Commit (FE)`, `Duration (BE)`, `Duration (FE)`, `Notes`.

The orchestrator uses: `US` (e.g., `US-01`), `Title`, `Layers` (e.g., `BE + FE`, `BE`, `FE`), `Depends on`, `BE`, `FE`.

### 2.5 Test the parser

Sanity-check against the feature (foundation should yield N rows; count must be non-zero):

```bash
parse_md_table "$FEATURE_DIR/tracker.md" "$FOUNDATION_START" | wc -l
parse_md_table "$FEATURE_DIR/tracker.md" "$STORIES_START" | head -1
```

## Step 3 — Compute eligible units

### 3.1 Build the unit list

A "unit" is one of:
- `F1` ... `FN` — foundation task (status from foundation table).
- `US-NN-BE` — backend layer of a story (status from story row's `BE` column).
- `US-NN-FE` — frontend layer of a story (status from story row's `FE` column).
- `T1` — cross-story integration tests.

For each story row, expand:
- If `Layers` includes `BE`, emit a `US-NN-BE` unit with status from `BE` column.
- If `Layers` includes `FE`, emit a `US-NN-FE` unit with status from `FE` column.

Foundation-table rows produce `F1`..`FN` units directly.

### 3.2 Dependency model

- Foundation: sequential (`F2` depends on `F1`, etc.).
- Stories: `Depends on` column of the tracker is the authority. Dependency resolution is **layer-aware**:
  - `Foundation` → all of F1..FN must be `done` (same rule for BE and FE).
  - `F5` → that specific foundation task `done` (same rule for BE and FE).
  - `US-NN` (prior story reference) → **BE eligibility**: only `US-NN-BE` must be `done`. **FE eligibility**: both `US-NN-BE` AND `US-NN-FE` must be `done`.
- FE layer additionally depends on **`US-NN-BE`** being `done` when the same story has both layers (FE consumes BE's real endpoints; tracker note: "FE `done` = wired to real BE + E2E passes").

**Key invariant:** a BE unit never waits for any FE unit to finish. Only `US-NN-BE` → `US-NN-FE` is a valid same-story ordering constraint. Cross-story deps on prior FE work never block a BE layer.

  **Note on scaffold-and-wait:** The FE `load-story` skill supports a scaffold-and-wait mode (its Step 8) for manual invocations where BE is still in flight. The orchestrator does NOT exploit this: it dispatches FE only after BE is `done`, keeping dispatch logic simple and avoiding the complexity of tracking two half-states for the same story. Manual `/load-story` still supports scaffold-and-wait.
- `T1` depends on every story's last applicable layer being `done`.

### 3.3 Eligibility predicate

A unit is eligible iff:
- `status == not-started`, AND
- all dependencies have `status == done`, AND
- not excluded by `--only` filter (if set), AND
- after `--from` (if set, skip units in natural order before `--from`), AND
- not past its per-layer stop point (see 3.4).

### 3.4 Per-layer stop point

`$STOP_BE` values:
- `ALL` (default) — no halt; all BE units eligible by other criteria.
- `NONE` — no BE unit is ever eligible.
- `<id>` (e.g., `US-04`, `F3`) — once that named unit completes on the BE layer, `$BE_STOPPED = true`; no further BE units dispatched.

Same logic for `$STOP_FE` / `$FE_STOPPED`.

Foundation tasks ignore stop flags. T1 also ignores stop flags.

### 3.5 Pick-next strategy

Given the eligible set:
- `$GATE == every`: pick **1** unit (sequential).
- Otherwise: dispatch up to `$MAX_PARALLEL` units (default 2), with at most one BE-layer and one FE-layer background dispatch in flight at any time. Foundation tasks dispatch sequentially (one in flight max). Contracts and T1 are singletons.

## Step 4 — Interactive five-question startup

For each variable unset after Step 0 flag parsing, ask the corresponding question using `AskUserQuestion`. Skip if already set. If `$NO_PROMPT == true`, skip all and use defaults.

### Q1 — Gate mode (if `$GATE` unset)

Question: "Q1 — Gate mode. Controls when the orchestrator pauses for your approval between units."

Options:
1. `every` — pause for approval before merging every unit (pre-merge)
2. `story` — auto-merge each unit; pause after each completed user story
3. `ready-check` — auto-merge on green; halt only on strict-stage failure
4. `feature-end` — auto-merge all; review the whole feature branch at the end

Store answer in `$GATE`.

### Q2 — TDD discipline (if `$TDD` unset)

Question: "Q2 — TDD discipline. Whether dispatched skills enforce Red/Green/Refactor."

Options:
1. Off (default) — implement per the story's task order
2. On — pass `--tdd` to every dispatched skill

Store `false`/`true` in `$TDD`.

### Q3 — BE stop point (if `$STOP_BE` unset)

Question: "Q3 — Backend layer stop point. Halt point for backend-side work."

Options:
1. Run all BE units (sets `ALL`)
2. Stop BE after a specific story (user picks Other and types a unit ID)
3. Skip BE entirely (sets `NONE`)

Store in `$STOP_BE`.

### Q4 — FE stop point (if `$STOP_FE` unset)

Same shape as Q3. Store in `$STOP_FE`.

### Q5 — Subagent model (if `$SUBAGENT_MODEL` unset)

Question: "Q5 — Subagent model. Model used by the inner `claude -p` session inside each dispatched `run-unit.sh` process."

Options:
1. `sonnet` — Sonnet 4.6 (recommended; capable + cost-effective)
2. `opus` — Opus 4.7 (max quality; most expensive)
3. `haiku` — Haiku 4.5 (fastest & cheapest)

Store in `$SUBAGENT_MODEL`.

## Step 5 — Plan summary + confirmation

Compute and emit a one-screen plan before any dispatch. Use Step 2/Step 3 logic to gather completion counts, eligible units, and stories breakdown.

Emit this block:

```
Plan for $SLUG
─────────────────────────────
Gate:               $GATE
TDD:                $TDD
BE stop:            $STOP_BE
FE stop:            $STOP_FE
Subagent model:     $SUBAGENT_MODEL
Strict adversarial: ${STRICT_ADV:-no}
Contracts:          [already generated, skip | pending — will dispatch /generate-contracts first]
Foundation:         [N/M done; will dispatch FK..FN | all done, skip]
Stories:            [count] stories — [breakdown]
Eligible now:       [comma-separated unit IDs]
Pause keyword:      `pause`  · Status keyword: `peek`

Proceed?  [yes / no]
```

If `$DRY_RUN == true`: also emit the full planned dispatch order, then exit cleanly without dispatching.

If `$NO_PROMPT == false`: wait for user reply. Accept `yes`, `y`, `proceed`. Anything else → abort.

If `$NO_PROMPT == true`: proceed without asking.

**Warning under `--gate=feature-end`:** before the `Proceed?` prompt, emit:
```
WARNING: feature-end mode auto-merges every unit on green ready-check.
A bad unit can poison later units before you see it. Recommended unattended
mode is ready-check or story. Continue only if you intend feature-end.
```

## Step 6 — Dispatch via direct background Bash

Every dispatch launches `tenxengage-blueprint/.claude/skills/run-feature/run-unit.sh`
**directly** as a background Bash process from the orchestrator turn (Bash tool,
`run_in_background=true`). The orchestrator captures the `shell_id`, ends its
turn, and the harness re-invokes it when that background shell exits. The
orchestrator then reads the wrapper's pre-extracted `.result.json` inline and
parses the `ORCHESTRATOR_RETURN` lines itself (Step 8).

> **Do NOT wrap the dispatch in an Agent.** An earlier design used an
> intermediary "courier" subagent (`Agent(run_in_background=true)` whose only job
> was to launch `run-unit.sh` and report back). That pattern is broken: the
> harness fires the Agent "completed" notification when the courier ends its
> first turn — **not** when the inner `claude -p` shell exits — so the courier
> reports a spurious failure while the real work is still running, and can never
> relay `ORCHESTRATOR_RETURN`. Launch `run-unit.sh` as a background **Bash** call
> instead: a background Bash shell's exit *does* re-invoke the orchestrator
> correctly.

**Why spawn `claude -p` at all:** skills in sibling repos (`/load-story` in
`tenxengage-backend/.claude/skills/`, etc.) are not discoverable from the
orchestrator rooted in `tenxengage-blueprint`. Spawning `claude -p` inside the
sibling repo re-bootstraps that repo's full context (its CLAUDE.md, its
.claude/skills/, its settings, its hooks) so the slash command runs in its
native environment. `run-unit.sh` handles the spawning + monitoring plumbing and
escapes the Bash tool's 600s ceiling by running in the background — see the spec
at
[docs/superpowers/specs/2026-05-22-run-feature-shim-timeout-retry-fix-design.md](../../../docs/superpowers/specs/2026-05-22-run-feature-shim-timeout-retry-fix-design.md).

The wrapper writes deterministic files under `/tmp/run-feature-<SLUG>-<UNIT_ID>.*`:

```
BASE=/tmp/run-feature-<SLUG>-<UNIT_ID>
DONE_FILE=${BASE}.done            # rc=<n> unit=<UNIT_ID> sentinel (written last)
RESULT_FILE=${BASE}.result.json   # pre-extracted terminal result event
SESSION_ID_FILE=${BASE}.session_id
TRANSCRIPT=${BASE}.transcript     # human-readable; tail -f for live progress
```

The orchestrator's read-and-parse logic on shell exit lives in Step 8.

**Placeholders the orchestrator fills:**

| Placeholder | Source | Notes |
|---|---|---|
| `<UNIT_ID>` | The unit being dispatched (e.g., `US-01-BE`, `F3`, `T1`) | — |
| `<SLUG>` | `$SLUG` | Used in deterministic `/tmp` paths. |
| `<REPO_ABS_PATH>` | One of `$BACKEND`, `$FRONTEND`, `$CONTRACTS`, `$BLUEPRINT` | Sibling repo where the slash command should run. |
| `<SUBAGENT_MODEL>` | `$SUBAGENT_MODEL` (Q5 answer: `sonnet` / `opus` / `haiku`) | Passed as `run-unit.sh`'s 2nd arg; routes to the **inner** `claude -p` via `--model=`. |
| `<BLUEPRINT>` | `$BLUEPRINT` | Absolute path to tenxengage-blueprint repo (for resolving the wrapper script). |
| `<SLASH_COMMAND>` | Per unit kind (table below) | Unchanged from prior dispatch logic. |

The wrapper script's 90-min hard timeout (`HARD_TIMEOUT_SECS=5400`) is uniform across unit kinds. Story-layers normally take 10–25 min; foundation tasks 3–8 min. A unit hitting 90 min indicates a hang, not a slow legitimate run — the developer should investigate by tailing `/tmp/run-feature-<SLUG>-<UNIT_ID>.transcript`, not by raising the ceiling. No per-unit-kind override exists.

**`<SLASH_COMMAND>` per unit kind (unchanged from prior dispatch logic):**

- Foundation `FK`: `/execute-foundation $SLUG FK --gate=$GATE [--phase=implement] [--soft-stages=adversarial-review] [--tdd]`
- Story BE `US-NN-BE`: `/load-story $SLUG US-NN --gate=$GATE [--phase=implement] [--soft-stages=adversarial-review] [--tdd]` (in `$BACKEND`)
- Story FE `US-NN-FE`: `/load-story $SLUG US-NN --gate=$GATE [--phase=implement] [--soft-stages=adversarial-review] [--tdd]` (in `$FRONTEND`)
- Contracts: `/generate-contracts $SLUG` (in `$CONTRACTS`)
- T1: `/execute-integration-tests $SLUG --gate=$GATE` (in `$BLUEPRINT`)

**Conditional flags on `<SLASH_COMMAND>` (unchanged):**

- `--soft-stages=adversarial-review` included by default. Omit if `$STRICT_ADV == true`.
- `--phase=implement` set only when `$GATE == every` (orchestrator intercepts before merge, shows diff, waits for approval, then dispatches a second background Bash run with `--phase=merge`). Otherwise omit — the inner skill inlines both phases based on `$GATE`.
- When dispatching `--phase=merge` (second dispatch under `$GATE == every`): the inner skill starts cold but the sub-branch already exists on disk from the implement phase. The skill's merge-phase routing jumps directly to squash-merge against the existing sub-branch tip. Two fresh inner sessions in series is fine — the sub-branch on disk is the shared state, not the in-memory session.
- `--tdd` included only when `$TDD == true`.
- `--instruction="<text>"` appended when `phase=revise` (passed through to inner `/load-story --instruction=<text>`). Quote properly when interpolating into the command string.

**Dispatch via Bash tool with `run_in_background=true`:**

```
Bash(
  command="bash <BLUEPRINT>/.claude/skills/run-feature/run-unit.sh \
           '<REPO_ABS_PATH>' '<SUBAGENT_MODEL>' '<SLUG>' '<UNIT_ID>' \
           '<SLASH_COMMAND>'",
  run_in_background=true,
  description="dispatch <UNIT_ID>"
)
```

Capture the `shell_id` from the result and record in the `dispatched` map:
`{unit_id: {start_epoch, repo, phase, shell_id, inner_session_id: null}}`. The
`inner_session_id` field is populated when the dispatch shell exits, read
directly from `.result.json` (`.session_id`) — see Step 8.1.

## Step 7 — Main dispatch loop

Initialize before entering loop:

```
dispatched = {}         # map unit_id → {start_epoch, repo, phase, shell_id, inner_session_id}
advisory_findings = []  # accumulated soft-stage findings
done_this_run = []      # unit_ids completed this session
pause_requested = false

be_stopped = false      # set when $STOP_BE unit completes
fe_stopped = false      # set when $STOP_FE unit completes
be_halted = false       # set when a BE-layer unit FAILS
fe_halted = false       # set when an FE-layer unit FAILS
global_halted = false   # set when foundation, contracts, or T1 fails
inner_cost_total = 0.0   # accumulated .total_cost_usd across this run; emitted in Step 10

run_start_epoch = $(date +%s)
```

**Note:** There is no retry set. Failures halt the affected layer immediately. The orchestrator does not retry failed units — it prints helper-text and lets the developer fix and re-invoke.

**Loop:**

```
loop:
  if pause_requested and dispatched is empty:
    emit_pause_summary(); exit 0

  eligible = compute_eligible_units()  # respects be_halted/fe_halted/global_halted

  if eligible is empty:
    if all in-scope units are done:
      emit_success_summary(); exit 0
    if dispatched is empty:
      if be_halted or fe_halted or global_halted:
        emit_halt_exit_summary()  # mentions halted layer(s); helper-text was already printed
      else:
        emit_blocked_summary()    # genuine dep deadlock
      exit 0
    await_next_event()
    handle_event()
    continue

  if pause_requested:
    await_next_event(); handle_event(); continue

  next_batch = pick_next(eligible)
  for unit in next_batch:
    # TOCTOU re-check: skip for merge phase (row is legitimately in-progress at merge time)
    if phase != "merge":
      current_status = read_unit_status_from_tracker(unit.id)
      if current_status != "not-started": continue

    phase = "implement" if $GATE == "every" else null
    dispatch_unit(unit, phase)  # Step 6 — background Bash launch of run-unit.sh

  emit_progress_line()
  # e.g.
  #   [15:42:03] dispatched US-01-BE
  #              tail -f /tmp/run-feature-course-authoring-US-01-BE.transcript
  #              (in-flight: 2)
  await_next_event()
  handle_event()
```

`await_next_event()` ends the current response turn. The runtime resumes when a background dispatch shell exits or the user sends a message.

`handle_event()`:
- Background dispatch shell exit → call `handle_unit_return(unit)` (Step 8).
- User typed `pause` → set `pause_requested = true`.
- User typed `peek` → emit status snapshot (Step 9); do not halt.
- User typed `merge` and awaiting `--gate=every` approval → handle approval (Step 8.3).
- User typed `change <text>` and awaiting approval → dispatch revise phase (Step 8.3).
- Any other message → respond briefly; do not halt.

## Step 8 — Dispatch return handling

When a dispatched background shell exits, the harness re-invokes the
orchestrator. The orchestrator reads the wrapper's output files inline (using the
Read tool, never `Bash(cat …)`) and parses the result itself — there is no
courier; this is the logic the courier used to perform:

```
BASE=/tmp/run-feature-<SLUG>-<UNIT_ID>

# a) Read BASE.done → single line `rc=<n> unit=<UNIT_ID>`. Parse the int after rc=.
#    rc != 0  → status=failure, error=wrapper-rc-<rc>
#               (read BASE.session_id if present → inner_session_id). Go to fail path.
# b) Read BASE.result.json → parse JSON. Unreadable/empty → status=failure,
#    error=result-file-unreadable. Go to fail path.
# c) Extract from the JSON:
#       .result          → text of the inner session
#       .session_id      → inner_session_id
#       .total_cost_usd  → inner cost (accumulate into inner_cost_total)
# d) Scan .result for every line matching exactly: ^ORCHESTRATOR_RETURN .+$
#    Zero matches → status=failure, error=no-orchestrator-return-emitted.
#    Otherwise parse those lines into the result dict (see below).
```

Parse the matched lines with `^ORCHESTRATOR_RETURN ([a-z_]+)=(.*)$` into a dict.

### 8.1 Universal handling (all gates)

`inner_session_id` and the inner cost come straight from the parsed
`.result.json` (step c above), not from any informational line:

- `inner_session_id` (from `.session_id`) — record in `dispatched[unit_id].inner_session_id`. Used by Step 8.5 failure helper-text and by Step 10's final summary for failed units.
- inner cost (from `.total_cost_usd`) — accumulate into `inner_cost_total` and emit in Step 10's summary.

Then:

```
dispatched.remove(unit_id)

if status == "failure":
  layer = derive_layer(unit_id)
  # F<n> or contracts or T1 → global_halted = true
  # US-NN-BE → be_halted = true
  # US-NN-FE → fe_halted = true

  emit_failure_helper_text(unit_id, layer, result)  # see 8.5
  return  # in-flight dispatches on the OTHER layer continue

# Success path: collect advisory findings
if advisory_findings_path != "none":
  advisory_findings.extend(read_json(advisory_findings_path))
```

### 8.2a Eligibility-time exclusion

Exclude from eligibility:
- All BE units when `be_halted == true` OR `global_halted == true`.
- All FE units when `fe_halted == true` OR `global_halted == true`.
- All foundation/contracts/T1 when `global_halted == true`.

### 8.3 Gate-specific handling

**`$GATE == every` (pre-merge approval flow):**

```
if phase == "implement":
  print "Unit <unit_id> implement-phase complete."
  print "Diff stat: <diff_stat>"
  print "Ready-check: <ready_check>  Advisory findings: <count>"
  print "Summary: <summary>"
  print "Reply 'merge' to squash-merge, or 'change <text>' to revise."
  await_user_reply()
  if reply == "merge":
    dispatch_unit(unit_id, phase="merge"); return
  if reply.startswith("change "):
    dispatch_unit(unit_id, phase="revise", instruction=reply.removeprefix("change ").strip()); return
  # Unrecognized — re-prompt

if phase == "merge":
  done_this_run.append(unit_id)
  if unit_id ends with "-BE" and matches $STOP_BE: be_stopped = true
  if unit_id ends with "-FE" and matches $STOP_FE: fe_stopped = true
```

**`$GATE in {story, ready-check, feature-end}` (auto-merge inlined):**

```
done_this_run.append(unit_id)
if unit_id ends with "-BE" and matches $STOP_BE: be_stopped = true
if unit_id ends with "-FE" and matches $STOP_FE: fe_stopped = true

# Story-boundary detection (gate == story only)
if $GATE == "story":
  story_id = extract_story_id(unit_id)
  if is_last_applicable_layer_done(story_id):
    print_story_complete_summary(story_id)
    await_user_reply()
    if reply == "pause": pause_requested = true
    # else (yes/continue/any) — keep going
```

`is_last_applicable_layer_done(story_id)`: complete when the last applicable layer's cell is `done` (FE for BE+FE stories, BE for BE-only, FE for FE-only).

### 8.4 Story-complete summary template

```
Story <story_id> (<title>) complete
─────────────────────────────────────
BE:  done in <dur> · commit <sha>
FE:  done in <dur> · commit <sha>
Tests: BE <green/red>, FE <green/red>, E2E <green/red>
Advisory findings in this story: <count>

Continue with next story? [yes / pause]
```

### 8.5 Failure helper-text

```
HALT — <BE|FE|GLOBAL> layer halted on <unit_id> failure

  Failed stage:    <failed_stage>
  Reason:          <failed_reason>
  Sub-branch:      <branch>  (in <repo_abs_path>)
  Inner session:   <inner_session_id>  (resume with: claude --resume <inner_session_id> from <repo_abs_path>)
  Full report:     <findings_path>

  [IF layer == BE or FE]
  <other-layer> layer is NOT halted — it continues. Current state:
    In-flight:                      <list of other-layer in-flight units with elapsed>
    Eligible after current finish:  <list of other-layer eligible units>

  What to do next:

    1. Open Claude Code in the affected repo, switch to the sub-branch, and fix:
         cd <repo_abs_path> && git checkout <branch>
       Inspect: <findings_path>

    2. Once green, finalize merge + tracker flip:
         /finish-unit <slug> <unit_id>

    3. Re-run the orchestrator with the same flags:
         /run-feature <slug> --gate=<$GATE> [--tdd if $TDD] --stop-be-after=<$STOP_BE> --stop-fe-after=<$STOP_FE> --subagent-model=<$SUBAGENT_MODEL>

  [IF layer == BE or FE]
  If you want the <other-layer> to also stop, type `pause` now.
```

If `inner_session_id` is null (`.result.json` had no `.session_id` and no `.session_id` file was written — i.e. `run-unit.sh` never reached the early session-id write, or the dispatch failed before the inner session started), substitute `<inner_session_id>` with the literal string `unknown (inner session never reported its ID)` and omit the `resume with:` parenthetical.

## Step 9 — Pause and peek keywords

### 9.1 `pause`

When user message is exactly `pause` (case-insensitive, trimmed):
- Set `pause_requested = true`.
- No new dispatches.
- In-flight dispatches continue; their completions still drive the loop.
- When `dispatched` is empty, emit `emit_pause_summary()` and exit.

```
Paused — orchestrator halted at <timestamp>.

Completed this run (<N>):
  <list of unit_ids with elapsed time>

In flight at pause-time (now finished):
  <list with final status>

Queued (not dispatched):
  <list>

Advisory findings: <count> total

To resume: re-run `/run-feature $SLUG` with the same flags.
The tracker is the source of truth — re-invocation picks up exactly where it left off.
```

### 9.2 `peek`

When user message is exactly `peek` (case-insensitive, trimmed):
- Emit a snapshot from in-memory state only. Do NOT halt; do NOT change `pause_requested`.

```
[orchestrator status — peek at <HH:MM:SS>]
─────────────────────────────────────
In flight:
  • <unit_id>  · running · <elapsed>  · <repo>  · phase=<phase>
Done this run (<N>):  <comma-separated>
Queued (next eligible after current finish):
  • <list>
Blocked: <list or "none">
Advisory findings: <count> total
  latest: <unit_id> <stage> — "<finding summary>"
Gate: $GATE · TDD: $TDD · stop-be: $STOP_BE · stop-fe: $STOP_FE · model: $SUBAGENT_MODEL
```

### 9.3 Other keywords

- `merge` / `change <text>` — only meaningful during `--gate=every` approval wait.
- `yes` / `y` / `continue` — only meaningful at a `story`-mode boundary or `Proceed?` prompt.
- Any other message — respond briefly; do not halt the run.

## Step 10 — Final summary on clean exit

```
Run for $SLUG — <success | partial | paused>
─────────────────────────────────────────────
Wall time:    <elapsed since run_start_epoch>
Inner cost:   <total inner cost in USD across all dispatches this run, formatted as $X.XX>
Gate:         $GATE
Done:         <N> units (<list with durations>)
Blocked:      <M> units (with reasons)
Not started:  <K> units (with why-not)

Advisory findings (<total>):
  US-01-BE:
    [adversarial] <finding summary 1>
  US-05-BE:
    [adversarial] <finding summary>
  ...

Next steps:
  - Review the advisory list above; file follow-up tasks if any warrant action.
  - When tracker is all-green, open the BE feature PR (manual): in $BACKEND, gh pr create ...
  - When tracker is all-green, open the FE feature PR (manual): in $FRONTEND, gh pr create ...
```

For paused/partial runs, replace "Next steps" with the resume instruction.

## Step 11 — T1 dispatch (stub)

`/execute-integration-tests` is a planned skill; its internals are out of scope for this release.

When T1 becomes the only remaining unit (all stories `done`, T1 `not-started`):

- Emit:
  ```
  T1 (cross-story integration tests) is the only remaining unit, but the
  /execute-integration-tests skill has not yet been built.

  Halting cleanly. Stories are complete; T1 must be run by hand for now.

  To unblock the orchestrator's T1 path, design and implement the
  /execute-integration-tests skill (separate spec + plan).
  ```
- Mark T1 as `blocked` in in-memory state with reason "skill-not-implemented". Do NOT write to tracker.
- Emit the final summary (Step 10) with T1 listed under "Not started" with the why-not reason.
- Exit cleanly.

When `/execute-integration-tests` ships, replace this section with normal dispatch via the Step 6 template.
