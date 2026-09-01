# Pattern: permissions-and-feature-flags

## When this applies

This pattern applies to **every feature** in TenXEngage. All features introduce new pages, actions, and/or API endpoints — each requires permission declarations and, for new capabilities, a feature flag. Load this pattern on every `create-spec` run.

## Spec authoring guidance

- List every new permission the feature introduces in a table: key, type (`module.*` or `action.*`), description, and which subscription tiers it is available on.
- For each new page, specify the `module.*` permission that gates it and the `ProtectedRoute` wrapper.
- For each new action (button, API operation), specify the `action.*` permission.
- Declare a feature flag if the feature is gated by subscription tier. Specify the three tier booleans using the backend field names: `starterEnabled`, `professionalEnabled`, `enterpriseEnabled` (the DTO record fields on `FeatureFlag`/`CreateFeatureFlagRequest`).
- Never specify role-based checks (e.g., "only admin role can do this"). Permissions are the mechanism — roles and their permission sets are configurable per tenant.
- Include a Flyway migration row for seeding permissions: `V{N+1}__seed_{feature}_permissions.sql`.

## Implementation guidance

### Frontend

#### Permission Types

Two naming prefixes:
- `module.*` — controls access to entire pages/sections (e.g., `module.incentives`)
- `action.*` — controls specific operations within a module (e.g., `action.incentive.create`)

#### usePermissions Hook

The primary API for permission checks in component logic:

```typescript
const { can, canAny, canAll } = usePermissions();

can('action.incentive.create')       // single permission
canAny('action.incentive.create', 'action.incentive.edit')  // OR logic
canAll('action.incentive.create', 'action.incentive.publish') // AND logic
```

Internally builds a `Set` from the user's permission array for O(1) lookups; memoized to avoid recomputing on every render.

#### PermissionGate Component

Declarative permission check in JSX:

```tsx
<PermissionGate permission="action.incentive.create">
  <CreateButton />
</PermissionGate>

<PermissionGate any={['action.incentive.create', 'action.incentive.edit']}>
  <EditPanel />
</PermissionGate>

<PermissionGate all={['action.incentive.view', 'action.incentive.export']} fallback={<NoAccessMessage />}>
  <ExportButton />
</PermissionGate>
```

Props: `permission` (single, string), `any` (array, OR), `all` (array, AND), `fallback` (optional ReactNode).

#### ProtectedRoute

Wrap every new page in `App.tsx`:

```tsx
<ProtectedRoute permission="module.incentives">
  <IncentivesPage />
</ProtectedRoute>
```

If not authenticated → redirect to `/login`. If authenticated but missing permission → redirect to `/home`.

#### Sidebar Navigation

Every new sidebar item must include a `permissionKey` property. `RoleSidebar` filters the navigation tree using `can(item.permissionKey)`.

#### Feature Flags

Feature flags are subscription-tier based. Access via the dedicated `useFeatures` hook (not `useAuth`):

```typescript
const { ai_copilot, course_builder_terminal_save } = useFeatures();
// Returns an object keyed by featureKey with boolean values
```

The `useFeatures` hook reads from `AuthContext.enabledFeatures` internally. Use it directly — do not destructure `enabledFeatures` from `useAuth()` in component code; that coupling was split out into `useFeatures` to keep feature-flag reads stable as the auth context evolves.

Feature flags control whether a feature is available at all; permissions control who within a tenant can use it. Both checks may be needed for a single gate.

### Backend

#### Five-Layer Permission Resolution

Permissions resolve through five layers in order:

| Layer | Source | Effect |
|---|---|---|
| 1 | Platform defaults | Base permissions for each role |
| 2 | Tenant grants (`ClientPermissionGrant`) | Additional permissions granted to a tenant |
| 3 | Role permissions (`ClientRolePermission`) | Permissions assigned to roles within a tenant |
| 4 | Company overrides (`CompanyPermissionOverride`) | Deny-only overrides at partner company level |
| 5 | User overrides (`UserPermissionOverride`) | Deny-only overrides at individual user level |

`PermissionService.resolveEffectivePermissions(userId)` runs this resolution and caches the result per user.

#### Cache Eviction

Cache eviction is scoped:
- User override change → evict that user's cache
- Role change → evict all users with that role
- Company override change → evict all users in that company
- Tenant grant change → evict all users in that tenant

#### @RequiresPermission Annotation

Standard authorization mechanism for all endpoints beyond simple authentication:

```java
@RequiresPermission("action.incentive.create")
public ResponseEntity<IncentiveResponse> createIncentive(...) { ... }

@RequiresPermission(value = {"action.incentive.create", "action.incentive.edit"}, logic = Logic.ANY)
public ResponseEntity<IncentiveResponse> upsertIncentive(...) { ... }
```

Enforced by `PermissionAspect` (AOP). Throws an authorization exception on failure.

#### @PreAuthorize

Use only for simple `isAuthenticated()` checks — endpoints that require a valid JWT but no specific permission. For anything more granular, use `@RequiresPermission`.

#### Entities

| Entity | Purpose |
|---|---|
| `Permission` | Available permission keys (`module.*`, `action.*`) |
| `ClientRolePermission` | Maps permissions to roles within a tenant |
| `ClientPermissionGrant` | Additional permissions granted to a tenant |
| `CompanyPermissionOverride` | Deny-only overrides at company level |
| `UserPermissionOverride` | Deny-only overrides at user level |

