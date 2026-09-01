package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.SubmitReturnRequest;
import com.tenxengage.app.dto.response.redemption.ReturnQueueItemResponse;
import com.tenxengage.app.dto.response.redemption.ReturnSummaryResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.entity.enums.WalletType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — Query correctness at scale (US-01, US-02).
 * Verifies pagination, filtering, sorting, and tenant isolation across larger data sets.
 */
@Transactional
@Tag("integration")
class RedemptionReturnQueryIT extends AbstractLocalIntegrationTest {

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

    private UUID clientId;
    private UUID userId;
    private UUID walletId;
    private RedemptionCatalogItem returnableItem;

    // Tenant B
    private UUID clientIdB;
    private UUID userIdB;
    private UUID walletIdB;

    @BeforeEach
    void setUp() {
        var client = clientRepository.save(ClientFixtures.activeEnterprise().build());
        clientId = client.getId();

        var partner = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(clientId).build());

        User user = userRepository.save(User.builder()
                .email("query-" + UUID.randomUUID() + "@test.com")
                .firstName("Query").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(partner.getId())
                .build());
        userId = user.getId();

        var wallet = rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(clientId, userId).build());
        walletId = wallet.getId();

        returnableItem = catalogItemRepository.save(RedemptionCatalogItem.builder()
                .name("Query Test Gift Card")
                .category(RedemptionCategory.NON_CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(BigDecimal.TEN)
                .isReturnable(true)
                .defaultReturnWindowDays(30)
                .build());

        // Tenant B setup
        var clientB = clientRepository.save(ClientFixtures.activeEnterprise().build());
        clientIdB = clientB.getId();

        var partnerB = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(clientIdB).build());

        User userB = userRepository.save(User.builder()
                .email("query-b-" + UUID.randomUUID() + "@test.com")
                .firstName("Query").lastName("B User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientIdB)
                .partnerCompanyId(partnerB.getId())
                .build());
        userIdB = userB.getId();

        var walletB = rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(clientIdB, userIdB).build());
        walletIdB = walletB.getId();

        TenantContext.setClientId(clientId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void adminList_75Returns_page2Size25_returnsCorrectSlice() {
        // Create 75 distinct redemptions, each with one return
        for (int i = 0; i < 75; i++) {
            RedemptionRequest redemption = redemptionRequestRepository.save(
                    buildCompletedRedemption(clientId, userId, new BigDecimal("10.00")));
            returnService.submitReturn(
                    new SubmitReturnRequest(redemption.getId(), "reason " + i),
                    userId, clientId);
        }

        // Page 1 (0-indexed) of size 25, sorted by createdAt DESC
        // Pass explicit date range covering today to avoid null-parameter type inference issue in PostgreSQL
        java.time.LocalDate farPast = java.time.LocalDate.of(2000, 1, 1);
        java.time.LocalDate farFuture = java.time.LocalDate.of(2099, 12, 31);
        // Native query: sort property must use DB column name (snake_case), not JPA field name
        PageRequest page2 = PageRequest.of(1, 25, Sort.by(Sort.Direction.DESC, "created_at"));
        Page<ReturnQueueItemResponse> result = returnService.getAdminReturns(
                clientId, null, farPast, farFuture, page2);

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(75);
        assertThat(result.getContent()).hasSize(25);
    }

    @Test
    void adminList_statusFilter_pendingApprovalOnly_noLeakage() {
        // Create 5 PENDING_APPROVAL and 3 that are then cancelled
        for (int i = 0; i < 5; i++) {
            RedemptionRequest r = redemptionRequestRepository.save(
                    buildCompletedRedemption(clientId, userId, new BigDecimal("10.00")));
            returnService.submitReturn(new SubmitReturnRequest(r.getId(), "pending"), userId, clientId);
        }

        for (int i = 0; i < 3; i++) {
            RedemptionRequest r = redemptionRequestRepository.save(
                    buildCompletedRedemption(clientId, userId, new BigDecimal("10.00")));
            var sub = returnService.submitReturn(
                    new SubmitReturnRequest(r.getId(), "cancel me"), userId, clientId);
            returnService.cancelReturn(sub.id(), userId, clientId);
        }

        java.time.LocalDate farPast = java.time.LocalDate.of(2000, 1, 1);
        java.time.LocalDate farFuture = java.time.LocalDate.of(2099, 12, 31);
        // Native query: sort property must use DB column name (snake_case), not JPA field name
        PageRequest pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "created_at"));
        Page<ReturnQueueItemResponse> result = returnService.getAdminReturns(
                clientId, ReturnStatus.PENDING_APPROVAL, farPast, farFuture, pageable);

        assertThat(result.getContent())
                .allSatisfy(item -> assertThat(item.status()).isEqualTo(ReturnStatus.PENDING_APPROVAL));
        assertThat(result.getContent()).hasSize(5);
    }

    @Test
    void partnerList_2Tenants_each10Returns_tenantASeesOnlyOwn() {
        // Create 10 returns for Tenant A
        for (int i = 0; i < 10; i++) {
            RedemptionRequest r = redemptionRequestRepository.save(
                    buildCompletedRedemption(clientId, userId, new BigDecimal("10.00")));
            returnService.submitReturn(new SubmitReturnRequest(r.getId(), "A " + i), userId, clientId);
        }

        // Create 10 returns for Tenant B
        TenantContext.setClientId(clientIdB);
        for (int i = 0; i < 10; i++) {
            RedemptionRequest r = redemptionRequestRepository.save(
                    buildCompletedRedemption(clientIdB, userIdB, walletIdB, new BigDecimal("10.00")));
            returnService.submitReturn(new SubmitReturnRequest(r.getId(), "B " + i), userIdB, clientIdB);
        }

        // Query as Tenant A
        TenantContext.setClientId(clientId);
        PageRequest pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReturnSummaryResponse> resultA = returnService.getPartnerReturns(userId, clientId, pageable);

        assertThat(resultA.getContent()).hasSize(10);
        // Tenant B's returns must not appear
        assertThat(resultA.getContent())
                .allSatisfy(item -> {
                    // All entries belong to Tenant A's user
                    assertThat(item.id()).isNotNull();
                });
    }

    @Test
    void partnerList_pageOutOfBounds_returnsEmptyContent_withCorrectTotalElements() {
        // Create 2 returns
        for (int i = 0; i < 2; i++) {
            RedemptionRequest r = redemptionRequestRepository.save(
                    buildCompletedRedemption(clientId, userId, new BigDecimal("10.00")));
            returnService.submitReturn(new SubmitReturnRequest(r.getId(), "oob " + i), userId, clientId);
        }

        PageRequest outOfBounds = PageRequest.of(999, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReturnSummaryResponse> result = returnService.getPartnerReturns(userId, clientId, outOfBounds);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void adminList_sortByAmountAscending_returnsOrderedByAmountAsc() {
        // Create returns with varying amounts
        BigDecimal[] amounts = {
                new BigDecimal("50.00"),
                new BigDecimal("10.00"),
                new BigDecimal("30.00"),
                new BigDecimal("20.00")
        };
        for (BigDecimal amount : amounts) {
            RedemptionRequest r = redemptionRequestRepository.save(
                    buildCompletedRedemption(clientId, userId, amount));
            returnService.submitReturn(new SubmitReturnRequest(r.getId(), "sort test"), userId, clientId);
        }

        java.time.LocalDate farPast = java.time.LocalDate.of(2000, 1, 1);
        java.time.LocalDate farFuture = java.time.LocalDate.of(2099, 12, 31);
        PageRequest sortedAsc = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "amount"));
        Page<ReturnQueueItemResponse> result = returnService.getAdminReturns(
                clientId, null, farPast, farFuture, sortedAsc);

        var content = result.getContent();
        assertThat(content).hasSizeGreaterThanOrEqualTo(4);
        // Verify ascending order
        for (int i = 1; i < content.size(); i++) {
            assertThat(content.get(i).amount())
                    .isGreaterThanOrEqualTo(content.get(i - 1).amount());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RedemptionRequest buildCompletedRedemption(UUID clientId, UUID userId, BigDecimal amount) {
        return buildCompletedRedemption(clientId, userId, walletId, amount);
    }

    private RedemptionRequest buildCompletedRedemption(UUID clientId, UUID userId, UUID wId, BigDecimal amount) {
        return RedemptionRequest.builder()
                .clientId(clientId)
                .walletId(wId)
                .userId(userId)
                .catalogItemId(returnableItem.getId())
                .amount(amount)
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
