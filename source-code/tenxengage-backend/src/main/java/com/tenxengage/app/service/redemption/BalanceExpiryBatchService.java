package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.BalanceExpirationPolicy;
import com.tenxengage.app.entity.BalanceExpiryNotice;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.ExpirationMode;
import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.BalanceExpiryNoticeRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.SchedulerBalanceExpirationRepository;
import com.tenxengage.app.repository.projection.WalletLastActivityProjection;
import com.tenxengage.app.service.AuditLogService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled batch service for the warn phase of the balance expiry sweep.
 *
 * <p>Runs cross-tenant (no ambient TenantContext). Per-wallet queries bind clientId explicitly
 * via {@link SchedulerBalanceExpirationRepository} — never via Hibernate @Filter.
 * See spec.md § Security Design for the documented isolation deviation.
 */
@Service
public class BalanceExpiryBatchService {

    private static final Logger log = LoggerFactory.getLogger(BalanceExpiryBatchService.class);

    /** Entry types that count as "activity" for the inactivity clock (ADR #1, #5). */
    static final Set<LedgerEntryType> ACTIVITY_ENTRY_TYPES = EnumSet.of(
            LedgerEntryType.CREDIT,
            LedgerEntryType.DEBIT,
            LedgerEntryType.RESERVE,
            LedgerEntryType.RETURN_CREDIT
    );

    /** AC-10: candidate-wallet page size — bounds batch memory for large tenants. */
    static final int CANDIDATE_BATCH_SIZE = 500;

    private final SchedulerBalanceExpirationRepository schedulerRepo;
    private final BalanceExpiryNoticeRepository noticeRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final AuditLogService auditLogService;
    private final MeterRegistry meterRegistry;
    private final WalletNotificationRecipientResolver recipientResolver;

    /**
     * Self-proxy reference — injected lazily to break the circular dependency.
     *
     * <p>Spring's @Transactional works by wrapping the bean in a proxy. When
     * {@code processExpireForNotice} is called via {@code this.}, the proxy is bypassed
     * and the transaction never starts. Calling via {@code self.} goes through the proxy,
     * so the @Transactional boundary is correctly established for each notice.
     *
     * <p>@Lazy is required to avoid a circular-dependency error at context startup
     * (BalanceExpiryBatchService depends on itself).
     */
    @Autowired
    @Lazy
    private BalanceExpiryBatchService self;

    public BalanceExpiryBatchService(SchedulerBalanceExpirationRepository schedulerRepo,
                                     BalanceExpiryNoticeRepository noticeRepository,
                                     LedgerEntryRepository ledgerEntryRepository,
                                     NotificationEventProducer notificationEventProducer,
                                     AuditLogService auditLogService,
                                     MeterRegistry meterRegistry,
                                     WalletNotificationRecipientResolver recipientResolver) {
        this.schedulerRepo = schedulerRepo;
        this.noticeRepository = noticeRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.notificationEventProducer = notificationEventProducer;
        this.auditLogService = auditLogService;
        this.meterRegistry = meterRegistry;
        this.recipientResolver = recipientResolver;
    }

    // ── Scheduled entry point ──────────────────────────────────────────────────

