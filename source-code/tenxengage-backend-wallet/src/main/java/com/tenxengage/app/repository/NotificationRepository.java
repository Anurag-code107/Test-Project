package com.tenxengage.app.repository;

import com.tenxengage.app.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
        SELECT n FROM Notification n
        WHERE n.clientId = :clientId AND n.userId = :userId
        ORDER BY n.createdAt DESC
        """)
    Page<Notification> findByClientIdAndUserId(@Param("clientId") UUID clientId,
                                                @Param("userId") UUID userId,
                                                Pageable pageable);

    @Query("""
        SELECT n FROM Notification n
        WHERE n.clientId = :clientId AND n.userId = :userId AND n.isRead = false
        ORDER BY n.createdAt DESC
        """)
    Page<Notification> findUnreadByClientIdAndUserId(@Param("clientId") UUID clientId,
                                                      @Param("userId") UUID userId,
                                                      Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.clientId = :clientId AND n.userId = :userId AND n.isRead = false")
    long countUnreadByClientIdAndUserId(@Param("clientId") UUID clientId, @Param("userId") UUID userId);

    Optional<Notification> findByIdAndClientIdAndUserId(UUID id, UUID clientId, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now, n.updatedAt = :now " +
           "WHERE n.clientId = :clientId AND n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("clientId") UUID clientId, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.clientId = :clientId AND n.createdAt < :cutoff")
    int deleteByClientIdAndCreatedAtBefore(@Param("clientId") UUID clientId, @Param("cutoff") Instant cutoff);

    @Query("SELECT DISTINCT n.clientId FROM Notification n")
    List<UUID> findDistinctClientIds();

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.clientId = :clientId AND n.userId = :userId")
    long countByClientIdAndUserId(@Param("clientId") UUID clientId, @Param("userId") UUID userId);
}
