package com.tenxengage.app.repository;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncentiveRepository extends JpaRepository<Incentive, UUID> {

    @Query("""
        SELECT i FROM Incentive i
        WHERE i.clientId = :clientId
        AND i.deleted = false
        AND (CAST(:type AS STRING) IS NULL OR i.incentiveType = :type)
        AND (CAST(:status AS STRING) IS NULL OR i.status = :status)
        AND (:search IS NULL OR :search = ''
            OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY i.startDate DESC, i.createdAt DESC
        """)
    Page<Incentive> searchByClientId(@Param("clientId") UUID clientId,
                                      @Param("type") IncentiveType type,
                                      @Param("status") IncentiveStatus status,
                                      @Param("search") String search,
                                      Pageable pageable);

    Optional<Incentive> findByIdAndClientIdAndDeletedFalse(UUID id, UUID clientId);

    @Query("""
        SELECT DISTINCT i FROM Incentive i
        LEFT JOIN FETCH i.trainingCourses
        LEFT JOIN FETCH i.budgets
        LEFT JOIN FETCH i.audienceRules
        WHERE i.clientId = :clientId
        AND i.incentiveType = com.tenxengage.app.entity.enums.IncentiveType.TRAINING
        AND i.status = com.tenxengage.app.entity.enums.IncentiveStatus.ACTIVE
        AND i.deleted = false
        """)
    List<Incentive> findActiveTrainingByClientIdWithAssociations(@Param("clientId") UUID clientId);

    @Query("""
        SELECT i FROM Incentive i
        JOIN i.activityDefinitions ad
        WHERE ad.id = :activityDefinitionId
        AND i.deleted = false
        """)
    Optional<Incentive> findByActivityDefinitionId(
            @Param("activityDefinitionId") UUID activityDefinitionId);

    @Query("""
        SELECT DISTINCT i FROM Incentive i
        LEFT JOIN FETCH i.journeyStages
        WHERE i.incentiveType = com.tenxengage.app.entity.enums.IncentiveType.JOURNEY
        AND i.status = com.tenxengage.app.entity.enums.IncentiveStatus.ACTIVE
        AND i.deleted = false
        AND EXISTS (
            SELECT js FROM JourneyStage js
            WHERE js.incentive = i AND js.linkedIncentiveId = :linkedIncentiveId
        )
        """)
    List<Incentive> findJourneyIncentivesContainingStage(
            @Param("linkedIncentiveId") UUID linkedIncentiveId);

    @Query("""
        SELECT i FROM Incentive i
        WHERE i.clientId = :clientId
        AND i.status = com.tenxengage.app.entity.enums.IncentiveStatus.ACTIVE
        AND i.endDate IS NOT NULL AND i.endDate < :now
        AND i.deleted = false
        """)
    List<Incentive> findActiveWithEndDateBeforeByClientId(@Param("clientId") UUID clientId,
                                                          @Param("now") Instant now);

    @Query("""
        SELECT i FROM Incentive i
        WHERE i.clientId = :clientId
        AND i.status = com.tenxengage.app.entity.enums.IncentiveStatus.ACTIVE
        AND i.endDate IS NOT NULL AND i.endDate > :from AND i.endDate <= :to
        AND i.deleted = false
        """)
    List<Incentive> findActiveWithEndDateBetweenByClientId(@Param("clientId") UUID clientId,
                                                            @Param("from") Instant from,
                                                            @Param("to") Instant to);
}
