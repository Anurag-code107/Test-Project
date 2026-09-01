# Step 11: permissions-analysis

## Goal
Build the complete permissions matrix and feature flag plan. These feed spec.md (matrix) and technical.md (Flyway migration SQL).

## Inputs (from prior steps)
- Locked FRs and scope
- Loaded `permissions-and-feature-flags.md` (from step 06; this pattern is ALWAYS-on)

## Loads (just-in-time)
- None (uses already-loaded context)

## Procedure

1. **Module permissions.** One `module.*` key per sidebar page or section this feature adds (e.g., `module.enablement_courses`). Scope:
   - `INTERNAL` if only client-level roles see it
   - `EXTERNAL` if only partner roles
   - `ALL` if both

2. **Action permissions.** One `action.{entity}.{verb}` key per distinct operation:
   - Standard verbs: `view`, `create`, `edit`, `delete`
   - Domain-specific verbs as needed: `publish`, `approve`, `submit`, `manage`, etc.
   - Follow the naming convention from existing permissions (audit `tenxengage-backend/src/main/resources/db/migration/V*.sql` for examples).

3. **Permission matrix.** Build a grid: 4 default roles × each permission key.
   - `CLIENT_ADMIN` — typically all module + action permissions for features they manage
   - `ACTIVITY_APPROVER` — typically view + approve actions only
   - `PARTNER_ADMIN` — typically view + participate actions
   - `PARTNER_SELLER` — typically view + self-service actions only

   Mark each cell `Y` (granted) or `—` (not granted).

4. **Feature flag.** Determine:
   - Feature key (snake_case)
   - Description
   - Tier availability: `starter` (basic), `professional` (mid), `enterprise` (full)
   - Most new features are `false / true / true` or `false / false / true`.

5. **Flyway migration plan.** Plan one migration file with:
   - INSERT into `permissions` (with `ON CONFLICT (permission_key) DO NOTHING`)
   - INSERT into `feature_flags`
   - Per-role INSERTs into `client_role_permissions` linking `client_roles.id` to each permission key with `granted = true`
   - The actual SQL is written by step 14.

## Rules (scoped to this step)
- Every endpoint MUST have `@RequiresPermission`. Every page MUST have `ProtectedRoute`. Every sidebar item MUST have `permissionKey`. The matrix here drives all three.
- The matrix's `Y` / `—` columns are the source of truth for the Flyway seed SQL written in step 14.
- Add rows for every non-CRUD verb identified — the template's CRUD rows are examples only.

## User interaction
None.

## Output for downstream steps
- Permission matrix table (rows = permission keys, columns = roles)
- Feature flag spec (key, tiers)
- Flyway migration plan (description, not SQL — SQL is step 14's job)

## Boundary
Permissions matrix locked → route to step 12: read steps/step-12-derive-slug.md`.