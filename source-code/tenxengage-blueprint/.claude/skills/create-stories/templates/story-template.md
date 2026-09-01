---
id: US-NN
title: "{{Story title}}"
layers: ["BE", "FE"]   # ["BE", "FE"] | ["BE"] | ["FE"]
touches_entities: ["{{Entity1}}", "{{Entity2}}"]
depends_on_stories: ["US-NN"]
---

# US-NN: {{Story title}}

## Description

**Actor:** {{Actor role — e.g., CLIENT_ADMIN, PARTNER_SELLER}}
**Trigger:** {{What initiates this story — e.g., "User clicks 'New Course' on the Courses list page"}}

**Steps:**
1. {{Step 1 — what the user does}}
2. {{Step 2}}
3. {{Step 3}}

**Expected outcome:** {{What the user sees / gets — e.g., "New course appears in the list with status DRAFT; toast shows 'Course created'"}}

**Negative paths:**
- {{Invalid input → specific error shown — e.g., "Empty title → error below field: 'Title is required'"}}
- {{Permission denied → redirect or disabled control}}

---

## Acceptance Criteria

_Each bullet is a single binary claim — what must be true for this story to be considered done. AC IDs are stable and referenced from E2E scenarios and Execution checklist items. Source from `spec.md → ## Functional Requirements` (one FR row may produce one or more AC bullets here; only include the ACs that this story owns)._

_For any story that builds a content-bearing/composite element (detail page, drawer, multi-section panel, card, dashboard, table), ACs must be **display-level**: per section, one AC enumerating what content is rendered, plus the section's key interactions (e.g., "Detail drawer Details section shows {name, description, type, status}"; "Stats section shows {enrolledCount, completionRate}"; "Lessons table rows show {title, duration, status}; row click opens lesson editor"). This carries the spec's element-completeness contract into testable ACs — a story whose ACs only assert "drawer opens" is incomplete; it must assert what each section displays and does._

- **AC-1:** {{Specific verifiable claim — e.g., "POST /api/v1/courses with valid body returns 201 and a CourseDetailResponse with state=DRAFT, version=1"}}
- **AC-2:** {{e.g., "Cross-tenant GET /api/v1/courses/{id} returns 404, never 403"}}
- **AC-3:** {{e.g., "Title field validation rejects empty input with error 'Title is required'"}}
- **AC-4:** {{e.g., "Audit row written on create with action=Created, resourceType=COURSE, resourceId=newId"}}

---

## Out of Scope

_Adjacent work this story explicitly does NOT do. Anti-drift anchor for sessions and clarity signal for reviewers. If there is truly nothing, write `— none`._

- {{e.g., "Course publish flow (covered by US-04)"}}
- {{e.g., "Bulk import (deferred — not in this feature)"}}

---

## Non-Functional Notes

_**Optional section.** Include only when there is a story-specific perf budget, a11y requirement, telemetry event, i18n callout, or responsive constraint not already obvious from `spec.md → ## Non-Functional Requirements` / `## Security Design` / `## Observability`. **Skip the section entirely** when the cross-cutting spec sections cover it — do not leave a placeholder._

- **Perf:** {{e.g., "List endpoint p95 < 400 ms with 1k rows (story-specific tighter bound than spec default)"}}
- **a11y:** {{e.g., "Filter chip group must be keyboard-navigable and announce selected state to screen readers"}}
- **Telemetry:** {{e.g., "Emit `enablement.course.created` event on successful save with payload {courseId, locale}"}}
- **i18n:** {{e.g., "Locale picker uses BCP-47 codes; UI strings sourced from i18n bundle, no hardcoded English"}}
- **Responsive:** {{e.g., "Builder collapses to single-column layout below 768 px"}}

---

## UI States

_FE stories only. Omit this entire section if `layers: ["BE"]`._

- [ ] **Loading:** {{e.g., "Skeleton rows in table while initial query is in flight"}}
- [ ] **Empty:** {{e.g., "No courses yet — show EmptyState with copy 'No courses yet — create your first course' + primary CTA"}}
- [ ] **Error:** {{e.g., "5xx fallback — show ErrorState with retry button; toast 'Could not load courses'"}}
- [ ] **Partial / Optimistic:** {{e.g., "Newly created course appears immediately in list with optimistic state badge while POST is in flight" — omit row if not applicable}}

### Verbatim microcopy

_Required when this story introduces user-visible strings not already quoted in `## Acceptance Criteria` or in the empty/error bullets above; omitted entirely otherwise. /create-mockups and /load-story (FE) both read this — if a string isn't here, they will invent different ones._

- Button labels: {{e.g., "Add Lesson", "Confirm delete", "Cancel"}}
- Success toast: {{e.g., "Lesson added"}}
- Tooltip on disabled CTA: {{e.g., "Insufficient balance"}}
- Helper text: {{e.g., Under fileSizeBytes field: "Max 50 MB"}}
- Placeholder text: {{e.g., Search input: "Search by course title"}}

