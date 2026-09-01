# Ready-check Step 5 Codex Hand-off Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `ready-check` Step 5 actually work by reframing it from "Claude runs Codex" to "Claude consumes a Codex review the user already fired or pasted."

**Architecture:** Step 5 acquires its Codex result via three paths in order: (1) parse pasted content in the same turn, (2) query the Codex job store via `codex-companion.mjs status --all --json` + `result <id> --json`, (3) pause with status `awaiting-user-review` and instruct the user. The hand-off respects the codex plugin's `disable-model-invocation: true` gate on `/codex:adversarial-review`. Same change applied to backend and frontend `ready-check` SKILL.md files, plus a `/create-pr`-side update to recognize the new status.

**Tech Stack:** Markdown (Claude Code skill files). Bash for verification probes. No code compilation involved; the "tests" are scenario walkthroughs that the user runs interactively.

**Spec reference:** [docs/superpowers/specs/2026-05-12-ready-check-step5-codex-handoff-design.md](../specs/2026-05-12-ready-check-step5-codex-handoff-design.md)

---

## Pre-flight

The plan modifies skill files in three repositories:

- `tenxengage-application/tenxengage-backend/.claude/skills/ready-check/SKILL.md`
- `tenxengage-application/tenxengage-frontend/.claude/skills/ready-check/SKILL.md`
- Wherever `/create-pr` checks step statuses (located in Task 1)

The blueprint repo (current working dir) is where this plan, the spec, and commits land — but the skills themselves live in sibling repos. Each sibling repo gets its own commit on its current branch (no need to create new branches for this work).

---

### Task 1: Recon — locate `/create-pr` gate and confirm Step 5 line ranges

**Files:**
- Inspect (do not modify yet):
  - `../tenxengage-backend/.claude/skills/ready-check/SKILL.md`
  - `../tenxengage-frontend/.claude/skills/ready-check/SKILL.md`
  - `../tenxengage-backend/.claude/skills/create-pr/SKILL.md` (likely path)
  - `../tenxengage-frontend/.claude/skills/create-pr/SKILL.md` (likely path)

- [ ] **Step 1: Confirm backend Step 5 line range**

Run:
```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-backend && grep -n "^## Step 5\|^## Step 6\|## Report JSON\|## Step 8" .claude/skills/ready-check/SKILL.md
```

Expected: matches like `226:## Step 5: Adversarial Review ...`, `302:## Step 6: ...`, `## Report JSON Structure`, `## Step 8: Summary & Report`. Record the start/end lines for Step 5, the `adversarial-review` block inside the report schema, and the Step 5 line inside Step 8's summary format. The Step 5 body to replace runs from `## Step 5: Adversarial Review` up to the `---` separator before `## Step 6`.

- [ ] **Step 2: Confirm frontend Step 5 line range**

Run:
```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-frontend && grep -n "^## Step 5\|^## Step 6\|## Report JSON\|## Step 7\|## Step 8" .claude/skills/ready-check/SKILL.md
```

Expected: similar matches. Record the Step 5 body line range, the `adversarial-review` block in the report schema, and the Step 5 summary line. (Note: frontend has 6 steps total; backend has 7. Step 8 wording may differ — verify whether the summary section header is `## Step 7` or `## Step 8` in the frontend file.)

- [ ] **Step 3: Locate `/create-pr` gate in backend**

Run:
```bash
ls /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-backend/.claude/skills/ | grep -i "create-pr\|pr-creat\|open-pr"
```

If found, grep that file for status comparisons:
```bash
grep -n 'status.*"failed"\|status.*"pending"\|status.*"passed"\|allow.*PR\|block.*PR\|create.*PR' /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-backend/.claude/skills/<found-dir>/SKILL.md
```

Expected: identifies the lines that gate PR creation on step statuses. Record the file path and the gating logic. If no `create-pr` skill exists in the backend, search wider:
```bash
grep -rln "ready-check\|status.*failed.*pending" /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-backend/.claude/ 2>/dev/null | head -10
```

