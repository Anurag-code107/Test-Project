package com.tenxengage.app.repository;

import com.tenxengage.app.entity.LocationLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LocationLevelRepository extends JpaRepository<LocationLevel, UUID> {

    List<LocationLevel> findByClientIdOrderByDepthAsc(UUID clientId);

    List<LocationLevel> findByClientIdAndUseInBuilderTrueOrderByDepthAsc(UUID clientId);

    List<LocationLevel> findByClientIdAndUseInFiltersTrueOrderByDepthAsc(UUID clientId);

    boolean existsByClientIdAndName(UUID clientId, String name);
}
