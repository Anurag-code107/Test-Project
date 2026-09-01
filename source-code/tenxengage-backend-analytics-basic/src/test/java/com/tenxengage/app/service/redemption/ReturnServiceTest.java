package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.redemption.RejectReturnRequest;
import com.tenxengage.app.dto.request.redemption.SubmitReturnRequest;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.ReturnResolution;
import com.tenxengage.app.dto.response.redemption.ReturnDetailResponse;
import com.tenxengage.app.dto.response.redemption.ReturnQueueItemResponse;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RedemptionReturnRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.ReturnEventProducer;
import com.tenxengage.app.service.WalletMutationDelegate;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import com.tenxengage.app.testdata.RedemptionRequestFixtures;
import com.tenxengage.app.testdata.RedemptionReturnFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnServiceTest {

    @Mock private RedemptionReturnRepository returnRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private PartnerCompanyRepository partnerCompanyRepository;
    @Mock private ReturnEventProducer returnEventProducer;
    @Mock private ReturnVendorService returnVendorService;
    @Mock private WalletMutationDelegate walletMutationDelegate;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private ReturnService returnService;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();
    private static final UUID CATALOG_ITEM_ID = UUID.randomUUID();
    private static final UUID REDEMPTION_ID = UUID.randomUUID();
    private static final UUID RETURN_ID = UUID.randomUUID();

    private RedemptionRequest completedNonCashRedemption;
    private RedemptionCatalogItem returnableCatalogItem;

    @BeforeEach
    void setUp() {
        completedNonCashRedemption = RedemptionRequestFixtures
                .defaultPersonal(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .status(RedemptionStatus.COMPLETED)
                .category(RedemptionCategory.NON_CASH)
                .completedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .amount(new BigDecimal("100.0000"))
                .currencyId("cash")
                .build();
        completedNonCashRedemption.setId(REDEMPTION_ID);

        returnableCatalogItem = RedemptionCatalogItemFixtures.activeNonCashItem()
                .isReturnable(true)
                .defaultReturnWindowDays(30)
                .name("Test Gift Card")
                .build();
        returnableCatalogItem.setId(CATALOG_ITEM_ID);

        User user = User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@test.com")
                .build();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(redemptionRequestRepository.findByIdAndClientId(REDEMPTION_ID, CLIENT_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(returnableCatalogItem));
    }

    // ── submitReturn happy path ─────────────────────────────────────────────────

    @Test
    void submitReturn_happyPath_returnsDetailResponseWithPendingApproval() {
        RedemptionReturn saved = RedemptionReturnFixtures
                .aSubmittedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .amount(new BigDecimal("100.0000"))
                .build();
        saved.setId(RETURN_ID);

        when(redemptionRequestRepository.findByIdForUpdate(REDEMPTION_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));
        when(returnRepository.existsByRedemptionIdAndClientIdAndStatusNotIn(
                eq(REDEMPTION_ID), eq(CLIENT_ID), any()))
                .thenReturn(false);
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(saved);

        SubmitReturnRequest request = new SubmitReturnRequest(REDEMPTION_ID, "Item was damaged");
        ReturnDetailResponse response = returnService.submitReturn(request, USER_ID, CLIENT_ID);

        assertThat(response).isNotNull();
        assertThat(response.redemptionId()).isEqualTo(REDEMPTION_ID);
        assertThat(response.status()).isEqualTo(ReturnStatus.PENDING_APPROVAL);
        assertThat(response.amount()).isEqualTo(new BigDecimal("100.0000"));

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReturnStatus.PENDING_APPROVAL);
        assertThat(captor.getValue().getReason()).isEqualTo("Item was damaged");
        // Amount must be copied from redemption, not from caller
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(completedNonCashRedemption.getAmount());

        verify(returnEventProducer).publishReturnRequested(saved);
    }

    // ── submitReturn 422 paths ──────────────────────────────────────────────────

    @Test
    void submitReturn_xtrm422_whenCashCategory() {
        RedemptionRequest cashRedemption = RedemptionRequestFixtures
                .defaultPersonal(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .status(RedemptionStatus.COMPLETED)
                .category(RedemptionCategory.CASH)
                .completedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        cashRedemption.setId(REDEMPTION_ID);

        when(redemptionRequestRepository.findByIdForUpdate(REDEMPTION_ID))
                .thenReturn(Optional.of(cashRedemption));

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(REDEMPTION_ID, null), USER_ID, CLIENT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cash redemptions cannot be returned");

        verify(returnRepository, never()).save(any());
        verify(returnEventProducer, never()).publishReturnRequested(any());
    }

    @Test
    void submitReturn_nonCompleted422_whenRedemptionNotCompleted() {
        RedemptionRequest processingRedemption = RedemptionRequestFixtures
                .defaultPersonal(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .status(RedemptionStatus.PROCESSING)
                .category(RedemptionCategory.NON_CASH)
                .build();
        processingRedemption.setId(REDEMPTION_ID);

        when(redemptionRequestRepository.findByIdForUpdate(REDEMPTION_ID))
                .thenReturn(Optional.of(processingRedemption));

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(REDEMPTION_ID, null), USER_ID, CLIENT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("COMPLETED");

        verify(returnRepository, never()).save(any());
    }

    @Test
    void submitReturn_nonReturnable422_whenCatalogItemNotReturnable() {
        RedemptionCatalogItem nonReturnableItem = RedemptionCatalogItemFixtures.activeNonCashItem()
                .isReturnable(false)
                .defaultReturnWindowDays(30)
                .build();
        nonReturnableItem.setId(CATALOG_ITEM_ID);

        when(redemptionRequestRepository.findByIdForUpdate(REDEMPTION_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(nonReturnableItem));

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(REDEMPTION_ID, null), USER_ID, CLIENT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not eligible for return");

        verify(returnRepository, never()).save(any());
    }

    @Test
    void submitReturn_expiredWindow422_whenReturnWindowExpired() {
        RedemptionRequest oldRedemption = RedemptionRequestFixtures
                .defaultPersonal(CLIENT_ID, USER_ID, WALLET_ID, CATALOG_ITEM_ID)
                .status(RedemptionStatus.COMPLETED)
                .category(RedemptionCategory.NON_CASH)
                .completedAt(Instant.now().minus(60, ChronoUnit.DAYS))  // 60 days ago
                .build();
        oldRedemption.setId(REDEMPTION_ID);
        RedemptionCatalogItem shortWindowItem = RedemptionCatalogItemFixtures.activeNonCashItem()
                .isReturnable(true)
                .defaultReturnWindowDays(30)  // only 30 day window
                .build();
        shortWindowItem.setId(CATALOG_ITEM_ID);

        when(redemptionRequestRepository.findByIdForUpdate(REDEMPTION_ID))
                .thenReturn(Optional.of(oldRedemption));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(shortWindowItem));

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(REDEMPTION_ID, null), USER_ID, CLIENT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Return window");

        verify(returnRepository, never()).save(any());
    }

    // ── submitReturn 409 path ───────────────────────────────────────────────────

    @Test
    void submitReturn_duplicateActive409_whenActiveReturnExists() {
        when(redemptionRequestRepository.findByIdForUpdate(REDEMPTION_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));
        when(returnRepository.existsByRedemptionIdAndClientIdAndStatusNotIn(
                eq(REDEMPTION_ID), eq(CLIENT_ID), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(REDEMPTION_ID, null), USER_ID, CLIENT_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("already active");

        verify(returnRepository, never()).save(any());
    }

    // ── cancelReturn happy path ─────────────────────────────────────────────────

    @Test
    void cancelReturn_happyPath_transitionsToCancelledAndPublishesEvent() {
        RedemptionReturn pendingReturn = RedemptionReturnFixtures
                .aSubmittedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        pendingReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientIdAndPartnerUserId(RETURN_ID, CLIENT_ID, USER_ID))
                .thenReturn(Optional.of(pendingReturn));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(pendingReturn);

        returnService.cancelReturn(RETURN_ID, USER_ID, CLIENT_ID);

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReturnStatus.CANCELLED);
        assertThat(captor.getValue().getCancelledAt()).isNotNull();

        verify(returnEventProducer).publishReturnCancelled(any());
    }

    @Test
    void cancelReturn_wrongState409_whenNotPendingApproval() {
        RedemptionReturn approvedReturn = RedemptionReturnFixtures
                .anApprovedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        approvedReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientIdAndPartnerUserId(RETURN_ID, CLIENT_ID, USER_ID))
                .thenReturn(Optional.of(approvedReturn));

        assertThatThrownBy(() -> returnService.cancelReturn(RETURN_ID, USER_ID, CLIENT_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("PENDING_APPROVAL");

        verify(returnRepository, never()).save(any());
    }

    @Test
    void cancelReturn_wrongOwner404_whenReturnNotFoundForUser() {
        UUID otherUserId = UUID.randomUUID();
        when(returnRepository.findByIdAndClientIdAndPartnerUserId(RETURN_ID, CLIENT_ID, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.cancelReturn(RETURN_ID, otherUserId, CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(returnRepository, never()).save(any());
    }

    // ── isReturnEligible (single-item, DB path) ─────────────────────────────────

    @Test
    void isReturnEligible_singleItem_returnsTrueWhenNoActiveReturn() {
        when(returnRepository.existsByRedemptionIdAndClientIdAndStatusNotIn(
                eq(REDEMPTION_ID), eq(CLIENT_ID), anyList()))
                .thenReturn(false);

        boolean result = returnService.isReturnEligible(completedNonCashRedemption, returnableCatalogItem, CLIENT_ID);

        assertThat(result).isTrue();
    }

    @Test
    void isReturnEligible_singleItem_returnsFalseWhenActiveReturnExists() {
        when(returnRepository.existsByRedemptionIdAndClientIdAndStatusNotIn(
                eq(REDEMPTION_ID), eq(CLIENT_ID), anyList()))
                .thenReturn(true);

        boolean result = returnService.isReturnEligible(completedNonCashRedemption, returnableCatalogItem, CLIENT_ID);

        assertThat(result).isFalse();
    }

    // ── isReturnEligible (batch Set overload) ────────────────────────────────────

    @Test
    void isReturnEligible_batchOverload_returnsTrueWhenIdNotInActiveSet() {
        Set<UUID> activeReturns = Set.of(UUID.randomUUID()); // different ID — not this redemption

        boolean result = returnService.isReturnEligible(
                completedNonCashRedemption, returnableCatalogItem, activeReturns);

        assertThat(result).isTrue();
        verifyNoInteractions(returnRepository); // no DB call — pre-loaded set used
    }

    @Test
    void isReturnEligible_batchOverload_returnsFalseWhenIdInActiveSet() {
        Set<UUID> activeReturns = Set.of(REDEMPTION_ID);

        boolean result = returnService.isReturnEligible(
                completedNonCashRedemption, returnableCatalogItem, activeReturns);

        assertThat(result).isFalse();
        verifyNoInteractions(returnRepository);
    }

    // ── getRedemptionIdsWithActiveReturns ────────────────────────────────────────

    @Test
    void getRedemptionIdsWithActiveReturns_delegatesToRepository() {
        Set<UUID> expected = Set.of(REDEMPTION_ID);
        when(returnRepository.findRedemptionIdsWithActiveReturns(anyList(), eq(CLIENT_ID), anyList()))
                .thenReturn(expected);

        Set<UUID> result = returnService.getRedemptionIdsWithActiveReturns(List.of(REDEMPTION_ID), CLIENT_ID);

        assertThat(result).isEqualTo(expected);
        verify(returnRepository).findRedemptionIdsWithActiveReturns(anyList(), eq(CLIENT_ID), anyList());
    }

    @Test
    void getRedemptionIdsWithActiveReturns_emptyListGuard_returnsEmptySetWithoutDbCall() {
        Set<UUID> result = returnService.getRedemptionIdsWithActiveReturns(List.of(), CLIENT_ID);

        assertThat(result).isEmpty();
        verify(returnRepository, never()).findRedemptionIdsWithActiveReturns(any(), any(), any());
    }

    // ── approveReturn happy path ────────────────────────────────────────────────

    @Test
    void approveReturn_happyPath_transitionsToApprovedAndPublishesEvent() {
        RedemptionReturn pendingReturn = RedemptionReturnFixtures
                .aSubmittedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        pendingReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.of(pendingReturn));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(pendingReturn);
        // resolver stubs
        when(redemptionRequestRepository.findByIdAndClientId(REDEMPTION_ID, CLIENT_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(returnableCatalogItem));

        ReturnDetailResponse response = returnService.approveReturn(RETURN_ID, USER_ID, CLIENT_ID);

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(captor.getValue().getReviewedBy()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getApprovedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(ReturnStatus.APPROVED);

        verify(returnEventProducer).publishReturnApproved(any(RedemptionReturn.class));
        // In unit tests TransactionSynchronizationManager.isActualTransactionActive() == false,
        // so the else-branch fires notifyXoxodayReturn synchronously — verify it was called.
        verify(returnVendorService).notifyXoxodayReturn(any(RedemptionReturn.class));
    }

    @Test
    void approveReturn_wrongState409_whenNotPendingApproval() {
        RedemptionReturn approvedReturn = RedemptionReturnFixtures
                .anApprovedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        approvedReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.of(approvedReturn));

        assertThatThrownBy(() -> returnService.approveReturn(RETURN_ID, USER_ID, CLIENT_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("PENDING_APPROVAL");

        verify(returnRepository, never()).save(any());
        verify(returnEventProducer, never()).publishReturnApproved(any());
    }

    @Test
    void approveReturn_notFound404_whenReturnNotFound() {
        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.approveReturn(RETURN_ID, USER_ID, CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(returnRepository, never()).save(any());
    }

    // ── rejectReturn happy path ─────────────────────────────────────────────────

    @Test
    void rejectReturn_happyPath_transitionsToRejectedAndPublishesEvent() {
        RedemptionReturn pendingReturn = RedemptionReturnFixtures
                .aSubmittedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        pendingReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.of(pendingReturn));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(pendingReturn);
        when(redemptionRequestRepository.findByIdAndClientId(REDEMPTION_ID, CLIENT_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(returnableCatalogItem));

        RejectReturnRequest request = new RejectReturnRequest("Item was already used");
        ReturnDetailResponse response = returnService.rejectReturn(RETURN_ID, request, USER_ID, CLIENT_ID);

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReturnStatus.RETURN_REJECTED);
        assertThat(captor.getValue().getReviewedBy()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getRejectedAt()).isNotNull();
        assertThat(captor.getValue().getReviewNotes()).isEqualTo("Item was already used");
        assertThat(response.status()).isEqualTo(ReturnStatus.RETURN_REJECTED);

        verify(returnEventProducer).publishReturnRejected(any(RedemptionReturn.class));
    }

    @Test
    void rejectReturn_wrongState409_whenNotPendingApproval() {
        RedemptionReturn approvedReturn = RedemptionReturnFixtures
                .anApprovedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        approvedReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.of(approvedReturn));

        RejectReturnRequest request = new RejectReturnRequest("some reason");
        assertThatThrownBy(() -> returnService.rejectReturn(RETURN_ID, request, USER_ID, CLIENT_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("PENDING_APPROVAL");

        verify(returnRepository, never()).save(any());
        verify(returnEventProducer, never()).publishReturnRejected(any());
    }

    // ── getAdminReturns ──────────────────────────────────────────────────────────

    @Test
    void getAdminReturns_happyPath_returnsPaginatedQueueItems() {
        RedemptionReturn pendingReturn = RedemptionReturnFixtures
                .aSubmittedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        pendingReturn.setId(RETURN_ID);

        when(returnRepository.findByClientIdWithFilters(
                eq(CLIENT_ID), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(pendingReturn)));
        when(redemptionRequestRepository.findByIdInAndClientId(anyList(), eq(CLIENT_ID)))
                .thenReturn(List.of(completedNonCashRedemption));
        when(catalogItemRepository.findAllById(anyList()))
                .thenReturn(List.of(returnableCatalogItem));
        when(userRepository.findAllById(anyList())).thenReturn(List.of());
        when(partnerCompanyRepository.findAllById(anyList())).thenReturn(List.of());

        var result = returnService.getAdminReturns(CLIENT_ID, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(RETURN_ID);
        assertThat(result.getContent().get(0).status()).isEqualTo(ReturnStatus.PENDING_APPROVAL);
    }

    @Test
    void getAdminReturns_withStatusFilter_passesFilterToRepository() {
        when(returnRepository.findByClientIdWithFilters(
                eq(CLIENT_ID), eq(ReturnStatus.PENDING_APPROVAL.name()), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(userRepository.findAllById(anyList())).thenReturn(List.of());
        when(partnerCompanyRepository.findAllById(anyList())).thenReturn(List.of());

        var result = returnService.getAdminReturns(CLIENT_ID, ReturnStatus.PENDING_APPROVAL,
                null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        verify(returnRepository).findByClientIdWithFilters(
                eq(CLIENT_ID), eq(ReturnStatus.PENDING_APPROVAL.name()), isNull(), isNull(), any());
    }

    // ── getReturnById admin path ─────────────────────────────────────────────────

    @Test
    void getReturnById_adminPath_returnsAdminFieldsIncluded() {
        RedemptionReturn pendingReturn = RedemptionReturnFixtures
                .aSubmittedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .reviewNotes("Admin internal note")
                .vendorReturnReference("xoxo-ref-123")
                .build();
        pendingReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.of(pendingReturn));
        when(redemptionRequestRepository.findByIdAndClientId(REDEMPTION_ID, CLIENT_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));
        when(catalogItemRepository.findById(CATALOG_ITEM_ID))
                .thenReturn(Optional.of(returnableCatalogItem));

        ReturnDetailResponse response = returnService.getReturnById(RETURN_ID, null, CLIENT_ID, true);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(RETURN_ID);
        assertThat(response.reviewNotes()).isEqualTo("Admin internal note");
        assertThat(response.vendorReturnReference()).isEqualTo("xoxo-ref-123");
    }

    // ── processVendorConfirmation — confirmed=true ──────────────────────────────

    @Test
    void processVendorConfirmation_confirmed_creditsWalletAndTransitionsToReturnConfirmed() {
        RedemptionReturn approvedReturn = RedemptionReturnFixtures
                .anApprovedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .vendorReturnReference("xoxo-ref-123")
                .amount(new BigDecimal("100.0000"))
                .build();
        approvedReturn.setId(RETURN_ID);

        when(returnRepository.findByVendorReturnReference("xoxo-ref-123"))
                .thenReturn(Optional.of(approvedReturn));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(approvedReturn);
        when(redemptionRequestRepository.findByIdAndClientId(REDEMPTION_ID, CLIENT_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));

        returnService.processVendorConfirmation("xoxo-ref-123", true, null);

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);
        assertThat(captor.getValue().getConfirmedAt()).isNotNull();

        // Wallet must be credited
        verify(walletMutationDelegate).doReturnCreditInTx(
                eq(completedNonCashRedemption.getWalletId()),
                eq(new BigDecimal("100.0000")),
                eq("RETURN"),
                eq(RETURN_ID));

        verify(returnEventProducer).publishReturnConfirmed(any(RedemptionReturn.class));
        verify(returnEventProducer, never()).publishReturnRejected(any());
    }

    // ── processVendorConfirmation — confirmed=false ─────────────────────────────

    @Test
    void processVendorConfirmation_rejected_noWalletCreditAndTransitionsToReturnRejected() {
        RedemptionReturn approvedReturn = RedemptionReturnFixtures
                .anApprovedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .vendorReturnReference("xoxo-ref-456")
                .build();
        approvedReturn.setId(RETURN_ID);

        when(returnRepository.findByVendorReturnReference("xoxo-ref-456"))
                .thenReturn(Optional.of(approvedReturn));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(approvedReturn);

        returnService.processVendorConfirmation("xoxo-ref-456", false, "Item was already used");

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReturnStatus.RETURN_REJECTED);
        assertThat(captor.getValue().getRejectedAt()).isNotNull();
        assertThat(captor.getValue().getReviewNotes()).isEqualTo("Item was already used");

        // No wallet credit on rejection
        verify(walletMutationDelegate, never()).doReturnCreditInTx(any(), any(), any(), any());

        verify(returnEventProducer).publishReturnRejected(any(RedemptionReturn.class));
        verify(returnEventProducer, never()).publishReturnConfirmed(any());
    }

    // ── processVendorConfirmation — idempotency guard ───────────────────────────

    @Test
    void processVendorConfirmation_alreadyReturnConfirmed_idempotentNoOp() {
        RedemptionReturn confirmedReturn = RedemptionReturnFixtures
                .aConfirmedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .vendorReturnReference("xoxo-ref-789")
                .build();
        confirmedReturn.setId(RETURN_ID);

        when(returnRepository.findByVendorReturnReference("xoxo-ref-789"))
                .thenReturn(Optional.of(confirmedReturn));

        returnService.processVendorConfirmation("xoxo-ref-789", true, null);

        // No state change — already terminal
        verify(returnRepository, never()).save(any());
        verify(walletMutationDelegate, never()).doReturnCreditInTx(any(), any(), any(), any());
        verify(returnEventProducer, never()).publishReturnConfirmed(any());
    }

    @Test
    void processVendorConfirmation_alreadyReturnTimedOut_idempotentNoOp() {
        RedemptionReturn timedOutReturn = RedemptionReturnFixtures
                .aTimedOutReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .vendorReturnReference("xoxo-ref-timed")
                .build();
        timedOutReturn.setId(RETURN_ID);

        when(returnRepository.findByVendorReturnReference("xoxo-ref-timed"))
                .thenReturn(Optional.of(timedOutReturn));

        returnService.processVendorConfirmation("xoxo-ref-timed", true, null);

        verify(returnRepository, never()).save(any());
        verify(walletMutationDelegate, never()).doReturnCreditInTx(any(), any(), any(), any());
        verify(returnEventProducer, never()).publishReturnConfirmed(any());
    }

    @Test
    void processVendorConfirmation_unknownVendorReturnReference_warnAndNoOp() {
        when(returnRepository.findByVendorReturnReference("unknown-ref"))
                .thenReturn(Optional.empty());

        returnService.processVendorConfirmation("unknown-ref", true, null);

        verify(returnRepository, never()).save(any());
        verify(walletMutationDelegate, never()).doReturnCreditInTx(any(), any(), any(), any());
        verify(returnEventProducer, never()).publishReturnConfirmed(any());
    }

    // ── resolveTimedOut — CONFIRM path ──────────────────────────────────────────

    @Test
    void resolveTimedOut_confirm_creditsWalletAndTransitionsToReturnConfirmed() {
        RedemptionReturn timedOutReturn = RedemptionReturnFixtures
                .aTimedOutReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .amount(new BigDecimal("100.0000"))
                .build();
        timedOutReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.of(timedOutReturn));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(timedOutReturn);
        when(redemptionRequestRepository.findByIdAndClientId(REDEMPTION_ID, CLIENT_ID))
                .thenReturn(Optional.of(completedNonCashRedemption));

        returnService.resolveTimedOut(RETURN_ID, ReturnResolution.CONFIRM, "Admin confirmed", USER_ID, CLIENT_ID);

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);
        assertThat(captor.getValue().getConfirmedAt()).isNotNull();
        assertThat(captor.getValue().getReviewedBy()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getReviewNotes()).isEqualTo("Admin confirmed");

        verify(walletMutationDelegate).doReturnCreditInTx(
                eq(completedNonCashRedemption.getWalletId()),
                eq(new BigDecimal("100.0000")),
                eq("RETURN"),
                eq(RETURN_ID));

        verify(returnEventProducer).publishReturnConfirmed(any(RedemptionReturn.class));
        verify(returnEventProducer, never()).publishReturnRejected(any());

        // BE-4: CONFIRM path must emit COMPLETED audit entry
        verify(auditLogService).logAsync(
                eq(AuditAction.COMPLETED),
                eq(AuditResourceType.REDEMPTION_RETURN),
                eq(RETURN_ID),
                isNull(),
                eq("Manually confirmed timed-out return"),
                isNull());
    }

    // ── resolveTimedOut — REJECT path ───────────────────────────────────────────

    @Test
    void resolveTimedOut_reject_noWalletCreditAndTransitionsToReturnRejected() {
        RedemptionReturn timedOutReturn = RedemptionReturnFixtures
                .aTimedOutReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        timedOutReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.of(timedOutReturn));
        when(returnRepository.save(any(RedemptionReturn.class))).thenReturn(timedOutReturn);

        returnService.resolveTimedOut(RETURN_ID, ReturnResolution.REJECT, "Cannot honor return", USER_ID, CLIENT_ID);

        ArgumentCaptor<RedemptionReturn> captor = ArgumentCaptor.forClass(RedemptionReturn.class);
        verify(returnRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReturnStatus.RETURN_REJECTED);
        assertThat(captor.getValue().getRejectedAt()).isNotNull();
        assertThat(captor.getValue().getReviewNotes()).isEqualTo("Cannot honor return");

        verify(walletMutationDelegate, never()).doReturnCreditInTx(any(), any(), any(), any());
        verify(returnEventProducer).publishReturnRejected(any(RedemptionReturn.class));
        verify(returnEventProducer, never()).publishReturnConfirmed(any());

        // BE-4: REJECT path must emit REJECTED audit entry (not COMPLETED)
        verify(auditLogService).logAsync(
                eq(AuditAction.REJECTED),
                eq(AuditResourceType.REDEMPTION_RETURN),
                eq(RETURN_ID),
                isNull(),
                eq("Manually rejected timed-out return"),
                isNull());
        verify(auditLogService, never()).logAsync(
                eq(AuditAction.COMPLETED),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    // ── resolveTimedOut — wrong state ───────────────────────────────────────────

    @Test
    void resolveTimedOut_wrongState409_whenNotReturnTimedOut() {
        RedemptionReturn approvedReturn = RedemptionReturnFixtures
                .anApprovedReturn(CLIENT_ID, REDEMPTION_ID, USER_ID)
                .build();
        approvedReturn.setId(RETURN_ID);

        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.of(approvedReturn));

        assertThatThrownBy(() ->
                returnService.resolveTimedOut(RETURN_ID, ReturnResolution.CONFIRM, null, USER_ID, CLIENT_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("RETURN_TIMED_OUT");

        verify(returnRepository, never()).save(any());
        verify(walletMutationDelegate, never()).doReturnCreditInTx(any(), any(), any(), any());
        verifyNoInteractions(auditLogService);
    }

    // ── resolveTimedOut — not found ─────────────────────────────────────────────

    @Test
    void resolveTimedOut_notFound404_whenReturnNotFound() {
        when(returnRepository.findByIdAndClientId(RETURN_ID, CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                returnService.resolveTimedOut(RETURN_ID, ReturnResolution.CONFIRM, null, USER_ID, CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(returnRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }
}
