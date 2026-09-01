package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String notificationTypeKey,
    String category,
    String title,
    String message,
    String resourceType,
    UUID resourceId,
    boolean isRead,
    Instant readAt,
    Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        String typeKey = n.getNotificationType() != null ? n.getNotificationType().getKey() : null;
        String category = n.getNotificationType() != null ? n.getNotificationType().getCategory() : null;
        return new NotificationResponse(
            n.getId(),
            typeKey,
            category,
            n.getTitle(),
            n.getMessage(),
            n.getResourceType(),
            n.getResourceId(),
            n.getIsRead(),
            n.getReadAt(),
            n.getCreatedAt()
        );
    }
}
