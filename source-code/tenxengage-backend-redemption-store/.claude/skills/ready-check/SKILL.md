---
name: "ready-check"
description: "Sequential quality gate: dispatches Code Quality skills as parallel subagents, collects structured findings, applies fixes, then runs tests and coverage checks. Tracks progress in a report for resume support."
argument-hint: "Optional: [feature-id] [step:<N-or-name>] [--base-branch <branch>]"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Step 0: Determine Context & Load Report

1. **Parse `--base-branch`**:
   - Scan `$ARGUMENTS` for `--base-branch <value>`; exclude it from all other argument parsing
   - If found: store as `baseBranch`; run `git show-ref --verify refs/heads/<value>` — if exit code non-zero, **abort**: "Error: branch '<value>' not found locally. Pass a branch that exists in this repo."
   - If not found: set `baseBranch = null`

1b. **Parse `--soft-stages`**:
   - Scan `$ARGUMENTS` for `--soft-stages=<csv>`; exclude it from all other argument parsing.
   - If found: split on commas → `$SOFT_STAGES` list (valid values: `prerequisites`, `code-review`, `security-review`, `contract-compliance`, `adversarial-review`, `tests`, `coverage`).
   - If not found: `$SOFT_STAGES` empty (all stages strict, today's behavior).
   - Validate each value is a known stage; if not, abort with: "Error: unknown stage in --soft-stages: <value>".

2. **Infer story context from current branch**:
   - Run `git branch --show-current` → `currentBranch`
   - If `currentBranch` matches `work/{slug}-{US-NN}-be`: extract `storyId = US-NN`; read `../tenxengage-blueprint/features/{slug}/tracker.md` and find the Title column for `storyId` → `storyTitle`
   - Otherwise: set `storyId = null`, `storyTitle = null`

3. **Pre-commit uncommitted developer work**:
   - Run `git status --porcelain`
   - If output is non-empty (working tree dirty):
     - Run `git add -u`
     - If `storyId` is not null: commit with message `{storyId} BE: {storyTitle}`
     - Otherwise: commit with message `wip: pre-ready-check snapshot`
     - If commit fails (non-zero exit): **abort** with the git error — do not proceed with dirty state
   - If output is empty: skip this sub-step

4. **Get branch name**: `currentBranch` already captured above — use as the report key (report path)

5. **Detect feature ID** (for spec/contract lookup — separate from report path):
   - If user provided a feature ID, use it
   - If branch matches `features/*` pattern: parse `features/<slug>` → `<slug>`
   - Otherwise: ask the user for the feature ID
   - **Feature ID can also be `none`** — for bug fixes or changes not tied to a spec

6. **Detect step selector** (for single-step mode):
   - Scan `$ARGUMENTS` for a token that is either:
     - A bare integer 1–7 (Step 9 cannot be targeted individually — it only runs on full runs), OR
     - A known slug: `prerequisites`, `code-review`, `security-review`, `contract-compliance`, `adversarial-review`, `tests`, `coverage`, OR
     - Any of the above prefixed with `step:` (e.g., `step:3`, `step:code-review`)
   - Step number → slug: 1=prerequisites, 2=code-review, 3=security-review, 4=contract-compliance, 5=adversarial-review, 6=tests, 7=coverage
   - If found: set `targetStep = <resolved-slug>` and `singleStepMode = true`; exclude the token from feature ID parsing
   - If not found: set `singleStepMode = false`; full run proceeds normally

7. **Load or create report**:
   - Report path uses the **full branch name**: `.ready-check/{currentBranch}/review.json`
   - If exists: load it, check which steps are already passed
   - If not exists: create the directory and initialize a new report

8. **Get current HEAD commit**: `git rev-parse HEAD`

9. **Determine resume point**:
   - For each previously passed step, check if relevant files changed since `validatedAtCommit`
   - Use `git diff {validatedAtCommit} HEAD --name-only` to find changed files
   - If files relevant to a passed step changed, invalidate that step and all subsequent steps
   - Resume from the earliest invalidated or pending step

10. **Get changed files** (for scoping reviews):
    - If `baseBranch` is not null: `git diff {baseBranch}...HEAD --name-only --diff-filter=ACMR`
    - Otherwise: `git diff main --name-only --diff-filter=ACMR`

11. **Determine which stages apply** (see "Stage Applicability" section below)

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

→ Determine outcome:
  - If passed → mark `prerequisites` = passed.
  - If failed AND `prerequisites` IN $SOFT_STAGES → mark `prerequisites` = advisory; append findings summary to `report.advisory_findings` array.
  - If failed AND `prerequisites` NOT IN $SOFT_STAGES → mark `prerequisites` = failed.
→ Update report: `prerequisites` with the determined outcome.

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
2. **autoFixable promotion pass** — for each finding where `autoFixable` is `false`, re-evaluate:
   - Promote to `true` if ALL of: fix is a text substitution, annotation addition, or line change inside the same file; exactly one correct fix; no new file; no Flyway migration; no caller analysis needed; suggestion contains no "OR" or competing options
   - Leave as `false` if any of: fix says "OR", "consider", "decide"; requires a new file; requires a Flyway migration or DB schema change; requires caller analysis
3. For **all** findings where `autoFixable` is `true` (any severity, after promotion):
   - Read the target file
   - Apply the fix
   - Increment fix counter
4. For critical/high findings where `autoFixable` is `false` (after promotion): note as manual action items
   (medium/low non-fixable: advisory only — do not block)

### Re-validate Phase (if fixes were applied)

If any files were modified in the fix phase: re-read those files and verify the issues are resolved. If a fix introduced a new issue, fix that too.

→ Determine outcome:
  - If passed or not_applicable → mark `code-review` = passed/not_applicable.
  - If failed AND `code-review` IN $SOFT_STAGES → mark `code-review` = advisory; append findings to `report.advisory_findings` array.
  - If failed AND `code-review` NOT IN $SOFT_STAGES → mark `code-review` = failed.
→ Update report: `code-review` with the determined outcome.
  - `fixes`: total auto-fixes applied
  - `filesReviewed`: list of .java files reviewed
  - `findings`: ALL findings from the Review Phase at every severity (critical/high/medium/low). **Never remove a finding from this list.** For each finding that was auto-fixed: set `"fixed": true`. For findings that were not auto-fixed: set `"fixed": false`. The `fixed` flag is the sole indicator of disposition — removal is not permitted.

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
2. **autoFixable promotion pass** — for each finding where `autoFixable` is `false`, re-evaluate:
   - Promote to `true` if ALL of: fix is a text substitution, annotation addition, or line change inside the same file; exactly one correct fix; no new file; no Flyway migration; no caller analysis needed; suggestion contains no "OR" or competing options
   - Leave as `false` if any of: fix says "OR", "consider", "decide"; requires a new file; requires a Flyway migration or DB schema change; requires caller analysis
3. For **all** findings where `autoFixable` is `true` (any severity, after promotion): apply fix, increment counter
4. For critical/high findings where `autoFixable` is `false` (after promotion): note as manual action items
   (medium/low non-fixable: advisory only — do not block)

→ Determine outcome:
  - If passed or not_applicable → mark `security-review` = passed/not_applicable.
  - If failed AND `security-review` IN $SOFT_STAGES → mark `security-review` = advisory; append findings to `report.advisory_findings` array.
  - If failed AND `security-review` NOT IN $SOFT_STAGES → mark `security-review` = failed.
→ Update report: `security-review` with the determined outcome.
  - `fixes`: total auto-fixes applied
  - `findings`: ALL findings from the Review Phase at every severity (critical/high/medium/low). **Never remove a finding from this list.** For each finding that was auto-fixed: set `"fixed": true`. For findings not auto-fixed: set `"fixed": false`. The `fixed` flag is the sole indicator of disposition — removal is not permitted.

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

→ Determine outcome:
  - If passed or not_applicable → mark `contract-compliance` = passed/not_applicable.
  - If failed AND `contract-compliance` IN $SOFT_STAGES → mark `contract-compliance` = advisory; append findings to `report.advisory_findings` array.
  - If failed AND `contract-compliance` NOT IN $SOFT_STAGES → mark `contract-compliance` = failed.
→ Update report: `contract-compliance` with the determined outcome.

---

## Pre-Step 5: Commit auto-fixes

Before running the Codex review, ensure all auto-fixes from Steps 2–4 are committed so Codex can see them.

- Run `git status --porcelain`
- If output is non-empty:
  - Run `git add -u`
  - If `storyId` is not null: commit with message `chore: ready-check auto-fixes — {storyId}`
  - Otherwise: commit with message `chore: ready-check auto-fixes`
  - If commit fails: mark step `adversarial-review` as `failed` with note "auto-fixes commit failed before Codex review" and stop the run
- If output is empty: skip

---

## Step 5: Adversarial Review

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "adversarial-review"` → skip to next step.

**Skip if**: Only test files, config files, migration SQL, or documentation changed → mark `not_applicable`

Resolve the Codex invocation arguments using the following table (evaluate top-to-bottom; first matching row wins), then invoke the slash command directly. Wait for Codex to return its full structured output. If Codex fails to complete (error or timeout), mark step `failed` with a note and stop the run.

| Condition | Codex invocation |
|---|---|
| `baseBranch` is set (from `--base-branch`) | `/codex:adversarial-review --wait --base {baseBranch}` |
| `--base <sha>` was passed explicitly in `$ARGUMENTS` | `/codex:adversarial-review --wait --base <sha>` |
| Neither, and working tree is dirty (defensive fallback — normally unreachable after Pre-Step 5) | `/codex:adversarial-review --wait --scope working-tree` |
| Neither, and working tree is clean | `/codex:adversarial-review --wait --base HEAD~1` |

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

The Fix Phase below handles findings that meet the strict `autoFixable` criteria. Findings that do not meet the criteria are left as manual action items — they never trigger a Codex re-invocation inside this run.

### Fix Phase (sequential, main agent)

After classification:

1. **autoFixable promotion pass** — for each finding, evaluate against the same criteria used by Steps 2 and 3:
   - Promote to `autoFixable = true` if ALL of: fix is a text substitution, annotation addition, or line change inside the same file; exactly one correct fix; no new file; no Flyway migration; no caller analysis needed; suggestion contains no "OR" or competing options
   - Leave as `false` if any of: fix says "OR", "consider", "decide"; requires a new file; requires a Flyway migration or DB schema change; requires caller analysis
2. For **all** findings where `autoFixable` is `true` (after promotion) AND `tier == "blocking"`:
   - Read the target file
   - Apply the fix
   - Increment fix counter
3. For findings where `autoFixable` is `false` AND `tier == "blocking"`: note as manual action items (they will surface in the Output section)
4. Advisory findings are never auto-fixed regardless of `autoFixable` — they are reported only.

**Loop guard — MANDATORY:** During this Fix Phase and the Re-validate Phase below, the agent MUST NOT re-invoke Codex (`/codex:adversarial-review` or equivalent). Verification is performed by the main agent re-reading the modified files. Findings that re-surface, new findings that emerge from the fixes, and any deeper architectural chain are explicitly out of scope for this run; they belong to the next ready-check cycle. This guard exists because Codex traces architectural chains across re-invocations — re-running it after fixes routinely produces a new tier of findings (new files, migrations, caller-analysis changes) that the `autoFixable` filter would correctly reject anyway. Breaking the chain at the first link is the design.

### Re-validate Phase (if fixes were applied)

If any files were modified in the Fix Phase:
1. Re-read each modified file.
2. For each fix applied, confirm the changed lines reflect the intended substitution and the surrounding code still parses (visual inspection — no Codex, no compiler invocation in this phase).
3. If a fix appears to have landed wrong (wrong line, syntactic damage), revert that one fix by re-editing the file back, and move the finding to manual action items.

Do NOT invoke Codex. Do NOT re-classify. The Re-validate Phase is bounded to the files this Fix Phase touched.

### Step Status

Determine status based on findings *and* what the Fix Phase did:

- No blocking findings (only advisory or none) → step status = `passed`.
- Blocking findings present AND every blocking finding was auto-fixed in the Fix Phase (none remain as manual action items) → step status = `passed`. The fact that fixes were applied is surfaced in the Output header and the report's `fixes` count — the step does NOT fail just because Codex initially flagged blocking findings.
- Blocking findings present AND at least one remains as a manual action item → step status = `failed`.
- Parse-failure guard tripped (any Markdown parse yields zero findings while text contains severity markers) → step status = `failed`, with `parseError` populated.

### Output

**When step status = `failed` (at least one blocking finding remains as manual action item):**

```
  Step 5: Adversarial Review    FAILED (N blocking remaining, N auto-fixed, N advisory)

  BLOCKING — MANUAL ACTION REQUIRED (severity critical/high + confidence ≥ 0.70 — must fix before PR):
  ✗ [critical | conf: 0.85] SomeFile.java:42-55
    Risk: <body>
    Fix:  <recommendation>
    Not auto-fixed: <reason — e.g., requires new file / Flyway migration / caller analysis>

  AUTO-FIXED (applied during Fix Phase — re-read the file before pushing to confirm the change landed correctly):
  ✓ [critical | conf: 0.80] SomeFile.java:32-34
    Fix applied: <substitution>

  ADVISORY (lower severity or confidence — review recommended, does not block):
  ⚠ [medium | conf: 0.55] SomeFile.java:120-134
    Risk: <body>
    Fix:  <recommendation>

  Classification: blocking = severity critical/high AND confidence ≥ 0.70 AND not alreadyHandled
  Source: codex (direct)
  Fix remaining blocking issues manually, then re-run: /ready-check adversarial-review
```

**When step status = `passed` AND auto-fixes were applied (every blocking finding was resolved in the Fix Phase):**

```
  Step 5: Adversarial Review    PASSED (0 blocking remaining, N auto-fixed, N advisory)

  AUTO-FIXED (applied during Fix Phase — re-read the file before pushing to confirm the change landed correctly):
  ✓ [critical | conf: 0.80] SomeFile.java:32-34
    Fix applied: <substitution>

  ADVISORY (does not block):
  ⚠ [medium | conf: 0.45] SomeFile.java:67-72
    Risk: <body>
    Fix:  <recommendation>

  Classification: blocking = severity critical/high AND confidence ≥ 0.70 AND not alreadyHandled
  Source: codex (direct)
```

**When step status = `passed` AND no fixes were applied (only advisory findings or none):**

```
  Step 5: Adversarial Review    PASSED (0 blocking remaining, 0 auto-fixed, N advisory)

  ADVISORY (does not block):
  ⚠ [medium | conf: 0.45] SomeFile.java:67-72
    Risk: <body>
    Fix:  <recommendation>

  Classification: blocking = severity critical/high AND confidence ≥ 0.70 AND not alreadyHandled
  Source: codex (direct)
```

→ Determine outcome:
  - If passed or not_applicable → mark `adversarial-review` = passed/not_applicable.
  - If failed AND `adversarial-review` IN $SOFT_STAGES → mark `adversarial-review` = advisory; append findings to `report.advisory_findings` array.
  - If failed AND `adversarial-review` NOT IN $SOFT_STAGES → mark `adversarial-review` = failed.
→ Update report: `adversarial-review` =
  - `status`: determined outcome above (`passed`/`failed`/`advisory`/`not_applicable`)
  - `validatedAtCommit`: current `git rev-parse HEAD` (after the Fix Phase commits, if any fixes landed — otherwise the same SHA Codex reviewed)
  - `source`: `"codex"` (always, when step ran)
  - `parseError`: `null` when the structured JSON result was used cleanly; a string describing the failure when any Markdown parse failed.
  - `fixes`: count of auto-fixes applied during the Step 5 Fix Phase (after the Re-validate Phase's revert-on-damage check). `0` when no fixes were applied.
  - `findings`: list of `{ summary, details, file, lineStart, lineEnd, severity, confidence, tier, tierReason, recommendation, alreadyHandled, autoFixable, autoFixed }` entries. `autoFixable` and `autoFixed` are populated by the Fix Phase: `autoFixable` is the promotion-pass verdict, `autoFixed` is `true` only if the fix was applied AND survived the Re-validate Phase.
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

→ Determine outcome:
  - If passed or not_applicable → mark `tests` = passed/not_applicable.
  - If failed AND `tests` IN $SOFT_STAGES → mark `tests` = advisory; append findings to `report.advisory_findings` array.
  - If failed AND `tests` NOT IN $SOFT_STAGES → mark `tests` = failed.
→ Update report: `tests` with the determined outcome + testsRun + fixes + testPlanCoverage (`{ planned: N, implemented: N, generated: N, passing: N }`).

---

## Step 7: Coverage Check (scoped to changes)

**Skip if single-step mode and not the target**: `singleStepMode = true` and `targetStep ≠ "coverage"` → skip to next step.

**Skip if**: No new methods or endpoints were added (only modifications to existing ones) → mark `not_applicable`

1. List all new/modified service classes (from diff)
2. For each **new** service method, verify a corresponding test exists
3. List all new/modified controller endpoints (from diff)
4. For each **new** endpoint, verify a `@WebMvcTest` test exists
5. **On gaps**: Report which methods/endpoints lack tests. Do NOT auto-generate tests — just report.

→ Determine outcome:
  - If passed or not_applicable → mark `coverage` = passed/not_applicable.
  - If failed AND `coverage` IN $SOFT_STAGES → mark `coverage` = advisory; append findings to `report.advisory_findings` array.
  - If failed AND `coverage` NOT IN $SOFT_STAGES → mark `coverage` = failed.
→ Update report: `coverage` with the determined outcome + gaps list.

---

## Step 8: Summary & Report

1. Update `review.json` with:
   - `headCommit`: current `git rev-parse HEAD`
   - `overall`: recompute from all steps' current statuses — `passed` if all strict (non-soft) steps are `passed` or `not_applicable`, `failed` if any strict step is `failed`, `in-progress` if any step is still `pending`. `advisory` outcomes do NOT block overall pass.
   - `advisory_findings`: accumulated array of findings from all soft-stage failures this run.
   - `updatedAt`: current timestamp

1b. **Write advisory findings file** (if any advisory outcomes exist):
   - If `report.advisory_findings` is non-empty:
     ```bash
     echo "$ADVISORY_FINDINGS_JSON" > ".ready-check/$currentBranch/advisory.json"
     echo "Advisory findings written to .ready-check/$currentBranch/advisory.json"
     ```
   - `$ADVISORY_FINDINGS_JSON` is the JSON serialization of the `advisory_findings` array.
   - If `report.advisory_findings` is empty: skip this sub-step.

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
  Step 5: Adversarial Review    {PASSED/FAILED/NOT_APPLICABLE} ({N} blocking remaining, {N} auto-fixed, {N} advisory)
  Step 6: Tests                 {PASSED/FAILED/NOT_APPLICABLE/SKIPPED (VIA TRACKER)} ({N} fixes)
  Step 7: Coverage              {PASSED/FAILED/NOT_APPLICABLE}

  Stages run: {N}/7 | Not applicable: {N}
  Total fixes applied: {N}  (= code-review.fixes + security-review.fixes + adversarial-review.fixes + tests.fixes)
  Validated at commit: {short-hash}

  {IF ALL PASSED/NOT_APPLICABLE}:
  Ready for PR! Run /create-pr to validate and create the PR.

  {IF ANY ADVISORY}:
  Stages with advisories (non-blocking): {comma-separated list of advisory stage names}
  Advisory findings: .ready-check/{branch-name}/advisory.json

  {IF ANY FAILED}:
  Fix the issues above, then run /ready-check again.
  The check will resume from the earliest failed step.

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
      "status": "passed|failed|not_applicable|pending",
      "validatedAtCommit": "{hash}",
      "source": "codex",
      "parseError": null,
      "fixes": 0,
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
          "alreadyHandled": false,
          "autoFixable": false,
          "autoFixed": false
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

**Note**: `not_applicable` and `skipped-green-via-tracker` both count as passed. The `/create-pr` skill checks that no step has `status: "failed"` or `status: "pending"` before allowing PR creation.

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
