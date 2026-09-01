package com.tenxengage.app.repository;

import com.tenxengage.app.entity.BalanceExpiryNotice;
import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;
import com.tenxengage.app.repository.projection.ExpiringBalancePreviewProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BalanceExpiryNoticeRepository extends JpaRepository<BalanceExpiryNotice, UUID> {

    Optional<BalanceExpiryNotice> findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
            UUID clientId, UUID walletId, String currencyId, LocalDate scheduledExpiryDate);

    List<BalanceExpiryNotice> findByClientIdAndStatusAndScheduledExpiryDateLessThanEqual(
            UUID clientId, ExpiryNoticeStatus status, LocalDate date);

    /** Expire-phase query: fetches only notices governed by the given policy, avoiding cross-policy over-fetch. */
    List<BalanceExpiryNotice> findByClientIdAndPolicyIdAndStatusAndScheduledExpiryDateLessThanEqual(
            UUID clientId, UUID policyId, ExpiryNoticeStatus status, LocalDate date);

    List<BalanceExpiryNotice> findByClientIdAndPolicyIdAndStatusIn(
            UUID clientId, UUID policyId, Collection<ExpiryNoticeStatus> statuses);

    @Query("SELECT n.currencyId AS currencyId, n.scheduledExpiryDate AS scheduledExpiryDate, " +
           "COUNT(n) AS affectedWalletCount, COALESCE(SUM(n.notifiedAmount), 0) AS totalAmountAtRisk " +
           "FROM BalanceExpiryNotice n " +
           "WHERE n.clientId = :clientId " +
           "AND n.status IN :statuses " +
           "AND n.scheduledExpiryDate <= :upToDate " +
           "AND (:currencyId IS NULL OR n.currencyId = :currencyId) " +
           "GROUP BY n.currencyId, n.scheduledExpiryDate " +
           "ORDER BY n.scheduledExpiryDate, n.currencyId")
    List<ExpiringBalancePreviewProjection> aggregateExpiringSoon(
            @Param("clientId") UUID clientId,
            @Param("statuses") Collection<ExpiryNoticeStatus> statuses,
            @Param("upToDate") LocalDate upToDate,
            @Param("currencyId") String currencyId);
}
