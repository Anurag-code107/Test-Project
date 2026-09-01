package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.event.CompanyDistributionSubmittedEvent;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Everything that happens <b>after</b> a distribution commits: paying each recipient, and recovering the ones
 * that got stuck.
 *
 * <p>Two entry points, deliberately sharing one settle path so a crash and a normal run converge:</p>
 * <ul>
 *   <li>{@link #onDistributionSubmitted} — the after-commit fan-out for a freshly submitted distribution.</li>
 *   <li>{@link #sweepStuckItems} — a scheduled retry for anything left unsettled. <b>Required</b>, because the
 *       existing crash-recovery sweep in {@code BatchRedemptionProcessor} scans only
 *       {@code redemption_requests}; a {@code WALLET_CREDIT} item has no such row and would otherwise sit
 *       reserved on the company wallet indefinitely.</li>
 * </ul>
 *
 * <p><b>Bounded concurrency.</b> Recipient count is uncapped by design, so the fan-out runs at a fixed
 * concurrency rather than one task per recipient. A 2 000-recipient distribution takes longer; it does not open
 * 2 000 XTRM connections or trip a vendor rate limit. The failure mode is slow, not wrong.</p>
 */
@Service
public class CompanyDistributionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CompanyDistributionDispatcher.class);

    private final CompanyDistributionItemRepository itemRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionOrchestrationService orchestrationService;
    private final RedemptionWebhookService webhookService;
    private final WalletCreditSettlementService walletCreditSettlement;
    private final ClientRepository clientRepository;
    private final CompanyDistributionRepository distributionRepository;
    private final DistributionNotificationService notifications;
    private final DistributionSettlementListener settlementListener;

    /** How many recipients are paid at once. Low on purpose — see the class note on bounded concurrency. */
    @Value("${redemption.distribution.dispatch-concurrency:4}")
    private int dispatchConcurrency = 4;

    /** Grace period before the sweep touches an item, so it never races the fan-out that is still running. */
    @Value("${redemption.distribution.sweep-grace-minutes:10}")
    private int sweepGraceMinutes = 10;

    /** Self-reference: the REQUIRES_NEW writes below must go through the proxy from this non-transactional path. */
    private CompanyDistributionDispatcher self;

    public CompanyDistributionDispatcher(CompanyDistributionItemRepository itemRepository,
                                          RedemptionRequestRepository redemptionRequestRepository,
                                          RedemptionOrchestrationService orchestrationService,
                                          RedemptionWebhookService webhookService,
                                          WalletCreditSettlementService walletCreditSettlement,
                                          ClientRepository clientRepository,
                                          CompanyDistributionRepository distributionRepository,
                                          DistributionNotificationService notifications,
                                          DistributionSettlementListener settlementListener) {
        this.itemRepository = itemRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.orchestrationService = orchestrationService;
        this.webhookService = webhookService;
        this.walletCreditSettlement = walletCreditSettlement;
        this.clientRepository = clientRepository;
        this.distributionRepository = distributionRepository;
        this.notifications = notifications;
        this.settlementListener = settlementListener;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy CompanyDistributionDispatcher self) {
        this.self = self;
    }

    /**
     * Fan out per recipient once the submit transaction has committed. Runs outside any transaction, so a
     * vendor success can never be lost to a rollback.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDistributionSubmitted(CompanyDistributionSubmittedEvent event) {
        List<CompanyDistributionItem> items =
                itemRepository.findByDistributionIdOrderByCreatedAtAsc(event.getDistributionId());
        log.info("[step=distribution_fanout_start] distributionId={} items={} concurrency={}",
                event.getDistributionId(), items.size(), dispatchConcurrency);

        runBounded(items, item -> processItem(item.getId(), item.isVendorPayout()));

        log.info("[step=distribution_fanout_done] distributionId={}", event.getDistributionId());
    }

    /**
     * Retry anything still unsettled. Covers a JVM death mid-fan-out, a transient wallet error, and an item
     * whose task never ran because the process went down between commit and dispatch.
     */
    @Scheduled(cron = "${redemption.distribution.sweep-cron:0 */15 * * * *}")
    public void sweepStuckItems() {
        Instant threshold = Instant.now().minusSeconds(sweepGraceMinutes * 60L);
        int swept = 0;
        for (Client client : clientRepository.findAll()) {
            List<CompanyDistributionItem> stuck = itemRepository
                    .findByClientIdAndStatusAndCreatedAtBefore(
                            client.getId(), DistributionItemStatus.RESERVED, threshold);
            if (stuck.isEmpty()) {
                continue;
            }
            log.warn("[step=distribution_sweep_found] clientId={} stuck={}", client.getId(), stuck.size());
            swept += stuck.size();
            runBounded(stuck, item -> processItem(item.getId(), false));
        }
        if (swept > 0) {
            log.warn("[step=distribution_sweep_done] retried={} — items older than {}m were still RESERVED",
                    swept, sweepGraceMinutes);
        }
    }

    /**
     * One recipient. Payout rails go to XTRM; the internal rail settles its ledger legs. Never throws — one
     * recipient's failure must not stop the others.
     */
    private void processItem(UUID itemId, boolean vendorPayout) {
        try {
            if (vendorPayout) {
                // The notification comes later, from the settle event — dispatch succeeding only means XTRM
                // accepted it, not that the seller has the money.
                dispatchPayoutItem(itemId);
            } else {
                // This rail has no redemption row and so no settle event to listen to. Notify here, where the
                // money has genuinely landed in the recipient's wallet.
                WalletCreditSettlementService.Outcome outcome = walletCreditSettlement.settleItem(itemId);
                notifyWalletCreditOutcome(itemId, outcome);
            }
        } catch (Exception e) {
            log.error("[step=distribution_item_failed] itemId={}", itemId, e);
        }
    }

    /**
     * Dispatch one gift-card / bank item, mirroring the proven CASH-INSTANT after-commit path.
     *
     * <p>The attempt marker is stamped in its own committed transaction <b>before</b> the vendor call. If that
     * write were lost while the transfer succeeded, a recovery pass would re-dispatch it — a double payment.</p>
     */
    private void dispatchPayoutItem(UUID itemId) {
        CompanyDistributionItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null || item.getRedemptionRequestId() == null) {
            return;
        }
        UUID requestId = item.getRedemptionRequestId();
        RedemptionRequest request = redemptionRequestRepository.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != RedemptionStatus.PROCESSING) {
            return; // already settled, or already attempted and awaiting the webhook
        }
        if (request.getVendorReferenceId() != null) {
            return; // already dispatched; the webhook or reconciliation will finish it
        }

        self.stampDispatchAttempt(requestId);
        try {
            orchestrationService.dispatch(request);
            if (request.getVendorReferenceId() != null) {
                self.persistVendorRef(requestId, request);
            }
            log.info("[step=distribution_payout_sent] itemId={} redemptionId={}", itemId, requestId);
        } catch (BusinessRuleException definitive) {
            // The vendor did not execute the transfer, so releasing this recipient's share is safe. settle()
            // handles the wallet release + status flip, and is terminal-state guarded.
            log.warn("[step=distribution_payout_rejected] itemId={} code={}", itemId, definitive.getErrorCode());
            webhookService.settle(requestId, false, "Distribution payout rejected: " + definitive.getErrorCode());
        } catch (Exception ambiguous) {
            // The vendor MAY have accepted. Leave PROCESSING — reconciliation (widened for COMPANY wallets in
            // F-8) polls and settles it. Never release on an unknown outcome.
            log.error("[step=distribution_payout_ambiguous] itemId={} — left PROCESSING for reconciliation",
                    itemId, ambiguous);
        }
    }


    /**
     * Tells the recipient their wallet transfer landed, then checks whether the whole distribution is
     * finished. Only SETTLED notifies the seller — a failed item releases their share and was never promised
     * to them, so the admin summary reports it instead.
     */
    private void notifyWalletCreditOutcome(UUID itemId, WalletCreditSettlementService.Outcome outcome) {
        if (outcome == WalletCreditSettlementService.Outcome.RETRY_LATER) {
            return; // unresolved; the sweep will come back to it
        }
        itemRepository.findById(itemId).ifPresent(item ->
                distributionRepository.findById(item.getDistributionId()).ifPresent(header -> {
                    if (outcome == WalletCreditSettlementService.Outcome.SETTLED) {
                        notifications.notifyAwardSettled(header, item, header.getRail().getDisplayName());
                    }
                    settlementListener.maybeNotifyFinished(header);
                }));
    }

    /** Durable "we are about to call the vendor" marker. Must commit before any money moves. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stampDispatchAttempt(UUID requestId) {
        redemptionRequestRepository.findById(requestId).ifPresent(r -> {
            r.setDispatchAttemptedAt(Instant.now());
            redemptionRequestRepository.save(r);
        });
    }

    /** Persists the dispatch output; a bare save would not commit from this non-transactional context. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistVendorRef(UUID requestId, RedemptionRequest dispatched) {
        redemptionRequestRepository.findById(requestId).ifPresent(r -> {
            r.setVendorReferenceId(dispatched.getVendorReferenceId());
            r.setBeneficiaryTransactionId(dispatched.getBeneficiaryTransactionId());
            r.setPayoutMethod(dispatched.getPayoutMethod());
            r.setPayoutDestinationLabel(dispatched.getPayoutDestinationLabel());
            redemptionRequestRepository.save(r);
        });
    }

    /**
     * Runs the tasks at a fixed concurrency and waits for them. A short-lived pool keeps this off the shared
     * {@code taskExecutor}, so a large distribution cannot starve unrelated async work.
     */
    private void runBounded(List<CompanyDistributionItem> items,
                            java.util.function.Consumer<CompanyDistributionItem> action) {
        if (items.isEmpty()) {
            return;
        }
        int concurrency = Math.max(1, dispatchConcurrency);
        Semaphore permits = new Semaphore(concurrency);
        try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
            for (CompanyDistributionItem item : items) {
                permits.acquire();
                pool.submit(() -> {
                    try {
                        action.accept(item);
                    } finally {
                        permits.release();
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[step=distribution_fanout_interrupted] remaining items left for the sweep");
        }
        // try-with-resources on ExecutorService close() awaits termination (Java 21), so every task has
        // finished — or been left RESERVED for the sweep — before this returns.
    }
}
