package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClientCatalogRegionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientCatalogRegionConfigRepository extends JpaRepository<ClientCatalogRegionConfig, UUID> {

    List<ClientCatalogRegionConfig> findByClientIdAndRedemptionCatalogItemId(UUID clientId, UUID redemptionCatalogItemId);

    Optional<ClientCatalogRegionConfig> findByClientIdAndRedemptionCatalogItemIdAndRegionCode(
            UUID clientId, UUID redemptionCatalogItemId, String regionCode);

    void deleteByClientIdAndRedemptionCatalogItemIdAndRegionCode(
            UUID clientId, UUID redemptionCatalogItemId, String regionCode);

    boolean existsByRedemptionCatalogItemIdAndRegionCode(UUID redemptionCatalogItemId, String regionCode);

    List<ClientCatalogRegionConfig> findByClientIdAndRedemptionCatalogItemIdIn(
            UUID clientId, Collection<UUID> redemptionCatalogItemIds);
}
