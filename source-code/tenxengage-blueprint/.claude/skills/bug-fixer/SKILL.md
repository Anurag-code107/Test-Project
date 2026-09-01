---
name: bug-fixer
description: Mode-aware bug fix loop. Consumes a bug (ClickUp ticket, inline text, or evidence folder) and produces per-repo MRs + learnings. Use when the user says "fix bug", "fix clickup <id>", "fix next bug", or "work on clickup bug".
---

# Bug Fixer Skill

End-to-end bug fix: normalize input → reproduce → failing test → fix → ready-check → visual check → MRs → learnings → structured report.

## Invocations

```
/bug-fixer                                 # scan pending evidence folders first, then oldest ClickUp bug
/bug-fixer <clickup-task-id>              # specific ClickUp task (M3)
/bug-fixer --evidence <slug>              # evidence folder (M2 or M3 depending on meta.md)
/bug-fixer --inline "<description>"       # inline on current branch (M1)
/bug-fixer --standalone "<description>"   # separate branch, no ticket (M2)
/bug-fixer --mode=M1|M2|M3 ...           # force a specific mode
/bug-fixer --wont-fix <slug-or-id> "reason"  # close as wont-fix; updates meta.md + ClickUp (no fix work)
```

## Shared contracts

All constants, API patterns, and status vocabulary live in `.claude/skills/_bug-shared/`. Read those files before acting:

- `clickup-lifecycle.md` — **ONLY source of truth** for status names and comment templates
- `clickup-client.md` — all ClickUp API call patterns
- `classify-repos.md` — repo classification signals
- `evidence-schema.md` — meta.md structure and concurrency rules
- `redaction.md` — what to redact before external writes
- `payload-schema.md` — canonical ticket shape

---

## Required sub-skills

This skill invokes one superpowers skill — do not skip it to save time:

| Step | Skill | Purpose |
|---|---|---|
| Step 5.5 (before creating MRs) | `superpowers:verification-before-completion` | Evidence gate — proves fix works before claiming done |

Cause investigation discipline is enforced inline by Phase 0.4.5 (declare critical path) and Phase 2.0 (hypothesis-driven exploration on that path). It does not require a separate skill invocation.

---

## Modes

| | **M1 Inline** | **M2 Standalone** | **M3 Tracked** |
|---|---|---|---|
| Trigger | `--inline` flag, or `--mode=M1` | `--standalone` flag, or `--mode=M2` | ClickUp ID, `--evidence` with ticket set, or `--mode=M3` |
| Git isolation | None — dev's current checkout | Worktree at `/tmp/bugfix-<slug>-<repo>/` | Same as M2 |
| Branch | No new branch | `bug/<slug>` off base | `bug/clickup-<id>-<slug>` off base |
| MRs | No | Yes — one per affected repo | Yes — one per affected repo, links ClickUp |
| ClickUp updates | No | No | Yes (status + comments per lifecycle) |
| Evidence folder | Update if `--evidence` used | Same | Same |
| `bugs-index.md` | Yes | Yes | Yes |
| Learnings (3-tier) | Yes | Yes | Yes |

### Mode auto-detection

```
/bug-fixer <clickup-id>              → M3
/bug-fixer                           → scan pending evidence folders, then oldest ClickUp → M3
/bug-fixer --evidence <slug>         → M3 if meta.md has ticket: set, else M2
/bug-fixer --inline "<text>"         → M1
/bug-fixer --standalone "<text>"     → M2
/bug-fixer "<text>" (no flag)        → M1 if on features/*, M2 if on main, else ask
```

---

## Step 0 — Normalize input

**This step is robust to malformed input.** Poorly-formed ticket, inline text, evidence folder with gaps — all are acceptable. Reconstruct missing fields from context; never abort because of format issues.

### Phase 0.1 — Read evidence inputs

Read: the ClickUp task (title + description + comments + attachments) OR the evidence folder (`meta.md` + files) OR the inline text. Use `clickup-client.md` API patterns.

Collect raw material for the canonical model:
- Title (rewritten cleanly)
- Reproduction steps
- Expected vs. observed
- Base branch (resolution order: `--base` flag → ClickUp `base_branch` field → evidence `base-branch:` → `main`)
- Evidence inventory (screenshots, stack traces, console logs, network HAR)

Do not classify affected repos yet — that happens in Phase 0.4 after signals are gathered.

---

### Phase 0.2 — Interpret screenshots

When screenshots exist in the evidence, identify and record each badge type **separately** before drawing any conclusion:

