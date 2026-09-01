package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ForecastAccuracyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ForecastAccuracyRepository extends JpaRepository<ForecastAccuracyRecord, UUID> {

    List<ForecastAccuracyRecord> findByClientId(UUID clientId);

    boolean existsByIncentiveIdAndForecastId(UUID incentiveId, UUID forecastId);
}
