-- ============================================================
-- Advanced Redemption Analytics: Permission catalog
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES (
    gen_random_uuid(),
    'action.redemption.analytics.advanced',
    'View Advanced Analytics',
    'Grants access to dimensional analytics breakdowns, trend charts, and liability CSV export',
    'REDEMPTION_ACTIONS',
    'ACTION',
    210,
    NOW(), NOW(),
    'CLIENT'
)
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- Advanced Redemption Analytics: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (
    gen_random_uuid(),
    'redemption_analytics_advanced',
    'Advanced Redemption Analytics — dimensional breakdowns, trend charts, and liability trend CSV export',
    false, true, true,
    NOW(), NOW(),
    'REDEMPTION'
)
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- Advanced Redemption Analytics: Role grants (CLIENT_ADMIN only)
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.analytics.advanced')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;
