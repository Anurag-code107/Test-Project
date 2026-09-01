# Pattern: data-states

## When this applies

Use this pattern when a feature introduces any data-fetching component — list pages, detail panels, tables, cards. Signal: any spec section describing loading, empty, or error conditions for a UI surface.

## Spec authoring guidance

- For every page and component that fetches data, specify all three states: **loading**, **empty**, and **error**.
- Empty state must include: copy ("No {entities} yet" or "No results match your search"), and a CTA (where relevant).
- Search-zero-results is a DISTINCT empty state — spec it separately if the page has search/filter.
- Error state must include: user-facing message and a Retry button (or back navigation).
- Specify which errors show an inline error vs. a toast vs. a banner.

## Implementation guidance

TBD — capture from:
- `src/components/incentives/IncentiveGridSkeleton.tsx`
- `src/components/ui/DataTable.tsx`
- `src/components/ui/EmptyState.tsx` (if it exists)

Sections to document:
- Loading skeleton: match the expected content shape (card grid skeleton, table row skeleton, single entity skeleton)
- Skeleton classes: `bg-muted animate-pulse rounded-xl` — match the content's border-radius and layout
- Empty state component: props pattern, icon usage, copy conventions
- Search-zero-results: show search term in copy ("No courses match 'intro'"), offer "Clear search" link
- Inline error: `isError` from TanStack Query → error message + Retry button (see page-layout.md pitfall)
- Toast vs. banner vs. inline: mutations → toast; persistent load failure → inline error with retry; action-blocking system message → banner
- Loading state accessibility: `role="status" aria-busy="true" aria-label="Loading ..."` on skeleton containers

## Examples in codebase

- `../tenxengage-frontend/src/components/incentives/IncentiveGridSkeleton.tsx` — grid skeleton
- `../tenxengage-frontend/src/components/ui/DataTable.tsx` — table with loading/empty states

## Common gotchas

- **React Query cached errors cause repeated `useEffect` close loops.** When a drawer or panel auto-closes on a 404 error, React Query caches that error on the query key. Reopening the same entity immediately re-surfaces the cached 404 before a refetch resolves, re-triggering the close effect in a loop. Fix: track the last-handled error object in a `useRef`; skip the effect if the error reference is unchanged. Reset the ref when the target entity id changes so a fresh 404 for a different entity is still acted on. Example: `EntityDrawer.tsx` `handledErrorRef` guard added in paths-and-assignments followup.

- **Add `vi.mock` stubs for every `useQuery`-based hook the component uses, not just the one under test.** When a shared component (e.g., `CompositionSection`) grows to call additional `useQuery` hooks (`useCourses`, `useAssessments`, `useRewardCurrencies`), existing Vitest specs that render it without a `QueryClientProvider` will begin failing with "No QueryClient set". Add a module-level `vi.mock('@/hooks/...')` stub returning the shape the component reads (`{ data: ..., isLoading: false }`) for every `useQuery`-backed hook the component touches — even if the test is not directly testing that hook's behavior.

- **Replace `as unknown as UseQueryResult<T>` double-casts in test mocks with a typed factory.** A helper function that accepts the narrow fields the component reads (`data`, `isLoading`, `isError`) and fills in UseQueryResult defaults eliminates scattered double-casts that bypass structural type checking. Maintain one factory per hook at the test-file level. Example: `makeEnrollmentsResult()` in `EnrollmentList.test.tsx`.

- **Empty-state containers with `role="status"` MUST also carry `aria-live="polite"`.** `role="status"` alone does not guarantee announcement — the container must also have `aria-live="polite"` so screen readers announce dynamically-injected empty-state text after data loads. Pattern: `<div role="status" aria-live="polite" className="...">`.

- **Skeleton `<TableHead>` cells MUST include hidden column header text.** A skeleton `<th>` that renders only a `<Skeleton>` element gives screen readers no column context. Add a visually-hidden span: `<TableHead scope="col"><span className="sr-only">{columnHeader}</span><Skeleton ... /></TableHead>`.

- **Populated `<Table>` elements should carry `aria-label` matching the section name.** While the parent `<section aria-label="...">` provides context at the section level, the `<Table>` itself should have `aria-label` so assistive technology can self-describe the table when navigated outside section context: `<Table aria-label="Segment Breakdown">`.
