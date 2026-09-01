# Permissions and Feature Flags Pattern — Backend

> **Cross-ref:** For the unified pattern (spec authoring guidance, frontend hooks and components),
> see [tenxengage-blueprint/docs/patterns/permissions-and-feature-flags.md](../../../tenxengage-blueprint/docs/patterns/permissions-and-feature-flags.md).
> This file covers backend-only implementation details.



How the backend implements 5-layer permission resolution and subscription-tier feature flags. Implementation: [PermissionService.java](../../src/main/java/com/tenxengage/app/service/PermissionService.java), [PermissionAspect.java](../../src/main/java/com/tenxengage/app/security/PermissionAspect.java), [FeatureFlagService.java](../../src/main/java/com/tenxengage/app/service/FeatureFlagService.java).

## Permission keys

Flat strings, two prefixes:

- `module.*` — page-level access (`module.incentives`, `module.users`)
- `action.*` — operations (`action.incentive.create`, `action.users.delete`)

## 5-layer resolution

`PermissionService.resolveEffectivePermissions(userId)` is `@Cacheable` and runs this chain. **Each subsequent layer can only restrict, never expand.**

| Layer | Entity | Purpose | Grant? | Deny? |
|---|---|---|---|---|
| 0 — Tenant Grants | `ClientPermissionGrant` | Tenant subscription scope | Yes | Yes |
| 1 — Role Permissions | `ClientRolePermission` | Permissions on the user's role | Yes | Yes |
| 2 — Company Overrides | `CompanyPermissionOverride` | Restrict for a partner company | No | Yes |
| 3 — User Overrides | `UserPermissionOverride` | Restrict for a single user | No | Yes |

Algorithm: `effective = role ∩ tenant; effective -= companyDenials; effective -= userDenials`. Layers 0 and 1 are intersected via `retainAll` — **a permission missing from either is silently dropped**. This is the most common source of "user has the role but not the permission" bugs (see Flyway checklist below).

`TENX_ADMIN` users (`clientId == null && clientRoleId == null`) bypass the chain entirely and receive all permissions.

## `@RequiresPermission` annotation

Primary authorization mechanism. Custom annotation enforced by `PermissionAspect` (AOP `@Before` advice). Every non-public endpoint must use it.

```java
public @interface RequiresPermission {
    String[] value();
    Logic logic() default Logic.ANY;
    enum Logic { ANY, ALL }
}
```

Usage:

```java
@RequiresPermission("action.incentive.create")                        // single
@RequiresPermission(value = {"action.claim.view", "action.claim.approve"})              // ANY (default)
@RequiresPermission(value = {"action.data.manage", "action.integrations.manage"},
                    logic = RequiresPermission.Logic.ALL)             // ALL
```

### Tier-gated module endpoints — dual-guard requirement

Every endpoint behind a subscription-tier-gated module **must check both the module permission AND the action permission** using `Logic.ALL`. A single `action.*` check is insufficient: a user can have the action permission from their role, but if the tenant's subscription doesn't include the module, both Layer 0 `module.*` and the action must be present before access is granted.

```java
// WRONG — user with action.assessment.view can access even on STARTER tier (module not checked)
@RequiresPermission("action.assessment.view")

// CORRECT — both must be in effective permissions; STARTER tier has neither
@RequiresPermission(
    value = {"module.assessment_authoring", "action.assessment.view"},
    logic = RequiresPermission.Logic.ALL
)
```

This applies to every CRUD operation on tier-gated resources: all assessment, question, and question-bank endpoints require `module.assessment_authoring` AND the specific action key.

The aspect resolves the user's effective permissions, applies ANY/ALL logic, throws `AccessDeniedException` on failure, and emits Micrometer metrics (`PermissionMetrics`) for resolution latency and denied-access events.

### Startup safety net

`EndpointSecurityValidator` runs at startup and **fails the application boot** if any non-public controller endpoint lacks `@RequiresPermission`. Don't disable this — it's the only thing preventing accidentally-shipped unprotected endpoints.

### When to use `@PreAuthorize`

