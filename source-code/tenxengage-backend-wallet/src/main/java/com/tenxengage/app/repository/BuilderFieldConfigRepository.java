package com.tenxengage.app.repository;

import com.tenxengage.app.entity.BuilderFieldConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BuilderFieldConfigRepository extends JpaRepository<BuilderFieldConfig, UUID> {

    List<BuilderFieldConfig> findBySectionConfigIdOrderBySortOrder(UUID sectionConfigId);

    @Query("SELECT f FROM BuilderFieldConfig f JOIN f.sectionConfig s "
            + "WHERE s.clientId = :clientId "
            + "AND s.incentiveType = :incentiveType "
            + "AND s.sectionKey = :sectionKey "
            + "AND f.isEligibility = true "
            + "AND f.isSystem = false "
            + "ORDER BY f.sortOrder")
    List<BuilderFieldConfig> findDynamicEligibilityFields(
            @Param("clientId") UUID clientId,
            @Param("incentiveType") String incentiveType,
            @Param("sectionKey") String sectionKey);
}
