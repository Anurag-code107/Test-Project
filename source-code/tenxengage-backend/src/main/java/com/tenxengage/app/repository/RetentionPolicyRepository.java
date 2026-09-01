package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RetentionPolicy;
import com.tenxengage.app.entity.enums.DataCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

    List<RetentionPolicy> findByClientId(UUID clientId);

    List<RetentionPolicy> findByClientIdIsNull();

    Optional<RetentionPolicy> findByClientIdAndDataCategory(UUID clientId, DataCategory dataCategory);
}