### Conditional rendering

_Required when the FE renders differently based on a discrete input (entity state, caller role, permission, feature flag, entity-derived boolean); omitted entirely otherwise._

**Input: {{discrete input — e.g., `course.status`}}**
- `{{VALUE_1}}`: {{what is visible / hidden / enabled / disabled — e.g., "Publish CTA visible; Delete enabled on all lessons"}}
- `{{VALUE_2}}`: {{e.g., "Unpublish CTA visible; Delete-last-lesson disabled with tooltip"}}
- `{{VALUE_3}}`: {{e.g., "All mutation CTAs hidden; banner 'Archived — read-only'"}}

_Add additional `**Input:** ...` blocks if multiple discrete inputs affect rendering. Common inputs: entity status, caller role, permission presence, feature flag, entity-derived boolean (e.g., `canAfford`)._

---

## Depends on

- **Foundation tasks:** F1, F2, F3 _(adjust to the actual tasks this story needs)_
- **Prior stories:** {{US-NN — e.g., "US-01 (entity must exist before it can be edited)" — or "None"}}

---

## Spec references

_Read these sections of `spec.md` before implementing — do not guess, read the spec._

- `## Functional Requirements` — FR rows that map to this story's AC bullets
- `## Data Model / Entities [BE]` — `{{EntityName}}` fields and relationships
- `## API Endpoints [BE + FE]` — `{{METHOD}} /api/v1/{{path}}` row(s) for this story
- `## DTOs [BE]` — request and response shapes
- `## Service Layer [BE]` — `{{ServiceMethod}}` business rules
- `## Permissions & Feature Flags [BE + FE]` — `action.{{entity}}.{{verb}}` row(s) used here
- `## Security Design [BE]` — rate limits and input validation rules for this endpoint
- `## Audit Trail [BE]` — audit row(s) triggered by this story
- _(Optional)_ Mockup: `{{path to Figma frame or mockup component}}` — only include this bullet if a mockup actually exists

---

## BE tasks [BE]

<!--
Numbering below (BE-1, BE-2, …) is a readable label, not a contract. The
typical CRUD-shaped vertical slice lands at 3–4 tasks; stories that need
more (e.g., a Kafka publisher in the same slice, a second service method)
should add BE-5, BE-6, … Stories that need fewer (e.g., a read-only
endpoint with no audit) should drop tasks rather than leaving empty slots.

Per-layer cap: if the BE execution checklist would exceed ~12–15 items,
split into two stories rather than expanding this one.
-->

_Omit this entire section if `layers: ["FE"]`._

### BE-1: DTOs

**Files:** `dto/request/Create{{Entity}}Request.java`, `dto/response/{{Entity}}Response.java`, `dto/response/{{Entity}}DetailResponse.java`

See `spec.md → ## DTOs [BE]` for field lists and validation annotations.

For composite/detail stories, the BE tasks here must **expose** every field/section the FE display ACs render in the response DTO and **hydrate referenced display data** — resolve the human-readable names/descriptions for any `refId`/UUID the surface shows, never returning bare `refId` + type + order or null/blank display values. Exposing the fields is this DTO task; the name/description resolution itself is a service-layer concern — carry it into **BE-2: Service method** below so the hydration half isn't lost.

### BE-2: Service method + unit test

**Files:** `service/{{Entity}}Service.java`, `test/.../service/{{Entity}}ServiceTest.java`

See `spec.md → ## Service Layer [BE]`. Every repository query must include `clientId` (tenant isolation). Unit test covers: happy path, validation failure, business rule enforcement.

### BE-3: Controller endpoint + @WebMvcTest

**Files:** `controller/{{Entity}}Controller.java`, `test/.../controller/{{Entity}}ControllerTest.java`

See `spec.md → ## API Endpoints [BE + FE]`. @WebMvcTest covers: 200/201 happy path, 400 validation, 403 missing permission, 404 wrong tenant.

### BE-4: Audit annotation _(write operations only)_

Add `@Audited` to the controller method. See `spec.md → ## Audit Trail [BE] → @Audited Annotation Details`.

---

## FE tasks [FE]

<!--
Numbering below (FE-1, FE-2, …) is a readable label, not a contract.
The typical page-building slice lands at 3–4 tasks; stories that need more
(e.g., multiple independent components, a secondary hook) should add
FE-5, FE-6, … Stories that need fewer should drop tasks rather than
leaving empty slots.

Per-layer cap: if the FE execution checklist would exceed ~12–15 items,
split into two stories.

