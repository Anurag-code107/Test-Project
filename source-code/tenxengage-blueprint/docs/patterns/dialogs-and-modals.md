# Pattern: dialogs-and-modals

## When this applies

Use this pattern when a feature introduces any overlay UI: confirmation dialogs, form dialogs, informational modals, or large side panels (Sheets). Signal: any spec describing "a dialog", "a modal", "a drawer", or "a confirmation prompt."

## Spec authoring guidance

- Choose the right overlay type:
  - **`AlertDialog`** — destructive confirmations (delete, archive, discard). Always requires explicit Cancel + Confirm. No escape key dismissal by default.
  - **`Dialog`** — informational or form content. Escape and backdrop click dismiss it.
  - **`Sheet`** — large side panels (right-side drawer). Use for content that benefits from full-height display (detail views, settings panels).
- Specify body padding, footer button alignment, and whether the dialog has a title.
- Every `Dialog` MUST include `DialogDescription` for ARIA compliance.

## Implementation guidance

TBD — capture from:
- `src/components/course/CloneCourseDialog.tsx`
- `src/components/course/DeleteCourseDialog.tsx`
- `src/components/incentive-builder/modals/FiscalQuarterCalendarModal.tsx`

Sections to document:
- Standard dialog structure: `DialogHeader` / `DialogTitle` / `DialogDescription` / body / `DialogFooter`
- Footer button order: secondary (Cancel/Close) on left, primary on right
- Body padding (`p-6` or derived from shadcn defaults)
- When to use `Sheet` vs `Dialog` (content size, interaction complexity)
- Escape behavior and backdrop click: Dialog closes, AlertDialog does not
- Form dialogs: submit on Enter, loading state on confirm button, error display inside dialog (not toast)
- Confirmation copy conventions: action-first ("Delete course?"), not question-last ("Are you sure?")

## Examples in codebase

- `../tenxengage-frontend/src/components/course/DeleteCourseDialog.tsx` — AlertDialog for destructive action
- `../tenxengage-frontend/src/components/course/CloneCourseDialog.tsx` — Dialog with form content

## Common gotchas

TBD — add from `page-layout.md` pitfalls: every Dialog must include `DialogDescription`.
