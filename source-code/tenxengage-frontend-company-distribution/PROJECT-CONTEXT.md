# Project Context — TenXEngage Frontend

## Stack

- **React 18** with TypeScript (strict mode)
- **Vite** for dev server and bundling
- **Tailwind CSS** v3 — NOT v4; config API differs
- **shadcn/ui** for base UI components (Radix UI primitives)
- **TanStack Query** for server state management
- **TanStack Table** for data tables
- **React Router v6** for routing
- **react-hook-form + zod** for form validation
- **axios** for HTTP requests
- **recharts** for charts
- **lucide-react** for icons
- **react-day-picker v8** (not v9 — API changed significantly between versions)
- **framer-motion v12** (breaking changes from v10/v11 — verify API before using advanced features)

## Coding Conventions
- Functional components only, one per file
- Props interface: {ComponentName}Props
- Named exports for hooks/utils, default exports for pages/layouts
- No `any` — use `unknown` with type guards
- Path alias: @/ → src/
- No wildcard imports, no barrel exports
- Custom hooks (`use*`) must live in `src/hooks/` — never define them inline in a page file

## API Communication
- Axios with auth interceptors + token refresh queue (`lib/axios.ts`)
- Service layer: one function per endpoint, typed request/response
- TanStack Query: `useQuery` for reads, `useMutation` for writes with cache invalidation
- Query keys must include all params that affect the result: `["resource", { filters, page }]`
- Always call `queryClient.invalidateQueries` in `useMutation.onSuccess`; when the query key contains a tenant-scoped ID (e.g. `clientId`), derive it from `useAuth()` and use `null` as the fallback (never `""`), then guard the invalidation with `if (clientId)` so invalidations are skipped when there is no authenticated tenant — an empty string `""` is truthy and causes spurious cache busts (Source: reward-balance-expiration US-01)
- Fire-and-forget secondary mutations inside an `onSuccess` callback (e.g. tagging after create) must surface failures via `toast.error` — silent `.catch(() => {})` creates invisible data loss for the user
- Drawer/detail-panel read-only queries (triggered by selecting a row) MUST pass `retry: false` to `useQuery` — the default `retry: 3` causes a 404 (e.g. deleted entity) to surface after 7 s+ (3 × exponential back-off), which exceeds the 5 s Playwright timeout and can retrigger auto-close effects on the cached error; read queries that have a dedicated loading UI and no write path should never silently retry
- Auth state from `AuthContext` via `useAuth()` — never read tokens directly from storage
- Response types: `ApiResponse<T>`, `PaginatedResponse<T>`

## Performance Rules
- Eliminate waterfalls — use `Promise.all()` for independent fetches (CRITICAL)
- Avoid barrel imports — import directly from source files, not `index.ts` re-exports (CRITICAL)
- Prefer ternary over `&&` for conditional rendering — `&&` renders `0` on falsy numbers
- No derived state in `useEffect` — compute values directly from existing state or props
- `useMemo` / `useCallback` only for genuinely expensive computations — not by default
- Hoist static/constant variables to module scope outside the component body
- In `useForm`, use `values` OR `defaultValues` — not both
- In `useForm`, use `useWatch({ control, name })` at the component top rather than inline `watch(name)` calls inside JSX — inline calls cause full-form re-subscriptions on every render
- Module-level `let` counters for generating IDs are NOT static — use `useRef(0)` inside the component so the counter is per-instance and reset-safe across Strict Mode double-invocations
- In edit mode, option lists mix server-loaded IDs with client-created IDs — always use `crypto.randomUUID()` for new option IDs; sequential counters (`useRef(0)`, module `let`) collide with loaded IDs like `opt-1`, `opt-2`
- When removing an option from a choice-question editor, always reconcile `correctAnswerJson` — clear `singleCorrectId` if it matches the removed option, prune `multipleCorrectIds` of the removed ID; validate ID membership (not just non-null existence) in both `superRefine` and save-disabled logic
- For PUT endpoints with optional nested rows (explanation, reference), always send explicit `null` when removing a previously-set value — omitting the field leaves the backend unchanged; `undefined` ≠ `null` in REST PATCH/PUT semantics

## UI Standards
- Tailwind CSS utility-first (no inline styles, no CSS modules)
- shadcn/ui component library (Radix UI primitives) — DO NOT edit `src/components/ui/` files
- `cn()` helper from `@/lib/utils` (clsx + tailwind-merge)
- Mobile-first responsive: sm:, md:, lg: breakpoints
- Icons: lucide-react only

