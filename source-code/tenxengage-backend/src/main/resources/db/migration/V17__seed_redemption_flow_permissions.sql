-- ============================================================
-- F-03 Redemption Flow: New action permissions
-- Note: module.redemption_store, action.redemption.view_history,
--       action.redemption.view_all_history seeded in F-01 V8.
--       action.redemption.configure, action.redemption.catalog.manage seeded in F-02 V12.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.redeem',
   'Redeem from Personal Wallet',
   'Initiate a redemption request from the partner''s personal reward wallet',
   'REDEMPTION_ACTIONS', 'ACTION', 403, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(),
   'action.redemption.redeem_company',
   'Redeem from Company Wallet',
   'Initiate a redemption request from the partner company reward wallet',
   'REDEMPTION_ACTIONS', 'ACTION', 404, NOW(), NOW(), 'EXTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- No new feature_flag — redemption_store already seeded in V8.

-- ============================================================
-- PARTNER_SELLER → action.redemption.redeem
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN (
    'action.redemption.redeem'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- PARTNER_ADMIN → action.redemption.redeem_company
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN (
    'action.redemption.redeem_company'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.redeem',
    'action.redemption.redeem_company'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
