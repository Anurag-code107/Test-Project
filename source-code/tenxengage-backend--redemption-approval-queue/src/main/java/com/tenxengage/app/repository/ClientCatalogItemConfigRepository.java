package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClientCatalogItemConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientCatalogItemConfigRepository extends JpaRepository<ClientCatalogItemConfig, UUID> {

    Optional<ClientCatalogItemConfig> findByClientIdAndRedemptionCatalogItemId(UUID clientId, UUID redemptionCatalogItemId);

    Page<ClientCatalogItemConfig> findByClientIdOrderByRedemptionCatalogItemId(UUID clientId, Pageable pageable);

    Page<ClientCatalogItemConfig> findByClientIdAndEnabled(UUID clientId, boolean enabled, Pageable pageable);

    boolean existsByClientIdAndRedemptionCatalogItemId(UUID clientId, UUID redemptionCatalogItemId);

    List<ClientCatalogItemConfig> findByClientIdAndRedemptionCatalogItemIdIn(
            UUID clientId, Collection<UUID> redemptionCatalogItemIds);

    List<ClientCatalogItemConfig> findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(
            UUID clientId, boolean enabled, Collection<UUID> redemptionCatalogItemIds);
}
