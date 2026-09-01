package com.tenxengage.app.security;

import java.util.Set;

/**
 * Constants for permission system hardening.
 */
public final class PermissionConstants {

    private PermissionConstants() {}

    /**
     * Permissions that cannot be removed from the system Client Admin role.
     * Removing these would lock the admin out of managing their own tenant.
     */
    public static final Set<String> IMMUTABLE_ADMIN_PERMISSIONS = Set.of(
            "module.home",
            "module.settings.users",
            "module.settings.profile",
            "action.users.view",
            "action.users.create",
            "action.users.edit",
            "action.roles.view",
            "action.roles.edit",
            "action.permissions.view"
    );

    /**
     * Permissions that a user cannot remove from their own role.
     * Prevents self-lockout from the permission management UI.
     */
    public static final Set<String> SELF_LOCK_CRITICAL_PERMISSIONS = Set.of(
            "action.roles.view",
            "action.roles.edit",
            "action.permissions.view",
            "action.permissions.manage",
            "module.settings.users"
    );
}