| Badge type | Example | Meaning | Must NOT be confused with |
|---|---|---|---|
| Filter/facet state badge | `Status (4)` | 4 filter options currently selected | Number of resources that exist |
| Data count badge | `Sales Incentives 0` | Actual item count in the list | Filter state |
| Empty state message | `No sales incentives found` | Zero items match the current view | A backend error |
| Pagination indicator | `Showing 12 of 47` | Real data volume | — |
| Loading spinner | — | Data not yet fetched | Empty data |

**Hard rule:** a filter badge number is never used to infer how many resources exist or should appear.

Always record filter state and data state separately:

```
Filter state: 4 status filters active (DRAFT, PENDING_APPROVAL, DENIED, ACTIVE)
Data state:   Sales Incentives section — 0 items, empty state message visible
```

If no screenshots exist, skip this phase silently.

---

### Phase 0.3 — Analyze network evidence

**Sources (check in order):**
1. `network.har` in the evidence folder
2. HAR attachment on the ClickUp ticket (download via attachment API if M3)

If neither is found → skip this phase silently and proceed to Phase 0.4.

**When a HAR is found:**

1. Identify calls relevant to the reported symptom — match by the screen URL (`meta.md screen.url`) and keywords from the bug title/description (e.g., "incentives not listed" → find calls to `/incentives`)
2. For each matched call, read the **response body**, not just the status code
3. Apply this decision table to form a *starting hypothesis*:

| Response pattern | Interpretation | Starting hypothesis |
|---|---|---|
| 2xx + empty collection (`data:[]`, `totalElements:0`, `items:[]`) | Backend not returning expected data | investigate backend first |
| 2xx + correct-looking populated data | Data layer working; issue likely in rendering | investigate frontend first |
| 4xx or 5xx | Backend returned an error | investigate backend first — but check if frontend sent bad params |
| Expected call absent from HAR entirely | Component not making the request | investigate frontend first |
| CORS / network failure | Backend CORS config or infrastructure | investigate backend first |

**Critical nuance:** these are starting directions, not locked classifications. The root cause may still be in the other repo. Examples:
- Backend 500 — could be the frontend sending malformed or missing params
- Backend 200 + empty list — could be the frontend passing wrong filters or tenant ID
- Missing call in HAR — could be a backend route not registered rather than a FE bug

When network evidence conflicts with `classify-repos.md` signals, note the conflict explicitly in the normalization block and list both hypotheses. The dev corrects the direction at "go" if needed.

---

### Phase 0.3.5 — Log Triage

**Only runs when `console.log` or `network.har` are present in the evidence.** If neither exists, skip silently.

Classify every console log entry and every network HAR entry into one of three buckets using the four signals below. Run this before Phase 0.4 so the canonical model and hypothesis are built only from relevant evidence.

#### Classification signals

| Signal | What it checks |
|---|---|
| **Entity path match** | Does the URL/resource path (or error message) touch the primary entity of the reported bug? Derive the primary entity from `screen.url` / `screen.route-pattern` in `meta.md` when available; for `--inline` / `--standalone` runs with no `meta.md`, extract entity keywords from the bug description text. Example: "incentives page shows empty" → primary entity = `/incentives`. |
| **Error pattern match** | Does this entry share the same HTTP status category (4xx / 5xx) or error message type as the primary symptom described in the bug title? |
| **User/tenant context** | Was this error logged under the same user session and tenant? Guards against multi-tab scenarios where a different session's errors appear in the window. |
| **Temporal proximity** | Does this entry fall within the session recording window declared in the evidence metadata? Secondary check — timestamp-scoped capture already handles most of this. If no session window is declared in the evidence, skip this signal. |

#### Bucket assignment rules

| Bucket | Rule | Action |
|---|---|---|
| **Relevant** | Entity path matches AND (error pattern matches OR temporal proximity confirms same window) | Include in Phase 0.4 canonical model; investigate fully |
| **Possibly Related** | No entity path match BUT error pattern matches (same HTTP status category or same error type) | Note the link in triage block; do NOT chase unless primary fix reveals the connection |
| **Unrelated** | No entity path match AND no error pattern match | Queue for stub evidence folder; list in final report |

#### Clustering unrelated entries

Group unrelated entries into clusters before stub creation:
- **Network errors:** group by first API segment after `/api/v1/` (e.g., all `/api/v1/notifications/*` errors → one cluster)
- **Console-only errors:** group by component/module prefix in the error message (text before the first colon, parenthesis, or `" at "` stack trace marker)
- Each cluster gets a slug: `<entity>-<verb>-<status>` for network (e.g., `notifications-mark-read-500`) or `<component>-<error-type>` for console-only (e.g., `course-card-react-key-warning`)
- Cap at 5 clusters maximum; if more exist, note the overflow count

