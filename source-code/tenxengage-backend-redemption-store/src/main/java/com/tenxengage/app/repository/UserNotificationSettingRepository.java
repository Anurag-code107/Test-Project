package com.tenxengage.app.repository;

import com.tenxengage.app.entity.UserNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, UUID> {

    Optional<UserNotificationSetting> findByClientIdAndUserId(UUID clientId, UUID userId);
}
