package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateUserNotificationPreferenceRequest(
    @NotNull(message = "Notification type ID is required")
    UUID notificationTypeId,

    @NotNull(message = "Opted out flag is required")
    Boolean optedOut
) {}
