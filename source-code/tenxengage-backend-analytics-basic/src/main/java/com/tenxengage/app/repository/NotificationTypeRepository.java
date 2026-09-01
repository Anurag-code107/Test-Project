package com.tenxengage.app.repository;

import com.tenxengage.app.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTypeRepository extends JpaRepository<NotificationType, UUID> {

    Optional<NotificationType> findByKey(String key);

    List<NotificationType> findByCategory(String category);

    List<NotificationType> findAllByOrderByCategoryAscKeyAsc();
}
