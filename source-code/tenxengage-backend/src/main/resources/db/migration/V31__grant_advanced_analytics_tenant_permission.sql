-- ============================================================
-- Advanced Redemption Analytics: tenant-level permission grant (corrective)
--
-- V29 seeded the permission catalog + CLIENT_ADMIN role grant, but omitted the
-- tenant-level client_permission_grants row that every sibling redemption seed
-- carries (cf. V27 "Acme tenant seed grants"). The 5-layer permission model
-- (PermissionService.resolveEffectivePermissions) intersects role permissions
-- with tenant grants, so without this row the permission is stripped at Layer 0
-- and no user at any client can exercise advanced analytics.
--
-- Acme tenant seed grant (dev/seed only) — production tenant grants are
-- provisioned via subscription/tier flow, mirroring V27.
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN ('action.redemption.analytics.advanced')
ON CONFLICT (client_id, permission_key) DO NOTHING;
