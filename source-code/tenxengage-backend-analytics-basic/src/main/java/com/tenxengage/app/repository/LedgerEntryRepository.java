package com.tenxengage.app.repository;

import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByRewardWalletIdAndReferenceTypeAndReferenceId(
            UUID rewardWalletId, String referenceType, UUID referenceId);

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
}
