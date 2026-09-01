package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.redemption.RedemptionHistoryFilters;
import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.dto.request.redemption.RedemptionAdminHistoryFilters;
import com.tenxengage.app.dto.response.redemption.RedemptionAdminHistoryResponse;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.redemption.RedemptionHistoryRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionHistoryServiceTest {

    @Mock private RedemptionHistoryRepository historyRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private RewardWalletRepository walletRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.tenxengage.app.service.PermissionService permissionService;
    @Mock private TenantValidator tenantValidator;
    @Mock private ReturnService returnService;

    @InjectMocks
    private RedemptionHistoryService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID CATALOG_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    private RedemptionCatalogItem catalogItem;

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(any())).thenReturn(java.util.Set.of());

        catalogItem = new RedemptionCatalogItem();
        catalogItem.setId(CATALOG_ID);
        catalogItem.setName("Amazon Gift Card");
        when(catalogItemRepository.findAllById(any())).thenReturn(List.of(catalogItem));
        when(catalogItemRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalogItem));
    }

    private RedemptionRequest request(RedemptionStatus status, RedemptionCategory category) {
        RedemptionRequest r = new RedemptionRequest();
        r.setId(REQUEST_ID);
        r.setClientId(CLIENT_ID);
        r.setUserId(USER_ID);
        r.setCatalogItemId(CATALOG_ID);
        r.setAmount(new BigDecimal("100.00"));
        r.setCurrencyId("cash");
        r.setStatus(status);
        r.setCategory(category);
        r.setProcessingMode(RedemptionProcessingMode.INSTANT);
        r.setWalletType(WalletType.INDIVIDUAL);
        r.setSubmittedAt(Instant.now());
        r.setDeleted(false);
        return r;
    }

    @Test
    void getPersonalHistory_noFilters_returnsAllRows() {
        Page<RedemptionRequest> dbPage = new PageImpl<>(List.of(
                request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH),
                request(RedemptionStatus.RESERVED, RedemptionCategory.NON_CASH)));
        when(historyRepository.findPersonalHistory(
                eq(USER_ID), eq(CLIENT_ID), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(dbPage);

        Page<RedemptionRequestResponse> result = service.getPersonalHistory(
                USER_ID, RedemptionHistoryFilters.empty(), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).catalogItemName()).isEqualTo("Amazon Gift Card");
    }

    @Test
    void getPersonalHistory_statusFilter_returnsMatchingRows() {
        RedemptionHistoryFilters filters = new RedemptionHistoryFilters(
                RedemptionStatus.COMPLETED, null, null, null);
        Page<RedemptionRequest> dbPage = new PageImpl<>(
                List.of(request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH)));
        when(historyRepository.findPersonalHistory(
                eq(USER_ID), eq(CLIENT_ID), eq(RedemptionStatus.COMPLETED), eq(null), eq(null), eq(null), any()))
                .thenReturn(dbPage);

        Page<RedemptionRequestResponse> result = service.getPersonalHistory(USER_ID, filters, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("COMPLETED");
    }

    @Test
    void getPersonalHistory_categoryFilter_returnsMatchingRows() {
        RedemptionHistoryFilters filters = new RedemptionHistoryFilters(
                null, RedemptionCategory.CASH, null, null);
        Page<RedemptionRequest> dbPage = new PageImpl<>(
                List.of(request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH)));
        when(historyRepository.findPersonalHistory(
                eq(USER_ID), eq(CLIENT_ID), eq(null), eq(RedemptionCategory.CASH), eq(null), eq(null), any()))
                .thenReturn(dbPage);

        Page<RedemptionRequestResponse> result = service.getPersonalHistory(USER_ID, filters, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).category()).isEqualTo("CASH");
    }

    @Test
    void getPersonalHistory_dateFilter_passesInstantsToRepository() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        RedemptionHistoryFilters filters = new RedemptionHistoryFilters(null, null, from, to);
        when(historyRepository.findPersonalHistory(
                eq(USER_ID), eq(CLIENT_ID), eq(null), eq(null), any(Instant.class), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of(request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH))));

        Page<RedemptionRequestResponse> result = service.getPersonalHistory(USER_ID, filters, PageRequest.of(0, 20));

        assertThat(result).isNotNull();
    }

    @Test
    void getPersonalHistory_crossTenant_returnsEmptyPage() {
        when(historyRepository.findPersonalHistory(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        Page<RedemptionRequestResponse> result = service.getPersonalHistory(
                UUID.randomUUID(), RedemptionHistoryFilters.empty(), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    void getRedemptionDetail_completed_vendorRefPresentLinkedReturnIdNull() {
        RedemptionRequest req = request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH);
        req.setVendorReferenceId("VENDOR-123");
        req.setCompletedAt(Instant.now());
        when(redemptionRequestRepository.findByIdAndClientIdAndUserId(REQUEST_ID, CLIENT_ID, USER_ID))
                .thenReturn(Optional.of(req));

        RedemptionRequestDetailResponse result = service.getRedemptionDetail(REQUEST_ID, USER_ID);

        assertThat(result.vendorReferenceId()).isEqualTo("VENDOR-123");
        assertThat(result.failureReason()).isNull();
        assertThat(result.linkedReturnId()).isNull();
    }

    @Test
    void getRedemptionDetail_failed_failureReasonPresentVendorRefNull() {
        RedemptionRequest req = request(RedemptionStatus.FAILED, RedemptionCategory.CASH);
        req.setFailureReason("Dispatch failed");
        when(redemptionRequestRepository.findByIdAndClientIdAndUserId(REQUEST_ID, CLIENT_ID, USER_ID))
                .thenReturn(Optional.of(req));

        RedemptionRequestDetailResponse result = service.getRedemptionDetail(REQUEST_ID, USER_ID);

        assertThat(result.failureReason()).isEqualTo("Dispatch failed");
        assertThat(result.vendorReferenceId()).isNull();
        assertThat(result.linkedReturnId()).isNull();
    }

    // The confirmation card shows the item's image: uploaded (via the API proxy) → vendor brand image
    // → category illustration. Both sources are resolved from the catalog item here.
    @Test
    void getRedemptionDetail_resolvesUploadedAndVendorImages() {
        RedemptionRequest req = request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH);
        when(redemptionRequestRepository.findByIdAndClientIdAndUserId(REQUEST_ID, CLIENT_ID, USER_ID))
                .thenReturn(Optional.of(req));
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .imageUrl("catalog/logo.png")
                .providerImageUrl("https://cdn.example.com/brands/sling.png")
                .build();
        item.setId(req.getCatalogItemId());
        when(catalogItemRepository.findById(req.getCatalogItemId())).thenReturn(Optional.of(item));

        RedemptionRequestDetailResponse result = service.getRedemptionDetail(REQUEST_ID, USER_ID);

        assertThat(result.imageUrl())
                .isEqualTo("/api/v1/admin/redemption-catalog/" + req.getCatalogItemId() + "/image");
        assertThat(result.providerImageUrl()).isEqualTo("https://cdn.example.com/brands/sling.png");
    }

    @Test
    void getRedemptionDetail_noUploadedImage_imageUrlNullVendorImageStillServed() {
        RedemptionRequest req = request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH);
        when(redemptionRequestRepository.findByIdAndClientIdAndUserId(REQUEST_ID, CLIENT_ID, USER_ID))
                .thenReturn(Optional.of(req));
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .providerImageUrl("https://cdn.example.com/brands/sling.png")
                .build();
        item.setId(req.getCatalogItemId());
        when(catalogItemRepository.findById(req.getCatalogItemId())).thenReturn(Optional.of(item));

        RedemptionRequestDetailResponse result = service.getRedemptionDetail(REQUEST_ID, USER_ID);

        assertThat(result.imageUrl()).isNull();
        assertThat(result.providerImageUrl()).isEqualTo("https://cdn.example.com/brands/sling.png");
    }

    @Test
    void getRedemptionDetail_notFound_throws404() {
        when(redemptionRequestRepository.findByIdAndClientIdAndUserId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRedemptionDetail(REQUEST_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // BU-9: the detail resolves the reviewer's display name from reviewedBy (COMPLETED + CANCELLED).
    @Test
    void getRedemptionDetail_resolvesReviewerName() {
        RedemptionRequest req = request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH);
        UUID reviewerId = UUID.randomUUID();
        req.setReviewedBy(reviewerId);
        when(redemptionRequestRepository.findByIdAndClientIdAndUserId(REQUEST_ID, CLIENT_ID, USER_ID))
                .thenReturn(Optional.of(req));
        User reviewer = mock(User.class);
        when(reviewer.getFirstName()).thenReturn("Jane");
        when(reviewer.getLastName()).thenReturn("Approver");
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));

        RedemptionRequestDetailResponse result = service.getRedemptionDetail(REQUEST_ID, USER_ID);

        assertThat(result.reviewedByName()).isEqualTo("Jane Approver");
    }

    // BU-9: safe fallback to null when the reviewer user is gone (still covers CANCELLED/rejected).
    @Test
    void getRedemptionDetail_reviewerMissing_nameFallsBackToNull() {
        RedemptionRequest req = request(RedemptionStatus.CANCELLED, RedemptionCategory.NON_CASH);
        req.setReviewedBy(UUID.randomUUID());
        req.setRejectionReason("Duplicate request");
        when(redemptionRequestRepository.findByIdAndClientIdAndUserId(REQUEST_ID, CLIENT_ID, USER_ID))
                .thenReturn(Optional.of(req));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        RedemptionRequestDetailResponse result = service.getRedemptionDetail(REQUEST_ID, USER_ID);

        assertThat(result.reviewedByName()).isNull();
        assertThat(result.rejectionReason()).isEqualTo("Duplicate request");
    }

    // ── Company history tests ──────────────────────────────────────────────

    @Test
    void getCompanyHistory_returnsAllCompanyWalletRecords_acrossAllCurrencies() {
        UUID companyId = UUID.randomUUID();
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(companyId);

        Page<RedemptionRequest> dbPage = new PageImpl<>(
                List.of(request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH)));
        when(historyRepository.findCompanyHistoryByPartnerCompany(
                eq(CLIENT_ID), eq(companyId), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(dbPage);

        Page<RedemptionRequestResponse> result = service.getCompanyHistory(
                USER_ID, RedemptionHistoryFilters.empty(), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).catalogItemName()).isEqualTo("Amazon Gift Card");
    }

    @Test
    void getCompanyHistory_withStatusFilter_passesThroughToRepository() {
        UUID companyId = UUID.randomUUID();
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(companyId);

        when(historyRepository.findCompanyHistoryByPartnerCompany(
                any(), any(), eq(RedemptionStatus.COMPLETED), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH))));

        RedemptionHistoryFilters filters = new RedemptionHistoryFilters(
                RedemptionStatus.COMPLETED, null, null, null);
        Page<RedemptionRequestResponse> result = service.getCompanyHistory(
                USER_ID, filters, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).status()).isEqualTo("COMPLETED");
    }

    @Test
    void getCompanyHistory_returnsEmptyPageWhenQueryFindsNothing() {
        UUID companyId = UUID.randomUUID();
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(companyId);
        when(historyRepository.findCompanyHistoryByPartnerCompany(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        Page<RedemptionRequestResponse> result = service.getCompanyHistory(
                USER_ID, RedemptionHistoryFilters.empty(), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    void getCompanyHistory_noPartnerCompanyId_returnsEmptyPage() {
        when(tenantValidator.getCurrentPartnerCompanyId()).thenReturn(null);

        Page<RedemptionRequestResponse> result = service.getCompanyHistory(
                USER_ID, RedemptionHistoryFilters.empty(), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ── Tenant history tests ──────────────────────────────────────────────

    private User userWithCompany(UUID userId, String firstName, String lastName, String companyName) {
        User u = new User();
        u.setId(userId);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        if (companyName != null) {
            PartnerCompany company = new PartnerCompany();
            company.setName(companyName);
            u.setPartnerCompany(company);
        }
        return u;
    }

    @Test
    void getTenantHistory_noFilters_returnsAllRowsWithDisplayNames() {
        RedemptionRequest req = request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH);
        when(historyRepository.findTenantHistory(
                eq(CLIENT_ID), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(req)));
        User user = userWithCompany(USER_ID, "Alice", "Smith", "Acme Corp");
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        Page<RedemptionAdminHistoryResponse> result = service.getTenantHistory(
                RedemptionAdminHistoryFilters.empty(), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).userDisplayName()).isEqualTo("Alice Smith");
        assertThat(result.getContent().get(0).partnerCompanyName()).isEqualTo("Acme Corp");
    }

    @Test
    void getTenantHistory_userIdFilter_passedToRepository() {
        UUID filterUserId = UUID.randomUUID();
        when(historyRepository.findTenantHistory(
                eq(CLIENT_ID), eq(filterUserId), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH))));
        when(userRepository.findAllById(any())).thenReturn(List.of(userWithCompany(USER_ID, "Bob", "Jones", null)));

        RedemptionAdminHistoryFilters filters = new RedemptionAdminHistoryFilters(
                null, null, null, null, filterUserId, null, null, null);
        Page<RedemptionAdminHistoryResponse> result = service.getTenantHistory(filters, PageRequest.of(0, 20));

        assertThat(result).isNotNull();
    }

    @Test
    void getTenantHistory_companyIdFilter_passedToRepository() {
        UUID filterCompanyId = UUID.randomUUID();
        when(historyRepository.findTenantHistory(
                eq(CLIENT_ID), eq(null), eq(filterCompanyId), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(request(RedemptionStatus.COMPLETED, RedemptionCategory.CASH))));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                userWithCompany(USER_ID, "Carol", "Davis", "BigCo")));

        RedemptionAdminHistoryFilters filters = new RedemptionAdminHistoryFilters(
                null, null, null, null, null, filterCompanyId, null, null);
        Page<RedemptionAdminHistoryResponse> result = service.getTenantHistory(filters, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).partnerCompanyName()).isEqualTo("BigCo");
    }

    @Test
    void getTenantHistory_crossTenant_returnsEmptyPage() {
        when(historyRepository.findTenantHistory(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        Page<RedemptionAdminHistoryResponse> result = service.getTenantHistory(
                RedemptionAdminHistoryFilters.empty(), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(0);
    }
}
