package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClientFeatureOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientFeatureOverrideRepository extends JpaRepository<ClientFeatureOverride, UUID> {

    List<ClientFeatureOverride> findByClientId(UUID clientId);

    void deleteByClientId(UUID clientId);
}
