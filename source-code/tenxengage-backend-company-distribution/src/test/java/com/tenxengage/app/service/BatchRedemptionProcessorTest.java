package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchItemResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchTransferResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchRedemptionProcessorTest {

    @Mock private ClientRepository clientRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private RedemptionOrchestrationService orchestrationService;
    @Mock private WalletService walletService;
    @Mock private XtrmVendorService xtrmVendorService;
    @Mock private RedemptionWebhookService redemptionWebhookService;

    @InjectMocks private BatchRedemptionProcessor processor;

    private static final UUID CLIENT_A = UUID.randomUUID();
    private static final UUID CLIENT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor.setSelf(processor); // @Transactional(REQUIRES_NEW) proxy is bypassed in unit tests
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Defaults: nothing to batch, batch submission accepts nothing — tests override as needed.
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(List.of(), List.of()));
        when(xtrmVendorService.dispatchPreparedBatch(any(), any()))
                .thenReturn(BatchTransferResult.ok(List.of()));
    }

    private Client client(UUID id) {
        Client c = new Client();
        c.setId(id);
        return c;
    }

    private RedemptionRequest request(UUID clientId, RedemptionCategory category) {
        RedemptionRequest r = RedemptionRequest.builder()
                .clientId(clientId)
                .userId(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .catalogItemId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currencyId("cash")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.RESERVED)
                .processingMode(RedemptionProcessingMode.BATCH)
                .category(category)
                .scheduledBatchDate(LocalDate.now().minusDays(1))
                .submittedAt(Instant.now())
                .deleted(false)
                .build();
        r.setId(UUID.randomUUID());
        return r;
    }

    private XtrmVendorService.PreparedBatchItem prepared(RedemptionRequest r, String customerTxnId) {
        return new XtrmVendorService.PreparedBatchItem(
                r.getId(), customerTxnId, "PAT-1", r.getAmount(), "XTR94502", null, "WALLET-1", null, null, null);
    }

    private void eligible(UUID clientId, List<RedemptionRequest> items) {
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                eq(clientId), eq(RedemptionStatus.RESERVED), eq(RedemptionProcessingMode.BATCH), any()))
                .thenReturn(items);
    }

    // ---- batch path ----

    @Test
    void processBatch_batchesEligibleItems_andTransitionsToProcessingWithBatchIds() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of(req));
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(List.of(prepared(req, "txn1")), List.of()));
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(xtrmVendorService.dispatchPreparedBatch(any(), any()))
                .thenReturn(BatchTransferResult.ok(List.of(new BatchItemResult("txn1", true, null))));

        processor.processBatch();

        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(r ->
                r.getStatus() == RedemptionStatus.PROCESSING
                        && r.getCustomerBatchId() != null
                        && "txn1".equals(r.getCustomerTransactionId()));
        // Accepted item stays PROCESSING — not settled here (reconciliation completes it).
        verify(redemptionWebhookService, never()).settle(any(), eq(true), any());
    }

    @Test
    void processBatch_rejectedBatchItem_isFailedAndReleased() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of(req));
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(List.of(prepared(req, "txn1")), List.of()));
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(xtrmVendorService.dispatchPreparedBatch(any(), any()))
                .thenReturn(BatchTransferResult.ok(List.of(new BatchItemResult("txn1", false, "Invalid bank"))));

        processor.processBatch();

        verify(redemptionWebhookService).settle(eq(req.getId()), eq(false), any());
    }

    @Test
    void processBatch_batchNotAccepted_leavesItemsProcessing() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of(req));
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(List.of(prepared(req, "txn1")), List.of()));
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(xtrmVendorService.dispatchPreparedBatch(any(), any()))
                .thenReturn(BatchTransferResult.failed(List.of("Could not reach XTRM"), true));

        processor.processBatch();

        // Ambiguous batch → no settlement; items left PROCESSING for reconciliation.
        verify(redemptionWebhookService, never()).settle(any(), any(Boolean.class), any());
    }

    @Test
    void processBatch_chunksLargeEligibleSet_intoMultipleBatches() {
        ReflectionTestUtils.setField(processor, "maxItemsPerBatch", 2);
        RedemptionRequest r1 = request(CLIENT_A, RedemptionCategory.CASH);
        RedemptionRequest r2 = request(CLIENT_A, RedemptionCategory.CASH);
        RedemptionRequest r3 = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of(r1, r2, r3));
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(
                        List.of(prepared(r1, "txn1"), prepared(r2, "txn2"), prepared(r3, "txn3")), List.of()));
        Map<UUID, RedemptionRequest> byId = Map.of(r1.getId(), r1, r2.getId(), r2, r3.getId(), r3);
        when(redemptionRequestRepository.findByIdForUpdate(any()))
                .thenAnswer(inv -> Optional.ofNullable(byId.get(inv.getArgument(0))));
        when(xtrmVendorService.dispatchPreparedBatch(any(), any()))
                .thenReturn(BatchTransferResult.ok(List.of()));

        processor.processBatch();

        // 3 items, chunk size 2 → two BatchTransfer calls (2 + 1), each with its own CustomerBatchId.
        ArgumentCaptor<String> batchIdCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<XtrmVendorService.PreparedBatchItem>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(xtrmVendorService, times(2)).dispatchPreparedBatch(batchIdCaptor.capture(), itemsCaptor.capture());
        assertThat(batchIdCaptor.getAllValues()).doesNotHaveDuplicates().hasSize(2);
        assertThat(itemsCaptor.getAllValues()).extracting(List::size).containsExactlyInAnyOrder(2, 1);
    }

    // ---- fallback path (CARD / unresolved → individual dispatch) ----

    @Test
    void processBatch_fallbackItems_dispatchIndividually() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of(req));
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(List.of(), List.of(req.getId())));
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(redemptionRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));

        processor.processBatch();

        verify(orchestrationService).dispatch(req);
    }

    @Test
    void processBatch_fallbackDefinitiveRejection_releasesAndFails() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of(req));
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(List.of(), List.of(req.getId())));
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(redemptionRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
        doThrow(new BusinessRuleException("XTRM_SEND_LIMIT", "exceeds recipient send limit"))
                .when(orchestrationService).dispatch(req);

        processor.processBatch();

        // Definitive rejection at dispatch → release + fail via the shared settlement (not left in flight).
        verify(redemptionWebhookService).settle(eq(req.getId()), eq(false), any());
    }

    @Test
    void processBatch_fallbackAmbiguousFailure_leavesInFlight() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of(req));
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(List.of(), List.of(req.getId())));
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(redemptionRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
        doThrow(new RuntimeException("vendor timeout")).when(orchestrationService).dispatch(req);

        processor.processBatch();

        // Ambiguous → NOT released; left in flight for reconciliation.
        verify(redemptionWebhookService, never()).settle(any(), any(Boolean.class), any());
    }

    // ---- scoping / empty ----

    @Test
    void processBatch_noEligibleRows_exitsCleanly() {
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of());

        processor.processBatch();

        verify(orchestrationService, never()).dispatch(any());
        verify(xtrmVendorService, never()).dispatchPreparedBatch(any(), any());
    }

    @Test
    void processBatch_doesNotAffectOtherClients() {
        RedemptionRequest reqA = request(CLIENT_A, RedemptionCategory.CASH);
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A), client(CLIENT_B)));
        eligible(CLIENT_A, List.of(reqA));
        eligible(CLIENT_B, List.of());
        when(xtrmVendorService.prepareBatchItems(any()))
                .thenReturn(new XtrmVendorService.BatchPreparation(List.of(prepared(reqA, "txnA")), List.of()));
        when(redemptionRequestRepository.findByIdForUpdate(reqA.getId())).thenReturn(Optional.of(reqA));
        when(xtrmVendorService.dispatchPreparedBatch(any(), any()))
                .thenReturn(BatchTransferResult.ok(List.of(new BatchItemResult("txnA", true, null))));

        processor.processBatch();

        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(r -> r.getClientId().equals(CLIENT_A));
    }

    // ---- individual dispatch (deprecated dispatchItem) — unchanged ----

    @Test
    void dispatchItem_onDispatchFailure_leavesInProcessingForReconciliation() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.NON_CASH);
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));
        when(redemptionRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
        doThrow(new RuntimeException("vendor timeout")).when(orchestrationService).dispatch(any());

        processor.dispatchItem(req.getId());

        verify(walletService, never()).release(any(), any(), any(), any());
        ArgumentCaptor<RedemptionRequest> captor = ArgumentCaptor.forClass(RedemptionRequest.class);
        verify(redemptionRequestRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(r -> r.getStatus() == RedemptionStatus.FAILED);
    }

    @Test
    void dispatchItem_skipsNonReservedRequest() {
        RedemptionRequest req = request(CLIENT_A, RedemptionCategory.CASH);
        req.setStatus(RedemptionStatus.PROCESSING);
        when(redemptionRequestRepository.findByIdForUpdate(req.getId())).thenReturn(Optional.of(req));

        processor.dispatchItem(req.getId());

        verify(orchestrationService, never()).dispatch(any());
        verify(redemptionRequestRepository, never()).save(any());
    }

    @Test
    void processBatch_recoversStrandedInstantCashItems() {
        when(clientRepository.findAll()).thenReturn(List.of(client(CLIENT_A)));
        eligible(CLIENT_A, List.of());
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndVendorReferenceIdIsNull(
                CLIENT_A, RedemptionStatus.PROCESSING, RedemptionProcessingMode.BATCH)).thenReturn(List.of());
        RedemptionRequest stranded = request(CLIENT_A, RedemptionCategory.CASH);
        stranded.setProcessingMode(RedemptionProcessingMode.INSTANT);
        stranded.setStatus(RedemptionStatus.PROCESSING);
        when(redemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndVendorReferenceIdIsNull(
                CLIENT_A, RedemptionStatus.PROCESSING, RedemptionProcessingMode.INSTANT)).thenReturn(List.of(stranded));
        when(redemptionRequestRepository.findById(stranded.getId())).thenReturn(Optional.of(stranded));
        when(redemptionRequestRepository.findStrandedApprovalItems(eq(CLIENT_A), any())).thenReturn(List.of());

        processor.processBatch();

        verify(orchestrationService).dispatch(stranded);
    }
}
