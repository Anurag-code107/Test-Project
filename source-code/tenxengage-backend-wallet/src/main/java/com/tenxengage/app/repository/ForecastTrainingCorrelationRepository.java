package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ForecastTrainingCorrelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ForecastTrainingCorrelationRepository extends JpaRepository<ForecastTrainingCorrelation, UUID> {

    List<ForecastTrainingCorrelation> findByClientId(UUID clientId);

    List<ForecastTrainingCorrelation> findByClientIdAndProductCategoryIn(UUID clientId, List<String> productCategories);

    void deleteByClientId(UUID clientId);
}
