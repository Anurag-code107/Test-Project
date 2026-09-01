package com.tenxengage.app.repository;

import com.tenxengage.app.entity.OnboardingToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnboardingTokenRepository extends JpaRepository<OnboardingToken, UUID> {

    Optional<OnboardingToken> findByTokenHash(String tokenHash);

    Optional<OnboardingToken> findByUserId(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM OnboardingToken t WHERE t.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
