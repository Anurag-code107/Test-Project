package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.GiftCardSkuResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionValueType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backing a distribution gift card with a catalog row.
 *
 * <p>The property that matters most is that a provisioned row is <b>inactive</b>. The seller storefront lists
 * items on {@code isActive = true} and does not require a per-client config, so an active row would appear in
 * every seller's personal store — silently widening what they can redeem for themselves.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionGiftCardServiceTest {

    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private GiftCardCatalogService giftCardCatalogService;
    @InjectMocks private DistributionGiftCardService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final String SKU = "U163059";

    private static GiftCardSkuResponse sku(String valueType, String face, String min, String max) {
        return new GiftCardSkuResponse(SKU, "Amazon.com Gift Card", "Amazon",
                "https://img.example/amazon.png", "USD", valueType,
                face == null ? null : new BigDecimal(face),
                min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max));
    }

    private RedemptionCatalogItem provisioned(GiftCardSkuResponse card) {
        when(catalogItemRepository.findByOwnerClientIdAndProviderItemIdAndIsActiveTrueAndDeletedFalse(CLIENT_ID, SKU))
                .thenReturn(Optional.empty());
        when(giftCardCatalogService.findBySku(SKU)).thenReturn(Optional.of(card));
        when(catalogItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.ensureCardForSku(CLIENT_ID, SKU);

        ArgumentCaptor<RedemptionCatalogItem> c = ArgumentCaptor.forClass(RedemptionCatalogItem.class);
        verify(catalogItemRepository).save(c.capture());
        return c.getValue();
    }

    /** The safety property: a provisioned card must never surface in the seller's personal store. */
    @Test
    void provisionsTheCardInactive_soItCannotLeakIntoTheSellerStore() {
        RedemptionCatalogItem saved = provisioned(sku("VARIABLE", null, "10", "2000"));

        assertThat(saved.isActive()).isFalse();
        assertThat(saved.isBankTransfer()).isFalse();
        assertThat(saved.getProviderItemId()).isEqualTo(SKU);
        assertThat(saved.getName()).isEqualTo("Amazon.com Gift Card");
    }

    /** Bounds come from the provider, so the admin can only send what the card actually supports. */
    @Test
    void variableCard_carriesTheProviderRange() {
        RedemptionCatalogItem saved = provisioned(sku("VARIABLE", null, "10", "2000"));

        assertThat(saved.getValueType()).isEqualTo(RedemptionValueType.VARIABLE);
        assertThat(saved.getDefaultMinRedemptionAmount()).isEqualByComparingTo("10");
        assertThat(saved.getDefaultMaxRedemptionAmount()).isEqualByComparingTo("2000");
    }

    /** A FIXED card has one denomination — both bounds collapse onto it so no other amount is sendable. */
    @Test
    void fixedCard_pinsBothBoundsToTheFaceValue() {
        RedemptionCatalogItem saved = provisioned(sku("FIXED", "50", null, null));

        assertThat(saved.getValueType()).isEqualTo(RedemptionValueType.FIXED);
        assertThat(saved.getDefaultMinRedemptionAmount()).isEqualByComparingTo("50");
        assertThat(saved.getDefaultMaxRedemptionAmount()).isEqualByComparingTo("50");
    }

    /** Reuse rather than duplicate — a card the client already curated is used as-is. */
    @Test
    void reusesAnExistingRowForTheSameSku() {
        RedemptionCatalogItem existing = RedemptionCatalogItem.builder()
                .ownerClientId(CLIENT_ID).providerItemId(SKU).name("Amazon").isActive(true).build();
        when(catalogItemRepository.findByOwnerClientIdAndProviderItemIdAndIsActiveTrueAndDeletedFalse(CLIENT_ID, SKU))
                .thenReturn(Optional.of(existing));

        assertThat(service.ensureCardForSku(CLIENT_ID, SKU)).isSameAs(existing);
        verify(catalogItemRepository, never()).save(any());
    }

    /**
     * Validated against the live provider catalogue at submit. An unknown SKU must fail here, before any
     * money is reserved — not later at dispatch, with the funds already held.
     */
    @Test
    void unknownSku_isRejectedBeforeAnythingIsReserved() {
        when(catalogItemRepository.findByOwnerClientIdAndProviderItemIdAndIsActiveTrueAndDeletedFalse(CLIENT_ID, "NOPE"))
                .thenReturn(Optional.empty());
        when(giftCardCatalogService.findBySku("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureCardForSku(CLIENT_ID, "NOPE"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer available");
        verify(catalogItemRepository, never()).save(any());
    }

    /**
     * The bug this finder shape exists to prevent.
     *
     * <p>Deactivating a card and re-adding it under a corrected name leaves two rows for one SKU — normal,
     * and permitted, because SKU uniqueness is enforced on the live set only. A finder that merely excluded
     * deleted rows saw both, and an Optional cannot hold two: the distribution died on
     * IncorrectResultSizeDataAccessException, naming neither the catalog nor the SKU.</p>
     *
     * <p>Expressed as a query the repository answers, so it is the derived method name under test — the one
     * thing that actually decides whether a retired row can still break a payout.</p>
     */
    @Test
    void aDeactivatedRowForTheSameSkuDoesNotBreakTheLookup() {
        RedemptionCatalogItem live = RedemptionCatalogItem.builder()
                .ownerClientId(CLIENT_ID).providerItemId(SKU).name("Amazon.com Gift Card").build();
        // The narrowed finder is what excludes the retired row; a broader one would return two and throw.
        when(catalogItemRepository.findByOwnerClientIdAndProviderItemIdAndIsActiveTrueAndDeletedFalse(CLIENT_ID, SKU))
                .thenReturn(Optional.of(live));

        assertThat(service.ensureCardForSku(CLIENT_ID, SKU)).isSameAs(live);

        // Reused, not re-provisioned: the client already curates this SKU.
        verify(catalogItemRepository, never()).save(any());
        verify(giftCardCatalogService, never()).findBySku(any());
    }

    @Test
    void blankSku_isRejected() {
        assertThatThrownBy(() -> service.ensureCardForSku(CLIENT_ID, "  "))
                .isInstanceOf(BusinessRuleException.class);
    }
}
