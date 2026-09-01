package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.enums.ReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RedemptionReturnRepository extends JpaRepository<RedemptionReturn, UUID> {

    Optional<RedemptionReturn> findByIdAndClientId(UUID id, UUID clientId);

    Optional<RedemptionReturn> findByIdAndClientIdAndPartnerUserId(UUID id, UUID clientId, UUID partnerUserId);

    // Native queries: derived/JPQL queries can't sort by snake_case column names (Spring Data splits
    // on '_'), and native queries don't translate camelCase properties. All paginated queries are
    // native so the controller can pass column names (created_at, amount) consistently.

    @Query(value = "SELECT * FROM redemption_returns r WHERE r.client_id = :clientId AND r.partner_user_id = :partnerUserId AND r.deleted = false",
           countQuery = "SELECT COUNT(*) FROM redemption_returns r WHERE r.client_id = :clientId AND r.partner_user_id = :partnerUserId AND r.deleted = false",
           nativeQuery = true)
    Page<RedemptionReturn> findByClientIdAndPartnerUserId(
            @Param("clientId") UUID clientId,
            @Param("partnerUserId") UUID partnerUserId,
            Pageable pageable);

    @Query(value = "SELECT * FROM redemption_returns r WHERE r.client_id = :clientId AND r.partner_user_id = :partnerUserId AND r.deleted = false AND CAST(r.status AS text) = :status",
           countQuery = "SELECT COUNT(*) FROM redemption_returns r WHERE r.client_id = :clientId AND r.partner_user_id = :partnerUserId AND r.deleted = false AND CAST(r.status AS text) = :status",
           nativeQuery = true)
    Page<RedemptionReturn> findByClientIdAndPartnerUserIdAndStatus(
            @Param("clientId") UUID clientId,
            @Param("partnerUserId") UUID partnerUserId,
            @Param("status") String status,
            Pageable pageable);

    Page<RedemptionReturn> findByClientId(UUID clientId, Pageable pageable);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM redemption_returns WHERE redemption_id = :redemptionId AND client_id = :clientId AND deleted = false AND CAST(status AS text) NOT IN :excludedStatuses)",
           nativeQuery = true)
    boolean existsByRedemptionIdAndClientIdAndStatusNotIn(
            @Param("redemptionId") UUID redemptionId,
            @Param("clientId") UUID clientId,
            @Param("excludedStatuses") List<String> excludedStatuses);

    @Query(value = "SELECT DISTINCT r.redemption_id FROM redemption_returns r WHERE r.client_id = :clientId AND r.deleted = false AND r.redemption_id IN :redemptionIds AND CAST(r.status AS text) NOT IN :excludedStatuses",
           nativeQuery = true)
    Set<UUID> findRedemptionIdsWithActiveReturns(
            @Param("redemptionIds") Collection<UUID> redemptionIds,
            @Param("clientId") UUID clientId,
            @Param("excludedStatuses") List<String> excludedStatuses);

    Optional<RedemptionReturn> findByVendorReturnReference(String vendorReturnReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RedemptionReturn r WHERE r.id = :id")
    Optional<RedemptionReturn> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT * FROM redemption_returns r WHERE r.client_id = :clientId AND r.deleted = false AND CAST(r.status AS text) = :status AND r.approved_at < :cutoff",
           countQuery = "SELECT COUNT(*) FROM redemption_returns r WHERE r.client_id = :clientId AND r.deleted = false AND CAST(r.status AS text) = :status AND r.approved_at < :cutoff",
           nativeQuery = true)
    Page<RedemptionReturn> findApprovedTimedOut(
            @Param("clientId") UUID clientId,
            @Param("cutoff") Instant cutoff,
            @Param("status") String status,
            Pageable pageable);

    default Page<RedemptionReturn> findApprovedTimedOut(UUID clientId, Instant cutoff, Pageable pageable) {
        return findApprovedTimedOut(clientId, cutoff, ReturnStatus.APPROVED.name(), pageable);
    }

    /**
     * Admin list query with optional status, startDate (createdAt >=), endDate (createdAt <=) filters.
     * Null params are treated as "no filter" via JPQL conditional expressions.
     * @SQLRestriction handles deleted=false automatically — no explicit predicate needed.
     *
     * Uses a native query to avoid PostgreSQL JDBC type-inference errors on null Instant parameters.
     * When startDate/endDate are null, the COALESCE falls back to a sentinel that always passes.
     * The @SQLRestriction (deleted=false) is a Hibernate filter and does NOT apply to native queries —
     * the WHERE clause must include it explicitly.
     */
    @Query(value = "SELECT * FROM public.redemption_returns r WHERE r.client_id = :clientId " +
                   "AND r.deleted = false " +
                   "AND COALESCE(CAST(:status AS text), CAST(r.status AS text)) = CAST(r.status AS text) " +
                   "AND r.created_at >= COALESCE(CAST(:startDate AS TIMESTAMPTZ), r.created_at) " +
                   "AND r.created_at < COALESCE(CAST(:endDate AS TIMESTAMPTZ), r.created_at + interval '1 second')",
           countQuery = "SELECT COUNT(*) FROM public.redemption_returns r WHERE r.client_id = :clientId " +
                        "AND r.deleted = false " +
                        "AND COALESCE(CAST(:status AS text), CAST(r.status AS text)) = CAST(r.status AS text) " +
                        "AND r.created_at >= COALESCE(CAST(:startDate AS TIMESTAMPTZ), r.created_at) " +
                        "AND r.created_at < COALESCE(CAST(:endDate AS TIMESTAMPTZ), r.created_at + interval '1 second')",
           nativeQuery = true)
    Page<RedemptionReturn> findByClientIdWithFilters(
            @Param("clientId") UUID clientId,
            @Param("status") String status,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);
}
