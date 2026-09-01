package com.tenxengage.app.security;

import com.tenxengage.app.entity.TenantAware;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.util.UUID;

public class TenantEntityListener {

    @PrePersist
    @PreUpdate
    public void setTenant(Object entity) {
        if (entity instanceof TenantAware tenantAware) {
            UUID clientId = TenantContext.getClientId();
            if (clientId != null && tenantAware.getClientId() == null) {
                tenantAware.setClientId(clientId);
            }
        }
    }
}
