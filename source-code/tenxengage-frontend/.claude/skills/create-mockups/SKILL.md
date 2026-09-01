---
name: "create-mockups"
description: "Use when creating interactive TSX mockups for user stories in a feature. Reads story files from the blueprint, groups stories by screen/flow, generates per-screen TSX mockups, and writes mockup_file back to story frontmatter. Run from the frontend repo after /create-stories has been run in the blueprint."
argument-hint: "{feature-slug} [US-NN,US-NN,...] — e.g., 'enablement-courses' or 'enablement-courses US-01,US-03'"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Phase 1 — Determine Feature and Story Filter

**Parse `$ARGUMENTS`**:
- `{feature-slug}` provided → use it directly
- `{feature-slug} US-NN[,US-NN,...]` provided → use feature-slug and treat the story list as a filter (only mockup those stories)
- _(empty)_ → auto-detect from current branch (`git branch --show-current`; extract `<slug>` from `features/<slug>`)

If detection fails, error: "No feature selected. Pass a slug (e.g. `/create-mockups enablement-courses`) or run from a `features/<slug>` branch."

---

## Phase 2 — Load Spec, Stories, and Build the Work List

### Blueprint branch guard (before reading any files)

a. Check if `../tenxengage-blueprint/features/{feature-id}/` exists — if YES, blueprint is already on the right branch; skip to (e).
b. `git -C ../tenxengage-blueprint branch --show-current` → note `{current-branch}`
c. Check for uncommitted changes: `git -C ../tenxengage-blueprint status --porcelain`
   - If changes exist, ask the user:
     ```
     Blueprint repo has uncommitted changes on branch {current-branch}.
     A) Commit them to {current-branch} (supply a commit message)
     B) Stash them
     C) Abort — I'll switch branches manually
     ```
     - On A: commit with the user-supplied message, then proceed to (d)
     - On B: `git -C ../tenxengage-blueprint stash`, then proceed to (d)
     - On C: **abort**
d. Checkout the feature branch:
   - Try local: `git -C ../tenxengage-blueprint checkout features/{feature-id}`
     - If successful: check if behind remote: `git -C ../tenxengage-blueprint log HEAD..origin/features/{feature-id} --oneline 2>/dev/null`
       - If behind: ask user: "Blueprint branch is behind origin by N commits. A) Pull with rebase  B) Continue with local version"
         - On A: `git -C ../tenxengage-blueprint pull --rebase origin features/{feature-id}`, then proceed to (e)
         - On B: branch already checked out — proceed to (e)
       - If not behind: proceed to (e)
     - If local branch not found: `git -C ../tenxengage-blueprint fetch origin features/{feature-id}:features/{feature-id} && git -C ../tenxengage-blueprint checkout features/{feature-id}`, then proceed to (e)
     - If remote also not found: **abort** — "Blueprint feature branch `features/{feature-id}` not found locally or on origin. Run `/create-stories {feature-id}` from the blueprint repo first."
e. Blueprint repo is now on `features/{feature-id}`. Continue reading story files.

### 2a. Read source files

1. Read `../tenxengage-blueprint/features/{feature-id}/spec.md`
2. Read `../tenxengage-blueprint/features/{feature-id}/stories.md` — extract the full story table
3. Read each story file `../tenxengage-blueprint/features/{feature-id}/stories/US-{NN}-{slug}.md` — check the `mockup_file` frontmatter field
4. Read `../tenxengage-blueprint/features/{feature-id}/spec.md` — frontend sections only (TypeScript Types and Frontend Specification)

> The contract is **optional** — mockups can be created before contracts are generated. If no contract is found in `contracts/endpoints/`, derive entity shapes and API structures directly from the spec's Data Model and API Endpoints sections.

### 2b. Determine eligible stories

Walk every row in the stories table. For each story:

1. Skip if `layers` = `["BE"]` (BE-only — no UI to mockup)
2. **Read the story file's frontmatter `mockup_file` field** — skip if it is NOT `null` (i.e., skip stories where `mockup_file` is already set to a file path or is `N/A`). Log "skipped — mockup_file already set: {path}"
3. If a story filter was given in `$ARGUMENTS`, skip any story not in that filter list
4. Add remaining stories to the candidate list: `{ story_id, slug, title, layers }`

### 2c. Propose screen grouping — WAIT FOR CONFIRMATION

