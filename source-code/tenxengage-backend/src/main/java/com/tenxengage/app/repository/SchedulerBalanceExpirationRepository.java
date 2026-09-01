package com.tenxengage.app.repository;

import com.tenxengage.app.entity.BalanceExpirationPolicy;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.repository.projection.WalletLastActivityProjection;
import jakarta.persistence.LockModeType;
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
import java.util.UUID;

// Cross-tenant sweep repository — tenantFilter deliberately NOT enabled by callers.
// findAllByEnabledTrueAndDeletedFalse is the entry point; every per-wallet query binds clientId explicitly.
// See spec.md § Security Design for the documented isolation deviation.
@Repository
public interface SchedulerBalanceExpirationRepository extends JpaRepository<BalanceExpirationPolicy, UUID> {

    List<BalanceExpirationPolicy> findAllByEnabledTrueAndDeletedFalse();

    // AC-10: bounded/paged candidate query. Callers iterate pages (PageRequest) so a large tenant
    // never loads all candidate wallets into memory at once. Order by id for stable paging.
    @Query("SELECT w FROM RewardWallet w WHERE w.clientId = :clientId AND w.currencyId = :currencyId AND w.availableBalance > 0 ORDER BY w.id")
    List<RewardWallet> findExpiryCandidateWallets(@Param("clientId") UUID clientId,
                                                  @Param("currencyId") String currencyId,
                                                  Pageable pageable);

    // AC-9: bulk last-activity — one GROUP BY over a page's wallets instead of an N+1 per-wallet query.
    @Query("SELECT e.rewardWalletId AS walletId, MAX(e.createdAt) AS lastActivityAt FROM LedgerEntry e "
            + "WHERE e.clientId = :clientId AND e.currencyId = :currencyId "
            + "AND e.rewardWalletId IN :walletIds AND e.entryType IN :activityTypes "
            + "GROUP BY e.rewardWalletId")
    List<WalletLastActivityProjection> findLastActivityForWallets(@Param("clientId") UUID clientId,
                                                                  @Param("currencyId") String currencyId,
                                                                  @Param("walletIds") Collection<UUID> walletIds,
                                                                  @Param("activityTypes") Collection<LedgerEntryType> activityTypes);

    // AC-9 (expiry revalidation): live last-activity for a single wallet, used under the wallet lock
    // in the expire phase to detect a notice whose inactivity clock moved after the warning.
    @Query("SELECT MAX(e.createdAt) FROM LedgerEntry e "
            + "WHERE e.clientId = :clientId AND e.currencyId = :currencyId "
            + "AND e.rewardWalletId = :walletId AND e.entryType IN :activityTypes")
    Instant findLastActivityAt(@Param("clientId") UUID clientId,
                               @Param("currencyId") String currencyId,
                               @Param("walletId") UUID walletId,
                               @Param("activityTypes") Collection<LedgerEntryType> activityTypes);

    // Row-lock for atomic expiry (AC-3, FR-09.11). JPQL + PESSIMISTIC_WRITE (not a native SELECT *):
    // a native query mapping to RewardWallet fails ("Object[] → RewardWallet") because two entities
    // (RewardWallet + the deprecated RewardBalance) map to reward_wallets — JPQL names the entity
    // explicitly and @Lock emits `FOR UPDATE`.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM RewardWallet w WHERE w.id = :walletId AND w.clientId = :clientId")
    Optional<RewardWallet> lockWallet(@Param("walletId") UUID walletId, @Param("clientId") UUID clientId);
}
