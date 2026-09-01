---
name: "run-tests"
description: "Run tests scoped to changed files. Includes unit tests (Vitest) and E2E tests (Playwright with API mocking). Updates ready-check report if one exists."
argument-hint: "Optional: specific test paths, 'unit' for unit only, 'e2e' for Playwright only, or 'all'"
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

## Output

```
Unit tests: {N} passed, {N} failed
E2E tests: {N} passed, {N} failed
{If test plan exists}: Test plan coverage: {N}/{N} planned scenarios implemented ({N} auto-generated this run)
{If fixes applied}: {N} auto-fixes applied
{If report updated}: Ready-check report updated for {feature-id}
```
