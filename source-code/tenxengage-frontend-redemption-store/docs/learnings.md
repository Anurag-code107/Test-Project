# Learnings Log

Append-only record of findings promoted from ready-check reports to project docs.
Not referenced anywhere — exists to track the rate of new pitfalls discovered over time.
A declining number of entries per feature signals the conventions are working.

---

## 2026-05-15 — wallet-ledger-foundation

| Rule | Category | Applied to |
|---|---|---|
| Layout wrapper components that render `<main>` must include `aria-label="Main content"` for screen reader navigation | accessibility | PROJECT-CONTEXT.md |

---

## 2026-05-15 — features/redemption-catalog (90d7c8c)

### [HIGH] z.coerce.number() coerces empty string to 0 in optional override fields
- **File:** `src/components/redemption-catalog/ItemConfigPanel.tsx`
- **Source:** adversarial-review (blocking)
- **Promoted to:** `docs/patterns/form-handling.md` § Optional Numeric Override Fields; `PROJECT-CONTEXT.md` Anti-Patterns
- **Fix:** `z.preprocess((v) => (v === "" || v == null ? undefined : v), z.coerce.number().min(0).optional())` + spread-omit pattern in request payload.

### [MEDIUM] useEffect syncing server data into react-hook-form via reset()
- **Files:** `ItemConfigPanel.tsx`, `TenantRedemptionSettingsForm.tsx`
- **Source:** code-review (advisory)
- **Promoted to:** `docs/patterns/form-handling.md` § Syncing Server Data into react-hook-form
- **Note:** Accepted pattern for edit forms, but triggers an extra render per dependency change. Using `reset()` over `setValue` field-by-field is preferred.

---

## 2026-05-26 — redemption-flow

| Rule | Category | Applied to |
|---|---|---|
| Never import lucide icons via ESM sub-paths (`lucide-react/dist/esm/icons/*`) — lacks TS declarations, breaks `tsc -b`. Use `import { Icon } from "lucide-react"` only. | react-patterns | PROJECT-CONTEXT.md Anti-Patterns |

---

## 2026-06-01 — redemption-approval-queue

| Rule | Category | Applied to |
|---|---|---|
| Never call `toast.success/error()` directly in the render body — fires on every re-render. Use `useEffect([isError])`, event handlers, or mutation callbacks. | react-patterns | PROJECT-CONTEXT.md Anti-Patterns |
| Never use hardcoded HSL arbitrary values for colours that have a design token (`success`, `destructive`, `primary`, etc.) — use Tailwind token classes (`bg-success/10`, `text-destructive`) instead. | tailwind | PROJECT-CONTEXT.md Anti-Patterns |
| Never use `Date.toISOString()` to produce a date-only string for API params — converts to UTC, off-by-one for users east of UTC. Use `getFullYear/getMonth/getDate` local fields instead. | adversarial | PROJECT-CONTEXT.md Anti-Patterns |

---

## 2026-06-09 — redemption-history (a1d9f1d)

### [HIGH] Page-level permission ≠ detail-level permission on admin list pages
- **File:** `src/pages/redemption-history/TenantTransactionHistoryPage.tsx`
- **Source:** adversarial-review (blocking)
- **Promoted to:** `docs/patterns/permissions-and-feature-flags.md` Rule 7
- **Fix:** `canViewDetail = can("action.redemption.view_history")` from `usePermissions()`. Pass `onRowClick={canViewDetail ? handler : undefined}` and conditionally mount the detail sheet. A user with `view_all_history` but not `view_history` gets a non-interactive list.

### [HIGH] SheetContent renders a built-in close button — custom SheetClose creates a duplicate
- **File:** `src/components/redemption-history/TransactionDetailSheet.tsx`
- **Source:** ui-ux-review (high)
- **Fix:** Add `[&>button:last-child]:hidden` to the SheetContent `className` when using `<SheetClose asChild>` for a custom close button. The built-in `SheetPrimitive.Close` is always rendered as the last child of SheetContent (absolute top-right). Cannot remove it from `sheet.tsx` without breaking all other sheets that rely on it.

### [MEDIUM] Space key on keyboard-interactive non-button elements scrolls the page
- **Files:** `TransactionHistoryTable.tsx`, `TenantTransactionHistoryPage.tsx`
- **Source:** ui-ux-review (medium)
- **Fix:** In `onKeyDown` handlers on `TableRow` (or any non-button interactive element), always call `e.preventDefault()` for `" "` (Space) before firing the click action. Without this the browser fires its default scroll-down behavior.

