package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.redemption.ApprovalQueueItemResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionRequestType;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.event.RedemptionApprovedEvent;
import com.tenxengage.app.event.RedemptionRejectedEvent;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.RedemptionOrchestrationService;
import com.tenxengage.app.service.WalletService;
import com.tenxengage.app.testdata.RedemptionRequestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionApprovalServiceTest {

    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private TenantValidator tenantValidator;
    @Mock private RedemptionOrchestrationService redemptionOrchestrationService;
    @Mock private WalletService walletService;
    @Mock private NotificationEventProducer notificationEventProducer;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private RedemptionApprovalService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID CATALOG_ITEM_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();
    private static final String REJECTION_REASON = "Duplicate request";

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
    }

    // ── getApprovalQueue tests ──────────────────────────────────────────────

    @Test
    void getApprovalQueue_happyPath_returnsMappedPage() {
        RedemptionRequest request = buildRequestWithAssociations();
        Pageable pageable = PageRequest.of(0, 20);
        Page<RedemptionRequest> repoPage = new PageImpl<>(List.of(request), pageable, 1);

        when(redemptionRequestRepository.findApprovalQueue(
                eq(CLIENT_ID), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(repoPage);

        Page<ApprovalQueueItemResponse> result = service.getApprovalQueue(
                null, null, null, null, RedemptionRequestType.REDEMPTION, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        ApprovalQueueItemResponse item = result.getContent().get(0);
        assertThat(item.requesterDisplayName()).isEqualTo("Jane Doe");
        assertThat(item.catalogItemName()).isEqualTo("Amazon Gift Card");
        assertThat(item.currencyId()).isEqualTo("cash");
        assertThat(item.walletType()).isEqualTo(WalletType.INDIVIDUAL);
    }

    @Test
    void getApprovalQueue_returnType_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);

        Page<ApprovalQueueItemResponse> result = service.getApprovalQueue(
                null, null, null, null, RedemptionRequestType.RETURN, pageable);

        assertThat(result.isEmpty()).isTrue();
        verify(redemptionRequestRepository, never()).findApprovalQueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getApprovalQueue_nullRequestType_treatedAsRedemption() {
        Pageable pageable = PageRequest.of(0, 20);
        when(redemptionRequestRepository.findApprovalQueue(
                eq(CLIENT_ID), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<ApprovalQueueItemResponse> result = service.getApprovalQueue(
                null, null, null, null, null, pageable);

        assertThat(result.isEmpty()).isTrue();
        verify(redemptionRequestRepository).findApprovalQueue(
                eq(CLIENT_ID), isNull(), isNull(), isNull(), isNull(), eq(pageable));
    }

    @Test
    void getApprovalQueue_filtersPassedThrough() {
        String currencyId = "points";
        LocalDate startDate = LocalDate.of(2026, 5, 1);
        LocalDate endDate = LocalDate.of(2026, 5, 31);
        Instant expectedStart = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant expectedEnd = endDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(0, 20);

        when(redemptionRequestRepository.findApprovalQueue(
                eq(CLIENT_ID), eq(currencyId), eq(CATALOG_ITEM_ID),
                eq(expectedStart), eq(expectedEnd), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        service.getApprovalQueue(currencyId, CATALOG_ITEM_ID, startDate, endDate,
                RedemptionRequestType.REDEMPTION, pageable);

        verify(redemptionRequestRepository).findApprovalQueue(
                eq(CLIENT_ID), eq(currencyId), eq(CATALOG_ITEM_ID),
                eq(expectedStart), eq(expectedEnd), eq(pageable));
    }

    // ── approveRedemption tests ─────────────────────────────────────────────

    @Test
    void approveRedemption_happyPath_savesReservedStatusAndReviewFields() {
        UUID redemptionId = UUID.randomUUID();
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .category(RedemptionCategory.CASH)
                .build();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.of(request));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(RedemptionCatalogItem.builder().name("Gift Card").build()));

        RedemptionRequestDetailResponse result = service.approveRedemption(redemptionId, REVIEWER_ID);

        assertThat(result.status()).isEqualTo("RESERVED");
        assertThat(result.reviewedBy()).isEqualTo(REVIEWER_ID);
        assertThat(result.reviewedAt()).isNotNull();
        assertThat(result.rejectionReason()).isNull();
        // dispatch deferred to onRedemptionApproved() post-commit listener — not called in transaction
        verify(redemptionOrchestrationService, never()).dispatch(any());
        verify(redemptionRequestRepository).save(request);
        verify(eventPublisher).publishEvent(any(RedemptionApprovedEvent.class));
    }

    @Test
    void approveRedemption_stateGuard_throwsStateConflictWhenNotPendingApproval() {
        UUID redemptionId = UUID.randomUUID();
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .status(RedemptionStatus.RESERVED)
                .build();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approveRedemption(redemptionId, REVIEWER_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessage("Redemption is not in PENDING_APPROVAL state");

        verify(redemptionOrchestrationService, never()).dispatch(any());
        verify(redemptionRequestRepository, never()).save(any());
    }

    @Test
    void approveRedemption_crossTenant_throwsResourceNotFoundException() {
        UUID redemptionId = UUID.randomUUID();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveRedemption(redemptionId, REVIEWER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(redemptionOrchestrationService, never()).dispatch(any());
    }

    @Test
    void approveRedemption_listener_cashDispatchesAndNotifies() {
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .category(RedemptionCategory.CASH)
                .build();
        RedemptionApprovedEvent event = new RedemptionApprovedEvent(this, request, REVIEWER_ID);

        service.onRedemptionApproved(event);

        verify(redemptionOrchestrationService).dispatch(request);
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        NotificationEvent published = captor.getValue();
        assertThat(published.notificationTypeKey()).isEqualTo("redemption.approved");
        assertThat(published.targetUserIds()).containsExactly(USER_ID);
        assertThat(published.actorUserId()).isEqualTo(REVIEWER_ID);
        assertThat(published.clientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void approveRedemption_listener_dispatchFailure_leavesReservedForReconciliation() {
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .category(RedemptionCategory.CASH)
                .build();
        RedemptionApprovedEvent event = new RedemptionApprovedEvent(this, request, REVIEWER_ID);
        doThrow(new RuntimeException("vendor timeout")).when(redemptionOrchestrationService).dispatch(any());

        service.onRedemptionApproved(event);

        // dispatch attempted
        verify(redemptionOrchestrationService).dispatch(request);
        // wallet NOT released — outcome ambiguous, double-payment risk if we release
        verify(walletService, never()).release(any(), any(), any(), any());
        // request NOT marked FAILED — left in RESERVED for manual reconciliation
        verify(redemptionRequestRepository, never()).save(any());
        // notification still fires
        verify(notificationEventProducer).publish(any(NotificationEvent.class));
    }

    @Test
    void approveRedemption_nonCash_throwsStateConflict() {
        UUID redemptionId = UUID.randomUUID();
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .category(RedemptionCategory.NON_CASH)
                .build();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approveRedemption(redemptionId, REVIEWER_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("NON_CASH redemption approval is not yet supported");

        verify(redemptionOrchestrationService, never()).dispatch(any());
        verify(redemptionRequestRepository, never()).save(any());
    }

    @Test
    void approveRedemption_transactionSucceeds_dispatchNotCalledInTransaction() {
        UUID redemptionId = UUID.randomUUID();
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .category(RedemptionCategory.CASH)
                .build();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.of(request));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(RedemptionCatalogItem.builder().name("Gift Card").build()));

        service.approveRedemption(redemptionId, REVIEWER_ID);

        // dispatch happens post-commit in the listener — never called within the transaction
        verify(redemptionOrchestrationService, never()).dispatch(any());
        verify(redemptionRequestRepository).save(any());
    }

    // ── rejectRedemption tests ──────────────────────────────────────────────

    @Test
    void rejectRedemption_happyPath_savesCancelledStatusAndRejectionFields() {
        UUID redemptionId = UUID.randomUUID();
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .build();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.of(request));
        when(redemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(RedemptionCatalogItem.builder().name("Gift Card").build()));

        RedemptionRequestDetailResponse result = service.rejectRedemption(redemptionId, REJECTION_REASON, REVIEWER_ID);

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.rejectionReason()).isEqualTo(REJECTION_REASON);
        assertThat(result.reviewedBy()).isEqualTo(REVIEWER_ID);
        assertThat(result.reviewedAt()).isNotNull();
        verify(walletService).releaseReservedBalance(request);
        verify(redemptionRequestRepository).save(request);
        verify(eventPublisher).publishEvent(any(RedemptionRejectedEvent.class));
    }

    @Test
    void rejectRedemption_stateGuard_throwsStateConflictWhenNotPendingApproval() {
        UUID redemptionId = UUID.randomUUID();
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .status(RedemptionStatus.CANCELLED)
                .build();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.rejectRedemption(redemptionId, REJECTION_REASON, REVIEWER_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessage("Redemption is not in PENDING_APPROVAL state");

        verify(walletService, never()).releaseReservedBalance(any());
        verify(redemptionRequestRepository, never()).save(any());
    }

    @Test
    void rejectRedemption_crossTenant_throwsResourceNotFoundException() {
        UUID redemptionId = UUID.randomUUID();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rejectRedemption(redemptionId, REJECTION_REASON, REVIEWER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(walletService, never()).releaseReservedBalance(any());
    }

    @Test
    void rejectRedemption_notificationFires_withCorrectTypeAndNoRejectionReason() {
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .build();
        RedemptionRejectedEvent event = new RedemptionRejectedEvent(this, request, REVIEWER_ID);

        service.onRedemptionRejected(event);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        NotificationEvent published = captor.getValue();
        assertThat(published.notificationTypeKey()).isEqualTo("redemption.rejected");
        assertThat(published.targetUserIds()).containsExactly(USER_ID);
        assertThat(published.actorUserId()).isEqualTo(REVIEWER_ID);
        assertThat(published.metadata()).doesNotContainKey("rejectionReason");
    }

    @Test
    void rejectRedemption_walletReleaseFailure_propagatesExceptionBeforeSave() {
        UUID redemptionId = UUID.randomUUID();
        RedemptionRequest request = RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .build();
        when(redemptionRequestRepository.findByIdAndClientIdForUpdate(redemptionId, CLIENT_ID))
                .thenReturn(Optional.of(request));
        doThrow(new RuntimeException("wallet locked")).when(walletService).releaseReservedBalance(any());

        assertThatThrownBy(() -> service.rejectRedemption(redemptionId, REJECTION_REASON, REVIEWER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("wallet locked");

        verify(redemptionRequestRepository, never()).save(any());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private RedemptionRequest buildRequestWithAssociations() {
        User requester = User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .passwordHash("hash")
                .build();

        RedemptionCatalogItem catalogItem = RedemptionCatalogItem.builder()
                .name("Amazon Gift Card")
                .build();

        return RedemptionRequestFixtures
                .pendingApproval(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .user(requester)
                .catalogItem(catalogItem)
                .build();
    }
}
