package com.tenxengage.app.repository;

import com.tenxengage.app.entity.GovernmentSegmentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GovernmentSegmentConfigRepository extends JpaRepository<GovernmentSegmentConfig, UUID> {

    List<GovernmentSegmentConfig> findByClientId(UUID clientId);

    Optional<GovernmentSegmentConfig> findByClientIdAndSegmentValue(UUID clientId, String segmentValue);
}
