package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.NotificationTypeResponse;
import com.tenxengage.app.entity.NotificationType;
import com.tenxengage.app.repository.NotificationTypeRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationTypeService {

    private final NotificationTypeRepository notificationTypeRepository;

    public NotificationTypeService(NotificationTypeRepository notificationTypeRepository) {
        this.notificationTypeRepository = notificationTypeRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "notificationTypes", key = "'all'")
    public List<NotificationTypeResponse> getAllTypes() {
        return notificationTypeRepository.findAllByOrderByCategoryAscKeyAsc().stream()
            .map(NotificationTypeResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationTypeResponse> getTypesByCategory(String category) {
        return notificationTypeRepository.findByCategory(category).stream()
            .map(NotificationTypeResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<NotificationType> findByKey(String key) {
        return notificationTypeRepository.findByKey(key);
    }

    @Transactional(readOnly = true)
    public NotificationType getByKey(String key) {
        return notificationTypeRepository.findByKey(key)
            .orElseThrow(() -> new IllegalArgumentException("Unknown notification type: " + key));
    }

    @Transactional(readOnly = true)
    public NotificationType getById(UUID id) {
        return notificationTypeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Notification type not found: " + id));
    }
}
