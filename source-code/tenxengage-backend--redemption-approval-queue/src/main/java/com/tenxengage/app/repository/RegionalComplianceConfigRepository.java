package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RegionalComplianceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegionalComplianceConfigRepository extends JpaRepository<RegionalComplianceConfig, UUID> {

    Optional<RegionalComplianceConfig> findByRegionCode(String regionCode);
}
