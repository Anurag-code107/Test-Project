-- Company Distribution Store — permission catalog, role grants, and the feature flag.
--
-- Every key is seeded into BOTH client_role_permissions (role-level) AND client_permission_grants
-- (tenant-level). The effective-permission resolver intersects the two, so a key present in only one
-- is silently stripped and every call 403s.
--
-- NOTE: this migration only ADDS. Deleting action.redemption.redeem_company happens later, once its
-- consumers are gone (ExportScope.COMPANY, the FE gates, submitCompanyRedemption). Splitting grant from
-- delete means that if the delete is ever reverted, nobody is locked out in the meantime.

-- ============================================================
-- 1. Permission catalog
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(), 'action.redemption.distribute',               'Distribute Company Wallet',   'Distribute the company wallet balance to the company''s partner sellers', 'REDEMPTION_ACTIONS', 'ACTION', 410, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(), 'action.redemption.view_distribution_history','View Distribution History',   'View the company''s distribution history and per-recipient outcomes',    'REDEMPTION_ACTIONS', 'ACTION', 411, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(), 'action.redemption.view_company_awards',      'View Company Awards',         'View rewards received from company admins',                              'REDEMPTION_ACTIONS', 'ACTION', 412, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(), 'action.wallet.fund_company',                 'Fund Company Wallet',         'Credit a partner company''s wallet — creates balance, internal only',    'REDEMPTION_ACTIONS', 'ACTION', 413, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- 2. PARTNER_ADMIN → distribute + view_distribution_history
--    plus action.redemption.redeem, which they have NEVER held.
--
--    V17 granted PARTNER_ADMIN only action.redemption.redeem_company, so their personal redemption
--    (POST /api/v1/redemption/requests, gated on action.redemption.redeem) has always returned 403 —
--    and the company endpoint they COULD reach always failed at dispatch. Both doors were shut. A
--    partner admin redeems from their own individual wallet like any seller, so they need this key.
--    The override resolver is deny-only, so this could not have been worked around at runtime.
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN (
    'action.redemption.distribute',
    'action.redemption.view_distribution_history',
    'action.redemption.redeem'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- 3. PARTNER_SELLER → view_company_awards
--    Sellers receive distributions; they never create them.
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN ('action.redemption.view_company_awards')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- 4. CLIENT_ADMIN → fund_company
--    Deliberately NOT granted to PARTNER_ADMIN: they must not be able to top up the budget they spend.
--    Also deliberately NOT granted view_distribution_history — company money is the company's business
--    (design OQ-13); a client admin funds the wallet but has no application view of how it was spent.
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.wallet.fund_company')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- 5. Tenant-level grants (Acme dev/seed tenant)
--    Required alongside the role grants — the resolver intersects role ∩ tenant.
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.distribute',
    'action.redemption.view_distribution_history',
    'action.redemption.view_company_awards',
    'action.wallet.fund_company',
    'action.redemption.redeem'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;

-- ============================================================
-- 6. Feature flag — lets the whole distribution surface be switched off without a revert.
--    Scope note: this gates the NEW surface only. The origin filters, the retirement deletions and the
--    analytics rebuild are not flag-protected; rolling those back needs a code revert.
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (gen_random_uuid(), 'company_distribution', 'Enables the Company Distribution Store — partner admins distributing the company wallet to their sellers', true, true, true, NOW(), NOW(), 'REWARDS')
ON CONFLICT (feature_key) DO NOTHING;
