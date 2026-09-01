package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateClientNotificationConfigRequest(
    @NotNull(message = "Notification type ID is required")
    UUID notificationTypeId,

    @NotBlank(message = "Role name is required")
    String roleName,

    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {}
