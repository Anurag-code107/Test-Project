package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.request.UpdateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.response.IntegrationHealthResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemDetailResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemResponse;
import com.tenxengage.app.dto.response.SyncJobResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StorageException;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
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

    public RedemptionCatalogAdminService(RedemptionCatalogItemRepository catalogItemRepository,
                                          ClientCatalogRegionConfigRepository regionConfigRepository,
                                          XoxodaySyncJobService syncJobService,
                                          FileStorageService fileStorageService) {
        this.catalogItemRepository = catalogItemRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.syncJobService = syncJobService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public RedemptionCatalogItemDetailResponse createCatalogItem(CreateRedemptionCatalogItemRequest request) {
        if (request.category() == RedemptionCategory.CASH && request.isReturnable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CASH items cannot be returnable");
        }
        if (request.providerItemId() != null) {
            catalogItemRepository.findByProviderItemId(request.providerItemId())
                    .filter(existing -> existing.getCategory() == request.category())
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "A catalog item with this providerItemId already exists for category " + request.category());
                    });
        }

        RedemptionCatalogItem item = RedemptionCatalogItem.builder()
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .currencyId(request.currencyId())
                .defaultMinRedemptionAmount(request.defaultMinRedemptionAmount())
                .defaultProcessingMode(request.defaultProcessingMode() != null
                        ? request.defaultProcessingMode() : RedemptionProcessingMode.INSTANT)
                .geographicScope(request.geographicScope() != null
                        ? request.geographicScope().toArray(String[]::new) : new String[0])
                .providerItemId(request.providerItemId())
                .isReturnable(request.isReturnable())
                .defaultReturnWindowDays(request.defaultReturnWindowDays())
                .imageUrl(request.imageUrl())
                .isActive(false)
                .build();

        return RedemptionCatalogItemDetailResponse.from(catalogItemRepository.save(item));
    }

    @Transactional
    public RedemptionCatalogItemDetailResponse updateCatalogItem(UUID id, UpdateRedemptionCatalogItemRequest request) {
        RedemptionCatalogItem item = catalogItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", id));

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
        if (request.providerItemId() != null) item.setProviderItemId(request.providerItemId());
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
        RedemptionCatalogItem item = catalogItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", id));

        if (item.getCategory() == RedemptionCategory.NON_CASH && item.getProviderItemId() == null) {
            throw new BusinessRuleException(
                    "Cannot activate a non-cash catalog item without a provider item ID");
        }
        item.setActive(true);
        return RedemptionCatalogItemResponse.from(catalogItemRepository.save(item));
    }

    @Transactional
    public RedemptionCatalogItemResponse deactivateCatalogItem(UUID id) {
        RedemptionCatalogItem item = catalogItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", id));
        item.setActive(false);
        return RedemptionCatalogItemResponse.from(catalogItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public Page<RedemptionCatalogItemResponse> listCatalogItems(
            RedemptionCategory category, Boolean isActive, String search, Pageable pageable) {
        if (pageable.getPageSize() > 50) {
            throw new IllegalArgumentException("pageSize must not exceed 50");
        }
        if (search != null && !search.isBlank()) {
            return catalogItemRepository
                    .searchByName(escapeLike(search.trim()), pageable)
                    .map(RedemptionCatalogItemResponse::from);
        }
        if (category != null && isActive != null) {
            return catalogItemRepository
                    .findAllByCategoryAndIsActive(category, isActive, pageable)
                    .map(RedemptionCatalogItemResponse::from);
        }
        return catalogItemRepository.findAllByOrderByNameAsc(pageable)
                .map(RedemptionCatalogItemResponse::from);
    }

    @Transactional(readOnly = true)
    public RedemptionCatalogItemDetailResponse getCatalogItemDetail(UUID id) {
        return catalogItemRepository.findById(id)
                .map(RedemptionCatalogItemDetailResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", id));
    }

    public SyncJobResponse triggerXoxodaySync() {
        UUID jobId = UUID.randomUUID();
        syncJobService.submitSyncJob();
        log.info("featureArea=redemption-catalog step=xoxoday_sync_triggered jobId={}", jobId);
        return new SyncJobResponse(jobId, "QUEUED");
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

        RedemptionCatalogItem item = catalogItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", id));

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
