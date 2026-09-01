---
name: "ready-check"
description: "Sequential quality gate: dispatches Code Quality skills as parallel subagents, collects structured findings, applies fixes, then runs tests and coverage checks. Tracks progress in a report for resume support."
argument-hint: "Optional: [feature-id] [step:<N-or-name>]"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Step 0: Determine Context & Load Report

1. **Get branch name**: Run `git branch --show-current` → this is the report key (used as-is for report path)

2. **Detect feature ID** (for spec/contract lookup — separate from report path):
   - If user provided a feature ID, use it
   - If branch matches `features/*` pattern: parse `features/<slug>` → `<slug>`
   - Otherwise: ask the user for the feature ID
   - **Feature ID can also be `none`** — for bug fixes or changes not tied to a spec

3. **Detect step selector** (for single-step mode):
   - Scan `$ARGUMENTS` for a token that is either:
     - A bare integer 1–7 (Step 9 cannot be targeted individually — it only runs on full runs), OR
     - A known slug: `prerequisites`, `code-review`, `security-review`, `contract-compliance`, `adversarial-review`, `tests`, `coverage`, OR
     - Any of the above prefixed with `step:` (e.g., `step:3`, `step:code-review`)
   - Step number → slug: 1=prerequisites, 2=code-review, 3=security-review, 4=contract-compliance, 5=adversarial-review, 6=tests, 7=coverage
   - If found: set `targetStep = <resolved-slug>` and `singleStepMode = true`; exclude the token from feature ID parsing
   - If not found: set `singleStepMode = false`; full run proceeds normally

4. **Load or create report**:
   - Report path uses the **full branch name**: `.ready-check/{branch-name}/review.json`
   - If exists: load it, check which steps are already passed
   - If not exists: create the directory and initialize a new report

5. **Get current HEAD commit**: `git rev-parse HEAD`

6. **Determine resume point**:
   - For each previously passed step, check if relevant files changed since `validatedAtCommit`
   - Use `git diff {validatedAtCommit} HEAD --name-only` to find changed files
   - If files relevant to a passed step changed, invalidate that step and all subsequent steps
   - Resume from the earliest invalidated or pending step

7. **Get changed files** (for scoping reviews): `git diff main --name-only --diff-filter=ACMR`

8. **Determine which stages apply** (see "Stage Applicability" section below)

---

## Stage Applicability Rules

Not all stages apply to every change. **If a stage doesn't apply, mark it as `not_applicable` in the report and move on.**

| Stage | Applies When | Skip When |
|---|---|---|
| **Prerequisites** | Always | Never — always runs |
| **Code Review** | Changed files include `src/main/java/**/*.java` | No Java source files changed (e.g., only config, docs, SQL) |
| **Security + API Review** | Changed files include controllers, security classes, or DTOs | No controller/security/DTO files changed |
| **Contract Compliance** | Feature contract exists in `contracts/endpoints/` AND changed files include controllers or DTOs | No feature contract exists, OR no controller/DTO changes |
| **Adversarial Review** | Changed files include service or controller logic | Only tests, config, or migration files changed |
| **Tests** | Source files changed that should have tests | No testable source files changed |
| **Coverage** | New service methods or controller endpoints were added | Only modifications to existing methods |

**A `not_applicable` stage counts as passed.** The summary clearly shows which stages ran vs not_applicable.

---

## Step 1: Prerequisites Check

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "prerequisites"` → skip to next step.

- Run `./gradlew check` — compilation and checkstyle pass
- If feature ID is not `none`: verify `../tenxengage-blueprint/features/{feature-id}/spec.md` exists
- Verify Flyway migrations compile (if any SQL files changed)
- **On failure**: Report the error, do NOT auto-fix prerequisites — these need manual attention

→ Update report: `prerequisites` = passed/failed

---

## Step 2: Code Review (Orchestrated — parallel subagents)

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "code-review"` → skip to next step.

**Skip if**: No `src/main/java/**/*.java` files in the diff → mark `not_applicable`

### Review Phase

Read each changed Java file to get its full content. Then use the **Agent tool** to dispatch the following **3 subagents in parallel** (send all 3 in a single message):