    /**
     * Runs the warn + expire phases of the expiry sweep. Cron is externalized so local/demo
     * can run it frequently; the default is off-peak 02:00 UTC daily (prod).
     *
     * <p>MDC fields set per the observability spec: featureArea, userId (SYSTEM).
     * tenantId is set per-wallet inside the inner loop.
     */
    @Scheduled(cron = "${redemption.balance-expiry.cron:0 0 2 * * *}")
    public void runExpirySweep() {
        MDC.put("featureArea", "balance-expiration");
        MDC.put("userId", "SYSTEM");
        long startMs = System.currentTimeMillis();
        int warnedCount = 0;
        int expiredCount = 0;

        try {
            List<BalanceExpirationPolicy> policies = schedulerRepo.findAllByEnabledTrueAndDeletedFalse();

            log.info("step=balance_expiry_batch_started enabledPolicyCount={}", policies.size());

            for (BalanceExpirationPolicy policy : policies) {
                try {
                    warnedCount += processWarnPhaseForPolicy(policy);
                } catch (Exception e) {
                    log.error("Warn phase failed for policy={} client={}: {}",
                            policy.getId(), policy.getClientId(), e.getMessage(), e);
                }
                try {
                    expiredCount += processExpirePhaseForPolicy(policy);
                } catch (Exception e) {
                    log.error("Expire phase failed for policy={} client={}: {}",
                            policy.getId(), policy.getClientId(), e.getMessage(), e);
                }
            }

            long durationMs = System.currentTimeMillis() - startMs;
            log.info("step=balance_expiry_batch_finished warnedCount={} expiredCount={} durationMs={}",
                    warnedCount, expiredCount, durationMs);

            meterRegistry.summary("balance_expiry.batch.duration_ms").record(durationMs);

        } finally {
            // Clear all MDC keys set during the sweep to prevent bleed into the next scheduler tick
            MDC.remove("featureArea");
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }

    // ── Warn phase ─────────────────────────────────────────────────────────────

    /**
     * Processes the warn phase for one policy: finds candidate wallets, computes the
     * scheduled expiry date, applies the grace window, and upserts a NOTIFIED notice.
     *
     * <p>Note: each per-wallet call to {@link #processWarnForWallet} performs its own
     * repository operations in auto-commit mode (matching the existing
     * {@link ReturnTimeoutScheduler} pattern). A shared {@code @Transactional} wrapper
     * around the full policy loop is intentionally omitted — the self-call from
     * {@link #runExpirySweep} would bypass Spring's AOP proxy and silently drop the
     * transaction boundary.
     *
     * @return number of wallets warned in this policy pass
     */
    int processWarnPhaseForPolicy(BalanceExpirationPolicy policy) {
        UUID clientId = policy.getClientId();
        String currencyId = policy.getCurrencyId();
        MDC.put("tenantId", clientId.toString());

        try {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            // Grace window: must have had at least one full leadTimeDays window since enabled_at (ADR #4, FR-09.7)
            if (!isGraceWindowPassed(policy, today)) {
                log.debug("Grace window not passed for policy={} client={} currency={}",
                        policy.getId(), clientId, currencyId);
                return 0;
            }

            // AC-10: page through candidate wallets so a large tenant never loads them all into memory.
            int warnedCount = 0;
            int pageNum = 0;
            List<RewardWallet> page;
            do {
                page = schedulerRepo.findExpiryCandidateWallets(
                        clientId, currencyId, PageRequest.of(pageNum, CANDIDATE_BATCH_SIZE));
                if (page.isEmpty()) {
                    break;
                }

                // AC-9: bulk-fetch last-activity for this page in one GROUP BY (avoids the per-wallet N+1).
                Map<UUID, Instant> lastActivityByWallet = bulkLastActivity(policy, page);

                for (RewardWallet wallet : page) {
                    try {
                        boolean warned = processWarnForWallet(policy, wallet, today, lastActivityByWallet);
                        if (warned) {
                            warnedCount++;
                        }
                    } catch (Exception e) {
                        log.error("Warn phase failed for wallet={} currency={} client={}: {}",
                                wallet.getId(), currencyId, clientId, e.getMessage(), e);
                    }
                }
                pageNum++;
            } while (page.size() == CANDIDATE_BATCH_SIZE);

            return warnedCount;
        } finally {
            MDC.remove("tenantId");
        }
    }

    /**
     * AC-9: bulk-fetches last-activity for a page of candidate wallets in a single GROUP BY query,
     * avoiding the per-wallet N+1. Returns an empty map for FIXED_DATE policies (which don't use
     * last-activity) so no query is issued.
     */
    private Map<UUID, Instant> bulkLastActivity(BalanceExpirationPolicy policy, List<RewardWallet> page) {
        if (policy.getExpirationMode() != ExpirationMode.INACTIVITY) {
            return Map.of();
        }
        List<UUID> walletIds = page.stream().map(RewardWallet::getId).toList();
        Map<UUID, Instant> map = new HashMap<>();
        for (WalletLastActivityProjection row : schedulerRepo.findLastActivityForWallets(
                policy.getClientId(), policy.getCurrencyId(), walletIds, ACTIVITY_ENTRY_TYPES)) {
            map.put(row.getWalletId(), row.getLastActivityAt());
        }
        return map;
    }

    /**
     * Processes the warn phase for a single wallet under a given policy.
     *
     * @return true if a warn notice was created/sent, false if skipped
     */
    private boolean processWarnForWallet(BalanceExpirationPolicy policy,
                                          RewardWallet wallet,
                                          LocalDate today,
                                          Map<UUID, Instant> lastActivityByWallet) {
        UUID clientId = policy.getClientId();
        String currencyId = policy.getCurrencyId();

        // Compute scheduled expiry date (last-activity pre-fetched in bulk for the page — AC-9)
        Instant lastActivityAt = lastActivityByWallet.get(wallet.getId());
        LocalDate scheduledExpiryDate = computeScheduledExpiryDate(policy, today, lastActivityAt);
        if (scheduledExpiryDate == null) {
            // Not yet in the lead window (or no activity recorded) — skip
            return false;
        }

        // Idempotency check: find or create the notice (AC-6, FR-09.8)
        Optional<BalanceExpiryNotice> existing = noticeRepository
                .findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                        clientId, wallet.getId(), currencyId, scheduledExpiryDate);

        if (existing.isPresent()) {
            BalanceExpiryNotice notice = existing.get();
            if (notice.getNotifiedAt() != null) {
                // Already notified — idempotent skip (FR-09.8)
                log.debug("step=balance_expiry_idempotent_skip walletId={} currencyId={} scheduledExpiryDate={}",
                        wallet.getId(), currencyId, scheduledExpiryDate);
                return false;
            }
            // Existing SCHEDULED notice — advance to NOTIFIED
            return advanceToNotified(notice, wallet, policy, today);
        }

        // Create new notice directly in NOTIFIED state to avoid a redundant SCHEDULED save
        // (the SCHEDULED intermediate state is never observable externally)
        BalanceExpiryNotice notice = BalanceExpiryNotice.builder()
                .clientId(clientId)
                .walletId(wallet.getId())
                .currencyId(currencyId)
                .policyId(policy.getId())
                .scheduledExpiryDate(scheduledExpiryDate)
                .status(ExpiryNoticeStatus.SCHEDULED)
                .build();

        return advanceToNotified(notice, wallet, policy, today);
    }