Only for `@PreAuthorize("isAuthenticated()")` on endpoints that need a valid JWT but no specific permission (e.g. user-self health probes). Everything else uses `@RequiresPermission`.

## Hardening rules

Three independent guardrails in the permission-management endpoints — they all live in the assignment/update flow:

- **Immutable `CLIENT_ADMIN` permissions** — `PermissionConstants.IMMUTABLE_ADMIN_PERMISSIONS` cannot be removed from the system Client Admin role. Throws `BusinessRuleException`.
- **Self-lock prevention** — a user cannot remove `PermissionConstants.SELF_LOCK_CRITICAL_PERMISSIONS` from their own role. Computed via `retainAll` of "would-remove" against the critical set; non-empty intersection ⇒ rejected with "Have another admin make this change."
- **Scope enforcement** — `Permission.scope` is `INTERNAL`/`EXTERNAL`/`ALL`. Permissions can only be assigned to roles whose `roleType` matches the scope. Additionally, **a permission can only be granted to a role if Layer 0 has it** — guards against assigning permissions the tenant doesn't actually have.

All three throw `BusinessRuleException`. Don't silently no-op.

## Cache strategy

`@Cacheable(value = "effectivePermissions", key = "#userId")` on the resolution method. Eviction granularity matters — over-evicting causes thundering-herd resolves on next request:

- User override changed → evict that one user
- Role permission changed → evict every user assigned to that role (`findByClientRoleId` + iterate)
- Company override changed → evict every user in that partner company
- Tenant Layer-0 grants changed → clear the entire `effectivePermissions` cache

Use `cacheManager.getCache("effectivePermissions").evict(userId)`. **Never use `@CacheEvict(allEntries = true)` for user-level changes** — it nukes every other tenant's cache.

## Feature flags

Independent of permissions. Subscription-tier-driven.

- `FeatureFlag` (platform-level) — `featureKey` + three booleans: `starterEnabled`, `professionalEnabled`, `enterpriseEnabled`.
- `ClientFeatureOverride` (tenant-scoped, `TenantAware`) — overrides the tier default for a specific tenant.

`FeatureFlagService.getEnabledFeatures(clientId)` is `@Cacheable(value = "enabledFeatures", key = "#clientId")`. For each flag: if a `ClientFeatureOverride` exists, it wins (true → enabled, false → disabled regardless of tier); otherwise check the tier boolean against `client.subscriptionTier`.

The result list is delivered as `enabledFeatures[]` in the login and token-refresh responses. Frontend checks via `enabledFeatures.includes('key')` — no separate endpoint call.

Eviction: flag tier change ⇒ `@CacheEvict(allEntries = true)`; client override change ⇒ `@CacheEvict(key = "#clientId")`.

## Rules for new permissions

1. Every new endpoint gets `@RequiresPermission`. The startup validator will fail the build if you forget.
2. Define the permission key in the `permissions` Flyway seed.
3. Naming: `module.{area}` for pages, `action.{area}.{verb}` for operations. Lowercase, dot-separated.
4. **The two-block Flyway requirement** — every new permission needs grants at both Layer 0 (tenant) AND Layer 1 (role). The intersection logic in `resolveEffectivePermissions` silently drops permissions missing from either layer.
5. Never hardcode role-name checks. Always use permission keys.

## Flyway migration template — new permission

Every migration that adds permissions needs **all three blocks**. Missing block 3 is the #1 cause of "permission visible in DB but not in login response":

