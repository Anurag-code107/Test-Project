package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.*;
import com.tenxengage.app.entity.enums.*;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.repository.*;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.RedemptionOrchestrationService;
import com.tenxengage.app.service.redemption.RedemptionApprovalService;
import com.tenxengage.app.testdata.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Integration tests for the redemption approval queue — lifecycle, state machine,
 * concurrency, and tenant isolation. Calls service directly (no HTTP layer).
 *
 * RedemptionOrchestrationService is mocked: vendor stubs (Xoxoday/XTRM) throw in tests,
 * so dispatch must be a no-op unless a specific test needs to exercise failure behaviour.
 *
 * No class-level @Transactional: service uses internal transactions; setup data must be committed.
 */
@Tag("integration")
class RedemptionApprovalQueueIntegrationTest extends AbstractLocalIntegrationTest {

    @MockBean
    private RedemptionOrchestrationService redemptionOrchestrationService;

    @Autowired private RedemptionApprovalService approvalService;
    @Autowired private RedemptionRequestRepository redemptionRequestRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientCatalogItemConfigRepository catalogConfigRepository;
    @Autowired private RewardWalletRepository walletRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private DataSource dataSource;

    private Client testClient;
    private Client otherClient;
    private User requesterUser;
    private User approverUser;
    private User otherTenantUser;
    private PartnerCompany testCompany;
    private RedemptionCatalogItem catalogItem;
    private ClientCatalogItemConfig catalogConfig;
    private RewardWallet testWallet;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        otherClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        testCompany = partnerCompanyRepository.save(PartnerFixtures.activeReseller(testClient.getId()).build());

        requesterUser = userRepository.save(UserFixtures.activeUser(testClient.getId(), testCompany.getId()).build());
        approverUser = userRepository.save(UserFixtures.activeUser(testClient.getId(), testCompany.getId()).build());
        otherTenantUser = userRepository.save(User.builder()
                .email("other-tenant-" + UUID.randomUUID() + "@test.com")
                .firstName("Other").lastName("Tenant")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(otherClient.getId())
                .build());

        catalogItem = catalogItemRepository.save(
                RedemptionCatalogItemFixtures.activeNonCashItem()
                        .currencyId("cash")
                        .build());
        catalogConfig = catalogConfigRepository.save(
                ClientCatalogItemConfigFixtures.enabledConfig(testClient.getId(), catalogItem.getId()).build());

        // availableBalance + reservedBalance = 1000; reject flow requires reservedBalance >= request.amount (100)
        testWallet = walletRepository.save(
                RewardWalletFixtures.individualWalletWithBalance(
                        testClient.getId(), requesterUser.getId(), new BigDecimal("900.00"))
                        .reservedBalance(new BigDecimal("100.00"))
                        .build());

