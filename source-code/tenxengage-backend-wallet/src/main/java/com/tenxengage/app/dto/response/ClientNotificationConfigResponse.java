package com.tenxengage.app.dto.response;

import java.util.List;
import java.util.UUID;

public record ClientNotificationConfigResponse(
    UUID notificationTypeId,
    String notificationTypeKey,
    String category,
    String title,
    List<RoleConfig> roleConfigs
) {
    public record RoleConfig(
        String roleName,
        boolean enabled,
        boolean isDefault
    ) {}
}
