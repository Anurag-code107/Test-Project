# Rules

- **One story-layer per invocation.** This skill does the FE half only.
- **Scaffold-and-wait is correct.** If BE is not done, stopping at Vitest-green with the sub-branch preserved is the expected state — not a failure.
- **Working directory invariant.** This skill runs in the **frontend** repo. All blueprint operations MUST use `git -C ../tenxengage-blueprint` — never `cd ../tenxengage-blueprint`. Work branches (`work/*`) must NEVER be created in the blueprint repo. The CWD guard in Step 4 enforces this; if it fires, fix CWD before continuing.
- **Never push the sub-branch.** Local only.
- **Never auto-merge when `$GATE == every`.** Mandatory developer approval pause under manual/gate=every invocation. When `$GATE != every`, the orchestrator drives merge via the structured return summary — no chat pause.
- **Tracker claims come first.** Flip to `in-progress` before touching code.
- On `change X`: stay on the same sub-branch.
- On resume (previously in-progress with BE now done): read tracker, check out existing sub-branch, rebase on latest feature branch, skip to step 9 (Playwright against real BE).
- **E2E must run against real BE, never mocks.** `done` requires a passing Playwright run against a BE built from the current feature branch.
- **TDD-only invocations.** `--tdd` is the sole switch for Red/Green/Refactor and the four conditional skill invocations. When `$USE_TDD = true`, follow RGR and invoke the four superpowers skills at the points noted in Steps 6/7/10: `test-driven-development` (start of Step 6), `systematic-debugging` (red Vitest in Step 7 or red Playwright in Step 9), `verification-before-completion` (start of Step 10), `requesting-code-review` (Step 10's approval-pause message). When `$USE_TDD = false`, none of the above applies — no RGR, no auto-invocation of those skills, regardless of any other superpowers skill that may be active.
