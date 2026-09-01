package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.BalanceExpiryNotice;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.ExpiryNoticeStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.BalanceExpiryNoticeRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.testdata.BalanceExpiryNoticeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BalanceExpiryNoticeService#cancelPendingForPolicy}.
 *
 * <p>In unit tests {@code TransactionSynchronizationManager.isSynchronizationActive()} is
 * {@code false}, so the service falls through to the direct-publish branch — same pattern as
 * {@link BalanceExpiryBatchServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class BalanceExpiryNoticeServiceTest {

    @Mock private BalanceExpiryNoticeRepository noticeRepository;
    @Mock private NotificationEventProducer notificationEventProducer;
    @Mock private AuditLogService auditLogService;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private WalletNotificationRecipientResolver recipientResolver;

    private BalanceExpiryNoticeService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID POLICY_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BalanceExpiryNoticeService(
                noticeRepository, notificationEventProducer, auditLogService,
                walletRepository, recipientResolver);
    }

    // ── No-op when none pending ───────────────────────────────────────────────

    @Test
    void cancelPendingForPolicy_noPending_returnsZeroAndNoEvents() {
        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(
                eq(CLIENT_ID), eq(POLICY_ID), any())).thenReturn(List.of());

        int result = service.cancelPendingForPolicy(POLICY_ID, CLIENT_ID);

        assertThat(result).isEqualTo(0);
        verifyNoInteractions(notificationEventProducer);
    }

    // ── Cancels SCHEDULED notices — no event emitted ─────────────────────────

    @Test
    void cancelPendingForPolicy_scheduledOnly_cancelsWithoutEvent() {
        BalanceExpiryNotice scheduledNotice = BalanceExpiryNoticeFixtures
                .scheduledNotice(CLIENT_ID, WALLET_ID, "points", POLICY_ID)
                .scheduledExpiryDate(LocalDate.now().plusDays(30))
                .build();
        setId(scheduledNotice, UUID.randomUUID());

        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(
                eq(CLIENT_ID), eq(POLICY_ID), any())).thenReturn(List.of(scheduledNotice));
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int result = service.cancelPendingForPolicy(POLICY_ID, CLIENT_ID);

        assertThat(result).isEqualTo(1);
        // SCHEDULED → no BALANCE_EXPIRY_CANCELLED event
        verifyNoInteractions(notificationEventProducer);

        // Notice must be saved with CANCELLED status
        ArgumentCaptor<BalanceExpiryNotice> captor = ArgumentCaptor.forClass(BalanceExpiryNotice.class);
        verify(noticeRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ExpiryNoticeStatus.CANCELLED);
        assertThat(captor.getValue().getCancelledAt()).isNotNull();
    }

    // ── Cancels NOTIFIED notices and emits BALANCE_EXPIRY_CANCELLED ──────────

    @Test
    void cancelPendingForPolicy_notifiedOnly_cancelsAndEmitsCancellationEvent() {
        UUID ownerId = UUID.randomUUID();
        BalanceExpiryNotice notifiedNotice = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, WALLET_ID, "points", POLICY_ID)
                .scheduledExpiryDate(LocalDate.now().plusDays(5))
                .build();
        setId(notifiedNotice, UUID.randomUUID());
        RewardWallet wallet = RewardWallet.builder().clientId(CLIENT_ID)
                .walletType(WalletType.INDIVIDUAL).userId(ownerId).build();

        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(
                eq(CLIENT_ID), eq(POLICY_ID), any())).thenReturn(List.of(notifiedNotice));
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(recipientResolver.resolve(wallet)).thenReturn(List.of(ownerId));

        int result = service.cancelPendingForPolicy(POLICY_ID, CLIENT_ID);

        assertThat(result).isEqualTo(1);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.notificationTypeKey()).isEqualTo("BALANCE_EXPIRY_CANCELLED");
        assertThat(event.clientId()).isEqualTo(CLIENT_ID);
        assertThat(event.metadata()).containsKey("currencyId");
        assertThat(event.metadata()).containsKey("scheduledExpiryDate");
        assertThat(event.metadata()).containsKey("walletId");
        // No PII
        assertThat(event.metadata()).doesNotContainKey("userEmail");
    }

    // ── Mixed SCHEDULED + NOTIFIED: cancels both, emits only for NOTIFIED ────

    @Test
    void cancelPendingForPolicy_mixedStatuses_emitsOnlyForNotified() {
        BalanceExpiryNotice scheduled = BalanceExpiryNoticeFixtures
                .scheduledNotice(CLIENT_ID, WALLET_ID, "points", POLICY_ID)
                .scheduledExpiryDate(LocalDate.now().plusDays(30))
                .build();
        setId(scheduled, UUID.randomUUID());

        UUID wallet2 = UUID.randomUUID();
        UUID ownerId2 = UUID.randomUUID();
        BalanceExpiryNotice notified = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, wallet2, "points", POLICY_ID)
                .scheduledExpiryDate(LocalDate.now().plusDays(5))
                .build();
        setId(notified, UUID.randomUUID());
        RewardWallet wallet2Obj = RewardWallet.builder().clientId(CLIENT_ID)
                .walletType(WalletType.INDIVIDUAL).userId(ownerId2).build();

        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(
                eq(CLIENT_ID), eq(POLICY_ID), any())).thenReturn(List.of(scheduled, notified));
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.findById(wallet2)).thenReturn(Optional.of(wallet2Obj));
        when(recipientResolver.resolve(wallet2Obj)).thenReturn(List.of(ownerId2));

        int result = service.cancelPendingForPolicy(POLICY_ID, CLIENT_ID);

        assertThat(result).isEqualTo(2);
        // Exactly 1 event — only for the NOTIFIED notice
        verify(notificationEventProducer, times(1)).publish(any());
        // Both notices saved as CANCELLED
        verify(noticeRepository, times(2)).save(any(BalanceExpiryNotice.class));
    }

    // ── Audit log written ─────────────────────────────────────────────────────

    @Test
    void cancelPendingForPolicy_writesAuditLog() {
        BalanceExpiryNotice scheduled = BalanceExpiryNoticeFixtures
                .scheduledNotice(CLIENT_ID, WALLET_ID, "points", POLICY_ID)
                .scheduledExpiryDate(LocalDate.now().plusDays(30))
                .build();
        setId(scheduled, UUID.randomUUID());

        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(
                eq(CLIENT_ID), eq(POLICY_ID), any())).thenReturn(List.of(scheduled));
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelPendingForPolicy(POLICY_ID, CLIENT_ID);

        // Uses synchronous log() (not logAsync()) because logAsync() is @Async — SecurityContext
        // (ThreadLocal) would be null on the async thread, causing the audit entry to be dropped.
        verify(auditLogService, atLeastOnce()).log(
                eq(AuditAction.CANCELLED),
                eq(AuditResourceType.BALANCE_EXPIRATION_POLICY),
                eq(POLICY_ID),
                any(),
                any(),
                any(Map.class)
        );
    }

    // ── Multiple NOTIFIED notices — one event per notice ─────────────────────

    @Test
    void cancelPendingForPolicy_multipleNotified_emitsOneEventEach() {
        UUID walletId1 = UUID.randomUUID();
        UUID walletId2 = UUID.randomUUID();
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();

        BalanceExpiryNotice n1 = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, walletId1, "points", POLICY_ID)
                .scheduledExpiryDate(LocalDate.now().plusDays(5))
                .build();
        setId(n1, UUID.randomUUID());

        BalanceExpiryNotice n2 = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, walletId2, "points", POLICY_ID)
                .scheduledExpiryDate(LocalDate.now().plusDays(5))
                .build();
        setId(n2, UUID.randomUUID());

        RewardWallet w1 = RewardWallet.builder().clientId(CLIENT_ID)
                .walletType(WalletType.INDIVIDUAL).userId(owner1).build();
        RewardWallet w2 = RewardWallet.builder().clientId(CLIENT_ID)
                .walletType(WalletType.INDIVIDUAL).userId(owner2).build();

        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(
                eq(CLIENT_ID), eq(POLICY_ID), any())).thenReturn(List.of(n1, n2));
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.findById(walletId1)).thenReturn(Optional.of(w1));
        when(walletRepository.findById(walletId2)).thenReturn(Optional.of(w2));
        when(recipientResolver.resolve(w1)).thenReturn(List.of(owner1));
        when(recipientResolver.resolve(w2)).thenReturn(List.of(owner2));

        int result = service.cancelPendingForPolicy(POLICY_ID, CLIENT_ID);

        assertThat(result).isEqualTo(2);
        verify(notificationEventProducer, times(2)).publish(any(NotificationEvent.class));
    }

    // ── Task 3: recipient resolver used in cancellation flow ─────────────────

    @Test
    void cancelPendingForPolicy_notifiedNotice_scopesCancellationToWalletAudience() {
        UUID policyId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BalanceExpiryNotice notified = notifiedCancellableNotice(walletId);
        RewardWallet wallet = RewardWallet.builder().clientId(CLIENT_ID)
                .walletType(WalletType.INDIVIDUAL).userId(ownerId).build();
        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(eq(CLIENT_ID), eq(policyId), anyList()))
                .thenReturn(List.of(notified));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(recipientResolver.resolve(wallet)).thenReturn(List.of(ownerId));

        service.cancelPendingForPolicy(policyId, CLIENT_ID);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertThat(captor.getValue().targetUserIds()).containsExactly(ownerId);
    }

    @Test
    void cancelPendingForPolicy_unresolvedRecipients_doesNotPublish() {
        UUID policyId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        BalanceExpiryNotice notified = notifiedCancellableNotice(walletId);
        RewardWallet wallet = RewardWallet.builder().clientId(CLIENT_ID)
                .walletType(WalletType.COMPANY).partnerCompanyId(UUID.randomUUID()).build();
        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(eq(CLIENT_ID), eq(policyId), anyList()))
                .thenReturn(List.of(notified));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(recipientResolver.resolve(wallet)).thenReturn(List.of());

        service.cancelPendingForPolicy(policyId, CLIENT_ID);

        verify(notificationEventProducer, never()).publish(any());
    }

    @Test
    void cancelPendingForPolicy_missingWallet_doesNotPublish() {
        UUID policyId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        BalanceExpiryNotice notified = notifiedCancellableNotice(walletId);
        when(noticeRepository.findByClientIdAndPolicyIdAndStatusIn(eq(CLIENT_ID), eq(policyId), anyList()))
                .thenReturn(List.of(notified));
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        service.cancelPendingForPolicy(policyId, CLIENT_ID);

        // Wallet row absent — cancellation event must be skipped
        verify(notificationEventProducer, never()).publish(any());
        // The notice itself is still saved as CANCELLED
        ArgumentCaptor<BalanceExpiryNotice> captor = ArgumentCaptor.forClass(BalanceExpiryNotice.class);
        verify(noticeRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ExpiryNoticeStatus.CANCELLED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BalanceExpiryNotice notifiedCancellableNotice(UUID walletId) {
        BalanceExpiryNotice notice = BalanceExpiryNoticeFixtures
                .notifiedNotice(CLIENT_ID, walletId, "points", POLICY_ID)
                .scheduledExpiryDate(LocalDate.now().plusDays(5))
                .build();
        setId(notice, UUID.randomUUID());
        when(noticeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return notice;
    }

    private static void setId(Object entity, UUID id) {
        try {
            java.lang.reflect.Field idField = com.tenxengage.app.entity.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set id via reflection in test", e);
        }
    }
}
