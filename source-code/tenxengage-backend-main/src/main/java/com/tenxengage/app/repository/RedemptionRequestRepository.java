package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedemptionRequestRepository extends JpaRepository<RedemptionRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id")
    Optional<RedemptionRequest> findByIdForUpdate(@Param("id") UUID id);

    Optional<RedemptionRequest> findByIdAndClientId(UUID id, UUID clientId);

    Optional<RedemptionRequest> findByIdAndClientIdAndUserId(UUID id, UUID clientId, UUID userId);

    Page<RedemptionRequest> findByClientIdAndUserIdAndDeletedFalse(UUID clientId, UUID userId, Pageable pageable);

    Page<RedemptionRequest> findByClientIdAndDeletedFalse(UUID clientId, Pageable pageable);

    long countByClientIdAndUserIdAndStatusIn(UUID clientId, UUID userId, List<RedemptionStatus> statuses);

    List<RedemptionRequest> findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
            UUID clientId, RedemptionStatus status, RedemptionProcessingMode processingMode, LocalDate batchDate);
}
