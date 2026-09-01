package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phone,
    String avatar,
    UserStatus status,
    List<String> permissions,
    UUID clientRoleId,
    String clientRoleName,
    UUID organizationId,
    UUID clientId,
    String clientName,
    UUID partnerCompanyId,
    String partnerCompanyName,
    String metadata,
    HomeDashboardTemplateResponse homeDashboardTemplate,
    Instant createdAt,
    Instant updatedAt
) {

    /**
     * Creates a UserResponse without permissions (for listing users, etc.).
     */
    public static UserResponse from(User user) {
        return from(user, Set.of(), null);
    }

    /**
     * Creates a UserResponse with effective permissions but no dashboard template.
     */
    public static UserResponse from(User user, Set<String> effectivePermissions) {
        return from(user, effectivePermissions, null);
    }

    /**
     * Creates a UserResponse with effective permissions and the resolved home dashboard template.
     */
    public static UserResponse from(User user, Set<String> effectivePermissions,
                                    HomeDashboardTemplateResponse homeDashboardTemplate) {
        String clientRoleName = null;
        if (user.getClientRole() != null) {
            clientRoleName = user.getClientRole().getName();
        }
        return from(user, List.copyOf(effectivePermissions), clientRoleName, homeDashboardTemplate);
    }

    private static UserResponse from(User user, List<String> permissions, String clientRoleName,
                                     HomeDashboardTemplateResponse homeDashboardTemplate) {
        String resolvedClientName = null;
        if (user.getClient() != null) {
            resolvedClientName = user.getClient().getName();
        }

        String partnerCompanyName = null;
        if (user.getPartnerCompany() != null) {
            partnerCompanyName = user.getPartnerCompany().getName();
        }

        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getAvatar(),
            user.getStatus(),
            permissions,
            user.getClientRoleId(),
            clientRoleName,
            user.getOrganizationId(),
            user.getClientId(),
            resolvedClientName,
            user.getPartnerCompanyId(),
            partnerCompanyName,
            user.getMetadata(),
            homeDashboardTemplate,
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
