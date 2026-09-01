package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.projection.StatusCountProjection;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedemptionRequestRepository extends JpaRepository<RedemptionRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id")
    Optional<RedemptionRequest> findByIdForUpdate(@Param("id") UUID id);

    Optional<RedemptionRequest> findByIdAndClientId(UUID id, UUID clientId);

    List<RedemptionRequest> findByIdInAndClientId(List<UUID> ids, UUID clientId);

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

    /** In-flight payouts to poll for reconciliation: dispatched, non-terminal, within the cap window. */
    @Query("""
            SELECT r FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.category = :category
              AND r.walletType = :walletType
              AND r.status IN :statuses
              AND r.dispatchAttemptedAt IS NOT NULL
              AND r.dispatchAttemptedAt >= :notBefore
              AND r.deleted = false
            """)
    List<RedemptionRequest> findInFlightForReconciliation(
            @Param("clientId") UUID clientId,
            @Param("category") RedemptionCategory category,
            @Param("walletType") WalletType walletType,
            @Param("statuses") Collection<RedemptionStatus> statuses,
            @Param("notBefore") Instant notBefore);

    /** Count non-terminal dispatched payouts older than the cap — surfaced for manual review, not polled. */
    @Query("""
            SELECT COUNT(r) FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.category = :category
              AND r.walletType = :walletType
              AND r.status IN :statuses
              AND r.dispatchAttemptedAt IS NOT NULL
              AND r.dispatchAttemptedAt < :cutoff
              AND r.deleted = false
            """)
    long countStuckPastCap(
            @Param("clientId") UUID clientId,
            @Param("category") RedemptionCategory category,
            @Param("walletType") WalletType walletType,
            @Param("statuses") Collection<RedemptionStatus> statuses,
            @Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id AND r.clientId = :clientId")
    Optional<RedemptionRequest> findByIdAndClientIdForUpdate(
            @Param("id") UUID id,
            @Param("clientId") UUID clientId);

    @Query("""
            SELECT COUNT(r) FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.currencyId = :currencyId
              AND r.submittedAt >= :from AND r.submittedAt < :toExclusive
              AND r.deleted = false
            """)
    Long countByClientIdAndCurrencyIdAndSubmittedAtBetween(
            @Param("clientId") UUID clientId,
            @Param("currencyId") String currencyId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT COUNT(r) FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.currencyId = :currencyId
              AND r.status IN :statuses
              AND r.submittedAt >= :from AND r.submittedAt < :toExclusive
              AND r.deleted = false
            """)
    Long countByClientIdAndCurrencyIdAndStatusInAndSubmittedAtBetween(
            @Param("clientId") UUID clientId,
            @Param("currencyId") String currencyId,
            @Param("statuses") Collection<RedemptionStatus> statuses,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT r.status AS status, COUNT(r) AS count
            FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.submittedAt >= :from AND r.submittedAt < :toExclusive
              AND r.deleted = false
            GROUP BY r.status
            """)
    List<StatusCountProjection> countGroupByStatusByClientIdAndSubmittedAtBetween(
            @Param("clientId") UUID clientId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);
}
