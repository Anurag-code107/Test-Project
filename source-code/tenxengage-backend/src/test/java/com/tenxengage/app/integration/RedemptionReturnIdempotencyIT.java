package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.SubmitReturnRequest;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.entity.enums.ReturnResolution;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T1 — Idempotency tests for webhook duplicate handling (US-03, US-04).
 * Verifies that a late Xoxoday webhook arriving after admin manual resolve
 * is a no-op and does not double-credit the wallet.
 */
@Transactional
@Tag("integration")
class RedemptionReturnIdempotencyIT extends AbstractLocalIntegrationTest {

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
    private RedemptionRequest baseRedemption;

    @BeforeEach
    void setUp() {
        var client = clientRepository.save(ClientFixtures.activeEnterprise().build());
        clientId = client.getId();

        var partner = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(clientId).build());

        User user = userRepository.save(User.builder()
                .email("idempotency-" + UUID.randomUUID() + "@test.com")
                .firstName("Idempotency").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(partner.getId())
                .build());
        userId = user.getId();

        var wallet = rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(clientId, userId).build());

        RedemptionCatalogItem item = catalogItemRepository.save(RedemptionCatalogItem.builder()
                .name("Idempotency Test Gift Card")
                .category(RedemptionCategory.NON_CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(BigDecimal.TEN)
                .isReturnable(true)
                .defaultReturnWindowDays(30)
                .build());

        baseRedemption = redemptionRequestRepository.save(RedemptionRequest.builder()
                .clientId(clientId)
                .walletId(wallet.getId())
                .userId(userId)
                .catalogItemId(item.getId())
                .amount(new BigDecimal("100.00"))
                .currencyId("USD")
                .walletType(WalletType.INDIVIDUAL)
                .status(RedemptionStatus.COMPLETED)
                .processingMode(RedemptionProcessingMode.INSTANT)
                .category(RedemptionCategory.NON_CASH)
                .submittedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .completedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build());

        TenantContext.setClientId(clientId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void webhook_arrivesAfterAdminManualResolveConfirm_isNoOp_walletNotDoubleCredited() {
        // Submit and approve
        var submitted = returnService.submitReturn(
                new SubmitReturnRequest(baseRedemption.getId(), "Defective"), userId, clientId);
        returnService.approveReturn(submitted.id(), userId, clientId);

        // Force RETURN_TIMED_OUT
        RedemptionReturn ret = returnRepository.findById(submitted.id()).orElseThrow();
        String vendorRef = "VEND-" + UUID.randomUUID();
        ret.setStatus(ReturnStatus.RETURN_TIMED_OUT);
        ret.setTimedOutAt(Instant.now());
        ret.setVendorReturnReference(vendorRef);
        returnRepository.save(ret);

        // Admin manually resolves (CONFIRM) — this is the first credit
        returnService.resolveTimedOut(submitted.id(), ReturnResolution.CONFIRM, "Manual confirm", userId, clientId);

        // Verify state is RETURN_CONFIRMED after manual resolve
        RedemptionReturn afterResolve = returnRepository.findById(submitted.id()).orElseThrow();
        assertThat(afterResolve.getStatus()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);

        // Late webhook arrives — should be a no-op (TERMINAL_STATUSES guard)
        returnService.processVendorConfirmation(vendorRef, true, null);

        // State should remain RETURN_CONFIRMED (unchanged)
        RedemptionReturn afterWebhook = returnRepository.findById(submitted.id()).orElseThrow();
        assertThat(afterWebhook.getStatus()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);

        // WalletMutationDelegate should have been called exactly once (from resolveTimedOut)
        // The second call (from processVendorConfirmation on terminal state) must be skipped
        verify(walletMutationDelegate, times(1))
                .doReturnCreditInTx(any(), any(), eq("RETURN"), eq(submitted.id()));
    }

    @Test
    void webhook_duplicateConfirmOnAlreadyConfirmed_isNoOp() {
        // Submit → Approve → set vendorRef → first webhook CONFIRM
        var submitted = returnService.submitReturn(
                new SubmitReturnRequest(baseRedemption.getId(), "Defective"), userId, clientId);
        returnService.approveReturn(submitted.id(), userId, clientId);

        RedemptionReturn ret = returnRepository.findById(submitted.id()).orElseThrow();
        String vendorRef = "VEND-DUP-" + UUID.randomUUID();
        ret.setVendorReturnReference(vendorRef);
        returnRepository.save(ret);

        // First webhook — processes normally
        returnService.processVendorConfirmation(vendorRef, true, null);

        RedemptionReturn afterFirst = returnRepository.findById(submitted.id()).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);

        // Second (duplicate) webhook — must be no-op
        returnService.processVendorConfirmation(vendorRef, true, null);

        // Status unchanged
        RedemptionReturn afterSecond = returnRepository.findById(submitted.id()).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);
        assertThat(afterSecond.getConfirmedAt()).isEqualTo(afterFirst.getConfirmedAt());

        // WalletMutationDelegate called exactly once (idempotency guard in service)
        verify(walletMutationDelegate, times(1))
                .doReturnCreditInTx(any(), any(), eq("RETURN"), eq(submitted.id()));
    }
}