    /**
     * Advances a SCHEDULED notice to NOTIFIED, sets notified_at/notified_amount,
     * and emits BALANCE_EXPIRING_SOON afterCommit.
     */
    private boolean advanceToNotified(BalanceExpiryNotice notice,
                                       RewardWallet wallet,
                                       BalanceExpirationPolicy policy,
                                       LocalDate today) {
        BigDecimal amount = wallet.getAvailableBalance();
        UUID clientId = policy.getClientId();
        String currencyId = policy.getCurrencyId();
        LocalDate scheduledExpiryDate = notice.getScheduledExpiryDate();
        UUID walletId = wallet.getId();

        // AC-8 (FR-09.7): persist the notice as SCHEDULED first — this gives it an id (used as the
        // event referenceId) and materialises the unique (wallet, currency, scheduledExpiryDate) row
        // for idempotency/retry. We deliberately do NOT set notified_at yet: a notice must never be
        // expirable until its advance warning is confirmed delivered. The expire phase only acts on
        // NOTIFIED notices, so a SCHEDULED (un-warned) notice is never expired.
        if (notice.getId() == null) {
            notice.setStatus(ExpiryNoticeStatus.SCHEDULED);
            notice = noticeRepository.save(notice);
        }

        List<UUID> recipients = recipientResolver.resolve(wallet);
        if (recipients.isEmpty()) {
            // Directed event with empty recipients would broadcast tenant-wide — skip the warning.
            // Leave the notice SCHEDULED so it is never expired without a delivered warning (FR-09.7).
            log.warn("step=balance_expiry_recipients_unresolved phase=warn walletId={} walletType={} currencyId={}",
                    walletId, wallet.getWalletType(), currencyId);
            meterRegistry.counter("balance_expiry.recipients_unresolved.total", "currencyId", currencyId).increment();
            return false;
        }
        NotificationEvent event = new NotificationEvent(
                "BALANCE_EXPIRING_SOON",
                clientId,
                "Reward Balance Expiring Soon",
                "Your reward balance is scheduled to expire on " + scheduledExpiryDate,
                "BALANCE_EXPIRY_NOTICE",
                notice.getId(),
                null,
                recipients,
                Map.of(
                        "currencyId", currencyId,
                        "amount", amount.toPlainString(),
                        "scheduledExpiryDate", scheduledExpiryDate.toString(),
                        "walletId", walletId.toString()
                )
        );

        // Publish-and-confirm: the warn phase runs with no enclosing transaction (auto-commit), so a
        // synchronous confirm is safe here — there is no rolled-back-state concern, and the only caller
        // is the off-peak sweep (never a request thread). An unconfirmed send leaves the notice
        // SCHEDULED (notified_at null) so the next sweep retries the warning. This closes the
        // suppression gap where a failed warning still let the balance expire un-warned.
        boolean delivered = notificationEventProducer.publishAndConfirm(event);
        if (!delivered) {
            log.warn("step=balance_expiry_warn_undelivered walletId={} currencyId={} scheduledExpiryDate={}"
                            + " — notice left SCHEDULED for retry on next sweep",
                    walletId, currencyId, scheduledExpiryDate);
            meterRegistry.counter("balance_expiry.warn_undelivered.total", "currencyId", currencyId).increment();
            return false;
        }

        // Delivery confirmed → only now mark NOTIFIED + notified_at (the expirability marker)
        notice.setStatus(ExpiryNoticeStatus.NOTIFIED);
        notice.setNotifiedAt(Instant.now());
        notice.setNotifiedAmount(amount);
        noticeRepository.save(notice);

        log.info("step=balance_expiry_warned walletId={} currencyId={} scheduledExpiryDate={} amount={}",
                walletId, currencyId, scheduledExpiryDate, amount);

        // meterRegistry.counter() is idempotent and does a single registry lookup per (name,tags) pair
        // — cheaper than Counter.builder().register() which constructs a builder object on every call
        meterRegistry.counter("balance_expiry.warned.total", "currencyId", currencyId).increment();

        return true;
    }

