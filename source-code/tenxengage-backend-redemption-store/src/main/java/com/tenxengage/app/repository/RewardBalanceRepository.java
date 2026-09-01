package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RewardBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Deprecated
@Repository
public interface RewardBalanceRepository extends JpaRepository<RewardBalance, UUID> {

    Optional<RewardBalance> findByClientIdAndUserIdAndCurrencyId(UUID clientId, UUID userId, String currencyId);

    List<RewardBalance> findByClientIdAndUserId(UUID clientId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rb FROM RewardBalance rb WHERE rb.clientId = :clientId AND rb.userId = :userId AND rb.currencyId = :currencyId")
    Optional<RewardBalance> findByClientIdAndUserIdAndCurrencyIdForUpdate(
            @Param("clientId") UUID clientId, @Param("userId") UUID userId, @Param("currencyId") String currencyId);
}
