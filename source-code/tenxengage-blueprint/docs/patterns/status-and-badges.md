# Pattern: status-and-badges

## When this applies

Use this pattern when a feature introduces entities with a **status lifecycle** (DRAFT, PUBLISHED, ARCHIVED, etc.) or when a feature displays categorical labels (role badges, type chips, tier labels). Signal: any spec with a `status` field on a business entity.

## Spec authoring guidance

- Enumerate all status values and their display labels in the spec.
- Specify the color convention for each status (see Implementation guidance for the platform convention).
- Specify where status badges appear: list-page rows, detail-page headers, PageBanner actions.
- Badges display status only — they do not trigger state transitions. State transition is always a separate action (button, menu item).

## Implementation guidance

TBD — capture from:
- `src/components/course/CourseStatusBadge.tsx`
- `src/components/courses/CoursesListTable.tsx` (`courseStatusStyles` map)

Sections to document:
- Status color map: `DRAFT → bg-muted text-muted-foreground`, `PUBLISHED → bg-primary/10 text-primary`, `ARCHIVED → bg-muted text-muted-foreground/60`, etc.
- Badge anatomy: `inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium`
- Icon usage: status badges may include a small icon (e.g. `CheckCircle` for PUBLISHED, `Archive` for ARCHIVED)
- `CourseStatusBadge` as the reference implementation for enablement entities
- When to use badge vs. label vs. chip (badge = status, label = category, chip = filter tag)

## Examples in codebase

- `../tenxengage-frontend/src/components/course/CourseStatusBadge.tsx` — enablement status badge
- `../tenxengage-frontend/src/components/courses/CoursesListTable.tsx` — `courseStatusStyles` map

## Common gotchas

TBD
