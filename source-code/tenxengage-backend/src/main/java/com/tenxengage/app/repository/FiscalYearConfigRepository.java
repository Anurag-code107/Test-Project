package com.tenxengage.app.repository;

import com.tenxengage.app.entity.FiscalYearConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FiscalYearConfigRepository extends JpaRepository<FiscalYearConfig, UUID> {

    List<FiscalYearConfig> findByClientIdOrderByStartDateAsc(UUID clientId);

    Optional<FiscalYearConfig> findByClientIdAndLabel(UUID clientId, String label);

    boolean existsByClientIdAndLabel(UUID clientId, String label);
}
