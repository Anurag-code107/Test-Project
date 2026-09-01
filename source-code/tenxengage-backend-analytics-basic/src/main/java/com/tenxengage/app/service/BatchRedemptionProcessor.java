package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private volatile BatchRedemptionProcessor self;

    public BatchRedemptionProcessor(ClientRepository clientRepository,
                                    RedemptionRequestRepository redemptionRequestRepository,
                                    RewardWalletRepository walletRepository,
                                    LedgerEntryRepository ledgerEntryRepository,
                                    RedemptionOrchestrationService orchestrationService,
                                    WalletService walletService) {
        this.clientRepository = clientRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.orchestrationService = orchestrationService;
        this.walletService = walletService;
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

        for (Client client : clients) {
            List<RedemptionRequest> eligible = redemptionRequestRepository
                    .findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(
                            client.getId(), RedemptionStatus.RESERVED, RedemptionProcessingMode.BATCH, today);

            for (RedemptionRequest request : eligible) {
                try {
                    boolean shouldDispatch = self.markProcessing(request.getId());
                    if (shouldDispatch) {
                        self.dispatchAfterCommit(request.getId());
                    }
                } catch (Exception e) {
                    log.error("[step=batch_dispatch_failed] redemptionId={}", request.getId(), e);
                }
            }

            // Reclaim BATCH items stuck in PROCESSING with no vendorReferenceId —
            // these were committed to PROCESSING but JVM crashed before dispatch fired.
            List<RedemptionRequest> staleProcessing = redemptionRequestRepository
                    .findByClientIdAndStatusAndProcessingModeAndVendorReferenceIdIsNull(
                            client.getId(), RedemptionStatus.PROCESSING, RedemptionProcessingMode.BATCH);
            for (RedemptionRequest request : staleProcessing) {
                try {
                    log.info("[step=batch_dispatch_recovery] redemptionId={} — reclaiming stale PROCESSING",
                            request.getId());
                    self.dispatchAfterCommit(request.getId());
                } catch (Exception e) {
                    log.error("[step=batch_dispatch_recovery_failed] redemptionId={}", request.getId(), e);
                }
            }

            // Reclaim APPROVAL_REQUIRED items approved but not yet dispatched (RESERVED, no vendorReferenceId).
            // These are stranded when the JVM died or async executor rejected work after commit.
            // Threshold: 5 minutes — enough time for the async event to have fired normally.
            Instant approvalRecoveryThreshold = java.time.Instant.now().minusSeconds(300);
            List<RedemptionRequest> strandedApprovals = redemptionRequestRepository
                    .findStrandedApprovalItems(client.getId(), approvalRecoveryThreshold);
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
            // Persist vendorReferenceId set by the vendor service on the detached entity
            redemptionRequestRepository.findById(requestId).ifPresent(fresh -> {
                fresh.setVendorReferenceId(request.getVendorReferenceId());
                redemptionRequestRepository.save(fresh);
            });
            log.info("[step=batch_dispatch_sent] redemptionId={}, clientId={}",
                    request.getId(), request.getClientId());
        } catch (Exception e) {
            // Do NOT release wallet or mark FAILED — dispatch outcome is ambiguous.
            // A timeout or dropped response means the vendor may have accepted the transfer.
            // Leave in PROCESSING so a success webhook can complete legitimately.
            // Implement outbox/reconciliation in US-06 to handle definitive failures safely.
            log.error("[step=batch_dispatch_ambiguous] redemptionId={} — outcome unknown, leaving in PROCESSING for manual reconciliation",
                    request.getId(), e);
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
