# Pattern: entity-list-page

## When this applies

Use this pattern when a feature introduces a **list page for a new entity type** — a page that displays a collection of items as a grid of cards or a table, with filters, search, and a "create new" CTA. Signal: any spec with a "list of {entities}" or "{Entities} overview" page.

## Spec authoring guidance

- Choose **grid-of-cards vs. table list** based on content density: cards for richly-visual items (courses, incentives); tables for text-heavy, multi-column data (claims, reports, audit logs). Call out the choice explicitly.
- Define page header: `PageBanner` with title, subtitle (count + search summary), and a "New {Entity}" primary button in `actions`.
- Specify filter controls: which fields are filterable, filter layout (sidebar vs. inline pills), and whether filters are URL-persisted.
- Define empty state: copy ("No {entities} yet"), CTA button, and whether to show a different empty state for search-zero-results.
- Specify pagination: default page size, "load more" vs. paged navigation.
- Specify "create new" CTA placement: in `PageBanner.actions` (primary) AND inline in the empty state.

## Two-up card grid (the enablement list reference)

Enablement list pages (Learning Paths, and courses where card density fits) use a **two-column card grid** — `grid grid-cols-1 sm:grid-cols-2 gap-4` (one column on mobile, two from the `sm` breakpoint up). Reference: `LearningPathCardGrid.tsx`. The "two card layout with relevant details" pattern:

- **Grid:** `grid grid-cols-1 sm:grid-cols-2 gap-4`, wrapped in a scroll container (`overflow-y-auto flex-1`). Loading skeleton mirrors the grid: 6 pulse cards of fixed height (`h-56`).
- **Card anatomy** (top → bottom):
  - **Header band** — entity icon + truncated name on the left; on the right a **status badge that doubles as an actions dropdown** when the user has applicable actions (publish/unpublish/archive/clone/delete gated by `PermissionGate`), else a plain badge. An approval-round badge (`Approval · Round N`) overrides the status label while `PENDING_APPROVAL`.
  - **Body** — optional source badge (e.g. "External"), a 2-line clamped description, and a **stats strip** of the entity's "relevant details" (Learning Path: step count + required-step count).
  - **Footer** (pushed down with `mt-auto`, separated by a top border) — availability window, a secondary meta line (`v{version} · difficulty · language`), a relative-time line (`{StatusActionLabel} {relativeTime}`), and an inline edit button.
- **Whole card is clickable** (`onClick` → edit); action controls inside stop propagation (`onClick={(e) => e.stopPropagation()}`).
- **Filter row** (above the grid): debounced search input (`useDebounce`, 300 ms) + a status filter popover; both reset `page` to 0 on change.
- **Title strip** with entity icon, name, a count pill, and the "New {Entity}" primary CTA (`PermissionGate`-gated).
- **Empty state** distinguishes filtered-zero-results ("No … match your filters" + Clear filters) from no-data ("No … yet — create your first" + New CTA).
- **Pagination:** Previous/Next with "Page N of M", shown only when `totalPages > 1`.

## Implementation guidance

TBD — additionally capture table-variant details from:
- `src/components/courses/CoursesListTable.tsx`
- `src/pages/client-admin/CoursesPage.tsx`
- `src/components/incentives/` (grid variant reference)

Sections to document:
- Grid layout: CSS grid classes, card spacing, responsive breakpoints
- Table layout: `DataTable.tsx` with column definitions, sortable columns, row actions
- Filter bar placement and state management (URL params via TanStack Router)
- Empty state component (`EmptyState`) — props, copy conventions
- Search-zero-results empty state (distinct from no-data empty state)
- Skeleton loading pattern (one skeleton card/row per expected visible item)
- Pagination controls and stale-data behavior on filter change

## Examples in codebase

- `../tenxengage-frontend/src/components/learning-paths/LearningPathCardGrid.tsx` — **two-up card grid reference** (status-badge-as-actions-dropdown, stats strip, debounced search + status filter, two empty states, pagination)
- `../tenxengage-frontend/src/pages/learning-paths/LearningPathsPage.tsx` — page shell for the card grid
- `../tenxengage-frontend/src/components/courses/CoursesListTable.tsx` — table variant with status filter, sort, row actions
- `../tenxengage-frontend/src/pages/client-admin/CoursesPage.tsx` — page shell

## Common gotchas

TBD