Analyze the candidate stories and the spec to determine which stories share a screen or flow. Stories that render on the same page/view should be grouped into one mockup file.

Present the proposed grouping in this format and **stop — do not generate any files until the developer confirms**:

```
Proposed mockup grouping for: {feature-id}

| Mockup file | Stories covered | Screen description |
|-------------|-----------------|-------------------|
| src/mockups/{feature-id}/CoursesListPage.tsx | US-01, US-02 | Courses list — empty + populated states |
| src/mockups/{feature-id}/CourseDetailPage.tsx | US-05, US-06 | Course detail + enrollment flow |
| ...

Skipped ({M} stories):
  US-XX  Already has mockup_file: src/mockups/...
  US-YY  BE-only — no UI

Does this grouping look right? Reply:
  - "yes" or "proceed" to generate all mockups as proposed
  - "skip {ScreenName}" to drop a screen from this pass
  - Suggest a different grouping (e.g., "put US-05 with US-03 instead")
```

Wait for the developer's reply. Apply any adjustments to the grouping before proceeding. Produce the final confirmed work list:

```
{ screen_name, file_path, stories: [{ story_id, slug, title }], dev_route }
```

where:
- `screen_name` — PascalCase screen name (e.g. `CoursesListPage`)
- `file_path` — `src/mockups/{feature-id}/{ScreenName}.tsx`
- `dev_route` — `/mockup/{feature-id}/{screen-slug}` (kebab-case of screen name, e.g. `/mockup/enablement-courses/courses-list-page`)

---

## Phase 2.5 — Build BUILDER_CONFIG_SECTIONS shared mock data (builder features only)

Trigger: this phase runs only when at least one candidate screen falls into the "Builder — wizard body", "Platform Settings — Builder Config tab", or "Builder Config — standalone page" Mirror rows.

The output of this phase is a single shared mock data file `src/mockups/{feature-id}/_mockData.builderConfig.ts` exporting:

```ts
export const BUILDER_CONFIG_SECTIONS = [
  { key: 'basic_information', label: 'Basic Information', kind: 'system', locked: true, fields: [...] },
  { key: 'course_timeline',   label: 'Course Timeline',   kind: 'system', locked: true, fields: [...] },
  // ...
] as const
```

Both the admin-config screen mockup and the wizard body mockup MUST import `BUILDER_CONFIG_SECTIONS` from this file. Generating divergent section lists per screen is a defect.

### How to populate BUILDER_CONFIG_SECTIONS

