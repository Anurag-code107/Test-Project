package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ForecastRegionDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ForecastRegionDistributionRepository extends JpaRepository<ForecastRegionDistribution, UUID> {

    List<ForecastRegionDistribution> findByClientId(UUID clientId);

    List<ForecastRegionDistribution> findByClientIdAndLocationValueIdIn(UUID clientId, List<UUID> locationValueIds);

    @Modifying
    @Query("DELETE FROM ForecastRegionDistribution f WHERE f.clientId = :clientId")
    void deleteByClientId(UUID clientId);
}
