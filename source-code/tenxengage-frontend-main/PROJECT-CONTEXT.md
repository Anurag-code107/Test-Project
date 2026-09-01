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
- Always call `queryClient.invalidateQueries` in `useMutation.onSuccess`
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
- Contracts are source of truth for API shapes — always check `contracts/` before creating types or services. TypeScript interfaces MUST match contracts exactly.
- Tokens stored in HTTPOnly cookies set by the backend. The axios instance uses `withCredentials: true` so the browser sends them automatically — never in localStorage, sessionStorage, or JS-accessible memory.
- Reference `../ai-incentive-pilot` for styling inspiration only — NEVER edit files in that repo.

## Adding New Features
- **New page:** Create in `src/pages/`, add route in `App.tsx`, add sidebar link in `Sidebar.tsx`
- **New API endpoint:** Check `contracts/` first → types in `src/types/` → service in `src/services/` → TanStack Query hook in `src/hooks/`
- **New UI component:** Create in `src/components/` with Props interface; for shadcn components use `npx shadcn-ui add <component>`

## Pattern References
Before starting work that involves these areas, read the relevant pattern file:
- Page headers & layout conventions → `docs/patterns/page-layout.md`
- Builder/wizard components → `docs/patterns/builder-widget.md`
- Dynamic builder configuration → `docs/patterns/builder-config.md`
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

## Accessibility Conventions
- When a page has two or more buttons with the same visible text, add differentiated `aria-label` attributes so screen readers can distinguish them.
- Tailwind's `animate-pulse` is NOT automatically covered by the project's `prefers-reduced-motion` CSS block — add `.animate-pulse` to that block in `src/index.css` whenever you use it for skeleton loaders.
- Layout wrapper components that render a `<main>` element must include `aria-label="Main content"` so screen reader users can identify and navigate to the primary content region.

## Anti-Patterns
- NEVER fetch data with `useEffect + useState` — use TanStack Query hooks exclusively
- NEVER import directly from `@radix-ui/*` — always use the shadcn/ui wrapper in `src/components/ui/`
- NEVER use `window.location` for navigation — use `useNavigate()` from React Router
- NEVER use `z.coerce.number().optional()` alone for optional numeric override fields — empty string coerces to `0`, silently overwriting inherited defaults. Use `z.preprocess((v) => (v === "" || v == null ? undefined : v), z.coerce.number().min(0).optional())` instead. See `docs/patterns/form-handling.md`.
- NEVER import lucide icons via ESM sub-paths (e.g. `import Loader2 from "lucide-react/dist/esm/icons/loader-2"`) — those sub-paths lack TypeScript declarations in this tsconfig and break `tsc -b`. Always import from the package root: `import { Loader2 } from "lucide-react"`.
