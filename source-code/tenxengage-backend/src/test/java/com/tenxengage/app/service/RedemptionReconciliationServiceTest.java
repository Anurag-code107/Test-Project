package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.RedemptionWebhookService.SettlementOutcome;
import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchStatusItem;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchStatusResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetBatchStatusCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetTransactionDetailsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.TransactionStatusResult;
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionReconciliationServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private PartnerRedemptionRepository partnerRedemptionRepository;
    @Mock private XtrmApiClient xtrmApiClient;
    @Mock private RedemptionWebhookService redemptionWebhookService;

    private RedemptionReconciliationService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RedemptionReconciliationService(
                clientRepository, redemptionRequestRepository, partnerRedemptionRepository,
                xtrmApiClient, redemptionWebhookService,
                "Success,Completed,Released", "Failed", 3, 50);
        Client c = new Client();
        c.setId(CLIENT_ID);
        when(clientRepository.findAll()).thenReturn(List.of(c));
        when(redemptionRequestRepository.countStuckPastCap(any(), any(), any(), any(), any())).thenReturn(0L);
        when(redemptionWebhookService.settle(any(), anyBoolean(), any())).thenReturn(SettlementOutcome.COMPLETED);
    }

    private RedemptionRequest single(String vendorRef) {
        RedemptionRequest r = base();
        r.setVendorReferenceId(vendorRef);
        r.setBeneficiaryTransactionId("BEN-" + vendorRef);
        r.setProcessingMode(RedemptionProcessingMode.INSTANT);
        return r;
    }

    private RedemptionRequest batchItem(String batchId, String customerTxnId) {
        RedemptionRequest r = base();
        r.setCustomerBatchId(batchId);
        r.setCustomerTransactionId(customerTxnId);
        r.setProcessingMode(RedemptionProcessingMode.BATCH);
        return r;
    }

    private RedemptionRequest base() {
        RedemptionRequest r = RedemptionRequest.builder()
                .clientId(CLIENT_ID)
                .userId(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.PROCESSING)
                .category(RedemptionCategory.CASH)
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        r.setId(UUID.randomUUID());
        return r;
    }

    private void inFlight(RedemptionRequest... items) {
        when(redemptionRequestRepository.findInFlightForReconciliation(
                eq(CLIENT_ID), eq(RedemptionCategory.CASH), eq(WalletType.INDIVIDUAL), any(), any()))
                .thenReturn(List.of(items));
    }

    private void patFor(RedemptionRequest r, String pat) {
        when(partnerRedemptionRepository.findByUserIdAndClientId(r.getUserId(), CLIENT_ID))
                .thenReturn(Optional.of(PartnerRedemptionFixtures.enrolled(CLIENT_ID, r.getUserId(), pat).build()));
    }

    // ---- single-mode ----

    @Test
    void single_success_settlesComplete() {
        RedemptionRequest r = single("879264");
        inFlight(r);
        patFor(r, "PAT-1");
        when(xtrmApiClient.getTransactionDetails(any(GetTransactionDetailsCommand.class)))
                .thenReturn(TransactionStatusResult.of("Success"));

        service.reconcile();

        verify(redemptionWebhookService).settle(r.getId(), true, null);
    }

    @Test
    void single_polledByBeneficiaryId_notVendorRef() {
        RedemptionRequest r = single("879264"); // vendorRef=879264, beneficiaryTxn=BEN-879264
        inFlight(r);
        patFor(r, "PAT-1");
        when(xtrmApiClient.getTransactionDetails(any(GetTransactionDetailsCommand.class)))
                .thenReturn(TransactionStatusResult.of("Success"));

        service.reconcile();

        ArgumentCaptor<GetTransactionDetailsCommand> captor =
                ArgumentCaptor.forClass(GetTransactionDetailsCommand.class);
        verify(xtrmApiClient).getTransactionDetails(captor.capture());
        // Wallet status API must be keyed on the beneficiary id, never the payment-side vendorReferenceId.
        org.assertj.core.api.Assertions.assertThat(captor.getValue().transactionId()).isEqualTo("BEN-879264");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().recipientUserId()).isEqualTo("PAT-1");
    }

    @Test
    void single_missingBeneficiaryId_isAmbiguousNotPolled() {
        RedemptionRequest r = base();
        r.setVendorReferenceId("879264"); // has payment id but NO beneficiary id → cannot poll wallet API
        r.setProcessingMode(RedemptionProcessingMode.INSTANT);
        inFlight(r);

        service.reconcile();

        verify(xtrmApiClient, never()).getTransactionDetails(any());
        verify(redemptionWebhookService, never()).settle(any(), anyBoolean(), any());
    }

    @Test
    void single_failed_settlesFailed() {
        RedemptionRequest r = single("879264");
        inFlight(r);
        patFor(r, "PAT-1");
        when(xtrmApiClient.getTransactionDetails(any(GetTransactionDetailsCommand.class)))
                .thenReturn(TransactionStatusResult.of("Failed"));

        service.reconcile();

        verify(redemptionWebhookService).settle(eq(r.getId()), eq(false), any());
    }

    @Test
    void single_pending_doesNotSettle() {
        RedemptionRequest r = single("879264");
        inFlight(r);
        patFor(r, "PAT-1");
        when(xtrmApiClient.getTransactionDetails(any(GetTransactionDetailsCommand.class)))
                .thenReturn(TransactionStatusResult.of("Processing"));

        service.reconcile();

        verify(redemptionWebhookService, never()).settle(any(), anyBoolean(), any());
    }

    @Test
    void single_notFound_doesNotSettle() {
        RedemptionRequest r = single("879264");
        inFlight(r);
        patFor(r, "PAT-1");
        when(xtrmApiClient.getTransactionDetails(any(GetTransactionDetailsCommand.class)))
                .thenReturn(TransactionStatusResult.notFound());

        service.reconcile();

        verify(redemptionWebhookService, never()).settle(any(), anyBoolean(), any());
    }

    // ---- batch-mode ----

    @Test
    void batch_settlesPerItemStatus_inOneCall() {
        RedemptionRequest ok = batchItem("BATCH-1", "txn1");
        RedemptionRequest bad = batchItem("BATCH-1", "txn2");
        RedemptionRequest pend = batchItem("BATCH-1", "txn3");
        inFlight(ok, bad, pend);
        when(xtrmApiClient.getBatchStatus(any(GetBatchStatusCommand.class))).thenReturn(
                BatchStatusResult.ok(List.of(
                        new BatchStatusItem("txn1", "Success"),
                        new BatchStatusItem("txn2", "Failed"),
                        new BatchStatusItem("txn3", "Processing")), false, 0));

        service.reconcile();

        verify(redemptionWebhookService).settle(ok.getId(), true, null);
        verify(redemptionWebhookService).settle(eq(bad.getId()), eq(false), any());
        verify(redemptionWebhookService, never()).settle(eq(pend.getId()), anyBoolean(), any());
        // One status call for the whole batch (single page).
        verify(xtrmApiClient).getBatchStatus(any(GetBatchStatusCommand.class));
    }

    @Test
    void batch_transientStatusFailure_doesNotSettle() {
        RedemptionRequest r = batchItem("BATCH-1", "txn1");
        inFlight(r);
        when(xtrmApiClient.getBatchStatus(any(GetBatchStatusCommand.class)))
                .thenReturn(BatchStatusResult.failed(true));

        service.reconcile();

        verify(redemptionWebhookService, never()).settle(any(), anyBoolean(), any());
    }

    // ---- ambiguous ----

    @Test
    void ambiguous_noIds_isSkippedWithoutPollingOrSettling() {
        RedemptionRequest r = base(); // no vendorReferenceId, no customerBatchId
        inFlight(r);

        service.reconcile();

        verifyNoInteractions(xtrmApiClient);
        verify(redemptionWebhookService, never()).settle(any(), anyBoolean(), any());
    }
}
