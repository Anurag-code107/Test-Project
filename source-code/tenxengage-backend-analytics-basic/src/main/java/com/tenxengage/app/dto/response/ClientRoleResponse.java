package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.ClientRolePermission;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record ClientRoleResponse(
    UUID id,
    UUID clientId,
    String name,
    String description,
    String baseRoleName,
    boolean isSystem,
    String roleType,
    UUID homeDashboardTemplateId,
    Map<String, Boolean> permissions,
    Instant createdAt,
    Instant updatedAt
) {
    public static ClientRoleResponse from(ClientRole role, List<ClientRolePermission> permissions) {
        Map<String, Boolean> permMap = permissions.stream()
            .collect(Collectors.toMap(
                ClientRolePermission::getPermissionKey,
                ClientRolePermission::isGranted
            ));

        return new ClientRoleResponse(
            role.getId(),
            role.getClientId(),
            role.getName(),
            role.getDescription(),
            role.getBaseRoleName(),
            role.isSystem(),
            role.getRoleType() != null ? role.getRoleType() : "INTERNAL",
            role.getHomeDashboardTemplateId(),
            permMap,
            role.getCreatedAt(),
            role.getUpdatedAt()
        );
    }
}
