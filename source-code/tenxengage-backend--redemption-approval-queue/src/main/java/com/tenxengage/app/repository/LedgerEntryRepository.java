package com.tenxengage.app.repository;

import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByRewardWalletIdAndReferenceTypeAndReferenceId(
            UUID rewardWalletId, String referenceType, UUID referenceId);

    boolean existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
            UUID rewardWalletId, String referenceType, UUID referenceId, LedgerEntryType entryType);

    Page<LedgerEntry> findByRewardWalletId(UUID rewardWalletId, Pageable pageable);

    Page<LedgerEntry> findByClientId(UUID clientId, Pageable pageable);
}
