package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ActivityCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityCategoryRepository extends JpaRepository<ActivityCategory, UUID> {

    List<ActivityCategory> findByClientIdOrderBySortOrder(UUID clientId);

    boolean existsByClientIdAndName(UUID clientId, String name);
}
