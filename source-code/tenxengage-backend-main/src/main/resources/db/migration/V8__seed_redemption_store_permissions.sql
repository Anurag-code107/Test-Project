-- ============================================================
-- Redemption Store: Permission catalog (F-01 subset)
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(), 'module.redemption_store',            'Redemption Store',            'Access to Redemption Store module',                          'MODULE_ACCESS',      'MODULE', 400, NOW(), NOW(), 'ALL'),
  (gen_random_uuid(), 'action.redemption.view_history',     'View Redemption History',     'View own redemption transaction history and wallet balances', 'REDEMPTION_ACTIONS', 'ACTION', 401, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(), 'action.redemption.view_all_history', 'View All Redemption History', 'View all tenant redemption history and wallet balances',      'REDEMPTION_ACTIONS', 'ACTION', 402, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- Redemption Store: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (gen_random_uuid(), 'redemption_store', 'Enables Redemption Store — wallet, catalog, and redemption flow', true, true, true, NOW(), NOW(), 'REWARDS')
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- Redemption Store: Role grants
-- ============================================================

-- PARTNER_SELLER
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN (
    'module.redemption_store',
    'action.redemption.view_history'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- PARTNER_ADMIN
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN (
    'module.redemption_store',
    'action.redemption.view_history'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- CLIENT_ADMIN
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN (
    'module.redemption_store',
    'action.redemption.view_all_history'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- Acme tenant seed grants (dev/seed only)
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'module.redemption_store',
    'action.redemption.view_history',
    'action.redemption.view_all_history'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
