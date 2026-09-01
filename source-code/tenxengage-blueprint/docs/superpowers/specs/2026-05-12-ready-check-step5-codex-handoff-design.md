# Ready-check Step 5: Codex adversarial-review hand-off

**Status:** draft
**Date:** 2026-05-12
**Repos affected:** `tenxengage-backend`, `tenxengage-frontend`

## Problem

Step 5 of the `ready-check` skill in both backend and frontend tells the model to:

> *"Use the **Skill tool** to invoke `codex:adversarial-review` in foreground mode."*

This has never worked, for two compounding reasons:

1. **`codex:adversarial-review` is not a skill.** The codex plugin (`openai-codex` v1.0.3) ships only three skills (`codex-cli-runtime`, `codex-result-handling`, `gpt-5-4-prompting`). `adversarial-review` is a **slash command** (`commands/adversarial-review.md`), which the `Skill` tool cannot load. Result: "skill not present" error.
2. **Even as a slash command, it's blocked from model invocation.** The command's frontmatter sets `disable-model-invocation: true`. This is intentional — Codex reviews are billed and the plugin author wants the user to fire them explicitly. The same flag is set on `review`, `cancel`, `result`, and `status`. Only `setup` and `rescue` are model-invocable.

The plugin author's design intent is clear: **don't initiate billed Codex work without explicit user say-so**. Any fix must respect that gate rather than circumvent it.

## Goal

Make Step 5 actually work, by reframing it from "Claude runs Codex" to "Claude consumes a Codex review the user has already fired."

## Out of scope

The following are deliberate non-goals for this spec. They are noted to prevent scope creep:

- **Per-feature vs per-story scoping.** Whether Step 5 should fire on every story branch or only on feature-integration branches is a separate (legitimate) design question. This spec keeps the current per-branch trigger and only fixes the hand-off mechanism.
- **Equivalent rewrite of any `/codex:review` step.** Only Step 5 (adversarial) is in scope. If a non-adversarial code-review step is added later, the same pattern will apply but is not implemented here.
- **`--accept-stale` flag for iterative fix-up commits.** Useful future enhancement when iteration noise becomes a real pain point. Not in this spec.
- **Changes to the existing tier-classification logic** (severity × confidence × `alreadyHandled` → blocking/advisory). The classification thresholds stay exactly as they are.
- **Caching across branches or auto-cleanup of the Codex job store.**

## Design

### Behavior at Step 5

When `ready-check` reaches Step 5 (and the step is applicable per the existing applicability rules), Step 5 attempts to acquire a Codex adversarial-review result via three paths, **in this order**:

1. **In-turn pasted content.** If the user's most recent message contains pasted Codex output (heuristics below), parse that and use it. Skip the job store entirely.
2. **Codex job store.** Shell out to `codex-companion.mjs status --all --json`, find a fresh adversarial-review job for the current branch and base, fetch its full result via `codex-companion.mjs result <id> --json`, parse the structured findings.
3. **Neither found → pause.** Write status `awaiting-user-review` to the report, print the pause prompt (see below), stop the ready-check run.

The user's expected workflow:

```
$ /ready-check
... (Steps 1–4 run) ...
Step 5: Adversarial Review    AWAITING USER REVIEW
  Run /codex:adversarial-review --wait --base main (or paste output), then re-run /ready-check 5.

$ /codex:adversarial-review --wait --base main
(Codex runs ~30–90s, output appears in the same conversation)

$ /ready-check 5
Step 5: Adversarial Review    PASSED (0 blocking, 2 advisory)
... continues with Steps 6–7 ...
```

**Same session vs separate session.** The user MAY run `/codex:adversarial-review` in either the same Claude session as `/ready-check` or a different one. The Codex **on-disk** job store is shared across all Claude sessions for a given workspace, so cross-session discovery is supported — but the discovery mechanism must scan disk directly, NOT call `codex-companion.mjs status`. The `status` command only surfaces jobs from the current session's in-memory broker; jobs fired in another session are invisible to it even though they live in the same on-disk state directory. The original draft of this spec assumed `status` was cross-session and was empirically refuted on 2026-05-12 — the design below reflects the correction.

### Job-store lookup (path 2)

**Discovery is filesystem-based, not via `status`.** The codex plugin persists each job to `~/.claude/plugins/data/<plugin-dir>/state/<workspace-hash>/jobs/<id>.json`. Each JSON file contains the full structured Codex output — no separate fetch is required.

**Locate the state directory** with a glob to tolerate plugin renames:

```bash
ls -d ~/.claude/plugins/data/*codex*/state 2>/dev/null | head -1
```

Typical result: `~/.claude/plugins/data/codex-openai-codex/state`. If the glob finds nothing, fall through to Path 3 — the plugin layout has changed and this skill needs updating.

**Lookup:**

