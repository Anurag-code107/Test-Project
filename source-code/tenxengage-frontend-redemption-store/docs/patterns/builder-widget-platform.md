# Builder Widget Pattern — Platform Primitives

This file documents frontend patterns for builders backed by the **platform primitives** API (`BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`). The [incentive builder pattern](builder-widget.md) covers the incentive builder only; this file applies to all enablement domains.

**Reference implementation:** the generalized **enablement-builder shell** in `src/components/enablement-builder/` (`EnablementBuilderContext`, `EnablementBuilderAccordion`, `sectionRegistry`, `EnablementSetupHeader`, and the common `steps/`), with the **COURSE module** in `src/components/course-builder/` as its first consumer (`CourseBuilderLayout.tsx`, `CourseBuilderAccordion.tsx`).

---

## Shell + module architecture

The enablement builder is split into a generalized **shell** (`enablement-builder/`) and per-type **modules** (today only `course-builder/`). A new enablement type (Learning Path, Certification Program) is added by **registering a module**, not by forking the builder.

**Shell owns (module-agnostic):**
- `enablement-builder-state.types.ts` — base `EnablementBuilderState` / `EnablementBuilderAction` / `EnablementBuilderSection`. A module's state interface `extends EnablementBuilderState`.
- `EnablementBuilderContext.tsx` — generic `EnablementBuilderProvider({reducer, initialState})` + the widening `useEnablementBuilder()` accessor. One React context; a module's typed accessor (e.g. `useCourseBuilder()`) re-narrows it.
- `EnablementBuilderRuntimeContext.tsx` — shell runtime inputs that aren't reducer state (read-only/archived, AI-copilot lock, AI-drafted-chip wiring, validation errors, tag samples/entity-type). Returns safe defaults when no provider so common sections render standalone.
- The **common sections** (`steps/`): `BasicsSection`, `DatesSection`, `AudienceSection`, `TagsSection`, `RewardsSection`, `ApprovalFlowSection`.
- `sectionRegistry.ts` — `universalSections` (the 6 common keys) + `resolveSection(moduleSections, key)` (module override wins, else universal). `EnablementBuilderAccordion` resolves each server-declared `section_key` through it and renders sections **props-less**.
- `EnablementSetupHeader.tsx` — the generic progress widget, parameterized by the module's action-section list, labels, and copy.

**A module provides (per-type):**
- Its state interface (`extends EnablementBuilderState`) + reducer + initial state, and a thin context wrapper (`CourseBuilderProvider` / `useCourseBuilder`).
- Its module-specific sections (e.g. `CourseLessonsSection`, `CourseEocQuizSection`, `CoursePublishSection`, `CourseCompletionSection`).
- A server-data channel for its sections (e.g. `CourseDataProvider` / `useCourseData()`) so the shell never imports module types.
- A thin accordion wrapper (`CourseBuilderAccordion`) that supplies its `moduleSections` map, an optional `injectAfter` map, `primaryActionKey`/`onPrimaryAction`, and provides the runtime + data contexts.

**`publish` is a universal section KEY but a per-type COMPONENT** — it is intentionally NOT a universal default in the registry; each module registers its own publish component (the data shown differs by type).

The section set/order/visibility/labels remain backend-config-driven (`useBuilderDefinition(builderType)` → `/builder-config/enablement/{builderType}`); only the `section_key → component` mapping and the module's data wiring are FE code.

## Outer Page Layout

The builder page wrapper uses `p-8` outer padding, `gap-5` column gap, and a two-column body:

```tsx
<div className="flex flex-col gap-5 h-full p-8">
  <PageBanner ... />
  {isArchived && <ArchivedBanner />}
  <div className="flex flex-1 overflow-hidden pb-4 gap-5">
    {state.copilot.mode === "ai" && (
      <div className="flex-[2] min-w-[340px]">  {/* left — AI copilot */}
        <AICopilotPanel />
      </div>
    )}
    <div className={cn(
      "relative overflow-hidden min-w-[400px]",
      state.copilot.mode === "ai" ? "flex-[3]" : "flex-1",
    )}>
      {/* right — accordion or quiz editor */}
    </div>
  </div>
</div>
```