#### Triage block — print immediately after classification

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
LOG TRIAGE — Phase 0.3.5
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Reported context: <screen-name or page title> → <primary entity path>

✅ RELEVANT (investigating)
  [<timestamp>] <level>  <status>  <method> <url>          ← network entries
  [<timestamp>] <level>  <message text>                    ← console-only entries
  ...

⚠️  POSSIBLY RELATED (same error pattern, different feature — noting, not chasing)
  [<timestamp>] <level>  <status>  <method> <url>          ← network entries
  [<timestamp>] <level>  <message text>                    ← console-only entries
  Reason: <why it matches error pattern but not entity>
  ...

🔕 UNRELATED (different entity + pattern — stub evidence folder will be created)
  Cluster N · <cluster-slug>
    [<timestamp>] <level>  <status>  <method> <url>          ← network entries
    [<timestamp>] <level>  <message text>                    ← console-only entries
  ...

⚠️  <N> unrelated error cluster(s) found. Stub evidence folders will be created
    after Phase 0 — investigate separately when ready.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Edge cases:**
- All entries are relevant → print the single line `All log entries relevant — no triage needed.` (no bordered block) and continue to Phase 0.4
- No log entries → skip Phase 0.3.5 entirely (silent)
- No unrelated entries → omit the 🔕 section and the warning line
- No possibly-related entries → omit the ⚠️ POSSIBLY RELATED section entirely
- More than 5 unrelated clusters → create stubs for the first 5; append `"(N more unrelated entries omitted — check raw logs)"` to the warning line

Store the queued unrelated clusters in session memory for Phase 0.5.1 (runs after dev says `go`).

---

### Phase 0.4 — Build canonical model

Using signals from Phases 0.2, 0.3, and 0.3.5, build the canonical model and determine `affected-repos`. Only **Relevant**-bucketed entries from Phase 0.3.5 feed the hypothesis — Possibly Related and Unrelated entries are excluded.

**Signal priority for `affected-repos`:**
1. **Network evidence** (Phase 0.3 + 0.3.5 Relevant bucket) — strongest signal; use only Relevant-bucketed HAR entries as the initial classification, with explicit reasoning
2. **Screenshot data state** (Phase 0.2) — secondary signal; corroborates or conflicts with network evidence
3. **`classify-repos.md` signals** — fallback only when Phases 0.2 and 0.3 produce no usable signal or conflict without resolution

When signals conflict, list both repos as candidates and explain the conflict. Do not silently pick one.

---

### Phase 0.4.5 — Declare critical path

Before any code exploration begins, trace the full data path the user's action flows through to produce the symptom. Write it out explicitly.

**Template:**
```
Critical path:
  [UI page / component]
  → [hook or state manager]
  → [service function]
  → [HTTP method + path]
  → [Controller method]
  → [Service method]
  → [Repository method / DB query]
```

This path is the **scope boundary** for all exploration in Steps 2–4. Three rules follow from it:

1. **On-path-only reads:** Every file you read must be on this path OR be a file that changed on the current branch and directly intersects this path. Before reading any file, complete: *"I'm reading `<file>` because it is on the critical path as `<role>`."* If you cannot complete that sentence, do not read the file.

2. **Changed-files gate (feature branches):** When working on a non-main branch, immediately run `git diff <base-branch> --name-only` and reduce the output to files that appear in the critical path. Those are the only branch-specific changes relevant to this bug. Investigate them before exploring any other code. New files that don't exist on the base branch are **presumed innocent** — do not explore them unless you can trace a direct import or call dependency from the critical path to that file.

3. **Out of scope always:** Feature spec files (`spec.md`, `stories.md`, `tracker.md`, etc.), documentation, and code for adjacent features not on the critical path are never needed to fix a bug. Do not read them.

---

### Phase 0.5 — Print normalization block

**Print the normalization block before proceeding:**

```
Mode:             M{N} ({source details})
Title:            <clean title>
Affected repos:   <repos>  [note if overridden or conflicting]
Base branch:      <branch> (auto-resolved / from ticket / from evidence)
Shared branch:    bug/[clickup-<id>-]<slug>
Your branch:      <current branch> (untouched)
Worktrees:        <paths or "none (M1)">
Reproduction:     <summary of repro steps>
Evidence:         <N screenshots, N HAR calls, N console errors, etc.>
Network evidence: <relevant API calls + response body summary>  OR  "none"
API signals:      <interpretation + starting hypothesis>        OR  "n/a"
Critical path:    <condensed one-line trace, e.g. "ManageIncentivesPage → useIncentives → GET /api/v1/incentives → IncentiveController → IncentiveService → IncentiveRepository">

Reply 'go' or tell me what to correct.
```

**Wait for `go` before continuing.**

