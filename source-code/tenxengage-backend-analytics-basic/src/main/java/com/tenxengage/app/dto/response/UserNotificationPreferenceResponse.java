package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.UserNotificationPreference;

import java.util.UUID;

public record UserNotificationPreferenceResponse(
    UUID id,
    UUID notificationTypeId,
    String notificationTypeKey,
    boolean optedOut
) {
    public static UserNotificationPreferenceResponse from(UserNotificationPreference pref) {
        String key = pref.getNotificationType() != null ? pref.getNotificationType().getKey() : null;
        return new UserNotificationPreferenceResponse(
            pref.getId(),
            pref.getNotificationTypeId(),
            key,
            pref.getOptedOut()
        );
    }
}
