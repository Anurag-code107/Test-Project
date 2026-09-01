package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.CompanyAwardResponse;
import com.tenxengage.app.dto.response.DistributionCatalogItemResponse;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.RedemptionValueType;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyDistributionQueryServiceTest {

    @Mock private TenantValidator tenantValidator;
    @Mock private CompanyDistributionRepository distributionRepository;
    @Mock private CompanyDistributionItemRepository itemRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private ClientCatalogItemConfigRepository catalogConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private PartnerCompanyRepository partnerCompanyRepository;

    @InjectMocks private CompanyDistributionQueryService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(tenantValidator.getCurrentUserId()).thenReturn(SELLER_ID);
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(COMPANY_ID);
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(any(), any()))
                .thenReturn(Optional.empty());
    }

    private RedemptionCatalogItem item(String name, String sku, String min, String max) {
        RedemptionCatalogItem i = RedemptionCatalogItem.builder()
                .ownerClientId(CLIENT_ID).category(RedemptionCategory.CASH).currencyId("cash")
                .providerItemId(sku).name(name)
                .defaultMinRedemptionAmount(min == null ? null : new BigDecimal(min))
                .defaultMaxRedemptionAmount(max == null ? null : new BigDecimal(max))
                .valueType(RedemptionValueType.VARIABLE)
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT).isActive(true).build();
        i.setId(UUID.randomUUID());
        return i;
    }

    // ------------------------------------------------------------------ distributable catalog

    /**
     * A SKU-less item cannot be dispatched, so offering it in the picker would only produce a confusing
     * failure at submit. The catalog must filter exactly what submit accepts.
     */
    @Test
    void catalog_excludesItemsWithNoSku() {
        RedemptionCatalogItem good = item("Amazon", "SKU-1", "10.00", "500.00");
        RedemptionCatalogItem noSku = item("Legacy card", null, "10.00", null);
        RedemptionCatalogItem blankSku = item("Blank", "   ", "10.00", null);
        when(catalogItemRepository.findByOwnerClientIdAndIsActiveAndDeletedFalseAndIsBankTransferFalse(
                CLIENT_ID, true)).thenReturn(List.of(good, noSku, blankSku));

        List<DistributionCatalogItemResponse> out = service.listDistributableCatalog();

        assertThat(out).extracting(DistributionCatalogItemResponse::name).containsExactly("Amazon");
    }

    /** The picker must constrain to the same bounds submit enforces, including a client override. */
    @Test
    void catalog_appliesClientOverridesToTheBounds() {
        RedemptionCatalogItem i = item("Amazon", "SKU-1", "10.00", "500.00");
        when(catalogItemRepository.findByOwnerClientIdAndIsActiveAndDeletedFalseAndIsBankTransferFalse(
                CLIENT_ID, true)).thenReturn(List.of(i));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, i.getId()))
                .thenReturn(Optional.of(ClientCatalogItemConfig.builder()
                        .clientId(CLIENT_ID).redemptionCatalogItemId(i.getId()).enabled(true)
                        .minTransactionAmountOverride(new BigDecimal("25.00"))
                        .maxTransactionAmountOverride(new BigDecimal("100.00"))
                        .build()));

        DistributionCatalogItemResponse out = service.listDistributableCatalog().get(0);

        assertThat(out.minAmount()).isEqualByComparingTo("25.00");
        assertThat(out.maxAmount()).isEqualByComparingTo("100.00");
    }

    /** Uploaded images must be served through the API proxy, never as a raw storage key. */
    @Test
    void catalog_buildsProxyUrlForUploadedImage() {
        RedemptionCatalogItem i = item("Amazon", "SKU-1", "10.00", null);
        i.setImageUrl("catalog/raw-key.png");
        when(catalogItemRepository.findByOwnerClientIdAndIsActiveAndDeletedFalseAndIsBankTransferFalse(
                CLIENT_ID, true)).thenReturn(List.of(i));

        DistributionCatalogItemResponse out = service.listDistributableCatalog().get(0);

        assertThat(out.imageUrl()).isEqualTo("/api/v1/admin/redemption-catalog/" + i.getId() + "/image");
        assertThat(out.imageUrl()).doesNotContain("raw-key");
    }

    @Test
    void catalog_emptyWhenNothingDistributable() {
        when(catalogItemRepository.findByOwnerClientIdAndIsActiveAndDeletedFalseAndIsBankTransferFalse(
                CLIENT_ID, true)).thenReturn(List.of());

        assertThat(service.listDistributableCatalog()).isEmpty();
    }

    // ------------------------------------------------------------------ distribution detail

    /**
     * Regression guard. A wallet-transfer item has a null {@code redemptionRequestId}, and the leg map is
     * {@code Map.of()} when no item on the page has a leg — and {@code Map.of().get(null)} throws NPE rather
     * than returning null. So the detail view for an all-wallet-transfer distribution crashed outright. Both
     * this and the award path went through the same naive lookup.
     */
    @Test
    void getDistribution_allWalletCreditItems_doesNotThrow() {
        UUID distId = UUID.randomUUID();
        CompanyDistribution d = CompanyDistribution.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).sourceWalletId(UUID.randomUUID())
                .rail(DistributionRail.WALLET_CREDIT).currencyId("cash")
                .initiatedByUserId(UUID.randomUUID()).recipientCount(2)
                .totalAmount(new BigDecimal("40.00")).build();
        d.setId(distId);

        CompanyDistributionItem a = walletCreditItem(distId, "20.00", DistributionItemStatus.COMPLETED);
        CompanyDistributionItem b = walletCreditItem(distId, "20.00", DistributionItemStatus.FAILED);

        when(distributionRepository.findByIdAndClientIdAndPartnerCompanyId(distId, CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(d));
        when(itemRepository.findByDistributionIdOrderByCreatedAtAsc(distId)).thenReturn(List.of(a, b));

        var out = service.getDistribution(distId);

        assertThat(out.items()).hasSize(2);
        assertThat(out.items()).allSatisfy(r -> assertThat(r.destination()).isEqualTo("Cash wallet"));
        // One completed, one failed -> the rollup must say so rather than claiming success.
        assertThat(out.status()).isEqualTo("PARTIALLY_COMPLETED");
        // Requested is what was submitted; settled counts only the recipient who actually got paid.
        assertThat(out.requestedTotal()).isEqualByComparingTo("40.00");
        assertThat(out.settledTotal()).isEqualByComparingTo("20.00");
    }

    private CompanyDistributionItem walletCreditItem(UUID distId, String amount, DistributionItemStatus s) {
        CompanyDistributionItem i = CompanyDistributionItem.builder()
                .clientId(CLIENT_ID).distributionId(distId).recipientUserId(UUID.randomUUID())
                .amount(new BigDecimal(amount)).status(s).build();
        i.setId(UUID.randomUUID());
        return i;
    }

    // ------------------------------------------------------------------ award detail

    /** A seller may only read their OWN award — the scoping is in the query, not a post-load check. */
    @Test
    void getMyAward_scopedToCaller_otherwise404() {
        UUID awardId = UUID.randomUUID();
        when(itemRepository.findByIdAndClientIdAndRecipientUserId(awardId, CLIENT_ID, SELLER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyAward(awardId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** A wallet-transfer award reports its own status and lands in the cash wallet. */
    @Test
    void getMyAward_walletCredit_reportsOwnStatusAndCashWalletDestination() {
        CompanyDistributionItem item = CompanyDistributionItem.builder()
                .clientId(CLIENT_ID).distributionId(UUID.randomUUID()).recipientUserId(SELLER_ID)
                .amount(new BigDecimal("20.00")).status(DistributionItemStatus.COMPLETED).build();
        item.setId(UUID.randomUUID());
        CompanyDistribution d = CompanyDistribution.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).sourceWalletId(UUID.randomUUID())
                .rail(DistributionRail.WALLET_CREDIT).currencyId("cash")
                .initiatedByUserId(UUID.randomUUID()).recipientCount(1)
                .totalAmount(new BigDecimal("20.00")).note("Nice work").build();
        d.setId(item.getDistributionId());

        when(itemRepository.findByIdAndClientIdAndRecipientUserId(item.getId(), CLIENT_ID, SELLER_ID))
                .thenReturn(Optional.of(item));
        when(distributionRepository.findById(d.getId())).thenReturn(Optional.of(d));

        CompanyAwardResponse out = service.getMyAward(item.getId());

        assertThat(out.status()).isEqualTo("COMPLETED");
        assertThat(out.destination()).isEqualTo("Cash wallet");
        assertThat(out.rail()).isEqualTo(DistributionRail.WALLET_CREDIT);
        assertThat(out.rewardName()).isEqualTo("Wallet Transfer");
        assertThat(out.note()).isEqualTo("Nice work");
        // No vendor for this rail, so there is no payment transaction to show.
        assertThat(out.paymentTransactionId()).isNull();
    }

    /**
     * A payout award defers to its redemption leg for status, and the vendor reference is only surfaced once
     * completed — mirroring the existing redemption-detail rule.
     */
    @Test
    void getMyAward_payoutRail_defersToLegAndHidesVendorRefUntilCompleted() {
        UUID legId = UUID.randomUUID();
        CompanyDistributionItem item = CompanyDistributionItem.builder()
                .clientId(CLIENT_ID).distributionId(UUID.randomUUID()).recipientUserId(SELLER_ID)
                .amount(new BigDecimal("50.00")).redemptionRequestId(legId).build();
        item.setId(UUID.randomUUID());
        CompanyDistribution d = CompanyDistribution.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).sourceWalletId(UUID.randomUUID())
                .rail(DistributionRail.BANK_TRANSFER).currencyId("cash")
                .initiatedByUserId(UUID.randomUUID()).recipientCount(1)
                .totalAmount(new BigDecimal("50.00")).build();
        d.setId(item.getDistributionId());

        RedemptionRequest leg = RedemptionRequest.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID).walletId(UUID.randomUUID())
                .walletType(WalletType.COMPANY).amount(new BigDecimal("50.00")).currencyId("cash")
                .status(RedemptionStatus.PROCESSING).category(RedemptionCategory.CASH)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .vendorReferenceId("XTRM-999").payoutDestinationLabel("KOTAK ****8943").build();
        leg.setId(legId);

        when(itemRepository.findByIdAndClientIdAndRecipientUserId(item.getId(), CLIENT_ID, SELLER_ID))
                .thenReturn(Optional.of(item));
        when(distributionRepository.findById(d.getId())).thenReturn(Optional.of(d));
        when(redemptionRequestRepository.findAllById(List.of(legId))).thenReturn(List.of(leg));

        CompanyAwardResponse out = service.getMyAward(item.getId());

        assertThat(out.status()).isEqualTo("PROCESSING");
        assertThat(out.destination()).isEqualTo("KOTAK ****8943");
        assertThat(out.paymentTransactionId())
                .as("vendor ref is only meaningful once the payout completed")
                .isNull();
    }
}
