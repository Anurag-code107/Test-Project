package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RecommendationScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommendationScoreRepository extends JpaRepository<RecommendationScore, UUID> {

    @Query("SELECT rs FROM RecommendationScore rs WHERE rs.clientId = :clientId " +
           "AND rs.userId = :userId AND rs.recommendationType = :type " +
           "AND rs.rank <= :maxRank ORDER BY rs.rank ASC")
    List<RecommendationScore> findTopRecommendations(
            @Param("clientId") UUID clientId,
            @Param("userId") UUID userId,
            @Param("type") String type,
            @Param("maxRank") int maxRank);

    List<RecommendationScore> findByClientIdAndUserIdAndRecommendationTypeOrderByRankAsc(
            UUID clientId, UUID userId, String recommendationType);

    void deleteByClientId(UUID clientId);

    void deleteByClientIdAndRecommendationType(UUID clientId, String recommendationType);
}
