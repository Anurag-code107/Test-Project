package com.tenxengage.app.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces that the currently authenticated user has the specified permission(s).
 * This is the primary authorization mechanism — all non-public endpoints must use this.
 *
 * <p>Usage examples:</p>
 * <pre>
 * // Single permission (most common)
 * &#64;RequiresPermission("action.incentive.create")
 *
 * // Any of these permissions (OR logic)
 * &#64;RequiresPermission(value = {"action.claim.view", "action.claim.approve"}, logic = Logic.ANY)
 *
 * // All of these permissions (AND logic)
 * &#64;RequiresPermission(value = {"action.data.manage", "action.integrations.manage"}, logic = Logic.ALL)
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * One or more permission keys to check.
     */
    String[] value();

    /**
     * Logic for combining multiple permissions.
     * ANY = user needs at least one (default). ALL = user needs every permission.
     */
    Logic logic() default Logic.ANY;

    enum Logic {
        /** User must have at least one of the specified permissions. */
        ANY,
        /** User must have all of the specified permissions. */
        ALL
    }
}
