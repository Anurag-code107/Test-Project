-- ============================================================
-- F-09 Balance Expiration: Permission catalog
-- Note: module.redemption_store already seeded by F-01 (V8) — reused, not re-seeded.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(), 'action.redemption.expiration.configure',
   'Configure Balance Expiration',
   'Configure per-currency reward balance expiration policies (enable/disable, mode, lead time)',
   'REDEMPTION_ACTIONS', 'ACTION', 413, NOW(), NOW(), 'INTERNAL'),
  (gen_random_uuid(), 'action.redemption.expiration.view_breakage',
   'View Balance Expiration Breakage',
   'View and export the reward balance expiration (breakage) report by currency type and period',
   'REDEMPTION_ACTIONS', 'ACTION', 414, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- F-09: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (gen_random_uuid(), 'reward_balance_expiration',
   'Reward Balance Expiration — per-currency expiration policies (inactivity or fixed date), advance-expiry + expiry notifications, and breakage reporting/CSV export',
   false, true, true, NOW(), NOW(), 'REWARDS')
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- F-09: Role grants — CLIENT_ADMIN only (per Permission Matrix in spec.md)
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN (
    'action.redemption.expiration.configure',
    'action.redemption.expiration.view_breakage'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- F-09: Tenant-level grant — Acme seed tenant (dev/seed only).
-- REQUIRED: the 5-layer permission model intersects role permissions with
-- tenant grants; without this row the permission is stripped at Layer 0
-- (cf. V31 corrective for advanced analytics). Production tenant grants are
-- provisioned via the subscription/tier flow, mirroring V27.
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.expiration.configure',
    'action.redemption.expiration.view_breakage'
  )
ON CONFLICT (client_id, permission_key) DO NOTHING;

-- ============================================================
-- F-09: Notification types (REWARDS) — partner recipients
-- ============================================================
INSERT INTO notification_types (id, key, category, title, description, default_roles, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'BALANCE_EXPIRING_SOON',     'REWARDS', 'Reward Balance Expiring Soon',
   'A reward balance is scheduled to expire',              'PARTNER_SELLER,PARTNER_ADMIN', NOW(), NOW()),
  (gen_random_uuid(), 'BALANCE_EXPIRED',           'REWARDS', 'Reward Balance Expired',
   'A reward balance has expired',                         'PARTNER_SELLER,PARTNER_ADMIN', NOW(), NOW()),
  (gen_random_uuid(), 'BALANCE_EXPIRY_CANCELLED',  'REWARDS', 'Reward Balance Expiry Cancelled',
   'A scheduled reward balance expiry was cancelled',      'PARTNER_SELLER,PARTNER_ADMIN', NOW(), NOW())
ON CONFLICT (key) DO NOTHING;
