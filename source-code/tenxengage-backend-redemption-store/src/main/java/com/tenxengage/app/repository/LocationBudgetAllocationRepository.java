package com.tenxengage.app.repository;

import com.tenxengage.app.entity.LocationBudgetAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationBudgetAllocationRepository extends JpaRepository<LocationBudgetAllocation, UUID> {

    List<LocationBudgetAllocation> findByBudgetId(UUID budgetId);

    Optional<LocationBudgetAllocation> findByBudgetIdAndLocationValueId(UUID budgetId, UUID locationValueId);

    void deleteByBudgetId(UUID budgetId);
}
