-- ============================================================
-- Redemption Analytics Basic (F-07): new view_analytics permission
-- Note: module.redemption_store already seeded by F-01 (V8).
-- No new tables — F-07 reads existing RewardWallet, LedgerEntry, RedemptionRequest entities.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.view_analytics',
   'View Redemption Analytics',
   'Access the redemption analytics dashboard and export unredeemed balance CSV',
   'REDEMPTION_ACTIONS', 'ACTION', 412, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- CLIENT_ADMIN → action.redemption.view_analytics
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.view_analytics')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN ('action.redemption.view_analytics')
ON CONFLICT (client_id, permission_key) DO NOTHING;

-- ============================================================
-- Composite indexes for analytics queries
-- ============================================================

-- Support LedgerEntry aggregation queries (FR-07.1 lifetime totals)
-- ledger_entries has no soft-delete column; full index without predicate
CREATE INDEX IF NOT EXISTS idx_ledger_entries_client_currency_type
  ON ledger_entries (client_id, currency_id, entry_type);

-- Support RewardWallet balance aggregation (FR-07.2 unredeemed liability)
-- reward_wallets has no soft-delete column; full index without predicate
CREATE INDEX IF NOT EXISTS idx_reward_wallets_client_currency
  ON reward_wallets (client_id, currency_id);

-- Support RedemptionRequest windowed count queries (FR-07.3, FR-07.7)
CREATE INDEX IF NOT EXISTS idx_redemption_requests_client_status_submitted
  ON redemption_requests (client_id, status, submitted_at)
  WHERE deleted = false;
