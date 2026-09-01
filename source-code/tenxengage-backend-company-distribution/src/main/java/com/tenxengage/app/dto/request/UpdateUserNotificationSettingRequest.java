package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateUserNotificationSettingRequest(
    @NotNull(message = "Notifications enabled flag is required")
    Boolean notificationsEnabled
) {}