    // ── Expire phase ───────────────────────────────────────────────────────────

    /**
     * Processes the expire phase for one policy: finds NOTIFIED notices whose
     * scheduled_expiry_date &le; today, re-checks the policy is still enabled (AC-4),
     * and executes the expiry debit for each.
     *
     * @return number of wallets expired in this policy pass
     */
    int processExpirePhaseForPolicy(BalanceExpirationPolicy policy) {
        UUID clientId = policy.getClientId();
        MDC.put("tenantId", clientId.toString());

        try {
            // AC-4: re-verify the governing policy is still enabled at execution time.
            // The policy object was loaded at sweep start; if it was disabled mid-sweep skip it.
            if (!policy.isEnabled()) {
                log.debug("Expire phase skipped — policy disabled mid-sweep policy={} client={}",
                        policy.getId(), clientId);
                return 0;
            }

            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            // Scoped to this policy's policyId — avoids cross-policy over-fetch (perf fix: ADV-01)
            List<BalanceExpiryNotice> policyDueNotices = noticeRepository
                    .findByClientIdAndPolicyIdAndStatusAndScheduledExpiryDateLessThanEqual(
                            clientId, policy.getId(), ExpiryNoticeStatus.NOTIFIED, today);

            int expiredCount = 0;
            for (BalanceExpiryNotice notice : policyDueNotices) {
                try {
                    // Call via self-proxy so @Transactional on processExpireForNotice is respected.
                    // Direct this.processExpireForNotice(...) would bypass the AOP proxy and silently
                    // run without a transaction boundary.
                    boolean expired = self.processExpireForNotice(notice, policy);
                    if (expired) {
                        expiredCount++;
                    }
                } catch (Exception e) {
                    log.error("Expire phase failed for notice={} wallet={} currency={} client={}: {}",
                            notice.getId(), notice.getWalletId(), notice.getCurrencyId(),
                            clientId, e.getMessage(), e);
                }
            }

            return expiredCount;
        } finally {
            MDC.remove("tenantId");
        }
    }

