package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.UserNotificationSetting;

import java.util.UUID;

public record UserNotificationSettingResponse(
    UUID id,
    UUID userId,
    boolean notificationsEnabled
) {
    public static UserNotificationSettingResponse from(UserNotificationSetting setting) {
        return new UserNotificationSettingResponse(
            setting.getId(),
            setting.getUserId(),
            setting.getNotificationsEnabled()
        );
    }
}
