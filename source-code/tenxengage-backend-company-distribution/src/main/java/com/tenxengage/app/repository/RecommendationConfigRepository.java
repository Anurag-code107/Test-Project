package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RecommendationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationConfigRepository extends JpaRepository<RecommendationConfig, UUID> {

    Optional<RecommendationConfig> findByClientId(UUID clientId);
}
