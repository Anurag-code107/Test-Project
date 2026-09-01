package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.NotificationType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record NotificationTypeResponse(
    UUID id,
    String key,
    String category,
    String title,
    String description,
    List<String> defaultRoles
) {
    public static NotificationTypeResponse from(NotificationType type) {
        List<String> roles = type.getDefaultRoles() != null && !type.getDefaultRoles().isBlank()
            ? Arrays.asList(type.getDefaultRoles().split(","))
            : List.of();
        return new NotificationTypeResponse(
            type.getId(),
            type.getKey(),
            type.getCategory(),
            type.getTitle(),
            type.getDescription(),
            roles
        );
    }
}
