### 9.5. Ready-check (scoped quality gate)

**Only runs when `BE cell = done` (not in scaffold-and-wait mode).** This step never executes if Step 8 exited early.

Run a scoped ready-check on the sub-branch before the developer approval pause. Auto-fixes land on this sub-branch and get squashed at Step 11.

**Scope:** Skip prerequisites (no equivalent compile step). Skip tests (Vitest and Playwright just ran). Run: code-review, ui-ux-review, security-review, adversarial-review, coverage steps.

Load and invoke the `ready-check` skill with `--base-branch` so the Codex review and changed-files scoping are both limited to this story's diff:

```
/ready-check {feature-id} --base-branch features/{feature-id} --soft-stages=$SOFT_STAGES
```

All promotion-pass auto-fixes are applied inline on the current sub-branch (ready-check commits them automatically before its Codex step).

**Capture for Step 10:**
- `$RC_FIXES` = count of auto-fixes applied
- `$RC_MANUAL` = list of `{ severity, file, line, rule, manualAction }` for critical/high non-fixable findings
- `$RC_BLOCKED` = true if any ready-check step returned `failed` status

If `$RC_BLOCKED` is true, do not abort — surface the detail in the Step 10 approval message.

**Autonomous fix loop (subagent only — skip if running in main session):** Fix anything flagged by any review type (code quality, security, contracts, adversarial) — not just ready-check failures.

**Fix rules:**
- Fix all fixable findings from any review type.
- Two valid solutions: pick the simpler/recommended one; document the choice in the commit message.
- Cross-file changes within story scope are allowed (files already in the implementation diff).
- Shared infrastructure file NOT in the original diff (e.g., shared hooks, global providers, common config): do NOT fix autonomously — add to `$RC_MANUAL` and continue.
- Contracts repo change needed: follow the contracts ritual below.

**Contracts ritual:** Read `references/contracts-ritual.md` and follow it.

**Loop:**
1. Fix on the current branch (per rules above)
2. Re-run `npm run test`
3. Re-run ready-check

Repeat up to 3 total ready-check attempts. If still blocked after 3 attempts, or if the blocker requires a design decision, set `status=blocked` in the subagent return value — the main session (Step 4.5) handles surfacing this to the developer.

**Anti-pattern checklist pass (mandatory before status=pass).** Re-walk `git diff features/$FEATURE_SLUG..HEAD` against the Step-5 anti-pattern checklist. Confirm each item explicitly; fix any in-scope violation before returning; add out-of-scope violations (shared file outside the diff) to `$RC_MANUAL` citing the PROJECT-CONTEXT rule AND list them in the `antipattern_pass` field — that field is echoed into the orchestrator/headless return (Step 14), so it stays visible even when the per-story approval gate is skipped. Add `"antipattern_pass"` to the return JSON (`clean` or the list of unresolved items).

**Element-completeness pass (mandatory before status=pass).** For each content-bearing/composite element this story builds (detail page, drawer, multi-section panel, card, dashboard, table), confirm the built UI actually renders every section + content the element-completeness contract enumerates (the story's display-level ACs + the spec's `## Frontend Specification` Key Components entry for this element), performs its specified interactions, and meets the repo's accessibility/responsive conventions — running `ui-ux-review` over the diff is the right tool to judge this; defer to it rather than re-checking by eye. An element that ships missing sections/content the contract lists (the "threadbare drawer") is a blocker: fix it in-scope before returning, or add it to `$RC_MANUAL` if it lands in a shared file outside the diff. (Don't re-verify loading/empty/error here — those are already covered above.)

## Return

Produce and return the structured JSON result:
```json
{
  "status": "pass or blocked or scaffold-and-wait",
  "summary": "2–3 sentence summary of types/hooks/components/pages/E2E created",
  "rc_fixes": "<integer count of ready-check fix iterations>",
  "rc_manual": [],
  "rc_blocked": false,
  "rc_blocked_detail": null,
  "vitest_result": "green",
  "playwright_result": "green or deferred",
  "antipattern_pass": "clean",
  "diff_stat": "git diff --stat features/$FEATURE_SLUG..HEAD output (semicolon-joined)"
}
```

Do not read any further step files.
