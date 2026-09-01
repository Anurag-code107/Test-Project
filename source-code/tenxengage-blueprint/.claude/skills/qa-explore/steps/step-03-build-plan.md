# Step 3 — build-plan

### Inputs
- Story files from Step 1
- `spec.md` and `test-plan.md` from Step 1
- Scope variables (`SCOPE_MODE`, `PAGE_ROUTE`, `STORY_ID`)

### Actions

#### 3a. Build route manifest

For each story file in scope, extract URL patterns from:
- The `## FE tasks` section: look for routes mentioned as file paths, `page.goto()` patterns, or URL path references (e.g., `/assessments`, `/assessments/new`, `/assessments/:id/edit`, `/question-banks`)
- The `## E2E test` section: extract URL patterns from the "User flow" column
- The `## Description` section: if the story introduces a dedicated page, extract its path

Normalization rules:
- Convert specific UUIDs or numeric IDs → `:id` (e.g., `/assessments/abc-123/edit` → `/assessments/:id/edit`)
- Remove query strings from patterns
- Deduplicate: keep only unique path patterns

Apply scope filter:
- `SCOPE_MODE = "story"`: keep only routes explicitly mentioned in that story's FE tasks
- `SCOPE_MODE = "page"`: manifest = `[PAGE_ROUTE]` exactly (one entry)
- `SCOPE_MODE = "full"`: all routes extracted across all story files

Save as `ROUTE_MANIFEST = ["/assessments", "/assessments/new", ...]`

Print:
```
Route manifest (<N> routes):
  /assessments
  /assessments/new
  /assessments/:id/edit
  ...
```

#### 3b. Build interaction inventory (per route)

For each route in `ROUTE_MANIFEST`:

1. Find all story files that mention this route in their FE tasks.
2. From each matching story file, extract:
   - **AC assertions:** Each bullet in `## Acceptance Criteria`. Format: `{ id: "AC-N", text: "<summary>", storyId: "US-NN" }`
   - **Microcopy selectors:** The `### Verbatim microcopy` subsection (each line = one selector). These become Playwright `getByText()` / `getByRole()` / `getByPlaceholder()` targets.
   - **UI states:** The `## UI States` section — each `- [ ] **<State>:**` entry. Extract the state name and the exact visible text (e.g., `"No assessments match your filters"`, `"Could not load banks"`).
   - **Flows:** The `## FE tasks → E2E test` section — each numbered Scenario's "User flow" field as an ordered sequence of interactions.
   - **Out of scope:** The `## Out of Scope` section — collect these as exclusions so the exploration plan does not attempt to exercise them.
   - **Permission rules:** Any text referencing role-based conditions (e.g., `"Missing action.question.create → 403"`, `"CLIENT_ADMIN: has view but not create"`).

3. Merge extractions from multiple stories for the same route into one entry per route.

Save as `INTERACTION_INVENTORY = { "/assessments": { assertions: [...], selectors: [...], uiStates: [...], flows: [...], permissions: [...] }, ... }`

#### 3c. Build test world specification

From all story files in scope, collect:
1. `touches_entities` from the YAML frontmatter of each story file.
2. `depends_on_stories` from frontmatter (determines entity creation ordering).
3. Entity shapes and creation endpoints from `spec.md` `## API Endpoints` section.

Build a flat ordered entity creation list. For each entity type referenced:

| Entity type | Creation endpoint | Notes |
|---|---|---|
| Assessment (DRAFT) | `POST /api/v1/assessments` | `{ title, type: "INLINE_QUIZ", status: "DRAFT" }` — create one per type needed |
| Assessment (PUBLISHED) | publish after DRAFT: `POST /api/v1/assessments/:id/publish` | |
| Assessment (ARCHIVED) | archive after PUBLISHED | |
| Question (on assessment) | `POST /api/v1/assessments/:id/questions` | Create 3 per assessment that needs questions |
| QuestionBank | `POST /api/v1/question-banks` | Only if `QuestionBank` in `touches_entities` |
| BankQuestion | `POST /api/v1/question-banks/:id/questions` | 5 questions per bank |
| Tag Namespace | `POST /api/v1/tag-namespaces` | Only if `Tag` in `touches_entities` |
| Tag | `POST /api/v1/tags` | 2 tags per namespace |

User credentials are not created by the skill — they rely on the test seed data in the BE database. The `helpers.ts` file defines `INSTRUCTOR_A` and `LEARNER_A` with known credentials. Use the same credentials pattern.

Save as `TEST_WORLD_SPEC = [{ entity, endpoint, payload, dependsOn }]`

#### 3d. Console noise baseline

Set the default noise baseline — console messages matching any of these patterns are excluded from anomaly detection:

```javascript
const NOISE_PATTERNS = [
  /\[HMR\]/,
  /Fast Refresh/,
  /Download the React DevTools/,
  /React.StrictMode/,
  /Warning: ReactDOM.render/,
  /webpack-dev-server/,
  /vite.*connected/i,
  /\[vite\]/i,
];
```

A console message is flagged as anomaly if it does NOT match any noise pattern AND contains any of:
`Error:`, `Uncaught`, `TypeError`, `ReferenceError`, `Failed to fetch`, or matches `/[45]\d{2}/` (4xx or 5xx status code patterns).

Save as `NOISE_BASELINE = [...]`

#### 3e. Dry-run early exit

If `DRY_RUN = true`:

Print the full exploration plan:
```
=== DRY RUN — /qa-explore <slug> ===

Routes (<N>):
  <route manifest, one per line>

Interaction Inventory:
  <for each route>
  /assessments:
    AC assertions: N (AC-1: ..., AC-2: ..., ...)
    Microcopy selectors: N ("Create Assessment", "Inline", "Archived", ...)
    UI states: N (empty list, loading skeleton, error state)
    Flows: N

Test World:
  <entity list with endpoints>

Console noise baseline: <N> patterns

Estimated primary pass interactions: ~<sum of assertions + UI states + 5 per DOM inventory>
Estimated secondary pass scenarios: ~<N routes × 8 deviation techniques>
```

Print: `"Dry run complete. No browser interaction performed."` and exit cleanly.

### Output of Step 3
- `ROUTE_MANIFEST` — list of route patterns to explore
- `INTERACTION_INVENTORY` — per-route assertions, selectors, UI states, flows, permissions
- `TEST_WORLD_SPEC` — ordered entity creation plan
- `NOISE_BASELINE` — console noise filter
- Dry-run path exits here if `--dry-run`

---

## Boundary

Outputs of this step:
- `ROUTE_MANIFEST` (sorted, deduped)
- `INTERACTION_INVENTORY`
- `TEST_WORLD_SPEC`

If `--dry-run` is set, print the plan (route manifest + interaction inventory + test world spec) and STOP. Do NOT proceed to step 04.

Otherwise, route to step 04: read `steps/step-04-setup-world.md`.
