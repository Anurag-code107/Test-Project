package com.tenxengage.app.repository;

import com.tenxengage.app.entity.KycRegionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycRegionConfigRepository extends JpaRepository<KycRegionConfig, UUID> {

    Optional<KycRegionConfig> findByRegionCode(String regionCode);
}