Rules:
- Left panel: `flex-[2] min-w-[340px]` — only rendered when `copilot.mode === "ai"`.
- Right panel: `flex-[3]` when AI on, `flex-1` when manual. Always `min-w-[400px]`.
- Body row: `flex flex-1 overflow-hidden pb-4 gap-5` — `overflow-hidden` prevents page-level scroll.

_(Source: `CourseBuilderLayout.tsx:640, 744, 747, 755–758`)_

---

## PageBanner Usage

Every builder renders `PageBanner` as the first child:

```tsx
<PageBanner
  title={pageTitle}
  subtitle="Entity name, description, dimension"
  theme="builder-manual"
  onBack={() => navigate("/entities")}
  actions={
    <div className="flex items-center gap-2">
      {/* status badge, version history, save button, mode toggle */}
      {terminalSaveEnabled && <SaveButton />}
      <ModeTogglePill />
    </div>
  }
/>
```

- `theme="builder-manual"` is the only builder theme currently in `BannerTheme`. Do not use `"builder-ai"` for the course builder — it is used by the incentive builder.
- Terminal-save builders (Learning Path, and Course when `course_builder_terminal_save` / `terminalSaveEnabled` is on) render the save button in `actions`. Only the legacy course incremental fallback saves per section. See "Save Model" below.
- `onBack` always navigates to the entity list page.

_(Source: `CourseBuilderLayout.tsx:641–728`)_

---

## AiDraftBanner

Rendered when `state.aiDraftPendingSave` is `true` — signals that an AI-generated draft is loaded and awaiting explicit save. Place it as the **first child of the right-panel accordion motion.div**, above `CourseSetupHeader`:

```tsx
<AiDraftBanner />
<CourseSetupHeader />
<div className="flex-1 overflow-y-auto"> ... </div>
```

Visual spec:
```tsx
<div className="rounded-lg border border-primary/30 bg-primary/5 px-4 py-3 text-sm">
  <Sparkles className="h-4 w-4 text-primary mt-0.5 shrink-0" />
  <p>Drafted by AI from your materials — review before publishing.</p>
  {/* expandable hints list via ChevronDown toggle */}
</div>
```

Rules:
- Banner is conditionally rendered — returns `null` when `aiDraftPendingSave` is false.
- Hints are expandable (chevron toggle), not always visible.
- Uses `role="alert" aria-live="polite"` for accessibility.

_(Source: `AiDraftBanner.tsx:25–50`, `CourseBuilderLayout.tsx:783`)_

---

## Archived / Read-Only State

When an entity is `ARCHIVED`, two things happen:

1. **Archived alert banner** — rendered between PageBanner and the two-column body:
   ```tsx
   {isArchived && (
     <div
       role="alert" aria-live="polite" data-testid="archived-banner"
       className="flex items-center gap-2 rounded-lg border border-border bg-muted/40 px-4 py-2.5 text-sm text-muted-foreground"
     >
       <Lock className="h-4 w-4" aria-hidden />
       <span>Archived — read-only</span>
     </div>
   )}
   ```

2. **`<fieldset disabled>`** wraps the entire accordion, graying out all inputs:
   ```tsx
   <fieldset
     disabled={isArchived}
     className="flex flex-col gap-2 min-w-0 disabled:opacity-70"
     aria-label={isArchived ? "Archived course (read-only)" : undefined}
   >
     <CourseBuilderAccordion ... />
   </fieldset>
   ```

The `disabled:opacity-70` Tailwind class on `<fieldset>` is sufficient to visually communicate read-only state across all child inputs. No additional per-field disabled prop is needed.

_(Source: `CourseBuilderLayout.tsx:636, 730–740, 787–789`)_