For M3: transition ClickUp status `pending` → `in-progress` after `go`, and post the "On `go`" comment from `clickup-lifecycle.md`. Populate `{REPRO_STEPS_NUMBERED_LIST}` from the normalized reproduction steps in the canonical model built above.

**Also update custom fields (M3 only, immediately after `go`):**

1. Discover custom field IDs using the "Discover custom field IDs" pattern from `clickup-client.md`. Cache results in shell variables — call this API only once per session.
2. Set these fields using the patterns from `clickup-client.md`:
   - `fix_branch` (Text): the shared branch name (e.g. `bug/clickup-<id>-<slug>`)
   - `base_branch` (Text): the resolved base branch
   - `affected_repos` (Labels multi-select): the classified repo(s) — use the Labels pattern to resolve option IDs first

Skip any field whose ID is empty (field not configured in the workspace). Never abort the fix run because a custom field update fails.

---

### Phase 0.5.1 — Create stub evidence folders (unrelated clusters)

**Only runs when Phase 0.3.5 queued unrelated clusters.** Runs immediately after the `go` gate and M3 custom field updates, before Step 1.

For each queued cluster, create one evidence folder:

```
bugs-evidence/bug-<id>/
  meta.md
  console.log    ← entries for this cluster only (omit file if cluster has no console entries)
  network.har    ← HAR entries for this cluster only (omit file if cluster has no network entries)
```

Generate the ID with `openssl rand -hex 4`. If a folder with the generated name already exists (extremely unlikely), regenerate.

**`meta.md` frontmatter:**

```yaml
---
slug: bug-<id>
captured: <parent evidence `captured` timestamp, or current ISO 8601 timestamp>
source: bug-fixer-triage
reporter: <from parent evidence `reporter` field if present, else omit>
status: pending
mode-hint: <M{N} from parent run>
discovered-during: <parent evidence slug (M3), or "inline-<bug-description-slug>" for --inline runs (M1), or "standalone-<bug-description-slug>" for --standalone runs (M2)>
affected-repos: [backend]    # backend for /api/v1/* network errors; frontend for console-only errors
base-branch: <same as parent bug base branch>
ticket: -
fix-mrs: []
fix-commits: []
linked-duplicates: []
root-cause-summary: -
last-updated: <current ISO 8601 timestamp>
---
```

**`meta.md` body:**

```markdown
# <Derived title — e.g. "Notifications mark-read 500 error">

## Observed
Discovered during triage of bug: <parent-slug>.
<error type> on <METHOD> <URL> captured in log evidence.

<paste the filtered log entries for this cluster, preserving original format>

## Expected
Request should complete successfully.

## Reproduction steps
- TBD — captured automatically from log evidence, not yet manually reproduced.
```

**`console.log`** (only when cluster has console entries): write filtered entries in the same `[timestamp] level: message` format as the parent evidence `console.log`.

**`network.har`** (only when cluster has network entries): write a valid HAR 1.2 JSON containing only the HAR entries belonging to this cluster.

Do NOT copy screenshots — the parent capture's screenshots belong to the reported bug's context only.

**Print inline notification immediately after all stubs are created:**

```
📁 Created <N> stub evidence folder(s) for unrelated errors found during triage:
   • bugs-evidence/bug-<id-1>/
   • bugs-evidence/bug-<id-2>/
   Investigate these separately when ready, or run /bug-fixer --evidence <folder>.
   ⚠️ Failed to create: bug-<id> — <reason>   ← only shown if any stub creation failed
```

If creation of a stub folder fails for a cluster, include it in the ⚠️ line of the notification and continue with remaining clusters — do not abort the fix run.

Regenerate the browse page after all stubs are created:

```bash
BLUEPRINT_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
node "$BLUEPRINT_ROOT/.claude/skills/_bug-shared/generate-bug-list.mjs"
```

---

## Step 1 — Duplicate detection (never aborts the fix)

Search all three sources in parallel:
1. `tenxengage-blueprint/bugs-evidence/**/meta.md` — all statuses
2. `tenxengage-blueprint/docs/bugs-index.md` — full file
3. ClickUp search API — keyword + affected-repo filter (statuses: pending, in-progress, needs-review)

**Match heuristic:** title keyword Jaccard similarity > 0.4 AND ≥1 shared affected repo. Exact stack-trace signature = strong signal (push to top).

**If matches found, pause and ask explicitly:**

