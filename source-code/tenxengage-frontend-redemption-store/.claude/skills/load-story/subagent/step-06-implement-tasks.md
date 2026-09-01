### 6. Implement the FE tasks

**TDD discipline.** If `$USE_TDD = true`, invoke `superpowers:test-driven-development` at the start of this step and follow it for every component, hook, or service function. Project-specific glue (the skill's examples are generic):

- Scoped test command for the Red/Green verify steps: `npm run test -- {Component}.test.tsx`
- Tests live in `src/components/{feature}/__tests__/*.test.tsx` (components) or `src/hooks/__tests__/*.test.ts` (hooks).

If `$USE_TDD = false`, do NOT enter a Red/Green/Refactor loop. Write production code and tests in the order the story task block specifies; tests are still required by each task's acceptance criteria, just not test-first.

**Provenance comments and fidelity rules:** Read `references/fidelity-rules.md` now and apply throughout this step.

**Builder primitives:** If this story's FE tasks include a builder screen type, read `references/builder-primitives.md` now before implementing those tasks.

**Contract-change rule:** Do NOT hand-write TypeScript interfaces from scratch or add fields not in the contract. If a field mismatch is found, read `references/contracts-ritual.md` and follow it before writing code. Then note in tracker Notes.

**Mock derivation rule (mandatory).** Every mocked BE response in Vitest tests and in scaffold-and-wait stubs MUST be constructed by copying field names, types, and JSON shape directly from `contracts/models/*.md` and `contracts/endpoints/*.yaml` — never from your own assumption of what the BE returns. This explicitly includes streaming/event payload discriminators (e.g. SSE `data.type` vs `data.text`, never invented `action`/`delta`), drawer/detail field names, and answer-JSON shape. Add a one-line comment above each mock: `// shape: contracts/models/{model}.md`. A mock whose field names do not appear verbatim in the contract is a defect, not a passing test.

Iterate the `## FE tasks [FE]` section **in the order written in the story file**. Each task block specifies its own `**Files:**` and acceptance criteria — the story is the source of truth for what tasks exist and what each entails.

Do NOT assume a fixed count. Some stories have two FE tasks, some have five. The numbering (`FE-1`, `FE-2`, …) is a readable label, not a contract — read them as a list, not as slots.

As each task's work lands, check the matching items in the **FE session** block of `## Execution checklist` (expressed by concrete deliverables, e.g. `{Component}.test.tsx Vitest tests pass`, not by task number). Commit checklist updates alongside code.

Typical tasks you'll encounter in a standard page-building story — for orientation only; your actual story drives what gets implemented:
- TypeScript types in `src/types/{feature}.types.ts` + service call in `src/services/{feature}.service.ts`
- TanStack Query hook in `src/hooks/use{Entity}.ts`
- Component(s) + Vitest tests in `src/components/{feature}/**`
- Page wiring + route in `src/pages/{feature}/**` and `src/App.tsx`

## Next step

Read `subagent/step-07-run-vitest.md`.