| Rule | Category | Applied to |
|---|---|---|
| Static arrays/objects defined inside component function bodies create new references every render — hoist to module scope (`const COLS = [...]` above the function) or wrap with `useMemo` for derived objects (pagination shape, filter snapshot). | react-patterns | project convention |

---

## 2026-06-13 — redemption-returns (US-01)

| Rule | Category | Applied to |
|---|---|---|
| Destructure stable function refs (`mutateAsync`, `mutate`) from `useMutation` hooks before using in `useCallback` deps — the mutation object itself is a new reference every render, so using the whole object as a dep defeats memoization | react-patterns | PROJECT-CONTEXT.md Anti-Patterns |
| Status badge className maps must use semantic Tailwind tokens (`text-warning`, `text-success`, etc.) — NEVER hardcode palette colors like `text-amber-600` or `text-orange-500` | tailwind | PROJECT-CONTEXT.md Anti-Patterns |
| Table loading skeletons must match the column count of the data state — if an Actions column is conditionally rendered based on a prop, derive the skeleton column list from that prop too | ui-ux | PROJECT-CONTEXT.md Anti-Patterns |

---

## 2026-06-13 — redemption-returns (US-02)

| Rule | Category | Applied to |
|---|---|---|
| `overflow-x-auto` on a table wrapper only scrolls if the table exceeds the container width — add `min-w-[Npx]` on the `<Table>` itself to set a floor that triggers overflow; without it columns compress instead of scrolling | ui-ux | docs/patterns/page-layout.md Pitfalls |

---

## 2026-06-17 — redemption-analytics-basic

| Rule | Category | Applied to |
|---|---|---|
| Tailwind's `animate-spin` is NOT covered by the project's `prefers-reduced-motion` CSS block — always pair it with `motion-reduce:animate-none` inline: `className="animate-spin motion-reduce:animate-none"` | accessibility | PROJECT-CONTEXT.md Accessibility Conventions |
| NEVER attach `onClick` to a button that also has `disabled` — inert to pointer events but handler is still in event model, confusing AT; for stubs, omit `onClick` or guard with a feature flag | react-patterns | PROJECT-CONTEXT.md Anti-Patterns |
| Use lazy `useState` initialization (`useState(() => computeValue())`) whenever the initial value involves `new Date()`, date arithmetic, or parsing | react-patterns | PROJECT-CONTEXT.md Anti-Patterns |

## 2026-06-22 — redemption-analytics-advanced

| Rule | Category | Applied to |
|---|---|---|
| Metric card grids using `lg:grid-cols-N` MUST generate exactly N skeleton cards — fixed count < N leaves an empty column at lg breakpoint causing layout shift | ui-ux | PROJECT-CONTEXT.md Accessibility/Loading Conventions |
| When implementing TypeScript interfaces from `contracts/models/*.md`, verify ALL field names against the model file — spec prose may reference removed/renamed fields (e.g. `partnerTier` removed from SegmentRedemptionDto, `tiers`→`regions` in TimeToFirstRedemptionResponse); the model `.md` is the ground truth | typescript | PROJECT-CONTEXT.md Data / API Conventions |
| `role="status"` alone is insufficient for screen reader announcement — empty-state containers MUST also carry `aria-live="polite"` so dynamically-injected text is announced after data loads | accessibility | PROJECT-CONTEXT.md Accessibility Conventions; docs/patterns/data-states.md Pitfalls |
| Skeleton `<TableHead>` cells must include a visually-hidden `<span className="sr-only">` with the column header text — a Skeleton-only `<th>` gives screen readers no column context | accessibility | PROJECT-CONTEXT.md Accessibility Conventions; docs/patterns/data-states.md Pitfalls |
| Populated `<Table>` elements must carry `aria-label` matching the section name so AT can identify the table when navigated outside the parent `<section>` | accessibility | PROJECT-CONTEXT.md Accessibility Conventions; docs/patterns/data-states.md Pitfalls |

---

## 2026-06-23 — redemption-analytics-advanced

| Rule | Category | Applied to |
|---|---|---|
| Every `currencyId` table column cell renderer MUST use `getCurrency(id.toLowerCase()).label` — never render the raw backend ID (e.g. "POINTS") directly. Peer components `ItemBreakdownTable`, `SegmentBreakdownTable`, `LiabilityTrendChart`, `RedemptionTrendChart` all follow this pattern; new tables in this domain must match. | ui-ux | PROJECT-CONTEXT.md Data / API Conventions |
