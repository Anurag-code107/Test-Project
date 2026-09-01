package com.tenxengage.app.config;

import com.tenxengage.app.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class HibernateFilterConfig {

    private final EntityManager entityManager;

    public HibernateFilterConfig(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("execution(* com.tenxengage.app.repository.*Repository.*(..))")
    public void enableTenantFilter() {
        UUID clientId = TenantContext.getClientId();
        if (clientId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                .setParameter("clientId", clientId);
        }
    }
}
