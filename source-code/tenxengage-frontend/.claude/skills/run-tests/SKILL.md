---
name: "run-tests"
description: "Run tests scoped to changed files. Includes unit tests (Vitest) and E2E tests (Playwright with API mocking). Updates ready-check report if one exists."
argument-hint: "Optional: specific test paths, 'unit' for unit only, 'e2e' for Playwright only, 'all', or '--real-backend <spec-files>' for T1 cross-story Playwright against a running BE"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Determine Scope

1. If user specified `unit`, run only Vitest: `npm run test`
2. If user specified `e2e`, run only Playwright: `npx playwright test`
3. If user specified `all`, run both
4. If user specified specific paths, run those
5. Otherwise, scope to changes:
   - Get changed files: `git diff main --name-only --diff-filter=ACMR`
   - Map source files to test files:
     - `src/components/quiz/QuizForm.tsx` → `src/components/quiz/__tests__/QuizForm.test.tsx`
     - `src/hooks/useQuizzes.ts` → `src/hooks/__tests__/useQuizzes.test.ts`
   - Run unit tests: `npx vitest run {test-paths}`
   - If any page components changed, run Playwright E2E: `npx playwright test e2e/{feature}.spec.ts`

---

## --real-backend Mode (T1 Cross-Story)

When `--real-backend` is the first argument, run in T1 mode:

1. Treat remaining arguments as Playwright spec paths (relative to `e2e/`)
2. Read `TEST_BACKEND_URL` from environment (required — fail fast with
   `"T1 mode requires TEST_BACKEND_URL. Start the test stack with /execute-integration-tests or docker compose -f ../tenxengage-backend/docker-compose.test.yml up -d --wait"`
   if unset)
3. Verify the backend is reachable: `curl -s --max-time 5 "$TEST_BACKEND_URL/actuator/health" | grep -q '"status":"UP"'`
   — if unreachable, fail fast with the same hint
4. When generating any missing E2E test from `test-plan.md → ## E2E Cross-Story Scenarios (Real Stack)`:
   - **Skip `page.route()` mocking entirely** — these tests hit the real backend
   - Generate a `test.beforeAll` block that POSTs to `${TEST_BACKEND_URL}/api/v1/auth/login`
     to acquire a JWT for a test user (use credentials defined in the test-plan or
     the `seed-test-users` fixture if present)
   - Subsequent setup calls use `request.fetch(...)` with `Authorization: Bearer <jwt>`
     to create state via real endpoints
   - Verify side effects with `request.fetch(...)` against real endpoints, not mocks
5. Override Playwright config at runtime:
   - `use.baseURL = ${TEST_BACKEND_URL.replace(':8081', ':3000')}` — FE served from the
     dev port, API calls proxied/configured to hit the BE on the T1 port
   - `retries = 0` — T1 should be deterministic; flaky-retried-into-green is dishonest
   - Override via env: `PLAYWRIGHT_RETRIES=0 PLAYWRIGHT_BASE_URL=... npx playwright test ...`
6. On first failure, capture screenshot and trace (already default behavior)
7. Do NOT update the ready-check report — T1 has its own report path (`features/<slug>/t1-report.md`)
   which the orchestrator writes; this skill just emits structured output

---

## Playwright API Mocking

When running E2E tests, Playwright intercepts API calls using `page.route()`:
- Mock responses are derived from the feature contract at `contracts/endpoints/{resource}.yaml`
- This means no backend is needed for FE E2E tests
- Tests validate that the frontend correctly handles the contract-defined responses

---

## Test Plan Alignment

Before running tests, check if a test plan exists for the current feature:

1. Get feature ID from branch name (e.g., `feat/001-enablement-courses` → `001-enablement-courses`)
2. Check if `../tenxengage-blueprint/features/{feature-id}/test-plan.md` exists
3. If it exists:
   - Parse the FE test tables (Unit Tests [FE], E2E / Playwright Tests [FE])
   - Extract expected test files and scenarios from each table
   - For each expected test file: check if it exists in `src/` (unit) or `e2e/` (Playwright)
   - **For missing unit test files**: auto-generate using Vitest + RTL pattern (`vi.mock()` for hooks, `render()` + `screen` queries, `userEvent` for interactions). Read the component being tested to understand props and behavior.
   - **For missing E2E test files**: auto-generate Playwright test with `page.route()` API mocking. Mock responses derived from contract at `contracts/endpoints/{resource}.yaml`. Use the user flow and mocked APIs columns from the test-plan table.
   - Add generated test files to the scoped test list
4. If no test plan exists, skip this step — proceed with normal scoping

---

## Execution

1. Run the tests (including any auto-generated from the test plan)
2. If failures occur:
   - Read the failure output
   - Auto-fix the failing test or source code
   - Re-run failed tests only
   - Repeat until all pass (max 3 fix cycles)
3. Report results

---

## Report Update

Same pattern as backend: get branch name via `git branch --show-current`, check `.ready-check/{branch-name}/review.json`. If exists, update the `tests` step and write an archive snapshot to `review_{YYYY-MM-DD}_{short-commit}.json`.

---

## Output (v9 ORCHESTRATOR_RETURN Protocol)

On exit (success or failure), emit a structured return block. Multiple lines,
one field per line, prefixed `ORCHESTRATOR_RETURN`. Matches the v9 convention
used by `/load-story` and `/execute-foundation` so orchestrators
(`/run-feature`, `/execute-integration-tests`) can parse uniformly.

Required fields (always emitted):

    ORCHESTRATOR_RETURN status=<success|failure>
    ORCHESTRATOR_RETURN unit_id=<T1-FE if --real-backend else context-derived>
    ORCHESTRATOR_RETURN tests_passed=<N>
    ORCHESTRATOR_RETURN tests_failed=<N>
    ORCHESTRATOR_RETURN tests_skipped=<N>
    ORCHESTRATOR_RETURN tests_generated=<N>
    ORCHESTRATOR_RETURN tests_generated_files=<csv of paths, or "none">
    ORCHESTRATOR_RETURN fix_cycles_used=<0..3>
    ORCHESTRATOR_RETURN commit=<short SHA after any auto-fix commits, or "none">
    ORCHESTRATOR_RETURN duration_seconds=<int>
    ORCHESTRATOR_RETURN summary=<one-line, no newlines>

Additional fields on failure:

    ORCHESTRATOR_RETURN failed_spec=<spec file path, or "n/a">
    ORCHESTRATOR_RETURN failed_test=<test name, or "n/a">
    ORCHESTRATOR_RETURN failed_reason=<short reason, single line>
    ORCHESTRATOR_RETURN screenshot_path=<absolute path, or "none">
    ORCHESTRATOR_RETURN trace_path=<absolute path, or "none">

For developer-readable summary, also print (before the ORCHESTRATOR_RETURN lines):

    Unit tests: {N} passed, {N} failed
    E2E tests: {N} passed, {N} failed
    {If test plan exists}: Test plan coverage: {N}/{N} planned scenarios implemented ({N} auto-generated this run)
    {If --real-backend}: T1 mode — real backend at {TEST_BACKEND_URL}
    {If fixes applied}: {N} auto-fixes applied
