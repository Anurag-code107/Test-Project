package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ForecastSalesAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ForecastSalesAggregateRepository extends JpaRepository<ForecastSalesAggregate, UUID> {

    List<ForecastSalesAggregate> findByClientIdAndLocationValueIdIn(UUID clientId, List<UUID> locationValueIds);

    List<ForecastSalesAggregate> findByClientIdAndProductCategoryIn(UUID clientId, List<String> productCategories);

    List<ForecastSalesAggregate> findByClientIdAndLocationValueIdInAndProductCategoryIn(
            UUID clientId, List<UUID> locationValueIds, List<String> productCategories);

    List<ForecastSalesAggregate> findByClientIdAndYearMonthBetween(
            UUID clientId, LocalDate start, LocalDate end);

    List<ForecastSalesAggregate> findByClientIdAndLocationValueIdInAndYearMonthBetween(
            UUID clientId, List<UUID> locationValueIds, LocalDate start, LocalDate end);

    @Modifying
    @Query("DELETE FROM ForecastSalesAggregate f WHERE f.clientId = :clientId")
    void deleteByClientId(UUID clientId);
}
