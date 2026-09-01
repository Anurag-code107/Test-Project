-- ============================================================
-- Client admins now OWN catalog management (confirmed 2026-07-22). Previously
-- action.redemption.catalog.manage was PLATFORM/TENX-admin only (V12) and existed
-- for Client Admin only as an un-migrated hand-added dev-DB grant. This migration
-- makes the grant reproducible on a fresh DB.
--
-- Layer-0 strips a permission unless it is present in BOTH client_role_permissions
-- AND client_permission_grants, so seed both (mirrors V12's action.redemption.configure).
-- ============================================================

-- Re-scope from PLATFORM to INTERNAL — it is now a client-admin capability, not platform-only.
UPDATE permissions
SET scope = 'INTERNAL', updated_at = NOW()
WHERE permission_key = 'action.redemption.catalog.manage';

-- Role-level grant for every CLIENT_ADMIN role.
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, 'action.redemption.catalog.manage', true, NOW(), NOW()
FROM client_roles cr
WHERE cr.base_role_name = 'CLIENT_ADMIN'
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- Client-level grant (Acme/demo tenant) — mirrors V12's seed pattern.
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'action.redemption.catalog.manage', true, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM client_permission_grants
    WHERE client_id = 'a0000000-0000-0000-0000-000000000001'
      AND permission_key = 'action.redemption.catalog.manage'
);