    /**
     * Executes the expiry debit for a single NOTIFIED notice.
     *
     * <p>Steps:
     * <ol>
     *   <li>Idempotency guard: check if an EXPIRY ledger entry already exists (AC-2).</li>
     *   <li>Row-lock the wallet (AC-3, FR-09.11).</li>
     *   <li>Write EXPIRY ledger entry using live availableBalance under the lock.</li>
     *   <li>Reduce availableBalance, mark notice EXPIRED.</li>
     *   <li>Emit BALANCE_EXPIRED afterCommit (AC-1).</li>
     *   <li>Write audit log (AC-6).</li>
     * </ol>
     *
     * @return true if the expiry was executed, false if skipped (idempotent re-run or zero balance)
     */
    @Transactional
    boolean processExpireForNotice(BalanceExpiryNotice notice, BalanceExpirationPolicy policy) {
        UUID clientId = notice.getClientId();
        UUID walletId = notice.getWalletId();
        String currencyId = notice.getCurrencyId();

        // AC-3, FR-09.11: Row-lock the wallet FIRST for atomic expiry.
        // The idempotency check (AC-2) runs AFTER the lock so that a concurrent thread cannot
        // simultaneously pass the check and both proceed to write a duplicate EXPIRY entry.
        // (Lock-then-check is the correct order; check-then-lock has a TOCTOU race window.)
        Optional<RewardWallet> lockedWalletOpt = schedulerRepo.lockWallet(walletId, clientId);
        if (lockedWalletOpt.isEmpty()) {
            log.warn("Wallet not found or could not be locked walletId={} clientId={} — skipping expiry",
                    walletId, clientId);
            return false;
        }
        RewardWallet wallet = lockedWalletOpt.get();

        // AC-2: Idempotency — check if an EXPIRY ledger entry already exists for this notice.
        // Runs AFTER acquiring the row lock so no concurrent thread can win the race between
        // this check and the subsequent ledger entry write.
        boolean alreadyExpired = ledgerEntryRepository
                .existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                        walletId, "BALANCE_EXPIRY_NOTICE", notice.getId(), LedgerEntryType.EXPIRY);
        if (alreadyExpired) {
            log.debug("step=balance_expiry_idempotent_skip walletId={} currencyId={} scheduledExpiryDate={}",
                    walletId, currencyId, notice.getScheduledExpiryDate());
            return false;
        }

        // Use live availableBalance under the row lock (ADR #3: reserved balance NEVER touched)
        BigDecimal expiredAmount = wallet.getAvailableBalance();
        if (expiredAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // Zero or negative available balance — no debit to make (Edge Case 8)
            log.info("step=balance_expiry_zero_balance walletId={} currencyId={} — skipping expiry",
                    walletId, currencyId);
            return false;
        }

