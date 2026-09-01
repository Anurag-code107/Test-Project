-- ============================================================
-- Redemption Catalog F-02: Permission additions
-- Note: module.redemption_store, action.redemption.view_history,
--       action.redemption.view_all_history seeded in F-01 V8.
-- ============================================================
INSERT INTO permissions (
    id, permission_key, display_name, description,
    category, permission_type, sort_order, created_at, updated_at, scope
)
VALUES
  (gen_random_uuid(),
   'action.redemption.catalog.manage',
   'Manage Redemption Catalog',
   'Create, edit, activate, and deactivate global redemption catalog items; trigger Xoxoday catalog sync',
   'REDEMPTION_ACTIONS', 'ACTION', 810, NOW(), NOW(), 'PLATFORM'),
  (gen_random_uuid(),
   'action.redemption.configure',
   'Configure Tenant Catalog',
   'Enable/disable catalog items for tenant, override processing modes and thresholds, configure regional availability and batch cadence',
   'REDEMPTION_ACTIONS', 'ACTION', 811, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- action.redemption.catalog.manage is PLATFORM scope — no client_role_permissions row.
-- Granted only via TENX_ADMIN platform-level check.

-- action.redemption.configure → CLIENT_ADMIN only
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN (
    'action.redemption.configure'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- Acme tenant seed grant (dev/seed only) — mirrors V8 pattern for redemption store permissions
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', 'action.redemption.configure', true, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM client_permission_grants
    WHERE client_id = 'a0000000-0000-0000-0000-000000000001'
      AND permission_key = 'action.redemption.configure'
);