- [ ] **Step 4: Locate `/create-pr` gate in frontend**

Same as Step 3 but in `tenxengage-application/tenxengage-frontend/`. Record findings.

- [ ] **Step 5: Write recon notes**

Append a short notes section to this plan (or a scratch file) capturing:
- Backend Step 5 body line range (e.g., `226–300`)
- Backend `adversarial-review` block line range in report schema
- Backend Step 8 summary template line for Step 5
- Frontend Step 5 body line range (e.g., `193–265`)
- Frontend `adversarial-review` block line range
- Frontend summary line for Step 5
- Backend `/create-pr` file path + gating-logic line(s)
- Frontend `/create-pr` file path + gating-logic line(s)

These line numbers feed Tasks 2–4. No commit for this task — it's pure recon.

---

### Task 2: Rewrite backend `ready-check` Step 5

**Files:**
- Modify: `../tenxengage-backend/.claude/skills/ready-check/SKILL.md`
  - Replace the Step 5 body (from Task 1 Step 1)
  - Update the `adversarial-review` block inside `## Report JSON Structure`
  - Update the Step 5 line and the failure-message block inside `## Step 8: Summary & Report`

- [ ] **Step 1: Read the existing Step 5 body**

Read `../tenxengage-backend/.claude/skills/ready-check/SKILL.md` from `## Step 5:` (Task 1 Step 1's start line) through the `---` separator before `## Step 6:`. Confirm it matches what the spec describes (current broken version invoking `codex:adversarial-review` via Skill tool).

- [ ] **Step 2: Replace the Step 5 body with the new content**

Use `Edit` to replace the entire Step 5 section (header line through the trailing `---`) with the following block. The `old_string` is the existing Step 5 body verbatim from Step 1; the `new_string` is:

````markdown
## Step 5: Adversarial Review (scoped to changes since main)

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "adversarial-review"` → skip to next step.

**Skip if**: Only test files, config files, migration SQL, or documentation changed → mark `not_applicable`

Step 5 does NOT run Codex itself — the `/codex:adversarial-review` slash command is intentionally not model-invocable (the codex plugin sets `disable-model-invocation: true`). Instead, Step 5 **consumes** a Codex review the user has fired or pasted. Acquire the review using these three paths, in order. Use the first one that yields a usable result.

### Path 1: In-turn pasted content

If the user's message in this turn contains pasted Codex output, parse that and skip Path 2.

**Detect a paste** when the message contains either:
- A JSON object with a top-level `verdict` or `findings` key (matches the Codex result schema), OR
- A Markdown block starting with `# Codex Adversarial Review` (Codex's rendered output).

**Parse**:
- **JSON paste**: parse the object directly using the schema in the "Codex result schema" section below.
- **Markdown paste**: line-oriented regex parse. Codex's rendered Markdown has this structure for each finding:
  ```
  - [<severity>] <title> (<file>:<line_start>-<line_end>)
    <body text>
    Recommendation: <recommendation>
  ```
  Extract `severity`, `title`, `file`, `line_start`, `line_end`, `body`, `recommendation`.

**Parse-failure guard**: if parsing yields zero findings but the text clearly contains `[critical]`, `[high]`, `[medium]`, or `[low]` markers, set step status `failed` with `parseError` describing the failure rather than passing silently.

**When using Path 1**, the report records `source: "user-paste"`. Pasted content cannot be auto-verified against branch/HEAD/base — also record `pasteWarning: "review provenance not auto-verified — branch/baseRef/timestamp checks skipped"`.

### Path 2: Codex job store

If no paste detected in Path 1, query the Codex job store:

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/codex-companion.mjs" status --all --json
```

**Find a candidate job**:
1. Inspect `latestFinished` first. If `latestFinished.kind == "adversarial-review"` AND it passes the freshness check below, it is the candidate.
2. Otherwise scan `recent[]` for the most recent entry where `kind == "adversarial-review"` AND it passes the freshness check.
3. If no candidate, fall through to Path 3.

**Freshness check** — all three of these must hold for a job to be considered fresh:

| Condition | How to compute |
|---|---|
| `job.completedAt >= HEAD_committer_iso` | `HEAD_committer_iso = git log -1 --format=%cI HEAD`. ISO-8601 lexicographic comparison. |
| `job.result.context.branch == current_branch` | `current_branch = git branch --show-current` |
| `job.result.target.baseRef == "main"` | hardcoded — Step 5 always reviews against `main` |

An amended commit updates the committer timestamp without changing the tree, so it will fail the freshness check and force a re-review. This is an accepted false positive.

**If multiple jobs match**, take the most recent by `completedAt`. Record its `id` as `codexJobId`.

**Fetch the result**:

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/codex-companion.mjs" result <job-id> --json
```

Trust the structured object at `storedJob.result.result` (already in the schema below) when `storedJob.result.parseError == null`. If `parseError != null`, fall back to parsing `storedJob.rendered` (Markdown) using the same regex as Path 1.

**When using Path 2**, the report records `source: "job-store"` and the fields `codexJobId`, `codexBaseRef`, `codexBranch`, `codexCompletedAt`.

### Path 3: Pause (no review available)

Set step status `awaiting-user-review`. Stop the ready-check run. Print:

```
  Step 5: Adversarial Review    AWAITING USER REVIEW

  Step 5 needs an adversarial Codex review of this branch's diff against main.
  Either:

  (a) Run the slash command and re-run ready-check:
      /codex:adversarial-review --wait --base main
      /ready-check 5

  (b) Paste the Codex review output (JSON or rendered Markdown) into your next
      message, then re-run /ready-check 5. The pasted form is accepted but
      cannot be auto-verified against this branch.

  Same-session is fine — the Codex job store is workspace-keyed, not session-keyed.
```

### Codex result schema (reference)

The structured object available at `storedJob.result.result` (or in a JSON paste):

```json
{
  "verdict": "ship|needs-attention|...",
  "summary": "...",
  "findings": [
    {
      "severity": "critical|high|medium|low",
      "title": "...",
      "body": "...",
      "file": "src/...",
      "line_start": 24,
      "line_end": 26,
      "confidence": 0.99,
      "recommendation": "..."
    }
  ],
  "next_steps": ["..."]
}
```

### Field-name mapping (Codex → report)

When converting Codex's structured output into report findings, map fields as follows:

| Report field | Codex field |
|---|---|
| `summary` | `title` |
| `details` | `body` |
| `lineStart` | `line_start` |
| `lineEnd` | `line_end` |
| `severity` | `severity` (passthrough) |
| `confidence` | `confidence` (passthrough) |
| `file` | `file` (passthrough) |
| `recommendation` | `recommendation` (passthrough) |

### Classification Phase

Codex does not emit `alreadyHandled`. Derive it per-finding: read the code at `file:lineStart-lineEnd` and its immediate surroundings, then set `alreadyHandled = true` if the risk is already mitigated in surrounding code, otherwise `false`.

Then classify into a tier:

| Tier | Criteria |
|---|---|
| **Blocking** | severity `critical` or `high` AND confidence `>= 0.70` AND `alreadyHandled == false` |
| **Advisory** | severity `medium` or `low`, OR confidence `< 0.70`, OR `alreadyHandled == true` |

Assign `tier` and `tierReason` to each finding. Examples:
- `"severity=critical, confidence=0.85 (≥ 0.70), alreadyHandled=false"` → blocking
- `"severity=high, confidence=0.60 (< 0.70)"` → advisory
- `"severity=medium"` → advisory
- `"alreadyHandled=true"` → advisory (excluded from blocking count regardless of severity/confidence)

**Do NOT fix any issues** — this step never modifies source files.

### Step Status

- Any blocking findings → step status = `failed`.
- Only advisory findings or none → step status = `passed`.
- Path 3 reached (no review acquired) → step status = `awaiting-user-review`.

### Output

**When step status = `failed` (blocking findings):**

```
  Step 5: Adversarial Review    FAILED (N blocking, N advisory)

  BLOCKING (severity critical/high + confidence ≥ 0.70 — must fix before PR):
  ✗ [critical | conf: 0.85] SomeFile.java:42-55
    Risk: <body>
    Fix:  <recommendation>

  ADVISORY (lower severity or confidence — review recommended, does not block):
  ⚠ [medium | conf: 0.55] SomeFile.java:120-134
    Risk: <body>
    Fix:  <recommendation>

  Classification: blocking = severity critical/high AND confidence ≥ 0.70 AND not alreadyHandled
  Source: <job-store | user-paste>
  Fix blocking issues, then re-run: /ready-check adversarial-review
```

**When step status = `passed` (only advisory or none):**

```
  Step 5: Adversarial Review    PASSED (0 blocking, N advisory)

  ADVISORY (does not block):
  ⚠ [medium | conf: 0.45] SomeFile.java:67-72
    Risk: <body>
    Fix:  <recommendation>

  Classification: blocking = severity critical/high AND confidence ≥ 0.70 AND not alreadyHandled
  Source: <job-store | user-paste>
```

**When step status = `awaiting-user-review`:** print the Path 3 pause prompt above (no findings block).

→ Update report: `adversarial-review` =
  - `status`: `passed`/`failed`/`not_applicable`/`awaiting-user-review`
  - `validatedAtCommit`: current `git rev-parse HEAD` (only when step ran; not for `awaiting-user-review`)
  - `source`: `"job-store"` or `"user-paste"` (only when step ran)
  - `codexJobId`, `codexBaseRef`, `codexBranch`, `codexCompletedAt` (only when `source == "job-store"`)
  - `pasteWarning` (only when `source == "user-paste"`)
  - `parseError` (only when the Markdown fallback parser failed)
  - `findings`: list of `{ summary, details, file, lineStart, lineEnd, severity, confidence, tier, tierReason, recommendation, alreadyHandled }` entries
  - `classificationRules`: `{ "blockingThreshold": "severity critical/high AND confidence >= 0.70 AND not alreadyHandled", "advisoryThreshold": "severity medium/low OR confidence < 0.70 OR alreadyHandled" }`
  - `codexOutput`: full verbatim Codex rendered Markdown
````

- [ ] **Step 3: Update the `adversarial-review` block in the Report JSON Structure section**

Find the existing block (Task 1 Step 1 noted its location). Replace it with:

````markdown
    "adversarial-review": {
      "status": "passed|failed|not_applicable|pending|awaiting-user-review",
      "validatedAtCommit": "{hash}",
      "source": "job-store|user-paste",
      "codexJobId": "review-xxx",
      "codexBaseRef": "main",
      "codexBranch": "{branch-name}",
      "codexCompletedAt": "{ISO}",
      "pasteWarning": "review provenance not auto-verified — branch/baseRef/timestamp checks skipped",
      "parseError": null,
      "findings": [
        {
          "summary": "...",
          "details": "...",
          "file": "...",
          "lineStart": 0,
          "lineEnd": 0,
          "severity": "critical|high|medium|low",
          "confidence": 0.0,
          "tier": "blocking|advisory",
          "tierReason": "...",
          "recommendation": "...",
          "alreadyHandled": false
        }
      ],
      "classificationRules": {
        "blockingThreshold": "severity critical/high AND confidence >= 0.70 AND not alreadyHandled",
        "advisoryThreshold": "severity medium/low OR confidence < 0.70 OR alreadyHandled"
      },
      "codexOutput": "<full verbatim Codex rendered Markdown>"
    },
````

Also update the "Note" line near the bottom of the Report JSON Structure section if it mentions allowed statuses. Add `awaiting-user-review` as an explicitly **not-passed** status (distinct from `failed` and `pending`).

- [ ] **Step 4: Update the Step 5 line in Step 8's summary template**

Find the existing line:
```
  Step 5: Adversarial Review    {PASSED/FAILED/NOT_APPLICABLE} ({N} findings)
```

Replace with:
```
  Step 5: Adversarial Review    {PASSED/FAILED/NOT_APPLICABLE/AWAITING USER REVIEW} ({N} findings)
```

And in the same Step 8 section, find the "IF ANY FAILED" message block. Add a new block AFTER it for the awaiting-user-review case (using whatever indentation matches the existing blocks):

```
  {IF ANY AWAITING USER REVIEW}:
  Step 5 needs an adversarial Codex review. Run /codex:adversarial-review --wait --base main,
  then re-run /ready-check 5.
```

- [ ] **Step 5: Read back the file and skim-verify**

Read the modified SKILL.md and skim Step 5, the report schema block, and Step 8 to confirm:
- Step 5 body matches the new content above
- Report schema includes all new fields
- Step 8 template handles all four statuses
- No leftover references to "Use the Skill tool to invoke codex:adversarial-review"
- No leftover references to `codex:adversarial-review --wait --base main` as a model-invoked command (the only mention should be in the Path 3 user-facing prompt)

Run:
```bash
grep -n "Use the Skill tool to invoke\|Skill tool to invoke .codex" /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-backend/.claude/skills/ready-check/SKILL.md
```

Expected: zero matches.

- [ ] **Step 6: Commit**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-backend && git add .claude/skills/ready-check/SKILL.md && git commit -m "$(cat <<'EOF'
fix(ready-check): rewrite Step 5 to consume Codex reviews via job store

Step 5 previously instructed Claude to invoke codex:adversarial-review
via the Skill tool, but that's a slash command marked
disable-model-invocation: true — Claude cannot fire it. Reframe Step 5
to consume a Codex review the user already fired (job store) or pasted,
with a pause + clear hand-off prompt if neither is available.

Adds awaiting-user-review status and the corresponding report fields
(source, codexJobId, codexBaseRef, codexBranch, codexCompletedAt,
pasteWarning, parseError).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Rewrite frontend `ready-check` Step 5

Mirrors Task 2 for the frontend repo. The body is identical except that the existing applicability rule ("Changed files include components, pages, or hooks") MUST be preserved — it lives in the **Stage Applicability Rules** table near the top of the SKILL.md, not in the Step 5 body, so the Task 2 rewrite already preserves it. Confirm during Step 1 below.

**Files:**
- Modify: `../tenxengage-frontend/.claude/skills/ready-check/SKILL.md`

- [ ] **Step 1: Verify the applicability rule lives outside Step 5**

Run:
```bash
grep -n "components, pages, or hooks\|Adversarial Review" /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-frontend/.claude/skills/ready-check/SKILL.md
```

Expected: the "components, pages, or hooks" rule appears in the Stage Applicability Rules table (early in the file), NOT inside the Step 5 body. If it does live inside Step 5, the rewrite must preserve it verbatim — note this and adjust the replacement in Step 2.

- [ ] **Step 2: Read the existing Step 5 body**

Read the frontend SKILL.md from `## Step 5:` (Task 1 Step 2's start line) through the `---` separator before the next step. Confirm it matches the current broken pattern.

- [ ] **Step 3: Replace the Step 5 body**

Use `Edit` to replace the entire frontend Step 5 section with the **exact same content** shown in Task 2 Step 2. No frontend-specific adjustments needed inside the Step 5 body — the body is identical between repos.

- [ ] **Step 4: Update the frontend `adversarial-review` block in the Report JSON Structure section**

Same replacement as Task 2 Step 3.

- [ ] **Step 5: Update the Step 5 summary line and message block in the frontend summary section**

The frontend may number its summary section `## Step 7` rather than `## Step 8` (frontend has 6 steps, backend has 7). Apply the same edit as Task 2 Step 4, targeting whatever section heading the frontend uses.

- [ ] **Step 6: Skim-verify**

```bash
grep -n "Use the Skill tool to invoke\|Skill tool to invoke .codex" /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-frontend/.claude/skills/ready-check/SKILL.md
```

Expected: zero matches.

- [ ] **Step 7: Commit**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-frontend && git add .claude/skills/ready-check/SKILL.md && git commit -m "$(cat <<'EOF'
fix(ready-check): rewrite Step 5 to consume Codex reviews via job store

Mirrors the backend ready-check Step 5 rewrite. Same hand-off mechanic:
parse pasted Codex output, else query the Codex job store via
codex-companion.mjs, else pause with awaiting-user-review status.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Update `/create-pr` (or equivalent gate) to recognize `awaiting-user-review`

**Files:**
- Modify: backend `/create-pr` skill (path from Task 1 Step 3)
- Modify: frontend `/create-pr` skill (path from Task 1 Step 4)

**Note:** if Task 1 found that the same gating logic lives in only one file (e.g., a shared skill), update that file once. If both repos have their own copy, update both. If no `/create-pr` skill exists yet, skip this task and note the omission — Step 5's new status will be honored when the gate is eventually written.

- [ ] **Step 1: Read the existing gating logic**

Read the file(s) Task 1 identified. Find the status checks (likely something like "if any step has status `failed` or `pending`, refuse to create the PR").

- [ ] **Step 2: Add `awaiting-user-review` to the not-passed list**

Where the existing logic enumerates the not-passed statuses (e.g., `"failed"`, `"pending"`), add `"awaiting-user-review"`. Concretely, if the gate is written in prose like:

```
Before creating the PR, ensure no step in report.json has status "failed" or "pending".
```

Replace with:

```
Before creating the PR, ensure no step in report.json has status "failed", "pending", or "awaiting-user-review".
A step in "awaiting-user-review" indicates Step 5 (Adversarial Review) needs a Codex review the user has not yet provided.
Refuse to create the PR and ask the user to run /codex:adversarial-review --wait --base main and then /ready-check 5.
```

If the gate is written as a Bash check (e.g., `jq` query), update the query to include the new status. Show the exact change in the commit.

- [ ] **Step 3: Skim-verify**

Re-read the modified file. Confirm the new status appears in the not-passed list and a brief explanation references Step 5 / Codex.

- [ ] **Step 4: Commit (backend)**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-backend && git add .claude/skills/<create-pr-dir>/SKILL.md && git commit -m "$(cat <<'EOF'
fix(create-pr): treat awaiting-user-review as not-passed

Step 5 of ready-check can now set status awaiting-user-review when a
Codex adversarial review is required but not yet available. Treat that
status as blocking PR creation, with a hint to run Codex and re-run
ready-check.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 5: Commit (frontend, if separate file)**

If the frontend has its own copy of the gate, apply the same change and commit it the same way in the frontend repo. If shared, skip.

---

### Task 5: Manual verification

These scenarios cannot be automated cheaply (each happy-path scenario costs a real Codex review). Run them as a smoke test. The user can defer any subset.

**Setup**: pick a test feature branch in `tenxengage-backend` with a small but non-trivial diff vs `main` (at least one service or controller file modified). All commands assume you're inside that branch's checkout.

- [ ] **Scenario A: Happy path via job store**

1. From the test branch, run `/codex:adversarial-review --wait --base main`. Wait for completion.
2. Run `/ready-check 5`.
3. Expected: Step 5 detects the fresh job, prints the PASSED or FAILED summary with `Source: job-store`, and writes findings + `codexJobId` + `codexBaseRef` + `codexBranch` + `codexCompletedAt` to `report.json`.

```bash
cat .ready-check/$(git branch --show-current)/report.json | jq '.steps."adversarial-review"' | head -40
```

Expected: `source: "job-store"`, `codexJobId` present, findings populated.

- [ ] **Scenario B: Pause path**

1. From a different test branch where Codex has NEVER run (or after clearing the job store), run `/ready-check 5`.
2. Expected: step status `awaiting-user-review`, the Path 3 pause prompt is printed, ready-check stops.

```bash
cat .ready-check/$(git branch --show-current)/report.json | jq '.steps."adversarial-review".status'
```

Expected: `"awaiting-user-review"`.

- [ ] **Scenario C: Stale path**

1. From the Scenario A branch, add a new commit (e.g., `git commit --allow-empty -m "stale-test"`).
2. Run `/ready-check 5`.
3. Expected: Step 5 detects the old job, fails the freshness check (`completedAt < new HEAD committer time`), falls through to pause. Report shows `awaiting-user-review` again.

- [ ] **Scenario D: Paste path (JSON)**

1. Capture a Codex JSON result from any past run (`node codex-companion.mjs result <id> --json | jq .storedJob.result.result`).
2. In a new `/ready-check 5` invocation, paste the JSON object into the message along with the command.
3. Expected: Step 5 detects the paste, parses it, classifies findings, and writes the report with `source: "user-paste"` and `pasteWarning` present. No `codexJobId`.

- [ ] **Scenario E: Paste path (Markdown)**

Same as Scenario D but paste the rendered Markdown form (the human-readable output from `--wait` mode) instead of the JSON. Expected: same as Scenario D but parsed via the Markdown regex.

- [ ] **Scenario F: Cross-branch rejection**

1. Run Codex on branch A.
2. Switch to branch B (which has different changes vs main).
3. Run `/ready-check 5` on branch B.
4. Expected: Step 5 rejects the branch-A job because `job.result.context.branch != current_branch`, falls through to pause.

- [ ] **Scenario G: `/create-pr` blocks**

1. On a branch where Step 5 is `awaiting-user-review`, run `/create-pr`.
2. Expected: refuses to create the PR. Output should reference Step 5 / Codex and suggest the unblocking commands.

- [ ] **Cleanup**

Discard any test branches and clear `.ready-check/<branch>/` directories if you want a clean slate. No commit needed.

---

## Self-review notes

**Spec coverage check** — every requirement in [the spec](../specs/2026-05-12-ready-check-step5-codex-handoff-design.md) is addressed:

- Three-path acquisition (paste / job store / pause) — Task 2 Step 2 (and Task 3 mirror).
- Freshness check (timestamp + branch + baseRef) — Task 2 Step 2.
- Field-name mapping (Codex → report) — Task 2 Step 2.
- `alreadyHandled` derivation by Claude — Task 2 Step 2 Classification section.
- Tier classification unchanged — Task 2 Step 2.
- New `awaiting-user-review` status — Task 2 Step 2, Task 2 Step 3 (report schema), Task 2 Step 4 (Step 8 summary), Task 4 (`/create-pr` gate).
- Trust caveat for pasted content (`source`, `pasteWarning`) — Task 2 Step 2.
- Same-session vs separate-session note — included in Path 3 prompt.
- Files to change (backend, frontend, `/create-pr`) — Tasks 2, 3, 4.
- Verification scenarios — Task 5.
- Open items (locate `/create-pr` gate) — Task 1.

**Out-of-scope items** from the spec (per-feature scoping, `--accept-stale`, `/codex:review` step, classification-threshold changes, store cleanup) are NOT addressed in any task — correct.

**Type consistency:** all field names referenced across tasks match — `source`, `codexJobId`, `codexBaseRef`, `codexBranch`, `codexCompletedAt`, `pasteWarning`, `parseError`, `awaiting-user-review`. The Codex `line_start`/`line_end` vs report `lineStart`/`lineEnd` mapping is shown once and used consistently.