```
Possible duplicate(s) found:

  [1] bugs-evidence/bug-a1b2c3d4/ (status=fixed)
      Fixed 2026-04-11, MRs: backend!19, frontend!34
      Keyword overlap: 5/8

  [2] ClickUp xyz789 (status=in-progress)
      "Password reset doesn't log user out of other sessions"
      Keyword overlap: 4/8

How should I proceed?
  a) Proceed independently; link as 'related' to [1] and [2]
  b) Consolidate with [2] — stop this run, continue work on xyz789
  c) Mark current bug as duplicate of [1]/[2] and fix it anyway (regression case)
  d) Mark as misfiling — close without fix, link to [1]/[2]
  e) Cancel run entirely
```

**No auto-decision.** Block on the dev's explicit choice. If no duplicates found, continue automatically.

When `c` (regression) is chosen: flag learnings capture in Step 9 with regression weight (stronger promotion even if borderline).

Record choice in `linked-duplicates:` / ClickUp comment / bugs-index entry. For M3, also update the `linked_duplicates` custom field (Text) with comma-separated ticket IDs / evidence slugs using the "Set a text custom field" pattern from `clickup-client.md`.

**Prior root cause as hypothesis primer only:** If duplicates were found and their root cause is known, that becomes **Hypothesis 1** to test first in Step 2 — nothing more. It is a starting point, not a search anchor. If the prior root cause is ruled out (the code is correct), write "H1 ruled out" explicitly and pivot immediately to the changed-files gate and critical path analysis declared in Phase 0.4.5. Do not continue searching the same area, explore thematically related code, or assume the bug must still be in the same component.

---

## Step 2 — Reproduce (mandatory; graceful give-up)

### Phase 2.0 — Critical path exploration (runs before writing any test)

Exploration is scoped to the critical path declared in Phase 0.4.5. Follow this exact sequence:

**1. Lock Hypothesis 1.** Before reading the first file, write:
```
Hypothesis 1: [specific claim, e.g. "IncentiveService.getIncentives() returns empty page"]
Testing by:   reading [specific file]
Confirms if:  [what you'd see in the code]
Rules out if: [what you'd see instead]
```

**2. Apply the changed-files gate (feature branches).** Run `git diff <base-branch> --name-only`. Cross-reference with the critical path. Identify the intersection — those are the highest-priority files to read. A file that is both changed and on the critical path is the most likely root cause.

**3. Read only files on the critical path.** For each file you read, state: *"Reading `<file>` as `<role on path>` (e.g., controller, service, hook)."* If you cannot identify its role on the critical path, skip it.

**4. Hypothesis discipline.** After reading each file:
- If it **confirms** the hypothesis → proceed to write the failing test (Phase 2.1).
- If it **rules out** the hypothesis → write "H1 ruled out," then form H2 before reading anything else.
- Never read a second file without an active hypothesis to confirm or deny.

**What is always out of scope during Step 2:**
- Feature spec files, stories, tracker files — never needed to reproduce a bug
- Files added by the current feature branch that have no import/call path to the critical path
- Adjacent features, other controllers, other pages not referenced in the critical path

---

### Phase 2.1 — Write and run the reproduction test

| Bug type | Mechanism |
|---|---|
| Backend logic/service/API | JUnit test matching scenario |
| Backend data/query | Testcontainers integration test |
| Frontend UI/flow | Playwright MCP: headed Chromium, reproduce steps, screenshot before/after |
| Frontend component-only | Vitest render + assertion |
| Cross-repo | FE Playwright E2E hitting BE; plus BE unit/integration test |

**If reproduction fails:**
- No branch, no worktree, no MR created.
- For M3: post graceful give-up comment to ClickUp (from `clickup-lifecycle.md` template), set status → `cant-reproduce`.
- For evidence: update `meta.md` status → `cant-reproduce`.
- Report: what was tried, what evidence is missing, specific questions for the reporter.
- **Stop the skill run.**

---

## Step 3 — Write failing test

Write the test capturing the exact failure. It must fail before the fix. Follow each repo's test conventions from `PROJECT-CONTEXT.md` and `docs/patterns/`.

Run the test, then verify it fails for the **right** reason — not from a typo, missing helper, or framework load error masquerading as a test failure:

- [ ] Test fails at an **assertion**, not at compile / setup / framework load
- [ ] The failure message **matches the reported symptom** (e.g., bug says "incentives list empty" → assertion says "expected size 1, got 0")
- [ ] Failure is from missing/wrong production behavior, **not** from a typo, missing import, missing factory, missing `@SpringBootTest`, or test setup throwing before the call under test

If any check fails → fix the test, re-run, re-verify before moving to Step 4.

Report: `Test confirmed failing at assertion: <message>. Matches reported symptom.`

**Backend (JUnit 5):**
- Service bug → unit test in `src/test/java/.../service/`
- Controller/API bug → `@WebMvcTest` in `src/test/java/.../controller/`
- Data/query bug → integration test extending `AbstractIntegrationTest`

