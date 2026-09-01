package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.BalanceExpirationPolicy;
import com.tenxengage.app.entity.BalanceExpiryNotice;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.BalanceExpiryNoticeRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.SchedulerBalanceExpirationRepository;
import com.tenxengage.app.repository.projection.WalletLastActivityProjection;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.testdata.BalanceExpirationPolicyFixtures;
import com.tenxengage.app.testdata.BalanceExpiryNoticeFixtures;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the warn + expire phases of {@link BalanceExpiryBatchService}.
 *
 * <p>In unit tests, {@code TransactionSynchronizationManager.isSynchronizationActive()} is
 * {@code false}, so the expire phase's publish-or-register logic falls through to the direct-publish
 * branch (established pattern, see ReturnServiceTest). The warn phase uses
 * {@code publishAndConfirm} (AC-8) — synchronous, no transaction registration.
 *
 * <p>AC-9/AC-10: candidate wallets are paged ({@code findExpiryCandidateWallets(..., Pageable)})
 * and last-activity is bulk-fetched per page ({@code findLastActivityForWallets}); tests stub the
 * paged + bulk repository methods accordingly.
 */
@ExtendWith(MockitoExtension.class)
class BalanceExpiryBatchServiceTest {

    @Mock private SchedulerBalanceExpirationRepository schedulerRepo;
    @Mock private BalanceExpiryNoticeRepository noticeRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private NotificationEventProducer notificationEventProducer;
    @Mock private AuditLogService auditLogService;
    @Mock private WalletNotificationRecipientResolver recipientResolver;

