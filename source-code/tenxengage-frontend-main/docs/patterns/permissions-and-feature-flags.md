# Permissions and Feature Flags Pattern — Frontend

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

The enabled feature flags are returned as an `enabledFeatures[]` string array in the login and token refresh responses. The AuthContext stores this array and makes it available throughout the application. To check if a feature is enabled:

```tsx
const { enabledFeatures } = useAuth();

if (enabledFeatures.includes('ai_copilot')) {
  // show AI copilot button
}
```

Feature flag checks are simpler than permission checks because they are a flat includes check with no OR/AND logic needed. Use feature flags to gate access to premium features, beta functionality, or features that are being progressively rolled out across subscription tiers.

## Rules

Follow these rules when working with permissions and feature flags:

1. **Every new page must have a ProtectedRoute.** No exceptions. Define the module permission key and wrap the route.
2. **Every new action must have a PermissionGate.** Any button, form, or operation that modifies data should be gated with the appropriate action permission.
3. **Every new nav item must have a permissionKey.** The sidebar must stay in sync with route protection.
4. **Never hardcode role checks.** Do not check if a user's role is "admin" or "manager" — always check permissions. Roles can be customized per tenant, so role names are not reliable indicators of access.
5. **Feature flags for feature-level gating, permissions for user-level gating.** Use feature flags when the question is "does this tenant have this feature?" and permissions when the question is "can this user perform this action?"
6. **Use the hook for logic, the component for JSX.** Use `usePermissions()` in event handlers and conditional logic. Use `<PermissionGate>` in the render template for visibility gating.