        // Fix: revalidate the inactivity condition under the lock before the irreversible debit.
        // The notice's scheduledExpiryDate was computed in an earlier warn phase; activity after the
        // warning moves the inactivity clock forward and invalidates it. FIXED_DATE is activity-independent.
        if (policy.getExpirationMode() == ExpirationMode.INACTIVITY) {
            Instant freshLastActivity = schedulerRepo.findLastActivityAt(
                    clientId, currencyId, walletId, ACTIVITY_ENTRY_TYPES);
            if (freshLastActivity == null) {
                // Anomaly: a NOTIFIED notice exists but no activity is visible now (append-only ledger).
                // Do not destroy balance on inconsistent data.
                log.warn("step=balance_expiry_revalidation_no_activity walletId={} currencyId={} noticeId={}",
                        walletId, currencyId, notice.getId());
                return false;
            }
            LocalDate freshExpiryDate = freshLastActivity.atZone(ZoneOffset.UTC).toLocalDate()
                    .plusDays(policy.getInactivityDays());
            if (!freshExpiryDate.equals(notice.getScheduledExpiryDate())) {
                // Activity moved the inactivity clock — this notice is stale. Cancel it; a future
                // sweep re-warns for the new date if the wallet goes inactive again.
                notice.setStatus(ExpiryNoticeStatus.CANCELLED);
                notice.setCancelledAt(Instant.now());
                noticeRepository.save(notice);
                log.info("step=balance_expiry_revalidation_stale walletId={} currencyId={} scheduledExpiryDate={} freshExpiryDate={}",
                        walletId, currencyId, notice.getScheduledExpiryDate(), freshExpiryDate);
                meterRegistry.counter("balance_expiry.revalidation_stale.total", "currencyId", currencyId).increment();
                return false;
            }
        }

        Instant now = Instant.now();

        // FR-09.5: Write immutable EXPIRY ledger entry
        LedgerEntry ledgerEntry = LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(walletId)
                .entryType(LedgerEntryType.EXPIRY)
                .amount(expiredAmount)
                .currencyId(currencyId)
                .referenceType("BALANCE_EXPIRY_NOTICE")
                .referenceId(notice.getId())
                .note("Balance expired per policy " + policy.getId())
                .availableBalanceBefore(expiredAmount)
                .availableBalanceAfter(BigDecimal.ZERO)
                .reservedBalanceBefore(wallet.getReservedBalance())
                .reservedBalanceAfter(wallet.getReservedBalance())
                .build();
        LedgerEntry savedEntry = ledgerEntryRepository.save(ledgerEntry);

        // Reduce wallet availableBalance
        wallet.setAvailableBalance(BigDecimal.ZERO);

        // Mark notice EXPIRED
        notice.setStatus(ExpiryNoticeStatus.EXPIRED);
        notice.setExpiredAt(now);
        notice.setExpiredAmount(expiredAmount);
        notice.setLedgerEntryId(savedEntry.getId());
        noticeRepository.save(notice);

        log.info("step=balance_expired walletId={} currencyId={} expiredAmount={} ledgerEntryId={}",
                walletId, currencyId, expiredAmount, savedEntry.getId());

        meterRegistry.counter("balance_expiry.executed.total",
                "currencyId", currencyId,
                "walletType", wallet.getWalletType().name()).increment();
        meterRegistry.counter("balance_expiry.amount.total", "currencyId", currencyId)
                .increment(expiredAmount.doubleValue());

