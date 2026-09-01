### 7. Run tests

- **Inner loop** (inside Step 6): scoped test runs that follow each unit of production code. Step 6 drives this — see the scoped test command at the top of `subagent/step-06-implement-tasks.md`. When `$USE_TDD = true`, this is the TDD Green phase; otherwise it's just incremental verification.
- **Outer loop** (before approval pause): `./gradlew test` — full suite, must be green

**If any test is red:** If `$USE_TDD = true`, invoke `superpowers:systematic-debugging` before proposing fixes; otherwise diagnose directly. Do not proceed to the approval pause until the full suite is green.

## Next step

Read `subagent/step-07.1-pre-commit.md`.