**Path A — Spec frontmatter has `domain:` and `builder_type:` set (slot-filling builder):**
1. Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md` and, if present, `../tenxengage-blueprint/docs/patterns/domains/{domain}/{builder-type}.md`.
2. Pull `section_keys` (ordered) and per-section `locked` flags from the registry. The registry is the source of truth — do NOT propose, do NOT ask the user to confirm what the registry already declares.
3. Materialize the section list into `_mockData.builderConfig.ts` directly. Use representative field stubs per section (3–5 fields each) consistent with the registry's slot fillers. Add 1–2 representative `kind: 'custom'` placeholder sections after the registry-driven system sections (illustrative — to demonstrate the unlocked, tenant-configurable shape).
4. Announce in the run log: "Builder sections populated from domain registry: `{domain}/{builder_type}` → {N} sections."

**Path B — `domain:` is not set (one-off builder, no slot-filling pattern):**
1. Read the spec's Data Model + frontend sections + each builder story.
2. Draft a proposed section list with fields per section. Tag each section as `[system]` (platform-fixed) or `[custom]` (tenant-configurable). Example for a course builder without domain set:
   ```
   Proposed Builder Config sections for: enablement-courses

   1. Basic Information       [system]   → Title (TEXT_BOX, mandatory), Description (TEXT_AREA), Category (DROPDOWN, ACTIVITY_CATEGORIES)
   2. Course Timeline         [system]   → Start Date (DATE_PICKER, mandatory), End Date (DATE_PICKER), Duration (NUMBER_INPUT)
   3. Participant Eligibility [system]   → Roles (MULTI_SELECT, CLIENT_ROLES), Locations (MULTI_SELECT, LOCATION_HIERARCHY)
   4. Training Courses        [custom]   → Linked Courses (MULTI_SELECT, DATA_OBJECT_FIELD)
   5. Approval Criteria       [system]   → Approvers (MULTI_SELECT, CLIENT_ROLES), Approval Mode (DROPDOWN, STATIC)

   Reply:
     - "yes" / "proceed" to use this section list
     - "drop {N}" to remove a section
     - "add {SectionName}" / "rename {N} to ..." / suggest changes
   ```
3. Wait for confirmation. Apply changes. Then materialize the confirmed list into `_mockData.builderConfig.ts`.

### Hard rule for Phase 4 generation

If a screen is "Builder Config tab", "Builder Config standalone page", or "Builder wizard body", the generated TSX MUST:
- Import `BUILDER_CONFIG_SECTIONS` from `./_mockData.builderConfig`.
- Render the section list from this constant — never inline.
- Use the `locked` flag to render locked sections distinctly (grayed / lock icon) from customizable sections.

---

## Phase 3 — Load Style Context (once, before the generation loop)

Read these files once before generating any mockup:

| File | Purpose |
|------|---------|
| `CLAUDE.md` | Stack conventions and rules |
| `src/index.css` | HSL CSS custom properties (color tokens) |
| `tailwind.config.ts` | Custom keyframes, animations, spacing |
| `src/lib/utils.ts` | `cn()` helper |
| `src/config/currencies.ts` | Currency display _(skip if feature doesn't involve currency)_ |
| `PROJECT-CONTEXT.md` | Frontend platform rules |

**Glob `src/components/ui/*.tsx`** — list available shadcn/ui components before generating.

**UI Pattern files** — read only the ones relevant to this feature's UI types (check once against the full story set):

| Condition | Read |
|-----------|------|
| Any story has a builder, wizard, or multi-step creation flow | `../tenxengage-blueprint/docs/patterns/builder-wizard.md` + `builder-config.md` |
| Any story has an AI copilot/assistant panel | `../tenxengage-blueprint/docs/patterns/ai-copilot.md` |
| Feature introduces new permission rules | `../tenxengage-blueprint/docs/patterns/permissions-and-feature-flags.md` |
| Feature has new tenant-scoped entities | `../tenxengage-blueprint/docs/patterns/tenant-isolation.md` |

**Screen Pattern Mirror** — before generating any screen, identify its type and read the matching production file. **Fidelity rule.** The mockup's outer container, spacing scale, hover/transition classes, and animation classes MUST be copied verbatim from the referenced file. Only the inner text content, icon choice, mock data values, and entity-specific labels may differ. If you are tempted to write a class string that does not appear in the reference file, stop and re-read the reference. This rule applies to every Mirror reference, every conditional reference component read in Phase 4b, and every section adapted via the `// Mirrors:` declaration in Phase 4c.

| Screen type | Production file to read | What to extract |
|-------------|------------------------|-----------------|
| List / table page | `src/pages/client-admin/ManageIncentivesPage.tsx` | Page wrapper class, PageBanner theme, tab pill classes, search input classes, grid/card structure |
| Detail page | `src/pages/tenx-admin/ClientDetailPage.tsx` | Back button pattern, Card-based section structure, CardHeader/CardContent usage |
| Settings / config page | `src/pages/client-admin/TenXSettingsPage.tsx` | PageBanner theme, Tabs pattern, `border-dashed` nested Card |
| Form page | `src/pages/client-admin/MyProfilePage.tsx` | Card form shell, Label + Input structure, CardHeader/CardContent |
| Builder — entry menu (choice cards) | `src/components/incentive-builder/EntryMenu.tsx` + `BuilderFlowBackground.tsx` | Card grid layout, hover lift/glow animations, hero heading + pill label |
| Builder — type selector | `src/components/incentive-builder/TypeSelector.tsx` | Selectable type cards, selected-state styling, back/next CTAs |
| Builder — template upload/picker | `src/components/incentive-builder/TemplateSelector.tsx` | Template card list, upload area styling, preview pattern |
| Builder — existing-item picker | `src/components/incentive-builder/ExistingIncentiveSelector.tsx` | Search input, card list with metadata badges, selected highlight |
| Builder — wizard body | `src/components/incentive-builder/BuilderLayout.tsx` + `BuilderAccordion.tsx` + `PageBanner.tsx` + `src/hooks/useBuilderConfig.ts` + `src/components/incentive-builder/DynamicFieldRenderer.tsx` _(read all five)_ | 40/60 layout, banner theme, accordion step structure, config-driven field rendering via DynamicFieldRenderer |
| Platform Settings — Builder Config tab | `src/pages/client-admin/PlatformSettingsPage.tsx` + `src/components/settings/BuilderConfigTab.tsx` + `BuilderConfigSection.tsx` + `BuilderFieldEditor.tsx` | PageBanner, Tabs pattern, section list with collapsible field editors, system-vs-custom field treatment, sort order handle |
| Builder Config — standalone page | `src/pages/client-admin/BuilderConfigPage.tsx` | Same section/field editor scaffold without the surrounding PlatformSettings tabs |
| Dashboard / home | `src/pages/DashboardPage.tsx` | `space-y-6` wrapper, plain h2+p header (no PageBanner), stat card grid |

**No-match rule.** If a screen doesn't match any row in the Screen Pattern Mirror table, generate it freely (no production analog exists), AND emit a warning in Phase 5's summary listing the screen with a one-line description of what it does. This surfaces gaps in the Mirror table over time — when the same shape appears repeatedly in warnings, add a new row pointing to whichever screen first established the pattern in production.

---

## Phase 4 — Per-Screen Generation Loop

For each confirmed screen in the work list (in dependency order — screens whose stories have fewer deps first):

### 4a. Read each covered story file

For every story in the screen's `stories` list, read `../tenxengage-blueprint/features/{feature-id}/stories/US-{NN}-{slug}.md`. Extract:
- `## Description` — actor, trigger, steps, expected outcome, negative paths
- `## FE tasks [FE]` — all FE task blocks (UI components, views, interactions)
- `## E2E test [FE]` — scenario flows (use to identify all interactive paths to demonstrate)
- `## Spec references` — sections of spec.md this story depends on (read them if not already read)

**Read domain registry for builder structure** (only if the feature is slot-filling — spec.md frontmatter has `domain:` non-null AND `builder_type:` non-null):
- Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`.
- Find the table that maps `builder_type` to `section_keys` (ordered) and per-section lock flags.
- If `../tenxengage-blueprint/docs/patterns/domains/{domain}/{builder-type}.md` exists, prefer its section keys over the domain default.
- Use these `section_keys` to determine mockup section ORDER. Use the lock flags to render locked sections distinctly (e.g., grayed/with a lock icon) from customizable sections.
- **Without this read, mockups silently drift from structural primitives — do not skip.**

### 4b. Find reference components (conditional)

Only read reference components when the screen involves:
- A builder or multi-step wizard
- A complex table with search, filters, and bulk actions
- A pattern not yet seen in any prior screen in this batch

**Skip for**: simple list pages, basic forms, standard card grids, detail panels — their reference file was already read in Phase 3 (Screen Pattern Mirror table).

**For builder/wizard screens specifically**, always read these three source files before writing a single line of JSX:
- `src/components/incentive-builder/BuilderLayout.tsx` — 40/60 flex layout, setup header card structure, and mode toggle actions block
- `src/components/PageBanner.tsx` — banner theme system, SVG artwork, and action slot
- `src/components/incentive-builder/BuilderAccordion.tsx` — accordion step structure, badge styling, AnimatedCollapse

**Builder screen layout rules (non-negotiable — apply to every builder/wizard mockup):**
1. `PageBanner` theme = `"builder-ai"` when builder mode is AI, `"builder-manual"` when it is Manual. Never `"default"`. The theme must track the live mode state, not the page's create/edit intent.
2. Body: `flex flex-1 overflow-hidden pb-4 gap-5` — left `flex-[2] min-w-[340px]` + right `flex-[3] min-w-[400px]`
3. Progress bar lives inside the right panel setup header card. Never a separate page-level strip between the banner and the body.
4. Left panel is never a step-list sidebar. It is always a contextual panel: AI copilot, outline summary, or course preview.
5. Mode toggle (AI / Manual) goes in the PageBanner `actions` prop — use the pill-toggle pattern in `BuilderLayout.tsx` (the `actions={<div className="flex items-center gap-1 p-1 rounded-xl ...">` block inside the `<PageBanner>` call).

### 4c. Generate the mockup file

**Output path:**
```
src/mockups/{feature-id}/{ScreenName}.tsx
```

**File structure:**

```tsx
// Covers: US-01, US-02
// Mirrors: src/components/incentive-builder/EntryMenu.tsx, src/components/incentive-builder/BuilderFlowBackground.tsx
// MOCKUP: {feature-id} — {ScreenName} ({comma-separated story titles})
// (Builder-related screens only — wizard body, Builder Config tab, Builder Config standalone page:)
// BuilderConfig sections (source of truth — both admin tab + wizard consume this):
//   1. Basic Information      [system, locked]
//   2. Course Timeline        [system, locked]
//   3. Participant Eligibility [system, locked]
//   4. Training Courses       [custom]
//   5. Approval Criteria      [system, locked]
// Source: src/mockups/{feature-id}/_mockData.builderConfig.ts
//
// Interactive design mockup — not production code.
// Dev route: /mockup/{feature-id}/{screen-slug}
// To view: npm run dev → navigate to the route above.

import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
// shadcn/ui imports from @/components/ui/...
// lucide-react icons

// ── Types ────────────────────────────────────────────────────────────
// Minimal TypeScript interfaces matching the spec/contract entity shapes.
// Defined inline — no imports from src/types/ (keep mockup self-contained).

// ── Mock Data ────────────────────────────────────────────────────────
// 4–6 realistic records per main entity.
// Include all status variants the spec defines.
// Use domain-realistic names, dates, durations, and numbers.
// Never use "Item 1" or "Test Course".

// ── {ScreenName} ─────────────────────────────────────────────────────
// type ActiveView = 'list' | 'builder' | 'detail' | ...
// type MockState = 'empty' | 'loading' | 'populated'
// useState for activeView and mockState
// Renders <MockupControls /> + the active view component

// ── MockupControls ───────────────────────────────────────────────────
// Floating bottom bar — fixed bottom-4 left-1/2 -translate-x-1/2 z-50
// Contains: view switcher tabs | state switcher (empty / loading / populated)
// Styled visually distinct from app UI (dark pill background)
// Non-intrusive — does not affect layout
```

The first line of the file **must** be the `// Covers:` comment listing all story IDs covered by this screen (e.g. `// Covers: US-01, US-02`). This is how implementation sessions identify which stories this mockup covers.

**Styling fidelity:**
- HSL tokens only — `text-primary`, `bg-card`, `text-muted-foreground` etc. Never hardcode colors
- shadcn/ui components for all interactive elements from `@/components/ui/`
- Icons from `lucide-react` only
- `cn()` for all conditional classes from `@/lib/utils`
- Framer Motion animations at the same points the real implementation would use them
- Mobile-first responsive — same `sm:` / `md:` / `lg:` breakpoints as existing pages
- The `// Mirrors:` line at the top of every generated mockup file is non-negotiable. It is the structural audit trail — `grep '// Mirrors:'` shows every reference each mockup committed to copying from; `grep '// Mirrors: none'` shows every freely-designed mockup. For screens with no production analog (per the no-match rule in Phase 3), write `// Mirrors: none — no production reference`.

**CONSISTENCY MANDATE — non-negotiable for every screen type:**

| Concern | Rule |
|---------|------|
| Page header | Use `<PageBanner>` on every page that renders inside `<Layout>` (sidebar + header shell). Dashboard pages and full-screen overlay flows use a plain `<h2>` + `<p>` header instead. Never `theme="default"` on builder screens. |
| Cards | Use `<Card>`, `<CardHeader>`, `<CardTitle>`, `<CardDescription>`, `<CardContent>` from `@/components/ui/card`. Never build card shells from raw divs. `border-dashed` on `<Card>` is for _optional or unconfigured_ sections only — for plain layout grouping, use `<div className="rounded-lg border border-border p-4 space-y-3">`. |
| Typography | Section heading: `text-xl font-semibold text-foreground tracking-tight` (equivalent to `text-[hsl(200_20%_10%)]`). Card title: default `<CardTitle>`. Body/label: `text-sm text-muted-foreground`. Metadata: `text-xs text-[hsl(200_10%_50%)]`. |
| Status badges | Incentive/course screens: `bg-success/10 text-success` (ACTIVE/PUBLISHED), `bg-muted text-muted-foreground` (DRAFT), `bg-destructive/10 text-destructive` (ARCHIVED/SUSPENDED). User/account screens: `bg-green-100 text-green-800` (ACTIVE), `bg-red-100 text-red-800` (SUSPENDED). Always read the nearest production badge component — never invent new color combos. |
| Buttons | Use `<Button variant="...">` — never custom button-like divs. Inside `<Button>` the component handles icon sizing automatically. Outside `<Button>` in custom icon+text layouts: `gap-2`, icon at `h-4 w-4`. |
| Spacing | Page sections: `space-y-6` or `gap-6`. Within card: `space-y-4`. Form field (label + input): `space-y-2`. |
| Colors | Prefer semantic tokens (`text-primary`, `text-muted-foreground`, `border-border`, `bg-success/10`, `text-success`, `bg-destructive/10`). Only use literal HSL values that already exist in `src/index.css`. Never introduce new HSL values. Status badge colors are the one explicit exception — follow the Status badges row above. |

**Interactivity (must be fully click-through):**

| Action | What happens in mockup |
|--------|------------------------|
| Form submit | Toast success state / inline validation errors |
| Table row click | Navigate to detail view |
| Status button click | Badge updates, state changes |
| Builder step Next | Progress to next step with visual feedback |
| Modal / Drawer trigger | Opens and closes correctly |
| Accordion section | Expands and collapses |
| AI copilot send | Shows simulated streaming response |
| Delete / Archive | Item removed or status updated in local state |

**Mock data quality:**
- Realistic names — "Advanced Sales Methodology", not "Course 1"
- Realistic dates — spread across weeks/months in 2026
- All status variants present — DRAFT, PUBLISHED, ARCHIVED each represented
- Realistic numbers — progress percentages, lesson counts, durations
- Empty state copy written as it would appear in production — informative, with a clear CTA

**States (every list/table/data view):**

| State | What to show |
|-------|-------------|
| `empty` | Icon + descriptive message + CTA button |
| `loading` | Skeleton placeholders matching the populated layout |
| `populated` | Full mock records |

### 4d. Confirm the dev route

Routes are **auto-served by `MockupRouter`** — no App.tsx edits required. Any `.tsx` file placed under `src/mockups/{feature-id}/` is immediately accessible without manual registration.

The route for this screen: `/mockup/{feature-id}/{screen-slug}` (kebab-case of the PascalCase filename — e.g. `CourseListPage.tsx` → `/mockup/enablement-courses/course-list-page`).

### 4e. Write mockup_file to blueprint story frontmatter (MANDATORY)

For **every story** covered by this screen, edit `../tenxengage-blueprint/features/{feature-id}/stories/US-{NN}-{slug}.md`:
- Change `mockup_file: null` → `mockup_file: "src/mockups/{feature-id}/{ScreenName}.tsx"`

All stories covered by a screen get the same `mockup_file` path pointing to that screen's TSX file.

### 4f. Update tracker

Edit `../tenxengage-blueprint/features/{feature-id}/tracker.md`:
- In the Stories table, set the `Mockup` cell for each covered story → `src/mockups/{feature-id}/{ScreenName}.tsx`

### 4g. Commit after each screen

After each screen's mockup + frontmatter + tracker updates are all written:

```
git add src/mockups/{feature-id}/{ScreenName}.tsx
git commit -m "mockup: {feature-id} {ScreenName} — covers {story-ids}"

git -C ../tenxengage-blueprint add features/{feature-id}/stories/ features/{feature-id}/tracker.md
git -C ../tenxengage-blueprint commit -m "mockup: {feature-id} {ScreenName} — stamp tracker + story frontmatter"
```

This makes each screen's mockup independently revertible.

---

### 4h. Regenerate FullFeatureMockup (runs once after the full loop)

After **all screens** in this batch are committed, create or overwrite `src/mockups/{feature-id}/FullFeatureMockup.tsx`.

This file is the **feature navigator** — it is served at `/mockup/{feature-id}` by `MockupRouter` (which looks for `FullFeatureMockup.tsx` when no screen slug is given). Overwriting it on every pass is safe and expected.

**What it must contain:**

1. A `SCREENS` registry array — one entry per screen that exists in the folder (all screens generated across all passes, not just this batch). For each screen:
   - `key` — PascalCase component name (e.g. `"CourseListPage"`)
   - `label` — human-readable name (e.g. `"Course List"`)
   - `stories` — array of story IDs covered (e.g. `["US-01", "US-02"]`)
   - `description` — one sentence describing the screen
   - `component` — `lazy(() => import('./{ScreenName}'))`

2. A `FeatureIndex` component (shown when no screen is active):
   - Page header: feature name as `<h1>` + one-line description as `<p>`
   - Grid of card-buttons (`grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4`), one per screen
   - Each card: screen label (bold), description (muted), story ID badges (`font-mono bg-muted text-muted-foreground`)
   - Clicking a card sets the active screen

3. The main `FullFeatureMockup` default export:
   - `useState<string | null>(null)` for `activeKey`
   - When `activeKey` is set: lazy-renders the matching screen component full-screen via `<Suspense fallback={null}>`
   - When `activeKey` is null: renders `<FeatureIndex />`
   - Always renders the **floating feature nav widget** on top

4. The **floating feature nav widget** — fixed, always visible regardless of active screen:
   ```
   fixed bottom-4 left-1/2 -translate-x-1/2 z-[100]
   flex items-center gap-1
   bg-foreground/90 backdrop-blur-sm text-background
   rounded-full px-2 py-1.5 shadow-xl
   max-w-[90vw] overflow-x-auto
   ```
   Contents (left to right):
   - **Home pill** — feature name as label; click sets `activeKey` to null; active style: `bg-background text-foreground`; inactive: `text-background/70 hover:text-background`
   - A `w-px h-4 bg-background/20 mx-1 shrink-0` divider
   - **One pill per screen** — screen label; click sets `activeKey` to that screen's key; same active/inactive styles as home pill
   - All pills: `text-xs font-medium px-3 py-1 rounded-full transition-colors whitespace-nowrap`

**Template:**

```tsx
// MOCKUP: {feature-id} — Feature Navigator
// Served at /mockup/{feature-id} — switch screens via the floating nav widget.

import { useState, lazy, Suspense } from 'react'
import { cn } from '@/lib/utils'

const SCREENS = [
  {
    key: 'CourseListPage',
    label: 'Course List',
    stories: ['US-01', 'US-02'],
    description: 'Browse and manage courses',
    component: lazy(() => import('./CourseListPage')),
  },
  // ... one entry per screen in the folder
] as const

type ScreenKey = typeof SCREENS[number]['key']

function FeatureIndex({ onSelect }: { onSelect: (key: ScreenKey) => void }) {
  return (
    <div className="min-h-screen bg-background p-8">
      <div className="max-w-4xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">{Feature Name}</h1>
          <p className="text-sm text-muted-foreground mt-1">Select a screen to preview it. Use the nav widget below to switch between screens.</p>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {SCREENS.map(screen => (
            <button
              key={screen.key}
              onClick={() => onSelect(screen.key)}
              className="text-left rounded-xl border border-border bg-card p-5 hover:border-primary/40 hover:shadow-sm transition-all space-y-2"
            >
              <div className="text-base font-medium text-foreground">{screen.label}</div>
              <div className="text-sm text-muted-foreground">{screen.description}</div>
              <div className="flex flex-wrap gap-1 pt-1">
                {screen.stories.map(s => (
                  <span key={s} className="text-xs font-mono bg-muted text-muted-foreground rounded px-1.5 py-0.5">{s}</span>
                ))}
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

export default function FullFeatureMockup() {
  const [activeKey, setActiveKey] = useState<ScreenKey | null>(null)
  const activeScreen = SCREENS.find(s => s.key === activeKey)
  const ActiveComponent = activeScreen?.component ?? null

  return (
    <div className="relative min-h-screen bg-background">
      {ActiveComponent ? (
        <Suspense fallback={null}>
          <ActiveComponent />
        </Suspense>
      ) : (
        <FeatureIndex onSelect={setActiveKey} />
      )}

      <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-[100] flex items-center gap-1 bg-foreground/90 backdrop-blur-sm text-background rounded-full px-2 py-1.5 shadow-xl max-w-[90vw] overflow-x-auto">
        <button
          onClick={() => setActiveKey(null)}
          className={cn('text-xs font-medium px-3 py-1 rounded-full transition-colors whitespace-nowrap', activeKey === null ? 'bg-background text-foreground' : 'text-background/70 hover:text-background')}
        >
          {Feature Name}
        </button>
        <div className="w-px h-4 bg-background/20 mx-1 shrink-0" />
        {SCREENS.map(screen => (
          <button
            key={screen.key}
            onClick={() => setActiveKey(screen.key)}
            className={cn('text-xs font-medium px-3 py-1 rounded-full transition-colors whitespace-nowrap', activeKey === screen.key ? 'bg-background text-foreground' : 'text-background/70 hover:text-background')}
          >
            {screen.label}
          </button>
        ))}
      </div>
    </div>
  )
}
```

**Commit FullFeatureMockup separately after writing it:**

```
git add src/mockups/{feature-id}/FullFeatureMockup.tsx
git commit -m "mockup: {feature-id} FullFeatureMockup — feature navigator ({N} screens)"
```

---

## Phase 5 — Final Summary and Review Instructions

After all screens are generated, print:

```
Mockup pass complete: {feature-id}

Feature navigator:  /mockup/{feature-id}   ← open this to switch between all screens

Generated ({N} screens):
  CoursesListPage       src/mockups/{feature-id}/CoursesListPage.tsx      →  /mockup/{feature-id}/courses-list-page    (covers: US-01, US-02)
  CourseDetailPage      src/mockups/{feature-id}/CourseDetailPage.tsx     →  /mockup/{feature-id}/course-detail-page   (covers: US-05, US-06)
  ...

Skipped ({M} stories):
  US-XX  Already had mockup_file: src/mockups/...
  US-YY  BE-only — no UI

Mirror gaps ({K} screens with no production analog):
  {ScreenName1}  ({feature-id}/{ScreenName1}.tsx)  — {one-line description}
  {ScreenName2}  ({feature-id}/{ScreenName2}.tsx)  — {one-line description}
  (omit this block entirely if K == 0)

Tracker updated: {N} Mockup cells stamped
Story files updated: {N} mockup_file frontmatter fields set

── To review your mockups ──────────────────────────────────────────────

1. Start the dev server: npm run dev
2. Open the feature navigator in Chrome: /mockup/{feature-id}
   Use the floating pill widget at the bottom to switch between screens.
   Or open individual screen URLs directly (listed above).
3. Open the Claude extension in Chrome (side panel or popup)
4. Describe changes to Claude: e.g. "move the status badge to the left of the title"
   or take a screenshot and ask "make the empty state illustration larger"
5. Claude will suggest or apply TSX edits directly
6. Repeat until the mockup matches your vision
7. Once satisfied, run /create-mockups again to regenerate if needed,
   or just edit the TSX file directly for fine-grained tweaks
8. Mockup paths are already recorded in story frontmatter —
   implementation sessions will pick them up automatically

When all mockups are approved → begin implementation with /load-story.
```

---

## Rules

- **Always wait for developer confirmation of the screen grouping (Phase 2) before generating any files**
- **Always write back mockup_file to blueprint story frontmatter after creating each mockup (Phase 4 is mandatory)**
- **Generate in dependency order** — if US-03 depends on US-02, generate the screen covering US-02 first so shared visual patterns are consistent
- **Shared mock data** — if two screens share the same entity type, use consistent names/IDs across their mock data (same course IDs, same tenant, same dates)
- **One commit per screen** — never batch multiple screens into a single commit; each screen mockup must be independently revertible
- **Never overwrite an existing mockup** — if `mockup_file` is already set (not `null`) in any story's frontmatter, skip that story during work list construction; do not regenerate
- **Mockup filtering uses story frontmatter, not tracker** — the authoritative skip signal is `mockup_file` in each story file's frontmatter, not the Mockup cell in tracker.md
- **Mockup code only** — no real services, hooks, API calls, TanStack Query, or context providers
- **Same styling system as production** — same components, same tokens, same patterns
- **Never use placeholder colors** — `bg-muted` not `bg-gray-200`, `text-muted-foreground` not `text-gray-500`
- **All interactions respond** — no dead buttons, no broken navigation, no non-functional form fields
- **MockupControls is non-intrusive** — fixed position, does not push content or affect layout
- **Fully self-contained** — no imports from `src/services/`, `src/hooks/`, or `src/contexts/`
- **No App.tsx edits for routes** — `MockupRouter` auto-serves all files in `src/mockups/`. Never add lazy imports or `<Route>` elements to App.tsx for mockups.
- **FullFeatureMockup is always regenerated** — after each pass, overwrite `FullFeatureMockup.tsx` with the current full screen registry. Overwriting is safe and expected; never skip this step.
- **Blueprint repo commits use `git -C ../tenxengage-blueprint`** — never `cd` into the blueprint repo; always operate from the frontend working directory
- **File path is per-screen, not per-story** — `src/mockups/{feature-id}/{ScreenName}.tsx`; never create per-story subfolders
- **Dev route is per-screen** — `/mockup/{feature-id}/{screen-slug}`; never use US-NN in the route path
- **Every TSX file starts with `// Covers: US-NN, ...`** — this comment on line 1 is mandatory; it tells implementation sessions which stories the file covers
