---
name: "create-stories"
description: "Use after /create-spec has written and reviewed a spec.md — decomposes the reviewed spec into stories, foundation tasks, tracker, and test plan. Run in the tenxengage-blueprint repo."
argument-hint: "feature-slug (e.g., enablement-courses)"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

The argument is the feature folder name, e.g. `enablement-courses`.

---

## Timer

Run this immediately — before Phase 0:

```bash
date +%s > /tmp/create_stories_start && echo "⏱ create-stories started: $(date '+%H:%M:%S')"
```

---

## Phase 0: Locate the Approved Spec

> **Phase 0 — Locating spec.** Verifying the reviewed spec exists before proceeding.

1. Read `features/{feature-slug}/spec.md`
2. Check the frontmatter `status` field:
   - If `status: reviewed` → proceed to Phase 1
   - If `status: draft` → STOP: "The spec is still in `draft` status. Run `/review-spec {feature-slug}` and resolve any issues before decomposing into stories."
   - If file not found → STOP: "No spec found at `features/{feature-slug}/spec.md`. Run `/create-spec` first."

---

## Phase 1: Required Reading

> **Phase 1 — Reading spec and templates.** Loading the spec content and file templates to ground story decomposition.

Read these files before generating any content:

1. `features/{feature-slug}/spec.md` — already read in Phase 0; focus on: Functional Requirements, Data Model, API Endpoints, Permissions, Domain Events, Security Design, and Edge Cases sections

   **Read domain registry** (only if `spec.md` frontmatter has `domain:` non-null):
   - Read `docs/patterns/domains/INDEX.md` and `docs/patterns/domains/{domain}.md`.
   - Propagate `domain` and `builder_type` into per-story frontmatter (where stories declare scope).
   - Story-level type, service, and repository names MUST conform to the slot fillers in `{domain}.md`. Flag any story that proposes a different name before writing the story file.
   - Propagate `visual_reference` and `applicable_sections` from spec frontmatter into every story file's frontmatter so implementers see the visual reference and applicable sections without re-reading the spec. For builder stories, `applicable_sections` scopes which sections the story exercises.
2. `features/{feature-slug}/technical.md` — Flyway migrations (for F2 foundation task), package layout (for concrete file paths in every story task), repository queries (for F3 foundation task), hook specs (for FE hook query keys and invalidation)
3. `.claude/skills/create-stories/templates/stories-index-template.md` — stories.md format
4. `.claude/skills/create-stories/templates/foundation-tasks-template.md` — foundation.md format
5. `.claude/skills/create-stories/templates/tracker-template.md` — tracker.md format
6. `.claude/skills/create-stories/templates/story-template.md` — per-story file format
7. `.claude/skills/create-stories/templates/test-plan-template.md` — test-plan.md format
8. **`## Planning seeds (from feature brief)` section in `spec.md`** — if present, these are the planning-level story seeds from the upstream feature brief (written by `/decompose-brd`). Use them as the starting backlog skeleton — start story identification from seeds, not from a blank slate. If absent, the feature was not sliced from decompose-brd feature briefs; proceed without seeds.

---

## Phase 1.5: Story-level Completeness Probe

> **Phase 1.5 — Completeness probe.** Surfacing flow-level gaps that the spec-level probe may not have caught before story decomposition begins.

This phase is often a no-op: `/create-spec` already ran the broader functional-completeness probe. Phase 1.5 applies a narrower lens — user flows — to catch gaps that only surface when thinking about what a user actually does step-by-step.

### When to run a full probe

Always run it, but expect it to be brief. A no-op is a valid result.

### What to look for

Walk the spec's FR table and API endpoint table as if you are a user performing each action end-to-end. For each action, ask: *"Does the spec cover what happens at each transition point in this flow? Is there anything an engineer would have to ask before implementing?"*