        TenantContext.setClientId(testClient.getId());
        setSecurityContext(approverUser, "ACTIVITY_APPROVER");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        safeDelete(() -> ledgerEntryRepository.deleteAll(
                ledgerEntryRepository.findByClientId(testClient.getId(), PageRequest.of(0, 10000)).getContent()));
        safeDelete(() -> ledgerEntryRepository.deleteAll(
                ledgerEntryRepository.findByClientId(otherClient.getId(), PageRequest.of(0, 10000)).getContent()));
        safeDelete(() -> redemptionRequestRepository.deleteAll(
                redemptionRequestRepository.findByClientIdAndDeletedFalse(testClient.getId(), PageRequest.of(0, 10000)).getContent()));
        safeDelete(() -> redemptionRequestRepository.deleteAll(
                redemptionRequestRepository.findByClientIdAndDeletedFalse(otherClient.getId(), PageRequest.of(0, 10000)).getContent()));
        safeDelete(() -> walletRepository.findById(testWallet.getId()).ifPresent(walletRepository::delete));
        safeDelete(() -> catalogConfigRepository.delete(catalogConfig));
        safeDelete(() -> catalogItemRepository.delete(catalogItem));
        safeDelete(() -> userRepository.delete(requesterUser));
        safeDelete(() -> userRepository.delete(approverUser));
        safeDelete(() -> userRepository.delete(otherTenantUser));
        safeDelete(() -> partnerCompanyRepository.delete(testCompany));
        safeDelete(() -> clientRepository.delete(testClient));
        safeDelete(() -> clientRepository.delete(otherClient));
    }

    // =========================================================================
    // Schema / Migration
    // =========================================================================

    @Test
    void flyway_v20_redemptionRequests_hasApprovalColumns() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            var cols = conn.getMetaData().getColumns(null, null, "redemption_requests", null);
            Set<String> columns = new HashSet<>();
            while (cols.next()) columns.add(cols.getString("COLUMN_NAME").toLowerCase());
            assertThat(columns).contains("reviewed_by", "reviewed_at", "rejection_reason");
        }
    }

    // =========================================================================
    // Lifecycle & CRUD
    // =========================================================================

    @Test
    void approveRedemption_pendingApproval_statusBecomesReserved() {
        var request = savedPendingApproval();

        var response = approvalService.approveRedemption(request.getId(), approverUser.getId());

        assertThat(response.status()).isEqualTo(RedemptionStatus.RESERVED.name());
        assertThat(response.reviewedBy()).isEqualTo(approverUser.getId());
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(response.rejectionReason()).isNull();

        var saved = redemptionRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RedemptionStatus.RESERVED);
        assertThat(saved.getReviewedBy()).isEqualTo(approverUser.getId());
        assertThat(saved.getReviewedAt()).isNotNull();
    }

    @Test
    void rejectRedemption_pendingApproval_statusBecomesCancelled() {
        var request = savedPendingApproval();

        var response = approvalService.rejectRedemption(request.getId(), "Duplicate request", approverUser.getId());

        assertThat(response.status()).isEqualTo(RedemptionStatus.CANCELLED.name());
        assertThat(response.rejectionReason()).isEqualTo("Duplicate request");
        assertThat(response.reviewedBy()).isEqualTo(approverUser.getId());
        assertThat(response.reviewedAt()).isNotNull();

        var saved = redemptionRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RedemptionStatus.CANCELLED);
        assertThat(saved.getRejectionReason()).isEqualTo("Duplicate request");
    }

    // =========================================================================
    // State Machine Transitions
    // =========================================================================

    @Test
    void approveRedemption_alreadyReserved_throwsStateConflictException() {
        var request = savedWithStatus(RedemptionStatus.RESERVED);

        assertThatThrownBy(() -> approvalService.approveRedemption(request.getId(), approverUser.getId()))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    void rejectRedemption_alreadyCancelled_throwsStateConflictException() {
        var request = savedWithStatus(RedemptionStatus.CANCELLED);

        assertThatThrownBy(() -> approvalService.rejectRedemption(request.getId(), "reason", approverUser.getId()))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    void rejectRedemption_alreadyReserved_throwsStateConflictException() {
        var request = savedWithStatus(RedemptionStatus.RESERVED);

        assertThatThrownBy(() -> approvalService.rejectRedemption(request.getId(), "reason", approverUser.getId()))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    // =========================================================================
    // Business Rule Enforcement
    // =========================================================================

    @Test
    void approveRedemption_concurrent_pessimisticLockEnsuresOnlyOneSucceeds() throws Exception {
        var request = savedPendingApproval();

        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    TenantContext.setClientId(testClient.getId());
                    setSecurityContext(approverUser, "ACTIVITY_APPROVER");
                    ready.countDown();
                    go.await();
                    approvalService.approveRedemption(request.getId(), approverUser.getId());
                    successes.incrementAndGet();
                } catch (StateConflictException | ResourceNotFoundException e) {
                    conflicts.incrementAndGet();
                } catch (Exception e) {
                    conflicts.incrementAndGet();
                } finally {
                    TenantContext.clear();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        var saved = redemptionRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RedemptionStatus.RESERVED);
    }

    @Test
    void getApprovalQueue_requestTypeReturn_returnsEmptyPage() {
        savedPendingApproval();

        var page = approvalService.getApprovalQueue(
                null, null, null, null,
                RedemptionRequestType.RETURN,
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void approveRedemption_vendorRoutingFailure_rollsBackStatusStaysPendingApproval() {
        doThrow(new RuntimeException("Vendor unavailable"))
                .when(redemptionOrchestrationService).dispatch(any());

        var request = savedPendingApproval();

        assertThatThrownBy(() -> approvalService.approveRedemption(request.getId(), approverUser.getId()))
                .isInstanceOf(RuntimeException.class);

        var saved = redemptionRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RedemptionStatus.PENDING_APPROVAL);
    }

    // =========================================================================
    // Multi-Entity Workflows
    // =========================================================================

    @Test
    void getApprovalQueue_returnsOnlyPendingApprovalItems() {
        var pending = savedPendingApproval();
        savedWithStatus(RedemptionStatus.RESERVED);
        savedWithStatus(RedemptionStatus.CANCELLED);

        var page = approvalService.getApprovalQueue(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(pending.getId());
    }

    @Test
    void approveRedemption_approvedItemDisappearsFromQueue() {
        var request = savedPendingApproval();
        approvalService.approveRedemption(request.getId(), approverUser.getId());

        var page = approvalService.getApprovalQueue(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(item -> item.id())
                .doesNotContain(request.getId());
    }

    @Test
    void rejectRedemption_rejectedItemDisappearsFromQueue() {
        var request = savedPendingApproval();
        approvalService.rejectRedemption(request.getId(), "Not eligible", approverUser.getId());

        var page = approvalService.getApprovalQueue(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(item -> item.id())
                .doesNotContain(request.getId());
    }

    // =========================================================================
    // Tenant Isolation & Security
    // =========================================================================

    @Test
    void approveRedemption_crossTenant_throwsResourceNotFoundException() {
        var request = savedPendingApproval();
        TenantContext.setClientId(otherClient.getId());
        setSecurityContext(otherTenantUser, "ACTIVITY_APPROVER");

        assertThatThrownBy(() -> approvalService.approveRedemption(request.getId(), otherTenantUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        TenantContext.setClientId(testClient.getId());
        setSecurityContext(approverUser, "ACTIVITY_APPROVER");
    }

    @Test
    void rejectRedemption_crossTenant_throwsResourceNotFoundException() {
        var request = savedPendingApproval();
        TenantContext.setClientId(otherClient.getId());
        setSecurityContext(otherTenantUser, "ACTIVITY_APPROVER");

        assertThatThrownBy(() -> approvalService.rejectRedemption(request.getId(), "reason", otherTenantUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        TenantContext.setClientId(testClient.getId());
        setSecurityContext(approverUser, "ACTIVITY_APPROVER");
    }

    @Test
    void getApprovalQueue_crossTenant_returnsEmptyNotException() {
        savedPendingApproval();
        TenantContext.setClientId(otherClient.getId());
        setSecurityContext(otherTenantUser, "ACTIVITY_APPROVER");

        var page = approvalService.getApprovalQueue(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();

        TenantContext.setClientId(testClient.getId());
        setSecurityContext(approverUser, "ACTIVITY_APPROVER");
    }

    // =========================================================================
    // Audit & Events
    // =========================================================================

    @Test
    void approveRedemption_persistsAuditFields() {
        var request = savedPendingApproval();
        approvalService.approveRedemption(request.getId(), approverUser.getId());

        var saved = redemptionRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getReviewedBy()).isEqualTo(approverUser.getId());
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getRejectionReason()).isNull();
    }

    @Test
    void rejectRedemption_persistsAuditFields() {
        var request = savedPendingApproval();
        approvalService.rejectRedemption(request.getId(), "Policy violation", approverUser.getId());

        var saved = redemptionRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(saved.getReviewedBy()).isEqualTo(approverUser.getId());
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getRejectionReason()).isEqualTo("Policy violation");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RedemptionRequest savedPendingApproval() {
        return redemptionRequestRepository.save(
                RedemptionRequestFixtures.pendingApproval(
                        testClient.getId(), requesterUser.getId(),
                        testWallet.getId(), catalogItem.getId()).build());
    }

    private RedemptionRequest savedWithStatus(RedemptionStatus status) {
        return redemptionRequestRepository.save(
                RedemptionRequestFixtures.withStatus(
                        testClient.getId(), requesterUser.getId(),
                        testWallet.getId(), catalogItem.getId(), status).build());
    }

    private void setSecurityContext(User user, String baseRole) {
        CustomUserDetails details = new CustomUserDetails(user);
        var token = new UsernamePasswordAuthenticationToken(
                details, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_" + baseRole)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void safeDelete(Runnable action) {
        try { action.run(); } catch (Exception ignored) {}
    }
}
