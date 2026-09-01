package com.tenxengage.app.security;

import com.tenxengage.app.service.ClientService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Client-Subdomain";

    private final ClientService clientService;

    public TenantFilter(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String subdomain = request.getHeader(TENANT_HEADER);
            if (StringUtils.hasText(subdomain)) {
                TenantContext.setSubdomain(subdomain);
                String clientIdStr = clientService.findClientIdBySubdomain(subdomain);
                if (clientIdStr != null) {
                    TenantContext.setClientId(UUID.fromString(clientIdStr));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