```sql
-- 1. Add to the permissions catalog
INSERT INTO permissions (id, permission_key, ...) VALUES (...) ON CONFLICT (permission_key) DO NOTHING;

-- 2. Layer 1 — grant to relevant roles (client_role_permissions)
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, ...)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr CROSS JOIN permissions p
WHERE cr.base_role_name = 'ROLE_NAME'
  AND p.permission_key IN ('new.permission.key', ...)
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- 3. Layer 0 — grant to all existing tenants (client_permission_grants)
--    !! REQUIRED. Without this row, the permission is invisible at login
--    !! regardless of what the role has, because resolveEffectivePermissions
--    !! intersects tenant grants with role grants.
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, ...)
SELECT gen_random_uuid(), c.id, p.permission_key, true, NOW(), NOW()
FROM clients c CROSS JOIN permissions p
WHERE p.permission_key IN ('new.permission.key', ...)
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

> **How the bug surfaces:** user's role has the permission in `client_role_permissions`, but `user.permissions` in the login response doesn't include it. Root cause is always a missing `client_permission_grants` row for the user's tenant (Layer 0 absent → intersection empty).

### Flyway migration template — tier-gated permission

When a feature is gated by subscription tier (not available on STARTER), block 3 of the migration must filter to only the tiers that include it. Using the all-tenants template without a tier filter provisions STARTER tenants with grants they shouldn't have.

```sql
-- 3. Layer 0 — grant to PROFESSIONAL and ENTERPRISE tenants only
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), c.id, p.permission_key, true, NOW(), NOW()
FROM clients c CROSS JOIN permissions p
WHERE c.subscription_tier IN ('PROFESSIONAL', 'ENTERPRISE')
  AND c.status = 'ACTIVE'
  AND p.permission_key IN (
    'module.assessment_authoring',
    'action.assessment.view',
    -- ... full list of tier-gated permission keys
  )
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

The tier filter in Flyway covers **existing tenants at migration time**. New tenants created after migration must be provisioned at runtime by `ClientPermissionGrantSeedService.seedForTier()`.

## Tier-lifecycle management for tier-gated features

`ClientPermissionGrantSeedService.seedForTier(clientId, tier)` is the runtime authority for Layer-0 grants scoped to subscription tier. It must be called from **exactly these three call sites** to cover the full lifecycle — missing any one of them creates a billing bypass or a silent permission gap:

| Trigger | Call site | Expected behaviour |
|---|---|---|
| New client created | `ClientService.createClient()` after `save()` | Seeds grants for the initial tier |
| Client upgraded (tier changed to PRO/ENTERPRISE) | `ClientService.updateClient()` when `tierChanged == true` | Adds missing grants for the new tier |
| Client downgraded (tier changed to STARTER or null) | Same `updateClient()` path | Deletes tier-gated grants; no billing bypass |
| Cache eviction | After any `seedForTier()` call | `effectivePermissions` cache must be cleared (`cacheManager.getCache(...).clear()`) |

**Only call `seedForTier()` when the tier actually changed** on updates — calling it unconditionally on every `updateClient()` triggers unnecessary deletes/queries on every name or logo change.

```java
// CORRECT — scoped to tier transitions only
boolean tierChanged = request.subscriptionTier() != null
        && request.subscriptionTier() != client.getSubscriptionTier();
if (tierChanged) {
    client.setSubscriptionTier(request.subscriptionTier());
    // ... save ...
    clientPermissionGrantSeedService.seedForTier(updated.getId(), updated.getSubscriptionTier());
    evictEffectivePermissionsCache();
}
```

`seedForTier()` is idempotent for both directions: calling it twice for the same tier and client is safe (grants already-present keys are skipped; deletes are idempotent).

## Known architectural gap — new client provisioning

`ClientService.createClient()` does **not** provision `client_roles`, `client_role_permissions` (Layer 1), or baseline `client_permission_grants` for non-tier-gated permissions. The Acme seed tenant is fully provisioned by V3 Flyway with hardcoded UUIDs; API-created tenants receive only: currencies, tag namespaces, and assessment-authoring Layer-0 grants (from `seedForTier`).

This means: a user assigned to a new API-created tenant has no roles and no effective permissions for any non-assessment-authoring feature. The intersection of Layer 0 (assessment grants only) ∩ Layer 1 (empty — no roles) = empty.

**Mitigation until a `ClientRoleSeedService` is built:** create new production tenants directly via Flyway migration with explicit UUID provisioning, following the V3 pattern. Do not use the admin API for production tenant onboarding until this gap is closed.

## Rules for new feature flags

1. Create a `FeatureFlag` row via Flyway migration.
2. Set all three tier booleans explicitly — don't rely on column defaults.
3. Feature keys: descriptive, lowercase, dot-separated (`ai.copilot`, `analytics.advanced`).
4. No backend endpoint needed after login — frontend uses the `enabledFeatures[]` it received.
