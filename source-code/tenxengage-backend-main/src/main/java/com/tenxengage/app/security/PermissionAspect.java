package com.tenxengage.app.security;

import com.tenxengage.app.service.PermissionService;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * AOP aspect that enforces @RequiresPermission annotations on controller methods.
 * Supports single permission, multiple permissions with ANY (OR) or ALL (AND) logic.
 * Reports metrics via PermissionMetrics (Micrometer).
 */
@Aspect
@Component
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    private final PermissionService permissionService;
    private final TenantValidator tenantValidator;
    private final PermissionMetrics metrics;

    public PermissionAspect(PermissionService permissionService,
                            TenantValidator tenantValidator,
                            PermissionMetrics metrics) {
        this.permissionService = permissionService;
        this.tenantValidator = tenantValidator;
        this.metrics = metrics;
    }

    @Before("@annotation(requiresPermission)")
    public void checkPermission(RequiresPermission requiresPermission) {
        String[] permissions = requiresPermission.value();
        RequiresPermission.Logic logic = requiresPermission.logic();

        UUID userId = tenantValidator.getCurrentUserId();

        Timer.Sample sample = metrics.startResolution();
        Set<String> effective = permissionService.resolveEffectivePermissions(userId);
        metrics.recordResolution(sample);

        boolean authorized;
        if (logic == RequiresPermission.Logic.ALL) {
            authorized = effective.containsAll(Arrays.asList(permissions));
        } else {
            authorized = Arrays.stream(permissions).anyMatch(effective::contains);
        }

        if (!authorized) {
            for (String perm : permissions) {
                metrics.recordPermissionDenied(perm);
            }
            log.debug("Permission denied for user {}: required={} logic={}, effective={}",
                    userId, Arrays.toString(permissions), logic, effective.size());
            throw new AccessDeniedException(
                    "Missing required permission(s): " + String.join(", ", permissions));
        }
    }
}