        // AC-1, Rollback safety: emit BALANCE_EXPIRED afterCommit — never for a rolled-back expiry
        List<UUID> recipients = recipientResolver.resolve(wallet);
        if (recipients.isEmpty()) {
            log.warn("step=balance_expiry_recipients_unresolved phase=expire walletId={} walletType={} currencyId={}",
                    walletId, wallet.getWalletType(), currencyId);
            meterRegistry.counter("balance_expiry.recipients_unresolved.total", "currencyId", currencyId).increment();
        } else {
            NotificationEvent expiredEvent = new NotificationEvent(
                    "BALANCE_EXPIRED",
                    clientId,
                    "Reward Balance Expired",
                    "Your reward balance of " + expiredAmount.toPlainString() + " " + currencyId + " has expired",
                    "BALANCE_EXPIRY_NOTICE",
                    notice.getId(),
                    null,
                    recipients,
                    Map.of(
                            "currencyId", currencyId,
                            "expiredAmount", expiredAmount.toPlainString(),
                            "expiredAt", now.toString(),
                            "walletId", walletId.toString(),
                            "ledgerEntryId", savedEntry.getId().toString()
                    )
            );

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notificationEventProducer.publish(expiredEvent);
                    }
                });
            } else {
                // No active transaction (e.g., unit test context) — publish directly
                notificationEventProducer.publish(expiredEvent);
            }
        }

        // AC-6: Audit log — expiry execution with SYSTEM actor
        auditLogService.logSystemEvent(
                AuditAction.EXPIRED,
                AuditResourceType.REWARD_WALLET,
                walletId,
                "RewardWallet:" + walletId,
                "Expired unused balance",
                clientId
        );

        return true;
    }

    // ── Business logic helpers ─────────────────────────────────────────────────

    /**
     * Computes the scheduledExpiryDate for a wallet given its policy.
     * Returns null if the wallet is not yet in the lead window (should not be warned yet).
     *
     * <p>INACTIVITY: candidate when lastActivityAt + inactivityDays ≤ today AND
     *   today ≥ scheduledExpiryDate - leadTimeDays (lead window start).
     * <p>FIXED_DATE: candidate when today ≥ fixedExpiryDate - leadTimeDays.
     */
    LocalDate computeScheduledExpiryDate(BalanceExpirationPolicy policy,
                                          LocalDate today,
                                          Instant lastActivityAt) {
        if (policy.getExpirationMode() == ExpirationMode.INACTIVITY) {
            if (lastActivityAt == null) {
                // No activity recorded for this wallet+currency — cannot compute an inactivity
                // expiry, so skip safely (AC-9: lastActivityAt is pre-fetched in bulk by the caller).
                return null;
            }

            LocalDate expiryDate = lastActivityAt.atZone(ZoneOffset.UTC).toLocalDate()
                    .plusDays(policy.getInactivityDays());

            // Warn when today is within the lead window: today >= expiryDate - leadTimeDays
            LocalDate leadWindowStart = expiryDate.minusDays(policy.getLeadTimeDays());
            if (!today.isBefore(leadWindowStart)) {
                return expiryDate;
            }
            return null;

        } else if (policy.getExpirationMode() == ExpirationMode.FIXED_DATE) {
            LocalDate fixedDate = policy.getFixedExpiryDate();
            if (fixedDate == null) {
                return null;
            }
            LocalDate leadWindowStart = fixedDate.minusDays(policy.getLeadTimeDays());
            if (!today.isBefore(leadWindowStart)) {
                return fixedDate;
            }
            return null;
        } else {
            throw new IllegalStateException("Unhandled ExpirationMode: " + policy.getExpirationMode());
        }
    }

    /**
     * Grace window check (ADR #4, FR-09.7):
     * A wallet cannot be notified until at least one full leadTimeDays window has elapsed since enabled_at.
     */
    boolean isGraceWindowPassed(BalanceExpirationPolicy policy, LocalDate today) {
        Instant enabledAt = policy.getEnabledAt();
        if (enabledAt == null) {
            return false;
        }
        LocalDate graceEnd = enabledAt.atZone(ZoneOffset.UTC).toLocalDate()
                .plusDays(policy.getLeadTimeDays());
        return !today.isBefore(graceEnd);
    }

}
