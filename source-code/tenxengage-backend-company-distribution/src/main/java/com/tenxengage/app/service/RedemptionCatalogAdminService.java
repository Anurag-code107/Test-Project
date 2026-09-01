package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.request.UpdateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.response.GiftCardSkuResponse;
import com.tenxengage.app.dto.response.IntegrationHealthResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemDetailResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemResponse;
import com.tenxengage.app.dto.response.SyncJobResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionValueType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StorageException;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RedemptionCatalogAdminService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionCatalogAdminService.class);

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = List.of("image/png", "image/jpeg", "image/webp");

    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final ClientCatalogRegionConfigRepository regionConfigRepository;
    private final XoxodaySyncJobService syncJobService;
    private final FileStorageService fileStorageService;
    private final TenantValidator tenantValidator;
    private final GiftCardCatalogService giftCardCatalogService;

    public RedemptionCatalogAdminService(RedemptionCatalogItemRepository catalogItemRepository,
                                          ClientCatalogRegionConfigRepository regionConfigRepository,
                                          XoxodaySyncJobService syncJobService,
                                          FileStorageService fileStorageService,
                                          TenantValidator tenantValidator,
                                          GiftCardCatalogService giftCardCatalogService) {
        this.catalogItemRepository = catalogItemRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.syncJobService = syncJobService;
        this.fileStorageService = fileStorageService;
        this.tenantValidator = tenantValidator;
        this.giftCardCatalogService = giftCardCatalogService;
    }

    /** Fetch a non-deleted catalog item owned by the current client, or 404. */
    private RedemptionCatalogItem findOwnedOrThrow(UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return catalogItemRepository.findByIdAndOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", id));
    }

    /** Best-effort SKU resolution — never fails create if the gift-card catalog is unavailable. */
    private Optional<GiftCardSkuResponse> safeFindGiftCardSku(String providerItemId) {
        if (providerItemId == null || providerItemId.isBlank()) {
            return Optional.empty();
        }
        try {
            return giftCardCatalogService.findBySku(providerItemId);
        } catch (RuntimeException e) {
            log.warn("[step=giftcard_sku_resolve_failed] SKU lookup failed — using requested bounds");
            return Optional.empty();
        }
    }

    @Transactional
    public RedemptionCatalogItemDetailResponse createCatalogItem(CreateRedemptionCatalogItemRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        if (request.category() == RedemptionCategory.CASH && request.isReturnable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CASH items cannot be returnable");
        }
        if (request.providerItemId() != null
                && catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                        clientId, request.providerItemId(), request.category())) {
            // Uniqueness is per-owner and enforced on the LIVE set only: only an ACTIVE non-deleted item
            // with this SKU blocks. A retired item (deactivated OR soft-deleted) never blocks reusing its
            // SKU — new items are created inactive, so activation (below) is where a live-duplicate is caught.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An active catalog item with this providerItemId already exists for category " + request.category());
        }

        // Stamp value type + amount bounds authoritatively from the XTRM SKU (the picker sends a valid SKU).
        // Best-effort: if the catalog is unavailable or the SKU isn't a known gift card, fall back to the
        // requested min as an open-value item (no ceiling).
        //
        // CASH only: providerItemId means an XTRM gift-card SKU for CASH, but a Xoxoday product id for
        // NON_CASH. Looking a Xoxoday id up in the XTRM catalog is meaningless — and a coincidental match
        // would stamp the wrong value type and bounds onto the item — so skip it entirely.
        RedemptionValueType valueType = null;
        BigDecimal minAmount = request.defaultMinRedemptionAmount();
        BigDecimal maxAmount = null;
        Optional<GiftCardSkuResponse> resolved = request.category() == RedemptionCategory.CASH
                ? safeFindGiftCardSku(request.providerItemId())
                : Optional.empty();
        // Stamped alongside the bounds: the SKU's brand image, so the card has something to show when the
        // admin uploads no image of their own. Purely cosmetic — a null here just means the inline SVG.
        String providerImageUrl = resolved.map(GiftCardSkuResponse::brandImageUrl).orElse(null);
        if (resolved.isPresent()) {
            GiftCardSkuResponse s = resolved.get();
            boolean fixed = "FIXED".equalsIgnoreCase(s.valueType());
            valueType = fixed ? RedemptionValueType.FIXED : RedemptionValueType.VARIABLE;
            if (fixed) {
                // Locked denomination — min == max == face value.
                minAmount = s.faceValue();
                maxAmount = s.faceValue();
            } else {
                // VARIABLE: ceiling from the SKU; keep the admin-entered min as the platform floor.
                // XTRM's own minValue is frequently 0 ("any amount"), but the DB requires min > 0 — so
                // only raise the floor to the vendor min when that vendor min is itself positive.
                maxAmount = s.maxValue();
                minAmount = request.defaultMinRedemptionAmount();
                if (s.minValue() != null && s.minValue().signum() > 0
                        && (minAmount == null || minAmount.compareTo(s.minValue()) < 0)) {
                    minAmount = s.minValue();
                }
            }
            if (minAmount == null) {
                minAmount = request.defaultMinRedemptionAmount();
            }
        }

        // The DB requires a strictly-positive minimum (and a FIXED face value should never be 0).
        if (minAmount == null || minAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum redemption amount must be greater than 0");
        }

        RedemptionCatalogItem item = RedemptionCatalogItem.builder()
                .ownerClientId(clientId)
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .currencyId(request.currencyId())
                .defaultMinRedemptionAmount(minAmount)
                .valueType(valueType)
                .defaultMaxRedemptionAmount(maxAmount)
                .defaultProcessingMode(request.defaultProcessingMode() != null
                        ? request.defaultProcessingMode() : RedemptionProcessingMode.INSTANT)
                .geographicScope(request.geographicScope() != null
                        ? request.geographicScope().toArray(String[]::new) : new String[0])
                .providerItemId(request.providerItemId())
                .isReturnable(request.isReturnable())
                .defaultReturnWindowDays(request.defaultReturnWindowDays())
                .imageUrl(request.imageUrl())
                .providerImageUrl(providerImageUrl)
                .isActive(false)
                .build();

        return RedemptionCatalogItemDetailResponse.from(catalogItemRepository.save(item));
    }

    @Transactional
    public RedemptionCatalogItemDetailResponse updateCatalogItem(UUID id, UpdateRedemptionCatalogItemRequest request) {
        RedemptionCatalogItem item = findOwnedOrThrow(id);

        if (request.geographicScope() != null) {
            List<String> newScope = request.geographicScope();
            List<String> blocked = Arrays.stream(item.getGeographicScope())
                    .filter(region -> !newScope.contains(region))
                    .filter(region -> regionConfigRepository
                            .existsByRedemptionCatalogItemIdAndRegionCode(id, region))
                    .toList();
            if (!blocked.isEmpty()) {
                throw new BusinessRuleException(
                        "Cannot narrow geographic scope while tenant configurations exist for region(s) "
                                + blocked + ". Remove regional configurations first.");
            }
            item.setGeographicScope(newScope.toArray(String[]::new));
        }
        if (request.name() != null) item.setName(request.name());
        if (request.description() != null) item.setDescription(request.description());
        if (request.category() != null) item.setCategory(request.category());
        if (request.currencyId() != null) item.setCurrencyId(request.currencyId());
        if (request.defaultMinRedemptionAmount() != null) item.setDefaultMinRedemptionAmount(request.defaultMinRedemptionAmount());
        if (request.defaultProcessingMode() != null) item.setDefaultProcessingMode(request.defaultProcessingMode());
        if (request.providerItemId() != null) {
            boolean skuChanged = !request.providerItemId().equals(item.getProviderItemId());
            // Same LIVE-set rule as create/activate, checked BEFORE the setter so the flush that the
            // query triggers can't make this item match itself. Only a live card reserves a SKU, so an
            // inactive draft may be pointed anywhere; without this the move would fail on the DB index
            // instead, as a generic data-integrity 409 that names no field.
            if (skuChanged && item.isActive()
                    && catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                            item.getOwnerClientId(), request.providerItemId(), item.getCategory())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "An active catalog item with this providerItemId already exists for category " + item.getCategory());
            }
            item.setProviderItemId(request.providerItemId());
            // Re-stamp the brand image when the SKU changes — the old one belongs to a different brand —
            // and fill it in when it's simply missing, so items created before this column existed pick
            // one up on their next save. CASH only (a Xoxoday id means nothing to the XTRM catalog);
            // best-effort, so an unresolvable SKU leaves null and the card shows the illustration rather
            // than the previous brand's logo.
            if (skuChanged || item.getProviderImageUrl() == null) {
                item.setProviderImageUrl(item.getCategory() == RedemptionCategory.CASH
                        ? safeFindGiftCardSku(request.providerItemId())
                                .map(GiftCardSkuResponse::brandImageUrl).orElse(null)
                        : null);
            }
        }
        if (request.isReturnable() != null) item.setReturnable(request.isReturnable());
        if (request.defaultReturnWindowDays() != null) item.setDefaultReturnWindowDays(request.defaultReturnWindowDays());

        // imageUrl: absent field (null Optional) = don't touch; Optional.empty() = remove; Optional.of(v) = set/replace
        Optional<String> imageUrlUpdate = request.imageUrl();
        if (imageUrlUpdate != null) {
            String newImageUrl = imageUrlUpdate.orElse(null);
            if (newImageUrl == null && item.getImageUrl() != null) {
                // explicit null sent → remove image from storage
                try {
                    fileStorageService.delete(item.getImageUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old catalog image key={}", item.getImageUrl());
                }
            } else if (newImageUrl != null && item.getImageUrl() != null && !item.getImageUrl().equals(newImageUrl)) {
                // replaced with different non-null value → delete old
                try {
                    fileStorageService.delete(item.getImageUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old catalog image key={}", item.getImageUrl());
                }
            }
            item.setImageUrl(newImageUrl);
        }

        return RedemptionCatalogItemDetailResponse.from(catalogItemRepository.save(item));
    }

    @Transactional
    public RedemptionCatalogItemResponse activateCatalogItem(UUID id) {
        RedemptionCatalogItem item = findOwnedOrThrow(id);

        if (item.getCategory() == RedemptionCategory.NON_CASH && item.getProviderItemId() == null) {
            throw new BusinessRuleException(
                    "Cannot activate a non-cash catalog item without a provider item ID");
        }
        // Enforce SKU uniqueness at activation (create allows inactive drafts to share a SKU): never
        // activate a second live card with the same providerItemId + category.
        if (!item.isActive() && item.getProviderItemId() != null
                && catalogItemRepository.existsByOwnerClientIdAndProviderItemIdAndCategoryAndIsActiveTrueAndDeletedFalse(
                        item.getOwnerClientId(), item.getProviderItemId(), item.getCategory())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another active catalog item with this providerItemId already exists for category " + item.getCategory());
        }
        item.setActive(true);
        return RedemptionCatalogItemResponse.from(catalogItemRepository.save(item));
    }

    @Transactional
    public RedemptionCatalogItemResponse deactivateCatalogItem(UUID id) {
        RedemptionCatalogItem item = findOwnedOrThrow(id);
        item.setActive(false);
        return RedemptionCatalogItemResponse.from(catalogItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public Page<RedemptionCatalogItemResponse> listCatalogItems(
            RedemptionCategory category, Boolean isActive, String search, Pageable pageable) {
        if (pageable.getPageSize() > 50) {
            throw new IllegalArgumentException("pageSize must not exceed 50");
        }
        UUID clientId = tenantValidator.getCurrentClientId();

        // Fetch the owner's matching items unpaged, then apply the client-admin display order in
        // memory (see catalogAdminDisplayOrder). A client's catalog holds only its own items
        // (Model 2), so it is small enough for a full fetch + in-memory sort — and that lets us
        // express a three-tier ordering that a plain column sort cannot.
        List<RedemptionCatalogItem> all;
        if (search != null && !search.isBlank()) {
            all = catalogItemRepository
                    .searchByNameForOwner(clientId, escapeLike(search.trim()), Pageable.unpaged())
                    .getContent();
        } else if (category != null && isActive != null) {
            all = catalogItemRepository
                    .findByOwnerClientIdAndCategoryAndIsActiveAndDeletedFalseAndIsBankTransferFalse(clientId, category, isActive, Pageable.unpaged())
                    .getContent();
        } else {
            all = catalogItemRepository
                    .findByOwnerClientIdAndDeletedFalseAndIsBankTransferFalse(clientId, Pageable.unpaged())
                    .getContent();
        }

        List<RedemptionCatalogItemResponse> ordered = all.stream()
                .sorted(catalogAdminDisplayOrder(all))
                .map(RedemptionCatalogItemResponse::from)
                .toList();

        // Manual pagination over the ordered list.
        int from = Math.min((int) pageable.getOffset(), ordered.size());
        int to = Math.min(from + pageable.getPageSize(), ordered.size());
        return new PageImpl<>(ordered.subList(from, to), pageable, ordered.size());
    }

    /**
     * Client-admin catalog display order — three tiers, so a just-created item is visible at the
     * top without burying active items:
     * <ol>
     *   <li>fresh drafts — inactive items newer than every active item (a newly created item, which
     *       starts inactive, lands here);</li>
     *   <li>active items;</li>
     *   <li>older / deactivated inactive items.</li>
     * </ol>
     * Newest-first ({@code createdAt} DESC) within each tier.
     */
    private Comparator<RedemptionCatalogItem> catalogAdminDisplayOrder(List<RedemptionCatalogItem> items) {
        Instant newestActiveCreatedAt = items.stream()
                .filter(RedemptionCatalogItem::isActive)
                .map(RedemptionCatalogItem::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.MIN);
        return Comparator
                .comparingInt((RedemptionCatalogItem i) -> displayTier(i, newestActiveCreatedAt))
                .thenComparing(RedemptionCatalogItem::getCreatedAt, Comparator.reverseOrder());
    }

    /** Tier for {@link #catalogAdminDisplayOrder}: 0 = fresh draft, 1 = active, 2 = older inactive. */
    private int displayTier(RedemptionCatalogItem item, Instant newestActiveCreatedAt) {
        if (item.isActive()) {
            return 1;
        }
        return item.getCreatedAt().isAfter(newestActiveCreatedAt) ? 0 : 2;
    }

    /** Soft-delete a catalog item (owner-scoped). The row is kept so history still resolves its name. */
    @Transactional
    public void deleteCatalogItem(UUID id) {
        RedemptionCatalogItem item = findOwnedOrThrow(id);
        item.setDeleted(true);
        catalogItemRepository.save(item);
        log.info("featureArea=redemption-catalog step=catalog_item_soft_deleted catalogItemId={} tenantId={}",
                id, item.getOwnerClientId());
    }

    @Transactional(readOnly = true)
    public RedemptionCatalogItemDetailResponse getCatalogItemDetail(UUID id) {
        return RedemptionCatalogItemDetailResponse.from(findOwnedOrThrow(id));
    }

    public SyncJobResponse triggerXoxodaySync() {
        // Disabled under the client-owned catalog model (Model 2): the Xoxoday sync is an @Async
        // global job with no client owner and would violate owner_client_id NOT NULL. Catalog is
        // client-managed — items are created manually. The FE Sync trigger is hidden.
        throw new BusinessRuleException("CATALOG_SYNC_DISABLED",
                "Catalog sync is disabled — catalog items are managed per client.");
    }

    public IntegrationHealthResponse getIntegrationHealth() {
        String syncStatus = syncJobService.getSyncStatus();
        Instant lastSyncAt = syncJobService.getLastSyncAt();
        int failedSyncCount = syncJobService.getFailedSyncCount();

        if (lastSyncAt == null) {
            Optional<Instant> latestItemSync = catalogItemRepository.findAllByIsActive(true).stream()
                    .filter(item -> item.getCategory() == RedemptionCategory.NON_CASH)
                    .map(RedemptionCatalogItem::getXoxodayLastSyncedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder());
            lastSyncAt = latestItemSync.orElse(null);
        }

        return IntegrationHealthResponse.from(
                syncStatus,
                lastSyncAt != null ? lastSyncAt.atOffset(ZoneOffset.UTC) : null,
                failedSyncCount);
    }

    @Transactional
    public RedemptionCatalogItemResponse uploadCatalogItemImage(UUID id, MultipartFile file) {
        validateImageFile(file);

        RedemptionCatalogItem item = findOwnedOrThrow(id);

        String ext = resolveExtension(Objects.requireNonNull(file.getContentType()));
        String newKey = "catalog/" + id + "/image-" + UUID.randomUUID() + "." + ext;

        try (InputStream stream = file.getInputStream()) {
            fileStorageService.upload(newKey, stream, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new StorageException("Failed to upload catalog image", e);
        }

        String oldKey = item.getImageUrl();
        item.setImageUrl(newKey);
        RedemptionCatalogItemResponse response = RedemptionCatalogItemResponse.from(catalogItemRepository.save(item));

        if (oldKey != null) {
            try {
                fileStorageService.delete(oldKey);
            } catch (Exception e) {
                log.warn("Failed to delete old catalog image key={} — orphaned object in storage", oldKey);
            }
        }

        return response;
    }

    private void validateImageFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds 5 MB limit");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported type. Allowed: image/png, image/jpeg, image/webp");
        }
    }

    @Transactional(readOnly = true)
    public ImageStream streamCatalogItemImage(UUID id) {
        RedemptionCatalogItem item = catalogItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", id));
        String objectKey = item.getImageUrl();
        if (objectKey == null) {
            throw new ResourceNotFoundException("No image set for catalog item " + id);
        }
        String contentType = contentTypeForKey(objectKey);
        InputStream stream = fileStorageService.download(objectKey);
        return new ImageStream(stream, contentType);
    }

    private String contentTypeForKey(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    public record ImageStream(InputStream content, String contentType) {}

    private String resolveExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported: " + contentType);
        };
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
