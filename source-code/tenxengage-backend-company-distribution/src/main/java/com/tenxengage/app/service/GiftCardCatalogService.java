package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.GiftCardSkuResponse;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetDigitalGiftCardsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetDigitalGiftCardsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GiftCardCatalogItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serves the XTRM digital gift-card catalog to the client-admin SKU picker. Fetches the (large, slow,
 * rarely-changing) XTRM catalog, keeps only <b>active USD gift cards</b>, de-dupes by SKU (the same SKU
 * appears under multiple brands), normalizes the value type, and caches the result
 * ({@code giftCardCatalog}, 6h TTL — see {@code RedisConfig}). Never called from the browser directly.
 */
@Service
public class GiftCardCatalogService {

    private static final Logger log = LoggerFactory.getLogger(GiftCardCatalogService.class);

    /** Only USD gift cards are dispatchable on the current XTRM gift-card rail. */
    private static final String CURRENCY = "USD";
    private static final String GIFT_CARD = "gift card";
    private static final String ACTIVE = "Active";

    private final XtrmApiClient xtrmApiClient;
    // Lazy self-reference so findBySku() re-enters listGiftCardSkus() THROUGH the cache proxy — a plain
    // this.listGiftCardSkus() is a self-invocation that bypasses @Cacheable and re-fetches XTRM every create.
    private final ObjectProvider<GiftCardCatalogService> selfProvider;

    public GiftCardCatalogService(XtrmApiClient xtrmApiClient,
                                  ObjectProvider<GiftCardCatalogService> selfProvider) {
        this.xtrmApiClient = xtrmApiClient;
        this.selfProvider = selfProvider;
    }

    /** Active USD gift-card SKUs, de-duped and lean. Cached; throws 503-style on XTRM failure. */
    @Cacheable("giftCardCatalog")
    public List<GiftCardSkuResponse> listGiftCardSkus() {
        GetDigitalGiftCardsResult result = xtrmApiClient.getDigitalGiftCards(new GetDigitalGiftCardsCommand(CURRENCY));
        if (!result.success()) {
            throw new ExternalServiceException("XTRM_UNAVAILABLE",
                    "The gift-card catalog is temporarily unavailable. Please try again shortly.");
        }
        Map<String, GiftCardSkuResponse> bySku = new LinkedHashMap<>();
        for (GiftCardCatalogItem it : result.items()) {
            if (it.sku() == null || it.sku().isBlank()) {
                continue;
            }
            if (!GIFT_CARD.equalsIgnoreCase(it.rewardType())
                    || !ACTIVE.equalsIgnoreCase(it.status())
                    || !CURRENCY.equalsIgnoreCase(it.currencyCode())) {
                continue;
            }
            bySku.putIfAbsent(it.sku(), map(it)); // dedupe by SKU — first brand wins
        }
        log.info("[step=giftcard_catalog_loaded] skus={}", bySku.size());
        return List.copyOf(bySku.values());
    }

    /** Resolve a single SKU (used by the create flow to stamp value_type + bounds authoritatively). */
    public Optional<GiftCardSkuResponse> findBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return self().listGiftCardSkus().stream().filter(s -> sku.equals(s.sku())).findFirst();
    }

    /** The cache-proxied bean, so findBySku() is served from the {@code giftCardCatalog} cache too. */
    private GiftCardCatalogService self() {
        GiftCardCatalogService proxied = selfProvider == null ? null : selfProvider.getIfAvailable();
        return proxied != null ? proxied : this;
    }

    private GiftCardSkuResponse map(GiftCardCatalogItem it) {
        return new GiftCardSkuResponse(
                it.sku(),
                it.rewardName(),
                it.brandName(),
                it.brandImageUrl(),
                it.currencyCode(),
                normalizeValueType(it.valueType()),
                it.faceValue(),
                it.minValue(),
                it.maxValue());
    }

    /** XTRM "FIXED_VALUE"/"VARIABLE_VALUE" → our "FIXED"/"VARIABLE"; unknown defaults to VARIABLE (open-value). */
    private static String normalizeValueType(String xtrmValueType) {
        return "FIXED_VALUE".equalsIgnoreCase(xtrmValueType) ? "FIXED" : "VARIABLE";
    }
}
