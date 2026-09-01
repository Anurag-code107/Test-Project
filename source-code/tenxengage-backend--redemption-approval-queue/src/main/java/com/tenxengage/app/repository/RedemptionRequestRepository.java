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
import java.time.Instant;
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

    @Query("""
            SELECT r FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.status = :status
              AND r.processingMode = :processingMode
              AND r.dispatchAttemptedAt IS NULL
              AND r.deleted = false
            """)
    List<RedemptionRequest> findByClientIdAndStatusAndProcessingModeAndVendorReferenceIdIsNull(
            @Param("clientId") UUID clientId,
            @Param("status") RedemptionStatus status,
            @Param("processingMode") RedemptionProcessingMode processingMode);

    @Query("""
            SELECT r FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.status = 'RESERVED'
              AND r.processingMode = 'APPROVAL_REQUIRED'
              AND r.dispatchAttemptedAt IS NULL
              AND r.reviewedAt IS NOT NULL
              AND r.reviewedAt < :threshold
              AND r.deleted = false
            """)
    List<RedemptionRequest> findStrandedApprovalItems(
            @Param("clientId") UUID clientId,
            @Param("threshold") Instant threshold);

    @Query(value = """
            SELECT r FROM RedemptionRequest r
            JOIN FETCH r.user u
            JOIN FETCH r.catalogItem ci
            WHERE r.clientId = :clientId
              AND r.status = 'PENDING_APPROVAL'
              AND r.deleted = false
              AND r.currencyId = COALESCE(:currencyId, r.currencyId)
              AND r.catalogItemId = COALESCE(:catalogItemId, r.catalogItemId)
              AND r.submittedAt >= COALESCE(:startDate, r.submittedAt)
              AND r.submittedAt <= COALESCE(:endDate, r.submittedAt)
            ORDER BY r.submittedAt DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.status = 'PENDING_APPROVAL'
              AND r.deleted = false
              AND r.currencyId = COALESCE(:currencyId, r.currencyId)
              AND r.catalogItemId = COALESCE(:catalogItemId, r.catalogItemId)
              AND r.submittedAt >= COALESCE(:startDate, r.submittedAt)
              AND r.submittedAt <= COALESCE(:endDate, r.submittedAt)
            """)
    Page<RedemptionRequest> findApprovalQueue(
            @Param("clientId") UUID clientId,
            @Param("currencyId") String currencyId,
            @Param("catalogItemId") UUID catalogItemId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    Optional<RedemptionRequest> findByClientIdAndUserIdAndClientIdempotencyKey(
            UUID clientId, UUID userId, String clientIdempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id AND r.clientId = :clientId")
    Optional<RedemptionRequest> findByIdAndClientIdForUpdate(
            @Param("id") UUID id,
            @Param("clientId") UUID clientId);
}
