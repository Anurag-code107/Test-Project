package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.NotificationResponse;
import com.tenxengage.app.entity.Notification;
import com.tenxengage.app.repository.NotificationRepository;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private NotificationService notificationService;

    private UUID clientId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void getNotifications_returnsPagedResults() {
        Notification notification = Notification.builder()
                .clientId(clientId).userId(userId).title("Test").message("Message")
                .isRead(false).build();
        notification.setId(UUID.randomUUID());

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(notificationRepository.findByClientIdAndUserId(eq(clientId), eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<NotificationResponse> result = notificationService.getNotifications(false, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void markAsRead_throwsWhenNotFound() {
        UUID notifId = UUID.randomUUID();
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(notificationRepository.findByIdAndClientIdAndUserId(notifId, clientId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notifId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void markAsRead_updatesUnreadNotification() {
        UUID notifId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .clientId(clientId).userId(userId).title("Test").message("Msg")
                .isRead(false).build();
        notification.setId(notifId);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(notificationRepository.findByIdAndClientIdAndUserId(notifId, clientId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markAsRead(notifId);

        assertThat(notification.getIsRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAllAsRead_delegatesToRepository() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(notificationRepository.markAllAsRead(any(), any(), any())).thenReturn(5);

        int count = notificationService.markAllAsRead();

        assertThat(count).isEqualTo(5);
    }
}
