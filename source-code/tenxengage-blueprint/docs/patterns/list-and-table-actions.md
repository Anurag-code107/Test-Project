# Pattern: list-and-table-actions

## When this applies

Use this pattern when a feature introduces **actions on list-page rows or table rows** — edit, delete, clone, archive, or any per-item operation. Signal: any spec describing a "row actions" column, context menu, or action buttons visible on list items.

## Spec authoring guidance

- Choose **dropdown vs. inline** row actions: use a dropdown (3-dot kebab menu) when there are ≥3 actions or when the actions are destructive. Use inline icon buttons when there are ≤2 actions and they are non-destructive.
- Specify which actions are always visible and which are conditional (e.g., "Archive only shows for PUBLISHED entities").
- Specify bulk-selection behavior: whether the feature needs multi-select with bulk operations.
- Destructive actions (delete, archive) always require a confirmation `AlertDialog`.

## Implementation guidance

TBD — capture from:
- `src/components/ui/DataTable.tsx` — row actions column definition
- `src/components/courses/CoursesListTable.tsx` — dropdown actions for courses
- `src/components/claims/ClaimsTable.tsx` — table row action patterns

Sections to document:
- Row actions dropdown: shadcn `DropdownMenu` with `DropdownMenuTrigger` (3-dot icon button)
- Icon conventions: `Pencil` (edit), `Copy` / `ClipboardCopy` (clone), `Archive` (archive), `Trash2` (delete)
- Conditional rendering: check entity status before showing transition actions
- Bulk selection: `DataTable` checkbox column, bulk action bar above table
- DataTable column definition pattern: `accessor`, `header`, `cell` renderer
- Row action destructive confirmation: always `AlertDialog`, never `Dialog`

## Examples in codebase

- `../tenxengage-frontend/src/components/ui/DataTable.tsx` — reusable table with column actions
- `../tenxengage-frontend/src/components/courses/CoursesListTable.tsx` — course row actions
- `../tenxengage-frontend/src/components/claims/ClaimsTable.tsx` — claims row actions

## Common gotchas

- **Per-row actions need a real button affordance — not ghost/plain text.** A destructive or primary row action styled as bare colored text (`variant="ghost"` + `text-destructive`) reads as a label, not a control. Use a bordered/filled button (`variant="outline"` with a destructive tint, or `variant="destructive"`) so it's recognizably interactive, while keeping `size="sm"`, the loading spinner, `disabled`, and `aria-label`. (redemption-returns "Cancel Return", FE UX enhancements US-01, 2026-06-30.)
