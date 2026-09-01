package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.GiftCardSkuResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionValueType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Backs a distribution gift card with a catalog row, so a partner admin can send <b>any</b> XTRM SKU rather
 * than only the handful a client admin has curated.
 *
 * <p><b>Why a catalog row at all.</b> {@code redemption_requests.catalog_item_id} is NOT NULL and
 * {@link com.tenxengage.app.service.XtrmVendorService} reads the SKU off that item at dispatch. Storing a
 * bare SKU on the distribution would mean making that column nullable on the table personal redemptions also
 * use, and teaching the shared payout path a second way to find a SKU. Provisioning a row instead keeps the
 * whole downstream chain — payout legs, dispatch, history joins, award detail — working untouched.</p>
 *
 * <p>Directly modelled on {@link BankTransferCardService}, which already provisions a hidden per-client card
 * for the bank rail. Same idempotent get-or-create, same {@code REQUIRES_NEW} isolation so a concurrent
 * first-distribution race rolls back only this transaction and the loser re-finds the winner's row.</p>
 *
 * <p><b>Created inactive, and that is the safety property.</b> The seller storefront lists items on
 * {@code isActive = true} and does <i>not</i> require a per-client config, so an active row would silently
 * appear in every seller's personal store — expanding what they can redeem for themselves, which no client
 * admin asked for. Inactive keeps it invisible everywhere while still being a valid dispatch target.</p>
 *
 * <p>An existing row for the same SKU is reused rather than duplicated, so a card the client admin already
 * curated (active, with their own min/max) is used as-is. Reuse deliberately ignores {@code isActive}: once
 * partner admins can pick any SKU, a deactivated catalog item no longer restricts distribution — that is the
 * accepted consequence of bypassing curation, not an oversight.</p>
 */
@Service
public class DistributionGiftCardService {

    private static final Logger log = LoggerFactory.getLogger(DistributionGiftCardService.class);

    /** Distributions spend the company cash wallet, so the row is denominated in it like every other rail. */
    static final String CARD_CURRENCY = "cash";

    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final GiftCardCatalogService giftCardCatalogService;

    public DistributionGiftCardService(RedemptionCatalogItemRepository catalogItemRepository,
                                       GiftCardCatalogService giftCardCatalogService) {
        this.catalogItemRepository = catalogItemRepository;
        this.giftCardCatalogService = giftCardCatalogService;
    }

    /** The catalog row for this SKU, provisioning a hidden one if the client has none (idempotent). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RedemptionCatalogItem ensureCardForSku(UUID clientId, String sku) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessRuleException("SKU_REQUIRED", "Choose a gift card to distribute.");
        }
        return catalogItemRepository.findByOwnerClientIdAndProviderItemIdAndIsActiveTrueAndDeletedFalse(clientId, sku)
                .orElseGet(() -> provision(clientId, sku));
    }

    private RedemptionCatalogItem provision(UUID clientId, String sku) {
        // Validated against the live XTRM catalog rather than trusted from the client: an unknown SKU would
        // otherwise only fail later, at dispatch, after the money was already reserved.
        GiftCardSkuResponse card = giftCardCatalogService.findBySku(sku)
                .orElseThrow(() -> new BusinessRuleException(
                        "SKU_UNKNOWN", "That gift card is no longer available from the provider."));

        RedemptionCatalogItem saved = catalogItemRepository.save(build(clientId, card));
        log.info("step=distribution_gift_card_provisioned clientId={} sku={} itemId={}",
                clientId, sku, saved.getId());
        return saved;
    }

    private RedemptionCatalogItem build(UUID clientId, GiftCardSkuResponse card) {
        boolean fixed = "FIXED".equalsIgnoreCase(card.valueType());
        return RedemptionCatalogItem.builder()
                .ownerClientId(clientId)
                .providerItemId(card.sku())
                .name(card.rewardName())
                .providerImageUrl(card.brandImageUrl())
                .category(RedemptionCategory.NON_CASH)
                .currencyId(CARD_CURRENCY)
                .valueType(fixed ? RedemptionValueType.FIXED : RedemptionValueType.VARIABLE)
                // FIXED cards carry a single face value; the bounds collapse onto it so the amount the admin
                // is allowed to send is exactly what the provider will issue.
                .defaultMinRedemptionAmount(fixed ? card.faceValue() : card.minValue())
                .defaultMaxRedemptionAmount(fixed ? card.faceValue() : card.maxValue())
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .isBankTransfer(false)
                // Hidden from the seller storefront — see the class javadoc.
                .isActive(false)
                .isReturnable(false)
                .defaultReturnWindowDays(0)
                .geographicScope(new String[0])
                .build();
    }
}