Multi-component rule (FE-3): If FE-3 covers more than one top-level
component that can break independently, split into FE-3a, FE-3b … — each
with its own component file and its own __tests__/*.test.tsx. Sub-components
private to one parent are not split.
-->

_Omit this entire section if `layers: ["BE"]`._

### FE-1: TypeScript types + service call

**Files:** `src/types/{{feature}}.types.ts`, `src/services/{{feature}}.service.ts`

Copy types from `../tenxengage-contracts/` only — do not hand-write. See `spec.md → ## TypeScript Types [FE]`.

### FE-2: Hook

**File:** `src/hooks/use{{Entity}}.ts`

See `spec.md → ## Frontend Specification [FE] → Data Flow` for query key, staleTime, and invalidation.

### FE-3: Component + Vitest test

**Files:** `src/components/{{feature}}/{{Component}}.tsx`, `src/components/{{feature}}/__tests__/{{Component}}.test.tsx`

### FE-4: Page wiring _(if this story introduces or modifies a page)_

**Files:** `src/pages/{{feature}}/{{Page}}.tsx`, `src/App.tsx` _(if new route)_

---

## E2E test [FE]

_Omit this entire section if `layers: ["BE"]`._

_Each scenario states the AC IDs it covers in parentheses. Every AC must be covered by at least one E2E scenario, unit test, or @WebMvcTest case (cross-referenced in the Execution checklist)._

---

**Scenario 1:** `'{{descriptive test name}}'` _(covers AC-1, AC-3)_

**File:** `e2e/{{feature}}.spec.ts`

| Field | Value |
|---|---|
| **User flow** | {{Step-by-step: navigate → action → verify}} |
| **APIs to mock via `page.route()`** | `{{METHOD}} /api/v1/{{path}}` → `{{status}}` + response shape from contract |
| **Visible assertion** | `expect(page.getByText('...')).toBeVisible()` or equivalent |
| **Negative case** | {{Invalid input → specific error text visible}} |

---

**Scenario 2 (if applicable):** `'{{descriptive test name}}'` _(covers AC-2)_

**File:** `e2e/{{feature}}.spec.ts`

| Field | Value |
|---|---|
| **User flow** | {{Step-by-step}} |
| **APIs to mock via `page.route()`** | `{{METHOD}} /api/v1/{{path}}` → `{{status}}` + shape |
| **Visible assertion** | `expect(page.getByText('...')).toBeVisible()` |
| **Negative case** | {{Error text visible — omit if no negative case}} |

_Add more scenario blocks as needed. Delete unused blocks._

---

## Execution checklist

_Resumed sessions read this checklist and start from the first unchecked item. Each item references the AC IDs it satisfies (in parentheses) so a reviewer can confirm coverage at a glance._

**BE session:** _(omit if FE-only)_
- [ ] `Create{{Entity}}Request.java` DTO created _(supports AC-1, AC-3)_
- [ ] `{{Entity}}Response.java` DTO created _(supports AC-1)_
- [ ] `{{Entity}}DetailResponse.java` DTO created _(supports AC-1)_
- [ ] `{{Entity}}Service.{{methodName}}` method added _(AC-1)_
- [ ] `{{Entity}}ServiceTest` unit tests pass _(AC-1, AC-3)_
- [ ] `{{Entity}}Controller.{{httpMethod}}` endpoint added with `@RequiresPermission` _(AC-1, AC-2)_
- [ ] `@Audited` annotation on controller method _(AC-4)_ _(if write operation)_
- [ ] `{{Entity}}ControllerTest` @WebMvcTest tests pass _(AC-1, AC-2, AC-3)_

**FE session:** _(omit if BE-only)_
- [ ] `{{EntityInterface}}` TypeScript type added to `{{feature}}.types.ts`
- [ ] `{{verb}}{{Entity}}` service call added to `{{feature}}.service.ts`
- [ ] `use{{Entity}}` hook created with correct query key + staleTime
- [ ] `{{Component}}` component created
- [ ] `{{Component}}.test.tsx` Vitest tests pass _(AC-3)_
- [ ] UI states implemented: loading, empty, error _(matches `## UI States` checklist above)_
- [ ] Page wired to real API (not mocked)
- [ ] E2E: `'{{descriptive test name}}'` Playwright test passes _(AC-1, AC-3)_

---

## Done when

1. **BE** _(if `layers` includes `"BE"`)_: `./gradlew test` passes — all new `{{Entity}}ServiceTest` + `{{Entity}}ControllerTest` cases green
2. **FE** _(if `layers` includes `"FE"`)_: `npm run test` passes + `npx playwright test e2e/{{feature}}.spec.ts -g '{{test name}}'` passes against real BE
3. Every AC ID above is referenced by at least one passing test (unit, @WebMvcTest, Vitest, or E2E)

_For BE+FE stories: `done` only when both are wired and E2E passes. FE scaffolded against mocks = `in-progress`._
