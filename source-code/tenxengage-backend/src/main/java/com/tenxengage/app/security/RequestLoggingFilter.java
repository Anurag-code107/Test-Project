package com.tenxengage.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        try {
            MDC.put("requestId", requestId);

            // Extract user context from SecurityContext (populated by JwtAuthenticationFilter)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
                MDC.put("userId", userDetails.getUserId().toString());
                MDC.put("userEmail", userDetails.getUsername());
                if (userDetails.getClientId() != null) {
                    MDC.put("clientId", userDetails.getClientId().toString());
                }
            }

            // Extract tenant context
            UUID clientId = TenantContext.getClientId();
            if (clientId != null && MDC.get("clientId") == null) {
                MDC.put("clientId", clientId.toString());
            }
            String subdomain = TenantContext.getSubdomain();
            if (subdomain != null) {
                MDC.put("subdomain", subdomain);
            }

            String clientIp = getClientIp(request);
            log.info("Request started: {} {} from {}", request.getMethod(), request.getRequestURI(), clientIp);

            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("Request completed: {} {} status={} duration={}ms",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration);
            MDC.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
