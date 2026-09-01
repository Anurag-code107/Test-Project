package com.tenxengage.app.repository;

import com.tenxengage.app.entity.BudgetUtilization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetUtilizationRepository extends JpaRepository<BudgetUtilization, UUID> {

    Optional<BudgetUtilization> findByIncentiveIdAndCurrencyIdAndLocationValueId(
            UUID incentiveId, String currencyId, UUID locationValueId);

    Optional<BudgetUtilization> findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNull(
            UUID incentiveId, String currencyId);

    List<BudgetUtilization> findByIncentiveId(UUID incentiveId);

    List<BudgetUtilization> findByIncentiveIdAndCurrencyId(UUID incentiveId, String currencyId);

    /**
     * Atomically ensures a BudgetUtilization row exists for the given key.
     * Uses INSERT ... ON CONFLICT DO NOTHING so concurrent first-callers never race to create the row.
     * Always follow this with findByIncentiveIdAndCurrencyId...ForUpdate to acquire the row lock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value =
        "INSERT INTO budget_utilizations (id, incentive_id, currency_id, location_value_id, utilized, created_at, updated_at) " +
        "VALUES (gen_random_uuid(), :incentiveId, :currencyId, :locationValueId, 0, NOW(), NOW()) " +
        "ON CONFLICT DO NOTHING")
    void ensureExists(@Param("incentiveId") UUID incentiveId,
                      @Param("currencyId") String currencyId,
                      @Param("locationValueId") UUID locationValueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bu FROM BudgetUtilization bu WHERE bu.incentiveId = :incentiveId AND bu.currencyId = :currencyId AND bu.locationValueId = :locationValueId")
    Optional<BudgetUtilization> findByIncentiveIdAndCurrencyIdAndLocationValueIdForUpdate(
            @Param("incentiveId") UUID incentiveId, @Param("currencyId") String currencyId, @Param("locationValueId") UUID locationValueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bu FROM BudgetUtilization bu WHERE bu.incentiveId = :incentiveId AND bu.currencyId = :currencyId AND bu.locationValueId IS NULL")
    Optional<BudgetUtilization> findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNullForUpdate(
            @Param("incentiveId") UUID incentiveId, @Param("currencyId") String currencyId);
}
