---
name: "review-spec"
description: "Validate a feature spec across 19 architectural dimensions (+ 4 conditional for feature-brief specs). Acts as a senior architect reviewing the design before implementation begins."
argument-hint: "feature-slug (e.g., quiz-engine)"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Locate the Spec

1. Parse the feature slug from user input (e.g., `quiz-engine`)
2. Read `features/{feature-slug}/spec.md`
3. Read `features/{feature-slug}/technical.md` — if not found, flag as CRITICAL: "technical.md is missing. Run `/create-spec` to regenerate both files."
4. If spec.md not found, error: "No spec found at features/<slug>/spec.md. Pass a valid slug, e.g. /review-spec quiz-engine."

---

## Required Reading (Grounding Sources)

Before reviewing, read these files to compare the spec against actual project patterns:

### Contracts (source of truth)
1. Read `../tenxengage-contracts/enums-index.md` — compact enum registry; deep-read specific `enums.md` section only when checking a particular enum's values
2. Read `../tenxengage-contracts/PROJECT-CONTEXT.md` — API conventions to verify spec compliance
3. Glob `../tenxengage-contracts/models/*.md` — existing data models to check for conflicts or overlap

### Backend
1. Read `../tenxengage-backend/PROJECT-CONTEXT.md` — backend conventions and critical rules
2. Read `../tenxengage-backend/contracts/openapi.json` — first 100 lines for structure patterns
3. Read 1 existing entity from `../tenxengage-backend/src/main/java/com/tenxengage/app/entity/` — field patterns
4. Read 1 existing controller from `../tenxengage-backend/src/main/java/com/tenxengage/app/controller/` — endpoint patterns
5. Read 1 existing service from `../tenxengage-backend/src/main/java/com/tenxengage/app/service/` — service patterns
6. Glob `../tenxengage-backend/src/main/resources/db/migration/V*.sql` — latest migration number

### Frontend
7. Read `../tenxengage-frontend/PROJECT-CONTEXT.md` — frontend conventions
8. Read `../tenxengage-frontend/src/App.tsx` — route structure
9. Glob `../tenxengage-frontend/src/components/ui/*.tsx` — available components

---

## Review Dimensions (19 Checks + 4 Conditional)

Run each check against the spec. For each check, output one of:
- **PASSED** — no issues found
- **CRITICAL** — must fix before proceeding (blocks implementation)
- **WARNING** — should fix but not blocking
- **SUGGESTION** — nice to have improvement

### Check 1: Completeness

- `spec.md`: every required section from the spec template is present and filled; no `TODO`, `TBD`, `{{placeholder}}`, or empty sections remain; all entities have complete field lists; all endpoints defined with request/response types; no Java code blocks, no Flyway SQL, no TypeScript type code (those belong in `technical.md`)
- `technical.md`: present and contains — Flyway Migrations (DDL + permission seed SQL), Package Layout [BE], Repository Queries [BE], Package Layout [FE], Hook Specs [FE] (omit Hook Specs for BE-only features)
- All test scenarios are listed (unit, integration, API, E2E, cross-cutting)
- Permission Matrix `Y` cells in `spec.md` match the `IN (...)` lists in `technical.md → ## Flyway Migrations` role-grant blocks

### Check 2: Internal Consistency