    private MeterRegistry meterRegistry;
    private BalanceExpiryBatchService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new BalanceExpiryBatchService(
                schedulerRepo, noticeRepository, ledgerEntryRepository,
                notificationEventProducer, auditLogService, meterRegistry, recipientResolver);
        lenient().when(recipientResolver.resolve(any(RewardWallet.class)))
                .thenReturn(List.of(USER_ID));
    }

    // ── INACTIVITY mode candidate detection (AC-1) ────────────────────────────

    @Test
    void processWarnPhaseForPolicy_inactivity_createsNoticeAndWarnsSingleWallet() {
        // Policy: 365-day inactivity, 30-day lead time, grace passed
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));

        // Wallet with positive available balance
        RewardWallet wallet = walletWithBalance(new BigDecimal("200.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet));

        // Last activity: 366 days ago → expiry = lastActivity + 365 = 1 day ago
        // Lead window start = expiryDate - 30 = 31 days ago → today is past lead window → should warn
        Instant lastActivity = daysAgo(366);
        when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
                .thenReturn(List.of(lastActivityRow(wallet.getId(), lastActivity)));

        // No existing notice
        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(noticeRepository.save(any(BalanceExpiryNotice.class))).thenAnswer(inv -> {
            BalanceExpiryNotice n = inv.getArgument(0);
            if (n.getId() == null) {
                setId(n, UUID.randomUUID());
            }
            return n;
        });
        // AC-8: delivery confirmed → notice advances to NOTIFIED
        when(notificationEventProducer.publishAndConfirm(any())).thenReturn(true);

        int warned = service.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(1);
        // AC-8: notice is saved twice — SCHEDULED first (no notified_at), then NOTIFIED once the
        // advance warning is confirmed delivered. notified_at is never set before confirmed delivery.
        ArgumentCaptor<BalanceExpiryNotice> noticeCaptor = ArgumentCaptor.forClass(BalanceExpiryNotice.class);
        verify(noticeRepository, times(2)).save(noticeCaptor.capture());
        BalanceExpiryNotice finalNotice = noticeCaptor.getAllValues().get(noticeCaptor.getAllValues().size() - 1);
        assertThat(finalNotice.getStatus()).isEqualTo(ExpiryNoticeStatus.NOTIFIED);
        assertThat(finalNotice.getNotifiedAt()).isNotNull();

        // AC-8: warning is published via publishAndConfirm (delivery-gated), not fire-and-forget publish
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publishAndConfirm(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.notificationTypeKey()).isEqualTo("BALANCE_EXPIRING_SOON");
        assertThat(event.clientId()).isEqualTo(CLIENT_ID);
        assertThat(event.metadata()).containsKey("currencyId");
        assertThat(event.metadata()).containsKey("amount");
        assertThat(event.metadata()).containsKey("scheduledExpiryDate");
        assertThat(event.metadata()).containsKey("walletId");
        // No PII — no user name, email, etc.
        assertThat(event.metadata()).doesNotContainKey("userEmail");
    }

    // ── FIXED_DATE mode candidate detection (AC-1) ────────────────────────────

    @Test
    void processWarnPhaseForPolicy_fixedDate_warnsWhenInLeadWindow() {
        // Fixed expiry = 5 days from now, lead time = 30 → already in lead window
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).plusDays(5);
        BalanceExpirationPolicy policy = fixedDatePolicy(fixedDate, 30, daysAgo(60));

        RewardWallet wallet = walletWithBalance(new BigDecimal("500.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet));
        // FIXED_DATE mode does not consult last-activity → no bulk query stub needed.

        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(noticeRepository.save(any())).thenAnswer(inv -> {
            BalanceExpiryNotice n = inv.getArgument(0);
            if (n.getId() == null) setId(n, UUID.randomUUID());
            return n;
        });
        when(notificationEventProducer.publishAndConfirm(any())).thenReturn(true);

        int warned = service.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(1);
        verify(notificationEventProducer).publishAndConfirm(any());
    }

    // ── Grace-window skip (AC-3) ──────────────────────────────────────────────

    @Test
    void processWarnPhaseForPolicy_graceWindowNotPassed_skipsAllWallets() {
        // enabled_at = today (0 days ago) → grace not passed (must wait leadTimeDays)
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, Instant.now());

        int warned = service.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(0);
        verify(schedulerRepo, never()).findExpiryCandidateWallets(any(), any(), any());
        verify(noticeRepository, never()).save(any());
    }

    @Test
    void processWarnPhaseForPolicy_graceWindowPassed_exactlyOnLeadTimeBoundary() {
        // enabled_at = exactly leadTimeDays days ago → grace is just passed
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(30));

        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet));

        // Last activity: 396 days ago → expiry = today → lead window start = 30 days ago → in window
        Instant lastActivity = daysAgo(396);
        when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
                .thenReturn(List.of(lastActivityRow(wallet.getId(), lastActivity)));
        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(noticeRepository.save(any())).thenAnswer(inv -> {
            BalanceExpiryNotice n = inv.getArgument(0);
            if (n.getId() == null) setId(n, UUID.randomUUID());
            return n;
        });
        when(notificationEventProducer.publishAndConfirm(any())).thenReturn(true);

        int warned = service.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(1);
    }

    // ── Once-only / no duplicate notice on re-run (AC-2, FR-09.4) ────────────

    @Test
    void processWarnPhaseForPolicy_alreadyNotified_skipsWithoutDuplicateNotice() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        UUID policyId = policy.getId();

        RewardWallet wallet = walletWithBalance(new BigDecimal("300.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet));

        Instant lastActivity = daysAgo(366);
        when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
                .thenReturn(List.of(lastActivityRow(wallet.getId(), lastActivity)));

        // Existing notice already has notified_at set — idempotent skip
        LocalDate expiryDate = lastActivity.atZone(ZoneOffset.UTC).toLocalDate().plusDays(365);
        BalanceExpiryNotice alreadyNotified = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, wallet.getId(), "points", policyId)
                .scheduledExpiryDate(expiryDate)
                .build();
        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                any(), any(), any(), any())).thenReturn(Optional.of(alreadyNotified));

        int warned = service.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(0);
        verify(noticeRepository, never()).save(any());
        verify(notificationEventProducer, never()).publishAndConfirm(any());
    }

    // ── Cash/disabled policy skip (AC-4) ─────────────────────────────────────

    @Test
    void runExpirySweep_noEnabledPolicies_doesNothing() {
        when(schedulerRepo.findAllByEnabledTrueAndDeletedFalse()).thenReturn(List.of());

        service.runExpirySweep();

        verify(schedulerRepo, never()).findExpiryCandidateWallets(any(), any(), any());
        verify(noticeRepository, never()).save(any());
    }

    // ── Zero/non-positive available balance skip (AC-5) ──────────────────────

    @Test
    void processWarnPhaseForPolicy_zeroBalance_notIncludedInCandidates() {
        // The repository query (availableBalance > 0) excludes zero-balance wallets at DB level.
        // This test confirms that when candidates list is empty, no notice is created.
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of());

        int warned = service.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(0);
        verify(noticeRepository, never()).save(any());
    }

    // ── Producer fires exactly once per new notice (BE-2) ─────────────────────

    @Test
    void processWarnPhaseForPolicy_producerFiredOnce_perNewNotice() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));

        // Two wallets, both new candidates
        RewardWallet wallet1 = walletWithBalance(new BigDecimal("100.00"));
        RewardWallet wallet2 = walletWithBalance(new BigDecimal("200.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet1, wallet2));

        Instant lastActivity = daysAgo(366);
        when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
                .thenReturn(List.of(
                        lastActivityRow(wallet1.getId(), lastActivity),
                        lastActivityRow(wallet2.getId(), lastActivity)));
        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(noticeRepository.save(any())).thenAnswer(inv -> {
            BalanceExpiryNotice n = inv.getArgument(0);
            if (n.getId() == null) setId(n, UUID.randomUUID());
            return n;
        });
        when(notificationEventProducer.publishAndConfirm(any())).thenReturn(true);

        int warned = service.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(2);
        // Exactly 2 BALANCE_EXPIRING_SOON events (one per wallet), each delivery-confirmed
        verify(notificationEventProducer, times(2)).publishAndConfirm(any());
    }

    @Test
    void processWarnPhaseForPolicy_producerNotFired_whenNotifiedAtAlreadySet() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        UUID policyId = policy.getId();

        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet));

        Instant lastActivity = daysAgo(366);
        when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
                .thenReturn(List.of(lastActivityRow(wallet.getId(), lastActivity)));

        LocalDate expiryDate = lastActivity.atZone(ZoneOffset.UTC).toLocalDate().plusDays(365);
        BalanceExpiryNotice alreadyNotified = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, wallet.getId(), "points", policyId)
                .scheduledExpiryDate(expiryDate)
                .build();
        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                any(), any(), any(), any())).thenReturn(Optional.of(alreadyNotified));

        service.processWarnPhaseForPolicy(policy);

        // Producer must NOT be called
        verify(notificationEventProducer, never()).publishAndConfirm(any());
    }

    // ── AC-8: advance-warning delivery gating (FR-09.7) ───────────────────────

    @Test
    void processWarnForWallet_deliveryUnconfirmed_leavesNoticeScheduledAndNotExpirable() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));

        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet));
        when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
                .thenReturn(List.of(lastActivityRow(wallet.getId(), daysAgo(366))));
        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(noticeRepository.save(any())).thenAnswer(inv -> {
            BalanceExpiryNotice n = inv.getArgument(0);
            if (n.getId() == null) setId(n, UUID.randomUUID());
            return n;
        });
        // Broker did NOT confirm the advance warning
        when(notificationEventProducer.publishAndConfirm(any())).thenReturn(false);

        int warned = service.processWarnPhaseForPolicy(policy);

        // Not counted as warned — the partner was not actually warned
        assertThat(warned).isEqualTo(0);

        // The notice persists as SCHEDULED with NO notified_at, so the expire phase (which only acts
        // on NOTIFIED) can never expire this un-warned balance (AC-8, FR-09.7). Only the SCHEDULED
        // save happens — no advance to NOTIFIED.
        ArgumentCaptor<BalanceExpiryNotice> noticeCaptor = ArgumentCaptor.forClass(BalanceExpiryNotice.class);
        verify(noticeRepository).save(noticeCaptor.capture());
        BalanceExpiryNotice persisted = noticeCaptor.getValue();
        assertThat(persisted.getStatus()).isEqualTo(ExpiryNoticeStatus.SCHEDULED);
        assertThat(persisted.getNotifiedAt()).isNull();
    }

    @Test
    void processWarnForWallet_existingScheduledNotice_retriesAndAdvancesOnConfirmedDelivery() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        UUID policyId = policy.getId();

        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet));
        Instant lastActivity = daysAgo(366);
        when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
                .thenReturn(List.of(lastActivityRow(wallet.getId(), lastActivity)));

        // Prior-sweep SCHEDULED notice whose advance warning never delivered (notified_at null)
        LocalDate expiryDate = lastActivity.atZone(ZoneOffset.UTC).toLocalDate().plusDays(365);
        BalanceExpiryNotice scheduled = BalanceExpiryNotice.builder()
                .clientId(CLIENT_ID).walletId(wallet.getId()).currencyId("points")
                .policyId(policyId).scheduledExpiryDate(expiryDate)
                .status(ExpiryNoticeStatus.SCHEDULED).build();
        setId(scheduled, UUID.randomUUID());
        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(
                any(), any(), any(), any())).thenReturn(Optional.of(scheduled));
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationEventProducer.publishAndConfirm(any())).thenReturn(true);

        int warned = service.processWarnPhaseForPolicy(policy);

        assertThat(warned).isEqualTo(1);
        // Existing notice already had an id → no SCHEDULED re-save; one save to flip to NOTIFIED
        ArgumentCaptor<BalanceExpiryNotice> noticeCaptor = ArgumentCaptor.forClass(BalanceExpiryNotice.class);
        verify(noticeRepository).save(noticeCaptor.capture());
        assertThat(noticeCaptor.getValue().getStatus()).isEqualTo(ExpiryNoticeStatus.NOTIFIED);
        assertThat(noticeCaptor.getValue().getNotifiedAt()).isNotNull();
    }

    // ── computeScheduledExpiryDate (last-activity pre-fetched by caller — AC-9) ─

    @Test
    void computeScheduledExpiryDate_inactivity_notInLeadWindowYet_returnsNull() {
        // inactivityDays=365, leadTime=30, lastActivity=100 days ago
        // expiryDate = today + 265 days, leadWindowStart = today + 235 days — NOT yet in window
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        Instant lastActivity = daysAgo(100);

        LocalDate result = service.computeScheduledExpiryDate(policy, LocalDate.now(ZoneOffset.UTC), lastActivity);

        assertThat(result).isNull();
    }

    @Test
    void computeScheduledExpiryDate_inactivity_inLeadWindow_returnsExpiryDate() {
        // inactivityDays=365, leadTime=30, lastActivity=350 days ago
        // expiryDate = today + 15, leadWindowStart = today - 15 → in window
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        Instant lastActivity = daysAgo(350);

        LocalDate result = service.computeScheduledExpiryDate(policy, LocalDate.now(ZoneOffset.UTC), lastActivity);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(lastActivity.atZone(ZoneOffset.UTC).toLocalDate().plusDays(365));
    }

    @Test
    void computeScheduledExpiryDate_fixedDate_notInLeadWindowYet_returnsNull() {
        // Fixed date = 60 days from now, lead time = 30 → not yet in window
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).plusDays(60);
        BalanceExpirationPolicy policy = fixedDatePolicy(fixedDate, 30, daysAgo(60));

        // FIXED_DATE ignores last-activity → pass null
        LocalDate result = service.computeScheduledExpiryDate(policy, LocalDate.now(ZoneOffset.UTC), null);

        assertThat(result).isNull();
    }

    @Test
    void computeScheduledExpiryDate_fixedDate_inLeadWindow_returnsFixedDate() {
        // Fixed date = 10 days from now, lead time = 30 → in window (since today >= fixedDate - 30)
        LocalDate fixedDate = LocalDate.now(ZoneOffset.UTC).plusDays(10);
        BalanceExpirationPolicy policy = fixedDatePolicy(fixedDate, 30, daysAgo(60));

        LocalDate result = service.computeScheduledExpiryDate(policy, LocalDate.now(ZoneOffset.UTC), null);

        assertThat(result).isEqualTo(fixedDate);
    }

    @Test
    void computeScheduledExpiryDate_inactivity_noLastActivity_returnsNull() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));

        // INACTIVITY mode with no recorded activity → cannot compute expiry
        LocalDate result = service.computeScheduledExpiryDate(policy, LocalDate.now(ZoneOffset.UTC), null);

        assertThat(result).isNull();
    }

    // ── Grace window helper unit tests ─────────────────────────────────────────

    @Test
    void isGraceWindowPassed_enabledAtNull_returnsFalse() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, null);

        assertThat(service.isGraceWindowPassed(policy, LocalDate.now(ZoneOffset.UTC))).isFalse();
    }

    @Test
    void isGraceWindowPassed_enabledAtToday_returnsFalse() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, Instant.now());

        assertThat(service.isGraceWindowPassed(policy, LocalDate.now(ZoneOffset.UTC))).isFalse();
    }

    @Test
    void isGraceWindowPassed_enabledAtExactlyLeadTimeDaysAgo_returnsTrue() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(30));

        assertThat(service.isGraceWindowPassed(policy, LocalDate.now(ZoneOffset.UTC))).isTrue();
    }

    // ── Expire phase: happy path (AC-1) ──────────────────────────────────────

    @Test
    void processExpireForNotice_happyPath_debitsWalletAndMarksExpired() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));

        RewardWallet wallet = walletWithBalance(new BigDecimal("150.00"));
        wallet.setReservedBalance(new BigDecimal("20.00")); // reserved — must NOT be touched
        UUID noticeId = UUID.randomUUID();
        BalanceExpiryNotice notice = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, wallet.getId(), "points", policy.getId())
                .scheduledExpiryDate(LocalDate.now(ZoneOffset.UTC).minusDays(1))
                .build();
        setId(notice, noticeId);

        // No existing EXPIRY ledger entry
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                eq(wallet.getId()), eq("BALANCE_EXPIRY_NOTICE"), eq(noticeId), eq(LedgerEntryType.EXPIRY)))
                .thenReturn(false);
        when(schedulerRepo.lockWallet(eq(wallet.getId()), eq(CLIENT_ID)))
                .thenReturn(Optional.of(wallet));
        // Revalidation: still inactive (last activity 366d ago → freshExpiry = yesterday = scheduled)
        when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
                .thenReturn(daysAgo(366));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> {
            LedgerEntry e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });
        when(noticeRepository.save(any(BalanceExpiryNotice.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = service.processExpireForNotice(notice, policy);

        assertThat(result).isTrue();

        // Ledger entry saved with correct fields
        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        LedgerEntry savedEntry = ledgerCaptor.getValue();
        assertThat(savedEntry.getEntryType()).isEqualTo(LedgerEntryType.EXPIRY);
        assertThat(savedEntry.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(savedEntry.getReferenceType()).isEqualTo("BALANCE_EXPIRY_NOTICE");
        assertThat(savedEntry.getReferenceId()).isEqualTo(noticeId);
        // Reserved balance unchanged (ADR #3, AC-3)
        assertThat(savedEntry.getReservedBalanceBefore()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(savedEntry.getReservedBalanceAfter()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(savedEntry.getAvailableBalanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);

        // Notice marked EXPIRED
        ArgumentCaptor<BalanceExpiryNotice> noticeCaptor = ArgumentCaptor.forClass(BalanceExpiryNotice.class);
        verify(noticeRepository).save(noticeCaptor.capture());
        BalanceExpiryNotice savedNotice = noticeCaptor.getValue();
        assertThat(savedNotice.getStatus()).isEqualTo(ExpiryNoticeStatus.EXPIRED);
        assertThat(savedNotice.getExpiredAt()).isNotNull();
        assertThat(savedNotice.getExpiredAmount()).isEqualByComparingTo(new BigDecimal("150.00"));

        // Wallet availableBalance reduced to zero
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        // Reserved balance NOT changed
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo(new BigDecimal("20.00"));

        // BALANCE_EXPIRED event emitted (expire phase uses fire-and-forget publish, afterCommit/direct)
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().notificationTypeKey()).isEqualTo("BALANCE_EXPIRED");
        assertThat(eventCaptor.getValue().metadata()).containsKey("expiredAmount");
        assertThat(eventCaptor.getValue().metadata()).doesNotContainKey("userEmail");
    }

    // ── Expire phase: idempotent re-run — no double debit (AC-2) ─────────────

    @Test
    void processExpireForNotice_alreadyExpired_idempotentSkip() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        UUID noticeId = UUID.randomUUID();
        BalanceExpiryNotice notice = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, wallet.getId(), "points", policy.getId())
                .scheduledExpiryDate(LocalDate.now(ZoneOffset.UTC).minusDays(1))
                .build();
        setId(notice, noticeId);

        // Lock-first ordering: lockWallet is called BEFORE the idempotency check so the row
        // is held while we verify. The idempotency guard then fires and returns false.
        when(schedulerRepo.lockWallet(eq(wallet.getId()), eq(CLIENT_ID)))
                .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                eq(wallet.getId()), eq("BALANCE_EXPIRY_NOTICE"), eq(noticeId), eq(LedgerEntryType.EXPIRY)))
                .thenReturn(true);

        boolean result = service.processExpireForNotice(notice, policy);

        assertThat(result).isFalse();
        // lockWallet IS called (lock-first pattern) — row lock is acquired before idempotency check
        verify(schedulerRepo).lockWallet(eq(wallet.getId()), eq(CLIENT_ID));
        verify(ledgerEntryRepository, never()).save(any());
        verify(noticeRepository, never()).save(any());
        verify(notificationEventProducer, never()).publish(any());
    }

    // ── Expire phase: reserved balance protected — only availableBalance expired (AC-3) ──

    @Test
    void processExpireForNotice_reservedBalanceProtected_onlyAvailableExpired() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        RewardWallet wallet = walletWithBalance(new BigDecimal("200.00"));
        wallet.setReservedBalance(new BigDecimal("50.00"));
        UUID noticeId = UUID.randomUUID();
        BalanceExpiryNotice notice = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, wallet.getId(), "points", policy.getId())
                .scheduledExpiryDate(LocalDate.now(ZoneOffset.UTC).minusDays(1))
                .build();
        setId(notice, noticeId);

        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                any(), any(), any(), any())).thenReturn(false);
        when(schedulerRepo.lockWallet(eq(wallet.getId()), eq(CLIENT_ID)))
                .thenReturn(Optional.of(wallet));
        // Revalidation: still inactive (last activity 366d ago → freshExpiry = yesterday = scheduled)
        when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
                .thenReturn(daysAgo(366));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> {
            LedgerEntry e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processExpireForNotice(notice, policy);

        // Reserved balance must NOT change
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo(new BigDecimal("50.00"));
        // Ledger entry amount = availableBalance only
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(captor.getValue().getReservedBalanceBefore()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(captor.getValue().getReservedBalanceAfter()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ── Expire phase: policy disabled after warn → skip (AC-4) ───────────────

    @Test
    void processExpirePhaseForPolicy_policyDisabledAfterWarn_skipsAllNotices() {
        // Policy is disabled (enabled=false)
        BalanceExpirationPolicy disabledPolicy = BalanceExpirationPolicyFixtures
                .disabledPolicy(CLIENT_ID, "points")
                .build();
        setId(disabledPolicy, UUID.randomUUID());

        int result = service.processExpirePhaseForPolicy(disabledPolicy);

        assertThat(result).isEqualTo(0);
        verify(noticeRepository, never()).findByClientIdAndStatusAndScheduledExpiryDateLessThanEqual(
                any(), any(), any());
    }

    // ── Expire phase: partial balance after concurrent reservation ────────────

    @Test
    void processExpireForNotice_partialBalance_expiresOnlyRemainingAvailable() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        // Wallet has only 30.00 available (reservation reduced it from original 100)
        RewardWallet wallet = walletWithBalance(new BigDecimal("30.00"));
        wallet.setReservedBalance(new BigDecimal("70.00")); // 70 was reserved concurrently
        UUID noticeId = UUID.randomUUID();
        BalanceExpiryNotice notice = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, wallet.getId(), "points", policy.getId())
                .scheduledExpiryDate(LocalDate.now(ZoneOffset.UTC).minusDays(1))
                .build();
        setId(notice, noticeId);

        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                any(), any(), any(), any())).thenReturn(false);
        when(schedulerRepo.lockWallet(eq(wallet.getId()), eq(CLIENT_ID)))
                .thenReturn(Optional.of(wallet));
        // Revalidation: still inactive (last activity 366d ago → freshExpiry = yesterday = scheduled)
        when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
                .thenReturn(daysAgo(366));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> {
            LedgerEntry e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = service.processExpireForNotice(notice, policy);

        assertThat(result).isTrue();
        // Only 30.00 actually expired
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    // ── Expire phase: zero available balance — no expiry (Edge Case 8) ────────

    @Test
    void processExpireForNotice_zeroAvailableBalance_skipsExpiry() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        RewardWallet wallet = walletWithBalance(BigDecimal.ZERO);
        UUID noticeId = UUID.randomUUID();
        BalanceExpiryNotice notice = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, wallet.getId(), "points", policy.getId())
                .scheduledExpiryDate(LocalDate.now(ZoneOffset.UTC).minusDays(1))
                .build();
        setId(notice, noticeId);

        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                any(), any(), any(), any())).thenReturn(false);
        when(schedulerRepo.lockWallet(eq(wallet.getId()), eq(CLIENT_ID)))
                .thenReturn(Optional.of(wallet));

        boolean result = service.processExpireForNotice(notice, policy);

        assertThat(result).isFalse();
        verify(ledgerEntryRepository, never()).save(any());
        verify(noticeRepository, never()).save(any());
        verify(notificationEventProducer, never()).publish(any());
    }

    // ── Recipient resolver: unresolved → skip notification (Finding 1) ──────

    @Test
    void processWarnForWallet_unresolvedRecipients_doesNotPublishAndLeavesScheduled() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(60));
        RewardWallet wallet = walletWithBalance(new BigDecimal("200.00"));
        when(schedulerRepo.findExpiryCandidateWallets(eq(CLIENT_ID), eq("points"), any(Pageable.class)))
                .thenReturn(List.of(wallet));
        when(schedulerRepo.findLastActivityForWallets(eq(CLIENT_ID), eq("points"), anyCollection(), anyCollection()))
                .thenReturn(List.of(activity(wallet.getId(), daysAgo(366))));
        when(noticeRepository.findByClientIdAndWalletIdAndCurrencyIdAndScheduledExpiryDate(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(noticeRepository.save(any(BalanceExpiryNotice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recipientResolver.resolve(any(RewardWallet.class))).thenReturn(List.of()); // unresolved

        service.processWarnPhaseForPolicy(policy);

        verify(notificationEventProducer, never()).publishAndConfirm(any());
        assertThat(meterRegistry.counter("balance_expiry.recipients_unresolved.total", "currencyId", "points").count())
                .isEqualTo(1.0);

        // FR-09.7: an un-warnable notice must stay SCHEDULED (never advanced to NOTIFIED),
        // so the expire phase — which only acts on NOTIFIED notices — can never expire it.
        ArgumentCaptor<BalanceExpiryNotice> noticeCaptor = ArgumentCaptor.forClass(BalanceExpiryNotice.class);
        verify(noticeRepository).save(noticeCaptor.capture());
        assertThat(noticeCaptor.getValue().getStatus()).isEqualTo(ExpiryNoticeStatus.SCHEDULED);
        assertThat(noticeCaptor.getValue().getNotifiedAt()).isNull();
    }

    @Test
    void processExpireForNotice_unresolvedRecipients_stillExpiresButSkipsNotification() {
        BalanceExpirationPolicy policy = fixedDatePolicy(daysAgo(1), 30); // FIXED_DATE → no revalidation
        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        BalanceExpiryNotice notice = notifiedNotice(wallet.getId(), LocalDate.now(ZoneOffset.UTC).minusDays(1));
        when(schedulerRepo.lockWallet(wallet.getId(), CLIENT_ID)).thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                any(), any(), any(), any())).thenReturn(false);
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recipientResolver.resolve(any(RewardWallet.class))).thenReturn(List.of()); // unresolved

        boolean expired = service.processExpireForNotice(notice, policy);

        assertThat(expired).isTrue();                       // debit still happens
        verify(ledgerEntryRepository).save(any(LedgerEntry.class));
        verify(notificationEventProducer, never()).publish(any()); // notification skipped
    }

    // ── Expire phase: inactivity revalidation (Finding 2 fix) ────────────────

    @Test
    void processExpireForNotice_inactivity_postWarningActivity_cancelsNoticeAndSkipsDebit() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(400));
        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        LocalDate scheduled = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        BalanceExpiryNotice notice = notifiedNotice(wallet.getId(), scheduled);
        when(schedulerRepo.lockWallet(wallet.getId(), CLIENT_ID)).thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                any(), any(), any(), any())).thenReturn(false);
        // Fresh activity 5 days ago → freshExpiry = today+360, which != scheduled (yesterday) → stale
        when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
                .thenReturn(daysAgo(5));

        boolean expired = service.processExpireForNotice(notice, policy);

        assertThat(expired).isFalse();
        assertThat(notice.getStatus()).isEqualTo(ExpiryNoticeStatus.CANCELLED);
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("100.00"); // untouched
    }

    @Test
    void processExpireForNotice_inactivity_stillInactive_expires() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(400));
        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        // scheduled = lastActivity(366d ago) + 365 = yesterday
        LocalDate scheduled = daysAgo(366).atZone(ZoneOffset.UTC).toLocalDate().plusDays(365);
        BalanceExpiryNotice notice = notifiedNotice(wallet.getId(), scheduled);
        when(schedulerRepo.lockWallet(wallet.getId(), CLIENT_ID)).thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                any(), any(), any(), any())).thenReturn(false);
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> {
            LedgerEntry e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });
        when(noticeRepository.save(any(BalanceExpiryNotice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
                .thenReturn(daysAgo(366)); // unchanged → freshExpiry == scheduled

        boolean expired = service.processExpireForNotice(notice, policy);

        assertThat(expired).isTrue();
        verify(ledgerEntryRepository).save(any(LedgerEntry.class));
    }

    @Test
    void processExpireForNotice_inactivity_noLiveActivity_skipsDebit() {
        BalanceExpirationPolicy policy = inactivityPolicy(365, 30, daysAgo(400));
        RewardWallet wallet = walletWithBalance(new BigDecimal("100.00"));
        BalanceExpiryNotice notice = notifiedNotice(wallet.getId(), LocalDate.now(ZoneOffset.UTC).minusDays(1));
        when(schedulerRepo.lockWallet(wallet.getId(), CLIENT_ID)).thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.existsByRewardWalletIdAndReferenceTypeAndReferenceIdAndEntryType(
                any(), any(), any(), any())).thenReturn(false);
        when(schedulerRepo.findLastActivityAt(eq(CLIENT_ID), eq("points"), eq(wallet.getId()), anyCollection()))
                .thenReturn(null);

        boolean expired = service.processExpireForNotice(notice, policy);

        assertThat(expired).isFalse();
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BalanceExpirationPolicy inactivityPolicy(int inactivityDays, int leadTimeDays, Instant enabledAt) {
        BalanceExpirationPolicy p = BalanceExpirationPolicyFixtures
                .inactivityPolicy(CLIENT_ID, "points")
                .inactivityDays(inactivityDays)
                .leadTimeDays(leadTimeDays)
                .enabledAt(enabledAt)
                .build();
        setId(p, UUID.randomUUID());
        return p;
    }

    private BalanceExpirationPolicy fixedDatePolicy(LocalDate fixedDate, int leadTimeDays, Instant enabledAt) {
        BalanceExpirationPolicy p = BalanceExpirationPolicyFixtures
                .fixedDatePolicy(CLIENT_ID, "points")
                .fixedExpiryDate(fixedDate)
                .leadTimeDays(leadTimeDays)
                .enabledAt(enabledAt)
                .build();
        setId(p, UUID.randomUUID());
        return p;
    }

    private RewardWallet walletWithBalance(BigDecimal balance) {
        RewardWallet w = RewardWalletFixtures
                .individualWalletWithBalance(CLIENT_ID, USER_ID, balance)
                .currencyId("points")
                .walletType(WalletType.INDIVIDUAL)
                .userId(USER_ID)
                .build();
        setId(w, UUID.randomUUID());
        return w;
    }

    private Instant daysAgo(int days) {
        return Instant.now().minusSeconds((long) days * 86400L);
    }

    /** Builds a bulk last-activity projection row (AC-9 query result). */
    private static WalletLastActivityProjection lastActivityRow(UUID walletId, Instant lastActivityAt) {
        return new WalletLastActivityProjection() {
            @Override public UUID getWalletId() { return walletId; }
            @Override public Instant getLastActivityAt() { return lastActivityAt; }
        };
    }

    /**
     * Reflectively sets the {@code id} field on a {@link com.tenxengage.app.entity.BaseEntity}
     * to simulate JPA-generated IDs in unit tests.
     */
    private static void setId(Object entity, UUID id) {
        try {
            java.lang.reflect.Field idField = com.tenxengage.app.entity.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id via reflection in test", e);
        }
    }

    /** Alias for {@link #lastActivityRow} — used in new unresolved-recipient tests. */
    private static WalletLastActivityProjection activity(UUID walletId, Instant lastActivityAt) {
        return lastActivityRow(walletId, lastActivityAt);
    }

    /**
     * FIXED_DATE policy with the given expiry instant (converted to LocalDate) and lead time.
     * Grace window is set to 60 days ago so it is always passed.
     */
    private BalanceExpirationPolicy fixedDatePolicy(Instant fixedExpiryInstant, int leadTimeDays) {
        LocalDate fixedDate = fixedExpiryInstant.atZone(ZoneOffset.UTC).toLocalDate();
        return fixedDatePolicy(fixedDate, leadTimeDays, daysAgo(60));
    }

    /**
     * Builds a NOTIFIED notice for the given wallet + scheduled expiry date.
     * CLIENT_ID and policyId are inferred from test-level constants / a new UUID.
     */
    private BalanceExpiryNotice notifiedNotice(UUID walletId, LocalDate scheduledExpiryDate) {
        BalanceExpiryNotice notice = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, walletId, "points", UUID.randomUUID())
                .scheduledExpiryDate(scheduledExpiryDate)
                .build();
        setId(notice, UUID.randomUUID());
        return notice;
    }
}
