package com.tenxengage.app.repository;

import com.tenxengage.app.entity.UserJourneyStageProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJourneyStageProgressRepository extends JpaRepository<UserJourneyStageProgress, UUID> {

    List<UserJourneyStageProgress> findByClientIdAndUserIdAndJourneyIdOrderByCreatedAt(
            UUID clientId, UUID userId, UUID journeyId);

    Optional<UserJourneyStageProgress> findByClientIdAndUserIdAndJourneyIdAndStageId(
            UUID clientId, UUID userId, UUID journeyId, UUID stageId);

    long countByClientIdAndUserIdAndJourneyIdAndCompletedTrue(
            UUID clientId, UUID userId, UUID journeyId);

    boolean existsByClientIdAndUserIdAndJourneyIdAndLinkedIncentiveIdAndCompletedTrue(
            UUID clientId, UUID userId, UUID journeyId, UUID linkedIncentiveId);
}