---

## Right Panel Layout Structure

Builders using a split panel (AI copilot left, accordion right) must use this scroll-containment structure for the right panel:

```tsx
<div className={
  state.copilot.mode === "ai"
    ? "flex-[3] flex flex-col overflow-hidden min-w-[400px]"
    : "flex-1 flex flex-col overflow-hidden min-w-[400px]"
}>
  <div className="flex flex-col h-full gap-4">
    <DomainSetupHeader />          {/* shrink-0 — stays pinned */}
    <div className="flex-1 overflow-y-auto">
      <fieldset
        disabled={isArchived}
        className="flex flex-col gap-2 min-w-0 disabled:opacity-70"
        aria-label={isArchived ? "Archived ... (read-only)" : undefined}
      >
        <DomainBuilderAccordion ... />
      </fieldset>
    </div>
  </div>
</div>
```

Rules:
- **`overflow-hidden`** on the outer panel — scroll is delegated to the inner `flex-1 overflow-y-auto` wrapper.
- **`min-w-[400px]`** on **both** AI and manual mode variants — the manual variant is commonly written without it and then breaks on narrow viewports.
- **`flex flex-col h-full gap-4`** on the inner wrapper — pins `DomainSetupHeader` at the top and lets the accordion fill the rest.
- **`shrink-0`** on the `DomainSetupHeader` root element — prevents the header collapsing when the accordion overflows.
- **`flex-1 overflow-y-auto`** on the accordion wrapper — scroll stays inside the panel, not the page.

---

## ACTION_SECTIONS Pattern

Not all builder sections count toward step progress. Terminal / read-only sections (e.g. `preview`, `publish`) are excluded. Define a **module-scope** constant listing only the *action* sections:

```tsx
const ACTION_SECTIONS = [
  "basics",
  "lessons",
  "metadata",
  "audience",
  "eoc_quiz",
] as const;

type ActionSection = (typeof ACTION_SECTIONS)[number];

const SECTION_SHORT_LABELS: Record<ActionSection, string> = {
  basics: "Basics",
  lessons: "Lessons",
  metadata: "Metadata",
  audience: "Audience",
  eoc_quiz: "Quiz",
};
```

Why `as const` + derived type: `ActionSection` cannot go out of sync with `ACTION_SECTIONS` — any new key added to the array is immediately required in the labels record.

Why module-scope: these are pure constants. Defining them inside a component allocates new objects on every render.

Adapt the array and labels for your domain. Keep terminal sections (`preview`, `publish`) out of `ACTION_SECTIONS`.

---

## SetupHeader Component

`DomainSetupHeader` is a **local, non-exported** function component defined in the same file as the builder layout. It sits above the accordion and carries `shrink-0` on its root element.

