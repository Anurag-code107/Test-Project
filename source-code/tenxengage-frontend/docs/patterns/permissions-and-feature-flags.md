# Permissions and Feature Flags Pattern — Frontend

> **Cross-ref:** For the unified pattern (spec authoring guidance, backend 5-layer resolution),
> see [tenxengage-blueprint/docs/patterns/permissions-and-feature-flags.md](../../../tenxengage-blueprint/docs/patterns/permissions-and-feature-flags.md).
> This file covers frontend-only implementation details (hooks, components, feature flag gating).



This document describes how the frontend implements permission-based access control and feature flag gating. The frontend never resolves permissions itself — it receives a flat array of permission strings from the backend and uses hooks and components to gate UI elements.

## Permission Model

The backend resolves a 5-layer permission hierarchy (platform defaults, tenant grants, role permissions, company overrides, user overrides) and delivers the result to the frontend as a flat `string[]` on the authenticated user object. The frontend treats this array as the complete, authoritative set of permissions for the current user. It never needs to understand layers, inheritance, or resolution logic.

Permission strings follow a two-part convention: `module.*` keys control page-level access (e.g., `module.incentives`, `module.users`, `module.reports`), while `action.*` keys control specific operations (e.g., `action.incentive.create`, `action.incentive.edit`, `action.user.delete`). Module permissions determine whether a user can see a page at all; action permissions determine which buttons, forms, and operations are available within that page.

## usePermissions Hook

The `usePermissions()` hook is the primary API for checking permissions in component logic. It reads the current user's permissions from AuthContext and provides three check functions:

- **`can(key)`** — Returns `true` if the user has the exact permission string. Used for single-permission checks like `can('action.incentive.create')`.
- **`canAny(...keys)`** — Returns `true` if the user has at least one of the provided permissions (OR logic). Useful when multiple permissions could grant access to the same UI element.
- **`canAll(...keys)`** — Returns `true` if the user has every one of the provided permissions (AND logic). Used when an action requires multiple permissions simultaneously.

Internally, the hook converts the permissions array into a `Set` for O(1) lookup performance. The Set is memoized so it is not recreated on every render. Always use this hook rather than manually checking the permissions array — it provides consistent, optimized access.

```tsx
const { can, canAny } = usePermissions();

if (can('action.incentive.create')) {
  // show create button
}
```

## PermissionGate Component

The `<PermissionGate>` component provides declarative permission checking in JSX. It renders its children only if the permission check passes. It accepts three props for different check modes:

- **`permission`** — A single permission string. Children render if the user has this permission.
- **`any`** — An array of permission strings. Children render if the user has at least one (OR logic).
- **`all`** — An array of permission strings. Children render if the user has all of them (AND logic).
- **`fallback`** — Optional. A React node to render when the permission check fails. Defaults to rendering nothing.

Use PermissionGate to wrap UI elements that should only appear for authorized users. This is preferred over conditional rendering with `usePermissions()` when the gating is purely about visibility in the template.

```tsx
<PermissionGate permission="action.incentive.create">
  <Button onClick={handleCreate}>Create Incentive</Button>
</PermissionGate>

<PermissionGate any={['action.incentive.edit', 'action.incentive.manage']} fallback={<ReadOnlyView />}>
  <EditForm />
</PermissionGate>
```

## ProtectedRoute

The `<ProtectedRoute>` component wraps route definitions in `App.tsx` to enforce page-level access control. It checks permissions before rendering the route's element. If the check fails, it redirects the user — to `/login` if they are unauthenticated, or to `/home` if they are authenticated but lack the required permission.

ProtectedRoute accepts two props:

- **`permission`** — A single permission string required to access the route.
- **`anyPermission`** — An array of permission strings; access is granted if the user has at least one.

Every new page added to the application must be wrapped in a ProtectedRoute with the appropriate module permission. Do not create unprotected routes for any page that displays tenant data.

```tsx
<Route
  path="/incentives"
  element={
    <ProtectedRoute permission="module.incentives">
      <IncentivesPage />
    </ProtectedRoute>
  }
/>
```

## Sidebar Navigation

Sidebar navigation items include a `permissionKey` property in their configuration. The RoleSidebar component filters the navigation items using `usePermissions().can()`, only rendering items where the user has the required permission. This ensures the sidebar only shows pages the user can actually access.

When adding a new page, always add the corresponding `permissionKey` to the navigation item configuration. If a user does not have the permission, the nav item is hidden entirely — there is no disabled/grayed-out state for navigation items.

## Feature Flags

Feature flags control access to entire features based on the tenant's subscription tier (STARTER, PROFESSIONAL, ENTERPRISE). Unlike permissions which are user-scoped, feature flags are tenant-scoped — either the whole tenant has access to a feature or it does not.

The enabled feature flags are returned as an `enabledFeatures[]` string array in the login and token refresh responses. The AuthContext stores this array and makes it available throughout the application.

### useFeatures Hook

Always use the `useFeatures()` hook rather than reading `enabledFeatures` directly from `useAuth()`. The hook wraps the array in a `Set` for O(1) lookup and provides three check functions:

```tsx
const { has, hasAny, hasAll } = useFeatures();

if (has('ai_copilot')) { /* ... */ }
if (hasAny('ai_copilot', 'ai_forecasting')) { /* at least one */ }
if (hasAll('ai_copilot', 'ai_forecasting')) { /* both required */ }
```

