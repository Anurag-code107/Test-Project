package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.Permission;

import java.util.UUID;

public record PermissionResponse(
    UUID id,
    String permissionKey,
    String displayName,
    String description,
    String category,
    String permissionType,
    String scope,
    int sortOrder
) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(
            p.getId(),
            p.getPermissionKey(),
            p.getDisplayName(),
            p.getDescription(),
            p.getCategory(),
            p.getPermissionType(),
            p.getScope(),
            p.getSortOrder()
        );
    }
}
