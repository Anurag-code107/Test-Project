package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedemptionCatalogItemRepository extends JpaRepository<RedemptionCatalogItem, UUID> {

    Page<RedemptionCatalogItem> findAllByOrderByNameAsc(Pageable pageable);

    Page<RedemptionCatalogItem> findAllByCategoryAndIsActive(RedemptionCategory category, boolean isActive, Pageable pageable);

    Page<RedemptionCatalogItem> findByCurrencyIdInAndIsActive(Collection<String> currencyIds, boolean isActive, Pageable pageable);

    Optional<RedemptionCatalogItem> findByProviderItemId(String providerItemId);

    List<RedemptionCatalogItem> findAllByIsActive(boolean isActive);

    List<RedemptionCatalogItem> findAllByCategory(RedemptionCategory category);

    long countByCategoryAndIsActive(RedemptionCategory category, boolean isActive);

    @Query("SELECT e FROM RedemptionCatalogItem e WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<RedemptionCatalogItem> searchByName(@Param("q") String q, Pageable pageable);
}
