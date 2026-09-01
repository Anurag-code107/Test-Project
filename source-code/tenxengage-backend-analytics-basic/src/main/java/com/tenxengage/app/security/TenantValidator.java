package com.tenxengage.app.security;

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

    public void validateClientAccess(UUID clientId) {
        CustomUserDetails userDetails = getCurrentUserDetails();
        if (userDetails.isTenxAdmin()) {
            return;
        }
        if (userDetails.getClientId() == null || !userDetails.getClientId().equals(clientId)) {
            throw new AccessDeniedException("Access denied to client: " + clientId);
        }
    }

    public void validatePartnerCompanyAccess(UUID partnerCompanyId) {
        CustomUserDetails userDetails = getCurrentUserDetails();
        if (userDetails.isTenxAdmin()) {
            return;
        }
        // Internal users (no partnerCompanyId) are client-level staff with cross-partner access
        if (userDetails.getPartnerCompanyId() == null) {
            return;
        }
        if (!userDetails.getPartnerCompanyId().equals(partnerCompanyId)) {
            throw new AccessDeniedException("Access denied to partner company: " + partnerCompanyId);
        }
    }

    public boolean isTenxAdmin() {
        return getCurrentUserDetails().isTenxAdmin();
    }

    public UUID getCurrentClientId() {
        return getCurrentUserDetails().getClientId();
    }

    public UUID getCurrentUserId() {
        return getCurrentUserDetails().getUserId();
    }

    public UUID getCurrentPartnerCompanyId() {
        return getCurrentUserDetails().getPartnerCompanyId();
    }

}
