package com.tenxengage.app.repository;

import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-recipient distribution lines — the record of who was paid.
 */
@Repository
public interface CompanyDistributionItemRepository extends JpaRepository<CompanyDistributionItem, UUID> {

    /** "Who did I distribute to?" — the detail view of one distribution. */
    List<CompanyDistributionItem> findByDistributionIdOrderByCreatedAtAsc(UUID distributionId);

    /** Bulk-load items for a page of headers, so the list view avoids an N+1 on status rollup. */
    List<CompanyDistributionItem> findByDistributionIdIn(List<UUID> distributionIds);

    /** "What did I receive?" — Company Award History for one seller. */
    @Query("""
            SELECT i FROM CompanyDistributionItem i
            WHERE i.clientId = :clientId
              AND i.recipientUserId = :recipientUserId
            """)
    Page<CompanyDistributionItem> findAwardsForRecipient(
            @Param("clientId") UUID clientId,
            @Param("recipientUserId") UUID recipientUserId,
            Pageable pageable);

    /** One award, scoped to its recipient — a seller can never read another seller's award by id. */
    Optional<CompanyDistributionItem> findByIdAndClientIdAndRecipientUserId(
            UUID id, UUID clientId, UUID recipientUserId);

    /** Resolve the item that owns a payout leg, so webhook/reconciliation outcomes can be traced back. */
    Optional<CompanyDistributionItem> findByRedemptionRequestId(UUID redemptionRequestId);

    /**
     * Lock one item before settling it. The status check after the lock is what makes the settle loop and
     * the recovery sweep safe to run concurrently — the loser sees a non-RESERVED row and skips.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM CompanyDistributionItem i WHERE i.id = :id")
    Optional<CompanyDistributionItem> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Wallet-credit items still earmarked. Drives the stuck-item sweep, backed by the partial index
     * {@code idx_distribution_items_unsettled}. Required: the existing crash-recovery sweep scans only
     * {@code redemption_requests}, so without this a crash mid-settlement would leave a recipient's share
     * reserved on the company wallet indefinitely.
     */
    List<CompanyDistributionItem> findByClientIdAndStatusAndCreatedAtBefore(
            UUID clientId, DistributionItemStatus status, Instant createdBefore);


    /** Guard against a recipient being included twice in one distribution. */
    boolean existsByDistributionIdAndRecipientUserId(UUID distributionId, UUID recipientUserId);
}
