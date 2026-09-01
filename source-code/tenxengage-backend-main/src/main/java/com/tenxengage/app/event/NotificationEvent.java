package com.tenxengage.app.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NotificationEvent(
    String notificationTypeKey,
    UUID clientId,
    String title,
    String message,
    String resourceType,
    UUID resourceId,
    UUID actorUserId,
    List<UUID> targetUserIds,
    Map<String, String> metadata
) {}
