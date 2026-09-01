# Page Layout Pattern

This document describes the standard layout conventions for all feature pages and mockups in the TenXEngage frontend, including the `PageBanner` component, how themes are added, and how standalone mockup pages differ from production pages.

## PageBanner — Standard Page Header

Every feature page must open with a `PageBanner` header. The flat `border-b border-border bg-card` header style is **not** the standard — it was used in early development and must not be used for new pages.

`PageBanner` provides a gradient card with themed SVG line art, a `text-3xl font-bold` title, a subtitle, an optional back arrow, and an optional right-side `actions` slot.

```tsx
import { PageBanner } from '@/components/PageBanner'

<PageBanner
  title="Courses"
  subtitle="6 courses · search, filter, publish, clone, archive"
  theme="builder-manual"
  onBack={() => navigate(-1)}        // optional — renders a back arrow
  actions={<Button>New Course</Button>} // optional — renders on the right
/>
```

### Available Themes

| Theme | Used On |
|---|---|
| `default` | Generic / fallback |
| `home` | Home dashboard |
| `incentives` | Incentive list pages |
| `builder-ai` | Incentive builder (AI mode) |
| `builder-manual` | Course builder and other platform-primitive builders |
| `claims` | Claims pages |
| `reports` | Reporting pages |
| `activity` | Activity / timeline pages |
| `profile` | User profile |
| `users` | User management |
| `settings` | Settings pages |
| `rewards` | Reward management |
| `deal-qualifier` | Deal qualifier feature |
| `view-incentives` | Partner-facing incentive view |

> **Adding a new theme:** follow the "Adding a New Theme" section below. The `enablement-courses` theme does not exist in the current `BannerTheme` type — do not use it. For enablement pages, use `builder-manual` or add a new theme following the instructions below.

### Adding a New Theme

1. Add the string literal to the `BannerTheme` union type at the top of `src/components/PageBanner.tsx`.
2. Write a `function YourFeatureArt()` using the existing art functions as a template. The pattern is:
   - Two `<path>` flowing curves (use `home-banner-line home-banner-line-1/2` classes)
   - 2–3 Lucide icon outlines rendered as inline SVG using `<g transform="translate(X Y) scale(N)">` wrappers
   - 3 accent `<circle>` dots (use `home-banner-dot home-banner-dot-1/2/3` classes)
   - All strokes use `hsl(...)` values with low opacity (0.18–0.35) so art is always subtle
3. Register the function in the `themeArt` record below the art functions.

The SVG viewport is `0 0 1000 160`. Art shapes should cluster in the right 40% of the viewport (`x: 680–980`) to avoid overlapping the text on the left.

## Production Page Layout

Production pages live inside `Layout.tsx` which applies `p-6` padding on the `<main>` element. `PageBanner` has `rounded-2xl` so it naturally appears as a floating card within this padding — no extra wrapper needed.

```tsx
// In a production page (e.g. src/pages/partner-admin/SomePage.tsx)
export default function SomePage() {
  return (
    <div className="space-y-6">
      <PageBanner title="..." subtitle="..." theme="..." />
      {/* rest of page content */}
    </div>
  )
}
```

## Mockup Page Layout

Mockup pages are standalone full-screen components (not inside `Layout.tsx`). To replicate the same visual effect — PageBanner as a padded floating card — wrap it in `<div className="p-6 pb-0">`:

```tsx
// In a mockup (e.g. src/mockups/enablement-courses/SomePage.tsx)
export default function SomeMockupPage() {
  return (
    <div className="min-h-screen bg-background pb-20">
      <div className="p-6 pb-0">
        <PageBanner title="..." subtitle="..." theme="..." />
      </div>
      <div className="p-6">
        {/* content */}
      </div>
    </div>
  )
}
```

## Split-Panel Pages (e.g. AI Copilot)

When a page uses a full-height split panel below the banner, avoid fixed `h-[calc(100vh-Npx)]` height on the panel. Instead make the outer container a flex column and use `flex-1 overflow-hidden` on the panel:

```tsx
<div className="flex min-h-screen flex-col bg-background pb-20">
  <div className="p-6 pb-0">
    <PageBanner ... />
  </div>
  <div className="flex flex-1 overflow-hidden">
    {/* left panel */}
    {/* right panel */}
  </div>
</div>
```

This keeps the panel filling the remaining viewport height regardless of the banner's actual rendered height.

## Subtitle Constraints

## Pitfalls

**`subtitle` accepts `React.ReactNode`, not just `string`.** Status badges, icons, or JSX are valid in the subtitle. However, to keep the visual design consistent with the rest of the platform, prefer using the `actions` prop for interactive elements (badges, buttons) and keep `subtitle` as a plain string:

**Always handle `isError` from TanStack Query.** A failed query silently falls through to the empty-state or an empty table with no user feedback. Destructure `isError` and `refetch` from every `useQuery` hook and render a user-facing error message with a Retry button when `isError` is true.

**Add a distinct empty state for search-zero-results.** When a page supports search, add a `!isLoading && results.length === 0 && hasSearch` branch that explains "No X match your search" and offers a "Clear search" CTA. The table's generic "No X found." fallback row does not give the user enough context or a clear action.

**Wrap `<Table>` in `<div className="overflow-x-auto">`, AND add `className="min-w-[640px]"` (or appropriate pixel value) to the `<Table>` itself.** Without the wrapper, multi-column tables overflow the viewport with no scroll affordance. Without the inner `min-w`, the table compresses its columns instead of scrolling — `overflow-x-auto` only activates when the child's natural width exceeds the container, which requires an explicit minimum width on the Table.

**Picker/selector components that list paginated entities must handle pagination.** Calling `useXxx()` with no params silently shows only page 0 (default 20 items) — banks or other entities beyond the first page become unreachable. Either add a search input that passes `{ search }` to the query, or use a large explicit `pageSize` (e.g. `{ pageSize: 100 }`). Add an E2E scenario covering more results than a single page.

**Multi-select pickers with a search filter must not silently retain hidden selections.** `selectedIds` (a `Set`) persists across search-term changes — previously selected rows that are scrolled out of view or filtered away are still in the set and will be included in the submit payload. Either (a) clear selections on `search` change, or (b) render a visible "N selected" summary panel listing the selected item prompts/names so users can deselect before submitting.

**Every shadcn `<Dialog>` must include `<DialogDescription>`.** Import it from `@/components/ui/dialog` and add a short description below `<DialogTitle>`. Omitting it fails ARIA dialog spec and screen readers announce the dialog with no context.

**`text-warning-foreground` does not exist — use `text-foreground` on warning-tinted backgrounds.** `index.css` defines `--warning` (the background/border tint) but not `--warning-foreground`. Using `text-warning-foreground` produces an `hsl(var())` with no variable resolved, rendering text invisible. For text on a `bg-warning/N` or `border-warning/N` surface, use `text-foreground` (neutral) or `text-warning` (if icon/emphasis is needed). Valid: `bg-warning/10 border-warning/30 text-foreground`.

## Subtitle Conventions

The `subtitle` prop on `PageBanner` accepts `React.ReactNode` — JSX is valid. However, to keep the visual design consistent, prefer a plain `string` subtitle and use `actions` for interactive or status elements:

```tsx
// ✓ Preferred — plain string, interactive elements in actions
subtitle="12 lessons · by Sarah Chen"
actions={<><Badge>Published</Badge><Button>Edit</Button></>}

// ✓ Also valid — ReactNode in subtitle when needed
subtitle={<span>12 lessons <span className="text-primary">· active</span></span>}
```
