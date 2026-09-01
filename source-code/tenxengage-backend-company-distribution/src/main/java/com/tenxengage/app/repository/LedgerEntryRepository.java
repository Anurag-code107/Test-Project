package com.tenxengage.app.repository;

import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.repository.projection.BreakageRowProjection;
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
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByRewardWalletIdAndReferenceTypeAndReferenceId(
            UUID rewardWalletId, String referenceType, UUID referenceId);

    /**
     * The existing entry for one (wallet, reference, type) triple, when the caller needs its <b>id</b> and not
     * just whether it exists — a settle retry stamps the already-written entry onto its item rather than
     * writing a second one.
     */
    Optional<LedgerEntry> findFirstByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
            UUID rewardWalletId, String referenceType, UUID referenceId, LedgerEntryType entryType);

    boolean existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
            UUID rewardWalletId, String referenceType, UUID referenceId, LedgerEntryType entryType);

    Page<LedgerEntry> findByRewardWalletId(UUID rewardWalletId, Pageable pageable);

    Page<LedgerEntry> findByClientId(UUID clientId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
           "WHERE e.clientId = :clientId AND e.currencyId = :currencyId AND e.entryType = :entryType")
    BigDecimal sumAmountByClientIdAndCurrencyIdAndEntryType(
            @Param("clientId") UUID clientId,
            @Param("currencyId") String currencyId,
            @Param("entryType") LedgerEntryType entryType);

    @Query("SELECT DISTINCT w.currencyId FROM RewardWallet w WHERE w.clientId = :clientId")
    List<String> findDistinctCurrencyIdsByClientId(@Param("clientId") UUID clientId);

    // Note: use cast(... AS date), NOT ::date — the `::` PostgreSQL cast operator collides with
    // Hibernate's `:param` named-parameter parsing in a native query and yields a syntax error.
    @Query(value = "SELECT cast(date_trunc(:bucket, e.created_at) AS date) AS \"periodStart\", e.currency_id AS \"currencyId\", COUNT(*) AS \"expiredCount\", COALESCE(SUM(e.amount),0) AS \"totalExpiredAmount\" FROM ledger_entries e WHERE e.client_id = :clientId AND e.entry_type = 'EXPIRY' AND e.created_at >= :from AND e.created_at < :to AND (cast(:currencyId AS text) IS NULL OR e.currency_id = :currencyId) GROUP BY 1, e.currency_id ORDER BY 1, e.currency_id", nativeQuery = true)
    List<BreakageRowProjection> aggregateExpiryBreakage(
            @Param("clientId") UUID clientId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("currencyId") String currencyId,
            @Param("bucket") String bucket);
}