- API endpoint paths match the data model (e.g., if entity is `Quiz`, path should be `/quizzes` not `/quiz`)
- DTO fields match entity fields (no phantom fields in response that don't exist in entity)
- Response types in endpoint table match the DTO definitions
- FE TypeScript types match the API contract DTOs
- FE hooks reference the correct endpoints
- Service method signatures match what controllers call
- State machine transitions are consistent between workflow section and service business rules

### Check 3: Pattern Conformance

Compare against patterns read from the actual codebase:
- Entities extend `BaseEntity` and implement `TenantAware`
- All IDs are UUIDs (never auto-increment)
- All tables have `client_id`
- DTOs use records (not classes) for responses
- Response DTOs have `from()` static factories
- Request DTOs use Jakarta Bean Validation annotations
- Controllers return `ResponseEntity<T>`
- REST paths use kebab-case (`/api/v1/resource-name`)
- Service methods use `@Transactional` with `readOnly=true` for reads
- Constructor injection (no field `@Autowired`)
- Enums proposed in the spec match or extend (not conflict with) values in `../tenxengage-contracts/enums-index.md` — flag any enum that duplicates an existing one with different values
- API conventions (pagination shape, error format, URL patterns) match `../tenxengage-contracts/PROJECT-CONTEXT.md`
- New data models don't conflict with existing models in `../tenxengage-contracts/models/`

### Check 4: Data Model Soundness

- Relationships are correct (1:N, N:M with join tables)
- Foreign keys point to the right parent tables
- Cascade delete implications are safe (no orphan data, no unintended cascades)
- JSONB is used appropriately (flexible config) vs normalized tables (structured data)
- Indexes exist for commonly queried columns (client_id, status, foreign keys)
- No unnecessary nullable columns (prefer NOT NULL with defaults)

### Check 5: API Design Quality

- Correct HTTP methods (GET for read, POST for create, PUT for full update, PATCH for partial, DELETE for remove)
- Proper status codes (201 for create, 204 for delete, 400 for validation, 404 for not found)
- List endpoints use pagination (Spring Page format)
- Response envelope is consistent with existing API
- IDs in paths are UUIDs
- `client_id` is never in the API (handled by tenant filter)
- Idempotent operations where appropriate

### Check 6: Security & Permissions

- Every endpoint has a permission defined (e.g., `@RequiresPermission("MANAGE_COURSES")`)
- Tenant isolation enforced (client_id on every table, filtered by Hibernate @FilterDef)
- No PII leakage in list responses (only detail endpoint returns sensitive fields)
- Input validation on all request fields (size limits, format checks)
- Rate limiting mentioned for public-facing or heavy endpoints
- File upload endpoints validate file types and sizes

### Check 7: State Machine Validity

- All states are reachable (no orphan states)
- No dead states (states you can enter but never leave — unless intentional like ARCHIVED)
- All transitions have a defined trigger and permission
- Invalid transitions are explicitly listed
- Concurrent state change handling is addressed (optimistic locking?)

### Check 8: Edge Cases Sufficiency

Check if these common edge cases are covered:
- Empty collections (0 items in list)
- Maximum limits (what happens at boundary values)
- Concurrent access (two users modifying same entity)
- Permission boundaries (user with partial permissions)
- Deleted/archived entity access (attempting to modify archived items)
- Cross-tenant access (must return 404, not 403)
- Special characters in text fields (unicode, emoji, HTML)
- Pagination edge cases (last page, page beyond total)

### Check 9: Test Coverage Gaps

- Every edge case listed has a corresponding test scenario
- Every error response code has a test
- Every state transition has a test (valid and invalid)
- Playwright E2E scenarios cover the full user flow (create, read, update, delete)
- Playwright tests include error states and empty states
- Cross-cutting tests cover permissions, tenant isolation, feature flags

### Check 10: Frontend Feasibility

- Referenced shadcn/ui components actually exist (compared against the glob results)
- Loading, error, and empty states are defined for every data-fetching component
- Data flow makes sense (correct TanStack Query hooks, query keys follow existing patterns)
- Form validation schema matches the API contract constraints
- Mobile responsiveness is considered (breakpoints defined)
- Routes follow existing patterns in App.tsx

### Check 11: Performance & Scale

- N+1 query risks identified (list endpoint fetching nested data — use @EntityGraph or JOIN FETCH)
- Large payload responses use pagination (no unbounded lists)
- Database indexes exist for all filtered/sorted columns
- Caching opportunities identified (rarely changing data → @Cacheable)
- Async processing for heavy operations (file parsing, AI calls → @Async)
- List vs detail response pattern (lightweight list, full detail on demand)

### Check 12: Scope & Dependencies

- Feature is self-contained (no dependencies on unbuilt features)
- "Out of Scope" section is adequate (common related requests are explicitly deferred)
- Feature is not trying to do too much (should be shippable in 1-2 sprints)
- No circular dependencies with other features

### Check 13: Non-Functional Requirements & Production Readiness

- NFR section is present and filled (not left as placeholders)
- Response time SLAs are specified (P95 targets for reads and writes)
- Data sensitivity classification is set — not left as `{{PUBLIC | INTERNAL | CONFIDENTIAL | PII}}`
- Max page size is specified and enforced in the API endpoint table (`@Max(50)` or equivalent)
- Availability target is stated (even if "internal tool")
- `sort` query param has an allowlist of permitted columns (no open-ended sort)
- Optimistic locking (`@Version`) is present on any entity with concurrent update risk
- Soft delete (`deleted` flag) is on all business entities — no hard deletes of business records

### Check 14: Security Design Completeness

- Security Design section is present (never acceptable to omit for a multi-tenant app)
- Every PII field identified in the Data Model has a row in the Data Classification table
- Every write endpoint has a rate limit entry — or the section explicitly states why rate limiting doesn't apply
- OWASP risks listed are specific to this feature (not a generic boilerplate list)
- Input validation table covers every free-text and enum field
- Responses never include `client_id`, `deleted`, `version`, or internal fields
- 404 (not 403) is the specified response for cross-tenant resource access on all `/{id}` endpoints
- SQL injection risk on `search` params is addressed with parameterized JPQL pattern
- If file upload exists: MIME type validation, size limit, and storage location are specified

### Check 15: Audit, Observability & Compliance

- Audit Trail section is present and covers every `@Audited` write operation
- Each audit record specifies `oldValue`/`newValue` for status transitions (not just "entity changed")
- Observability section has at least 3 meaningful log events with `step` values
- MDC fields are specified (at minimum `requestId`, `tenantId`, `userId`)
- At least one metric counter or histogram is defined
- No PII fields appear in the log event Key Fields column
- Data Retention & Compliance section is present
- Soft-delete vs hard-delete decision is explicit with reasoning
- If any PII fields exist: GDPR treatment is specified for each (anonymization approach)
- Retention periods are specified for business records and audit logs
- Caching Strategy section is present — even if "no caching" is the answer

### Check 16: Brief Alignment (conditional)

> **Skip this check** when the spec was not derived from a `/decompose-brd` feature brief — i.e., when no `roadmaps/{slug}/features/F-NN-*.md` file exists for this feature (legacy roadmap or free-text input).

When a feature brief was used:
- The spec's `## Functional Requirements` table preserves the brief's FR numbering (FR-NN.1, FR-NN.2, …) — no silent renumbering, dropping, or splitting
- Any deviations (renumbered / dropped / split FRs) are explicitly flagged with a "Brief deviation" note and justification in the spec
- Net-new FRs added by the spec author are appended with the next number in sequence, not inserted mid-list
- A `## Planning seeds (from feature brief)` section is present in the spec, populated verbatim from the brief's Suggested Story Seeds table

### Check 17: Advisory Reconciliation Discipline (conditional)

> **Skip this check** when no `digest-annex.md` exists at `roadmaps/{slug}/digest-annex.md` for the roadmap this feature was sliced from.

When a `digest-annex.md` was available for this feature:
- The spec does NOT silently adopt BRD-stated entity / event / API names without reconciling against the codebase
- A `### Naming reconciliation` sub-section exists (within Overview or Data Model) documenting each name decision — adopted vs. overridden and why
- Names that conflict with existing codebase conventions or contracts repo are overridden (codebase wins), and the decision is documented
- The `digest-annex.md` is treated as advisory, not authoritative — the spec's named entities, events, and ops reflect codebase patterns

### Check 18: Domain registry conformance (slot-filling features only)

Conditional: applies only when `spec.md` frontmatter has `domain:` non-null.

- `spec.domain` is non-null and matches one of the known domains in `docs/patterns/domains/INDEX.md` (or is being introduced as a new domain in this same commit — check the staged diff).
- `spec.builder_type` is non-null when the feature is builder-shaped (FRs reference a multi-section wizard or section-keyed builder).
- For each entity in the spec's Data Model that fills a canonical slot (audience rule, eligibility engine, completion entity, budget, approval, section/field storage), the entity name matches the registry filler in `docs/patterns/domains/{domain}.md` — OR the spec carries an explicit deviation note linking back to a Registry edits decision in the plan file.
- If `domain` is non-null but `docs/patterns/domains/{domain}.md` does not exist AND is not being introduced in this commit → CRITICAL.

Output one of PASSED / CRITICAL / WARNING / SUGGESTION.

### Check 19: Fidelity fields (builder-shaped and entity-list features)

- **Builder-shaped features** (`builder_type` non-null): `visual_reference.component_path` MUST be present (non-null) AND the path must exist on disk in the frontend repo. `applicable_sections.sections` MUST be non-empty.
  - If `visual_reference.component_path` is null or missing → CRITICAL: "Builder-shaped spec missing visual_reference. Run /create-spec and answer the visual reference prompt."
  - If `applicable_sections.sections` is empty or missing → CRITICAL: "Builder-shaped spec missing applicable_sections. List which BuilderDefinition sections this feature renders."
  - If `component_path` is non-null but the file does not exist on disk → CRITICAL: "visual_reference.component_path points to a non-existent file: {path}. Verify the path in the frontend repo."
- **Non-builder features with a clear visual sibling** (e.g., an entity-list page where another entity-list page already exists): if `visual_reference` is null → WARNING: "Feature appears to have a visual sibling in the codebase. Consider setting visual_reference.component_path to guide fidelity."

### Check 20: Functional Completeness Audit section present

> This check is WARNING (not CRITICAL) to keep backward compatibility with specs authored before the probe existed.

- `spec.md` contains a `## Functional Completeness Audit` section.
- The section is filled — not left as the template placeholder rows.
- If the section is absent → WARNING: "spec.md is missing the `## Functional Completeness Audit` section. Run `/create-spec` to regenerate, or add the section manually to record the functional-completeness probe outcome."
- If the section contains unfilled template placeholders (`{{...}}`) → WARNING: "Functional Completeness Audit section has unfilled placeholders. Fill from the probe outcome or replace with 'No functional gaps identified.'"

### Check 21: UI-backing DTO specificity (CRITICAL)

For every response DTO that a page, drawer, table, or detail surface renders:
- Each field the FE displays is enumerated with a type — not just a "Key Fields" summary or a "see contracts repo" pointer.
- Reference-only detail entries are rejected: a detail/drawer DTO whose child/step entries carry only `refId` + type + order (no display name/description/status the surface shows) → CRITICAL: "Detail DTO {name} references entities by id but carries no display fields; the detail surface cannot render."
- Any encoded / polymorphic / JSON-blob field (discriminator-driven `type`/`format`/`variant`, or a JSONB column) has a concrete per-variant schema **and** a JSON example per variant. A field documented only as "encoded per format", "shape varies", or a bare `JSONB` Notes cell → CRITICAL: "Field {name} is polymorphic/encoded but has no per-variant schema; BE, FE, and AI will each invent a different shape."

Verdict ladder:
- **PASSED** — every UI-backing response DTO enumerates each displayed field with a type, child/step entries carry the display fields the surface shows (not just `refId`), and every polymorphic/encoded/JSONB field has a per-variant schema plus a JSON example per variant.
- **CRITICAL** — any of the triggers above fire (reference-only detail entries, or a polymorphic/encoded field with no per-variant schema).
- **WARNING** — most UI-backing DTOs enumerate their displayed fields, but at least one is thin (a summary "Key Fields" line or a "see contracts repo" pointer instead of a typed enumeration) without rising to a reference-only or encoded-field violation.
- **SUGGESTION** — fields are enumerated and typed, but a JSON example is missing for a non-polymorphic DTO, or field descriptions are terse — cosmetic gaps that don't block rendering.

Output one of PASSED / CRITICAL / WARNING / SUGGESTION.

### Check 22: Read/detail surface display contract (CRITICAL)

For every user-facing entity whose `## Frontend Specification → Pages` lists a detail/view page (`/{route}/:id`) or whose UI has a detail drawer/panel:
- The spec defines a `{{Entity}}DetailResponse` (or equivalent) whose rendered fields are enumerated (Check 21), AND
- The spec's Frontend Specification names the detail surface and the sections it renders (e.g., Details, Stats) so `/create-stories` can derive a "view {entity} detail" story with display-level ACs.
- If the spec describes create/edit builders for an entity but no detail/view surface with an enumerated display contract → CRITICAL: "Entity {name} has builders but no detail/view display contract. The detail surface will be cloned from another feature. Add a {{Entity}}DetailResponse with enumerated fields and a Frontend Specification detail surface."

Verdict ladder:
- **PASSED** — every user-facing entity with a detail/view page or drawer defines a `{{Entity}}DetailResponse` (or equivalent) with enumerated rendered fields, AND the Frontend Specification names the detail surface and the sections it renders (e.g., Details, Stats) so `/create-stories` can derive a "view {entity} detail" story with display-level ACs.
- **CRITICAL** — an entity has create/edit builders but no detail/view surface with an enumerated display contract (trigger above).
- **WARNING** — a detail surface exists and defines a `{{Entity}}DetailResponse`, but the Frontend Specification does not name the sections it renders, so the view story's display-level ACs cannot be derived cleanly.
- **SUGGESTION** — the detail contract and named sections are present, but section ordering or grouping is left implicit — an advisory clarification, not a rendering blocker.

Output one of PASSED / CRITICAL / WARNING / SUGGESTION.

### Check 23: Composite UI element completeness (CRITICAL)

Checks 21 and 22 pin down the data contract and the detail/view surface. Check 23 is the broader net: every CONTENT-BEARING / COMPOSITE element in the `## Frontend Specification` must carry its own build contract, not just the ones tied to a detail page. A composite/content-bearing element is one that renders structured content with multiple parts — detail pages, drawers, multi-section panels, cards, dashboards, data tables. Plain buttons, single standard inputs, and other atomic controls are out of scope.

For every such element, the spec must enumerate:
- **(a) Sections and content** — the element's sections and, per section, the content/fields each renders (not "renders the entity" — the actual fields, columns, or content blocks).
- **(b) Interactions** — what is actionable (rows, buttons, menu items, expand/collapse) and the keyboard path to each.
- **(c) Accessibility & responsive** — that a11y (roles, labels, focus order) and responsive behavior (breakpoints, reflow/stacking) are addressed for the element.

Scope boundaries — do not double-cover:
- Loading / empty / error states are **out of scope here** — that is Check 10's job. Reference it; don't re-flag.
- Permission-gated visibility is **out of scope here** — that is Check 6's job.
- This check builds on Checks 21 (DTO field specificity) and 22 (detail-surface display contract); it does not replace them — it extends the same completeness expectation to all composite elements, including those with no detail page.

- CRITICAL trigger: a composite/content-bearing element exists in the FE spec but its sections/content are not enumerated (e.g. "renders the entity" with no breakdown) → "Composite element {name} has no enumerated section/content contract; it will be built incomplete or cloned from another feature."

Verdict ladder:
- **PASSED** — every composite/content-bearing element in the Frontend Specification enumerates its sections and the content/fields each renders, names its interactions (actionable parts + keyboard path), and confirms accessibility and responsive behavior are addressed.
- **CRITICAL** — a composite element exists but its sections/content are not enumerated (trigger above); the element has no buildable contract.
- **WARNING** — sections and content are enumerated, but interactions or accessibility/responsive behavior are left unaddressed for at least one composite element, so the element is partially specified.
- **SUGGESTION** — sections, content, interactions, and a11y/responsive are all present, but section ordering, grouping, or interaction grouping is left implicit — a cosmetic clarification, not a build blocker.

Output one of PASSED / CRITICAL / WARNING / SUGGESTION.

---

## Output Format

```
=== SPEC REVIEW: {feature-id} ===

CRITICAL (must fix before proceeding):
  1. [{Check Name}] {Specific issue with file/section reference}
  2. [{Check Name}] {Specific issue}

WARNINGS (should fix, not blocking):
  3. [{Check Name}] {Specific issue}

SUGGESTIONS (nice to have):
  4. [{Check Name}] {Specific improvement}

PASSED CHECKS: {list of check names that passed}

Score: {N}/{M} passed | {N} critical | {N} warnings | {N} suggestions
(M = 19 for one-off specs; +1 each for brief alignment, advisory reconciliation, domain registry conformance, and fidelity fields as those conditions apply — max 23)
Status: {APPROVED | NEEDS REVISION}
```

---

## Interactive Clarification

During the review, if any check reveals **ambiguity or missing information that cannot be resolved by reading the codebase**, ask the user directly. Do NOT guess — ask.

Examples of when to ask:
- The spec says "users can delete courses" but doesn't specify whether this is a soft delete or hard delete — ask the user
- An endpoint is missing permission definition and the correct permission isn't obvious from context — ask the user
- The data model has a field with an unclear type or purpose — ask the user
- A workflow transition is ambiguous (e.g., can ARCHIVED go back to DRAFT?) — ask the user

**How to ask:**
- Collect all clarification questions across all checks
- Present them to the user in a single interactive batch (not one at a time)
- After the user answers, update the spec with the answers
- Re-run the checks that were affected by the answers

**Do NOT ask about:**
- Things you can determine by reading the codebase (e.g., "what's the BaseEntity pattern?" — just read it)
- Style preferences that already have established conventions (e.g., "should we use records for DTOs?" — yes, the project already does)
- Theoretical edge cases that are already covered by existing project patterns

---

## Post-Review Actions

### If NEEDS REVISION:
1. List all critical issues with specific fix instructions
2. Auto-fix critical issues when the fix is obvious (e.g., missing permission annotation — add it)
3. For issues that need user input: ask the user interactively, update the spec with their answers
4. Re-run the review (repeat from the top) until all critical issues are resolved

### If APPROVED:
1. Update the spec frontmatter: change `Status: draft` to `Status: reviewed`
2. Add a review timestamp: `> **Reviewed**: {DATE}`
3. Output the result — do NOT auto-run `/generate-contracts` or auto-commit
4. The calling skill (`create-spec`) or the user will handle next steps

---

## Rules

- Be thorough but practical — don't flag theoretical issues that won't happen in this codebase
- Compare against ACTUAL patterns from the codebase, not textbook ideals
- Critical issues are things that would cause bugs, security holes, or implementation confusion
- Warnings are things that could cause problems but have workarounds
- Suggestions are genuine improvements, not nitpicks
- Auto-fix critical issues when the fix is obvious (e.g., missing permission annotation — add it)
- **Ask the user when genuinely ambiguous** — don't guess, don't assume, don't skip
- When in doubt about a pattern, check the codebase rather than flagging