```tsx
function CourseSetupHeader() {
  const { state } = useCourseBuilder();
  const { data: builderDefinition, isLoading } = useBuilderDefinition("COURSE");

  if (isLoading) {
    return (
      <div
        className="h-14 w-full rounded-xl bg-muted animate-pulse shrink-0"
        role="status"
        aria-busy="true"
        aria-label="Loading course setup"
      />
    );
  }

  // Filter ACTION_SECTIONS (not the API response) to guarantee display order.
  const visibleActionSections = ACTION_SECTIONS.filter((key) =>
    (builderDefinition?.sections ?? []).some((s) => s.key === key && s.isVisible),
  );

  const totalActionSections = visibleActionSections.length;
  const rawIndex = ACTION_SECTIONS.indexOf(state.activeSection as ActionSection);
  // Clamp: terminal sections (preview/publish) are not in ACTION_SECTIONS.
  // Show "Step N of N" so the header reads as "all done, in terminal phase".
  const currentIndex = rawIndex === -1 ? Math.max(0, totalActionSections - 1) : rawIndex;

  const completedActionCount = state.completedSections.filter((s) =>
    (ACTION_SECTIONS as readonly string[]).includes(s),
  ).length;

  const completionPercent =
    totalActionSections > 0
      ? Math.round((completedActionCount / totalActionSections) * 100)
      : 0;

  return (
    <div className="rounded-xl border border-border bg-background p-4 shrink-0">
      {/* icon row: Settings2 + heading */}
      <div className="flex items-center gap-2 mb-1">
        <Settings2 className="h-5 w-5 text-muted-foreground" />
        <h3 className="text-2xl font-semibold text-foreground">Course Setup</h3>
      </div>
      <p className="text-base text-muted-foreground mb-4">
        Complete each section to build your course
      </p>

      {/* step counter + percent */}
      <div className="flex items-center justify-between text-xs mb-2">
        <span className="font-medium text-foreground">
          Step {currentIndex + 1} of {totalActionSections}
        </span>
        <span className="text-muted-foreground tabular-nums">{completionPercent}%</span>
      </div>

      {/* segmented progress bar */}
      <div className="flex gap-1.5 mb-2">
        {visibleActionSections.map((key) => (
          <div
            key={key}
            className={cn(
              "h-1.5 flex-1 rounded-full transition-[background-color]",
              state.completedSections.includes(key as CourseBuilderSection)
                ? "bg-primary"
                : state.expandedSections.includes(key as CourseBuilderSection)
                  ? "bg-primary/35"
                  : "bg-border",
            )}
          />
        ))}
      </div>

      {/* label row */}
      <div className="flex gap-1.5">
        {visibleActionSections.map((key) => (
          <span key={key} className="flex-1 text-xs text-muted-foreground text-center">
            {SECTION_SHORT_LABELS[key]}
          </span>
        ))}
      </div>
    </div>
  );
}
```

**Progress bar segment states:**
- `bg-primary` — section key is in `state.completedSections`
- `bg-primary/35` — section key is in `state.expandedSections` (active, not yet complete)
- `bg-border` — untouched

**Loading skeleton**: `h-14 w-full rounded-xl bg-muted animate-pulse shrink-0` with `role="status" aria-busy="true" aria-label="Loading ... setup"`. Required whenever `isLoading` is true to prevent layout shift.

---

## TanStack Query Deduplication

Calling `useBuilderDefinition("COURSE")` inside `DomainSetupHeader` AND inside the accordion (which also calls it internally) does **not** fire two network requests. TanStack Query deduplicates identical query keys within the same render tree — the second call reads from the in-flight or cached result for free. No prop-drilling or context sharing is needed.

---

## Mode Toggle Pill

The mode toggle uses native `<button>` elements (not shadcn `<Button>`) to match the incentive builder exactly. The `ai_copilot` feature flag gates the AI Mode button via `useFeatures`. See [permissions-and-feature-flags.md](permissions-and-feature-flags.md) for the disabled-button Tooltip pattern.

Visual spec:

| Property | Value |
|---|---|
| Container | `flex items-center gap-1 p-1 rounded-xl border border-primary/20 bg-primary/5` |
| Button sizing | `px-4 py-2 rounded-lg text-sm font-medium` |
| Active state | `bg-primary text-primary-foreground shadow-md shadow-primary/30` |
| Inactive hover | `hover:bg-primary/10 hover:text-foreground` |
| Disabled | `text-muted-foreground/50 cursor-not-allowed` |
| AI Mode icon | `Bot h-4 w-4` |
| Manual icon | `ClipboardList h-4 w-4` |
| Button order | AI Mode first, Manual second |

Both buttons must carry `aria-label` and `aria-pressed`.

---

## Animation — Framer Motion (not FlipTransition)

Enablement-domain builders use **Framer Motion** `AnimatePresence` + `motion.div` for right-panel transitions. **Do not use `FlipTransition`** — that is the incentive builder's animation component. Mixing them will break animation semantics.