## Design System
- Colors: HSL CSS custom properties (primary, secondary, destructive, success, warning, muted, accent)
- Typography: Inter font, Tailwind text scale
- Spacing: Tailwind default (0.25rem increments)
- Border radius: 0.75rem base
- Dark mode: class-based (.dark selector)
- Animations: Framer Motion for complex, tailwindcss-animate for simple

## UX Conventions
- Toast notifications: sonner
- Forms: react-hook-form + zod validation
- Loading: skeleton components for data fetching
- Errors: user-friendly messages, never expose technical errors; always handle `isError` from TanStack Query hooks
- Empty states: meaningful messaging with call-to-action; add a distinct "no search results" state when a page supports search
- Mutations: always add `toast.success(...)` on success — silent dialog close without feedback is never acceptable

## Source Tree

```
src/
  main.tsx / App.tsx / index.css / vite-env.d.ts
  lib/          # utils.ts (cn helper), axios.ts (auth interceptors, withCredentials for HTTPOnly cookie auth)
  config/       # currencies.ts — single source of truth for currency IDs, icons, colors
  types/        # api.types.ts, auth.types.ts, user.types.ts
  services/     # one file per domain (auth.service.ts, user.service.ts, …)
  contexts/     # AuthContext.tsx
  hooks/        # useAuth.ts, useApi.ts, feature hooks
  components/   # ProtectedRoute, DataTable, layout/, ui/ (shadcn — do not edit)
  pages/        # one file per page
  utils/        # formatters.ts, validators.ts
contracts/      # API contract definitions (submodule)
```

## Testing Standards
- **Unit/Integration:** Vitest + React Testing Library + user-event; files at `src/**/__tests__/*.test.tsx`; query by role/label, mock API not internal state
- **E2E (Playwright — planned):** files at `e2e/{feature-id}.spec.ts`; mock via `page.route()` using contract responses from `contracts/endpoints/{resource}.yaml`

## Currency & Domain Rules
- 4 currency types only: `cash`, `points`, `credits`, `tickets`. Never use `gift_card` or `training_credit`.
- Always read `src/config/currencies.ts` before working with rewards/currencies. Import `getCurrency()` or `currencies` from `@/config/currencies` — never hardcode currency icons, colors, or formatting logic.
- Every `currencyId` column cell renderer in a data table MUST use `getCurrency(id.toLowerCase()).label` to display the human-readable label. Never render the raw backend value (e.g. `"POINTS"`) directly — all advanced-analytics tables (`ItemBreakdownTable`, `SegmentBreakdownTable`, etc.) follow this pattern.
- For reward-facing **amount** displays (balance cards, at-risk totals, transaction amounts, breakage values), use `getCurrency(id.toLowerCase()).rewardFormat(value)` — NOT `.format(value)`. `.format()` is the budget/admin formatter and renders `points`/`cash` as USD (`$`); `.rewardFormat()` renders reward units (e.g. `"1,000 pts"`, `"3 tickets"`). Reserve `.format()` for budget/admin/monetary contexts only. (Source: reward-balance-expiration ready-check — `ExpiringSoonPreviewCard` mislabelled points as USD.)
- Contracts are source of truth for API shapes — always check `contracts/` before creating types or services. TypeScript interfaces MUST match contracts exactly. When implementing types from `contracts/models/*.md`, verify ALL field names (not just types) — spec-adjacent DTOs (e.g. "partnerTier") may be referenced in story text but later removed from the contract model; the `.md` file is the ground truth, not the spec prose.
- Tokens stored in HTTPOnly cookies set by the backend. The axios instance uses `withCredentials: true` so the browser sends them automatically — never in localStorage, sessionStorage, or JS-accessible memory.
- Reference `../ai-incentive-pilot` for styling inspiration only — NEVER edit files in that repo.

## Adding New Features
- **New page:** Create in `src/pages/`, add route in `App.tsx`, add sidebar link in `Sidebar.tsx`
- **New API endpoint:** Check `contracts/` first → types in `src/types/` → service in `src/services/` → TanStack Query hook in `src/hooks/`
- **New UI component:** Create in `src/components/` with Props interface; for shadcn components use `npx shadcn-ui add <component>`

