package com.tenxengage.app.repository;

import com.tenxengage.app.entity.UserActivityProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserActivityProgressRepository extends JpaRepository<UserActivityProgress, UUID> {

    List<UserActivityProgress> findByClientIdAndUserIdAndIncentiveId(
            UUID clientId, UUID userId, UUID incentiveId);

    Optional<UserActivityProgress> findByClientIdAndUserIdAndActivityDefinitionId(
            UUID clientId, UUID userId, UUID activityDefinitionId);

    long countByClientIdAndUserIdAndIncentiveIdAndCompletedTrue(
            UUID clientId, UUID userId, UUID incentiveId);
}
