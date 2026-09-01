package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ForecastIncentiveOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ForecastIncentiveOutcomeRepository extends JpaRepository<ForecastIncentiveOutcome, UUID> {

    List<ForecastIncentiveOutcome> findByClientId(UUID clientId);

    List<ForecastIncentiveOutcome> findByClientIdAndIncentiveType(UUID clientId, String incentiveType);

    @Modifying
    @Query("DELETE FROM ForecastIncentiveOutcome f WHERE f.clientId = :clientId")
    void deleteByClientId(UUID clientId);
}
