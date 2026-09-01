package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.SubmitCompanyRedemptionRequest;
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
import com.tenxengage.app.event.RedemptionRequestedEvent;
import com.tenxengage.app.exception.BusinessRuleException;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
        item.setCategory(RedemptionCategory.NON_CASH);
        item.setDefaultMinRedemptionAmount(MIN_AMOUNT);
        item.setDefaultProcessingMode(mode);
        item.setCurrencyId("cash");
        item.setActive(true);
        return item;
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
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(0L);
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
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(0L);
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
    void submitPersonal_inFlightCapReached_throws409() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(3)));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.submitPersonalRedemption(request(), USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Maximum in-flight redemptions reached");
    }

    @Test
    void submitPersonal_insufficientBalance_throws422() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(0L);
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
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(0L);
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

    private SubmitCompanyRedemptionRequest companyRequest() {
        return new SubmitCompanyRedemptionRequest(CATALOG_ITEM_ID, WALLET_ID, AMOUNT, "cash", null);
    }

    private RewardWallet companyWallet(BigDecimal available) {
        return RewardWallet.builder()
                .clientId(CLIENT_ID)
                .userId(null)
                .partnerCompanyId(COMPANY_ID)
                .currencyId("cash")
                .walletType(WalletType.COMPANY)
                .availableBalance(available)
                .reservedBalance(BigDecimal.ZERO)
                .build();
    }

    private void stubCompanyHappyPath(RedemptionProcessingMode mode) {
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(COMPANY_ID);
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(catalogItem(mode)));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(enabledConfig()));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(companyWallet(new BigDecimal("200.00"))));
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
    void submitCompany_happyPath() {
        stubCompanyHappyPath(RedemptionProcessingMode.INSTANT);

        RedemptionSubmissionConfirmationResponse result = service.submitCompanyRedemption(companyRequest(), USER_ID);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.processingMode()).isEqualTo("INSTANT");
        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getWalletType()).isEqualTo(WalletType.COMPANY);
    }

    @Test
    void submitCompany_insufficientCompanyBalance_throws422() {
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(COMPANY_ID);
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(catalogItem(RedemptionProcessingMode.INSTANT)));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(enabledConfig()));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(companyWallet(new BigDecimal("5.00"))));

        assertThatThrownBy(() -> service.submitCompanyRedemption(companyRequest(), USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient available balance");
    }

    @Test
    void submitCompany_inFlightCapReached_throws409() {
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(COMPANY_ID);
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(3)));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(companyWallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.submitCompanyRedemption(companyRequest(), USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Maximum in-flight redemptions reached");
    }

    @Test
    void submitPersonal_currencyMismatch_throws422() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(0L);
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
    void submitCompany_wrongPartnerCompanyWallet_throws404() {
        UUID otherPartnerCompanyId = UUID.randomUUID();
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(UUID.randomUUID());
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(10)));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(0L);
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(catalogItem(RedemptionProcessingMode.INSTANT)));
        when(catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, CATALOG_ITEM_ID))
                .thenReturn(Optional.of(enabledConfig()));
        RewardWallet otherWallet = RewardWallet.builder()
                .clientId(CLIENT_ID)
                .walletType(WalletType.COMPANY)
                .partnerCompanyId(otherPartnerCompanyId)
                .currencyId("cash")
                .availableBalance(new BigDecimal("200.00"))
                .reservedBalance(BigDecimal.ZERO)
                .build();
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(otherWallet));

        assertThatThrownBy(() -> service.submitCompanyRedemption(companyRequest(), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitPersonal_noLedgerEntry_whenTransactionFails() {
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings(3)));
        when(walletRepository.findByIdForUpdate(WALLET_ID))
                .thenReturn(Optional.of(wallet(new BigDecimal("200.00"))));
        when(redemptionRequestRepository.countByClientIdAndUserIdAndStatusIn(
                eq(CLIENT_ID), eq(USER_ID), any())).thenReturn(3L);

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
