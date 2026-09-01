package com.tenxengage.app.repository;

import com.tenxengage.app.entity.UserLegalAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserLegalAcceptanceRepository extends JpaRepository<UserLegalAcceptance, UUID> {

    Optional<UserLegalAcceptance> findByUserIdAndPolicyId(UUID userId, UUID policyId);

    List<UserLegalAcceptance> findByUserId(UUID userId);

    boolean existsByUserIdAndPolicyId(UUID userId, UUID policyId);
}
