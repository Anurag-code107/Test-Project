package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RecommendationInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecommendationInteractionRepository extends JpaRepository<RecommendationInteraction, UUID> {

    boolean existsByClientIdAndUserIdAndTargetIdAndInteractionType(
            UUID clientId, UUID userId, UUID targetId, String interactionType);

    List<RecommendationInteraction> findByClientIdAndUserIdAndInteractionType(
            UUID clientId, UUID userId, String interactionType);

    List<RecommendationInteraction> findByClientIdAndUserIdAndRecommendationTypeAndInteractionType(
            UUID clientId, UUID userId, String recommendationType, String interactionType);
}
