package com.tenxengage.app.repository.redemption;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface RedemptionHistoryRepository extends JpaRepository<RedemptionRequest, UUID> {

    @Query("SELECT r FROM RedemptionRequest r " +
           "JOIN RewardWallet w ON r.walletId = w.id " +
           "WHERE r.clientId = :clientId AND r.userId = :userId " +
           "AND w.walletType = com.tenxengage.app.entity.enums.WalletType.INDIVIDUAL " +
           "AND COALESCE(:status, r.status) = r.status " +
           "AND COALESCE(:category, r.category) = r.category " +
           "AND r.submittedAt >= COALESCE(:dateFrom, r.submittedAt) " +
           "AND r.submittedAt <= COALESCE(:dateTo, r.submittedAt)")
    Page<RedemptionRequest> findPersonalHistory(
            @Param("userId") UUID userId,
            @Param("clientId") UUID clientId,
            @Param("status") RedemptionStatus status,
            @Param("category") RedemptionCategory category,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);

    @Query("SELECT COUNT(r) FROM RedemptionRequest r " +
           "JOIN RewardWallet w ON r.walletId = w.id " +
           "WHERE r.clientId = :clientId AND r.userId = :userId " +
           "AND w.walletType = com.tenxengage.app.entity.enums.WalletType.INDIVIDUAL " +
           "AND COALESCE(:status, r.status) = r.status " +
           "AND COALESCE(:category, r.category) = r.category " +
           "AND r.submittedAt >= COALESCE(:dateFrom, r.submittedAt) " +
           "AND r.submittedAt <= COALESCE(:dateTo, r.submittedAt)")
    long countPersonalHistory(
            @Param("userId") UUID userId,
            @Param("clientId") UUID clientId,
            @Param("status") RedemptionStatus status,
            @Param("category") RedemptionCategory category,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo);

    // Filters by a specific wallet id (kept for backward-compat; prefer findCompanyHistoryByPartnerCompany)
    @Query("SELECT r FROM RedemptionRequest r " +
           "WHERE r.clientId = :clientId AND r.walletId = :walletId " +
           "AND COALESCE(:status, r.status) = r.status " +
           "AND COALESCE(:category, r.category) = r.category " +
           "AND r.submittedAt >= COALESCE(:dateFrom, r.submittedAt) " +
           "AND r.submittedAt <= COALESCE(:dateTo, r.submittedAt)")
    Page<RedemptionRequest> findCompanyHistory(
            @Param("walletId") UUID walletId,
            @Param("clientId") UUID clientId,
            @Param("status") RedemptionStatus status,
            @Param("category") RedemptionCategory category,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);

    // Returns all company-wallet redemptions for a partner company across ALL currencies.
    // Scoped by wallet.partnerCompanyId (not user.partnerCompanyId) so historical rows
    // remain visible even if the submitting user is later reassigned to another company.
    @Query(value =
           "SELECT r FROM RedemptionRequest r " +
           "JOIN RewardWallet w ON r.walletId = w.id " +
           "WHERE r.clientId = :clientId " +
           "AND w.partnerCompanyId = :partnerCompanyId " +
           "AND w.walletType = com.tenxengage.app.entity.enums.WalletType.COMPANY " +
           "AND COALESCE(:status, r.status) = r.status " +
           "AND COALESCE(:category, r.category) = r.category " +
           "AND r.submittedAt >= COALESCE(:dateFrom, r.submittedAt) " +
           "AND r.submittedAt <= COALESCE(:dateTo, r.submittedAt)",
           countQuery =
           "SELECT COUNT(r) FROM RedemptionRequest r " +
           "JOIN RewardWallet w ON r.walletId = w.id " +
           "WHERE r.clientId = :clientId " +
           "AND w.partnerCompanyId = :partnerCompanyId " +
           "AND w.walletType = com.tenxengage.app.entity.enums.WalletType.COMPANY " +
           "AND COALESCE(:status, r.status) = r.status " +
           "AND COALESCE(:category, r.category) = r.category " +
           "AND r.submittedAt >= COALESCE(:dateFrom, r.submittedAt) " +
           "AND r.submittedAt <= COALESCE(:dateTo, r.submittedAt)")
    Page<RedemptionRequest> findCompanyHistoryByPartnerCompany(
            @Param("clientId") UUID clientId,
            @Param("partnerCompanyId") UUID partnerCompanyId,
            @Param("status") RedemptionStatus status,
            @Param("category") RedemptionCategory category,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);

    @Query("SELECT r FROM RedemptionRequest r " +
           "WHERE r.clientId = :clientId " +
           "AND (:userId IS NULL OR r.userId = :userId) " +
           "AND (:companyId IS NULL OR r.user.partnerCompanyId = :companyId) " +
           "AND COALESCE(:status, r.status) = r.status " +
           "AND COALESCE(:category, r.category) = r.category " +
           "AND r.submittedAt >= COALESCE(:dateFrom, r.submittedAt) " +
           "AND r.submittedAt <= COALESCE(:dateTo, r.submittedAt)")
    Page<RedemptionRequest> findTenantHistory(
            @Param("clientId") UUID clientId,
            @Param("userId") UUID userId,
            @Param("companyId") UUID companyId,
            @Param("status") RedemptionStatus status,
            @Param("category") RedemptionCategory category,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);

    @Query("SELECT COUNT(r) FROM RedemptionRequest r " +
           "WHERE r.clientId = :clientId " +
           "AND (:userId IS NULL OR r.userId = :userId) " +
           "AND COALESCE(:status, r.status) = r.status " +
           "AND COALESCE(:category, r.category) = r.category " +
           "AND r.submittedAt >= COALESCE(:dateFrom, r.submittedAt) " +
           "AND r.submittedAt <= COALESCE(:dateTo, r.submittedAt)")
    long countTenantHistory(
            @Param("clientId") UUID clientId,
            @Param("userId") UUID userId,
            @Param("status") RedemptionStatus status,
            @Param("category") RedemptionCategory category,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo);
}
