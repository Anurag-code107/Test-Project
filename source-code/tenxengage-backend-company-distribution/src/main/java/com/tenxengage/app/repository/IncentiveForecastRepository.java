package com.tenxengage.app.repository;

import com.tenxengage.app.entity.IncentiveForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IncentiveForecastRepository extends JpaRepository<IncentiveForecast, UUID> {

    Optional<IncentiveForecast> findTopByIncentiveIdOrderByGeneratedAtDesc(UUID incentiveId);

    void deleteByIncentiveId(UUID incentiveId);
}
