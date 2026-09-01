package com.tenxengage.app.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates on startup that every non-public endpoint has a @RequiresPermission annotation.
 * Fails startup in production if any unsecured endpoints are found.
 */
@Component
public class EndpointSecurityValidator implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(EndpointSecurityValidator.class);

    private static final Set<String> EXEMPT_PATTERNS = Set.of(
            "/api/v1/webhooks/redemption",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/me",
            "/api/v1/health",
            "/api/v1/onboarding",
            "/api/v1/approvals",
            "/api/v1/compliance/financial/whistleblower/report",
            "/api/v1/compliance/financial/whistleblower/status",
            "/api/v1/permissions/effective",
            "/api/v1/reward-balances",
            "/api/v1/me/profile",
            "/api/v1/me/data-export",
            "/api/v1/feature-flags/my-features",
            "/api/v1/ai/tour-match",
            "/error",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final RequestMappingHandlerMapping handlerMapping;
    private final Environment environment;

    public EndpointSecurityValidator(
                                     @Qualifier("requestMappingHandlerMapping")
                                     RequestMappingHandlerMapping handlerMapping,
                                     Environment environment) {
        this.handlerMapping = handlerMapping;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        List<String> unsecuredEndpoints = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod method = entry.getValue();
            RequestMappingInfo info = entry.getKey();

            String pattern = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().toString()
                    : (info.getPatternsCondition() != null
                            ? info.getPatternsCondition().toString()
                            : "unknown");

            if (isExempt(pattern)) {
                continue;
            }

            boolean hasPermissionOnMethod = method.hasMethodAnnotation(RequiresPermission.class);
            boolean hasPermissionOnClass = method.getBeanType().isAnnotationPresent(RequiresPermission.class);

            if (!hasPermissionOnMethod && !hasPermissionOnClass) {
                unsecuredEndpoints.add(
                        method.getBeanType().getSimpleName()
                                + "." + method.getMethod().getName()
                                + " -> " + pattern);
            }
        }

        if (!unsecuredEndpoints.isEmpty()) {
            String message = "SECURITY: " + unsecuredEndpoints.size()
                    + " endpoint(s) lack @RequiresPermission:\n  - "
                    + String.join("\n  - ", unsecuredEndpoints);

            boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (isProduction) {
                log.error(message);
                throw new SecurityException(message);
            } else {
                log.warn(message);
            }
        } else {
            log.info("Endpoint security validation passed: all {} endpoints are secured",
                    handlerMethods.size());
        }
    }

    private boolean isExempt(String pattern) {
        return EXEMPT_PATTERNS.stream().anyMatch(exempt ->
                pattern.contains(exempt));
    }
}
