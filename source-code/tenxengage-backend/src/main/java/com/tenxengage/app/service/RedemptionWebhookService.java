package com.tenxengage.app.service;

import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WebhookStatus;
import com.tenxengage.app.event.RedemptionCompletedEvent;
import com.tenxengage.app.event.RedemptionFailedEvent;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RedemptionWebhookEventRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class RedemptionWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionWebhookService.class);

    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RewardWalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RedemptionWebhookEventRepository webhookEventRepository;
    private final RedemptionEventProducer redemptionEventProducer;
    private final ApplicationEventPublisher eventPublisher;

    public RedemptionWebhookService(RedemptionRequestRepository redemptionRequestRepository,
                                     RewardWalletRepository walletRepository,
                                     LedgerEntryRepository ledgerEntryRepository,
                                     RedemptionWebhookEventRepository webhookEventRepository,
                                     RedemptionEventProducer redemptionEventProducer,
                                     ApplicationEventPublisher eventPublisher) {
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.redemptionEventProducer = redemptionEventProducer;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Core webhook processing logic. Called by the controller after HMAC validation and
     * idempotency check. Also called directly by service tests to bypass the HTTP layer.
     *
     * @param redemptionRequestId the ID extracted from the vendor payload
     * @param webhookEvent        the persisted event (status=RECEIVED at entry)
     * @param completed           true = vendor confirms success (DEBIT path); false = failure (RELEASE path)
     * @param failureReason       populated only when completed=false
     */
    @Transactional
    public void process(UUID redemptionRequestId,
                 RedemptionWebhookEvent webhookEvent,
                 boolean completed,
                 String failureReason) {

        RedemptionRequest request = redemptionRequestRepository.findByIdForUpdate(redemptionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", redemptionRequestId));

        // Terminal-state guard — dead-letter if already resolved.
        if (request.getStatus() == RedemptionStatus.COMPLETED
                || request.getStatus() == RedemptionStatus.FAILED) {
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason("Redemption already in terminal state: " + request.getStatus());
            webhookEventRepository.save(webhookEvent);
            log.warn("[step=webhook-dead-lettered] redemptionId={}, existingStatus={}",
                    redemptionRequestId, request.getStatus());
            return;
        }

        // PROCESSING: vendor dispatch is confirmed in flight (INSTANT/BATCH flow).
        // RESERVED + APPROVAL_REQUIRED: admin approved and dispatch was sent post-commit;
        //   this flow skips PROCESSING since dispatch happens outside the approval transaction.
        // RESERVED + BATCH/INSTANT: batch queue or INSTANT pre-dispatch — vendor NOT yet called;
        //   webhooks for these are dead-lettered to prevent premature finalization.
        boolean dispatchable = request.getStatus() == RedemptionStatus.PROCESSING
                || (request.getStatus() == RedemptionStatus.RESERVED
                        && request.getProcessingMode() == RedemptionProcessingMode.APPROVAL_REQUIRED);
        if (!dispatchable) {
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason("Webhook received for non-dispatchable request: " + request.getStatus());
            webhookEventRepository.save(webhookEvent);
            log.warn("[step=webhook-dead-lettered-not-dispatchable] redemptionId={}, status={}",
                    redemptionRequestId, request.getStatus());
            return;
        }

        // Require dispatch to have been attempted — dispatchAttemptedAt IS NOT NULL means
        // the vendor was called. A null value means dispatch never ran; settling without
        // a dispatch attempt is unsafe. Using dispatchAttemptedAt (not vendorReferenceId)
        // allows fast callbacks that arrive before vendorReferenceId is persisted to proceed.
        if (request.getDispatchAttemptedAt() == null) {
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason("Webhook received but dispatch was never attempted");
            webhookEventRepository.save(webhookEvent);
            log.warn("[step=webhook-dead-lettered-no-dispatch-attempt] redemptionId={}", redemptionRequestId);
            return;
        }

        RewardWallet wallet = walletRepository.findByIdForUpdate(request.getWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("RewardWallet", "id", request.getWalletId()));

        try {
            if (completed) {
                applyCompletion(request, wallet, webhookEvent);
            } else {
                applyFailure(request, wallet, webhookEvent, failureReason);
            }
        } catch (Exception e) {
            // Any processing exception → dead-letter; return 200 to vendor to stop retry storm
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason(e.getClass().getSimpleName() + ": " + e.getMessage());
            webhookEventRepository.save(webhookEvent);
            log.error("[step=webhook-dead-lettered] redemptionId={}", redemptionRequestId, e);
        }
    }

    /**
     * Shared settlement core used by BOTH the webhook and the reconciliation cron. Locks the request + wallet,
     * guards against a terminal state (so a late webhook and the cron can never double-settle), then debits
     * (complete) or releases the reservation (fail), writes the ledger entry, flips status, and publishes the
     * domain event. Returns the outcome so the caller can record its own audit trail. No webhook-event coupling.
     */
    @Transactional
    public SettlementOutcome settle(UUID redemptionRequestId, boolean completed, String failureReason) {
        RedemptionRequest request = redemptionRequestRepository.findByIdForUpdate(redemptionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionRequest", "id", redemptionRequestId));
        if (request.getStatus() == RedemptionStatus.COMPLETED || request.getStatus() == RedemptionStatus.FAILED) {
            return SettlementOutcome.ALREADY_TERMINAL;
        }
        RewardWallet wallet = walletRepository.findByIdForUpdate(request.getWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("RewardWallet", "id", request.getWalletId()));
        if (completed) {
            return applyCompletionCore(request, wallet)
                    ? SettlementOutcome.COMPLETED : SettlementOutcome.INSUFFICIENT_RESERVED;
        }
        return applyFailureCore(request, wallet, failureReason)
                ? SettlementOutcome.FAILED : SettlementOutcome.INSUFFICIENT_RESERVED;
    }

    /** Outcome of {@link #settle}. */
    public enum SettlementOutcome { COMPLETED, FAILED, ALREADY_TERMINAL, INSUFFICIENT_RESERVED }

    // ---- webhook wrappers (preserve the webhook-event bookkeeping) --------------------------------------

    private void applyCompletion(RedemptionRequest request, RewardWallet wallet, RedemptionWebhookEvent webhookEvent) {
        if (!applyCompletionCore(request, wallet)) {
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason("Insufficient reserved balance for completion: reserved="
                    + wallet.getReservedBalance() + ", amount=" + request.getAmount());
            webhookEventRepository.save(webhookEvent);
            return;
        }
        webhookEvent.setStatus(WebhookStatus.PROCESSED);
        webhookEvent.setProcessedAt(Instant.now());
        webhookEventRepository.save(webhookEvent);
    }

    private void applyFailure(RedemptionRequest request, RewardWallet wallet,
                              RedemptionWebhookEvent webhookEvent, String failureReason) {
        if (!applyFailureCore(request, wallet, failureReason)) {
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason("Insufficient reserved balance for failure settlement: reserved="
                    + wallet.getReservedBalance() + ", amount=" + request.getAmount());
            webhookEventRepository.save(webhookEvent);
            return;
        }
        webhookEvent.setStatus(WebhookStatus.PROCESSED);
        webhookEvent.setProcessedAt(Instant.now());
        webhookEventRepository.save(webhookEvent);
    }

    // ---- pure settlement (no webhook event) ------------------------------------------------------------

    /** Debit path. Returns false (no mutation) if the reserved balance is insufficient. */
    private boolean applyCompletionCore(RedemptionRequest request, RewardWallet wallet) {
        BigDecimal resvBefore = wallet.getReservedBalance();
        if (resvBefore.compareTo(request.getAmount()) < 0) {
            log.error("[step=settle-insufficient-reserved] redemptionId={}, reserved={}, amount={}",
                    request.getId(), resvBefore, request.getAmount());
            return false;
        }
        wallet.setReservedBalance(resvBefore.subtract(request.getAmount()));
        walletRepository.save(wallet);

        LedgerEntry debitEntry = ledgerEntryRepository.save(LedgerEntry.builder()
                .clientId(request.getClientId())
                .rewardWalletId(request.getWalletId())
                .entryType(LedgerEntryType.DEBIT)
                .amount(request.getAmount())
                .currencyId(request.getCurrencyId())
                .referenceType("REDEMPTION_REQUEST")
                .referenceId(request.getId())
                .availableBalanceBefore(wallet.getAvailableBalance())
                .availableBalanceAfter(wallet.getAvailableBalance())
                .reservedBalanceBefore(resvBefore)
                .reservedBalanceAfter(wallet.getReservedBalance())
                .build());
        request.setDebitLedgerEntryId(debitEntry.getId());

        request.setStatus(RedemptionStatus.COMPLETED);
        request.setCompletedAt(Instant.now());
        redemptionRequestRepository.save(request);

        eventPublisher.publishEvent(new RedemptionCompletedEvent(this, request));
        log.info("[step=settle-completed] redemptionId={}", request.getId());
        return true;
    }

    /** Release path (refund the reservation to available). Returns false if the reserved balance is insufficient. */
    private boolean applyFailureCore(RedemptionRequest request, RewardWallet wallet, String failureReason) {
        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore = wallet.getReservedBalance();
        if (resvBefore.compareTo(request.getAmount()) < 0) {
            log.error("[step=settle-insufficient-reserved-failure] redemptionId={}, reserved={}, amount={}",
                    request.getId(), resvBefore, request.getAmount());
            return false;
        }
        wallet.setAvailableBalance(availBefore.add(request.getAmount()));
        wallet.setReservedBalance(resvBefore.subtract(request.getAmount()));
        walletRepository.save(wallet);

        LedgerEntry releaseEntry = ledgerEntryRepository.save(LedgerEntry.builder()
                .clientId(request.getClientId())
                .rewardWalletId(request.getWalletId())
                .entryType(LedgerEntryType.RELEASE)
                .amount(request.getAmount())
                .currencyId(request.getCurrencyId())
                .referenceType("REDEMPTION_REQUEST")
                .referenceId(request.getId())
                .availableBalanceBefore(availBefore)
                .availableBalanceAfter(wallet.getAvailableBalance())
                .reservedBalanceBefore(resvBefore)
                .reservedBalanceAfter(wallet.getReservedBalance())
                .build());
        request.setReleaseLedgerEntryId(releaseEntry.getId());

        request.setStatus(RedemptionStatus.FAILED);
        request.setFailureReason(failureReason);
        redemptionRequestRepository.save(request);

        eventPublisher.publishEvent(new RedemptionFailedEvent(this, request));
        log.info("[step=settle-failed] redemptionId={}, reason={}", request.getId(), failureReason);
        return true;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRedemptionCompleted(RedemptionCompletedEvent event) {
        redemptionEventProducer.publishRedemptionCompleted(event.getRequest());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRedemptionFailed(RedemptionFailedEvent event) {
        redemptionEventProducer.publishRedemptionFailed(event.getRequest());
    }
}
