package com.tenxengage.app.repository;

import com.tenxengage.app.entity.LegalPolicy;
import com.tenxengage.app.entity.enums.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LegalPolicyRepository extends JpaRepository<LegalPolicy, UUID> {

    List<LegalPolicy> findByClientIdAndActiveTrue(UUID clientId);

    Optional<LegalPolicy> findByClientIdAndPolicyTypeAndActiveTrue(UUID clientId, PolicyType policyType);
}
