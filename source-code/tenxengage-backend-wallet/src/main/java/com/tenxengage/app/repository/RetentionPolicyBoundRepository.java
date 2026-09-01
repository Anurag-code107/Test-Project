package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RetentionPolicyBound;
import com.tenxengage.app.entity.enums.DataCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionPolicyBoundRepository extends JpaRepository<RetentionPolicyBound, UUID> {

    Optional<RetentionPolicyBound> findByDataCategory(DataCategory dataCategory);
}
