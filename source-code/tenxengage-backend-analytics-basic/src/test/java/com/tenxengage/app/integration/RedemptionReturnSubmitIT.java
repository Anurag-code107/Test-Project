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
import com.tenxengage.app.exception.BusinessRuleException;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T1 — Business rule enforcement tests for return submission.
 * Covers all eligibility-rejection and duplicate-guard scenarios from spec US-01.
 */
@Transactional
@Tag("integration")
class RedemptionReturnSubmitIT extends AbstractLocalIntegrationTest {

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

    @BeforeEach
    void setUp() {
        var client = clientRepository.save(ClientFixtures.activeEnterprise().build());
        clientId = client.getId();

        var partner = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(clientId).build());

        User user = userRepository.save(User.builder()
                .email("submit-" + UUID.randomUUID() + "@test.com")
                .firstName("Submit").lastName("User")
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
                .name("Submit Test Gift Card")
                .category(RedemptionCategory.NON_CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(BigDecimal.TEN)
                .isReturnable(true)
                .defaultReturnWindowDays(30)
                .build());

        TenantContext.setClientId(clientId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Eligibility failures ───────────────────────────────────────────────────

    @Test
    void submitReturn_cashRedemption_throwsBusinessRuleException() {
        RedemptionCatalogItem cashItem = catalogItemRepository.save(RedemptionCatalogItem.builder()
                .name("Cash Item")
                .category(RedemptionCategory.CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(BigDecimal.TEN)
                .isReturnable(false)
                .defaultReturnWindowDays(0)
                .build());

        RedemptionRequest cashRedemption = redemptionRequestRepository.save(
                completedRedemptionBuilder(cashItem.getId())
                        .category(RedemptionCategory.CASH)
                        .build());

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(cashRedemption.getId(), "cash return"), userId, clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cash redemptions cannot be returned");
    }

    @Test
    void submitReturn_nonCompletedRedemption_throwsBusinessRuleException() {
        RedemptionRequest pendingRedemption = redemptionRequestRepository.save(
                completedRedemptionBuilder(returnableItem.getId())
                        .status(RedemptionStatus.PROCESSING)
                        .completedAt(null)
                        .build());

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(pendingRedemption.getId(), "reason"), userId, clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void submitReturn_nonReturnableCatalogItem_throwsBusinessRuleException() {
        RedemptionCatalogItem nonReturnableItem = catalogItemRepository.save(
                RedemptionCatalogItem.builder()
                        .name("Non-returnable Item")
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(BigDecimal.TEN)
                        .isReturnable(false)
                        .defaultReturnWindowDays(30)
                        .build());

        RedemptionRequest redemption = redemptionRequestRepository.save(
                completedRedemptionBuilder(nonReturnableItem.getId()).build());

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "reason"), userId, clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not eligible for return");
    }

    @Test
    void submitReturn_outsideReturnWindow_throwsBusinessRuleException() {
        RedemptionCatalogItem shortWindowItem = catalogItemRepository.save(
                RedemptionCatalogItem.builder()
                        .name("Short Window Item")
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(BigDecimal.TEN)
                        .isReturnable(true)
                        .defaultReturnWindowDays(1)
                        .build());

        RedemptionRequest expiredRedemption = redemptionRequestRepository.save(
                completedRedemptionBuilder(shortWindowItem.getId())
                        .completedAt(Instant.now().minus(90, ChronoUnit.DAYS))
                        .build());

        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(expiredRedemption.getId(), "reason"), userId, clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Return window for this redemption has expired");
    }

    // ── Duplicate active return guard ──────────────────────────────────────────

    @Test
    void submitReturn_duplicateActivePendingReturn_throwsStateConflictException() {
        RedemptionRequest redemption = redemptionRequestRepository.save(
                completedRedemptionBuilder(returnableItem.getId()).build());

        // First submission
        returnService.submitReturn(new SubmitReturnRequest(redemption.getId(), "first"), userId, clientId);

        // Second submission while first is PENDING_APPROVAL
        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "duplicate"), userId, clientId))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    void submitReturn_duplicateActiveApprovedReturn_throwsStateConflictException() {
        RedemptionRequest redemption = redemptionRequestRepository.save(
                completedRedemptionBuilder(returnableItem.getId()).build());

        // Submit and approve
        var submitted = returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "first"), userId, clientId);
        returnService.approveReturn(submitted.id(), userId, clientId);

        // Try to submit another while APPROVED
        assertThatThrownBy(() -> returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "duplicate"), userId, clientId))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    void submitReturn_afterCancelledReturn_allowsResubmission() {
        RedemptionRequest redemption = redemptionRequestRepository.save(
                completedRedemptionBuilder(returnableItem.getId()).build());

        // Submit then cancel
        var first = returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "first"), userId, clientId);
        returnService.cancelReturn(first.id(), userId, clientId);

        // Resubmission must succeed
        ReturnDetailResponse second = returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "resubmit"), userId, clientId);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(ReturnStatus.PENDING_APPROVAL);
    }

    // ── Amount derivation ─────────────────────────────────────────────────────

    @Test
    void submitReturn_amountAlwaysCopiedFromOriginalRedemption() {
        BigDecimal originalAmount = new BigDecimal("250.00");
        RedemptionRequest redemption = redemptionRequestRepository.save(
                completedRedemptionBuilder(returnableItem.getId())
                        .amount(originalAmount)
                        .build());

        ReturnDetailResponse resp = returnService.submitReturn(
                new SubmitReturnRequest(redemption.getId(), "test"), userId, clientId);

        // Amount must equal the original redemption amount, not any caller-supplied value
        assertThat(resp.amount()).isEqualByComparingTo(originalAmount);
        assertThat(resp.status()).isEqualTo(ReturnStatus.PENDING_APPROVAL);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RedemptionRequest.RedemptionRequestBuilder completedRedemptionBuilder(UUID catalogItemId) {
        return RedemptionRequest.builder()
                .clientId(clientId)
                .walletId(walletId)
                .userId(userId)
                .catalogItemId(catalogItemId)
                .amount(new BigDecimal("100.00"))
                .currencyId("USD")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.COMPLETED)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.NON_CASH)
                .submittedAt(Instant.now().minus(15, ChronoUnit.DAYS))
                .completedAt(Instant.now().minus(5, ChronoUnit.DAYS));
    }
}
