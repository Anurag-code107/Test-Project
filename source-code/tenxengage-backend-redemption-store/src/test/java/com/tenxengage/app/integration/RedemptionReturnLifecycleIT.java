package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.SubmitReturnRequest;
import com.tenxengage.app.dto.response.redemption.ReturnDetailResponse;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RedemptionReturn;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.ReturnResolution;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.ResourceNotFoundException;
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

@Transactional
@Tag("integration")
class RedemptionReturnLifecycleIT extends AbstractLocalIntegrationTest {

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

        PartnerCompany partner = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(clientId).build());

        User user = userRepository.save(User.builder()
                .email("lifecycle-" + UUID.randomUUID() + "@test.com")
                .firstName("Test").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(partner.getId())
                .build());
        userId = user.getId();

        var wallet = rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(clientId, userId).build());

        RedemptionCatalogItem item = catalogItemRepository.save(RedemptionCatalogItem.builder()
                .name("Lifecycle Test Gift Card")
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
    void submitReturn_createsReturnInPendingApproval() {
        var req = new SubmitReturnRequest(baseRedemption.getId(), "Product defective");

        ReturnDetailResponse resp = returnService.submitReturn(req, userId, clientId);

        assertThat(resp.id()).isNotNull();
        assertThat(resp.status()).isEqualTo(ReturnStatus.PENDING_APPROVAL);
        assertThat(resp.amount()).isEqualByComparingTo(baseRedemption.getAmount());
        assertThat(resp.redemptionId()).isEqualTo(baseRedemption.getId());
        assertThat(resp.createdAt()).isNotNull();
    }

    @Test
    void flyway_redemptionReturnsTableAndColumnsExist() {
        var req = new SubmitReturnRequest(baseRedemption.getId(), "Schema check");
        ReturnDetailResponse resp = returnService.submitReturn(req, userId, clientId);

        RedemptionReturn ret = returnRepository.findById(resp.id()).orElseThrow();

        assertThat(ret.getId()).isNotNull();
        assertThat(ret.getClientId()).isEqualTo(clientId);
        assertThat(ret.getPartnerUserId()).isEqualTo(userId);
        assertThat(ret.getStatus()).isEqualTo(ReturnStatus.PENDING_APPROVAL);
        assertThat(ret.getAmount()).isNotNull();
        assertThat(ret.getCurrencyId()).isNotNull();
        assertThat(ret.getVersion()).isNotNull();
        assertThat(ret.isDeleted()).isFalse();
    }

    @Test
    void submitReturn_nonExistentRedemptionId_throwsResourceNotFoundException() {
        var req = new SubmitReturnRequest(UUID.randomUUID(), "reason");

        assertThatThrownBy(() -> returnService.submitReturn(req, userId, clientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitReturn_redemptionFromDifferentTenant_throwsResourceNotFoundException() {
        var otherClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        var req = new SubmitReturnRequest(baseRedemption.getId(), "reason");

        assertThatThrownBy(() -> returnService.submitReturn(req, userId, otherClient.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approveReturn_transitionsFromPendingApprovalToApproved() {
        ReturnDetailResponse submitted = submit("Defective");

        ReturnDetailResponse approved = returnService.approveReturn(submitted.id(), userId, clientId);

        assertThat(approved.status()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.reviewedAt()).isNotNull();
    }

    @Test
    void processVendorConfirmation_confirmedTrue_transitionsToReturnConfirmed() {
        ReturnDetailResponse submitted = submit("Defective");
        returnService.approveReturn(submitted.id(), userId, clientId);

        RedemptionReturn ret = returnRepository.findById(submitted.id()).orElseThrow();
        String vendorRef = "VEND-" + UUID.randomUUID();
        ret.setVendorReturnReference(vendorRef);
        returnRepository.save(ret);

        returnService.processVendorConfirmation(vendorRef, true, null);

        RedemptionReturn updated = returnRepository.findById(submitted.id()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);
        assertThat(updated.getConfirmedAt()).isNotNull();
    }

    @Test
    void processVendorConfirmation_confirmedFalse_transitionsToReturnRejected() {
        ReturnDetailResponse submitted = submit("Defective");
        returnService.approveReturn(submitted.id(), userId, clientId);

        RedemptionReturn ret = returnRepository.findById(submitted.id()).orElseThrow();
        String vendorRef = "VEND-" + UUID.randomUUID();
        ret.setVendorReturnReference(vendorRef);
        returnRepository.save(ret);

        returnService.processVendorConfirmation(vendorRef, false, "Not eligible");

        RedemptionReturn updated = returnRepository.findById(submitted.id()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReturnStatus.RETURN_REJECTED);
        assertThat(updated.getRejectedAt()).isNotNull();
        assertThat(updated.getReviewNotes()).isEqualTo("Not eligible");
    }

    @Test
    void cancelReturn_transitionsToCancelled() {
        ReturnDetailResponse submitted = submit("Changed mind");

        returnService.cancelReturn(submitted.id(), userId, clientId);

        RedemptionReturn updated = returnRepository.findById(submitted.id()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReturnStatus.CANCELLED);
        assertThat(updated.getCancelledAt()).isNotNull();
    }

    @Test
    void resolveTimedOut_confirm_transitionsToReturnConfirmed() {
        ReturnDetailResponse submitted = submit("Defective");
        returnService.approveReturn(submitted.id(), userId, clientId);

        forceTimedOut(submitted.id());

        ReturnDetailResponse resolved = returnService.resolveTimedOut(
                submitted.id(), ReturnResolution.CONFIRM, "Admin confirmed", userId, clientId);

        assertThat(resolved.status()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);
        assertThat(resolved.confirmedAt()).isNotNull();
    }

    @Test
    void resolveTimedOut_reject_transitionsToReturnRejected() {
        ReturnDetailResponse submitted = submit("Defective");
        returnService.approveReturn(submitted.id(), userId, clientId);

        forceTimedOut(submitted.id());

        ReturnDetailResponse resolved = returnService.resolveTimedOut(
                submitted.id(), ReturnResolution.REJECT, "Not valid after review", userId, clientId);

        assertThat(resolved.status()).isEqualTo(ReturnStatus.RETURN_REJECTED);
        assertThat(resolved.rejectedAt()).isNotNull();
    }

    @Test
    void approveReturn_onReturnConfirmed_throwsStateConflictException() {
        ReturnDetailResponse submitted = submit("Defective");
        returnService.approveReturn(submitted.id(), userId, clientId);
        forceStatus(submitted.id(), ReturnStatus.RETURN_CONFIRMED);

        assertThatThrownBy(() -> returnService.approveReturn(submitted.id(), userId, clientId))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    void approveReturn_onReturnRejected_throwsStateConflictException() {
        ReturnDetailResponse submitted = submit("Defective");
        forceStatus(submitted.id(), ReturnStatus.RETURN_REJECTED);

        assertThatThrownBy(() -> returnService.approveReturn(submitted.id(), userId, clientId))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    void resolveTimedOut_onApproved_throwsStateConflictException() {
        ReturnDetailResponse submitted = submit("Defective");
        returnService.approveReturn(submitted.id(), userId, clientId);

        assertThatThrownBy(() -> returnService.resolveTimedOut(
                submitted.id(), ReturnResolution.CONFIRM, null, userId, clientId))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    void fullLifecycle_allTerminalStatesTraversed() {
        // Submit
        ReturnDetailResponse submitted = submit("Full lifecycle");
        assertThat(submitted.status()).isEqualTo(ReturnStatus.PENDING_APPROVAL);

        // Approve
        ReturnDetailResponse approved = returnService.approveReturn(submitted.id(), userId, clientId);
        assertThat(approved.status()).isEqualTo(ReturnStatus.APPROVED);

        // Simulate timeout
        forceTimedOut(submitted.id());

        // Admin resolve CONFIRM
        ReturnDetailResponse confirmed = returnService.resolveTimedOut(
                submitted.id(), ReturnResolution.CONFIRM, "Resolved after timeout", userId, clientId);
        assertThat(confirmed.status()).isEqualTo(ReturnStatus.RETURN_CONFIRMED);
    }

    @Test
    void rejectReturn_withReason_transitionsToReturnRejected() {
        ReturnDetailResponse submitted = submit("Defective");

        var rejectRequest = new com.tenxengage.app.dto.request.redemption.RejectReturnRequest("Item not eligible");
        ReturnDetailResponse rejected = returnService.rejectReturn(submitted.id(), rejectRequest, userId, clientId);

        assertThat(rejected.status()).isEqualTo(ReturnStatus.RETURN_REJECTED);
        assertThat(rejected.rejectedAt()).isNotNull();
        assertThat(rejected.reviewNotes()).isEqualTo("Item not eligible");
    }

    @Test
    void cancelledReturn_allowsResubmission() {
        ReturnDetailResponse first = submit("First attempt");
        returnService.cancelReturn(first.id(), userId, clientId);

        ReturnDetailResponse second = submit("Resubmission after cancel");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(ReturnStatus.PENDING_APPROVAL);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReturnDetailResponse submit(String reason) {
        return returnService.submitReturn(
                new SubmitReturnRequest(baseRedemption.getId(), reason), userId, clientId);
    }

    private void forceStatus(UUID returnId, ReturnStatus status) {
        RedemptionReturn ret = returnRepository.findById(returnId).orElseThrow();
        ret.setStatus(status);
        returnRepository.save(ret);
    }

    private void forceTimedOut(UUID returnId) {
        RedemptionReturn ret = returnRepository.findById(returnId).orElseThrow();
        ret.setStatus(ReturnStatus.RETURN_TIMED_OUT);
        ret.setTimedOutAt(Instant.now());
        returnRepository.save(ret);
    }
}
