package com.tenxengage.app.security;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_SUBDOMAIN = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_CLIENT_ID = new ThreadLocal<>();

    private TenantContext() {
        // Utility class, no instantiation
    }

    public static String getSubdomain() {
        return CURRENT_SUBDOMAIN.get();
    }

    public static void setSubdomain(String subdomain) {
        CURRENT_SUBDOMAIN.set(subdomain);
    }

    public static UUID getClientId() {
        return CURRENT_CLIENT_ID.get();
    }

    public static void setClientId(UUID clientId) {
        CURRENT_CLIENT_ID.set(clientId);
    }

    public static void clear() {
        CURRENT_SUBDOMAIN.remove();
        CURRENT_CLIENT_ID.remove();
    }
}