```tsx
<AnimatePresence initial={false}>
  {state.activeQuizEditorPanel ? (
    <motion.div
      key="quiz-editor"
      className="absolute inset-0"
      initial={{ x: "100%" }}
      animate={{ x: 0 }}
      exit={{ x: "100%" }}
      transition={{ duration: 0.32, ease: [0.32, 0, 0.18, 1] }}
    >
      <FullPanelQuizEditor ... />
    </motion.div>
  ) : (
    <motion.div
      key="accordion"
      className="absolute inset-0 flex flex-col gap-4"
      initial={{ x: "-100%" }}
      animate={{ x: 0 }}
      exit={{ x: "-100%" }}
      transition={{ duration: 0.32, ease: [0.32, 0, 0.18, 1] }}
    >
      ...
    </motion.div>
  )}
</AnimatePresence>
```

Both panels use `absolute inset-0` so enter + exit animate simultaneously without an empty-space flash between them. The easing curve `[0.32, 0, 0.18, 1]` is the platform standard for panel transitions.

_(Source: `CourseBuilderLayout.tsx:759–804`)_

---

## Save Model — terminal save is the enablement default

**Enablement builders use terminal save.** A single `Save{Entity}Request` carries the full builder state and is sent on **both** create (POST) and update (PUT) in one call — there is no per-section PATCH and no "Save Draft" button. This is the platform standard for every enablement module; the [blueprint save-flow.md](../../tenxengage-blueprint/docs/patterns/save-flow.md) is the canonical cross-repo reference.

**Learning Path is terminal-only** and the reference implementation. `LearningPathSaveButton.tsx` issues a single POST (create) or PUT (update) with the full `SaveLearningPathRequest` — no flag, no incremental fallback:

```tsx
// LearningPathSaveButton — both paths send the full payload in one call:
// create → POST /learning-paths        (version omitted)
// update → PUT  /learning-paths/{id}   (version = optimistic-lock counter)
```

**Course is migrating to terminal save** behind the `course_builder_terminal_save` flag (`terminalSaveEnabled`). When on, a `<SaveButton />` appears in `PageBanner.actions` and a single PUT fires; when off, the legacy incremental per-section save handlers run. The course backend already exposes the unified `SaveCourseRequest` on POST + PUT. **Do not model a new enablement type on the incremental fallback** — follow Learning Path.

```tsx
// Course (legacy/in-migration) — flag-gated terminal button in PageBanner actions:
{terminalSaveEnabled && <SaveButton />}
```

- The discard dialog (NavigationGuard / `AlertDialog`) is ALWAYS shown on dirty navigation — regardless of save model.

_(Source: `LearningPathSaveButton.tsx`, `CourseBuilderLayout.tsx:671`)_

---

## Edit-mode completion derivation

When the builder loads an **existing** entity, the step-progress widget (`EnablementSetupHeader`) must immediately reflect which sections are already complete — it must NOT start all-incomplete and "fill in" as the user clicks through. Completion is **derived from the builder config** evaluated against the loaded entity, in the same single dispatch that hydrates state.

Rules (Learning Path reference — `deriveSectionCompletion` in `LearningPathBuilderLayout.tsx`):

- **Wait for BOTH** the entity GET (`useLearningPath`) and the builder definition (`useBuilderDefinition(builderType)`) before dispatching. Seed `completedSections` inside the single `LOAD_{ENTITY}` dispatch — no double-hydrate, no all-incomplete flash. The builder-definition call is a cache hit (already fetched by `EnablementSetupHeader` + accordion; TanStack Query dedupes).
- **Section with ≥1 `isMandatory` field** → validate the section's loaded data against those mandatory fields; mark complete only if satisfied.
- **Section with no `isMandatory` fields** → optional → **always complete** (cannot block).
- **Config-less sections** (no `builder_field_config` rows reach the FE) → decide explicitly:
  - `tags` → always optional/complete.
  - module-specific system sections (e.g. `composition`) → module-defined rule (LP: ≥1 step required).
  - unknown config-less section → default to optional/complete (conservative).
