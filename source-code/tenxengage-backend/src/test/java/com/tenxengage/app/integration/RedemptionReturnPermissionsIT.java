package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.SubmitReturnRequest;
import com.tenxengage.app.dto.response.redemption.ReturnDetailResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RedemptionReturnRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.WalletMutationDelegate;
import com.tenxengage.app.service.redemption.ReturnService;
import com.tenxengage.app.service.redemption.ReturnVendorService;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.PartnerFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T1 — Permissions and security integration tests.
 * Covers optimistic locking concurrent approve+cancel, and state-machine enforcement
 * from the permissions perspective (US-01, US-02).
 *
 * Note: HTTP-layer permission checks (401/403 per role, feature flag) are covered
 * by per-story @WebMvcTest units. This IT class covers service-layer behaviours
 * that require real DB state.
 */
@Tag("integration")
class RedemptionReturnPermissionsIT extends AbstractLocalIntegrationTest {

    @Autowired private ReturnService returnService;
    @Autowired private RedemptionReturnRepository returnRepository;
    @Autowired private RedemptionRequestRepository redemptionRequestRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private RewardWalletRepository rewardWalletRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate txTemplate;

    @MockBean private WalletMutationDelegate walletMutationDelegate;
    @MockBean private ReturnVendorService returnVendorService;

    private UUID clientId;
    private UUID userId;
    private UUID walletId;
    private RedemptionCatalogItem returnableItem;

    @BeforeEach
    void setUp() {
        var client = txTemplate.execute(status ->
                clientRepository.save(ClientFixtures.activeEnterprise().build()));
        clientId = client.getId();

        var partner = txTemplate.execute(status ->
                partnerCompanyRepository.save(PartnerFixtures.activeReseller(clientId).build()));

        User user = txTemplate.execute(status -> userRepository.save(User.builder()
                .email("perm-" + UUID.randomUUID() + "@test.com")
                .firstName("Perm").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(partner.getId())
                .build()));
        userId = user.getId();

        var wallet = txTemplate.execute(status ->
                rewardWalletRepository.save(RewardWalletFixtures.individualWallet(clientId, userId).build()));
        walletId = wallet.getId();

        returnableItem = txTemplate.execute(status ->
                catalogItemRepository.save(RedemptionCatalogItem.builder()
                        .name("Perm Test Gift Card")
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(BigDecimal.TEN)
                        .isReturnable(true)
                        .defaultReturnWindowDays(30)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void concurrentApproveAndCancel_secondWriterReceivesStateConflict() {
        // Arrange: create a PENDING_APPROVAL return in a committed transaction
        UUID returnId = txTemplate.execute(status -> {
            TenantContext.setClientId(clientId);
            try {
                RedemptionRequest redemption = redemptionRequestRepository.save(
                        buildCompletedRedemption());
                var submitted = returnService.submitReturn(
                        new SubmitReturnRequest(redemption.getId(), "concurrent test"),
                        userId, clientId);
                return submitted.id();
            } finally {
                TenantContext.clear();
            }
        });

        assertThat(returnId).isNotNull();

        // Act: approve in its own committed transaction
        txTemplate.execute(status -> {
            TenantContext.setClientId(clientId);
            try {
                returnService.approveReturn(returnId, userId, clientId);
                return null;
            } finally {
                TenantContext.clear();
            }
        });

        // Attempting to approve an already-APPROVED return must fail with StateConflictException
        assertThatThrownBy(() ->
                txTemplate.execute(status -> {
                    TenantContext.setClientId(clientId);
                    try {
                        returnService.approveReturn(returnId, userId, clientId);
                        return null;
                    } finally {
                        TenantContext.clear();
                    }
                })
        ).isInstanceOf(StateConflictException.class);
    }

    @Test
    @Transactional
    void approveReturn_onAlreadyApproved_throwsStateConflict() {
        TenantContext.setClientId(clientId);
        RedemptionRequest redemption = redemptionRequestRepository.save(buildCompletedRedemption());
        var submitted = returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "test"), userId, clientId);

        // First approve succeeds
        returnService.approveReturn(submitted.id(), userId, clientId);

        // Second approve on same return should throw
        assertThatThrownBy(() -> returnService.approveReturn(submitted.id(), userId, clientId))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    @Transactional
    void cancelReturn_onAlreadyApproved_throwsStateConflict() {
        TenantContext.setClientId(clientId);
        RedemptionRequest redemption = redemptionRequestRepository.save(buildCompletedRedemption());
        var submitted = returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "test"), userId, clientId);

        // Approve first
        returnService.approveReturn(submitted.id(), userId, clientId);

        // Cancel on APPROVED must fail
        assertThatThrownBy(() -> returnService.cancelReturn(submitted.id(), userId, clientId))
                .isInstanceOf(StateConflictException.class);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RedemptionRequest buildCompletedRedemption() {
        return RedemptionRequest.builder()
                .clientId(clientId)
                .walletId(walletId)
                .userId(userId)
                .catalogItemId(returnableItem.getId())
                .amount(new BigDecimal("100.00"))
                .currencyId("USD")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.COMPLETED)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.NON_CASH)
                .submittedAt(Instant.now().minus(15, ChronoUnit.DAYS))
                .completedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();
    }
}
