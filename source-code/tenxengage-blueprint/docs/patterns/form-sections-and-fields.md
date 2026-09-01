# Pattern: form-sections-and-fields

## When this applies

Use this pattern when a feature introduces a **settings form, configuration panel, or multi-field section** — any non-builder form with labeled inputs. Signal: any spec with a "settings" tab, a "configure" section, or fields that are not inside a builder accordion step.

## Spec authoring guidance

- Group related fields into named sections with a section heading.
- Specify the label typography per field.
- Specify which fields are required at submit time.
- Describe field-level validation: inline error placement (below the input), not toast-based.

## Implementation guidance

TBD — capture from:
- `src/components/settings/BrandingSection.tsx`
- `src/components/course-builder/steps/BasicsSection.tsx`

Sections to document:
- Label typography: `text-xs text-muted-foreground` (uppercase or sentence case?)
- Field spacing: `space-y-1` between label and input; `space-y-2` between fields within a group; `space-y-4` between field groups / sections
- Section heading: `text-sm font-medium text-foreground` or `text-xs font-semibold uppercase tracking-widest text-muted-foreground/70`
- Inline validation error: red border + `<p className="text-xs text-destructive mt-1">{error}</p>` below input
- Required field indicator: asterisk suffix in label or `aria-required`
- Helper text: `text-xs text-muted-foreground` below the input (not in a tooltip)
- Read-only display: `text-sm text-foreground` inside a muted container or a `<dt>/<dd>` pair

## Examples in codebase

- `../tenxengage-frontend/src/components/settings/BrandingSection.tsx` — standalone settings section
- `../tenxengage-frontend/src/components/course-builder/steps/BasicsSection.tsx` — builder-step form fields

## Common gotchas

- **Field-clear on mode change must live in the event handler, not `useEffect`.** When a radio/select change should clear a sibling field (e.g. switching from INACTIVITY to FIXED_DATE clears `inactivityDays`), do the `setValue` inside the `onValueChange` handler — not a `useEffect` that watches the controlling field. `useEffect` fires on every render where the dep changed, including renders triggered by server data sync (e.g. `values:` resync in react-hook-form), which can silently clear valid data. Event-handler clears are user-intent-only. (Source: reward-balance-expiration US-01)

- **Pass an array to a single `useWatch` call when watching multiple fields.** Instead of two `useWatch({ control, name: 'field1' })` calls, use `const [a, b] = useWatch({ control, name: ['field1', 'field2'] })` — a single subscription vs. two, and avoids double re-renders when both values update together. (Source: reward-balance-expiration US-01; rule already in PROJECT-CONTEXT.md Performance Rules)
