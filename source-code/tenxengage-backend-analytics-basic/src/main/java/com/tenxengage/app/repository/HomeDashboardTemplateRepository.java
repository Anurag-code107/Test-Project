package com.tenxengage.app.repository;

import com.tenxengage.app.entity.HomeDashboardTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HomeDashboardTemplateRepository extends JpaRepository<HomeDashboardTemplate, UUID> {

    List<HomeDashboardTemplate> findByClientIdOrderByNameAsc(UUID clientId);

    List<HomeDashboardTemplate> findByClientIdAndRoleTypeOrderByNameAsc(UUID clientId, String roleType);

    Optional<HomeDashboardTemplate> findByClientIdAndName(UUID clientId, String name);

    boolean existsByClientIdAndName(UUID clientId, String name);
}
