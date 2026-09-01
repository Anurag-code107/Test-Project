package com.tenxengage.app.service;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.entity.enums.xtrm.RedemptionPayoutMethod;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchItem;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchItemResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchTransferCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchTransferResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetWalletsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.TransferFundCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.TransferFundResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.WalletInfo;
import com.tenxengage.app.service.xtrm.XtrmEnrollmentService;
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XtrmVendorServiceTest {

    @Mock
    private XtrmEnrollmentService enrollmentService;
    @Mock
    private PartnerRedemptionRepository userRedemptionRepository;
    @Mock
    private XtrmApiClient xtrmApiClient;
    @Mock
    private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private com.tenxengage.app.service.xtrm.XtrmRemitterResolver remitterResolver;

    private XtrmVendorService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final String PAT = "PAT-READY";
    private static final String TX_ID = "XTRM-TX-98765";
    private static final String BENEFICIARY_TX_ID = "XTRM-BEN-426373";
    private static final String ANYPAY_METHOD = "XTR94502";
    private static final String BANK_METHOD = "XTR94500";
    private static final String CARD_METHOD = "XTR94508";
    private static final String GIFT_CARD_METHOD = "XTR94505";

    @BeforeEach
    void setUp() {
        service = new XtrmVendorService(enrollmentService, userRedemptionRepository, xtrmApiClient,
                catalogItemRepository, userRepository, remitterResolver);
        ReflectionTestUtils.setField(service, "anypayPaymentMethodId", ANYPAY_METHOD);
        ReflectionTestUtils.setField(service, "bankPaymentMethodId", BANK_METHOD);
        ReflectionTestUtils.setField(service, "rapidTransferPaymentMethodId", CARD_METHOD);
        ReflectionTestUtils.setField(service, "giftCardPaymentMethodId", "XTR94505");
        // The remitter is resolved for every dispatch now; return platform credentials so these
        // tests keep asserting what they were written to assert.
        org.mockito.Mockito.lenient().when(remitterResolver.forRedemption(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.tenxengage.app.service.xtrm.XtrmCredentials(
                        "platform-id", "platform-secret", "SPN26237883", "203871", "2314"));
    }

    @Test
    void dispatch_companyWallet_throwsNotSupportedAndSkipsEnrollment() {
        assertThatThrownBy(() -> service.dispatch(buildRequest("cash", WalletType.COMPANY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COMPANY_PAYOUT_NOT_SUPPORTED");

        verifyNoInteractions(enrollmentService, xtrmApiClient);
    }

    @Test
    void dispatch_notEnrolled_propagatesAndSkipsTransfer() {
        when(enrollmentService.ensureEnrolledForPayout(USER_ID))
                .thenThrow(new BusinessRuleException("XTRM_NOT_ENROLLED", "not set up"));

        assertThatThrownBy(() -> service.dispatch(buildRequest("cash", WalletType.INDIVIDUAL)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_NOT_ENROLLED");

        verifyNoInteractions(xtrmApiClient);
    }

    // BU-6: two-rail dispatch by redemption TYPE. A non-bank-transfer (gift-card) item with a SKU routes to
    // the XTRM digital gift-card rail (XTR94505) with the SKU + the recipient's email. ANYPAY/CARD are dormant
    // (removed from the individual router) and no longer reachable here.
    @Test
    void dispatch_giftCardItem_usesGiftCardRailAndSetsVendorReferenceId() {
        stubEnrolled(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build());
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        stubCatalogItem(request, giftCardItem("SKU-1"));
        stubUserEmail("recipient@example.com");
        when(xtrmApiClient.transferFund(any(TransferFundCommand.class), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class)))
                .thenReturn(TransferFundResult.ok(TX_ID, BENEFICIARY_TX_ID));

        service.dispatch(request);

        ArgumentCaptor<TransferFundCommand> captor = ArgumentCaptor.forClass(TransferFundCommand.class);
        verify(xtrmApiClient).transferFund(captor.capture(), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class));
        TransferFundCommand cmd = captor.getValue();
        assertThat(cmd.recipientUserId()).isEqualTo(PAT);
        assertThat(cmd.paymentMethodId()).isEqualTo(GIFT_CARD_METHOD);
        assertThat(cmd.sku()).isEqualTo("SKU-1");
        assertThat(cmd.giftCardEmail()).isEqualTo("recipient@example.com");
        assertThat(cmd.partnerLinkedBankId()).isNull();
        assertThat(cmd.issuerTransactionId()).isEqualTo(REQUEST_ID.toString());
        assertThat(request.getVendorReferenceId()).isEqualTo(TX_ID);
        assertThat(request.getBeneficiaryTransactionId()).isEqualTo(BENEFICIARY_TX_ID);
    }

    @Test
    void dispatch_giftCardItem_noEmailOnFile_throwsUnroutable() {
        stubEnrolled(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build());
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        stubCatalogItem(request, giftCardItem("SKU-1"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dispatch(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "REDEMPTION_UNROUTABLE");

        verifyNoInteractions(xtrmApiClient);
    }

    @Test
    void dispatch_bankTransferItem_usesBankRailAndLinkedBankId() {
        stubEnrolled(PartnerRedemptionFixtures.enrolledWithBank(CLIENT_ID, USER_ID, PAT, "BANK-REF-1").build());
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        stubCatalogItem(request, bankTransferCard());
        when(xtrmApiClient.transferFund(any(TransferFundCommand.class), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class))).thenReturn(TransferFundResult.ok(TX_ID, BENEFICIARY_TX_ID));

        service.dispatch(request);

        ArgumentCaptor<TransferFundCommand> captor = ArgumentCaptor.forClass(TransferFundCommand.class);
        verify(xtrmApiClient).transferFund(captor.capture(), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class));
        assertThat(captor.getValue().paymentMethodId()).isEqualTo(BANK_METHOD);
        assertThat(captor.getValue().partnerLinkedBankId()).isEqualTo("BANK-REF-1");
        assertThat(captor.getValue().sku()).isNull();
        assertThat(request.getPayoutMethod()).isEqualTo(RedemptionPayoutMethod.BANK);
    }

    @Test
    void dispatch_bankTransferItem_withoutLinkedBank_throwsBankNotLinked() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT)
                .partnerLinkedBankId(null).build();
        stubEnrolled(profile);
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        stubCatalogItem(request, bankTransferCard());

        assertThatThrownBy(() -> service.dispatch(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "BANK_NOT_LINKED");

        verifyNoInteractions(xtrmApiClient);
    }

    // BU-7: dispatch backstop — an active non-bank-transfer CASH item with NO SKU is un-routable and must be
    // rejected definitively (never call the gift-card API with a null SKU).
    @Test
    void dispatch_nonBankTransferItem_noSku_throwsUnroutable() {
        stubEnrolled(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build());
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        stubCatalogItem(request, giftCardItem(null));

        assertThatThrownBy(() -> service.dispatch(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "REDEMPTION_UNROUTABLE");

        verifyNoInteractions(xtrmApiClient);
    }

    @Test
    void dispatch_catalogItemNotFound_throwsUnroutable() {
        stubEnrolled(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build());
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        when(catalogItemRepository.findById(request.getCatalogItemId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dispatch(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "REDEMPTION_UNROUTABLE");

        verifyNoInteractions(xtrmApiClient);
    }

    @Test
    void dispatch_sendLimitFailure_throwsXtrmSendLimit() {
        stubEnrolled(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build());
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        stubCatalogItem(request, giftCardItem("SKU-1"));
        stubUserEmail("recipient@example.com");
        when(xtrmApiClient.transferFund(any(TransferFundCommand.class), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class)))
                .thenReturn(TransferFundResult.failed(List.of("Send limit exceeded for identity level"), false));

        assertThatThrownBy(() -> service.dispatch(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_SEND_LIMIT");
    }

    @Test
    void dispatch_definitiveRejection_throwsBusinessRulePayoutRejected() {
        stubEnrolled(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build());
        // retryable=false → XTRM definitively rejected; caller may release + fail.
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        stubCatalogItem(request, giftCardItem("SKU-1"));
        stubUserEmail("recipient@example.com");
        when(xtrmApiClient.transferFund(any(TransferFundCommand.class), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class)))
                .thenReturn(TransferFundResult.failed(List.of("Insufficient vendor funds"), false));

        assertThatThrownBy(() -> service.dispatch(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_PAYOUT_REJECTED");

        verify(userRedemptionRepository, never()).save(any());
    }

    @Test
    void dispatch_transientFailure_throwsExternalServiceException() {
        stubEnrolled(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build());
        // retryable=true → XTRM unreachable; caller must hold (never release), so signal a distinct type.
        RedemptionRequest request = buildRequest("cash", WalletType.INDIVIDUAL);
        stubCatalogItem(request, giftCardItem("SKU-1"));
        stubUserEmail("recipient@example.com");
        when(xtrmApiClient.transferFund(any(TransferFundCommand.class), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class)))
                .thenReturn(TransferFundResult.failed(List.of("Could not reach XTRM"), true));

        assertThatThrownBy(() -> service.dispatch(request))
                .isInstanceOf(ExternalServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "XTRM_UNAVAILABLE");
    }

    @Test
    void dispatch_nonCashCurrency_mapsToUpperCase() {
        stubEnrolled(PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build());
        RedemptionRequest request = buildRequest("eur", WalletType.INDIVIDUAL);
        stubCatalogItem(request, giftCardItem("SKU-1"));
        stubUserEmail("recipient@example.com");
        when(xtrmApiClient.transferFund(any(TransferFundCommand.class), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class))).thenReturn(TransferFundResult.ok(TX_ID, BENEFICIARY_TX_ID));

        service.dispatch(request);

        ArgumentCaptor<TransferFundCommand> captor = ArgumentCaptor.forClass(TransferFundCommand.class);
        verify(xtrmApiClient).transferFund(captor.capture(), any(com.tenxengage.app.service.xtrm.XtrmCredentials.class));
        assertThat(captor.getValue().currency()).isEqualTo("EUR");
    }

    // ---- batch preparation + dispatch ----

    @Test
    void prepareBatchItems_bank_buildsBankDestinationWithoutWalletLookup() {
        PartnerRedemption profile = PartnerRedemptionFixtures
                .enrolledWithBank(CLIENT_ID, USER_ID, PAT, "BANK-REF-1").build();
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));

        XtrmVendorService.BatchPreparation prep =
                service.prepareBatchItems(List.of(buildRequest("cash", WalletType.INDIVIDUAL)));

        assertThat(prep.fallbackIds()).isEmpty();
        assertThat(prep.prepared()).hasSize(1);
        XtrmVendorService.PreparedBatchItem item = prep.prepared().get(0);
        assertThat(item.sendMethodId()).isEqualTo(BANK_METHOD);
        assertThat(item.bankBeneficiaryId()).isEqualTo("BANK-REF-1");
        assertThat(item.walletId()).isNull();
        assertThat(item.payoutMethod()).isEqualTo(RedemptionPayoutMethod.BANK);
        verifyNoInteractions(xtrmApiClient); // BANK needs no wallet lookup
    }

    @Test
    void prepareBatchItems_anypay_resolvesRecipientUsdWalletId() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build(); // ANYPAY
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
        when(xtrmApiClient.getBeneficiaryWallets(any(GetWalletsCommand.class))).thenReturn(
                GetWalletsResult.ok(List.of(new WalletInfo("445566", "Wallet - USD", "USD", new BigDecimal("10.00")))));

        XtrmVendorService.BatchPreparation prep =
                service.prepareBatchItems(List.of(buildRequest("cash", WalletType.INDIVIDUAL)));

        assertThat(prep.fallbackIds()).isEmpty();
        assertThat(prep.prepared()).hasSize(1);
        XtrmVendorService.PreparedBatchItem item = prep.prepared().get(0);
        assertThat(item.sendMethodId()).isEqualTo(ANYPAY_METHOD);
        assertThat(item.walletId()).isEqualTo("445566");
        assertThat(item.bankBeneficiaryId()).isNull();
        assertThat(item.payoutMethod()).isEqualTo(RedemptionPayoutMethod.ANYPAY);
        assertThat(item.payoutDestinationLabel()).isEqualTo("Wallet USD ••5566");
    }

    @Test
    void prepareBatchItems_anypayWalletLookupFails_fallsBackToIndividual() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT).build();
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
        when(xtrmApiClient.getBeneficiaryWallets(any(GetWalletsCommand.class)))
                .thenReturn(GetWalletsResult.failed(List.of("boom"), true));

        RedemptionRequest req = buildRequest("cash", WalletType.INDIVIDUAL);
        XtrmVendorService.BatchPreparation prep = service.prepareBatchItems(List.of(req));

        assertThat(prep.prepared()).isEmpty();
        assertThat(prep.fallbackIds()).containsExactly(req.getId());
    }

    @Test
    void prepareBatchItems_card_buildsCardDestination() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT)
                .payoutMethod(RedemptionPayoutMethod.CARD).partnerLinkedCardId("CARD-TOK").build();
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));

        XtrmVendorService.BatchPreparation prep =
                service.prepareBatchItems(List.of(buildRequest("cash", WalletType.INDIVIDUAL)));

        assertThat(prep.fallbackIds()).isEmpty();
        assertThat(prep.prepared()).hasSize(1);
        XtrmVendorService.PreparedBatchItem item = prep.prepared().get(0);
        assertThat(item.sendMethodId()).isEqualTo(CARD_METHOD);
        assertThat(item.cardToken()).isEqualTo("CARD-TOK");
        assertThat(item.bankBeneficiaryId()).isNull();
        assertThat(item.walletId()).isNull();
        assertThat(item.payoutMethod()).isEqualTo(RedemptionPayoutMethod.CARD);
        verifyNoInteractions(xtrmApiClient); // CARD needs no wallet lookup
    }

    @Test
    void prepareBatchItems_cardNotLinked_fallsBackToIndividual() {
        PartnerRedemption profile = PartnerRedemptionFixtures.enrolled(CLIENT_ID, USER_ID, PAT)
                .payoutMethod(RedemptionPayoutMethod.CARD).partnerLinkedCardId(null).build();
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));

        RedemptionRequest req = buildRequest("cash", WalletType.INDIVIDUAL);
        XtrmVendorService.BatchPreparation prep = service.prepareBatchItems(List.of(req));

        assertThat(prep.prepared()).isEmpty();
        assertThat(prep.fallbackIds()).containsExactly(req.getId());
    }

    @Test
    void prepareBatchItems_notEnrolled_fallsBackToIndividual() {
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.empty());

        RedemptionRequest req = buildRequest("cash", WalletType.INDIVIDUAL);
        XtrmVendorService.BatchPreparation prep = service.prepareBatchItems(List.of(req));

        assertThat(prep.prepared()).isEmpty();
        assertThat(prep.fallbackIds()).containsExactly(req.getId());
    }

    @Test
    void dispatchPreparedBatch_buildsBatchTransferCommand() {
        XtrmVendorService.PreparedBatchItem item = new XtrmVendorService.PreparedBatchItem(
                REQUEST_ID, "txn1", PAT, new BigDecimal("5.00"), BANK_METHOD, "BANK-REF-1", null, null, null, null);
        when(xtrmApiClient.batchTransfer(any(BatchTransferCommand.class)))
                .thenReturn(BatchTransferResult.ok(List.of(new BatchItemResult("txn1", true, null))));

        BatchTransferResult result = service.dispatchPreparedBatch("BATCH-1", List.of(item));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<BatchTransferCommand> captor = ArgumentCaptor.forClass(BatchTransferCommand.class);
        verify(xtrmApiClient).batchTransfer(captor.capture());
        BatchTransferCommand cmd = captor.getValue();
        assertThat(cmd.customerBatchId()).isEqualTo("BATCH-1");
        assertThat(cmd.items()).hasSize(1);
        BatchItem sent = cmd.items().get(0);
        assertThat(sent.customerTransactionId()).isEqualTo("txn1");
        assertThat(sent.sendMethodId()).isEqualTo(BANK_METHOD);
        assertThat(sent.bankBeneficiaryId()).isEqualTo("BANK-REF-1");
    }

    @Test
    void dispatchPreparedBatch_cardItem_carriesCardToken() {
        XtrmVendorService.PreparedBatchItem item = new XtrmVendorService.PreparedBatchItem(
                REQUEST_ID, "txn1", PAT, new BigDecimal("5.00"), CARD_METHOD, null, null, "CARD-TOK-9", null, null);
        when(xtrmApiClient.batchTransfer(any(BatchTransferCommand.class)))
                .thenReturn(BatchTransferResult.ok(List.of(new BatchItemResult("txn1", true, null))));

        service.dispatchPreparedBatch("BATCH-1", List.of(item));

        ArgumentCaptor<BatchTransferCommand> captor = ArgumentCaptor.forClass(BatchTransferCommand.class);
        verify(xtrmApiClient).batchTransfer(captor.capture());
        BatchItem sent = captor.getValue().items().get(0);
        assertThat(sent.sendMethodId()).isEqualTo(CARD_METHOD);
        assertThat(sent.cardToken()).isEqualTo("CARD-TOK-9");
        assertThat(sent.bankBeneficiaryId()).isNull();
        assertThat(sent.walletId()).isNull();
    }

    // ---- helpers ----

    private void stubEnrolled(PartnerRedemption profile) {
        when(enrollmentService.ensureEnrolledForPayout(USER_ID)).thenReturn(PAT);
        when(userRedemptionRepository.findByUserIdAndClientId(USER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
    }

    /** A normal (gift-card) catalog item; {@code sku} may be null to model an un-routable CASH item. */
    private RedemptionCatalogItem giftCardItem(String sku) {
        return RedemptionCatalogItem.builder().isBankTransfer(false).providerItemId(sku).build();
    }

    /** The reserved per-client bank-transfer card (routes to the bank rail). */
    private RedemptionCatalogItem bankTransferCard() {
        return RedemptionCatalogItem.builder().isBankTransfer(true).build();
    }

    private void stubCatalogItem(RedemptionRequest request, RedemptionCatalogItem item) {
        when(catalogItemRepository.findById(request.getCatalogItemId())).thenReturn(Optional.of(item));
    }

    private void stubUserEmail(String email) {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn(email);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private RedemptionRequest buildRequest(String currencyId, WalletType walletType) {
        RedemptionRequest r = RedemptionRequest.builder()
                .clientId(CLIENT_ID)
                .userId(USER_ID)
                .walletId(UUID.randomUUID())
                .catalogItemId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currencyId(currencyId)
                .walletType(walletType)
                .status(RedemptionStatus.PROCESSING)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.CASH)
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        r.setId(REQUEST_ID);
        return r;
    }
}
