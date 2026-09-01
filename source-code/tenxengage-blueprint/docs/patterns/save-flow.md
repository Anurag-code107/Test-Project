# Pattern: save-flow

## When this applies

Use this pattern when a feature introduces any form or editor with a save action. Signal: any spec describing a submit button, a "Save" CTA, or a mutation that persists user-entered data.

## The two save models

- **Terminal save** — the entire form state persists in **one API call** when the user clicks the final CTA. Both create (POST) and update (PUT) send the full builder state through a single unified `Save{Entity}Request` DTO. This is the **default and the standard for all enablement builders** (Course, Learning Path, future modules) and for config-only builders (incentive builder) and simple forms.
- **Incremental save** — each section/step fires its own targeted PATCH/PUT as it is edited; the parent entity is created on the first substantive save. This is a **constrained, legacy variant** reserved for genuinely artifact-heavy flows (see the narrow criteria in [builder-wizard.md](builder-wizard.md#save-model--terminal-save-is-the-enablement-default)). **Do not reach for it by default.** The course builder was originally authored incremental and is migrating to terminal save behind the `course_builder_terminal_save` flag; the course backend already exposes the unified `SaveCourseRequest` for both POST and PUT.

> **Decision rule for a NEW enablement module (e.g. Learning Path, Certification Program): use terminal save.** Author a single `Save{Entity}Request` DTO that carries every section (basics, dates, steps/composition, milestones, rewards, audience rules, …) and bind it to BOTH the POST (create) and PUT (update) endpoints. Learning Path (`SaveLearningPathRequest`) is the reference implementation. This was historically ambiguous — earlier revisions of [builder-wizard.md](builder-wizard.md) listed "learning paths with embedded media" under the incremental variant. That guidance was wrong and has been corrected: **Learning Path is terminal-save, full stop.**

### Unified `Save{Entity}Request` conventions (terminal save)

- One DTO covers both create and update. On create, the optimistic-lock `version` is `null` (service derives `status=DRAFT` + `authorUserId` from the JWT — never accepted from the body, mass-assignment guard). On update, `version` carries the lock counter.
- Section nullability semantics must be documented per DTO. Learning Path convention: a `null` section means "no change" on update; for collection-shaped sections an explicit empty list (`[]`) means "clear all" while `null` means "pass through" (e.g. audience rules).
- When a request field is renamed to align with the contract, add `@JsonAlias("oldName")` — `JacksonConfig` globally disables `FAIL_ON_UNKNOWN_PROPERTIES`, so a stale field name is silently read as `null` and causes data-loss on replace-semantics endpoints (root cause of paths-and-assignments US-03).
- Reward sub-sections accept a currency as **either a currency code (e.g. `"cash"`, `"points"`) or a UUID string** in the same `currencyId` field; the service resolves it. See [enablement-rewards.md](enablement-rewards.md) / [currency-handling.md](currency-handling.md).
- Ordered/positional references inside the payload (step prerequisites, milestone placement) use **the in-payload `sortOrder`** (e.g. `prerequisiteSortOrder`, `placementAfterSortOrder`), not internal entity UUIDs — the service resolves them to FKs after persisting the collection. This keeps the request self-describing and editor-friendly (the FE never sees server UUIDs for unsaved rows).
- Reference: `tenxengage-backend/src/main/java/com/tenxengage/app/dto/request/learningpath/SaveLearningPathRequest.java`, `dto/course/SaveCourseRequest.java`.

## Spec authoring guidance

- State the save model explicitly. For enablement builders this is always **terminal save** — do not present it as an open choice.
- Specify submit button states: default, loading (disabled + spinner), success (brief), error.
- Specify optimistic updates: does the UI update immediately before the API responds? What happens on failure?
- Specify the success toast: exact copy, duration, and whether it links to the saved entity.
- Specify retry behavior: does the UI re-enable the submit button automatically after failure?

## Implementation guidance

TBD — capture from:
- `src/components/course-builder/SaveButton.tsx`
- `src/components/ui/Button.tsx` (loading state pattern)
- Incentive builder's terminal save flow (`REQUEST_CREATE_CONFIRMATION` action)

Sections to document:
- Submit button loading state: `disabled` + spinner icon + "Saving..." label while mutation is in-flight
- Optimistic update via TanStack Query `onMutate` / `onError` rollback
- Success toast: `toast.success("Saved")` or `toast.success("{Entity} saved", { description: "..." })`
- Error toast vs. inline error: non-recoverable API errors → toast; validation errors → inline under the field
- Terminal save vs. incremental save decision rule (terminal is the enablement default — see "The two save models" above and builder-wizard.md)
- Edit-mode step-completion derivation (terminal builders): on reopen, the step-progress widget reflects which sections are already complete, derived from builder-config `is_mandatory` fields — see [builder-config.md](builder-config.md) and the frontend `builder-widget-platform.md` § "Edit-mode completion derivation"
- Draft state preservation: show the "Untouched · N days" muted label for empty drafts (incremental variant only)

## Examples in codebase

- `../tenxengage-frontend/src/components/learning-path-builder/LearningPathSaveButton.tsx` — terminal save reference for enablement builders (single POST/PUT with the full `SaveLearningPathRequest`)
- `../tenxengage-frontend/src/components/incentive-builder/` — terminal save (config-only)
- `../tenxengage-frontend/src/components/course-builder/SaveButton.tsx` — incremental save (legacy course path; migrating to terminal behind `course_builder_terminal_save`)
- Backend unified DTOs: `tenxengage-backend/.../dto/request/learningpath/SaveLearningPathRequest.java`, `.../dto/course/SaveCourseRequest.java` — one DTO bound to both POST and PUT

## Common gotchas

TBD
