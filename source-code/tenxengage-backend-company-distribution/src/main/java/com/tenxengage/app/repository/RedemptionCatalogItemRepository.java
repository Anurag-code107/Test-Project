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

    // ── Client-owned catalog (Model 2) — every read/write scoped to the owning client.
    //    Soft-deleted items are excluded (AndDeletedFalse); the reserved bank-transfer card is
    //    excluded from every catalog-facing read (AndIsBankTransferFalse) — it is reached ONLY via
    //    findByOwnerClientIdAndIsBankTransferTrueAndDeletedFalse (the dedicated payout path).
    //    findById/findAllById stay unfiltered so redemption-history name lookups still resolve
    //    deleted items. ──

    Optional<RedemptionCatalogItem> findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(UUID id, UUID ownerClientId);

    Page<RedemptionCatalogItem> findByOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(UUID ownerClientId, Pageable pageable);

    Page<RedemptionCatalogItem> findByOwnerClientIdAndCategoryAndIsActiveAndDeletedFalseAndIsBankTransferFalse(
            UUID ownerClientId, RedemptionCategory category, boolean isActive, Pageable pageable);

    @Query("SELECT e FROM RedemptionCatalogItem e WHERE e.ownerClientId = :owner AND e.deleted = false " +
           "AND e.isBankTransfer = false AND LOWER(e.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<RedemptionCatalogItem> searchByNameForOwner(
            @Param("owner") UUID owner, @Param("q") String q, Pageable pageable);

    /**
     * The one LIVE catalog row for a SKU — active and not deleted.
     *
     * <p>Deliberately narrower than "not deleted". SKU uniqueness is enforced on the live set only (see the
     * index note below), so a client may hold several rows for one SKU as long as at most one is active:
     * deactivating a card and re-adding it under a corrected name is a normal thing to do. A finder that
     * only excluded deleted rows would then see two, and an {@code Optional} cannot hold two — it threw
     * {@code IncorrectResultSizeDataAccessException} in the middle of a distribution, naming neither the
     * catalog nor the SKU.</p>
     *
     * <p>Matches how the {@code catalogItemId} path in {@code CompanyDistributionService} resolves an item:
     * active, not deleted, owned by the caller. A retired row can never be distributed, so it must not be
     * able to break the lookup either.</p>
     */
    Optional<RedemptionCatalogItem> findByOwnerClientIdAndProviderItemIdAndIsActiveTrueAndDeletedFalse(
            UUID ownerClientId, String providerItemId);

    // providerItemId (SKU) uniqueness is enforced on the LIVE set only: an ACTIVE, non-deleted item
    // with this SKU + category blocks. A retired item (deactivated OR soft-deleted) never blocks
    // reusing its SKU. Owner-scoped — another client reusing the same SKU is fine.
    boolean existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
            UUID ownerClientId, String providerItemId, RedemptionCategory category);

    // Seller browse: only the caller-client's own, active, non-deleted, non-bank-transfer items.
    Page<RedemptionCatalogItem> findByOwnerClientIdAndCurrencyIdInAndIsActiveAndDeletedFalseAndIsBankTransferFalse(
            UUID ownerClientId, Collection<String> currencyIds, boolean isActive, Pageable pageable);

    List<RedemptionCatalogItem> findByOwnerClientIdAndIsActiveAndDeletedFalseAndIsBankTransferFalse(
            UUID ownerClientId, boolean isActive);

    // The reserved per-client bank-transfer card — dedicated payout path only (never browse/admin).
    Optional<RedemptionCatalogItem> findByOwnerClientIdAndIsBankTransferTrueAndDeletedFalse(UUID ownerClientId);
}
