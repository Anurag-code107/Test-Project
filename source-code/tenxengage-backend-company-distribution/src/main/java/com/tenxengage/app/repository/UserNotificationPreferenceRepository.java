package com.tenxengage.app.repository;

import com.tenxengage.app.entity.UserNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {

    List<UserNotificationPreference> findByClientIdAndUserId(UUID clientId, UUID userId);

    Optional<UserNotificationPreference> findByClientIdAndUserIdAndNotificationTypeId(
        UUID clientId, UUID userId, UUID notificationTypeId);

    @Query("""
        SELECT p.userId FROM UserNotificationPreference p
        WHERE p.clientId = :clientId
        AND p.notificationTypeId = :notificationTypeId
        AND p.optedOut = true
        AND p.userId IN :userIds
        """)
    Set<UUID> findOptedOutUserIds(@Param("clientId") UUID clientId,
                                   @Param("notificationTypeId") UUID notificationTypeId,
                                   @Param("userIds") Set<UUID> userIds);
}