## Pattern References
Before starting work that involves these areas, read the relevant pattern file:
- Page headers & layout conventions → `docs/patterns/page-layout.md`
- Builder/wizard components → `docs/patterns/builder-widget-platform.md` (canonical for enablement builders)
  (use `docs/patterns/builder-widget.md` for incentive builder work)
  - The enablement builder is split into a generalized **shell** (`src/components/enablement-builder/` — context, runtime context, common sections, section registry, generic accordion, setup header) and per-type **modules** (`src/components/course-builder/` is the first). Adding an enablement type = register a module (its sections + data provider), not fork the builder. Common sections live in the shell; each type provides only its module-specific sections (`publish` is a universal key with a per-type component).
- Dynamic builder configuration → `docs/patterns/builder-config.md`
  (incentive builder; enablement builders follow the blueprint canonical at `tenxengage-blueprint/docs/patterns/builder-config.md`)
- Location hierarchy (types, hooks, cascading scope) → `docs/patterns/location-hierarchy.md`
- AI copilot integration → `docs/patterns/ai-copilot.md`
- Permissions & feature flags → `docs/patterns/permissions-and-feature-flags.md`
- Contract/API type alignment → `docs/patterns/contracts.md`
- Mutation error handling → `docs/patterns/error-handling.md`
- Form validation & optional numeric overrides → `docs/patterns/form-handling.md`

## Skills Reference
| Skill | When to Use |
|-------|-------------|
| `add-component` | Creating a new reusable UI component |
| `add-page` | New page (types → services → hooks → page → route) |
| `add-api-service` | Adding API service with TanStack Query hooks |
| `react` / `react-best-practices` | Performance optimization patterns |
| `tailwind` | Tailwind CSS patterns and configuration |
| `frontend-design` | Building production-grade UI components |
| `animate` / `polish` / `harden` | Animations, final quality pass, error/edge-case hardening |
| `critique` / `audit` / `normalize` | Design review, interface audit, design-system consistency |

## Routing Conventions
- Mockup/prototype routes in `App.tsx` must be wrapped in `{import.meta.env.DEV && (...)}` — comments saying "dev only" are not sufficient; unenforced comments ship to production.
- Page root `<div>` elements should include `animate-route-in` in their className for consistent staggered child-entry animations (`src/index.css` defines the keyframe rules).
- **HomeRedirect maintenance:** whenever a new `module.*` permission is introduced for a role that is the role's primary/only module access, add a `{ permission, path }` entry to `HOME_ROUTES` in `src/components/HomeRedirect.tsx`. Omitting it causes an infinite redirect loop (blank screen) for every user of that role on login. Current ordering: `module.home` → `module.activity_review` → `module.incentives.sales` → `module.rewards.claims` → `module.assessment_authoring` → `module.settings.profile`.
- The final fallback `return` in `HomeRedirect` (after all `HOME_ROUTES` checks) MUST point to an unprotected route such as `/403`. Never use a `ProtectedRoute`-guarded path as the fallback — if the user lacks that permission too, it bounces back to `/` and creates an infinite loop.

## Accessibility Conventions
- When a page has two or more buttons with the same visible text, add differentiated `aria-label` attributes so screen readers can distinguish them.
- Tailwind's `animate-pulse` is NOT automatically covered by the project's `prefers-reduced-motion` CSS block — add `.animate-pulse` to that block in `src/index.css` whenever you use it for skeleton loaders.
- Tailwind's `animate-spin` (used for Loader2 / spinner icons) is also NOT covered by the `prefers-reduced-motion` block — always pair it with `motion-reduce:animate-none` inline: `className="animate-spin motion-reduce:animate-none"`. Same applies to any other Tailwind `animate-*` utilities not listed in `src/index.css`.
- Layout wrapper components that render a `<main>` element must include `aria-label="Main content"` so screen reader users can identify and navigate to the primary content region.
- Multi-step wizard step indicators must use `<nav aria-label="..."><ol>` with `<li>` per step and `aria-current="step"` on the active step — a plain `<div>` with numeric spans is not accessible.
- HTML `<table>` elements MUST add `scope="col"` to all `<th>` header cells so screen readers associate headers with data columns correctly.
- `<Card>` (or any non-interactive element) used as a clickable target MUST add `role="button"`, `tabIndex={0}`, and an `onKeyDown` guard (`if (e.key !== 'Enter' && e.key !== ' ') return; e.preventDefault(); handler()`) — a bare `onClick` is mouse-only and fails keyboard and screen-reader navigation.
- Custom CSS-transition drawers (e.g. `translate-x` show/hide pattern, not Radix `Dialog`) MUST manage focus: move focus to the first interactive element inside the panel on open, and restore focus to the trigger element on close. Add `aria-hidden="true"` to the closed panel so screen readers skip its contents.
- Radix UI `RadioGroup` (via shadcn/ui) does NOT automatically inherit a label — it MUST have an `aria-labelledby` attribute pointing to a sibling `<Label id="...">` so screen readers announce the group name. Without it the group is unlabelled.
- Loading spinners inside `<Button>` elements MUST carry `aria-hidden="true"` (e.g. `<Loader2 aria-hidden="true" />`). The button's own accessible name already communicates the loading state; a duplicate icon role confuses screen readers.