1. List all `*/jobs/*.json` files under the state directory.
2. For each file, parse the JSON. Skip files that fail to parse.
3. Filter: `kind == "adversarial-review"` AND `workspaceRoot == $(git rev-parse --show-toplevel)`.
4. Apply the freshness check (below). Keep only candidates that pass all three conditions.
5. If multiple pass, take the most recent by `completedAt`. If none pass, fall through to Path 3.

**Freshness check** — all of the following must hold:

| Condition | Why |
|---|---|
| `completedAt >= HEAD_committer_iso` | Review captured the current HEAD. (Codex does not record the reviewed commit hash on the job — timestamp comparison is the proxy.) |
| `result.context.branch == git branch --show-current` | Review was for THIS branch, not a different one that happened to run more recently in the workspace. |
| `result.target.baseRef == "main"` | Review was scoped against `main`, matching ready-check's expected base. |

`HEAD_committer_iso` comes from `git log -1 --format=%cI HEAD`. ISO-8601 lexicographic comparison is correct for these timestamps.

**Edge case — amended commit with unchanged tree.** A `git commit --amend` updates the committer timestamp without changing the content. The freshness check will treat this as stale and request a re-review. This is an accepted false positive; the cost is one extra Codex run after each amend, and the alternative (tree-hash comparison) adds complexity for marginal benefit.

**Fetch the result:** the on-disk JSON IS the result. Read `result.result` directly (matches the Codex result schema). When `result.parseError != null`, fall back to parsing `result.rendered` (Markdown) with the same regex as Path 1.

**Workspace path notes.** `workspaceRoot` on each job is the result of `git rev-parse --show-toplevel` at the time Codex ran. It is the **repo root**, not the parent dir `tenxengage-application/`. Two different checkouts of the same repo (e.g., `tenxengage-application/tenxengage-backend` and `tenxengage-app/tenxengage-backend`) hash to different state subdirectories — filtering by exact `workspaceRoot` match prevents cross-checkout bleed.

### Pasted-content path (path 1)

**Detection.** If the user's most recent message body (the same turn that invokes `/ready-check 5`) contains either:

