-- ============================================================
-- F-04 Redemption Approval Queue: Permission catalog
-- Note: module.redemption_store already seeded in F-01 V8.
--       No new feature flag — redemption_store covers this feature.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.approve',
   'Approve/Reject Redemptions',
   'View the redemption approval queue and approve or reject pending redemption requests',
   'REDEMPTION_ACTIONS', 'ACTION', 405, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- CLIENT_ADMIN → action.redemption.approve
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN (
    'action.redemption.approve'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- ACTIVITY_APPROVER → action.redemption.approve
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'ACTIVITY_APPROVER'
  AND p.permission_key IN (
    'action.redemption.approve'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.approve'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