## Anti-Patterns
- NEVER fetch data with `useEffect + useState` — use TanStack Query hooks exclusively
- NEVER import directly from `@radix-ui/*` — always use the shadcn/ui wrapper in `src/components/ui/`
- NEVER use `window.location` for navigation — use `useNavigate()` from React Router
- NEVER use `z.coerce.number().optional()` alone for optional numeric override fields — empty string coerces to `0`, silently overwriting inherited defaults. Use `z.preprocess((v) => (v === "" || v == null ? undefined : v), z.coerce.number().min(0).optional())` instead. See `docs/patterns/form-handling.md`.
- NEVER import lucide icons via ESM sub-paths (e.g. `import Loader2 from "lucide-react/dist/esm/icons/loader-2"`) — those sub-paths lack TypeScript declarations in this tsconfig and break `tsc -b`. Always import from the package root: `import { Loader2 } from "lucide-react"`.
- NEVER call `toast.success/error(...)` directly in the component render body — it fires on every re-render, not just on state transition. Always call toasts inside `useEffect` (for query state like `isError`), event handlers, or mutation `onSuccess`/`onError` callbacks.
- NEVER use hardcoded HSL values (e.g. `bg-[hsl(95_55%_42%/0.1)]`) for colours that have a design token — use the Tailwind token class instead (e.g. `bg-success/10`, `text-destructive`, `border-primary/30`). Available tokens: `primary`, `secondary`, `destructive`, `success`, `warning`, `muted`, `accent`. See `tailwind.config.ts` for the full mapping.
- NEVER use `Date.toISOString()` to produce a date-only string for filter params or API calls — `toISOString()` converts to UTC, so users east of UTC will get the previous calendar day. Use local fields: `` `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` `` instead.
- NEVER render `error.response.data.message` verbatim — map API error codes to client-controlled strings; only pass backend message text through for explicitly allowlisted validation responses (the UX Conventions "never expose technical errors" rule applies here)
- NEVER use native `<input type="radio">`, `<input type="checkbox">`, or `<select>` — always use shadcn/ui equivalents (`RadioGroup`/`RadioGroupItem`, `Checkbox`, `Select`/`SelectTrigger`/`SelectContent`) which have the design system's focus ring, sizing, and dark-mode styles
- NEVER pass `title` prop directly to a Lucide icon (`<IconName title="..." />`) — TypeScript rejects it; wrap in `<span title="..."><IconName aria-hidden /></span>` instead
- NEVER import Lucide icons via deep ESM paths (`lucide-react/dist/esm/icons/<name>`) — no `.d.ts` files exist at those paths in the installed version; always use the barrel import `import { X } from "lucide-react"`
- EVERY mutation confirm dialog MUST destructure `isPending` from the mutation hook and set `disabled={isPending}` on the confirm button to prevent double-submit; for async mutations (mutateAsync), ALSO add a `submittingRef = useRef(false)` guard checked at the top of the handler, set before `await`, and cleared in `finally` — `isPending`-based disable relies on a re-render that may not fire before a second rapid click
- In `useMutation` `onError` callbacks, NEVER cast the error as `{ response?: { status?: number } }` inline — use `import { isAxiosError } from "axios"` and check `isAxiosError(error) && error.response?.status === 429`; inline unsafe casts bypass TypeScript's type narrowing and hide real type errors
- When using `useMutation` results in a `useCallback` dependency array, ALWAYS destructure stable function refs (`mutateAsync`, `mutate`) directly from the hook rather than passing the whole mutation object — `useMutation` returns a new object reference on every render, so `useCallback` with the full object as a dep provides zero memoization; only destructured function refs are stable across renders
- Status badge `className` maps (e.g. `Record<SomeStatus, { label: string; className: string }>`) MUST use semantic Tailwind tokens (`text-warning`, `text-success`, `text-destructive`, `text-primary`, `text-muted-foreground`) — NEVER hardcode palette colors like `text-amber-600` or `text-orange-500`; if a semantic token for the concept does not yet exist, add it to `tailwind.config.ts` and `src/index.css` before using it
- Table loading skeletons MUST match the column count of the loaded data state — if the table conditionally renders an extra column (e.g. an Actions column gated on a prop), the skeleton's header and cell arrays must include that column so the layout does not shift when data loads
- Skeleton `<TableHead>` cells that render only a `<Skeleton>` must also include a visually-hidden column name: `<TableHead scope="col"><span className="sr-only">{header}</span><Skeleton ... /></TableHead>` — without it, screen readers traversing the skeleton table receive no column context
- Populated `<Table>` elements MUST carry `aria-label` matching the section name (e.g. `aria-label="Segment Breakdown"`) so assistive technology can identify the table when navigated outside the parent `<section>` context
- Metric card grids using `lg:grid-cols-N` MUST generate exactly N skeleton cards during loading — `Array.from({ length: N }, ...)` — a fixed count smaller than N produces an empty last column at the lg breakpoint that causes a jarring layout shift when real data loads
- Dynamic error banners that appear after a user action MUST carry `role="alert" aria-live="assertive"` so assistive technology announces them
- Informational warning banners (non-critical, advisory state — e.g. grounding status) MUST carry `role="alert" aria-live="polite"` — use `polite` not `assertive` so the announcement does not interrupt the user mid-interaction
- Skeleton divs used for pending regen states (not full-page loading) MUST carry `role="status" aria-busy="true" aria-label="Regenerating <field>"` so assistive technology knows content is updating
- NEVER add `animate-route-in` to a component's root div when that component is rendered inside a page shell that already carries `animate-route-in` — the stagger animation fires twice, producing conflicting entrance transitions; only the outermost page container should carry this class
- NEVER attach an `onClick` handler to a button that also has `disabled` — the `disabled` attribute makes the button inert to pointer events in most browsers but the handler is still present in the event model, creating a logical contradiction and confusing assistive technology. For "coming soon" stubs, omit `onClick` entirely or remove `disabled` and guard the handler body with a feature-flag check.
- Use lazy `useState` initialization (`useState(() => computeValue())`) whenever the initial value involves `new Date()`, date arithmetic, parsing, or any work done more than once per mount — eager initialization runs on every render (before React skips the re-init), and is a lint anti-pattern with date utilities that mutate local state.
- NEVER use a `ProtectedRoute`-guarded path as the fallback `return` in `HomeRedirect` — a user who lacks all listed `HOME_ROUTES` permissions will be bounced back to `/` in an infinite redirect loop that renders as a blank screen; always use `/403` (an unguarded route) as the last-resort fallback
- EVERY error state that says "Could not load X" MUST include a `<Button variant="outline" onClick={() => refetch()}>Try again</Button>` — see AssessmentListTable.tsx line 213 as the reference pattern
- Fixed-width search inputs MUST pair `w-full` with `max-w-[Npx]` (e.g. `w-full max-w-[220px]`) — bare `w-[Npx]` without `w-full` overflows the flex container on narrow viewports
- Text filter inputs that drive TanStack Query calls MUST debounce the value (300 ms) before passing it to the hook — pass the raw value to the `<Input>` and the debounced value to the query; see `useDebounce` from `src/hooks/useDebounce.ts`
- When consuming a `PaginatedResponse<T>`, components MUST wire up `totalPages` into visible pagination controls; silently rendering only `data` truncates the result set without any user-visible indication. Standard pattern: `const [page, setPage] = useState(0)` + reset to 0 on filter change + `totalPages = data?.totalPages ?? 1` + prev/next Buttons gated on `page === 0` / `page >= totalPages - 1`. For table pages use `DataTable` with `onPageChange`; for custom layouts use inline prev/next buttons (see `AttemptHistoryPanel.tsx`). Exception: picker dialogs (not full-page lists) may substitute a large `pageSize` (e.g. 50) instead of pagination controls.
- Do NOT define a local `formatDate` / `formatDateTime` function in components — import `formatDate` or `formatDateTime` from `@/utils/formatters` (date-fns-backed, handles both `string | Date` inputs)
- When an AI regen mutation errors, NEVER call `setValue` to overwrite the field — preserve the prior form value and show a toast only; the pattern is `onError: () => toast.error(...)` with no `setValue` call
- After a successful AI regen, use local state (e.g. `regenGroundingStatus`) to reflect server-returned status fields that may differ from the prop — the prop value is frozen at mount time and does not update after mutation
- In Playwright specs, use `page.getByLabel("…")` NOT `page.getByLabelText("…")` — Playwright does not have `getByLabelText`; `getByLabel` matches elements associated via `aria-label`, `aria-labelledby`, or `<label>` `for`
- NEVER synthesise a placeholder upload URL on the client (e.g. `https://uploads.tenxengage.local/${uploadId}/…`) — the upload endpoint (`POST /courses/uploads`) returns `storageUrl` in the response; always use `response.storageUrl` as the asset URL; synthesised URLs fail `@Pattern`-level or scheme validation on the server and break subsequent asset-create calls
- SSE streaming hooks that support re-invocation (start/cancel loops) MUST use a `requestIdRef = useRef(0)` counter: increment it at the start of each `start()` call AND at the start of `cancel()`, capture the value in a closure, and guard every callback (`onDone`, `onError`, `onPhaseChange`) with `if (requestIdRef.current !== thisRequestId) return` — `AbortController.abort()` stops new data from arriving but cannot cancel already-scheduled promise microtasks, so without this guard a rapid re-submit OR a cancel delivers stale finalDraft/error to the new request's state; cancel() must rotate the id just like start() does or post-cancel callbacks still pass the guard
- ANY component that renders approval or publish-lifecycle mutating controls (submit, resubmit, approve, reject, publish, archive CTAs) MUST gate those controls behind `usePermissions().can('action.<domain>.publish')` — status/state checks alone do not enforce authorization; users lacking the permission can still reach the route and trigger mutations if CTAs are ungated; read-only status displays (banners, decision badges, approver list) MUST remain accessible to all viewers
- Hardcoded Tailwind palette color classes (`text-emerald-600`, `bg-blue-500/10`, etc.) on user-facing UI elements MUST include `dark:` variants using lighter text (400-range) and slightly higher-opacity background/border (e.g. `/15` bg, `/40` border) for dark-mode contrast — omitting dark: variants makes text invisible on dark backgrounds; the only exception is classes that already use CSS custom property tokens (e.g. `text-primary`, `bg-destructive/10`) which inherit dark-mode values automatically
- Empty-state containers that inform the user of a pending action (e.g. "Add an approver to enable submission") MUST use `<div role="status" aria-live="polite">` (not `<p>`) — `role="status"` alone is insufficient; `aria-live="polite"` is required so screen readers announce dynamically-injected empty-state text after data loads; when an icon logically accompanies the message add it with `aria-hidden` on the icon element
- Vitest test files for components that use `usePermissions` MUST add `vi.mock("@/hooks/usePermissions", () => ({ usePermissions: () => ({ can: () => true, canAny: () => true, canAll: () => true, permissions: new Set() }) }))` — without this mock the hook calls `useAuth` which throws "useAuth must be used within an AuthProvider" and all tests in the file fail; tests that specifically verify permission-denied states should override `can` to return `false` for the relevant key
- NEVER ship a hook or service without wiring the UI trigger that surfaces it — before running E2E, verify every "wire the trigger" checklist item in the story's FE task list: a hook that is implemented but never called from a table row / button / interaction is dead code and the E2E test will silently pass against a mock that never exercises the real code path
- In Playwright specs, when mocking two URL shapes for the same resource (e.g. list `**/api/v1/resource*` and detail `**/api/v1/resource/*/drawer`), register the MORE-SPECIFIC pattern AFTER the less-specific one — Playwright uses last-registered-wins precedence, so a broad pattern registered last will intercept all calls including the detail; register broad first, specific second
- When checking whether a paginated collection has ANY items (e.g. to enable/disable a CTA or display an empty state), use `totalElements > 0` from the response — NEVER `data.length > 0`; `data.length` reflects only the CURRENT PAGE, so a user paged past the first page would see a disabled CTA even when items exist on other pages
- Components with multiple `useState` slices that MUST reset when a controlling prop changes (e.g. a `jobId` or `entityId` prop) MUST receive `key={controllingProp}` from their parent — this forces React to unmount and remount on prop change, giving a clean slate; using a `useEffect` to reset each slice manually is fragile and prone to missing states as slices are added
