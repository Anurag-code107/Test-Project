### 7.5. Ready-check (scoped quality gate)

Run a scoped ready-check on the sub-branch to catch fixable issues before the developer approval pause. Auto-fixes land on this sub-branch and get squashed cleanly into the feature branch at Step 9.

**Scope:** Skip Step 1 (prerequisites — `./gradlew check` just ran in Step 7) and Step 6 (tests — full suite just ran). Run Steps 2, 3, 4, 5, 7 only (code-review, security+API review, contract-compliance, adversarial-review, coverage).

Load and invoke the `ready-check` skill with `--base-branch` so the Codex review and changed-files scoping are both limited to this story's diff. If `$SOFT_STAGES` is non-empty, append `--soft-stages=$SOFT_STAGES` to forward soft-stage promotion (the orchestrator uses this to soften specific review stages on multi-story runs):

```
/ready-check {feature-id} --base-branch features/{feature-id}{IF $SOFT_STAGES non-empty: ` --soft-stages=$SOFT_STAGES`}
```

All promotion-pass auto-fixes are applied inline to files on the current sub-branch (ready-check commits them automatically before its Codex step).

**Capture for Step 8:**
- `$RC_FIXES` = count of auto-fixes applied
- `$RC_MANUAL` = list of `{ severity, file, line, rule, manualAction }` for critical/high non-fixable findings (after promotion pass)
- `$RC_BLOCKED` = true if any ready-check step returned `failed` status

If `$RC_BLOCKED` is true, **do not abort** — surface the blocking detail in the Step 8 approval message and let the developer decide.

**Autonomous fix loop (subagent only — skip if running in main session):** Fix anything flagged by any review type (code quality, security, contracts, adversarial) — not just ready-check failures.

**Fix rules:**
- Fix all fixable findings from any review type.
- Two valid solutions: pick the simpler/recommended one; document the choice in the commit message.
- Cross-file changes within story scope are allowed (files already in the implementation diff).
- Shared infrastructure file NOT in the original diff (e.g., SecurityConstants, base classes, common config): do NOT fix autonomously — add to `$RC_MANUAL` and continue.
- Contracts repo change needed: follow the contracts ritual below.

**Contracts ritual:** Read `references/contracts-ritual.md` and follow it.

**Loop:**
1. Fix on the current branch (per rules above)
2. Re-run `./gradlew test`
3. Re-run ready-check

Repeat up to 3 total ready-check attempts. If still blocked after 3 attempts, or if the blocker requires a design decision, set `status=blocked` in the subagent return value — the main session (Step 4.5) handles surfacing this to the developer.

**Anti-pattern checklist pass (mandatory before status=pass).** Re-walk the diff (`git diff features/$FEATURE_SLUG..HEAD`) against the anti-pattern checklist built in Step 5. For each item, confirm compliance explicitly: no `BusinessRuleException`/domain exception thrown without a stable `errorCode`; no response DTO field typed `Object`; all multi-wire-format inbound JSON normalized at the boundary. Any violation in files within story scope MUST be fixed before returning. If a violation sits in a shared file outside the diff, add it to `$RC_MANUAL` with the PROJECT-CONTEXT rule cited AND list it in the `antipattern_pass` field below — that field is echoed into the orchestrator/headless return (Step 12), so it stays visible even when the per-story approval gate is skipped. Record the pass in the return JSON as `"antipattern_pass": "clean"` or list the unresolved items.

**Data-exposure-and-hydration pass (mandatory before status=pass).** If this story backs a user-facing UI element (detail/view surface or composite element — drawer, multi-section panel, detail page, card, dashboard, table), confirm the backend actually serves the data that element must render: the response DTO **exposes every field/section** the element's completeness contract (from the spec / `{{Entity}}DetailResponse`) lists, AND the service **populates them with rendered values** — every referenced display datum is hydrated (names/descriptions resolved for any `refId`), with no bare `refId`-only entries and no contract field left null/blank that the UI is meant to show. A DTO that is shape-correct but value-empty (right fields, blank or `refId`-only values because the service never hydrated them — the LP-drawer service-hydration miss) is a blocker: fix it in-scope before returning, or add it to `$RC_MANUAL` if the gap sits in a shared file outside the diff.

## Return

Produce and return the structured JSON result:
```json
{
  "status": "pass or blocked",
  "summary": "2–3 sentence summary of DTOs/services/endpoints/tests created",
  "rc_fixes": "<integer count of ready-check fix iterations>",
  "rc_manual": [],
  "rc_blocked": false,
  "rc_blocked_detail": null,
  "test_result": "green",
  "antipattern_pass": "clean",
  "diff_stat": "git diff --stat features/$FEATURE_SLUG..HEAD output (semicolon-joined)"
}
```

Do not read any further step files.
