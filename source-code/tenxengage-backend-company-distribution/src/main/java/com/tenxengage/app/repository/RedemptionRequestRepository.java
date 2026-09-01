package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionOrigin;
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

    /**
     * A user's own redemption by id. Filtered by {@code origin} so the detail endpoint agrees with the
     * list: a distribution row carries {@code user_id = recipient}, so without this the seller could open
     * a company award through the personal-redemption detail even though personal history hides it.
     * Awards are read through the Company Award History endpoints instead.
     */
    Optional<RedemptionRequest> findByIdAndClientIdAndUserIdAndOrigin(
            UUID id, UUID clientId, UUID userId, RedemptionOrigin origin);

    Page<RedemptionRequest> findByClientIdAndUserIdAndDeletedFalse(UUID clientId, UUID userId, Pageable pageable);

    Page<RedemptionRequest> findByClientIdAndDeletedFalse(UUID clientId, Pageable pageable);

    /**
     * In-flight count for the per-user submission cap. Must be filtered by {@code origin} — a
     * distribution row carries {@code user_id = recipient}, so counting it here would let a company award
     * consume the recipient's own allowance (and could reject the 11th recipient of a distribution).
     * Callers pass {@link com.tenxengage.app.entity.enums.RedemptionOrigin#SELF}.
     */
    long countByClientIdAndUserIdAndOriginAndStatusIn(
            UUID clientId, UUID userId, RedemptionOrigin origin, List<RedemptionStatus> statuses);

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

    /**
     * In-flight payouts to poll for reconciliation: dispatched, non-terminal, within the cap window.
     *
     * <p>{@code walletTypes} is a collection, not a single value, because BOTH wallet types need polling:
     * INDIVIDUAL for self-service redemptions and COMPANY for distribution payout legs. It was
     * single-valued and hard-called with INDIVIDUAL, which made every company-wallet payout invisible to
     * missed-webhook recovery — funds would sit reserved indefinitely with no alert (design F-8).</p>
     */
    @Query("""
            SELECT r FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.category = :category
              AND r.walletType IN :walletTypes
              AND r.status IN :statuses
              AND r.dispatchAttemptedAt IS NOT NULL
              AND r.dispatchAttemptedAt >= :notBefore
              AND r.deleted = false
            """)
    List<RedemptionRequest> findInFlightForReconciliation(
            @Param("clientId") UUID clientId,
            @Param("category") RedemptionCategory category,
            @Param("walletTypes") Collection<WalletType> walletTypes,
            @Param("statuses") Collection<RedemptionStatus> statuses,
            @Param("notBefore") Instant notBefore);

    /** Count non-terminal dispatched payouts older than the cap — surfaced for manual review, not polled. */
    @Query("""
            SELECT COUNT(r) FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.category = :category
              AND r.walletType IN :walletTypes
              AND r.status IN :statuses
              AND r.dispatchAttemptedAt IS NOT NULL
              AND r.dispatchAttemptedAt < :cutoff
              AND r.deleted = false
            """)
    long countStuckPastCap(
            @Param("clientId") UUID clientId,
            @Param("category") RedemptionCategory category,
            @Param("walletTypes") Collection<WalletType> walletTypes,
            @Param("statuses") Collection<RedemptionStatus> statuses,
            @Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id AND r.clientId = :clientId")
    Optional<RedemptionRequest> findByIdAndClientIdForUpdate(
            @Param("id") UUID id,
            @Param("clientId") UUID clientId);

    /**
     * Redemption analytics counts — the three queries below all pin {@code origin = SELF}.
     *
     * <p>Distribution payout legs live in this same table, so without the predicate an admin distributing to
     * 40 sellers would register as 40 redemptions and move every rate on the dashboard. The product decision
     * is that distributions do not appear in redemption analytics at all (matching the {@code mv_*} analytics
     * views, which filter the same way).</p>
     *
     * <p>Pinned to the enum constant rather than taken as a parameter: there is no caller that legitimately
     * wants a different origin, and a parameter would let a future one silently widen the metric. This mirrors
     * how {@code r.deleted = false} is pinned in the same queries.</p>
     *
     * <p>Contrast the operational sweeps above ({@code findByClientIdAndStatusAndProcessingMode…},
     * {@code findStrandedApprovalItems}) which deliberately carry <b>no</b> origin filter — those move real
     * money, and hiding distribution legs from them would strand funds. Same table, opposite requirement.</p>
     */
    @Query("""
            SELECT COUNT(r) FROM RedemptionRequest r
            WHERE r.clientId = :clientId
              AND r.currencyId = :currencyId
              AND r.submittedAt >= :from AND r.submittedAt < :toExclusive
              AND r.deleted = false
              AND r.origin = com.tenxengage.app.entity.enums.RedemptionOrigin.SELF
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
              AND r.origin = com.tenxengage.app.entity.enums.RedemptionOrigin.SELF
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
              AND r.origin = com.tenxengage.app.entity.enums.RedemptionOrigin.SELF
            GROUP BY r.status
            """)
    List<StatusCountProjection> countGroupByStatusByClientIdAndSubmittedAtBetween(
            @Param("clientId") UUID clientId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);
}
