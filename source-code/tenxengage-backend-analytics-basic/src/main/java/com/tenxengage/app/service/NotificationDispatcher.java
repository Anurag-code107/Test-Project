package com.tenxengage.app.service;

import com.tenxengage.app.entity.Notification;
import com.tenxengage.app.entity.NotificationType;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserNotificationSetting;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.repository.NotificationRepository;
import com.tenxengage.app.repository.UserNotificationPreferenceRepository;
import com.tenxengage.app.repository.UserNotificationSettingRepository;
import com.tenxengage.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationTypeService notificationTypeService;
    private final NotificationConfigService notificationConfigService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserNotificationSettingRepository settingRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;

    public NotificationDispatcher(NotificationTypeService notificationTypeService,
                                   NotificationConfigService notificationConfigService,
                                   NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   UserNotificationSettingRepository settingRepository,
                                   UserNotificationPreferenceRepository preferenceRepository) {
        this.notificationTypeService = notificationTypeService;
        this.notificationConfigService = notificationConfigService;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.settingRepository = settingRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional
    public void dispatch(NotificationEvent event) {
        // 1. Look up NotificationType by key
        NotificationType type = notificationTypeService.findByKey(event.notificationTypeKey()).orElse(null);
        if (type == null) {
            log.warn("Unknown notification type key: {}", event.notificationTypeKey());
            return;
        }

        // 2. Determine target users
        List<User> targetUsers;
        if (event.targetUserIds() != null && !event.targetUserIds().isEmpty()) {
            // Specific-user targeting
            targetUsers = userRepository.findAllById(event.targetUserIds()).stream()
                .filter(u -> event.clientId().equals(u.getClientId()))
                .toList();
        } else {
            // Role-broadcast targeting
            Set<String> effectiveRoleStrings = notificationConfigService.resolveEffectiveRoles(
                event.clientId(), type.getId(), type.getDefaultRoles());

            if (effectiveRoleStrings.isEmpty()) {
                log.debug("No effective roles for notification type {} in client {}",
                    type.getKey(), event.clientId());
                return;
            }

            // Query users by client + base role names, with audience filtering if incentive-related
            UUID incentiveId = event.metadata() != null ? parseUuid(event.metadata().get("incentiveId")) : null;
            if (incentiveId != null) {
                targetUsers = userRepository.findByClientIdAndBaseRoleNamesWithAudienceFilter(
                    event.clientId(), effectiveRoleStrings, incentiveId);
            } else {
                targetUsers = userRepository.findByClientIdAndBaseRoleNames(event.clientId(), effectiveRoleStrings);
            }
        }

        // 3. Exclude the actor (don't notify about own actions)
        if (event.actorUserId() != null) {
            targetUsers = targetUsers.stream()
                .filter(u -> !u.getId().equals(event.actorUserId()))
                .toList();
        }

        if (targetUsers.isEmpty()) {
            log.debug("No target users for notification type {} in client {}",
                type.getKey(), event.clientId());
            return;
        }

        // 4. Bulk-check user global settings (notifications_enabled = false)
        Set<UUID> targetUserIds = targetUsers.stream().map(User::getId).collect(Collectors.toSet());
        Set<UUID> disabledUserIds = findDisabledUserIds(event.clientId(), targetUserIds);

        // 5. Bulk-check user type preferences (opted_out = true)
        Set<UUID> remainingUserIds = targetUserIds.stream()
            .filter(id -> !disabledUserIds.contains(id))
            .collect(Collectors.toSet());

        Set<UUID> optedOutUserIds = Set.of();
        if (!remainingUserIds.isEmpty()) {
            optedOutUserIds = preferenceRepository.findOptedOutUserIds(
                event.clientId(), type.getId(), remainingUserIds);
        }

        Set<UUID> finalOptedOut = optedOutUserIds;

        // 6. Build notification records for eligible users
        List<Notification> notifications = new ArrayList<>();
        for (User user : targetUsers) {
            if (disabledUserIds.contains(user.getId())) {
                continue;
            }
            if (finalOptedOut.contains(user.getId())) {
                continue;
            }

            notifications.add(Notification.builder()
                .clientId(event.clientId())
                .userId(user.getId())
                .notificationTypeId(type.getId())
                .title(event.title())
                .message(event.message())
                .resourceType(event.resourceType())
                .resourceId(event.resourceId())
                .build());
        }

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
            log.info("Created {} notifications for type {} in client {}",
                notifications.size(), type.getKey(), event.clientId());
        }
    }

    private Set<UUID> findDisabledUserIds(UUID clientId, Set<UUID> targetUserIds) {
        // Check each target user's global setting
        return targetUserIds.stream()
            .filter(userId -> {
                return settingRepository.findByClientIdAndUserId(clientId, userId)
                    .map(s -> !s.getNotificationsEnabled())
                    .orElse(false); // no row = enabled (default TRUE)
            })
            .collect(Collectors.toSet());
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
