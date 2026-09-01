package com.tenxengage.app.service;

import com.tenxengage.app.client.XoxodayApiClient;
import com.tenxengage.app.client.XoxodayProductResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class XoxodaySyncJobService {

    private static final Logger log = LoggerFactory.getLogger(XoxodaySyncJobService.class);

    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final XoxodayApiClient xoxodayApiClient;

    private volatile String syncStatus = "NEVER_SYNCED";
    private volatile Instant lastSyncAt;
    private final AtomicInteger failedSyncCount = new AtomicInteger(0);

    @Lazy
    @Autowired
    private XoxodaySyncJobService self;

    public XoxodaySyncJobService(RedemptionCatalogItemRepository catalogItemRepository,
                                  XoxodayApiClient xoxodayApiClient) {
        this.catalogItemRepository = catalogItemRepository;
        this.xoxodayApiClient = xoxodayApiClient;
    }

    @Async("taskExecutor")
    public void submitSyncJob() {
        syncStatus = "IN_PROGRESS";
        self.runSync();
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional
    public void runSync() {
        log.info("featureArea=redemption-catalog step=xoxoday_sync_started");

        List<XoxodayProductResponse> xoxodayProducts = xoxodayApiClient.fetchAllProducts();

        if (xoxodayProducts.isEmpty()) {
            throw new IllegalStateException(
                    "Xoxoday returned empty product catalog — aborting sync fail-closed to prevent mass deactivation");
        }

        long currentActiveCount = catalogItemRepository.countByCategoryAndIsActive(
                RedemptionCategory.NON_CASH, true);
        if (currentActiveCount > 0 && xoxodayProducts.size() < currentActiveCount * 0.80) {
            throw new IllegalStateException(String.format(
                    "Xoxoday returned suspicious catalog size: %d products vs %d active non-cash items — aborting sync to prevent mass deactivation",
                    xoxodayProducts.size(), currentActiveCount));
        }

        Set<String> xoxodayProductIds = xoxodayProducts.stream()
                .map(XoxodayProductResponse::productId)
                .collect(Collectors.toSet());

        Instant now = Instant.now();

        // Build lookup of all existing NON_CASH items by providerItemId
        Map<String, RedemptionCatalogItem> existingByProviderId = catalogItemRepository
                .findAllByCategory(RedemptionCategory.NON_CASH).stream()
                .filter(item -> item.getProviderItemId() != null)
                .collect(Collectors.toMap(RedemptionCatalogItem::getProviderItemId, item -> item));

        // Upsert: activate existing items returned by Xoxoday; create placeholder rows for new productIds
        List<RedemptionCatalogItem> toUpsert = new ArrayList<>();
        int createdCount = 0;
        for (XoxodayProductResponse product : xoxodayProducts) {
            RedemptionCatalogItem existing = existingByProviderId.get(product.productId());
            if (existing != null) {
                existing.setActive(true);
                existing.setXoxodayLastSyncedAt(now);
                toUpsert.add(existing);
            } else {
                // Inactive placeholder — admin must enrich name/currency/amount and activate before use
                toUpsert.add(RedemptionCatalogItem.builder()
                        .name(product.productId())
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("POINTS")
                        .defaultMinRedemptionAmount(BigDecimal.ONE)
                        .providerItemId(product.productId())
                        .isActive(false)
                        .xoxodayLastSyncedAt(now)
                        .build());
                createdCount++;
                log.info("featureArea=redemption-catalog step=xoxoday_item_created providerItemId={}",
                        product.productId());
            }
        }

        // Deactivate items no longer present in the Xoxoday response
        List<RedemptionCatalogItem> toDeactivate = new ArrayList<>();
        for (RedemptionCatalogItem item : existingByProviderId.values()) {
            if (item.isActive() && !xoxodayProductIds.contains(item.getProviderItemId())) {
                item.setActive(false);
                item.setXoxodayLastSyncedAt(now);
                log.info("featureArea=redemption-catalog step=xoxoday_item_auto_deactivated itemId={}", item.getId());
                toDeactivate.add(item);
            }
        }

        List<RedemptionCatalogItem> allChanges = new ArrayList<>(toUpsert);
        allChanges.addAll(toDeactivate);
        if (!allChanges.isEmpty()) {
            catalogItemRepository.saveAll(allChanges);
        }

        syncStatus = "SUCCESS";
        lastSyncAt = now;
        failedSyncCount.set(0);
        log.info("featureArea=redemption-catalog step=xoxoday_sync_completed upsertedCount={} createdCount={} deactivatedCount={}",
                toUpsert.size(), createdCount, toDeactivate.size());
    }

    @Recover
    public void handleSyncFailure(Exception e) {
        failedSyncCount.incrementAndGet();
        syncStatus = "FAILED";
        log.error("featureArea=redemption-catalog step=xoxoday_sync_failed error={}", e.getMessage(), e);
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public int getFailedSyncCount() {
        return failedSyncCount.get();
    }
}