- **Terminal sections** (`publish`) → excluded from completion accounting (also kept out of `ACTION_SECTIONS`).

```tsx
function deriveSectionCompletion(sections, fetchedPath) {
  const completed = [];
  for (const section of sections) {
    if (!section.isVisible || TERMINAL_SECTIONS.has(section.key)) continue;
    const key = section.key;
    if (key === "composition") { if ((fetchedPath.steps ?? []).length > 0) completed.push(key); continue; }
    if (key === "tags") { completed.push(key); continue; }
    if (!section.fields.some((f) => f.isMandatory)) { completed.push(key); continue; } // optional
    if (isMandatorySectionSatisfied(key, fetchedPath)) completed.push(key);            // validate
  }
  return completed;
}
```

A per-section validator (`isMandatorySectionSatisfied`) checks the actual loaded values (e.g. `basics` → non-blank name; `dates` → `effectiveAt` present; `rewards` → every row amount > 0, with zero rows treated as complete). Add a case whenever a new module/section introduces mandatory fields; the `default` returns `true` (conservative — don't block on an unmodelled section).

_(Source: `LearningPathBuilderLayout.tsx:106–185, 211–247`)_

---

## Locked sections (system-managed)

Section config carries `isLocked` + `infoMessage`. A locked section is **system-managed** — its content is authored via a dedicated panel/widget/terminal action, not free-form fields in the accordion (`basics`, `tags`, `publish`, and module system sections like `composition`). The builder must surface the lock affordance + `infoMessage` **inside** the locked section so authors understand why it has no editable fields (rather than an empty or "Coming soon" panel).

> **Known gap (2026-06-03):** `EnablementBuilderAccordion` renders only `displayName`/`subtitle` — it does **not yet** read `section.isLocked` / `section.infoMessage`. Wiring the lock indicator + `infoMessage` into the shell accordion's section header/body is the pending half of this contract. The data is typed (`types/builder-config.types.ts`) and already consumed by the admin editor (`BuilderConfigSection.tsx`); the author-facing builder is not wired yet. See [blueprint builder-config.md](../../tenxengage-blueprint/docs/patterns/builder-config.md) § "Locked (system-managed) sections".

---

## Full-Panel Embedded Editor

When `state.activeQuizEditorPanel` is non-null, the right panel is taken over by a `FullPanelQuizEditor`. This is the canonical pattern for any sub-editor that requires full right-panel height.

Layout principles:
- **Inline quiz**: `flex flex-col h-full rounded-xl border border-border bg-card shadow-sm overflow-hidden`. The sub-panel (`InlineQuizQuestionsPanel`) owns its own header row, scrollable content, and footer.
- **EOC quiz** (stacked): pinned header (`shrink-0`) → scrollable settings strip + questions panel (`flex-1 overflow-y-auto`) → pinned save footer (`shrink-0`). Settings are inside the scroll so the question list gets full remaining height.

The `FullPanelQuizEditor` slides in from the right via `motion.div` with `initial={{ x: "100%" }}` (see Animation section). The accordion slides out to the left simultaneously.

Generalize: any embedded sub-editor that takes over the right panel follows the same `absolute inset-0 flex flex-col h-full` pattern with `AnimatePresence` controlling enter/exit.

_(Source: `CourseBuilderLayout.tsx:54–235`)_

---

## Error / 404 Layout

When an entity fetch fails in edit mode, render a centered error UI with an `AlertCircle` icon:

```tsx
if (mode === "edit" && courseError) {
  const is404 = (courseErrorObj as AxiosError)?.response?.status === 404;
  return (
    <div className="flex flex-col items-center justify-center h-full gap-3">
      <AlertCircle className="h-8 w-8 text-destructive" />
      <p className="text-base font-medium text-foreground">
        {is404 ? "Entity not found" : "You don't have permission to view this entity"}
      </p>
      <Button variant="outline" size="sm" onClick={() => navigate("/entities")}>
        Back to entities
      </Button>
    </div>
  );
}
```

This pattern also applies to loading skeletons — while `courseLoading` is true, render an animated pulse layout that mirrors the two-column structure:

```tsx
<div className="flex flex-col gap-5 h-full p-8">
  <div className="h-16 rounded-xl bg-muted animate-pulse" />
  <div className="flex flex-1 gap-5">
    <div className="flex-[2] min-w-[340px] rounded-xl bg-muted/50 animate-pulse" />
    <div className="flex-[3] min-w-[400px] space-y-3"> ... </div>
  </div>
</div>
```

_(Source: `CourseBuilderLayout.tsx:601–634`)_

---

## Discard Dialog

Always show a discard confirmation before navigating away from a dirty builder — regardless of whether terminal-save or incremental-save is active. Use shadcn `AlertDialog` (not a `Dialog`):

```tsx
<AlertDialog open={showDiscard} onOpenChange={setShowDiscard}>
  <AlertDialogContent>
    <AlertDialogHeader>
      <AlertDialogTitle>Discard changes?</AlertDialogTitle>
      <AlertDialogDescription>
        Your unsaved changes will be lost.
      </AlertDialogDescription>
    </AlertDialogHeader>
    <AlertDialogFooter>
      <AlertDialogCancel>Keep editing</AlertDialogCancel>
      <AlertDialogAction onClick={handleDiscard}>Discard</AlertDialogAction>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```

The `isDirty` flag in builder state drives the guard. Match the incentive builder's behavior: open the dialog whenever `isDirty` is true and the user attempts to navigate away (back button, sidebar link, browser back).

---

## Pitfalls

**Filter `ACTION_SECTIONS` — not the API response.** The API does not guarantee sort order. Iterating `ACTION_SECTIONS` and checking presence in the response ensures the bar segments always appear in the correct logical order regardless of what the backend returns.

**`min-w-[400px]` on both mode variants.** The manual-mode right panel commonly omits this and breaks on narrow viewports. Set it on both `"ai"` and non-AI class strings.

**Do not export `DomainSetupHeader`.** It is a layout-internal helper. If reuse is needed across multiple layouts in the future, extract it at that point — not preemptively.

**Zero-section edge case.** If `totalActionSections === 0` (all action sections are hidden on the backend), the completion-percent guard prevents divide-by-zero, but the step counter shows "Step 1 of 0" — a nonsensical label. In practice this only occurs under catastrophic server misconfiguration, but consider an early-return or empty-state render when `totalActionSections === 0`.

**Terminal section clamping.** When `state.activeSection` is `"preview"` or `"publish"`, `ACTION_SECTIONS.indexOf` returns -1. The `Math.max(0, totalActionSections - 1)` clamp converts this to "Step N of N", signalling all action work is done.

**Hydrate every new buffered state field in `LOAD_LEARNING_PATH` (and equivalent loaders).** When a new story adds a new buffered sub-collection to the module state (e.g. `compositionMilestones`, `compositionRewards`), the `LOAD_*` dispatch in the layout's `useEffect` must include that field populated from the GET response. Omitting it leaves the field at its initial empty value, so the first PUT after opening an existing entity silently wipes all server data for that sub-collection. Pattern: for each `XxxDraft[]` in state, there must be a corresponding `serverXxxToXxxDrafts()` helper called in the loader.

**Sub-collections keyed by step `sortOrder` must be reconciled on step mutation.** Milestones (and any future step-anchored data) store `placementAfterSortOrder` pointing to a step's `sortOrder`. When `REMOVE_STEP` removes a step, drop all sub-items whose placement targets the removed sort order. When `REORDER_STEPS` renumbers sort orders, remap sub-item placements via `_draftId` identity before sort orders change: build `oldSortOrder → _draftId` first, apply new sort orders, then build `_draftId → newSortOrder` and remap. Failing to reconcile leaves invisible orphaned items in state that produce 422 errors on the next save.
