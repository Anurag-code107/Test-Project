package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateCompanyDistributionRequest;
import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionOrigin;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyDistributionServiceTest {

    @Mock private TenantValidator tenantValidator;
    @Mock private CompanyDistributionRepository distributionRepository;
    @Mock private CompanyDistributionItemRepository itemRepository;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private ClientCatalogItemConfigRepository catalogConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private BankTransferCardService bankTransferCardService;
    @Mock private DistributionRecipientService recipientService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks private CompanyDistributionService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID SKU_ITEM_ID = UUID.randomUUID();
    private static final UUID SELLER_A = UUID.randomUUID();
    private static final UUID SELLER_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(tenantValidator.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(COMPANY_ID);
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(companyWallet("1000.00")));
        when(distributionRepository.save(any())).thenAnswer(i -> {
            CompanyDistribution d = i.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
        when(redemptionRequestRepository.save(any())).thenAnswer(i -> {
            RedemptionRequest r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        when(itemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private RewardWallet companyWallet(String available) {
        RewardWallet w = RewardWallet.builder()
                .clientId(CLIENT_ID)
                .partnerCompanyId(COMPANY_ID)
                .walletType(WalletType.COMPANY)
                .currencyId("cash")
                .availableBalance(new BigDecimal(available))
                .reservedBalance(BigDecimal.ZERO)
                .build();
        // The service stamps the payout leg from the LOADED wallet's id, not the requested one, so the
        // fixture needs a real id or the leg silently gets null.
        w.setId(WALLET_ID);
        return w;
    }

    private RedemptionCatalogItem giftCard() {
        RedemptionCatalogItem item = RedemptionCatalogItem.builder()
                .ownerClientId(CLIENT_ID)
                .category(RedemptionCategory.CASH)
                .currencyId("cash")
                .providerItemId("SKU-AMZN-50")
                .defaultMinRedemptionAmount(new BigDecimal("10.00"))
                .defaultMaxRedemptionAmount(new BigDecimal("500.00"))
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .isActive(true)
                .build();
        item.setId(SKU_ITEM_ID);
        return item;
    }

    private CreateCompanyDistributionRequest giftCardRequest(String amount, UUID... recipients) {
        return new CreateCompanyDistributionRequest(
                DistributionRail.GIFT_CARD, WALLET_ID, SKU_ITEM_ID, null,
                new BigDecimal(amount), List.of(recipients), "Q3 winners", null);
    }

    private CreateCompanyDistributionRequest walletCreditRequest(String amount, UUID... recipients) {
        return new CreateCompanyDistributionRequest(
                DistributionRail.WALLET_CREDIT, WALLET_ID, null, null,
                new BigDecimal(amount), List.of(recipients), null, null);
    }

    private void stubGiftCard() {
        when(catalogItemRepository.findById(SKU_ITEM_ID)).thenReturn(Optional.of(giftCard()));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, SKU_ITEM_ID))
                .thenReturn(Optional.empty());
    }

    // ---------------------------------------------------------------- money safety

    /** The whole total is earmarked at submit, in one wallet update, keyed on the header. */
    @Test
    void submit_reservesFullTotalOnCompanyWallet() {
        stubGiftCard();

        service.submit(giftCardRequest("50.00", SELLER_A, SELLER_B));

        ArgumentCaptor<RewardWallet> wc = ArgumentCaptor.forClass(RewardWallet.class);
        verify(walletRepository).save(wc.capture());
        assertThat(wc.getValue().getAvailableBalance()).isEqualByComparingTo("900.00");
        assertThat(wc.getValue().getReservedBalance()).isEqualByComparingTo("100.00");

        ArgumentCaptor<LedgerEntry> lc = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(lc.capture());
        LedgerEntry e = lc.getValue();
        assertThat(e.getEntryType()).isEqualTo(LedgerEntryType.RESERVE);
        assertThat(e.getAmount()).isEqualByComparingTo("100.00");
        assertThat(e.getReferenceType()).isEqualTo("COMPANY_DISTRIBUTION");
        // Before/after on both balances, so the company ledger is auditable without replaying anything.
        assertThat(e.getAvailableBalanceBefore()).isEqualByComparingTo("1000.00");
        assertThat(e.getAvailableBalanceAfter()).isEqualByComparingTo("900.00");
        assertThat(e.getReservedBalanceAfter()).isEqualByComparingTo("100.00");
    }

    /**
     * The after-commit fan-out is driven by this event, so a missing publish means recipients are reserved and
     * never paid. Its own event type on purpose — RedemptionRequestedEvent would send a recipient
     * "your redemption was submitted" and run the personal dispatch path too.
     */
    @Test
    void submit_publishesItsOwnAfterCommitEvent() {
        stubGiftCard();

        CompanyDistribution header = service.submit(giftCardRequest("50.00", SELLER_A));

        ArgumentCaptor<com.tenxengage.app.event.CompanyDistributionSubmittedEvent> ec =
                ArgumentCaptor.forClass(com.tenxengage.app.event.CompanyDistributionSubmittedEvent.class);
        verify(eventPublisher).publishEvent(ec.capture());
        assertThat(ec.getValue().getDistributionId()).isEqualTo(header.getId());
    }

    /** A rejected submit must not publish — nothing was reserved, so nothing should be dispatched. */
    @Test
    void submit_rejected_publishesNoEvent() {
        stubGiftCard();
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(companyWallet("10.00")));

        assertThatThrownBy(() -> service.submit(giftCardRequest("50.00", SELLER_A)))
                .isInstanceOf(BusinessRuleException.class);

        verify(eventPublisher, never()).publishEvent(any(com.tenxengage.app.event.CompanyDistributionSubmittedEvent.class));
    }

    /** Overdraft must be rejected under the wallet lock, before anything is written. */
    @Test
    void submit_insufficientBalance_rejectsAndWritesNothing() {
        stubGiftCard();
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(companyWallet("60.00")));

        assertThatThrownBy(() -> service.submit(giftCardRequest("50.00", SELLER_A, SELLER_B)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not have enough available balance");

        verify(distributionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    /** A wallet belonging to another company must not be spendable by this admin. */
    @Test
    void submit_walletOfAnotherCompany_is404() {
        stubGiftCard();
        RewardWallet foreign = companyWallet("1000.00");
        foreign.setPartnerCompanyId(UUID.randomUUID());
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.submit(giftCardRequest("50.00", SELLER_A)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** An INDIVIDUAL wallet is not a valid distribution source, even within the same client. */
    @Test
    void submit_individualWallet_is404() {
        stubGiftCard();
        RewardWallet personal = companyWallet("1000.00");
        personal.setWalletType(WalletType.INDIVIDUAL);
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(personal));

        assertThatThrownBy(() -> service.submit(giftCardRequest("50.00", SELLER_A)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- payout rails

    /**
     * The payout leg is what lets the existing pipeline work untouched, so its shape matters: the RECIPIENT
     * in user_id, the COMPANY wallet in wallet_id, and origin = COMPANY_DISTRIBUTION.
     */
    @Test
    void submit_giftCard_createsPayoutLegPerRecipient_withRecipientAsUserId() {
        stubGiftCard();

        service.submit(giftCardRequest("50.00", SELLER_A, SELLER_B));

        ArgumentCaptor<RedemptionRequest> rc = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository, org.mockito.Mockito.times(2)).save(rc.capture());
        assertThat(rc.getAllValues()).extracting(RedemptionRequest::getUserId)
                .containsExactlyInAnyOrder(SELLER_A, SELLER_B);
        RedemptionRequest leg = rc.getAllValues().get(0);
        assertThat(leg.getOrigin()).isEqualTo(RedemptionOrigin.COMPANY_DISTRIBUTION);
        assertThat(leg.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(leg.getWalletType()).isEqualTo(WalletType.COMPANY);
        assertThat(leg.getStatus()).isEqualTo(RedemptionStatus.PROCESSING);
        assertThat(leg.getProcessingMode()).isEqualTo(RedemptionProcessingMode.INSTANT);
        assertThat(leg.getCategory()).isEqualTo(RedemptionCategory.CASH);
        // No idempotency key on the leg — the header owns dedupe, and the unique index there is on (client, key).
        assertThat(leg.getClientIdempotencyKey()).isNull();
    }

    /** Payout items read status from their leg, so they must not also carry one. */
    @Test
    void submit_giftCard_itemPointsAtLegAndStoresNoStatus() {
        stubGiftCard();

        service.submit(giftCardRequest("50.00", SELLER_A));

        ArgumentCaptor<CompanyDistributionItem> ic = ArgumentCaptor.forClass(CompanyDistributionItem.class);
        verify(itemRepository).save(ic.capture());
        assertThat(ic.getValue().getRedemptionRequestId()).isNotNull();
        assertThat(ic.getValue().getStatus())
                .as("chk_distribution_item_leg: a payout item's status lives on the redemption row")
                .isNull();
    }

    /** The bank rail reuses the same reserved per-client card as personal bank transfers. */
    @Test
    void submit_bankTransfer_usesTheReservedBankCard() {
        RedemptionCatalogItem card = RedemptionCatalogItem.builder()
                .ownerClientId(CLIENT_ID).isBankTransfer(true).category(RedemptionCategory.CASH)
                .currencyId("cash").defaultMinRedemptionAmount(new BigDecimal("1.00"))
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT).isActive(true).build();
        card.setId(UUID.randomUUID());
        when(bankTransferCardService.ensureBankTransferCard(CLIENT_ID)).thenReturn(card);

        service.submit(new CreateCompanyDistributionRequest(
                DistributionRail.BANK_TRANSFER, WALLET_ID, null, null,
                new BigDecimal("25.00"), List.of(SELLER_A), null, null));

        ArgumentCaptor<RedemptionRequest> rc = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository).save(rc.capture());
        assertThat(rc.getValue().getCatalogItemId()).isEqualTo(card.getId());
    }

    // ---------------------------------------------------------------- wallet transfer

    /**
     * The internal rail must create NO redemption row — money that never leaves the platform must not be
     * counted as redeemed, or it is counted again when the seller later redeems that balance.
     */
    @Test
    void submit_walletCredit_refusedAndNothingReserved() {
        // Retired 2026-08-26. These two cases previously covered the rail's no-payout-leg shape and its
        // reservation; reservation on submit is still covered on the gift-card path above, so nothing is
        // lost by asserting the refusal instead.
        assertThatThrownBy(() -> service.submit(walletCreditRequest("20.00", SELLER_A, SELLER_B)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer available");

        // Refused before anything is written: no reservation to unwind.
        verify(ledgerEntryRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- recipients

    /** OQ-7: an admin redeems from their own wallet, never by distributing company funds to themself. */
    @Test
    void submit_recipientIsTheCaller_rejected() {
        stubGiftCard();

        assertThatThrownBy(() -> service.submit(giftCardRequest("50.00", SELLER_A, ADMIN_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot distribute to yourself");
    }

    /** A repeated id must not pay someone twice — uq_distribution_item_recipient would reject it anyway. */
    @Test
    void submit_duplicateRecipient_isDeduped() {
        stubGiftCard();

        CompanyDistribution header = service.submit(giftCardRequest("50.00", SELLER_A, SELLER_A));

        assertThat(header.getRecipientCount()).isEqualTo(1);
        assertThat(header.getTotalAmount()).isEqualByComparingTo("50.00");
        verify(itemRepository, org.mockito.Mockito.times(1)).save(any());
    }

    /** Eligibility is re-checked server-side; the ids posted by the client are not trusted. */
    @Test
    void submit_ineligibleRecipient_propagates() {
        stubGiftCard();
        org.mockito.Mockito.doThrow(new BusinessRuleException("RECIPIENT_NOT_ELIGIBLE", "Ana: No bank account linked"))
                .when(recipientService).assertAllEligible(any(), any(), any(), any());

        assertThatThrownBy(() -> service.submit(giftCardRequest("50.00", SELLER_A)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No bank account linked");
    }

    // ---------------------------------------------------------------- amounts + idempotency

    @Test
    void submit_amountBelowSkuMinimum_rejected() {
        stubGiftCard();

        assertThatThrownBy(() -> service.submit(giftCardRequest("5.00", SELLER_A)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("below the minimum");
    }

    @Test
    void submit_amountAboveSkuMaximum_rejected() {
        stubGiftCard();

        assertThatThrownBy(() -> service.submit(giftCardRequest("600.00", SELLER_A)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("above the maximum");
    }

    /** A re-POST returns the original distribution and reserves nothing further. */
    @Test
    void submit_idempotentKey_returnsOriginalWithoutReserving() {
        CompanyDistribution original = CompanyDistribution.builder()
                .clientId(CLIENT_ID).partnerCompanyId(COMPANY_ID).sourceWalletId(WALLET_ID)
                .rail(DistributionRail.GIFT_CARD).currencyId("cash").initiatedByUserId(ADMIN_ID)
                .recipientCount(2).totalAmount(new BigDecimal("100.00")).clientIdempotencyKey("k-1")
                .build();
        original.setId(UUID.randomUUID());
        when(distributionRepository.findByClientIdAndClientIdempotencyKey(CLIENT_ID, "k-1"))
                .thenReturn(Optional.of(original));

        CompanyDistribution result = service.submit(new CreateCompanyDistributionRequest(
                DistributionRail.GIFT_CARD, WALLET_ID, SKU_ITEM_ID, null,
                new BigDecimal("50.00"), List.of(SELLER_A, SELLER_B), null, "k-1"));

        assertThat(result.getId()).isEqualTo(original.getId());
        verify(ledgerEntryRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    /** A partner admin with no company cannot distribute at all. */
    @Test
    void submit_noPartnerCompany_rejected() {
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(null);

        assertThatThrownBy(() -> service.submit(giftCardRequest("50.00", SELLER_A)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("associated partner company");
    }

    @Test
    void submit_giftCardWithoutSku_rejected() {
        assertThatThrownBy(() -> service.submit(new CreateCompanyDistributionRequest(
                DistributionRail.GIFT_CARD, WALLET_ID, null, null,
                new BigDecimal("50.00"), List.of(SELLER_A), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Choose a gift card");
    }

    /** A SKU on a non-gift-card rail is a client bug; fail loudly rather than silently ignoring it. */
    @Test
    void submit_walletCreditWithSku_refusedForTheRailNotTheSku() {
        // The rail refusal fires before the SKU check, so the message names the real reason. Telling a
        // caller their gift card is invalid on a rail that no longer exists would send them fixing the
        // wrong thing.
        assertThatThrownBy(() -> service.submit(new CreateCompanyDistributionRequest(
                DistributionRail.WALLET_CREDIT, WALLET_ID, SKU_ITEM_ID, null,
                new BigDecimal("20.00"), List.of(SELLER_A), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer available");
    }
}
