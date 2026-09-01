package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.BalanceExpiryNotice;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.BalanceExpiryNoticeRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing {@link BalanceExpiryNotice} state transitions.
 *
 * <p>Handles the cancellation flow (FR-09.10): when a policy is disabled or relaxed,
 * pending SCHEDULED/NOTIFIED notices are cancelled and already-notified partners
 * receive a BALANCE_EXPIRY_CANCELLED notification.
 */
@Service
@Transactional(readOnly = true)
public class BalanceExpiryNoticeService {

    private static final Logger log = LoggerFactory.getLogger(BalanceExpiryNoticeService.class);

    private static final List<ExpiryNoticeStatus> CANCELLABLE_STATUSES =
            List.of(ExpiryNoticeStatus.SCHEDULED, ExpiryNoticeStatus.NOTIFIED);

    private final BalanceExpiryNoticeRepository noticeRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final AuditLogService auditLogService;
    private final RewardWalletRepository walletRepository;
    private final WalletNotificationRecipientResolver recipientResolver;

    public BalanceExpiryNoticeService(BalanceExpiryNoticeRepository noticeRepository,
                                      NotificationEventProducer notificationEventProducer,
                                      AuditLogService auditLogService,
                                      RewardWalletRepository walletRepository,
                                      WalletNotificationRecipientResolver recipientResolver) {
        this.noticeRepository = noticeRepository;
        this.notificationEventProducer = notificationEventProducer;
        this.auditLogService = auditLogService;
        this.walletRepository = walletRepository;
        this.recipientResolver = recipientResolver;
    }

    /**
     * Cancels all pending (SCHEDULED or NOTIFIED) notices for a given policy (FR-09.10, AC-5).
     *
     * <p>Already-NOTIFIED notices also emit a BALANCE_EXPIRY_CANCELLED notification afterCommit
     * so that partners who received an advance warning are re-notified of the cancellation.
     *
     * <p>Called by {@link BalanceExpirationPolicyService#upsertPolicy} when a policy is disabled
     * or its parameters are relaxed (BE-3).
     *
     * @param policyId the policy whose pending notices should be cancelled
     * @param clientId the owning tenant
     * @return the count of notices cancelled
     */
    @Transactional
    public int cancelPendingForPolicy(UUID policyId, UUID clientId) {
        List<BalanceExpiryNotice> pending = noticeRepository
                .findByClientIdAndPolicyIdAndStatusIn(clientId, policyId, CANCELLABLE_STATUSES);

        if (pending.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        List<NotificationEvent> cancellationEvents = new ArrayList<>();

        for (BalanceExpiryNotice notice : pending) {
            boolean wasNotified = notice.getStatus() == ExpiryNoticeStatus.NOTIFIED;

            notice.setStatus(ExpiryNoticeStatus.CANCELLED);
            notice.setCancelledAt(now);
            noticeRepository.save(notice);

            // AC-5: emit BALANCE_EXPIRY_CANCELLED only for previously-NOTIFIED notices —
            // SCHEDULED notices never sent an advance warning so no cancellation event needed
            if (wasNotified) {
                RewardWallet wallet = walletRepository.findById(notice.getWalletId()).orElse(null);
                if (wallet == null) {
                    log.warn("step=balance_expiry_cancel_wallet_missing walletId={} noticeId={}",
                            notice.getWalletId(), notice.getId());
                    continue;
                }
                List<UUID> recipients = recipientResolver.resolve(wallet);
                if (recipients.isEmpty()) {
                    log.warn("step=balance_expiry_cancel_recipients_unresolved walletId={} noticeId={}",
                            notice.getWalletId(), notice.getId());
                } else {
                    NotificationEvent event = new NotificationEvent(
                            "BALANCE_EXPIRY_CANCELLED",
                            clientId,
                            "Reward Balance Expiry Cancelled",
                            "Your previously scheduled reward balance expiry has been cancelled",
                            "BALANCE_EXPIRY_NOTICE",
                            notice.getId(),
                            null,
                            recipients,
                            Map.of(
                                    "currencyId", notice.getCurrencyId(),
                                    "scheduledExpiryDate", notice.getScheduledExpiryDate().toString(),
                                    "walletId", notice.getWalletId().toString()
                            )
                    );
                    cancellationEvents.add(event);
                }
            }
        }

        int cancelledCount = pending.size();
        log.info("step=balance_expiry_cancelled cancelledCount={} policyId={}",
                cancelledCount, policyId);

        // Emit all cancellation events afterCommit — never for rolled-back cancellations
        if (!cancellationEvents.isEmpty()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (NotificationEvent event : cancellationEvents) {
                            notificationEventProducer.publish(event);
                        }
                    }
                });
            } else {
                // No active transaction (e.g., unit test context) — publish directly
                for (NotificationEvent event : cancellationEvents) {
                    notificationEventProducer.publish(event);
                }
            }
        }

        // AC-6: Audit log — cancellation with caller actor (resolved from SecurityContext by auditLogService).
        // Use synchronous log() rather than logAsync() because logAsync() is @Async and runs on a
        // different thread where the SecurityContext (ThreadLocal) is null — the audit entry would be
        // silently dropped (auditLogService.log warns and returns when clientId cannot be resolved).
        auditLogService.log(
                AuditAction.CANCELLED,
                AuditResourceType.BALANCE_EXPIRATION_POLICY,
                policyId,
                "BalanceExpirationPolicy:" + policyId,
                "Cancelled pending balance expirations",
                Map.of("cancelledCount", cancelledCount)
        );

        return cancelledCount;
    }

}
