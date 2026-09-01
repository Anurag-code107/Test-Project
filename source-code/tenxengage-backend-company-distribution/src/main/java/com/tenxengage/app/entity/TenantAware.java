package com.tenxengage.app.entity;

import java.util.UUID;

public interface TenantAware {
    UUID getClientId();
    void setClientId(UUID clientId);
}
