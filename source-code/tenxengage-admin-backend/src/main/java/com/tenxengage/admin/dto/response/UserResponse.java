package com.tenxengage.admin.dto.response;

import com.tenxengage.admin.entity.User;
import com.tenxengage.admin.entity.enums.UserStatus;

import java.time.Instant;
import java.util.List;
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
    Instant createdAt,
    Instant updatedAt
) {

    /**
     * Creates a UserResponse without permissions (for listing users, etc.).
     */
    public static UserResponse from(User user) {
        return from(user, List.of(), null);
    }

    /**
     * Creates a UserResponse with effective permissions (for auth responses).
     */
    public static UserResponse from(User user, List<String> effectivePermissions) {
        return from(user, effectivePermissions, null);
    }

    private static UserResponse from(User user, List<String> permissions, String clientRoleName) {
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
            null,
            user.getPartnerCompanyId(),
            null,
            user.getMetadata(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
