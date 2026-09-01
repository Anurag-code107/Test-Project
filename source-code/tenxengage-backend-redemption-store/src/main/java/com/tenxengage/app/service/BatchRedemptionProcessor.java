package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchItemResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.BatchTransferResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class BatchRedemptionProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchRedemptionProcessor.class);

    private final ClientRepository clientRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RewardWalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RedemptionOrchestrationService orchestrationService;
    private final WalletService walletService;
    private final XtrmVendorService xtrmVendorService;
    private final RedemptionWebhookService redemptionWebhookService;

    private volatile BatchRedemptionProcessor self;

    /** Max line items per XTRM BatchTransfer request (XTRM's ceiling is 20). A client's eligible set is split into
     *  chunks of this size so a high-volume day never exceeds the per-request limit; each chunk becomes its own
     *  CustomerBatchId and reconciles independently. */
    @Value("${redemption.batch.max-items-per-batch:20}")
    private int maxItemsPerBatch = 20;

    public BatchRedemptionProcessor(ClientRepository clientRepository,
                                    RedemptionRequestRepository redemptionRequestRepository,
                                    RewardWalletRepository walletRepository,
                                    LedgerEntryRepository ledgerEntryRepository,
                                    RedemptionOrchestrationService orchestrationService,
                                    WalletService walletService,
                                    XtrmVendorService xtrmVendorService,
                                    RedemptionWebhookService redemptionWebhookService) {
        this.clientRepository = clientRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.orchestrationService = orchestrationService;
        this.walletService = walletService;
        this.xtrmVendorService = xtrmVendorService;
        this.redemptionWebhookService = redemptionWebhookService;
    }

    @Autowired
    @Lazy
    public void setSelf(BatchRedemptionProcessor self) {
        this.self = self;
    }

    @Scheduled(cron = "${redemption.batch.cron:0 0 2 * * *}")
    public void processBatch() {
        LocalDate today = LocalDate.now();
        List<Client> clients = clientRepository.findAll();
        log.info("[step=batch_sweep_start] date={} clients={}", today, clients.size());
        int eligibleCount = 0, staleBatchCount = 0, strandedInstantCount = 0, strandedApprovalCount = 0;

        for (Client client : clients) {
            List<RedemptionRequest> eligible = redemptionRequestRepository
                    .findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                            client.getId(), RedemptionStatus.RESERVED, RedemptionProcessingMode.BATCH, today);
            eligibleCount += eligible.size();

            if (!eligible.isEmpty()) {
                dispatchBatchForClient(eligible);
            }

            // Reclaim BATCH items stuck in PROCESSING with no vendorReferenceId —
            // these were committed to PROCESSING but JVM crashed before dispatch fired.
            List<RedemptionRequest> staleProcessing = redemptionRequestRepository
                    .findByClientIdAndStatusAndProcessingModeAndVendorReferenceIdIsNull(
                            client.getId(), RedemptionStatus.PROCESSING, RedemptionProcessingMode.BATCH);
            staleBatchCount += staleProcessing.size();
            for (RedemptionRequest request : staleProcessing) {
                try {
                    log.info("[step=batch_dispatch_recovery] redemptionId={} — reclaiming stale PROCESSING",
                            request.getId());
                    self.dispatchAfterCommit(request.getId());
                } catch (Exception e) {
                    log.error("[step=batch_dispatch_recovery_failed] redemptionId={}", request.getId(), e);
                }
            }

            // Reclaim INSTANT items stranded in PROCESSING — the JVM died between the submission commit and
            // the AFTER_COMMIT vendor dispatch (CASH INSTANT dispatches after commit; see
            // RedemptionSubmissionService.onRedemptionRequested). The query filters dispatchAttemptedAt IS NULL,
            // so only never-attempted items are re-dispatched — a transfer that may already have reached the
            // vendor (dispatchAttemptedAt set, vendorReferenceId null) is left for manual reconciliation, never
            // re-sent. (NON_CASH INSTANT completes in-transaction and is never left PROCESSING+unattempted.)
            List<RedemptionRequest> strandedInstant = redemptionRequestRepository
                    .findByClientIdAndStatusAndProcessingModeAndVendorReferenceIdIsNull(
                            client.getId(), RedemptionStatus.PROCESSING, RedemptionProcessingMode.INSTANT);
            strandedInstantCount += strandedInstant.size();
            for (RedemptionRequest request : strandedInstant) {
                try {
                    log.info("[step=instant_dispatch_recovery] redemptionId={} — reclaiming stranded PROCESSING",
                            request.getId());
                    self.dispatchAfterCommit(request.getId());
                } catch (Exception e) {
                    log.error("[step=instant_dispatch_recovery_failed] redemptionId={}", request.getId(), e);
                }
            }

            // Reclaim APPROVAL_REQUIRED items approved but not yet dispatched (RESERVED, no vendorReferenceId).
            // These are stranded when the JVM died or async executor rejected work after commit.
            // Threshold: 5 minutes — enough time for the async event to have fired normally.
            Instant approvalRecoveryThreshold = java.time.Instant.now().minusSeconds(300);
            List<RedemptionRequest> strandedApprovals = redemptionRequestRepository
                    .findStrandedApprovalItems(client.getId(), approvalRecoveryThreshold);
            strandedApprovalCount += strandedApprovals.size();
            for (RedemptionRequest request : strandedApprovals) {
                try {
                    log.info("[step=approval_dispatch_recovery] redemptionId={} — reclaiming stranded approval",
                            request.getId());
                    self.dispatchAfterCommit(request.getId());
                } catch (Exception e) {
                    log.error("[step=approval_dispatch_recovery_failed] redemptionId={}", request.getId(), e);
                }
            }
        }
        log.info("[step=batch_sweep_done] eligible={} staleBatch={} strandedInstant={} strandedApproval={}",
                eligibleCount, staleBatchCount, strandedInstantCount, strandedApprovalCount);
    }

    /**
     * Transitions a RESERVED batch request to PROCESSING within its own REQUIRES_NEW transaction.
     * Returns true if the transition succeeded (caller should then call dispatchAfterCommit).
     * Returns false if the request was already in a non-RESERVED state (skip).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessing(UUID requestId) {
        RedemptionRequest request = redemptionRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", requestId));

        // Terminal-state guard AFTER lock — two concurrent callers could both read RESERVED without this
        if (request.getStatus() != RedemptionStatus.RESERVED) {
            log.info("[step=batch_dispatch_skip] redemptionId={}, status={}", requestId, request.getStatus());
            return false;
        }

        request.setStatus(RedemptionStatus.PROCESSING);
        // Do NOT stamp dispatchAttemptedAt here: the recovery query (findBy...VendorReferenceIdIsNull)
        // reclaims PROCESSING items where dispatchAttemptedAt IS NULL, so stamping here would
        // make crash-stranded items unrecoverable. dispatchAfterCommit stamps the marker
        // immediately before the outbound vendor call, which is the correct point.
        // Trade-off accepted: within the same processBatch run, recovery can theoretically
        // observe a just-committed PROCESSING item (same-pass TOCTOU), but both calls happen
        // synchronously in the same thread, making this extremely unlikely vs. crash stranding.
        redemptionRequestRepository.save(request);
        return true;
    }

    /**
     * Calls the vendor AFTER the PROCESSING status has been durably committed by markProcessing.
     * Running outside any transaction ensures a vendor success cannot be lost due to a DB rollback,
     * and the scheduler will not re-dispatch an already-PROCESSING request (terminal-state guard in markProcessing).
     * On exception: leaves in PROCESSING for manual reconciliation — do NOT release funds since the
     * vendor may have accepted the transfer (timeout/dropped-response scenario).
     */
    public void dispatchAfterCommit(UUID requestId) {
        RedemptionRequest request = redemptionRequestRepository.findById(requestId).orElse(null);
        if (request == null) return;

        // Mark attempt BEFORE calling vendor so recovery can distinguish
        // "never attempted" (dispatchAttemptedAt IS NULL) from "attempted but ambiguous".
        redemptionRequestRepository.findById(requestId).ifPresent(fresh -> {
            fresh.setDispatchAttemptedAt(java.time.Instant.now());
            redemptionRequestRepository.save(fresh);
        });

        try {
            orchestrationService.dispatch(request);
            // Persist the dispatch-output fields the vendor service set on the detached entity.
            redemptionRequestRepository.findById(requestId).ifPresent(fresh -> {
                fresh.setVendorReferenceId(request.getVendorReferenceId());
                fresh.setBeneficiaryTransactionId(request.getBeneficiaryTransactionId());
                fresh.setPayoutMethod(request.getPayoutMethod());
                fresh.setPayoutDestinationLabel(request.getPayoutDestinationLabel());
                redemptionRequestRepository.save(fresh);
            });
            log.info("[step=batch_dispatch_sent] redemptionId={}, clientId={}",
                    request.getId(), request.getClientId());
        } catch (BusinessRuleException definitive) {
            // Definitive vendor rejection (e.g. send limit) — the transfer did NOT execute. Safe to release the
            // reservation + fail via the shared settlement (its own tx, terminal-state guarded). Covers batch
            // fallback, INSTANT recovery and APPROVAL recovery — all of which dispatch through this method.
            log.warn("[step=dispatch_rejected] redemptionId={}, code={} — releasing reservation",
                    request.getId(), definitive.getErrorCode());
            redemptionWebhookService.settle(request.getId(), false,
                    "Payout rejected at dispatch: " + definitive.getErrorCode());
        } catch (Exception e) {
            // Ambiguous (timeout / dropped response) — the vendor MAY have accepted the transfer.
            // Do NOT release wallet or mark FAILED. Leave in PROCESSING/RESERVED so a success webhook or the
            // reconciliation cron can complete it legitimately.
            log.error("[step=batch_dispatch_ambiguous] redemptionId={} — outcome unknown, leaving in place for manual reconciliation",
                    request.getId(), e);
        }
    }

    /**
     * Dispatch a client's RESERVED batch items as one or more real XTRM BatchTransfers. Steps: (1) resolve each
     * item's rail + destination (ANYPAY needs a wallet-id lookup); un-batchable items (CARD, not-enrolled,
     * unresolved) fall back to individual dispatch. (2) Split the batchable items into chunks of
     * {@code maxItemsPerBatch}. Per chunk: transition to PROCESSING + stamp the batch ids (committed BEFORE the
     * vendor call, for crash safety), submit the batch, then fail+release rejected items; accepted items stay
     * PROCESSING and are completed later by the webhook or the reconciliation cron.
     */
    public void dispatchBatchForClient(List<RedemptionRequest> eligible) {
        XtrmVendorService.BatchPreparation prep = xtrmVendorService.prepareBatchItems(eligible);

        for (UUID id : prep.fallbackIds()) {
            try {
                if (self.markProcessing(id)) {
                    self.dispatchAfterCommit(id);
                }
            } catch (Exception e) {
                log.error("[step=batch_fallback_dispatch_failed] redemptionId={}", id, e);
            }
        }

        List<XtrmVendorService.PreparedBatchItem> prepared = prep.prepared();
        if (prepared.isEmpty()) {
            return;
        }
        // Chunk into requests of <= maxItemsPerBatch so a high-volume day never exceeds XTRM's per-request limit.
        // Each chunk is its own CustomerBatchId and reconciles independently via the batch status API.
        int chunkSize = Math.max(1, maxItemsPerBatch);
        for (int from = 0; from < prepared.size(); from += chunkSize) {
            int to = Math.min(from + chunkSize, prepared.size());
            dispatchOneBatch(new ArrayList<>(prepared.subList(from, to)));
        }
    }

    /** Transition one chunk to PROCESSING, submit it as a single BatchTransfer, and settle any rejected items. */
    private void dispatchOneBatch(List<XtrmVendorService.PreparedBatchItem> chunk) {
        String customerBatchId = "BATCH-" + UUID.randomUUID();
        List<XtrmVendorService.PreparedBatchItem> transitioned;
        try {
            transitioned = self.transitionBatchToProcessing(customerBatchId, chunk);
        } catch (Exception e) {
            log.error("[step=batch_transition_failed] batchId={}", customerBatchId, e);
            return;
        }
        if (transitioned.isEmpty()) {
            return;
        }
        // Submit AFTER the PROCESSING transition has durably committed.
        BatchTransferResult result;
        try {
            result = xtrmVendorService.dispatchPreparedBatch(customerBatchId, transitioned);
        } catch (Exception e) {
            // Ambiguous — the batch may or may not have been accepted. Leave PROCESSING for reconciliation.
            log.error("[step=batch_dispatch_ambiguous] batchId={} — leaving items PROCESSING for reconciliation",
                    customerBatchId, e);
            return;
        }
        self.settleBatchResult(result, transitioned);
        log.info("[step=batch_dispatch_sent] batchId={}, items={}", customerBatchId, transitioned.size());
    }

    /**
     * Transition prepared batch items RESERVED → PROCESSING and stamp {@code customerBatchId} +
     * {@code customerTransactionId} + {@code dispatchAttemptedAt}, in its own committed transaction so a crash
     * after the vendor call can't lose the linkage. Returns the items actually transitioned (skips any no longer
     * RESERVED).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<XtrmVendorService.PreparedBatchItem> transitionBatchToProcessing(
            String customerBatchId, List<XtrmVendorService.PreparedBatchItem> prepared) {
        List<XtrmVendorService.PreparedBatchItem> ok = new ArrayList<>();
        for (XtrmVendorService.PreparedBatchItem p : prepared) {
            RedemptionRequest r = redemptionRequestRepository.findByIdForUpdate(p.redemptionId()).orElse(null);
            if (r == null || r.getStatus() != RedemptionStatus.RESERVED) {
                continue;
            }
            r.setStatus(RedemptionStatus.PROCESSING);
            r.setDispatchAttemptedAt(Instant.now());
            r.setCustomerBatchId(customerBatchId);
            r.setCustomerTransactionId(p.customerTransactionId());
            r.setPayoutMethod(p.payoutMethod());
            r.setPayoutDestinationLabel(p.payoutDestinationLabel());
            redemptionRequestRepository.save(r);
            ok.add(p);
        }
        return ok;
    }

    /**
     * Settle a batch submission: rejected items are failed + released (definitive rejection at submission);
     * accepted items stay PROCESSING to be completed by the webhook / reconciliation cron. A non-accepted batch
     * (transport/parse failure) leaves everything PROCESSING for reconciliation.
     */
    public void settleBatchResult(BatchTransferResult result, List<XtrmVendorService.PreparedBatchItem> transitioned) {
        if (!result.success()) {
            log.warn("[step=batch_dispatch_ambiguous] batch not accepted — leaving items PROCESSING for reconciliation");
            return;
        }
        Map<String, UUID> redemptionByTxn = new HashMap<>();
        for (XtrmVendorService.PreparedBatchItem p : transitioned) {
            redemptionByTxn.put(p.customerTransactionId(), p.redemptionId());
        }
        for (BatchItemResult item : result.items()) {
            if (item.accepted()) {
                continue; // accepted → stays PROCESSING; reconciliation completes it via the batch status API
            }
            UUID redemptionId = redemptionByTxn.get(item.customerTransactionId());
            if (redemptionId == null) {
                continue;
            }
            try {
                redemptionWebhookService.settle(redemptionId, false, "Batch item rejected: " + item.error());
                log.info("[step=batch_item_rejected] redemptionId={}, reason={}", redemptionId, item.error());
            } catch (Exception e) {
                log.error("[step=batch_item_reject_settle_failed] redemptionId={}", redemptionId, e);
            }
        }
    }

    /** @deprecated Use markProcessing + dispatchAfterCommit instead */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchItem(UUID requestId) {
        boolean shouldDispatch = markProcessing(requestId);
        if (shouldDispatch) {
            dispatchAfterCommit(requestId);
        }
    }
}