- A JSON object whose top level has a `verdict` or `findings` key matching the Codex schema, OR
- A Markdown block beginning with `# Codex Adversarial Review` (Codex's rendered output format),

treat the message as a paste and parse accordingly.

**Parsing.**

- **JSON paste:** parse with the same shape as `storedJob.result.result` (see "Codex result schema" below).
- **Markdown paste:** parse heuristically. Codex's `rendered` Markdown is consistently structured:
  ```
  - [<severity>] <title> (<file>:<line_start>-<line_end>)
    <body text>
    Recommendation: <recommendation>
  ```
  A line-oriented regex pass extracts `severity`, `title`, `file`, `line_start`, `line_end`, `body`, `recommendation`. If parsing yields zero findings but the text clearly contains issues (heuristic: the text contains `[critical]`, `[high]`, `[medium]`, or `[low]` substrings), mark Step 5 `failed` with `parseError` rather than silently passing.

**Trust caveat.** Pasted content cannot be verified against the current branch / HEAD / base. The report records the source explicitly:

- `source: "job-store"` — verified via the freshness checks.
- `source: "user-paste"` — accepted as-is; the report also records `pasteWarning: "review provenance not auto-verified — branch/baseRef/timestamp checks skipped"`.

`/create-pr` (and any other downstream consumer) treats both sources as `passed`; the warning is informational, for audit.

### Codex result schema (reference)

From `storedJob.result.result` (captured empirically on 2026-05-12):

```json
{
  "verdict": "needs-attention",
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

Also useful:

- `storedJob.result.context.branch` — branch the review ran on.
- `storedJob.result.target.baseRef` — the `--base` ref.
- `storedJob.result.parseError` — `null` on clean JSON parse; populated if Codex returned malformed JSON. Step 5 should fall back to parsing `storedJob.rendered` (the Markdown form) only when `parseError != null`.

### Field-name mapping

The current ready-check report uses different field names than Codex's output. Step 5 maps them:

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

### `alreadyHandled` derivation

Codex does not emit `alreadyHandled`. The current Step 5 tier-classification rules use it, and that mechanic is unchanged: for each finding, Step 5 inspects the code at `file:line_start-line_end` (and its immediate surroundings) and sets `alreadyHandled` based on whether the risk is already mitigated in the surrounding code. This logic was always intended to be derived by Claude, not provided by Codex — this spec just calls it out explicitly because the broken old wording could be misread otherwise.

### Tier classification (unchanged)

```
blocking  = severity in {critical, high} AND confidence >= 0.70 AND alreadyHandled == false
advisory  = everything else (lower severity, or lower confidence, or already handled)
```

Step status:

- Any blocking findings → step = `failed`.
- Only advisory findings, or no findings → step = `passed`.

### New report status: `awaiting-user-review`

A new status value, in addition to existing `passed`/`failed`/`not_applicable`/`pending`/`skipped-green-via-tracker`:

- **`awaiting-user-review`** — set when Step 5 needs Codex output it cannot acquire. Treated as **not passed** by downstream consumers (notably `/create-pr`), distinct from `failed` (no completed review found) and from `pending` (step not yet attempted in this run).

### Pause prompt

```
Step 5: Adversarial Review    AWAITING USER REVIEW

  Step 5 needs an adversarial Codex review of this branch's diff against main.
  Either:

  (a) Run the slash command and re-run ready-check:
      /codex:adversarial-review --wait --base main
      /ready-check 5

  (b) Paste the Codex review output (JSON or the rendered Markdown) into your
      next message, then re-run /ready-check 5. The pasted form is accepted but
      cannot be auto-verified against this branch.

  Same-session is fine — Codex's job store is workspace-keyed, not session-keyed.
```

### Report JSON additions

Adversarial-review report entry gains:

```json
{
  "status": "passed|failed|not_applicable|pending|awaiting-user-review",
  "validatedAtCommit": "{hash}",
  "source": "job-store|user-paste",
  "codexJobId": "review-mp1qahx9-i3f3r9",
  "codexBaseRef": "main",
  "codexBranch": "feature/foo",
  "codexCompletedAt": "2026-05-11T21:44:05.600Z",
  "pasteWarning": "review provenance not auto-verified...",
  "parseError": null,
  "findings": [ /* unchanged shape, mapped from Codex */ ],
  "classificationRules": { /* unchanged */ },
  "codexOutput": "<full verbatim Codex rendered Markdown>"
}
```

- `source`, `codexJobId`, `codexBaseRef`, `codexBranch`, `codexCompletedAt` — present only on `source == "job-store"`.
- `pasteWarning` — present only on `source == "user-paste"`.
- `parseError` — set only when the Markdown fallback parser was used and failed.

## Files to change

1. **`tenxengage-application/tenxengage-backend/.claude/skills/ready-check/SKILL.md`** — rewrite Step 5 (currently lines ~226–300) and the corresponding entries in the report-JSON-structure section and the Step 8 summary template.
2. **`tenxengage-application/tenxengage-frontend/.claude/skills/ready-check/SKILL.md`** — same rewrite (currently lines ~193–265). Frontend keeps its existing applicability rule (`Changed files include components, pages, or hooks`); backend keeps its existing rule (`Changed files include service or controller logic`). The body of Step 5 — including the new hand-off mechanics — is otherwise identical between repos.
3. **`/create-pr` skill (or whichever skill enforces "all steps passed" before opening a PR)** — recognize `awaiting-user-review` as not-passed. Exact location not yet identified; implementation must grep for the status check.

## Open items (resolve during implementation)

- **Exact location of the `/create-pr` "all-steps-passed" gate.** Need to grep for status comparisons (`status == "failed"`, `status == "pending"`, etc.) across the skill files and update the check.
- **`/ready-check 5 --feature-complete` flag.** Out of scope for this spec, but the rewrite should leave Step 5's structure friendly to a later applicability-rule change.

## Verification

The implementation is verified by:

1. **Happy path (job store).** On a feature branch, run `/codex:adversarial-review --wait --base main`, then `/ready-check 5`. Step 5 detects the fresh job, parses findings, classifies, writes report, prints summary.
2. **Pause path.** On a clean branch with no Codex job, run `/ready-check 5`. Step 5 sets status `awaiting-user-review`, prints the pause prompt, stops.
3. **Stale path.** On a branch where a Codex job ran and then a new commit was added, run `/ready-check 5`. Step 5 detects the stale job (failed freshness check), falls through to the pause prompt.
4. **Paste path (JSON).** Paste a JSON Codex result block into the message and run `/ready-check 5`. Step 5 detects the paste, parses, classifies, writes report with `source: "user-paste"` and `pasteWarning`.
5. **Paste path (Markdown).** Same as above with the rendered Markdown form.
6. **Different branch.** Run Codex on branch A, switch to branch B, run `/ready-check 5` on B. Step 5 rejects the cross-branch job via the `context.branch` check.
7. **`/create-pr` blocks.** On a branch where Step 5 is `awaiting-user-review`, run `/create-pr`. It must refuse to open the PR.

## Empirical reference (probe captured 2026-05-12)

Confirmed via a temporary worktree on `tenxengage-backend`:

- `status --all --json` returns `{ workspaceRoot, config, sessionRuntime, running[], latestFinished, recent[], needsReview }`.
- Per-job fields: `id`, `kind`, `kindLabel`, `title`, `workspaceRoot`, `jobClass`, `summary`, `write`, `sessionId`, `status`, `startedAt`, `phase`, `pid`, `logFile`, `threadId`, `turnId`, `completedAt`, `createdAt`, `updatedAt`, `elapsed`, `duration`, `progressPreview`.
- **No `headCommit` field on the job.** Forced the timestamp-based freshness check.
- `result <id> --json` returns the structured object under `storedJob.result.result` documented above.
- A successful run with two synthetic findings (one critical SQL-injection, one high resource-leak) emitted clean JSON with `parseError == null`.
