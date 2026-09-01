# Pattern: builder-wizard

> **ⓘ This is the canonical spec-level guide for ALL builder-shaped features** — across incentive and enablement domains alike. Two separate implementation docs cover the frontend details:
> - **[builder-widget.md](../../tenxengage-frontend/docs/patterns/builder-widget.md)** — incentive builder (SALES, TRAINING, ACTIVITY, JOURNEY)
> - **[builder-widget-platform.md](../../tenxengage-frontend/docs/patterns/builder-widget-platform.md)** — enablement builder (course, learning-path, future enablement types)
>
> See [builder-config.md](builder-config.md) for the configuration layer and [audience-rules.md](audience-rules.md) for audience rules.

---

## When this applies

Use this pattern when a feature introduces a **multi-step UI workflow for creating or editing an entity** — for example, an incentive builder, course builder, or quiz builder. The signal is any feature whose spec describes a wizard, accordion-stepped configuration flow, or "builder" page.

## Spec authoring guidance

- Name the feature's builder page `{Entity}BuilderPage` and describe its two-column layout (left: AI copilot or manual summary; right: accordion steps / detail editor / preview).
- Enumerate all builder steps by name in the spec — these become the accordion sections and drive completion tracking.
- Describe the entry flow separately: entity-type menu → type selection → sub-type (if any) → optional template picker → builder.
- Note which steps are shared across entity types and which are type-specific — this maps directly to the type dispatcher component.
- Specify the save model: TenXEngage builders do **not** expose a "Save Draft" button. State lives in context; a single API call fires only when all steps are complete. Enablement builders use **terminal save** by default — see [save-flow.md](save-flow.md) and "Save model — terminal save is the enablement default" below.
- Call out navigation guard requirements: if the user has made changes (isDirty), a confirmation dialog must appear before they can leave.

## Implementation guidance

### Layout

The builder uses a two-column layout. The left column (~40% viewport) displays either an AI copilot chat panel or a manual summary view, toggled by the user. The right column (~60%) cycles between three views — the setup accordion, a detail editor, and a preview panel — using a `FlipTransition` animation.

### Header

Every builder page renders a `PageBanner` containing: title/subtitle, a mode toggle (AI-assisted vs manual; AI gated by the `ai_copilot` feature flag), a completion percentage, and a "Step X of N" indicator with a segmented progress bar.

### Entry Flow

