package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.SubmitReturnRequest;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.ResourceNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T1 — Tenant isolation tests (US-01, US-02).
 * Verifies that Tenant B cannot access or act on Tenant A's returns (404, not 403).
 */
@Transactional
@Tag("integration")
class RedemptionReturnTenantIsolationIT extends AbstractLocalIntegrationTest {

    @Autowired private ReturnService returnService;
    @Autowired private RedemptionReturnRepository returnRepository;
    @Autowired private RedemptionRequestRepository redemptionRequestRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private RewardWalletRepository rewardWalletRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private WalletMutationDelegate walletMutationDelegate;
    @MockBean private ReturnVendorService returnVendorService;

    // Tenant A
    private UUID clientIdA;
    private UUID userIdA;
    private UUID walletIdA;

    // Tenant B
    private UUID clientIdB;
    private UUID userIdB;

    private RedemptionCatalogItem sharedItem;
    private UUID returnIdA;

    @BeforeEach
    void setUp() {
        // Set up Tenant A
        var clientA = clientRepository.save(ClientFixtures.activeEnterprise().build());
        clientIdA = clientA.getId();

        var partnerA = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(clientIdA).build());

        User userA = userRepository.save(User.builder()
                .email("isolation-a-" + UUID.randomUUID() + "@test.com")
                .firstName("Tenant").lastName("A User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientIdA)
                .partnerCompanyId(partnerA.getId())
                .build());
        userIdA = userA.getId();

        var walletA = rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(clientIdA, userIdA).build());
        walletIdA = walletA.getId();

        // Set up Tenant B
        var clientB = clientRepository.save(ClientFixtures.activeEnterprise().build());
        clientIdB = clientB.getId();

        var partnerB = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(clientIdB).build());

        User userB = userRepository.save(User.builder()
                .email("isolation-b-" + UUID.randomUUID() + "@test.com")
                .firstName("Tenant").lastName("B User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientIdB)
                .partnerCompanyId(partnerB.getId())
                .build());
        userIdB = userB.getId();

        // Shared catalog item (no client scoping on catalog items)
        sharedItem = catalogItemRepository.save(RedemptionCatalogItem.builder()
                .name("Isolation Test Gift Card")
                .category(RedemptionCategory.NON_CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(BigDecimal.TEN)
                .isReturnable(true)
                .defaultReturnWindowDays(30)
                .build());

        // Tenant A submits a return
        RedemptionRequest redemptionA = redemptionRequestRepository.save(
                buildCompletedRedemption(clientIdA, userIdA, walletIdA));

        TenantContext.setClientId(clientIdA);
        var submitted = returnService.submitReturn(
                new SubmitReturnRequest(redemptionA.getId(), "Tenant A return"), userIdA, clientIdA);
        returnIdA = submitted.id();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tenantB_getReturnById_onTenantAReturn_returns404() {
        TenantContext.setClientId(clientIdB);

        assertThatThrownBy(() ->
                returnService.getReturnById(returnIdA, userIdB, clientIdB, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tenantB_cancelReturn_onTenantAReturn_returns404() {
        TenantContext.setClientId(clientIdB);

        // Tenant B tries to cancel Tenant A's return — must throw 404 (IDOR prevention)
        assertThatThrownBy(() ->
                returnService.cancelReturn(returnIdA, userIdB, clientIdB))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tenantB_adminApproveReturn_onTenantAReturn_returns404() {
        TenantContext.setClientId(clientIdB);

        // Tenant B admin tries to approve Tenant A's return — must throw 404
        assertThatThrownBy(() ->
                returnService.approveReturn(returnIdA, userIdB, clientIdB))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tenantA_getReturnById_returnsOwnReturn_successfully() {
        TenantContext.setClientId(clientIdA);

        // Tenant A can access its own return (sanity check)
        var response = returnService.getReturnById(returnIdA, userIdA, clientIdA, false);
        org.assertj.core.api.Assertions.assertThat(response.id()).isEqualTo(returnIdA);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RedemptionRequest buildCompletedRedemption(UUID clientId, UUID userId, UUID walletId) {
        return RedemptionRequest.builder()
                .clientId(clientId)
                .walletId(walletId)
                .userId(userId)
                .catalogItemId(sharedItem.getId())
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
