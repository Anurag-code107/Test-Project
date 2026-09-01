package com.tenxengage.app.repository;

import com.tenxengage.app.entity.BuilderSectionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuilderSectionConfigRepository extends JpaRepository<BuilderSectionConfig, UUID> {

    List<BuilderSectionConfig> findByClientIdAndIncentiveTypeOrderBySortOrder(UUID clientId, String incentiveType);

    Optional<BuilderSectionConfig> findByClientIdAndIncentiveTypeAndSectionKey(
            UUID clientId, String incentiveType, String sectionKey);
}
