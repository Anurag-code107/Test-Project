-- BUG-007: register the permission key 'action.builder.manage' referenced by
-- BuilderConfigController (updateSection, addField, updateField, removeField)
-- and ActivityCategoryController (create, update, delete). The key was never
-- added to the permission catalog or granted to any role, so PermissionAspect
-- rejected every call with 403 ACCESS_DENIED.
--
-- PermissionService.resolveEffectivePermissions intersects role permissions
-- with tenant grants, so both client_role_permissions (Client Admin) and
-- client_permission_grants (Acme tenant) must be written for the key to take
-- effect. Only the Client Admin role receives the grant — the Builder Config
-- tab is gated by module.settings.tenx and is already admin-only.

INSERT INTO permissions (
    id, permission_key, display_name, description,
    category, permission_type, sort_order,
    created_at, updated_at, scope
) VALUES (
    gen_random_uuid(),
    'action.builder.manage',
    'Manage Builder Configuration',
    'Add, edit, or remove fields and sections in the incentive builder configuration, plus manage activity categories.',
    'SETTINGS_ACTIONS',
    'ACTION',
    2100,
    now(), now(),
    'INTERNAL'
) ON CONFLICT (permission_key) DO NOTHING;

-- Grant to the Client Admin role (id seeded in V3__baseline_tenant_and_config.sql:149).
INSERT INTO client_role_permissions (
    id, client_role_id, permission_key, granted, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    '13710c8a-51e4-4822-9919-58f433690093',
    'action.builder.manage',
    true,
    now(), now()
) ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- Grant at the Acme tenant level (id seeded in V3__baseline_tenant_and_config.sql:10).
-- Required because PermissionService intersects role permissions with tenant
-- grants; a role-only grant would still resolve to denied.
INSERT INTO client_permission_grants (
    id, client_id, permission_key, granted, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'a0000000-0000-0000-0000-000000000001',
    'action.builder.manage',
    true,
    now(), now()
) ON CONFLICT (client_id, permission_key) DO NOTHING;