#### Hardening Rules

- System role permissions are **immutable** — cannot be modified or deleted via API.
- **Self-lock prevention** — users cannot remove their own permissions.
- **Scope enforcement** — tenants can only assign permissions they already have.

#### Feature Flags (Backend)

| Entity | Purpose |
|---|---|
| `FeatureFlag` | Platform-level flag with `isEnabledForStarter`, `isEnabledForProfessional`, `isEnabledForEnterprise` booleans |
| `ClientFeatureOverride` | Tenant-scoped override (can enable/disable regardless of tier) |

`FeatureFlagService` resolves enabled features by checking the tenant's subscription tier against flag tier booleans, then applying client overrides. Result returned in login and token refresh responses as `enabledFeatures[]`.

#### Rules

- Every new endpoint must have `@RequiresPermission` with the appropriate key.
- New feature flags must define all three tier booleans.
- Permission changes must consider cache eviction scope.

## Pitfalls

### PlatformSettingsPage tab-content permission gaps

Components embedded as tab content inside `PlatformSettingsPage` (e.g. `AudienceFieldsPage`, `CourseBuilderConfigPage`) are only gated by `module.settings.tenx` at the route level. Granular `action.*` permissions (e.g. `action.builder.manage`) are **not** automatically applied to tab-embedded surfaces — the backend enforces them on mutations, but users without the permission will see the UI controls and receive 403 errors.

**When adding a new admin surface to PlatformSettingsPage tabs:** if the component performs privileged mutations gated by an `action.*` permission, add a `PermissionGate` wrapper around the tab content in `PlatformSettingsPage.tsx` or inside the component itself. The standalone deep-link route (in `App.tsx`) should also remain gated via `ProtectedRoute`.

## Examples in codebase

- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/PermissionService.java` — 5-layer resolution + cache eviction
- `../tenxengage-backend/src/main/java/com/tenxengage/app/aspect/PermissionAspect.java` — AOP enforcement of `@RequiresPermission`
- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/FeatureFlagService.java` — tier-based flag resolution
- `../tenxengage-frontend/src/hooks/usePermissions.ts` — Set-based O(1) permission lookup
- `../tenxengage-frontend/src/components/PermissionGate.tsx` — declarative JSX gate
- `../tenxengage-frontend/src/components/ProtectedRoute.tsx` — route-level guard

## Common gotchas

- **Never hardcode role checks.** Roles and their permission sets are configurable per tenant. Always check permission keys, not role names.
- **`module.*` and `action.*` both need to be checked.** A user may have `action.incentive.create` but not `module.incentives`. The `ProtectedRoute` checks the module permission; individual actions check action permissions. Both must be specified.
- **Feature flags gate availability; permissions gate access.** A feature can be enabled for a tenant but a specific user may lack the permission to use it. Check both if the feature has a flag.
- **Every new sidebar item needs a `permissionKey`.** Items without `permissionKey` are always shown — an easy way to accidentally expose navigation to unauthorized users.
- **Cache eviction scope must match the change scope.** A role change that evicts only one user is a bug. Use the table above to determine the correct eviction target.
- **`@PreAuthorize` is not a substitute for `@RequiresPermission`.** `@PreAuthorize(isAuthenticated())` only checks JWT validity, not permission. New endpoints with action-level requirements need `@RequiresPermission`.
- **New feature flags require all three tier booleans.** A flag with only `isEnabledForEnterprise = true` will resolve as disabled for Professional tenants even if the intent was to enable it for all paid tiers.
- **Tier changes must evict both `effectivePermissions` AND `enabledFeatures` caches.** `effectivePermissions` is keyed by userId (use `cache.clear()`); `enabledFeatures` is keyed by clientId (use `cache.evict(clientId)`). Evicting only one leaves stale state in the other.
- **Gate data-fetching sections, not just mutation CTAs.** Components that call permission-sensitive API endpoints (e.g. polling monitors, detail panels) must be gated with `can('action.*')` before rendering — not just wrapped in a `PermissionGate` around the visible CTA. If the component is allowed to mount, it fires the request and receives a 403 before the user sees anything actionable. Pattern: derive `const canView = can('action.x.view')` in the parent page and conditionally render `{canView && <MonitorComponent />}`. Example: `AssignmentsConsolePage` — `MaterializationJobMonitor` gated by `canViewEnrollments = can('action.enrollment.view')` added in US-16 followup.
- **Gate nav items and routes on the specific capability, not a coarse `MODULE_ACCESS` umbrella.** A `module.*` umbrella is often granted to several roles for unrelated reasons (e.g. `module.redemption_store` is held by Client Admin for catalog/wallet access). Gating a *surface* like the storefront on the umbrella then exposes it to roles that hold the umbrella but lack the real capability (Client Admin cannot redeem). Gate on the action permission(s) that actually unlock the surface; for an OR of capabilities use the sidebar `NavItem.anyPermission` and `ProtectedRoute anyPermission={[...]}`. (redemption-store storefront re-gated to `action.redemption.redeem` | `action.redemption.redeem_company`; FE UX enhancements US-04 / CR-04, 2026-06-30.)
