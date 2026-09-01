---
name: "run-tests"
description: "Run tests scoped to changed files. Standalone skill — updates the ready-check report if one exists for the current feature branch."
argument-hint: "Optional: specific test class names or 'all' to run full suite"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Determine Scope

1. If user specified test classes, run those directly
2. If user specified `all`, run `./gradlew test`
3. Otherwise, scope to changes:
   - Get changed files: `git diff main --name-only --diff-filter=ACMR`
   - Map source files to test files:
     - `src/main/java/.../service/FooService.java` → `src/test/java/.../service/FooServiceTest.java`
     - `src/main/java/.../controller/FooController.java` → `src/test/java/.../controller/FooControllerTest.java`
   - If entity or migration files changed, include integration tests: `*IntegrationTest`
   - Run: `./gradlew test --tests "*.FooServiceTest" --tests "*.FooControllerTest"`

---

## Test Plan Alignment

Before running tests, check if a test plan exists for the current feature:

1. Get feature ID from branch name (e.g., `feat/001-enablement-courses` → `001-enablement-courses`)
2. Check if `../tenxengage-blueprint/features/{feature-id}/test-plan.md` exists
3. If it exists:
   - Parse the BE test tables (Unit Tests [BE], Security Tests [BE], Integration Tests [BE], API Tests [BE])
   - Extract expected test classes and scenarios from each table
   - For each expected test class: check if the file exists in `src/test/java/`
   - **For missing test files**: auto-generate the test using:
     - The scenario description and assertion from the test-plan table
     - The correct pattern for the test type: unit → `@ExtendWith(MockitoExtension.class)` + AssertJ, integration → `extends AbstractLocalIntegrationTest`, controller → `@WebMvcTest` + MockMvc, security → `@WebMvcTest` + security context
     - Read the corresponding source file being tested to understand the API
   - Add generated test files to the scoped test list
4. If no test plan exists, skip this step — proceed with normal scoping

---

## Execution

1. Run the tests (including any auto-generated from the test plan)
2. If failures occur:
   - Read the failure output
   - Auto-fix the failing test or source code
   - Re-run the failed tests only
   - Repeat until all pass (max 3 fix cycles)
3. Report results

---

## Report Update

If a ready-check report exists for the current branch:

1. Get branch name: `git branch --show-current`
2. Check `.ready-check/{branch-name}/report.json` (uses full branch name as-is)
3. If exists: update the `tests` step with status, commit hash, and test names
4. Append to `.ready-check/{branch-name}/history.jsonl`

If no report exists, just output results — no report file created.

---

## Output

```
Tests run: {N} | Passed: {N} | Failed: {N} | Skipped: {N}
{If test plan exists}: Test plan coverage: {N}/{N} planned scenarios implemented ({N} auto-generated this run)
{If fixes applied}: {N} auto-fixes applied
{If report updated}: Ready-check report updated for {feature-id}
```
