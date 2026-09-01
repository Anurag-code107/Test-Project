package com.tenxengage.admin.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantValidator {

    public CustomUserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new AccessDeniedException("Not authenticated");
    }

    public boolean isTenxAdmin() {
        return getCurrentUserDetails().isTenxAdmin();
    }

    public UUID getCurrentUserId() {
        return getCurrentUserDetails().getUserId();
    }
}
