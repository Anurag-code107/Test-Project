### 7. Run frontend tests (scoped + full)

- **Inner loop** (inside Step 6): `npm run test -- {Component}.test.tsx` after each unit of production code. Step 6 drives this — this bullet is reference only. When `$USE_TDD = true`, this is the TDD Green phase; otherwise it's just incremental verification.
- Outer loop: `npm run test` — full Vitest suite, must be green

**If any test is red:** If `$USE_TDD = true`, invoke `superpowers:systematic-debugging` before proposing fixes; otherwise diagnose directly. Do not proceed until the full Vitest suite is green.

## Next step

Read `subagent/step-08-scaffold-and-wait.md`.
