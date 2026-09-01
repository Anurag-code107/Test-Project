package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.NotificationResponse;
import com.tenxengage.app.dto.response.UnreadCountResponse;
import com.tenxengage.app.entity.Notification;
import com.tenxengage.app.repository.NotificationRepository;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final TenantValidator tenantValidator;

    public NotificationService(NotificationRepository notificationRepository,
                                TenantValidator tenantValidator) {
        this.notificationRepository = notificationRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Boolean unreadOnly, Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        Page<Notification> page;
        if (Boolean.TRUE.equals(unreadOnly)) {
            page = notificationRepository.findUnreadByClientIdAndUserId(clientId, userId, pageable);
        } else {
            page = notificationRepository.findByClientIdAndUserId(clientId, userId, pageable);
        }
        return page.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        long count = notificationRepository.countUnreadByClientIdAndUserId(clientId, userId);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        Notification notification = notificationRepository.findByIdAndClientIdAndUserId(
                notificationId, clientId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + notificationId));

        if (!notification.getIsRead()) {
            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    public void sendRedemptionSubmitted(UUID userId, UUID redemptionRequestId) {
        log.info("[step=redemption-notification-submitted] userId={}, redemptionRequestId={}",
                userId, redemptionRequestId);
    }

    public void sendRedemptionCompleted(UUID userId, UUID redemptionRequestId, BigDecimal amount) {
        log.info("[step=redemption-notification-completed] userId={}, redemptionRequestId={}, amount={}",
                userId, redemptionRequestId, amount);
    }

    public void sendRedemptionFailed(UUID userId, UUID redemptionRequestId, String failureReason) {
        log.info("[step=redemption-notification-failed] userId={}, redemptionRequestId={}, reason={}",
                userId, redemptionRequestId, failureReason);
    }

    @Transactional
    public int markAllAsRead() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        int count = notificationRepository.markAllAsRead(clientId, userId, Instant.now());
        log.info("Marked {} notifications as read for user {}", count, userId);
        return count;
    }
}
