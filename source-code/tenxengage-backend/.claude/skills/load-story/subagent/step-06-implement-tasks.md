### 6. Implement the BE tasks

**TDD discipline.** If `$USE_TDD = true`, invoke `superpowers:test-driven-development` at the start of this step and follow it for every production class or method. Project-specific glue (the skill's examples are generic):

- Scoped test command for the Red/Green verify steps: `./gradlew test --tests '*{Class}Test.{method}*'`
- Tests are either service tests (`@ExtendWith(MockitoExtension.class)`) or `@WebMvcTest` controller tests.

If `$USE_TDD = false`, do NOT enter a Red/Green/Refactor loop. Write production code and tests in the order the story task block specifies; tests are still required by each task's acceptance criteria, just not test-first.

Iterate the `## BE tasks [BE]` section **in the order written in the story file**. Each task block specifies its own `**Files:**`, its acceptance criteria, and any test expectations — do not guess or supplement. The story file is the source of truth for which tasks exist and what each entails.

Do NOT assume a fixed count. Some stories have two BE tasks, some have five. The numbering (`BE-1`, `BE-2`, …) is a readable label, not a contract — read them as a list, not as slots.

As each task's work lands, check the matching items in the **BE session** block of `## Execution checklist` (which is expressed in terms of concrete deliverables, e.g. `{Entity}ServiceTest unit tests pass`, not by task number). Commit checklist updates alongside the code.

Typical tasks you'll encounter in a CRUD-shaped story — for orientation only; your actual story drives what gets implemented:
- DTOs in `src/main/java/com/tenxengage/app/dto/`
- Service method + unit test (`@ExtendWith(MockitoExtension.class)`)
- Controller endpoint + `@WebMvcTest`
- `@Audited` annotation on write operations
- **Kafka producer unit test** (Mockito) — if the story publishes a domain event, a producer unit test asserting the correct topic name and payload fields is required in the same story session. Full round-trip consumer tests go in `test-plan.md → Audit & Events`, not here.

**Contract-change rule:** DTO field names and types must match the generated contract. If a mid-story change is needed, read `references/contracts-ritual.md` and follow it before writing code. Then note in the story row's Notes column: "contract updated — refetch types".

Commit granularity: one commit per logical unit. All squashed at merge.

## Next step

Read `subagent/step-07-run-tests.md`.
