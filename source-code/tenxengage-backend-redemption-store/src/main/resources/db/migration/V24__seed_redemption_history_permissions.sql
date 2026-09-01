-- ============================================================
-- Redemption History (F-05): new export permission
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(), 'action.redemption.export', 'Export Redemption History', 'Export redemption transaction history as CSV or XLSX', 'REDEMPTION_ACTIONS', 'ACTION', 405, NOW(), NOW(), 'ALL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- PARTNER_SELLER
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN ('action.redemption.export')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- PARTNER_ADMIN
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN ('action.redemption.export')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- CLIENT_ADMIN
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.export')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN ('action.redemption.export')
ON CONFLICT (client_id, permission_key) DO NOTHING;