**Frontend (Vitest):**
- Hook bug → `renderHook` test
- Component/page bug → Testing Library render test
- Service/API bug → mocked axios unit test

---

## Step 4 — Implement the fix

The cause should already be confirmed by the time you reach this step — Phase 0.4.5 declared the critical path, Phase 2.0 ran hypothesis discipline on it, and Step 3's failing test pins the symptom. If the cause is **not** confirmed at this point, return to Phase 2.0 and form a new hypothesis before writing fix code.

**Before writing fix code, read the affected feature's `spec.md` frontmatter** (only the frontmatter — full body remains out of scope per Phase 0.4.5's rules). If the affected feature can be identified from the critical path or bug evidence:

- Read `tenxengage-blueprint/features/{feature-slug}/spec.md` frontmatter only.

**Read domain registry** (only if `spec.md` frontmatter has `domain:` non-null):
- Read `docs/patterns/domains/INDEX.md` and `docs/patterns/domains/{domain}.md`.
- The fix MUST NOT introduce names from a different domain (e.g., don't introduce `IncentiveAudienceRule` into an `enablement` feature, or vice versa).
- If the bug stems from cross-domain leakage, flag this explicitly in the bug-fix MR description.

Implement the fix in each affected repo's worktree (or current branch for M1).

Do NOT:
- Suppress the symptom (catch and swallow)
- Add workarounds masking the issue
- Introduce unrelated changes

Follow each repo's conventions:
- Backend: TenantAware entities, Flyway for schema changes, DTOs never expose entities, constructor injection, UUID IDs
- Frontend: contracts-first types, TanStack Query, no `any`, `cn()` for classes, no direct axios (use services)

Re-run the failing test → confirm it passes.

### Stuck-loop escalation

If 3+ fix attempts have failed (the failing test from Step 3 still doesn't pass), **stop. Do not attempt fix #4.** Re-examine:

- Is the critical path declared in Phase 0.4.5 actually correct, or does the symptom flow through a different path?
- Was Hypothesis 1 confirmed by reading code, or just assumed?
- Could the bug be in a layer not on the declared path (e.g., a filter, interceptor, frontend cache, or env-specific config)?

Surface to the dev with: what was tried, what was ruled out, what alternative paths to consider. Do not silently keep iterating.

---

## Step 5 — Ready-check per affected repo

Run `/ready-check` in each affected repo's worktree. No MR until ready-check is green in every affected repo.

If ready-check surfaces issues, fix them before proceeding.

---

## Step 5.5 — Verification gate

**Invoke `superpowers:verification-before-completion`.**

Before proceeding to MR creation, that skill requires fresh evidence for each claim:

| Claim | Required evidence |
|---|---|
| "Bug is fixed" | Failing test from Step 3 now passes; re-run confirms green |
| "No regressions" | Full test suite passes in each affected repo |
| "Ready-check passed" | Step 5 output is green (not assumed from an earlier run) |

Do not proceed to Step 6 or Step 7 until all three have live evidence from this session.

---

## Step 6 — Visual re-confirmation (frontend bugs with visual evidence)

Triggered when: the ticket/evidence had screenshots OR `--visual-check` flag is set.

1. Playwright MCP re-runs the reproduction flow from Step 2 against fixed code.
2. Capture before/after screenshots.
3. Claude vision reads both screenshots, confirms:
   - Symptom is gone.
   - No visible regression in adjacent UI.
4. If vision flags a regression → abort MR creation, report specifics, ask dev to review.

Save screenshots to `/tmp/bug-<slug>-before.png` and `/tmp/bug-<slug>-after.png`.

---

## Step 7 — Create MRs (M2/M3 only)

From each affected repo's worktree:

```bash
glab mr create \
  --source-branch "bug/<slug>" \
  --target-branch "<base-branch>" \
  --title "fix: <clean title>$([ -n '$TASK_ID' ] && echo ' (ClickUp #'$TASK_ID')')" \
  --description "$(cat <<'EOF'
## Bug Fix

**ClickUp Task:** [task name](https://app.clickup.com/t/TASK_ID)   ← M3 only

## Root cause
<1-2 sentences on what was actually wrong>

## Changes
- `<file>`: <1-line description>
- `<file>`: <1-line description>

## Tests
- Added failing test: `<TestName>`
- Confirmed: test passes after fix

## Ready Check
- All quality gates passed

## Visual verification
(if Step 6 ran)
Before: <path>
After: <path>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Apply a git tag on the merge-base commit in each affected repo:
- M2: `bug-<slug>`
- M3: `bug-clickup-<id>-<slug>`

For M3:
1. Update ClickUp status → `needs-review` (use `clickup-client.md` "Update task status" pattern).
2. Post MRs comment from `clickup-lifecycle.md`.
3. Update custom fields (use cached field IDs from Step 0 — call the field discovery API only if not already cached):
   - `fix_mrs` (Text): comma-separated MR URLs
   - `fix_branch` (Text): confirm/update the shared branch name (may already be set from Step 0)

---

## Step 8 — ClickUp lifecycle updates (M3 only)

All comment templates and transition rules come from `clickup-lifecycle.md`. Do not hardcode status strings.

Status should now be `needs-review`.

---

## Step 9 — Learnings capture (3-tier)

**Curation gate (both must be true):**
1. Would a competent dev following existing docs still make this mistake?
2. Is there a non-trivial chance this class of bug will recur?

If this bug was flagged as a regression in Step 1 → apply stronger weight, promote even if borderline.

**Tier 1 — Domain-specific:** append to `docs/patterns/<domain>.md` Pitfalls section in the affected repo. Create the pattern file + register in `tenxengage-blueprint/docs/patterns/INDEX.md` if it doesn't exist yet.

**Tier 2 — Cross-cutting (single repo):** add to `PROJECT-CONTEXT.md` in the affected repo.

**Tier 3 — Cross-repo:** append to `tenxengage-blueprint/docs/learnings.md`.

Per-repo Tier 1/2 promotions ride along on the fix branch (merged with the fix MR). Tier 3 blueprint promotions are committed separately (Step 10 blueprint worktree).

---

## Step 10 — bugs-index.md append + blueprint learnings

Commit one entry to `tenxengage-blueprint/docs/bugs-index.md` via a fresh blueprint worktree (always, regardless of blueprint's current checkout branch):

```bash
BLUEPRINT_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
git -C "$BLUEPRINT_ROOT" worktree add /tmp/bugfix-blueprint-<slug> main
# write to /tmp/bugfix-blueprint-<slug>/docs/bugs-index.md
# also write any Tier 3 blueprint learnings
git -C /tmp/bugfix-blueprint-<slug> add docs/bugs-index.md docs/learnings.md
git -C /tmp/bugfix-blueprint-<slug> commit -m "chore(bugs-index): append <mode> entry for <slug>"
git -C "$BLUEPRINT_ROOT" worktree remove /tmp/bugfix-blueprint-<slug>
```

Entry format (one line, append to end of file):
```
- 2026-04-23 | M{N} | repos=[<repos>] | slug=<slug> | ticket=<id or -> | MRs=[<list or ->] | cause: <1-line> | fix: <1-line>
```

---

## Step 11 — Evidence folder finalization (if --evidence input)

Update `meta.md` frontmatter:
- `status: needs-review` (after MRs created)
- `fix-mrs: [<mr-url>, ...]`
- `fix-commits: [<sha>, ...]`
- `root-cause-summary: <one-liner extracted from the root cause narrative>`
- `last-updated: <ISO timestamp>`

Then **append** the following sections to the end of the `meta.md` body (after `## Notes`). Do not overwrite existing content — append only:

```markdown
## Root cause
<full root cause narrative from Step 12 report>

## Fix summary
<per-file change descriptions from Step 12 report>

## Tests
Framework: <JUnit | Vitest | Playwright>
Added: <test names>
Status: All green after fix

## Learnings promoted
Tier 1: <count> (<files>)
Tier 2: <count>
Tier 3: <count> (<files>)
```

Regenerate browse page:
```bash
node "$BLUEPRINT_ROOT/.claude/skills/_bug-shared/generate-bug-list.mjs"
```

Folder is retained (not deleted).

---

## Step 12 — Structured final report

Always output this exact structure:

```
╭──────────────────────────────────────────────────────────
│ Bug Fix Complete
╰──────────────────────────────────────────────────────────

Bug:          <title>
Mode:         M{N}
Source:       <ClickUp <id> | bugs-evidence/<slug> | --inline "...">
Repos:        <comma-separated>
Shared slug:  <slug>
Branch:       <branch-name>
Tag:          <tag-name>

── Root cause ─────────────────────────────────────────────
<1-2 paragraphs: what was wrong and why the old code did the wrong thing>

── Fix summary ────────────────────────────────────────────
<repo>/<file>: <1-line>
...

── Tests ──────────────────────────────────────────────────
Framework(s): <JUnit | Vitest | Playwright>
Added:        <test names>
Status:       All green after fix

── Visual verification ────────────────────────────────────
(only if Step 6 ran)
Before: /tmp/bug-<slug>-before.png
After:  /tmp/bug-<slug>-after.png
Vision: <1-line summary>

── MRs opened ─────────────────────────────────────────────
- <repo>: <url>
...

── ClickUp (M3 only) ──────────────────────────────────────
Task:            <url>
Status:          needs-review
Comments posted: <count>

── Evidence folder (if --evidence) ────────────────────────
Path:    bugs-evidence/bug-<id>/
Status:  needs-review

── Discovered bugs (if unrelated clusters found) ──────────
<N> unrelated error cluster(s) found during log triage.
Stub evidence folders created (status: pending):

Discovered bugs:
  bug-<id>  <error summary>  →  bugs-evidence/bug-<id>/

Run /bug-fixer --evidence <folder> on any of these to investigate.

── Learnings promoted ─────────────────────────────────────
Tier 1: <count> (to <files>)
Tier 2: <count> (to <files>)
Tier 3: <count> (to <blueprint files>)

── bugs-index.md ──────────────────────────────────────────
Entry: - <date> | M{N} | repos=[...] | ...

── Duplicate context (if any) ─────────────────────────────
Action: <a|b|c|d|e>
Linked: <ids/paths>

── Next steps ─────────────────────────────────────────────
1. Review MRs: <urls>
2. Verify fix in staging/preview
3. Merge MRs in dependency order
4. Mark ClickUp task 'fixed' after deploy verification (manual)
```

After printing this report:
- **M3:** post the "On fix complete" comment to ClickUp using the template from `clickup-lifecycle.md`. Status remains `needs-review` (set in Step 7 — do not change it again).
- **M2/M1:** no ClickUp post. Step 11 still writes to meta.md if `--evidence` was used.
- **Discovered bugs section:** include it only when Phase 0.3.5 found at least one unrelated cluster (i.e., Phase 0.5.1 created at least one stub folder). If no unrelated clusters were found, omit the section entirely.

---

## Branching policy (universal)

1. **Worktrees are the default.** `bug-fixer` never modifies the working tree of a repo the dev is actively using. Each affected repo gets `/tmp/bugfix-<slug>-<repo>/`. Blueprint updates ALWAYS use a worktree regardless of current blueprint branch. **M1 is the only exception** (commits on dev's current branch).
2. **Base resolution order:** `--base` flag → ClickUp `base_branch` field → evidence `meta.md base-branch:` → `main`.
3. **Shared identity:** one branch name + one tag name across all affected repos.
4. **Dev's current checkout is never touched** — not even via `git stash`.

---

## Error table

| Situation | Action |
|---|---|
| ClickUp API 401 | Stop — "Check your CLICKUP_API_TOKEN" |
| ClickUp API 404 | Stop — "List or task not found. Check CLICKUP_BUGS_LIST_ID or task ID" |
| No open bugs in list | Stop — "No open bugs found. All caught up!" |
| Cannot classify FE/BE | Ask before branching |
| Reproduction fails | Graceful give-up (Step 2) |
| Ready-check fails, can't auto-fix | Surface to dev; no MR |
| Test can't be written (e2e only) | Note it: "This bug requires a manual/e2e test. Wrote a TODO stub instead." |
| Evidence folder status is fixed/duplicate/wont-fix/cant-reproduce | Refuse unless `--force` |
| Evidence folder `in-progress` and `last-updated < 1h ago` | Warn and wait for `y` |

---

## `--wont-fix` handling

Invoked as: `/bug-fixer --wont-fix <slug-or-id> "reason"`

`<slug-or-id>` may be:
- An evidence folder name (e.g. `bug-a3f2b1c2`)
- A ClickUp task ID (e.g. `abc123def`)
- If the evidence folder's `meta.md` has `ticket:` set, treat as both

**No fix work is performed.** This is a closure-only operation.

Steps:
1. Resolve input to evidence folder path and/or ClickUp task ID.
2. Check current status. Refuse if already `fixed` ("Cannot mark a fixed bug as wont-fix — use ClickUp directly if needed") unless `--force`.
3. **Evidence folder (if applicable):**
   - Set `status: wont-fix` and `last-updated: <now>` in frontmatter
   - Append section to end of body:
     ```markdown
     ## Won't fix
     <reason>
     ```
   - Regenerate browse page via `generate-bug-list.mjs`
4. **ClickUp (if task ID known — NOT mode-gated; runs even in M1/M2 whenever a ticket ID is available):**
   - Update status → `wont-fix`
   - Post "On wont-fix" comment from `clickup-lifecycle.md` with `{REASON}` substituted
5. Print confirmation:
   ```
   Marked as won't fix.
   Evidence: bugs-evidence/bug-<id>/   (if applicable)
   ClickUp:  <url>                   (if applicable)
   Reason:   <reason>
   ```