**Subagent A — Java Code Quality:**
```
[STRUCTURED-OUTPUT]
Use the Skill tool to load the 'java-code-review' skill, then apply its full checklist to the files below.
Return ONLY the structured JSON findings as defined in the skill's "Structured Output Mode" section.

Files to review:
{list of changed .java file paths}

{contents of each changed .java file}
```

**Subagent B — Concurrency Review:**
```
[STRUCTURED-OUTPUT]
Use the Skill tool to load the 'concurrency-review' skill, then apply its full checklist to the files below.
Return ONLY the structured JSON findings as defined in the skill's "Structured Output Mode" section.

Files to review:
{list of changed .java file paths}

{contents of each changed .java file}
```

**Subagent C — Performance Smell Detection:**
```
[STRUCTURED-OUTPUT]
Use the Skill tool to load the 'performance-smell-detection' skill, then apply its full checklist to the files below.
Return ONLY the structured JSON findings as defined in the skill's "Structured Output Mode" section.

Files to review:
{list of changed .java file paths}

{contents of each changed .java file}
```

### Fix Phase (sequential, main agent)

After all 3 subagents return their JSON findings:

1. Merge all findings into a single list, sorted by severity (critical → low)
2. For each finding where `severity` is `critical` or `high` and `autoFixable` is `true`:
   - Read the target file
   - Apply the fix
   - Increment fix counter
3. For critical/high findings where `autoFixable` is `false`: note them as manual action items

### Re-validate Phase (if fixes were applied)

If any files were modified in the fix phase: re-read those files and verify the issues are resolved. If a fix introduced a new issue, fix that too.

→ Update report: `code-review` = passed/failed/not_applicable
  - `fixes`: total auto-fixes applied
  - `filesReviewed`: list of .java files reviewed
  - `findings`: all critical/high issues (fixed and manual)

---

## Step 3: Security + API Review (Orchestrated — parallel subagents)

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "security-review"` → skip to next step.

**Skip if**: No controller, security class, or DTO files in the diff → mark `not_applicable`

### Review Phase

Read each changed file in `controller/`, `security/`, `dto/` packages. Then use the **Agent tool** to dispatch **2 subagents in parallel**:

**Subagent D — Security Audit:**
```
[STRUCTURED-OUTPUT]
Use the Skill tool to load the 'security-audit' skill, then apply its full checklist to the files below.
Return ONLY the structured JSON findings as defined in the skill's "Structured Output Mode" section.

Files to review:
{list of changed controller/security/dto file paths}

{contents of each file}
```

**Subagent E — API Contract Review:**
```
[STRUCTURED-OUTPUT]
Use the Skill tool to load the 'api-contract-review' skill, then apply its full checklist to the files below.
Focus on: HTTP verb semantics, versioning, entity-vs-DTO responses, status codes, error format, pagination.
Return ONLY the structured JSON findings as defined in the skill's "Structured Output Mode" section.

Files to review:
{list of changed controller/dto file paths}

{contents of each file}
```

### Fix Phase (sequential, main agent)

After both subagents return:

1. Merge findings, sort by severity
2. For each critical/high autoFixable finding: apply fix, increment counter
3. For critical/high non-autoFixable: note as manual action items

→ Update report: `security-review` = passed/failed/not_applicable + fixes

---

## Step 4: Contract Compliance (scoped to changes since main)

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "contract-compliance"` → skip to next step.

**Skip if**:
- Feature ID is `none` → mark `not_applicable`
- No contract file exists in `contracts/endpoints/` for this feature → mark `not_applicable`
- No controller or DTO files in the diff → mark `not_applicable`

**When this stage runs:**
- Read the feature contract: `contracts/endpoints/{resource}.yaml`
- Read the relevant models: `contracts/models/{model-name}.md`
- Read each changed controller file
- Verify:
  - Every endpoint in the contract has a corresponding controller method
  - HTTP methods match
  - Path parameters match
  - Request/response DTO fields align with contract models
  - Status codes match
- **On drift**: Auto-fix the code to match the contract (the contract is the source of truth)
- **If implementation revealed a valid contract change**: update `contracts/endpoints/` and `contracts/models/` to match

→ Update report: `contract-compliance` = passed/failed/not_applicable

---

