package com.tenxengage.app.repository;

import com.tenxengage.app.entity.UserCourseCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCourseCompletionRepository extends JpaRepository<UserCourseCompletion, UUID> {

    List<UserCourseCompletion> findByUserId(UUID userId);

    List<UserCourseCompletion> findByCourseId(UUID courseId);

    List<UserCourseCompletion> findByUserIdAndSource(UUID userId, String source);

    List<UserCourseCompletion> findByClientIdAndUserId(UUID clientId, UUID userId);

    boolean existsByClientIdAndUserIdAndCourseId(UUID clientId, UUID userId, UUID courseId);

    long countByClientIdAndUserIdAndCourseIdIn(UUID clientId, UUID userId, Collection<UUID> courseIds);

    @Query("SELECT MAX(ucc.completedAt) FROM UserCourseCompletion ucc "
         + "WHERE ucc.clientId = :clientId AND ucc.userId = :userId "
         + "AND ucc.courseId IN :courseIds")
    Optional<Instant> findLatestCompletionDate(@Param("clientId") UUID clientId,
                                               @Param("userId") UUID userId,
                                               @Param("courseIds") Collection<UUID> courseIds);
}
