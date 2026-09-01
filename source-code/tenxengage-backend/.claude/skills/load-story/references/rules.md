# Rules

- **One story-layer per invocation.** `/load-story 002 US-01` does the BE half. A separate invocation in the frontend repo does the FE half.
- **Working directory invariant.** This skill runs in the **backend** repo. All blueprint operations MUST use `git -C ../tenxengage-blueprint` — never `cd ../tenxengage-blueprint`. Work branches (`work/*`) must NEVER be created in the blueprint repo. The CWD guard in Step 4 enforces this; if it fires, fix CWD before continuing.
- **Never push the sub-branch.** Local only.
- **Never auto-merge when `$GATE == every`.** Mandatory developer approval pause under manual/gate=every invocation. When `$GATE != every`, the orchestrator drives merge via the structured return summary — no chat pause.
- **Tracker claims come first.** Flip to `in-progress` before touching code.
- On `change X`: stay on the same sub-branch. The feature-branch history stays clean via squash.
- On resume (previously in-progress or blocked): read tracker, check out existing sub-branch, continue from first unchecked execution-checklist BE item.
- If a prior story's BE is not yet `done`, abort with a clear dep message — do not implement out of order. FE status of dependencies does not gate BE work.
- **TDD-only invocations.** `--tdd` is the sole switch for Red/Green/Refactor and the four conditional skill invocations. When `$USE_TDD = true`, follow RGR and invoke the four superpowers skills at the points noted in Steps 6–8: `test-driven-development` (start of Step 6), `systematic-debugging` (red test in Step 7), `verification-before-completion` (start of Step 8), `requesting-code-review` (Step 8's approval-pause message). When `$USE_TDD = false`, none of the above applies — no RGR, no auto-invocation of those skills, regardless of any other superpowers skill that may be active.
