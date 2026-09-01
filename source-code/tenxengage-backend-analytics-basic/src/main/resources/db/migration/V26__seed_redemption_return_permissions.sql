-- ============================================================
-- F-06 Non-Cash Returns: Permission catalog
-- Note: module.redemption_store already seeded in F-01 V8.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.return.request',
   'Request Redemption Return',
   'Submit, view, and cancel return requests for completed non-cash redemptions',
   'REDEMPTION_ACTIONS', 'ACTION', 410, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(),
   'action.redemption.return.review',
   'Review Return Requests',
   'View, approve, reject, and resolve return requests in the admin queue',
   'REDEMPTION_ACTIONS', 'ACTION', 411, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- F-06: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (
    gen_random_uuid(),
    'redemption_non_cash_returns',
    'Enable non-cash gift-card / prepaid return requests for partners',
    false, true, true, NOW(), NOW(), 'REDEMPTION'
)
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- PARTNER_ADMIN → action.redemption.return.request
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN ('action.redemption.return.request')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- PARTNER_SELLER → action.redemption.return.request
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN ('action.redemption.return.request')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- CLIENT_ADMIN → action.redemption.return.review
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.return.review')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- ACTIVITY_APPROVER → action.redemption.return.review
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'ACTIVITY_APPROVER'
  AND p.permission_key IN ('action.redemption.return.review')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.return.request',
    'action.redemption.return.review'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
