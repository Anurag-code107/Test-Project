package com.tenxengage.app.service;

import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.LedgerEntryType;
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

        // Only PROCESSING requests are eligible for webhook callbacks — dead-letter everything else.
        // Locks are acquired before these checks to prevent TOCTOU with concurrent webhooks.
        if (request.getStatus() == RedemptionStatus.COMPLETED
                || request.getStatus() == RedemptionStatus.FAILED) {
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason("Redemption already in terminal state: " + request.getStatus());
            webhookEventRepository.save(webhookEvent);
            log.warn("[step=webhook-dead-lettered] redemptionId={}, existingStatus={}",
                    redemptionRequestId, request.getStatus());
            return;
        }

        if (request.getStatus() != RedemptionStatus.PROCESSING) {
            webhookEvent.setStatus(WebhookStatus.DEAD_LETTERED);
            webhookEvent.setFailureReason("Webhook received for non-PROCESSING request: " + request.getStatus());
            webhookEventRepository.save(webhookEvent);
            log.warn("[step=webhook-dead-lettered-not-processing] redemptionId={}, status={}",
                    redemptionRequestId, request.getStatus());
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

    private void applyCompletion(RedemptionRequest request,
                                 RewardWallet wallet,
                                 RedemptionWebhookEvent webhookEvent) {
        BigDecimal resvBefore = wallet.getReservedBalance();
        wallet.setReservedBalance(resvBefore.subtract(request.getAmount()));
        walletRepository.save(wallet);

        ledgerEntryRepository.save(LedgerEntry.builder()
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

        request.setStatus(RedemptionStatus.COMPLETED);
        request.setCompletedAt(Instant.now());
        redemptionRequestRepository.save(request);

        webhookEvent.setStatus(WebhookStatus.PROCESSED);
        webhookEvent.setProcessedAt(Instant.now());
        webhookEventRepository.save(webhookEvent);

        eventPublisher.publishEvent(new RedemptionCompletedEvent(this, request));
        log.info("[step=webhook-completed] redemptionId={}", request.getId());
    }

    private void applyFailure(RedemptionRequest request,
                              RewardWallet wallet,
                              RedemptionWebhookEvent webhookEvent,
                              String failureReason) {
        BigDecimal availBefore = wallet.getAvailableBalance();
        BigDecimal resvBefore = wallet.getReservedBalance();
        wallet.setAvailableBalance(availBefore.add(request.getAmount()));
        wallet.setReservedBalance(resvBefore.subtract(request.getAmount()));
        walletRepository.save(wallet);

        ledgerEntryRepository.save(LedgerEntry.builder()
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

        request.setStatus(RedemptionStatus.FAILED);
        request.setFailureReason(failureReason);
        redemptionRequestRepository.save(request);

        webhookEvent.setStatus(WebhookStatus.PROCESSED);
        webhookEvent.setProcessedAt(Instant.now());
        webhookEventRepository.save(webhookEvent);

        eventPublisher.publishEvent(new RedemptionFailedEvent(this, request));
        log.info("[step=webhook-failed] redemptionId={}, reason={}", request.getId(), failureReason);
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
