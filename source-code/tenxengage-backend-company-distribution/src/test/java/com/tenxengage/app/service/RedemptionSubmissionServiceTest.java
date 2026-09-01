package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.SubmitPersonalRedemptionRequest;
import com.tenxengage.app.dto.response.RedemptionSubmissionConfirmationResponse;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.TenantRedemptionSettings;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.event.RedemptionFailedEvent;
import com.tenxengage.app.event.RedemptionRequestedEvent;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.TenantRedemptionSettingsRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import com.tenxengage.app.dto.request.BankTransferRedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionOrigin;
import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionSubmissionServiceTest {

    @Mock private TenantValidator tenantValidator;
    @Mock private TenantRedemptionSettingsRepository settingsRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private ClientCatalogItemConfigRepository catalogConfigRepository;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private RedemptionEventProducer redemptionEventProducer;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RedemptionOrchestrationService orchestrationService;
    @Mock private WalletService walletService;
    @Mock private BankTransferCardService bankTransferCardService;
    @Mock private PartnerRedemptionRepository partnerRedemptionRepository;
    @Mock private PartnerLinkedBankRepository linkedBankRepository;

    @InjectMocks private RedemptionSubmissionService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID CATALOG_ITEM_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("50.00");
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("10.00");

    @BeforeEach
    void setUp() {
        service.setSelf(service); // @Transactional self-proxy bypassed in unit tests
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(null);
    }

    private SubmitPersonalRedemptionRequest request() {
        return new SubmitPersonalRedemptionRequest(CATALOG_ITEM_ID, WALLET_ID, AMOUNT, "cash", null);
    }

    private TenantRedemptionSettings settings(int maxInFlight) {
        return TenantRedemptionSettings.builder()
                .clientId(CLIENT_ID)
                .batchCadence(BatchCadence.DAILY)
                .maxInFlightRedemptions(maxInFlight)
                .build();
    }

    private RedemptionCatalogItem catalogItem(RedemptionProcessingMode mode) {
        RedemptionCatalogItem item = new RedemptionCatalogItem();
        item.setOwnerClientId(CLIENT_ID); // client-owned catalog: redeem guard checks owner == caller
        item.setCategory(RedemptionCategory.NON_CASH);
        item.setDefaultMinRedemptionAmount(MIN_AMOUNT);
        item.setDefaultProcessingMode(mode);
        item.setCurrencyId("cash");
        item.setActive(true);
        return item;
    }

    private RedemptionCatalogItem bankCard() {
        RedemptionCatalogItem card = RedemptionCatalogItem.builder()
                .ownerClientId(CLIENT_ID)
                .isBankTransfer(true)
                .category(RedemptionCategory.CASH)
                .currencyId("cash")
                .defaultMinRedemptionAmount(new BigDecimal("1.00"))
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .isActive(true)
                .build();
        card.setId(UUID.randomUUID());
        return card;
    }

    private ClientCatalogItemConfig enabledConfig() {
        return ClientCatalogItemConfig.builder()
                .clientId(CLIENT_ID)
                .redemptionCatalogItemId(CATALOG_ITEM_ID)
                .enabled(true)
                .build();
    }

    private RewardWallet wallet(BigDecimal available) {
        return RewardWallet.builder()
                .clientId(CLIENT_ID)
                .userId(USER_ID)
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .availableBalance(available)
                .reservedBalance(BigDecimal.ZERO)
                .build();
    }

    private RedemptionRequest savedRequest(RedemptionStatus status, RedemptionProcessingMode mode) {
        RedemptionRequest req = RedemptionRequest.builder()
                .clientId(CLIENT_ID)
                .userId(USER_ID)
                .walletId(WALLET_ID)
                .catalogItemId(CATALOG_ITEM_ID)
                .amount(AMOUNT)
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(status)
                .processingMode(mode)
                .category(RedemptionCategory.NON_CASH)
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        req.setId(UUID.randomUUID());
        return req;
    }

    private void stubHappyPath(RedemptionProcessingMode mode) {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(catalogItem(mode)));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(enabledConfig()));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RedemptionStatus status = mode == RedemptionProcessingMode.APPROVAL_REQUIRED
                ? RedemptionStatus.PENDING_APPROVAL
                : RedemptionStatus.RESERVED;
        when(redemptionRequestRepository.save(any())).thenReturn(savedRequest(status, mode));
        if (mode == RedemptionProcessingMode.INSTANT) {
            doNothing().when(orchestrationService).dispatch(any());
        }
    }

    @Test
    void submitPersonal_happyPath_instant() {
        stubHappyPath(RedemptionProcessingMode.INSTANT);

        RedemptionSubmissionConfirmationResponse result = service.submitPersonalRedemption(request(), USER_ID);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.processingMode()).isEqualTo("INSTANT");
        assertThat(result.estimatedDelivery()).isEqualTo("Available in minutes");
        assertThat(result.scheduledBatchDate()).isNull();
        verify(eventPublisher).publishEvent(any(RedemptionRequestedEvent.class));
    }

    /**
     * R1 guard (V51). The submission code never mentions {@code origin}, so this row's value comes
     * entirely from {@code @Builder.Default} on the entity. Remove that annotation and Hibernate sends an
     * explicit NULL for a NOT NULL column, which breaks EVERY personal redemption — a failure this test
     * catches at the unit level instead of at runtime.
     */
    @Test
    void submitPersonal_defaultsOriginToSelf_neverNull() {
        stubHappyPath(RedemptionProcessingMode.INSTANT);

        service.submitPersonalRedemption(request(), USER_ID);

        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigin())
                .as("origin must default to SELF; a null would violate the NOT NULL column")
                .isEqualTo(RedemptionOrigin.SELF);
    }

    /** Same guard on the dedicated bank-transfer path, which builds its row through the same core. */
    @Test
    void submitBankTransfer_defaultsOriginToSelf() {
        stubHappyPath(RedemptionProcessingMode.INSTANT);
        RedemptionCatalogItem card = bankCard();
        when(catalogItemRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(bankTransferCardService.ensureBankTransferCard(CLIENT_ID)).thenReturn(card);

        UUID bankId = UUID.randomUUID();
        when(linkedBankRepository.findByIdAndUserIdAndClientId(bankId, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(PartnerLinkedBank.builder()
                        .clientId(CLIENT_ID).userId(USER_ID)
                        .xtrmBeneficiaryId("BEN-111").maskedLabel("ICICI ****9090").build()));

        service.submitBankTransfer(
                new BankTransferRedemptionRequest(WALLET_ID, new BigDecimal("25.00"), bankId, null), USER_ID);

        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(RedemptionOrigin.SELF);
    }

    /**
     * R5 guard. The in-flight cap must be counted over SELF rows only. A distribution row carries
     * {@code user_id = recipient}, so counting company awards here would let an award the seller never
     * asked for consume their own submission allowance — and could reject the 11th recipient of a
     * distribution. Asserting on the argument is the only way to catch a caller passing the wrong origin,
     * since the mock would happily answer either way.
     */
    @Test
    void submitPersonal_inFlightCap_countsSelfOriginOnly() {
        stubHappyPath(RedemptionProcessingMode.INSTANT);

        service.submitPersonalRedemption(request(), USER_ID);

        verify(redemptionRequestRepository).countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any());
    }

    /**
     * The seller's personal detail endpoint must scope to SELF, so a company award cannot be opened
     * through it — the list already hides awards, and list-hides/detail-serves is the inconsistency this
     * prevents.
     */
    @Test
    void getRedemptionById_scopesToSelfOrigin() {
        RedemptionRequest req = savedRequest(RedemptionStatus.COMPLETED, RedemptionProcessingMode.INSTANT);
        when(redemptionRequestRepository.findByIdAndClientIdAndUserIdAndOrigin(
                any(), eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF)))
                .thenReturn(Optional.of(req));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(catalogItem(RedemptionProcessingMode.INSTANT)));

        service.getRedemptionById(UUID.randomUUID(), USER_ID);

        verify(redemptionRequestRepository).findByIdAndClientIdAndUserIdAndOrigin(
                any(), eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF));
    }

    /**
     * The builder must not silently accept a null origin either — proves the default is on the field and
     * not merely supplied by the one call site we control.
     */
    @Test
    void builder_withoutOrigin_yieldsSelfNotNull() {
        RedemptionRequest built = RedemptionRequest.builder()
                .clientId(CLIENT_ID)
                .userId(USER_ID)
                .walletId(WALLET_ID)
                .build();

        assertThat(built.getOrigin()).isEqualTo(RedemptionOrigin.SELF);
    }

    @Test
    void submitPersonal_rejectsItemOwnedByAnotherClient() {
        // Model 2 money-path isolation: a buyer cannot redeem another client's item by id.
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any()))
                .thenReturn(0L);
        RedemptionCatalogItem otherClientItem = catalogItem(RedemptionProcessingMode.INSTANT);
        otherClientItem.setOwnerClientId(UUID.randomUUID()); // different client owns it
        when(catalogItemRepository.findById(CATALOG_ITEM_ID)).thenReturn(Optional.of(otherClientItem));

        assertThatThrownBy(() -> service.submitPersonalRedemption(request(), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void submitPersonal_rejectsInactiveItem() {
        // Single gate = isActive: an inactive item is not redeemable (proves toggling Active off blocks redeem).
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any()))
                .thenReturn(0L);
        RedemptionCatalogItem inactive = catalogItem(RedemptionProcessingMode.INSTANT);
        inactive.setActive(false);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.submitPersonalRedemption(request(), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(walletRepository, never()).save(any());
    }

    // BU-4: the reserved bank-transfer card is NOT redeemable via the public store path (!isBankTransfer
    // guard) — only via the dedicated endpoint. Prevents bypassing the "no linked bank" precondition.
    @Test
    void submitPersonal_rejectsBankTransferCard() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any()))
                .thenReturn(0L);
        RedemptionCatalogItem bankCard = catalogItem(RedemptionProcessingMode.INSTANT);
        bankCard.setBankTransfer(true);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID)).thenReturn(Optional.of(bankCard));

        assertThatThrownBy(() -> service.submitPersonalRedemption(request(), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(walletRepository, never()).save(any());
    }

    // BU-5: the dedicated bank-transfer endpoint rejects (409) when the user has no default linked bank —
    // funds are never reserved-then-failed, and the card is not even provisioned.
    @Test
    void submitBankTransfer_noLinkedBank_throws409() {
        when(partnerRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitBankTransfer(
                new BankTransferRedemptionRequest(WALLET_ID, new BigDecimal("10.00"), null, null), USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No bank account is linked");
        verify(bankTransferCardService, never()).ensureBankTransferCard(any());
    }

    @Test
    void submitBankTransfer_withChosenBank_persistsThatBanksBeneficiaryAndLabel() {
        stubHappyPath(RedemptionProcessingMode.INSTANT);
        RedemptionCatalogItem card = bankCard();
        when(catalogItemRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(bankTransferCardService.ensureBankTransferCard(CLIENT_ID)).thenReturn(card);

        UUID bankId = UUID.randomUUID();
        when(linkedBankRepository.findByIdAndUserIdAndClientId(bankId, USER_ID, CLIENT_ID))
                .thenReturn(Optional.of(PartnerLinkedBank.builder()
                        .clientId(CLIENT_ID).userId(USER_ID)
                        .xtrmBeneficiaryId("BEN-999").maskedLabel("HDFC ****4242").build()));

        service.submitBankTransfer(
                new BankTransferRedemptionRequest(WALLET_ID, new BigDecimal("25.00"), bankId, null), USER_ID);

        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository).save(captor.capture());
        RedemptionRequest saved = captor.getValue();
        // The chosen bank (not the profile default) is snapshotted on the redemption for the after-commit dispatch.
        assertThat(saved.getPayoutBeneficiaryId()).isEqualTo("BEN-999");
        assertThat(saved.getPayoutDestinationLabel()).isEqualTo("HDFC ****4242");
        assertThat(saved.getCatalogItemId()).isEqualTo(card.getId());
    }

    @Test
    void submitPersonal_cashInstant_isProcessing_andDoesNotDispatchInTransaction() {
        stubHappyPath(RedemptionProcessingMode.INSTANT);
        RedemptionCatalogItem cashItem = catalogItem(RedemptionProcessingMode.INSTANT);
        cashItem.setCategory(RedemptionCategory.CASH);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID)).thenReturn(Optional.of(cashItem));
        RedemptionRequest cashSaved = savedRequest(RedemptionStatus.PROCESSING, RedemptionProcessingMode.INSTANT);
        cashSaved.setCategory(RedemptionCategory.CASH);
        when(redemptionRequestRepository.save(any())).thenReturn(cashSaved);

        RedemptionSubmissionConfirmationResponse result = service.submitPersonalRedemption(request(), USER_ID);

        assertThat(result.status()).isEqualTo("PROCESSING");
        // CASH INSTANT must NOT call the vendor inside the submission transaction — dispatch is after commit.
        verify(orchestrationService, never()).dispatch(any());
    }

    @Test
    void onRedemptionRequested_cashInstant_dispatchesAfterCommitAndPersistsVendorRef() {
        RedemptionRequest request = savedRequest(RedemptionStatus.PROCESSING, RedemptionProcessingMode.INSTANT);
        request.setCategory(RedemptionCategory.CASH);
        when(redemptionRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> {
            RedemptionRequest r = inv.getArgument(0);
            r.setVendorReferenceId("XTRM-TX-1");
            return null;
        }).when(orchestrationService).dispatch(any());

        service.onRedemptionRequested(new RedemptionRequestedEvent(this, request));

        verify(orchestrationService).dispatch(any(RedemptionRequest.class));
        verify(redemptionEventProducer).publishRedemptionRequested(request);
        assertThat(request.getVendorReferenceId()).isEqualTo("XTRM-TX-1");
    }

    @Test
    void onRedemptionRequested_cashInstantDefinitiveFailure_releasesAndMarksFailed() {
        RedemptionRequest request = savedRequest(RedemptionStatus.PROCESSING, RedemptionProcessingMode.INSTANT);
        request.setCategory(RedemptionCategory.CASH);
        when(redemptionRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(redemptionRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Definitive rejection — vendor did not execute the transfer.
        doThrow(new BusinessRuleException("XTRM_NOT_ENROLLED", "Not set up for payouts"))
                .when(orchestrationService).dispatch(any());

        service.onRedemptionRequested(new RedemptionRequestedEvent(this, request));

        verify(walletService).releaseReservedBalance(request);
        assertThat(request.getStatus()).isEqualTo(RedemptionStatus.FAILED);
        assertThat(request.getFailureReason()).isEqualTo("Not set up for payouts");
        verify(eventPublisher).publishEvent(any(RedemptionFailedEvent.class));
    }

    @Test
    void onRedemptionRequested_cashInstantAmbiguousFailure_holdsProcessing() {
        RedemptionRequest request = savedRequest(RedemptionStatus.PROCESSING, RedemptionProcessingMode.INSTANT);
        request.setCategory(RedemptionCategory.CASH);
        when(redemptionRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Transient/ambiguous — vendor may have accepted; must NOT release.
        doThrow(new ExternalServiceException("XTRM_UNAVAILABLE", "temporarily unavailable"))
                .when(orchestrationService).dispatch(any());

        service.onRedemptionRequested(new RedemptionRequestedEvent(this, request));

        verify(walletService, never()).releaseReservedBalance(any());
        assertThat(request.getStatus()).isEqualTo(RedemptionStatus.PROCESSING);
    }

    @Test
    void onRedemptionRequested_nonCashInstant_doesNotDispatchAfterCommit() {
        // NON_CASH INSTANT already completed synchronously in-tx (status COMPLETED) — no after-commit dispatch.
        RedemptionRequest request = savedRequest(RedemptionStatus.COMPLETED, RedemptionProcessingMode.INSTANT);

        service.onRedemptionRequested(new RedemptionRequestedEvent(this, request));

        verify(orchestrationService, never()).dispatch(any());
        verify(redemptionEventProducer).publishRedemptionRequested(request);
    }

    @Test
    void submitPersonal_happyPath_batch() {
        stubHappyPath(RedemptionProcessingMode.BATCH);
        RedemptionRequest batchRequest = savedRequest(RedemptionStatus.RESERVED, RedemptionProcessingMode.BATCH);
        batchRequest.setScheduledBatchDate(java.time.LocalDate.now().plusDays(1));
        when(redemptionRequestRepository.save(any())).thenReturn(batchRequest);

        RedemptionSubmissionConfirmationResponse result = service.submitPersonalRedemption(request(), USER_ID);

        assertThat(result.status()).isEqualTo("RESERVED");
        assertThat(result.processingMode()).isEqualTo("BATCH");
        assertThat(result.scheduledBatchDate()).isNotNull();
    }

    @Test
    void submitPersonal_happyPath_approvalRequired() {
        stubHappyPath(RedemptionProcessingMode.APPROVAL_REQUIRED);

        RedemptionSubmissionConfirmationResponse result = service.submitPersonalRedemption(request(), USER_ID);

        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.processingMode()).isEqualTo("APPROVAL_REQUIRED");
        assertThat(result.estimatedDelivery()).isNull();
    }

    @Test
    void submitPersonal_amountBelowMinimum_throws422() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(catalogItem(RedemptionProcessingMode.INSTANT)));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(enabledConfig()));

        SubmitPersonalRedemptionRequest belowMin =
                new SubmitPersonalRedemptionRequest(CATALOG_ITEM_ID, WALLET_ID, new BigDecimal("5.00"), "cash", null);

        assertThatThrownBy(() -> service.submitPersonalRedemption(belowMin, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Amount is below the minimum allowed");
    }

    @Test
    void submitPersonal_amountAboveMaximum_throws422() {
        // A FIXED / capped gift-card item carries a max; an amount above it is rejected before any
        // wallet-balance check (the max guard sits between the min guard and the balance guards).
        RedemptionCatalogItem capped = catalogItem(RedemptionProcessingMode.INSTANT);
        capped.setDefaultMinRedemptionAmount(new BigDecimal("1.00"));
        capped.setDefaultMaxRedemptionAmount(new BigDecimal("100.00"));

        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("500.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID)).thenReturn(Optional.of(capped));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(enabledConfig()));

        SubmitPersonalRedemptionRequest aboveMax = new SubmitPersonalRedemptionRequest(
                CATALOG_ITEM_ID, WALLET_ID, new BigDecimal("250.00"), "cash", null);

        assertThatThrownBy(() -> service.submitPersonalRedemption(aboveMax, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Amount is above the maximum allowed");
    }

    @Test
    void submitPersonal_amountAboveClientMaxOverride_throws422() {
        // The client narrowed the item's $1000 ceiling to $100 — the override wins, symmetric with
        // minTransactionAmountOverride.
        RedemptionCatalogItem capped = catalogItem(RedemptionProcessingMode.INSTANT);
        capped.setDefaultMinRedemptionAmount(new BigDecimal("1.00"));
        capped.setDefaultMaxRedemptionAmount(new BigDecimal("1000.00"));

        ClientCatalogItemConfig narrowed = enabledConfig();
        narrowed.setMaxTransactionAmountOverride(new BigDecimal("100.00"));

        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("500.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID)).thenReturn(Optional.of(capped));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(narrowed));

        SubmitPersonalRedemptionRequest aboveOverride = new SubmitPersonalRedemptionRequest(
                CATALOG_ITEM_ID, WALLET_ID, new BigDecimal("250.00"), "cash", null);

        assertThatThrownBy(() -> service.submitPersonalRedemption(aboveOverride, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Amount is above the maximum allowed: 100");
    }

    @Test
    void submitPersonal_amountAtClientMaxOverride_succeeds() {
        // The override is inclusive: exactly the narrowed ceiling still goes through.
        stubHappyPath(RedemptionProcessingMode.INSTANT);

        RedemptionCatalogItem capped = catalogItem(RedemptionProcessingMode.INSTANT);
        capped.setDefaultMinRedemptionAmount(new BigDecimal("1.00"));
        capped.setDefaultMaxRedemptionAmount(new BigDecimal("1000.00"));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID)).thenReturn(Optional.of(capped));

        ClientCatalogItemConfig narrowed = enabledConfig();
        narrowed.setMaxTransactionAmountOverride(new BigDecimal("100.00"));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(narrowed));

        SubmitPersonalRedemptionRequest atOverride = new SubmitPersonalRedemptionRequest(
                CATALOG_ITEM_ID, WALLET_ID, new BigDecimal("100.00"), "cash", null);

        assertThat(service.submitPersonalRedemption(atOverride, USER_ID)).isNotNull();
    }

    @Test
    void submitPersonal_inFlightCapReached_throws409() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(3)));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.submitPersonalRedemption(request(), USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Maximum in-flight redemptions reached");
    }

    @Test
    void submitPersonal_insufficientBalance_throws422() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(catalogItem(RedemptionProcessingMode.INSTANT)));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(enabledConfig()));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("5.00"))));

        assertThatThrownBy(() -> service.submitPersonalRedemption(request(), USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient available balance");
    }

    @Test
    void submitPersonal_catalogItemNotFound_throws404() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitPersonalRedemption(request(), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitPersonal_kafkaEventPublished_afterCommit() {
        stubHappyPath(RedemptionProcessingMode.INSTANT);

        service.submitPersonalRedemption(request(), USER_ID);

        ArgumentCaptor<RedemptionRequestedEvent> captor =
                ArgumentCaptor.forClass(RedemptionRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRequest()).isNotNull();
        assertThat(captor.getValue().getRequest().getClientId()).isEqualTo(CLIENT_ID);

        // Simulate after-commit: calling the listener directly
        service.onRedemptionRequested(captor.getValue());
        verify(redemptionEventProducer).publishRedemptionRequested(captor.getValue().getRequest());
    }




    @Test
    void submitPersonal_currencyMismatch_throws422() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(catalogItem(RedemptionProcessingMode.INSTANT)));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(enabledConfig()));
        RewardWallet mismatchWallet = RewardWallet.builder()
                .clientId(CLIENT_ID).userId(USER_ID)
                .currencyId("points")
                .walletType(WalletType.INDIVIDUAL)
                .availableBalance(new BigDecimal("200.00"))
                .reservedBalance(BigDecimal.ZERO)
                .build();
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(mismatchWallet));

        SubmitPersonalRedemptionRequest req =
                new SubmitPersonalRedemptionRequest(CATALOG_ITEM_ID, WALLET_ID, AMOUNT, "cash", null);
        assertThatThrownBy(() -> service.submitPersonalRedemption(req, USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("currency");
    }


    @Test
    void submitPersonal_noLedgerEntry_whenTransactionFails() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(3)));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndOriginAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), eq(RedemptionOrigin.SELF), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.submitPersonalRedemption(request(), USER_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void submitPersonal_orchestrationDispatch_calledOnlyForInstant() {
        // INSTANT — dispatch must be called synchronously before DEBIT write
        stubHappyPath(RedemptionProcessingMode.INSTANT);
        service.submitPersonalRedemption(request(), USER_ID);
        verify(orchestrationService).dispatch(any(RedemptionRequest.class));

        // BATCH — dispatch must NOT be called
        org.mockito.Mockito.reset(orchestrationService);
        stubHappyPath(RedemptionProcessingMode.BATCH);
        RedemptionRequest batchSaved = savedRequest(RedemptionStatus.RESERVED, RedemptionProcessingMode.BATCH);
        batchSaved.setScheduledBatchDate(java.time.LocalDate.now().plusDays(1));
        when(redemptionRequestRepository.save(any())).thenReturn(batchSaved);
        service.submitPersonalRedemption(request(), USER_ID);
        verify(orchestrationService, never()).dispatch(any());

        // APPROVAL_REQUIRED — dispatch must NOT be called
        org.mockito.Mockito.reset(orchestrationService);
        stubHappyPath(RedemptionProcessingMode.APPROVAL_REQUIRED);
        service.submitPersonalRedemption(request(), USER_ID);
        verify(orchestrationService, never()).dispatch(any());
    }
}
