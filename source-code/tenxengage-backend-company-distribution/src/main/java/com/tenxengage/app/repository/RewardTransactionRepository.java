package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RewardTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, UUID> {

    /**
     * Projection row for the user-facing transaction history. Joins to Incentive (for the display
     * name) and to ClaimAction → PurchaseOrder (for the order number). PurchaseOrder fields are
     * nullable because not every reward transaction has a claimActionId (e.g. completion-driven
     * credits in the future).
     */
    interface UserTransactionRow {
        UUID getId();
        Instant getCreatedAt();
        String getCurrencyId();
        BigDecimal getAmountAwarded();
        UUID getIncentiveId();
        String getIncentiveName();
        UUID getClaimActionId();
        String getOrderNumber();
    }

    @Query("SELECT rt.id AS id, rt.createdAt AS createdAt, rt.currencyId AS currencyId, " +
           "       rt.amountAwarded AS amountAwarded, rt.incentiveId AS incentiveId, " +
           "       i.name AS incentiveName, rt.claimActionId AS claimActionId, " +
           "       po.orderNumber AS orderNumber " +
           "FROM RewardTransaction rt " +
           "JOIN Incentive i ON i.id = rt.incentiveId " +
           "LEFT JOIN ClaimAction ca ON ca.id = rt.claimActionId " +
           "LEFT JOIN PurchaseOrder po ON po.id = ca.purchaseOrderId " +
           "WHERE rt.clientId = :clientId AND rt.userId = :userId " +
           "AND rt.createdAt >= :startDate AND rt.createdAt < :endDate")
    Page<UserTransactionRow> findUserTransactions(
            @Param("clientId") UUID clientId,
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    Optional<RewardTransaction> findByCompletionIdAndCurrencyId(UUID completionId, String currencyId);

    Optional<RewardTransaction> findByClaimActionIdAndCurrencyId(UUID claimActionId, String currencyId);

    List<RewardTransaction> findByClaimActionId(UUID claimActionId);

    void deleteByClaimActionId(UUID claimActionId);

    List<RewardTransaction> findByClientIdAndUserId(UUID clientId, UUID userId);

    List<RewardTransaction> findByClientId(UUID clientId);

    @Query("SELECT rt FROM RewardTransaction rt JOIN ClaimAction ca ON ca.id = rt.claimActionId " +
           "WHERE rt.clientId = :clientId AND ca.purchaseOrderId = :poId")
    List<RewardTransaction> findByClientIdAndPurchaseOrderId(
            @Param("clientId") UUID clientId, @Param("poId") UUID poId);

    @Query("SELECT rt FROM RewardTransaction rt JOIN ClaimAction ca ON ca.id = rt.claimActionId " +
           "WHERE rt.clientId = :clientId AND ca.purchaseOrderId = :poId AND rt.userId = :userId")
    List<RewardTransaction> findByClientIdAndPurchaseOrderIdAndUserId(
            @Param("clientId") UUID clientId, @Param("poId") UUID poId, @Param("userId") UUID userId);

    List<RewardTransaction> findByClientIdAndIncentiveId(UUID clientId, UUID incentiveId);

    /**
     * Sum total awarded for a partner company across an incentive (all currencies).
     * Joins through User so both claim-based and completion-based transactions are counted.
     */
    @Query("SELECT COALESCE(SUM(rt.amountAwarded), 0) FROM RewardTransaction rt " +
           "JOIN User u ON u.id = rt.userId " +
           "WHERE rt.clientId = :clientId AND rt.incentiveId = :incentiveId " +
           "AND u.partnerCompanyId = :partnerCompanyId")
    BigDecimal sumAwardedByClientIdAndIncentiveIdAndPartnerCompanyId(
            @Param("clientId") UUID clientId,
            @Param("incentiveId") UUID incentiveId,
            @Param("partnerCompanyId") UUID partnerCompanyId);

    /**
     * Sum total awarded for a partner company on a specific incentive and currency.
     * Joins through User so both claim-based and completion-based transactions are counted.
     */
    @Query("SELECT COALESCE(SUM(rt.amountAwarded), 0) FROM RewardTransaction rt " +
           "JOIN User u ON u.id = rt.userId " +
           "WHERE rt.clientId = :clientId AND rt.incentiveId = :incentiveId " +
           "AND u.partnerCompanyId = :partnerCompanyId AND rt.currencyId = :currencyId")
    BigDecimal sumAwardedByClientIdAndIncentiveIdAndPartnerCompanyIdAndCurrencyId(
            @Param("clientId") UUID clientId,
            @Param("incentiveId") UUID incentiveId,
            @Param("partnerCompanyId") UUID partnerCompanyId,
            @Param("currencyId") String currencyId);

    /**
     * Sum total awarded for a user on a specific incentive and currency, as a single DB aggregate.
     */
    @Query("SELECT COALESCE(SUM(rt.amountAwarded), 0) FROM RewardTransaction rt " +
           "WHERE rt.clientId = :clientId AND rt.userId = :userId " +
           "AND rt.incentiveId = :incentiveId AND rt.currencyId = :currencyId")
    BigDecimal sumAwardedByUserAndIncentiveAndCurrency(
            @Param("clientId") UUID clientId,
            @Param("userId") UUID userId,
            @Param("incentiveId") UUID incentiveId,
            @Param("currencyId") String currencyId);

    /**
     * Acquires a transaction-scoped PostgreSQL advisory lock on (incentiveId:userId).
     * Serializes concurrent grantReward() calls for the same user+incentive, preventing
     * per-user and per-partner cap overrun from concurrent SUM reads.
     * Lock is released automatically when the surrounding @Transactional commits or rolls back.
     */
    @Query(nativeQuery = true, value = "SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
    List<Object> acquireGrantLock(@Param("lockKey") String lockKey);

    /**
     * Sum total awarded for a user within a date range (used for annual compliance cap checks).
     */
    @Query("SELECT COALESCE(SUM(rt.amountAwarded), 0) FROM RewardTransaction rt " +
           "WHERE rt.clientId = :clientId AND rt.userId = :userId " +
           "AND rt.createdAt >= :startDate AND rt.createdAt < :endDate")
    BigDecimal sumAwardedByUserAndDateRange(
            @Param("clientId") UUID clientId,
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);
}