Before the builder loads, the user passes through: entity-type menu → type selection → sub-type (if applicable) → optional template picker → builder. Each transition uses `FlowTransition` (directional slide). Direction is computed from a per-flow-state depth map (see `FLOW_DEPTH` in the reference impl's page component) so retreats and forward moves animate correctly.

### Step and Wizard Pattern

Steps are presented as an accordion; only one expands at a time. Expansion uses `AnimatedCollapse` (300 ms). Each step header shows a badge — step number (incomplete) or checkmark icon (complete). A "Continue to [Next Step]" button advances the user, and the view auto-scrolls to the newly expanded step.

### State Management

Builder state is managed via a dedicated `BuilderContext` + `useBuilderReducer` hook (React Context + `useReducer`). State contains:

- Flow control: current flow state, direction
- Step tracking: which steps are complete, which is active
- Per-step data objects (form values)
- AI chat state (conversation history, streaming status)
- Detail editor state (which item is open)
- Preview / forecasting state
- `builderOrigin` — how the builder was entered (`scratch` / `template` / `existing` / `edit`); contextualizes AI copilot prompts
- `isDirty` flag (edit tracking; never set during `LOAD_{ENTITY}`)

### Actions Pattern

| Convention | Purpose |
| --- | --- |
| `SET_*` | Flow / navigation control (e.g., `SET_FLOW_STATE`, `SET_ACTIVE_STEP`, `SET_MODE`) |
| `UPDATE_*` | Per-step data mutations (e.g., `UPDATE_BASICS`, `UPDATE_SCHEDULE`, `UPDATE_AUDIENCE`, `UPDATE_BUDGET`, `UPDATE_CRITERIA`, `UPDATE_APPROVAL`) |
| `MARK_STEP_COMPLETE` / `MARK_STEP_INCOMPLETE` | Step completion toggles |
| `LOAD_{ENTITY}` | Populates entire state from API response (edit mode). The reference impl uses `LOAD_INCENTIVE`; future builders should follow the same `LOAD_{ENTITY}` convention with their own entity name |
| `RESET` | Clears to initial state |

The reducer performs data normalization on incoming data before storing it.

### Form Pattern

Builders dispatch changes directly to the reducer on every field change — **do not** use react-hook-form. This keeps a single source of truth in context. Each step auto-validates via a `useEffect` watching its data slice and marks itself complete/incomplete when all required fields are filled.

Config-driven fields use `DynamicFieldRenderer`, which reads the field type from server config and renders the appropriate input (text box, dropdown, multi-select, date picker, number input, toggle, text area).

### Step completion tracking — live editing vs. reopening an existing entity

Completion status (which drives the step-progress widget at the top of the builder) is computed **reactively**, never on button click. There are two distinct moments:

1. **Live editing** — each step has a `useEffect` watching its data slice; it dispatches `MARK_STEP_COMPLETE` / `MARK_STEP_INCOMPLETE` as required fields fill or empty.
2. **Reopening an existing entity (edit mode)** — when the builder loads an existing entity, the step-progress widget **must immediately reflect the true completion state** of the loaded data, not start all-incomplete. This completion is **derived from the builder config**, evaluated against the loaded entity:

   - A section whose builder-config fields include **≥1 `is_mandatory` field** → validate that section's loaded data against those mandatory fields; mark complete only if satisfied.
   - A section with **no mandatory fields in the config** → treat as **optional → always complete** (it cannot block).
   - Some sections receive **no field info from the builder config at all** (e.g. `tags` ships no `builder_field_config` rows; a module-specific section like `composition` is system-managed). Decide each explicitly: `tags` → always optional/complete; `composition` → at least one step required. Default an unknown config-less section to optional.
   - Terminal sections (`publish`) are excluded from completion accounting entirely (see `ACTION_SECTIONS` in the frontend doc).

   This derivation runs **once**, in a single dispatch that hydrates state and seeds `completedSections` together — wait for BOTH the entity GET and the builder-config to resolve before dispatching, so there is no all-incomplete flash and no double-hydrate. The Learning Path reference implements this as `deriveSectionCompletion(builderDefinition.sections, fetchedPath)` in `LearningPathBuilderLayout.tsx`. See the frontend `builder-widget-platform.md` § "Edit-mode completion derivation" for the canonical code.

### Type-Specific Content

A type dispatcher component examines the selected entity type and renders the appropriate editor. Common steps (basic info, schedule, audience) stay consistent; type-specific steps swap in. Step labels and CTA text are config-driven to avoid scattered hardcoded conditionals.

### Audience section

The audience section ("Who can take this") groups rules by audience-eligible DataObject (Partner Data, Partner User Data, future Customer Data). Each row pairs a DataObjectField with an operator and values. Cascading LOCATION_HIERARCHY fields gate their child editor on parent selection (see [location-hierarchy.md](location-hierarchy.md)).

Save fires on the wizard's `MARK_SECTION_COMPLETE { section: 'audience' }` event. Rules are persisted via `PUT /courses/{courseId}/audience-rules` and validated server-side (`CourseAudienceRuleService.replaceRules`).

The section is optional — a course with no rules is open to all eligible learners (no rules ⇒ true).

### Navigation Guards

`NavigationGuard` checks `isDirty` and whether the user has made actual edits (not just loaded initial data). If both are true and the user navigates away, a confirmation dialog appears.

### Save and Publish Flow

There is no "Save Draft" button. Once required steps are complete, a primary CTA — **"Review & Launch"** in the reference impl — surfaces a terminal review/forecast panel. In the incentive builder this is `ForecastingPanel`, gated solely by the `ai_forecasting` feature flag and applied uniformly to all incentive types (`SALES`, `TRAINING`, `ACTIVITY`, `JOURNEY`); when the flag is off, the flow dispatches `REQUEST_CREATE_CONFIRMATION` and goes directly to a confirm dialog. The user reviews, confirms, and the frontend issues a **single API call** to create or update the entity. In edit mode, opening the builder dispatches `LOAD_{ENTITY}` (e.g., `LOAD_INCENTIVE`) from the API response, which must not set `isDirty`.

### Save model — terminal save is the enablement default

> **For Claude Code / skill authors:** A new enablement module (Learning Path, Certification Program, …) uses **terminal save** — a single `Save{Entity}Request` DTO bound to BOTH the POST (create) and PUT (update) endpoints, sending the full builder state in one call. **Do NOT default to incremental save**, and do NOT special-case "learning paths with embedded media" as incremental. An earlier revision of this file did exactly that and confused implementers; it was wrong. Learning Path (`SaveLearningPathRequest` / `LearningPathSaveButton`) is the reference. The terminal save mechanics, the unified-DTO conventions, and the full decision rule live in [save-flow.md](save-flow.md).

The terminal-save model assumes the entity's full state can be serialised into one JSON payload and persisted in one transaction. This holds for **all enablement builders today** — Course and Learning Path both expose a unified `Save{Entity}Request` on POST + PUT — and for config-only builders (incentives, simple rule sets). Composition-heavy state (ordered steps, milestones, rewards, audience rules) is still terminal: it is sent as nested collections in the one payload and the service replaces/reconciles server-side.

**Incremental save is a narrow, legacy exception**, not a parallel default. It is reserved for flows that genuinely cannot fit one transaction:

- File uploads to object storage (videos, PDFs, images requiring AV scanning)
- AI streaming flows that produce server-side row state during the stream
- Multi-day authoring sessions where browser-refresh resilience of partial work matters

When (and only when) those constraints bite, the incremental mechanics are:

1. The parent entity is created on the first substantive save (e.g., Basics → `POST /{entities}`), establishing a server-side anchor.
2. Child entities (lessons, content assets, AI artifacts) persist as they are added.
3. Abandoned drafts are bounded by a scheduled **sweeper** that cascade-deletes stale `DRAFT` rows + their object-storage blobs (via an outbox table) + cross-context cleanup events.
4. Pre-publish business events are **advisory**: downstream consumers react durably only to the terminal `*_published` event.
5. List views distinguish "empty" drafts with muted styling and an "Untouched · N days" label.
6. Optional per-tenant draft quota prevents pathological accumulation between sweep windows.

The course builder (F-02) was authored as the incremental reference and is **migrating to terminal save** behind the `course_builder_terminal_save` flag (its backend already exposes the unified `SaveCourseRequest`). Treat incremental as the path course is leaving, not the path a new module should join. See `features/course-authoring/spec.md → Draft Lifecycle & Cleanup` for the legacy sweeper / cascade / quota spec.

### Animation Standards

| Component | Purpose |
| --- | --- |
| `FlowTransition` | Directional slide during entry flow |
| `AnimatedCollapse` | Accordion expand/collapse (300 ms height) |
| `FlipTransition` | Right-column panel switching |
| Fade | AI/manual mode toggle |
| Character-by-character render | Streaming AI text |

### File Organization

```text
components/{builder-name}/
  ├── {Builder}Layout.tsx          — Two-column layout shell
  ├── {Builder}Accordion.tsx       — Accordion step container
  ├── steps/
  │   ├── Step1{Name}.tsx
  │   ├── Step2{Name}.tsx
  │   └── StepN{Name}.tsx
  ├── {type-specific}/
  │   └── {Type}Editor.tsx         — Type-specific editor (rendered by the type dispatcher inside a step)
  ├── ai/
  │   └── AICopilotPanel.tsx
  ├── ManualSummaryPanel.tsx
  ├── DynamicFieldRenderer.tsx     — Builder-aware field-type dispatcher (lives with the builder, not under shared/)
  ├── DynamicExtraFields.tsx       — Renders config-driven extra fields per step
  └── {preview-or-forecast}/       — Terminal review/forecast panel; reference impl uses forecasting/ForecastingPanel.tsx, gated by feature flag

contexts/BuilderContext.tsx        — BuilderContext + provider (generic name; reference impl is currently the only builder)
hooks/useBuilderReducer.ts         — Reducer with all actions
hooks/useBuilderConfig.ts          — Config fetching hook (shared)
types/builder-state.types.ts       — TypeScript state types
pages/.../{Entity}BuilderPage.tsx  — Page component (entry point); orchestrates the entry flow
```

Use the builder's domain name (e.g., Incentive, Course, Quiz) in place of `{Builder}`.

## Examples in codebase

- `../tenxengage-frontend/src/components/incentive-builder/` — reference implementation of the full builder pattern
- `../tenxengage-frontend/src/contexts/BuilderContext.tsx` — canonical (generic) BuilderContext
- `../tenxengage-frontend/src/hooks/useBuilderReducer.ts` — canonical reducer with all action types
- `../tenxengage-frontend/src/pages/client-admin/IncentiveBuilderPage.tsx` — entry-flow orchestration with `FLOW_DEPTH`

## Common gotchas

- **Don't split state between react-hook-form and the reducer.** Builders use reducer-only state for all form values. react-hook-form is for standalone form pages, not builders.
- **`LOAD_ENTITY` must normalize data** — the API response shape may differ from the builder state shape. Normalize in the reducer, not in the component.
- **`MARK_STEP_COMPLETE` is driven by auto-validation `useEffect`, not by button click.** The "Continue" button only advances the accordion; completion status is computed reactively.
- **`isDirty` should not be set to true during `LOAD_ENTITY`.** Loading initial data is not a user edit.
- **Type dispatcher must handle null/unknown types gracefully** — render a fallback rather than crashing if the entity type is not recognized.
- **`FlipTransition` and `FlowTransition` are distinct components** — do not substitute one for the other; they have different animation semantics.
- **Wizard step content must carry a `key={step}` prop** — without it, React reuses the same DOM node across step changes and the `animate-mode-fade-in` (or equivalent) class never re-triggers; the transition appears instant.
- **`builder_section_configs.sort_order` uses multiples of 10** (since V41) — leave 5-step gaps between values for future insertions. Current course wizard order: basics=10, dates=20, audience=25, lessons=30, eoc_quiz=50, tags=52, rewards=55, approval_flow=60, publish=80. When adding a new section, pick a free 5-step slot rather than renumbering existing sections.
- **New lifecycle sections (`dates`, `rewards`, `approval_flow`) are seeded per-tenant** via V41 migration using `NOT EXISTS` guards (no applicable unique constraint for nullable-incentive_type rows in `builder_section_configs`).