Common flow-level gaps:
- An action is specified but not what triggers the next state (e.g., "user submits form" but no FR for what the BE returns to unblock the FE)
- A list page is specified but the empty state copy is missing from FRs or edge cases
- A delete action is specified but not what happens to in-flight users who have the entity open in another tab
- A status transition is specified but the guard condition (who can trigger it, when) is absent
- An async operation (file upload, AI generation) is specified but no loading / error / retry behavior is defined
- A create/edit surface is specified for an entity but no story covers its **detail/view surface** (the drawer or detail page that displays the entity's fields, stats, and related-entity names) — the spec describes builders but no "view detail" story enumerating what is rendered.
- A **list response DTO** exposes a bare `refId`/UUID (`userId`, `assignedById`, `targetId`, etc.) that a list row needs to render as a human-readable value (name, email, title) — the `spec.md → ## DTOs` definition carries only the raw UUID without the display field. Flag each case before decomposing the list story: `⚠️ LIST DTO GAP: {Entity}Response carries bare {field} — does the list row need to display a human-readable {attribute}?` Route to the spec owner for resolution before writing the story.

Do NOT re-probe dimensions that the spec's `## Functional Completeness Audit` already shows as covered, approved, rejected, or deferred — this avoids double-prompting.

### Proposal format

If flow-level gaps are found, present them as a brief one-shot list before starting decomposition:

> Before decomposing into stories, I spotted {N} flow-level gap(s) not already addressed in the spec. Each is a question a developer would have to ask before implementing the relevant story. Approve, reject, or modify per-item, or skip.
>
> ⊕-1 {Natural-language gap description}: proposing AC addition to {US-candidate} — "{proposed AC text}"
> ⊕-2 {Natural-language gap description}: proposing a new story — "{proposed story title}" (1–2 sentence rationale)
> ⊕-3 {Natural-language gap description}: no obvious resolution — flagging for deferral

Response grammar (same as `/create-spec` probe):

    a              → approve all
    r              → reject all
    a1,r2,m3=<wording>  → per-item with optional modification
    s              → skip

### Routing approved gaps

| Gap type | Resolution |
|---|---|
| Narrow AC on an existing story | Add the AC to that story's acceptance criteria when writing the story file |
| Material new behavior (would exceed the target story's AC count or checklist length) | Create a new `US-NN` story; add it to the stories table |
| Cannot resolve now | `⚠️ DEFERRED — flow-level gap: {description}` entry in `stories.md → ## Flow-level Completeness Audit` |

### Probe record

Held in conversation context for use in Phase 4. Written to `stories.md → ## Flow-level Completeness Audit` regardless of outcome (one sentence if no gaps found: "No flow-level gaps identified.").

---

## Phase 2: Story Decomposition

> **Phase 2 — Decomposing into stories.** Identifying foundation tasks and user stories from the spec.

### 2a: Foundation Tasks

Extract tasks in this fixed order — each is its own session:

1. **Enums** — all new enum classes listed in the spec's New Enums section, plus additions to `AuditAction.java` and `AuditResourceType.java`
2. **Flyway migrations** — all schema DDL for this feature's tables (from the spec's Data Model and Flyway Migrations sections)
3. **Base entities + repositories + fixtures** — entity classes, repository interfaces, and `{Entity}Fixtures.java` for every new entity (mandatory — never omit fixtures)
4. **Permissions + feature flags seed** — the Flyway seed SQL migration (from the spec's Permissions & Feature Flags section)
5. **BE-only plumbing** _(if applicable)_ — Kafka consumers/producers or other BE infrastructure with no user-visible story. **Omit F5 if not applicable.**

Each foundation task specifies: deps, files (concrete paths), done-when verification.

#### Contract generation note (Step 0)
Add explicitly to foundation.md: `/generate-contracts {feature-slug}` must be run in `../tenxengage-contracts/` **before any foundation task begins**. This is a pure spec → OpenAPI transform; generating contracts first lets FE sessions scaffold against types + mocks from day one.

### 2b: User Story Identification

**When the spec has a `## Planning seeds (from feature brief)` section (decompose-brd roadmaps):** Start from the planning seeds, not from a blank slate. Walk seeds in order:

1. For each seed `F-NN.S-NN`, identify the story or stories it maps to (`US-NN`).
2. Apply the merge-first and right-size rules below to produce the final story set.
3. Every story records its `seed_id` in frontmatter — values:
   - Single seed: `seed_id: "F-04.S-03"`
   - Merged seeds: `seed_id: ["F-04.S-01", "F-04.S-02"]`
   - No seed (foundation / infra): `seed_id: null`
4. Walk the spec's FR table to verify every FR is covered by at least one story (foundation or user). FRs with no story coverage indicate a missing story or a missed seed — add a story for it.

**Seed → story mapping rules:**
- **1 seed → 1 story** (typical): seed's title becomes the story's name; business outcome becomes the "As a / I want / So that"; FRs the seed covers become acceptance criteria.
- **1 seed → multiple stories** (seed too broad for spec's reality): split per the right-size rule. Each split story carries the same `seed_id` so the PM sees grouped stories under one seed.
- **Multiple seeds → 1 story** (seeds too thin and naturally merge per merge-first rule): merged story carries `seed_id: ["F-NN.S-01", "F-NN.S-02"]`. Surface this to the PM — it reveals over-fragmented planning seeds.
- **0 seeds → 1 story** (foundation tasks, infrastructure stories): `seed_id: null` is allowed and expected.

**When no planning seeds section exists (legacy roadmaps or free-text input):** Walk through every distinct user-visible path from the spec's Functional Requirements table and API endpoint table. Each path becomes one `US-NN` story. Omit `seed_id` from frontmatter.

#### Merge-first rule (check BEFORE creating separate stories)

**Merge into one story** when operations share the same entity, controller, and service class AND are naturally sequential:
- Edit + delete (same controller + service; delete is one endpoint alongside edit)
- Publish + unpublish + archive (same state machine; same service method group)
- List endpoint + empty state (same endpoint; empty state is a FE branch, not a separate story)
- Validation error path + success path for the same form action

**Create a separate story** only when:
- The operation introduces a **new entity type** as the primary subject (e.g., lessons are distinct from courses)
- The FE work would be a **distinct page component**, not a panel or section within an existing page
- The BE work requires a **different controller class** (e.g., AI chat vs. CRUD)
- The operation has ≥3 variants and each variant renders a **structurally distinct sub-component** (not just different field values in the same form). Each variant becomes its own story; one shared story owns the type selector and switching logic.
  _Example: "Attach content asset" where ARTICLE uses a rich-text editor, DOWNLOADABLE uses a file picker, and EMBEDDED uses an HTML editor with toolbar — different sub-components → split._
  _Counter-example: "Attach content asset" where all 6 types share one form with conditional URL/textarea fields → keep merged; use **variant-axis parameterization** from Test surface expansion instead._
- The entity has a **detail/view surface** (detail page, drawer, or panel) that renders fields beyond the list row — the view surface becomes its own story with display-level ACs (see Detail/view surface coverage rule). Do not merge it into the list or create story.
- The execution checklist would **exceed 20–25 items** even with merging (then split)

#### Story identification rules

- One story per distinct user action that produces a **different visible outcome** (but apply merge-first rule first)
- Stories touching the same entity run **sequentially** (create before edit, edit before delete) — annotate the dependency
- Stories touching **disjoint entities or disjoint pages** can run in parallel — annotate this too
- **Right-size rule**: If a story's execution checklist would exceed 20–25 items, split it. Target 15–20 items per story as the sweet spot — enough to justify a session's context cost
- **Layer assessment (required for every story)** — set `layers` in the story's frontmatter:
  - `["BE", "FE"]` — full-stack story (new endpoint + UI). Default for most user-visible actions.
  - `["BE"]` — no user-visible UI: Kafka event processor, scheduled job, webhook, backend-only enforcement
  - `["FE"]` — no new endpoints: presentational-only change, page that reads exclusively from existing APIs
  - Use `N/A` for the inapplicable layer's status columns in `tracker.md`
- **Detail/view surface coverage (mandatory).** For every user-facing entity that has a detail, drawer, or view surface (any surface that renders a single entity's fields, not just a row in a list), derive a dedicated `US-NN` "view {entity} detail" story when the detail surface displays fields beyond those on the list row (stats, nested children, descriptions, related-entity display names). This story's ACs MUST enumerate **what is displayed** — each section of the surface (e.g., Details, Stats) and the specific fields shown in each. A detail surface is NOT a free FE branch of the create/edit story: if no story enumerates its displayed fields, it will be cloned from another feature and render wrong. Walk the spec's `## Frontend Specification → Pages` (rows like `{{Detail}}Page | /{{route}}/:id`) and `## DTOs → {{Entity}}DetailResponse` — every detail page/DTO must map to a view story.
  - **Do NOT manufacture a view story when the spec has no detail/view surface.** If the entity has no detail page, drawer, or view surface in the spec's `## Frontend Specification`, there is no view story to derive — skip it; do not invent one. (Merge-first still applies: a detail surface that renders nothing beyond the list row stays a branch of the list/create story, not a separate story.)
  - **Make the trigger mechanical.** Derive a view story only when a `{{Entity}}DetailResponse` (or equivalent detail DTO) exists in `## DTOs` **AND** it carries fields beyond the list `{{Entity}}Response` — the Detail-vs-list field delta. That delta is exactly what the view story's ACs enumerate; if there is no delta, there is nothing for a view story to assert.
  - **Do NOT invent ACs when the DetailResponse is hollow.** If the spec lists a detail page/surface but its `{{Entity}}DetailResponse` is missing the display fields the surface would show (e.g., it carries only `refId` + type + order for referenced entities), do **not** guess what the surface renders. Emit a `⚠️ DEFERRED — detail surface gap: {entity} DetailResponse carries no display fields beyond bare refIds; cannot enumerate view ACs` note and route it back to `/review-spec` (Check 22) rather than fabricating display-level ACs.
  - **Pair every display AC with its BE data-exposure obligation (same story).** A composite/detail surface can only render what the backend returns, so the same story's BE task slice must own exposing that data — this is not a separate or FE-only concern. The story's BE tasks MUST require that the response DTO **exposes** every field/section the FE ACs render, and that the service **hydrates referenced display data**: resolve human-readable names/descriptions for any `refId`/UUID the surface shows, never returning bare `refId` + type + order nor the field shape with null/blank display values. This is the read side of create-spec's DTO contract (Check 21) and the element-completeness counterpart to the display ACs above.

- **List DTO display-field completeness (mandatory for every list story).** The hydration obligation is not limited to composite/detail stories — it applies equally to any list story whose FE renders rows that must show human-readable attributes for a referenced entity. When writing a list story:
  1. For each bare `refId`/UUID in `{{Entity}}Response` (e.g., `userId`, `assignedById`, `targetId`), ask: *does a list row need to display a name, email, or other human-readable attribute for this reference?*
  2. If yes: the DTO must include the display field(s); the BE service must hydrate them at query time. Add an explicit BE-1 task note: "DTO exposes `{displayField}` (hydrated from `{refId}` — never a bare UUID)."
  3. If the spec's `## DTOs` definition for `{{Entity}}Response` omits the display field, flag it as a spec gap in the Phase 1.5 probe (see `⚠️ LIST DTO GAP` pattern above) rather than silently carrying the bare UUID into the story. A bare UUID in a list DTO the FE has to display is always a spec gap, not an implementation detail.
  4. The FE must never be expected to resolve a UUID to a display name with a second call — hydration is a BE responsibility.

**Pattern-aware decomposition**: If the spec references the builder-widget or ai-copilot patterns, each major builder interaction is its own story: entry flow + type selection, each accordion step group, AI copilot panel, save + publish flow.

#### Story contents (what each `stories/US-NN-*.md` must contain)

- Story description: actor, trigger, step-by-step flow, expected outcome, negative paths
- **Acceptance Criteria:** plain bulleted list with stable IDs (`AC-1`, `AC-2`, …). Each bullet is one binary claim ("X returns 201", "Y validation rejects empty input", "Cross-tenant Z returns 404"). **Source from `spec.md → ## Functional Requirements`**: walk the FR rows that this story owns and translate each into one or more AC bullets. Add ACs for security/audit/permission boundaries that the story actually exercises (cross-tenant 404, missing-permission 403, audit row written) — these come from `spec.md → ## Edge Cases`, `## Permissions & Feature Flags`, and `## Audit Trail`. Cap at ~6–8 bullets; if more, the story is too big and should be split.
- **Out of Scope:** required section. Source from `spec.md → ## Out of Scope` (filter to what's adjacent to this story) plus adjacent capabilities owned by other stories ("Course publish flow — covered by US-04"). If there is genuinely nothing to call out, write `— none`.
- **Non-Functional Notes:** **optional** — include the section only when there is a story-specific NFR not already obvious from cross-cutting `spec.md` sections (`## Non-Functional Requirements`, `## Security Design`, `## Observability`, `## Frontend Specification`). Examples that warrant story-level callouts: a tighter perf budget than the spec default, a story-specific telemetry event name, a keyboard/ARIA requirement on a particular component, an i18n constraint on a specific input, a responsive breakpoint rule. Do **not** write `— none beyond spec.md` placeholders; skip the section entirely.
- **UI States:** FE stories only (omit for `layers: ["BE"]`). Three concerns when applicable: (1) visual states checklist (loading / empty / error / partial-or-optimistic) — source empty-state copy from `spec.md → ## Edge Cases → Empty state` and `## Frontend Specification → Pages`; source error-state behavior from `## Edge Cases`. (2) `### Verbatim microcopy` sub-section — required when the story introduces user-visible strings (button labels, success toasts, tooltips, helper text, placeholders) not already quoted in ACs or the empty/error bullets; omitted entirely otherwise. (3) `### Conditional rendering` sub-section — required when FE renders differently based on a discrete input (status enum, caller role, permission, feature flag, entity-derived boolean); omitted entirely otherwise.
- Spec references: exact `spec.md` sections the session must read (e.g., §6.1, §11, §13). Include the optional Mockup line only when an actual mockup file or Figma frame exists for this story; otherwise omit.
- BE tasks: DTOs, service method(s), controller endpoint(s), audit annotation, unit tests, @WebMvcTest tests — all with concrete file paths from `technical.md → ## Package Layout [BE]`. Task numbering (BE-1, BE-2, …) is a readable label, not a contract — add BE-5/BE-6 for Kafka publishers or extra service methods; drop tasks that don't apply (e.g., no audit on read-only endpoints). **Per-layer cap:** if the BE execution checklist items alone would exceed ~12–15, split the story rather than expanding it.
  - Spec references for BE tasks: point to `spec.md` for business rules and security decisions; point to `technical.md` for Flyway SQL, file paths, and repository queries
  - If the story publishes a Kafka event: include a producer unit test (Mockito — asserts topic name + payload fields) in the same file
  - Cross-consumer round-trip verification goes in `test-plan.md → Audit & Events`
- FE tasks: TypeScript types (from `../tenxengage-contracts/`), service call, hook, component(s), Vitest tests, page wiring — all with concrete file paths from `technical.md → ## Package Layout [FE]`. Hook query keys and staleTime from `technical.md → ## Hook Specs [FE]`. Task numbering (FE-1, FE-2, …) is a label — add FE-5/FE-6 for additional independent components; drop tasks that don't apply. **Per-layer cap:** if FE checklist items alone exceed ~12–15, split the story. **Multi-component rule:** if FE-3 covers more than one top-level component that can break independently, split into FE-3a, FE-3b … each with its own component file and `__tests__/*.test.tsx`. Sub-components private to one parent are not split. **Interaction-pattern commitment:** when an FE task describes an interaction that has multiple visually-equivalent-but-behaviorally-distinct patterns (sheet vs. drawer, modal vs. dialog vs. page, inline-edit vs. popover, accordion vs. tabs), the task narrative must commit to one. Phrasing like "in sheet/drawer" or "modal or dialog" leaves the choice to whichever consumer reads first — /create-mockups and /load-story (FE) will pick differently. Words used as if synonyms aren't.
  - Spec references for FE tasks: point to `spec.md` for page intent, component intent, and user flows; point to `technical.md` for file paths and hook specs
- E2E test: one Playwright scenario per story minimum (test file, step-by-step user flow, APIs to mock via `page.route()`, visible assertion). **Each scenario header must annotate the AC IDs it covers in parentheses**, e.g., `**Scenario 1:** 'Create course happy path' _(covers AC-1, AC-3)_`
- Execution checklist: flat `[ ]` list covering every BE + FE + E2E item. **Each item that maps to an AC must reference the AC ID(s) in parentheses** at end-of-line, e.g., `[ ] CourseService.createCourse() method added _(AC-1)_`. Coverage rule: every AC must be referenced by at least one E2E scenario, unit test, or @WebMvcTest item — if any AC is "untraced", the story is incomplete.
- Done when: BE `./gradlew test` passes + FE `npm run test` + E2E Playwright test passes against real BE; **and** every AC is referenced by at least one passing test

**Test co-location (non-negotiable):** Tests ship inside the story file that creates the corresponding code. Every story that creates a `*Service.java` includes `*ServiceTest.java`. Every story that creates a `*Controller.java` includes `*ControllerTest.java` (@WebMvcTest). Every story that creates a component includes `__tests__/ComponentName.test.tsx` (Vitest). Cross-story integration tests go into `test-plan.md` only.

#### Test surface expansion

When generating the test list for any service unit test, @WebMvcTest case, or FE Vitest component test task, apply these expansions to each AC's test set. The AC defines the contract; these heuristics expand the contract along its real dimensions.

- **Variant-axis parameterization.** If an AC is exercised by an action whose request contains an enum or categorical field with ≥3 values (e.g., `assetType`, `category`, `processingMode`), the happy-path test must be parameterized across **all** enum values, not written against one representative. Type-specific validation or rendering still gets its own dedicated test.
  _Example: `addAsset_happyPath` parameterized over `CourseContentType.{VIDEO, PDF, ARTICLE, EXTERNAL_URL, DOWNLOADABLE, EMBEDDED}` instead of a single VIDEO case._

- **State × action matrix.** If service behavior branches on entity state (status enum, lifecycle stage), include one test per state the branch distinguishes. State-agnostic actions do not need this expansion.
  _Example: `deleteLesson` tested against course `status = {DRAFT, UNPUBLISHED, PUBLISHED}` because each state produces a different outcome._

- **Verbatim error text.** When an AC quotes user-visible error or copy text (e.g., "Cannot remove the last lesson from a published course"), the test must assert the exact string, not a substring or regex. ACs that describe an error without quoting text are spec-vagueness — flag back via `/review-spec` rather than inventing copy.

### 2c: E2E Coverage Planning

For each story, count the distinct UI branches in the FE tasks — each branch = one E2E scenario minimum:

**Add a separate E2E scenario** when a branch:
- Renders a **distinct UI** (different component, layout, or input set) even if validation is the same — each can break independently
- Produces a **distinct user-visible error message** not covered by any other scenario
- Tests a **permission boundary** where the wrong role sees a hidden/disabled control or is redirected

**Do NOT add** a separate E2E scenario when:
- Two paths render the **same UI with different field values** (Vitest territory)
- A validation error is **identical** across branches (test it once)

**Builder pattern** extra E2E layers (if the spec uses builder-widget or ai-copilot patterns):
- Entry flow: type selection → sub-type → template picker → builder landing. Test forward, back, and type-to-builder routing.
- Accordion steps: expand, fill required fields, verify step completion badge, Continue to next, auto-scroll. Verify required-field skip keeps step incomplete.
- AI copilot panel: toggle modes, send message, verify streaming text, verify tool-call auto-fill, verify suggestion chips. Mock the SSE endpoint with staged event sequences (`text_delta`, `action`, `suggestions`, `done`).
- Document upload via AI: upload → phased extraction fills fields → AI guard blocks if required fields still missing.
- Navigation guard: edits (isDirty=true) → navigate away → confirm dialog → cancel returns to builder.
- Save and publish flow: complete all steps → "Complete Setup" → preview → confirm → single API call → redirect.

### 2d: Test Plan Highlights

Identify cross-story integration tests for `test-plan.md` — not CRUD filler, only scenarios that require multiple stories or entities:

1. **Entity relationships** → cascade delete, referential integrity, ordering constraints
2. **State machine full lifecycle** → every valid transition end-to-end (e.g., DRAFT→PUBLISHED→UNPUBLISHED→ARCHIVED) + verify invalid transitions return correct error
3. **Business rules** → rule enforcement against real DB (e.g., "can't publish with 0 lessons")
4. **Multi-entity workflows** → create parent → add children → configure → publish → verify object graph consistency
5. **Permission enforcement** → same endpoint called as different roles → verify access granted/denied
6. **Event publishing** → verify Kafka events emitted with correct payload after triggering operations
7. **Tenant isolation** → create as Tenant A, query as Tenant B → 404, not empty list
8. **Contract conformance** → OpenAPI validator against every endpoint group, wired to `../tenxengage-contracts/`
9. **Query correctness at scale** → pagination edge cases (0 results, last page partial, cursor drift), complex filter combinations with real data volumes, N+1 detection via Hibernate Statistics; only when the feature has list/search/filter endpoints
10. **E2E cross-story flows against real stack** → Playwright scenarios spanning multiple stories, no API mocking, run against running BE; only when the feature has user-facing cross-story flows

**Coverage rules applied when filling in each category:**

- **Contract conformance:** include at minimum one row each for **400 (validation), 404 (not found), and 422 (business rule)** error response shapes — not only success responses. Error shapes drift just as easily as success shapes.
- **Tenant Isolation & Security:** include one row each for **unauthenticated access (no token → 401)** per endpoint group, and for **cross-tenant PUT/DELETE on a primary resource (IDOR on writes)** — not only cross-tenant GET.
- **Audit & Events:** include at least one row verifying that **failed operations produce no audit record** (negative path).
- **Cross-Cutting Checks:** every row must name a concrete `Test Class` so `run-tests` can auto-generate it. Do not use the legacy two-column checklist format.

---

## Phase 3: Write the Plan Outline

> **Phase 3 — Writing plan outline.** Producing a reviewable capsule-based plan before writing any files.

Write the plan file using the Write tool (path provided by plan mode system message). The plan outline must be dense enough to review in a few minutes but capture all bespoke decisions.

**Plan file schema:**

```markdown
# Stories Plan: {feature-slug}

## Feature
- **Spec**: `features/{feature-slug}/spec.md` (status: reviewed)
- **Stories folder**: `features/{feature-slug}/stories/`

## Foundation tasks
| ID | Title | Deps | Key files (2–3 paths) | Done-when |
|----|-------|------|----------------------|-----------|
| F0 | Contracts (FIRST) | — | `../tenxengage-contracts/` | /generate-contracts completes |
| F1 | Enums | F0 | `entity/enums/*.java`, `AuditAction.java` | ./gradlew compileJava |
| F2 | Migrations | F1 | `db/migration/V{N}..V{M}.sql` | ./gradlew flywayMigrate |
| F3 | Entities + repos + fixtures | F2 | `entity/*.java`, `testdata/*Fixtures.java` | ./gradlew test -t fixtures |
| F4 | Permissions + flag seed | F3 | `db/migration/V{P}.sql` | seed rows verified |
| F5 | BE plumbing | F4 | `kafka/producer/*.java` | SKIP IF N/A |

## Story index
| ID | Title | Layers | Entities | Deps | Parallel with |
|----|-------|--------|----------|------|--------------|
| US-01 | ... | BE+FE / FE / BE | ... | F4 | US-02 |
...

## Dependency graph (ASCII tree)
[graph]

## Per-story capsules

### US-01 — {Title}
- **Layers:** {BE+FE | FE | BE}
- **Trigger:** {actor} {does what}
- **Steps:** {step sequence in 1–2 lines}
- **Acceptance Criteria:** AC-1: {claim}; AC-2: {claim}; AC-3: {claim} _(short list — full bullets land in the story file)_
- **Out of Scope:** {one-line list of adjacent capabilities this story does NOT cover, or `— none`}
- **NFR notes:** {only if story-specific — perf budget / a11y / telemetry / i18n / responsive — otherwise omit this line}
- **UI states (FE):** {loading | empty copy | error fallback | optimistic — omit for BE-only}
- **Negative paths:** {validation error text | permission redirect | state guard}
- **Business rules:** {cap, constraint, guard — only if non-obvious from spec}
- **E2E scenarios:**
  - S1 happy _(covers AC-1, AC-3)_: {visible assertion}
  - S2 {negative} _(covers AC-2)_: {visible assertion}
- **BE task intents:** {service method names, controller paths, DTO names, file paths in 1–2 lines}
- **FE task intents:** {types file, hook, component(s), page wiring — 1–2 lines}
- **Done when:** {specific test commands} pass; every AC referenced by ≥1 passing test

[repeat for every story]

## Test plan highlights
- {Specific cross-story scenario: class name, scenario, expected outcome}
- [only scenarios that need multiple stories or Testcontainers — no generic CRUD]

## Story count summary
- Total: N stories
- BE + FE: X | FE-only: Y | BE-only: Z
```

---

## Phase 4: After Plan Approval

> **Phase 4 — Writing files.** Branch check → directory structure → all execution artifacts.

Once the user approves the plan:

> **Read the plan file.** Use the story capsules and foundation table as the source of truth for every file you write. Do NOT regenerate decisions — only expand scaffolding.

0. **Verify branch**: Confirm `features/{feature-slug}` branch is checked out in both blueprint and contracts repos.

1. **Create directory structure**:
   - `features/{feature-slug}/stories/`
   - `features/{feature-slug}/tasks/`

2. **Write stories index**: `features/{feature-slug}/stories.md` — story index table + dependency graph. Follow `.claude/skills/create-stories/templates/stories-index-template.md`. **Append the `## Flow-level Completeness Audit` section** from the Phase 1.5 probe record — always present, even when no gaps were found (one sentence: "No flow-level gaps identified."). This is where the probe record from Phase 1.5 lands.

3. **Write foundation tasks**: `features/{feature-slug}/tasks/foundation.md` — from the plan's foundation table. Follow `.claude/skills/create-stories/templates/foundation-tasks-template.md`. Include the contract generation note (Step 0).

4. **Write tracker**: `features/{feature-slug}/tracker.md` — all story rows initialized to `not-started`. Include the "Cross-story integration tests" row (T1) as the final gate. Follow `.claude/skills/create-stories/templates/tracker-template.md`. Initialize the Mockup column: `N/A` for BE-only stories (`layers: ["BE"]`); `—` for all FE stories (mockup creation is always optional — never mark it as required or pending). For decompose-brd features, add a `seed_id` column so PMs can join by seed across foundation, story, and bug tracking.

5. **Write story files**: One `features/{feature-slug}/stories/US-NN-{slug}.md` per story in the plan. Follow `.claude/skills/create-stories/templates/story-template.md`. Each file must be self-contained: description, **acceptance criteria with stable AC IDs**, **out of scope**, **non-functional notes (only when story-specific)**, **UI states (FE only)**, deps, spec references, BE tasks + unit tests, FE tasks + Vitest tests, E2E Playwright scenarios (with AC ID coverage annotations), flat execution checklist (with AC ID references), done-when. Frontmatter must NOT include the legacy `mockup_file` field; if a mockup actually exists for a story, add it as a bullet inside `## Spec references` instead. For decompose-brd features, frontmatter must include `seed_id` (string for single seed, array for merged seeds, `null` for foundation/infra stories without a seed).

6. **Write test plan**: `features/{feature-slug}/test-plan.md` — cross-story integration tests from the plan's test plan highlights. Follow `.claude/skills/create-stories/templates/test-plan-template.md`. Remove template categories that don't apply to this feature.

7. **Show next steps**:
   ```
   Stories created. Next steps:

     1. [Contracts — FIRST] cd ../tenxengage-contracts && /generate-contracts {feature-slug}
        Run before any BE or FE work begins.

     2. [BE Foundation] cd ../tenxengage-backend && /execute-foundation {feature-slug} F1
        Run F1–F4 sequentially (enums → migrations → entities → permissions).

     3. [BE Stories] cd ../tenxengage-backend && /load-story {feature-slug} US-01
        Run after foundation. One session per story.

     4. [FE Stories] cd ../tenxengage-frontend && /load-story {feature-slug} US-01
        FE story sessions can start after contracts are generated.

     5. [Pick next] Run /next-eligible to see which stories are unblocked.
        Each story: one BE session + one FE session. Update tracker.md at start and end.
   ```

---

## Wall Time

Run this as the final step after showing next steps:

```bash
echo "⏱ create-stories wall time: $(($(date +%s) - $(cat /tmp/create_stories_start)))s"
```

---

## Rules

- **Read spec.md first** — every story decision traces back to a spec section. If a story task can't cite a spec section, it shouldn't exist.
- **No code generation** — this skill produces only markdown files
- **No generic placeholders** — every file path, DTO name, component name, and service method must be concrete (from the spec and codebase conventions)
- **Test co-location is non-negotiable** — every story that creates implementation must co-locate its tests in the same story file
- **Merge first, then split** — default to merging related operations; only split when the checklist exceeds 20–25 items or the entities/controllers are genuinely distinct
- **Layer N/A is explicit** — use `N/A` in tracker for the inapplicable layer's status columns; never leave them blank
- **AC traceability is non-negotiable** — every Acceptance Criterion (AC-N) must be referenced by at least one E2E scenario header or Execution checklist item. An untraced AC means the story is missing test coverage and is not ready to start.