Use `useFeatures` in component logic (event handlers, derived state). For JSX-level visibility gating use the same hook inline — there is no `FeatureGate` component (use `useFeatures().has()` in a ternary).

### Disabled Button with Tooltip for Feature-Gated UI

When a UI control is visible but unavailable because a feature flag is off, disable the button and show a tooltip explaining why. **Do not hide the button entirely** — the user should know the feature exists and how to get it.

Radix does not fire pointer events on a `disabled` element, so the tooltip trigger needs a `<span>` wrapper:

```tsx
import {
  Tooltip, TooltipContent, TooltipProvider, TooltipTrigger,
} from "@/components/ui/tooltip";
import { useFeatures } from "@/hooks/useFeatures";

const { has } = useFeatures();
const aiEnabled = has("ai_copilot");

<TooltipProvider>
  <Tooltip>
    <TooltipTrigger asChild>
      <span>
        <button
          type="button"
          disabled={!aiEnabled}
          onClick={() => { if (!aiEnabled) return; /* ... */ }}
          className={cn(
            "...",
            !aiEnabled ? "text-muted-foreground/50 cursor-not-allowed" : "...",
          )}
        >
          AI Mode
        </button>
      </span>
    </TooltipTrigger>
    {!aiEnabled && (
      <TooltipContent side="bottom" className="text-xs max-w-[240px]">
        AI Mode isn&apos;t included in your subscription plan.
        Contact your account admin to upgrade.
      </TooltipContent>
    )}
  </Tooltip>
</TooltipProvider>
```

Key points:
- `<span>` wrapper inside `TooltipTrigger asChild` — required because Radix's tooltip relies on pointer events which disabled HTML elements suppress.
- Guard the `onClick` with `if (!featureEnabled) return` — belt-and-suspenders against JS invocation even when the button is disabled.
- `TooltipContent` is conditionally rendered (`{!featureEnabled && ...}`) so no tooltip fires when the button is active.
- Use native `<button type="button">` (not shadcn `<Button>`) when the styling departs from shadcn defaults (e.g. mode toggle pills in builders).

Feature flag checks are simpler than permission checks because they are a flat includes check with no OR/AND logic needed. Use feature flags to gate access to premium features, beta functionality, or features that are being progressively rolled out across subscription tiers.

## Rules

Follow these rules when working with permissions and feature flags:

1. **Every new page must have a ProtectedRoute.** No exceptions. Define the module permission key and wrap the route.
2. **Every new action must have a PermissionGate.** Any button, form, or operation that modifies data should be gated with the appropriate action permission.
3. **Every new nav item must have a permissionKey.** The sidebar must stay in sync with route protection.
4. **Never hardcode role checks.** Do not check if a user's role is "admin" or "manager" — always check permissions. Roles can be customized per tenant, so role names are not reliable indicators of access.
5. **Feature flags for feature-level gating, permissions for user-level gating.** Use feature flags when the question is "does this tenant have this feature?" and permissions when the question is "can this user perform this action?"
6. **Use the hook for logic, the component for JSX.** Use `usePermissions()` in event handlers and conditional logic. Use `<PermissionGate>` in the render template for visibility gating.
7. **Page-level permission ≠ detail-level permission — check independently.** Admin list pages and their detail panels can require *different* permissions. A user who can access the list page (e.g. `action.redemption.view_all_history`) may not have the permission required to call the detail API (e.g. `action.redemption.view_history`). Before enabling row-click interactions or mounting a detail sheet, always call `can("action.X.detail_perm")` from `usePermissions()` and make the interaction conditional. Do not assume that having the page permission implies having any downstream action permission.
8. **Update HomeRedirect when adding a new module permission.** See Pitfalls below.

## Pitfalls

### HomeRedirect must be updated when a new module permission is introduced

`HomeRedirect` (`src/components/HomeRedirect.tsx`) contains a priority-ordered `HOME_ROUTES` list that maps module permissions to their post-login landing path. Whenever a new `module.*` permission is introduced as the primary access for a new role (e.g. `module.assessment_authoring` for `ENABLEMENT_ADMIN`), you **must** add a corresponding entry to that list.

**What breaks if you omit it:** The user logs in, hits `/`, and `HomeRedirect` falls through all `HOME_ROUTES` checks with no match. It then falls back to its final `<Navigate>` — historically `/settings/profile`, a `ProtectedRoute`-guarded path. `ProtectedRoute` denies access and redirects to `/`, which triggers `HomeRedirect` again. The result is an infinite redirect loop that renders as a blank screen.

**Current `HOME_ROUTES` order** (priority top → bottom):
```
module.home               → /home
module.activity_review    → /activity-review
module.incentives.sales   → /incentives
module.rewards.claims     → /rewards
module.assessment_authoring → /assessments
module.settings.profile   → /settings/profile
```

**The final fallback must always be an unguarded route.** `ProtectedRoute` redirects failed checks to `/`, which re-enters `HomeRedirect`. If the fallback is itself a guarded route, the loop is infinite. The fallback is currently `/403`, which is always reachable.

**Checklist when seeding a new role with module permissions:**
- [ ] Add the `module.*` permission to the permission catalog (DB migration)
- [ ] Assign it in the role's `client_role_permissions` seed block
- [ ] Add `{ permission: "module.your_permission", path: "/your-path" }` to `HOME_ROUTES` in `HomeRedirect.tsx`
- [ ] Wrap the new route in `App.tsx` with `<ProtectedRoute permission="module.your_permission" />`