## Step 5: Adversarial Review (scoped to changes since main)

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "adversarial-review"` → skip to next step.

**Skip if**: Only test files, config files, migration SQL, or documentation changed → mark `not_applicable`

Step 5 does NOT run Codex itself — the `/codex:adversarial-review` slash command is intentionally not model-invocable (the codex plugin sets `disable-model-invocation: true`). Instead, Step 5 **consumes** a Codex review the user has fired or pasted. Acquire the review using these three paths, in order. Use the first one that yields a usable result.

### Path 1: In-turn pasted content

If the user's message in this turn contains pasted Codex output, parse that and skip Path 2.

**Detect a paste** when the message contains either:
- A JSON object with BOTH a top-level `verdict` key AND a top-level `findings` key (matches the Codex result schema), OR
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

See **Parse-failure guard** below.

**When using Path 1**, the report records `source: "user-paste"`. Pasted content cannot be auto-verified against branch/HEAD/base — also record `pasteWarning: "review provenance not auto-verified — branch/baseRef/timestamp checks skipped"`.

### Path 2: Codex job store (cross-session, on-disk scan)

If no paste detected in Path 1, scan the Codex job store on disk. This works across Claude sessions — the on-disk store is shared. Do NOT rely on `codex-companion.mjs status` for discovery: its `running`/`latestFinished`/`recent` fields are scoped to the current session's in-memory broker and will not show jobs fired from another session.

**Locate the state directory.** Use a glob so plugin renames are tolerated:

```bash
ls -d ~/.claude/plugins/data/*codex*/state 2>/dev/null | head -1
```

Typical result: `~/.claude/plugins/data/codex-openai-codex/state`. If the glob finds nothing, the codex plugin's state layout has changed — fall through to Path 3.

**List all candidate job files for this workspace.** The state dir contains one subdirectory per workspace ever used with Codex; each holds `jobs/<id>.json` files:

```bash
WORKSPACE=$(git rev-parse --show-toplevel)
ls <state-dir>/*/jobs/*.json 2>/dev/null
```

For each `.json` file, read its contents. If a file cannot be parsed as JSON, skip it and continue. Then filter the parsed jobs:
- Skip if `kind != "adversarial-review"`.
- Skip if `workspaceRoot != $WORKSPACE` — the file belongs to a different repo checkout that shares a directory basename.

**Freshness check** — for each remaining candidate, all three of these must hold:

| Condition | How to compute |
|---|---|
| `completedAt >= HEAD_committer_iso` | `HEAD_committer_iso = git log -1 --format=%cI HEAD`. ISO-8601 lexicographic comparison. |
| `result.context.branch == current_branch` | `current_branch = git branch --show-current` |
| `result.target.baseRef == "main"` | hardcoded — Step 5 always reviews against `main` |

An amended commit updates the committer timestamp without changing the tree, so it will fail the freshness check and force a re-review. This is an accepted false positive.

**If multiple jobs pass**, take the most recent by `completedAt`. Record its `id` as `codexJobId`. If none pass, fall through to Path 3.

**Read the structured result directly from the job's JSON file.** No separate fetch command is needed — the on-disk file already contains the full Codex output. Trust the structured object at `result.result` (matches the "Codex result schema" section below) when `result.parseError == null`. If `parseError != null`, fall back to parsing `result.rendered` (Markdown) using the same regex as Path 1.

**When using Path 2**, the report records the following from the chosen job JSON:

| Report field | Source on the job JSON |
|---|---|
| `source` | hardcoded `"job-store"` |
| `codexJobId` | `id` |
| `codexBaseRef` | `result.target.baseRef` |
| `codexBranch` | `result.context.branch` |
| `codexCompletedAt` | `completedAt` |

### Parse-failure guard (applies to any Markdown parse, Path 1 or Path 2)

Whenever a Markdown parse attempt yields zero findings while the text contains `[critical]`, `[high]`, `[medium]`, or `[low]` markers — regardless of which path triggered the parse — set step status `failed` with `parseError` describing the failure rather than passing silently.

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
- Parse-failure guard tripped (any Markdown parse yields zero findings while text contains severity markers) → step status = `failed`, with `parseError` populated.
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
  - `parseError`: `null` when the structured JSON result was used cleanly; a string describing the failure when any Markdown parse failed.
  - `findings`: list of `{ summary, details, file, lineStart, lineEnd, severity, confidence, tier, tierReason, recommendation, alreadyHandled }` entries
  - `classificationRules`: `{ "blockingThreshold": "severity critical/high AND confidence >= 0.70 AND not alreadyHandled", "advisoryThreshold": "severity medium/low OR confidence < 0.70 OR alreadyHandled" }`
  - `codexOutput`: full verbatim Codex rendered Markdown

---

## Step 6: Run Tests (scoped to changes)

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "tests"` → skip to next step.

**Skip if**: No testable source files changed (e.g., only SQL migrations, config, docs) → mark `not_applicable`

**Tracker signal check** (if feature ID is not `none`):
1. Read `../tenxengage-blueprint/features/{feature-id}/tracker.md`
2. Collect all story rows where `BE` = `done`
3. If ALL such rows have `BE Tests` = `green @ {sha}` AND every `{sha}` equals `git rev-parse HEAD`:
   - Update report: `tests` step `status` = `skipped-green-via-tracker`, `validatedAtCommit` = HEAD, `trackerSignal` = `{ stories: [{ id, sha }], allMatch: true }`
   - Log: `Step 6: Tests SKIPPED — all done stories verified green @ {HEAD} (source: tracker.md)`
   - Proceed to Step 7 (do NOT run `./gradlew test`)
4. Otherwise: proceed with full test run below

1. Get changed source files from the diff
2. Map source files to test files:
   - `src/main/java/.../service/QuizService.java` → `src/test/java/.../service/QuizServiceTest.java`
   - `src/main/java/.../controller/QuizController.java` → `src/test/java/.../controller/QuizControllerTest.java`
3. **Test plan alignment** (if feature ID is not `none`):
   - Check if `../tenxengage-blueprint/features/{feature-id}/test-plan.md` exists
   - If it exists: parse the integration-test tables by their **category headings** (Lifecycle & CRUD, Entity Relationships & Cascades, State Machine Transitions, Business Rule Enforcement, Multi-Entity Workflows, Contract Conformance, Tenant Isolation & Security, Audit & Events, Cross-Cutting Checks). Extract `Test Class` column values.
   - For each expected test class: check if the file exists in `src/test/java/`
   - **Auto-generate missing tests** using the scenario/assertion from the plan and the correct test pattern (Mockito → unit service tests, `AbstractLocalIntegrationTest` → all integration categories, `@WebMvcTest` → controller tests, OpenAPI validator → Contract Conformance tests wired to `../tenxengage-contracts/endpoints/{feature}.yaml`)
   - Add generated tests to the scoped test list
4. Run only relevant tests: `./gradlew test --tests "*.QuizServiceTest" --tests "*.QuizControllerTest"`
5. If entity or migration files changed, also run: `./gradlew test --tests "*IntegrationTest"`
6. **On failure**: Read the test output, auto-fix failing tests or source code, re-run

### Test Quality Sub-check (if test files are new or modified)

If changed files include test files (`*Test.java`): use the **Agent tool** to dispatch a subagent:

```
[STRUCTURED-OUTPUT]
Use the Skill tool to load the 'test-quality' skill, then review the test files below for quality issues.
Return ONLY the structured JSON findings as defined in the skill's "Structured Output Mode" section.

Files to review:
{list of changed test .java file paths}

{contents of each test file}
```

Test quality findings are **advisory only** — they do not fail this step. Append them to the report as `testQualityFindings`.

→ Update report: `tests` = passed/failed/not_applicable + testsRun + fixes + testPlanCoverage (`{ planned: N, implemented: N, generated: N, passing: N }`)

---

## Step 7: Coverage Check (scoped to changes)

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "coverage"` → skip to next step.

**Skip if**: No new methods or endpoints were added (only modifications to existing ones) → mark `not_applicable`

1. List all new/modified service classes (from diff)
2. For each **new** service method, verify a corresponding test exists
3. List all new/modified controller endpoints (from diff)
4. For each **new** endpoint, verify a `@WebMvcTest` test exists
5. **On gaps**: Report which methods/endpoints lack tests. Do NOT auto-generate tests — just report.

→ Update report: `coverage` = passed/failed/not_applicable + gaps list

---

## Step 8: Summary & Report

1. Update `review.json` with:
   - `headCommit`: current `git rev-parse HEAD`
   - `overall`: recompute from all steps' current statuses — `passed` if all steps are `passed` or `not_applicable`, `failed` if any step is `failed`, `in-progress` if any step is still `pending`
   - `updatedAt`: current timestamp

2. Write archive snapshot:
   - Derive archive filename: `review_{YYYY-MM-DD}_{short-commit}.json` where date comes from `updatedAt` and short-commit is the first 8 chars of `headCommit`
   - Write the full contents of `review.json` to `.ready-check/{branch-name}/review_{YYYY-MM-DD}_{short-commit}.json`
   - This applies to both full runs and single-step mode — every invocation produces an archive file

3. Append to `history.jsonl`:
   - Full run: `{"commit":"{hash}","timestamp":"{ISO}","trigger":"ready-check","steps":{...},"overall":"{status}","fixes":{N}}`
   - Single-step mode: `{"commit":"{hash}","timestamp":"{ISO}","trigger":"ready-check:{targetStep}","steps":{...},"overall":"{status}","fixes":{N}}`

4. **If `singleStepMode = false`** — output full summary:

```
=== READY-CHECK SUMMARY: {branch-name} (Backend) ===

  Step 1: Prerequisites         {PASSED/FAILED}
  Step 2: Code Review           {PASSED/FAILED/NOT_APPLICABLE} ({N} fixes, {N} subagents)
  Step 3: Security + API Review {PASSED/FAILED/NOT_APPLICABLE} ({N} fixes, {N} subagents)
  Step 4: Contract Compliance   {PASSED/FAILED/NOT_APPLICABLE}
  Step 5: Adversarial Review    {PASSED/FAILED/NOT_APPLICABLE/AWAITING USER REVIEW} ({N} findings)
  Step 6: Tests                 {PASSED/FAILED/NOT_APPLICABLE/SKIPPED (VIA TRACKER)} ({N} fixes)
  Step 7: Coverage              {PASSED/FAILED/NOT_APPLICABLE}

  Stages run: {N}/7 | Not applicable: {N}
  Total fixes applied: {N}
  Validated at commit: {short-hash}

  {IF ALL PASSED/NOT_APPLICABLE}:
  Ready for PR! Run /create-pr to validate and create the PR.

  {IF ANY FAILED}:
  Fix the issues above, then run /ready-check again.
  The check will resume from the earliest failed step.

  {IF ANY AWAITING USER REVIEW}:
  Step 5 needs an adversarial Codex review. Either run /codex:adversarial-review --wait --base main
  and re-run /ready-check 5, OR paste the Codex output (JSON or rendered Markdown) into your
  next message and re-run /ready-check 5. See Step 5 output above for full instructions.
```

5. **If `singleStepMode = true`** — output targeted summary:

```
=== READY-CHECK: {branch-name} (Backend) — {targetStep} ===

  Step {N}: {Step Name}    {PASSED/FAILED/NOT_APPLICABLE} ({details})

  Overall: {PASSED/FAILED/IN-PROGRESS}
  Validated at commit: {short-hash}

  {IF PASSED/NOT_APPLICABLE}:
  Step complete. Run /ready-check to run all steps.

  {IF FAILED}:
  Fix the issue above, then re-run /ready-check {targetStep}.
```

---

## Report JSON Structure

File: `.ready-check/{branch-name}/review.json`

```json
{
  "featureId": "{feature-id or none}",
  "repo": "tenxengage-backend",
  "branch": "{full-branch-name}",
  "headCommit": "{full-hash}",
  "startedAt": "{ISO}",
  "updatedAt": "{ISO}",
  "steps": {
    "prerequisites": {
      "status": "passed|failed|pending",
      "validatedAtCommit": "{hash}",
      "completedAt": "{ISO}"
    },
    "code-review": {
      "status": "passed|failed|not_applicable|pending",
      "validatedAtCommit": "{hash}",
      "completedAt": "{ISO}",
      "fixes": 0,
      "filesReviewed": ["file1.java"],
      "findings": [
        {"severity": "high", "file": "...", "rule": "...", "message": "...", "fixed": true}
      ]
    },
    "security-review": {
      "status": "passed|failed|not_applicable|pending",
      "fixes": 0,
      "findings": []
    },
    "contract-compliance": { "status": "passed|failed|not_applicable|pending" },
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
    "tests": {
      "status": "passed|failed|not_applicable|pending|skipped-green-via-tracker",
      "validatedAtCommit": "{hash}",
      "testsRun": ["QuizServiceTest"],
      "fixes": 0,
      "testQualityFindings": [],
      "trackerSignal": { "stories": [{"id": "US-NN", "sha": "{short-sha}"}], "allMatch": true }
    },
    "coverage": {
      "status": "passed|failed|not_applicable|pending",
      "gaps": []
    }
  },
  "overall": "passed|failed|in-progress",
  "lastCompletedStep": "step-name"
}
```

**Note**: `not_applicable` and `skipped-green-via-tracker` both count as passed. The `/create-pr` skill checks that no step has `status: "failed"`, `status: "pending"`, or `status: "awaiting-user-review"` before allowing PR creation. `awaiting-user-review` indicates Step 5 needs a Codex review the user has not yet provided.

---

## Step 9: Knowledge Capture

**Skip if `singleStepMode = true`** — knowledge capture only runs on full ready-check runs.

After the report is finalized, scan all findings from `review.json` for rules worth promoting to project docs. This step runs regardless of overall pass/fail — findings exist and are worth capturing even when the report fails.

### Sources to scan

- `steps.code-review.findings` (if status is not `not_applicable`)
- `steps.security-review.findings` (if present and status is not `not_applicable`)
- `steps.adversarial-review.findings` where `tier = "blocking"` (if present and status is not `not_applicable`) — blocking adversarial findings represent repeatable architectural risks worth promoting

### Curation Gate

For each finding, ask:

> *Would a competent developer following our existing docs still make this mistake?*

If **yes** → promote. If the mistake is a one-off, too file-specific to generalize → skip (leave it in `review.json` only).

Before deciding "already covered," read the target pattern file (if it exists) to check whether the rule is already present verbatim or in substance. Do not rely on memory.

### Promotion Tiers

**Tier 1 — Domain-specific, generalizable:**
- Write the finding as a generalized rule (no file-specific detail, no copy-paste from JSON)
- Append to the **"Pitfalls"** section of the relevant `docs/patterns/<domain>.md`
- If the pattern file does not exist yet, create it. Then add its entry to:
  - `CLAUDE.md` Project Standards patterns list
  - `PROJECT-CONTEXT.md` Pattern References section

**Tier 2 — Cross-cutting, reveals a convention gap:**
- Write a single generalized line
- Add it to the most relevant section in `PROJECT-CONTEXT.md`

**Tier 3 — One-off or already covered:**
- No action. Leave in `review.json` only.

### After promoting each finding

If `docs/learnings.md` does not exist, create it with this header first:

```markdown
# Learnings Log

Append-only record of findings promoted from ready-check reports to project docs.
Not referenced anywhere — exists to track the rate of new pitfalls discovered over time.
A declining number of entries per feature signals the conventions are working.
```

Then append a row:

```markdown
## YYYY-MM-DD — {feature-id}

| Rule | Category | Applied to |
|---|---|---|
| {generalized rule} | {domain or category} | {target file} |
```

Use the finding's source as the `Category` value: `kafka-events`, `tenant-isolation`, `db-performance`, `security`, `adversarial`, etc. For adversarial findings, use `adversarial` as the category.

If `docs/learnings.md` already has an entry for today's date and this feature-id, add rows to the existing table rather than creating a new `##` section.

### Output

After this step, output a brief summary:

```
  Step 9: Knowledge Capture    COMPLETE
  Promoted: N findings (N to pattern files, N to PROJECT-CONTEXT.md)
  Skipped:  N (one-offs or already covered)
  Log: docs/learnings.md
```

If no findings were promoted, output:

```
  Step 9: Knowledge Capture    COMPLETE (no new promotions)
```

**Note:** Step 9 does not write to `review.json` — it has no pass/fail gate and its execution is implicit in every full run. No `knowledge-capture` key is added to the report.
