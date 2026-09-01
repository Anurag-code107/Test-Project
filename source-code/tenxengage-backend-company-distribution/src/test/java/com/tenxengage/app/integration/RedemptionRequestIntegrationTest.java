package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.SubmitPersonalRedemptionRequest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.TenantRedemptionSettingsRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.RedemptionSubmissionService;
import com.tenxengage.app.testdata.ClientCatalogItemConfigFixtures;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.PartnerFixtures;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import com.tenxengage.app.testdata.RedemptionRequestFixtures;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import com.tenxengage.app.testdata.TenantRedemptionSettingsFixtures;
import com.tenxengage.app.entity.RedemptionWebhookEvent;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.WebhookStatus;
import com.tenxengage.app.repository.RedemptionWebhookEventRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.RedemptionWebhookService;
import com.tenxengage.app.testdata.xtrm.PartnerRedemptionFixtures;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the redemption submission flow — business rules, wallet reservation,
 * batch date computation, and tenant isolation. Calls service directly (no HTTP layer).
 *
 * No class-level @Transactional: service uses internal transactions; setup data must be committed.
 */
@Tag("integration")
class RedemptionRequestIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionSubmissionService submissionService;
    @Autowired private RedemptionRequestRepository redemptionRequestRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientCatalogItemConfigRepository catalogConfigRepository;
    @Autowired private RewardWalletRepository walletRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private TenantRedemptionSettingsRepository settingsRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private PartnerRedemptionRepository userRedemptionRepository;
    @Autowired private RedemptionWebhookService webhookService;
    @Autowired private RedemptionWebhookEventRepository webhookEventRepository;
    @Autowired private DataSource dataSource;

    private Client testClient;
    private Client otherClient;
    private User testUser;
    private User otherUser;
    private PartnerCompany testCompany;
    private RedemptionCatalogItem instantItem;
    private RedemptionCatalogItem batchItem;
    private RedemptionCatalogItem cashInstantItem;
    private ClientCatalogItemConfig instantConfig;
    private ClientCatalogItemConfig batchConfig;
    private ClientCatalogItemConfig cashInstantConfig;
    private RewardWallet personalWallet;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        otherClient = clientRepository.save(ClientFixtures.activeEnterprise().build());

        testCompany = partnerCompanyRepository.save(PartnerFixtures.activeReseller(testClient.getId()).build());

        testUser = userRepository.save(User.builder()
                .email("redeem-test-" + UUID.randomUUID() + "@test.com")
                .firstName("Test").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .partnerCompanyId(testCompany.getId())
                .build());

        otherUser = userRepository.save(User.builder()
                .email("redeem-other-" + UUID.randomUUID() + "@test.com")
                .firstName("Other").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(otherClient.getId())
                .build());

        instantItem = catalogItemRepository.save(
                RedemptionCatalogItemFixtures.activeNonCashItem()
                        .currencyId("cash")
                        .defaultMinRedemptionAmount(new BigDecimal("50.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                        .build());

        batchItem = catalogItemRepository.save(
                RedemptionCatalogItemFixtures.activeNonCashItem()
                        .name("Batch Gift Card")
                        .providerItemId("BATCH-001")
                        .currencyId("cash")
                        .defaultMinRedemptionAmount(new BigDecimal("50.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.BATCH)
                        .build());

        instantConfig = catalogConfigRepository.save(
                ClientCatalogItemConfigFixtures.enabledConfig(testClient.getId(), instantItem.getId()).build());

        batchConfig = catalogConfigRepository.save(
                ClientCatalogItemConfigFixtures.enabledConfig(testClient.getId(), batchItem.getId()).build());

        personalWallet = walletRepository.save(
                RewardWalletFixtures.individualWalletWithBalance(
                        testClient.getId(), testUser.getId(), new BigDecimal("1000.00")).build());

        // CASH INSTANT item + an already-enrolled payout profile, for the XTRM after-commit dispatch path.
        cashInstantItem = catalogItemRepository.save(
                RedemptionCatalogItemFixtures.activeCashItem()
                        .currencyId("cash")
                        .defaultMinRedemptionAmount(new BigDecimal("50.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                        .build());
        cashInstantConfig = catalogConfigRepository.save(
                ClientCatalogItemConfigFixtures.enabledConfig(testClient.getId(), cashInstantItem.getId()).build());
        userRedemptionRepository.save(
                PartnerRedemptionFixtures.enrolled(testClient.getId(), testUser.getId(), "PAT-INT-1").build());

        TenantContext.setClientId(testClient.getId());
        setSecurityContext(testUser, "PARTNER_SELLER");
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
        safeDelete(() -> walletRepository.delete(personalWallet));
        safeDelete(() -> settingsRepository.findByClientId(testClient.getId()).ifPresent(settingsRepository::delete));
        safeDelete(() -> catalogConfigRepository.delete(instantConfig));
        safeDelete(() -> catalogConfigRepository.delete(batchConfig));
        safeDelete(() -> catalogItemRepository.delete(instantItem));
        safeDelete(() -> catalogItemRepository.delete(batchItem));
        safeDelete(() -> catalogConfigRepository.delete(cashInstantConfig));
        safeDelete(() -> catalogItemRepository.delete(cashInstantItem));
        // partner_redemption FKs users(id) — delete it before the user.
        safeDelete(() -> userRedemptionRepository
                .findByUserIdAndClientId(testUser.getId(), testClient.getId())
                .ifPresent(userRedemptionRepository::delete));
        safeDelete(() -> userRepository.delete(testUser));
        safeDelete(() -> userRepository.delete(otherUser));
        safeDelete(() -> partnerCompanyRepository.delete(testCompany));
        safeDelete(() -> clientRepository.delete(testClient));
        safeDelete(() -> clientRepository.delete(otherClient));
    }

    // =========================================================================
    // Schema / Migration
    // =========================================================================

    @Test
    void flyway_v16_redemptionRequestsTable_hasRequiredColumns() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            var cols = conn.getMetaData().getColumns(null, null, "redemption_requests", null);
            Set<String> columns = new HashSet<>();
            while (cols.next()) columns.add(cols.getString("COLUMN_NAME").toLowerCase());
            assertThat(columns).contains(
                    "id", "client_id", "user_id", "wallet_id", "catalog_item_id",
                    "amount", "currency_id", "wallet_type", "status", "processing_mode",
                    "category", "submitted_at", "deleted");
        }
    }

    @Test
    void flyway_v16_redemptionWebhookEventsTable_hasRequiredColumns() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            var cols = conn.getMetaData().getColumns(null, null, "redemption_webhook_events", null);
            Set<String> columns = new HashSet<>();
            while (cols.next()) columns.add(cols.getString("COLUMN_NAME").toLowerCase());
            assertThat(columns).contains(
                    "id", "client_id", "vendor", "redemption_request_id",
                    "idempotency_key", "payload", "status", "received_at");
        }
    }

    @Test
    void flyway_v16_tenantRedemptionSettings_hasMaxInFlightColumn() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            var cols = conn.getMetaData().getColumns(null, null, "tenant_redemption_settings", null);
            Set<String> columns = new HashSet<>();
            while (cols.next()) columns.add(cols.getString("COLUMN_NAME").toLowerCase());
            assertThat(columns).contains("max_in_flight_redemptions");
        }
    }

    // =========================================================================
    // Wallet reservation & ledger
    // =========================================================================

    @Test
    void submitPersonalRedemption_reservesWalletBalance_andCreatesReserveLedgerEntry() {
        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("100.00"), "cash", null);

        var response = submissionService.submitPersonalRedemption(req, testUser.getId());

        assertThat(response.id()).isNotNull();
        // NON_CASH INSTANT settles synchronously via the Xoxoday stub: reserve → debit → COMPLETED.
        assertThat(response.status()).isEqualTo(RedemptionStatus.COMPLETED.name());

        RewardWallet updated = walletRepository.findById(personalWallet.getId()).orElseThrow();
        assertThat(updated.getAvailableBalance()).isEqualByComparingTo("900.00");
        assertThat(updated.getReservedBalance()).isEqualByComparingTo("0.00");

        var ledger = ledgerEntryRepository.findByRewardWalletId(personalWallet.getId(), PageRequest.of(0, 10));
        assertThat(ledger.getContent())
                .extracting(e -> e.getEntryType())
                .containsExactlyInAnyOrder(LedgerEntryType.RESERVE, LedgerEntryType.DEBIT);
        var reserveEntry = ledger.getContent().stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.RESERVE).findFirst().orElseThrow();
        assertThat(reserveEntry.getAmount()).isEqualByComparingTo("100.00");
        assertThat(reserveEntry.getAvailableBalanceBefore()).isEqualByComparingTo("1000.00");
        assertThat(reserveEntry.getAvailableBalanceAfter()).isEqualByComparingTo("900.00");
        assertThat(reserveEntry.getReservedBalanceBefore()).isEqualByComparingTo("0.00");
        assertThat(reserveEntry.getReservedBalanceAfter()).isEqualByComparingTo("100.00");
    }

    @Test
    void submitPersonalRedemption_cashInstant_dispatchesAfterCommit_thenWebhookCompletes() {
        var req = new SubmitPersonalRedemptionRequest(
                cashInstantItem.getId(), personalWallet.getId(), new BigDecimal("100.00"), "cash", null);

        var response = submissionService.submitPersonalRedemption(req, testUser.getId());

        // CASH INSTANT starts PROCESSING (not synchronous COMPLETED); the AFTER_COMMIT listener dispatches to XTRM.
        assertThat(response.status()).isEqualTo(RedemptionStatus.PROCESSING.name());
        assertThat(response.processingMode()).isEqualTo(RedemptionProcessingMode.INSTANT.name());

        // After the after-commit dispatch (XTRM stub), the request is PROCESSING with a vendor ref — not yet debited.
        RedemptionRequest dispatched = redemptionRequestRepository.findById(response.id()).orElseThrow();
        assertThat(dispatched.getStatus()).isEqualTo(RedemptionStatus.PROCESSING);
        assertThat(dispatched.getCategory()).isEqualTo(RedemptionCategory.CASH);
        assertThat(dispatched.getVendorReferenceId()).isNotBlank();
        assertThat(dispatched.getDispatchAttemptedAt()).isNotNull();

        RewardWallet afterDispatch = walletRepository.findById(personalWallet.getId()).orElseThrow();
        assertThat(afterDispatch.getAvailableBalance()).isEqualByComparingTo("900.00");
        assertThat(afterDispatch.getReservedBalance()).isEqualByComparingTo("100.00");
        assertThat(ledgerEntryRepository.findByRewardWalletId(personalWallet.getId(), PageRequest.of(0, 10)).getContent())
                .as("dispatch reserves but does not debit — the debit happens on the webhook")
                .extracting(e -> e.getEntryType())
                .containsExactly(LedgerEntryType.RESERVE);

        // XTRM webhook confirms the payout → COMPLETED + DEBIT + reservation settled.
        RedemptionWebhookEvent event = webhookEventRepository.save(RedemptionWebhookEvent.builder()
                .clientId(testClient.getId())
                .vendor("xtrm")
                .redemptionRequestId(dispatched.getId())
                .idempotencyKey("wh-" + dispatched.getId())
                .payload("{}")
                .status(WebhookStatus.RECEIVED)
                .receivedAt(Instant.now())
                .build());
        webhookService.process(dispatched.getId(), event, true, null);

        RedemptionRequest completed = redemptionRequestRepository.findById(dispatched.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(RedemptionStatus.COMPLETED);
        RewardWallet settled = walletRepository.findById(personalWallet.getId()).orElseThrow();
        assertThat(settled.getReservedBalance()).isEqualByComparingTo("0.00");
        assertThat(ledgerEntryRepository.findByRewardWalletId(personalWallet.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(e -> e.getEntryType())
                .contains(LedgerEntryType.DEBIT);

        webhookEventRepository.delete(event); // keep teardown's request delete FK-safe
    }


    // =========================================================================
    // Business rules
    // =========================================================================

    @Test
    void submitPersonalRedemption_amountBelowMinimum_throwsBusinessRuleException() {
        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("10.00"), "cash", null);

        assertThatThrownBy(() -> submissionService.submitPersonalRedemption(req, testUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("minimum");
    }

    @Test
    void submitPersonalRedemption_insufficientBalance_throwsBusinessRuleException() {
        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("2000.00"), "cash", null);

        assertThatThrownBy(() -> submissionService.submitPersonalRedemption(req, testUser.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("balance");
    }

    @Test
    void submitPersonalRedemption_defaultInFlightCapOf10_throwsConflictOn11th() {
        for (int i = 0; i < 10; i++) {
            redemptionRequestRepository.save(
                    RedemptionRequestFixtures.inFlight(
                            testClient.getId(), testUser.getId(),
                            personalWallet.getId(), instantItem.getId()).build());
        }

        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);

        assertThatThrownBy(() -> submissionService.submitPersonalRedemption(req, testUser.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("in-flight");
    }

    @Test
    void submitPersonalRedemption_configurableInFlightCap_throwsWhenExceeded() {
        settingsRepository.save(TenantRedemptionSettingsFixtures.defaultSettings(testClient.getId())
                .maxInFlightRedemptions(2)
                .build());

        for (int i = 0; i < 2; i++) {
            redemptionRequestRepository.save(
                    RedemptionRequestFixtures.inFlight(
                            testClient.getId(), testUser.getId(),
                            personalWallet.getId(), instantItem.getId()).build());
        }

        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);

        assertThatThrownBy(() -> submissionService.submitPersonalRedemption(req, testUser.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("in-flight");
    }

    // =========================================================================
    // Batch date computation
    // =========================================================================

    @Test
    void submitPersonalRedemption_batchMode_dailyCadence_scheduledBatchDateIsTomorrow() {
        settingsRepository.save(TenantRedemptionSettingsFixtures.dailySettings(testClient.getId()).build());

        var req = new SubmitPersonalRedemptionRequest(
                batchItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);
        var response = submissionService.submitPersonalRedemption(req, testUser.getId());

        var saved = redemptionRequestRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getScheduledBatchDate()).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(saved.getProcessingMode()).isEqualTo(RedemptionProcessingMode.BATCH);
    }

    @Test
    void submitPersonalRedemption_batchMode_weeklyCadence_scheduledBatchDateIsNextMonday() {
        settingsRepository.save(TenantRedemptionSettingsFixtures.weeklySettings(testClient.getId()).build());

        var req = new SubmitPersonalRedemptionRequest(
                batchItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);
        var response = submissionService.submitPersonalRedemption(req, testUser.getId());

        var saved = redemptionRequestRepository.findById(response.id()).orElseThrow();
        LocalDate expectedDate = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);
        assertThat(saved.getScheduledBatchDate()).isEqualTo(expectedDate);
    }

    // =========================================================================
    // Tenant isolation
    // =========================================================================

    @Test
    void getRedemptionById_crossTenantLookup_throwsResourceNotFoundException() {
        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);
        var response = submissionService.submitPersonalRedemption(req, testUser.getId());

        TenantContext.setClientId(otherClient.getId());
        setSecurityContext(otherUser, "PARTNER_SELLER");

        assertThatThrownBy(() -> submissionService.getRedemptionById(response.id(), otherUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        TenantContext.setClientId(testClient.getId());
        setSecurityContext(testUser, "PARTNER_SELLER");
    }

    @Test
    void listRedemptions_tenantIsolation_eachUserSeesOnlyOwnData() {
        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);
        submissionService.submitPersonalRedemption(req, testUser.getId());

        var myPage = submissionService.getPersonalRedemptions(testUser.getId(), PageRequest.of(0, 10));
        assertThat(myPage.getTotalElements()).isGreaterThanOrEqualTo(1);

        TenantContext.setClientId(otherClient.getId());
        setSecurityContext(otherUser, "PARTNER_SELLER");
        var otherPage = submissionService.getPersonalRedemptions(otherUser.getId(), PageRequest.of(0, 10));
        assertThat(otherPage.getTotalElements()).isZero();

        TenantContext.setClientId(testClient.getId());
        setSecurityContext(testUser, "PARTNER_SELLER");
    }

    // =========================================================================
    // Response shape (contract conformance — service layer)
    // =========================================================================

    @Test
    void submitPersonalRedemption_confirmationResponse_hasRequiredFields() {
        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);
        var response = submissionService.submitPersonalRedemption(req, testUser.getId());

        assertThat(response.id()).isNotNull();
        // NON_CASH INSTANT completes synchronously (Xoxoday stub). NOTE: the confirmation response can thus
        // carry COMPLETED, which is outside the contract's documented submit-status enum — pre-existing.
        assertThat(response.status()).isEqualTo(RedemptionStatus.COMPLETED.name());
        assertThat(response.processingMode()).isEqualTo(RedemptionProcessingMode.INSTANT.name());
        assertThat(response.submittedAt()).isNotNull();
        assertThat(response.estimatedDelivery()).isNotBlank();
        assertThat(response.scheduledBatchDate()).isNull();
    }

    @Test
    void getRedemptionById_detailResponse_includesCatalogItemName() {
        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);
        var confirmation = submissionService.submitPersonalRedemption(req, testUser.getId());

        var detail = submissionService.getRedemptionById(confirmation.id(), testUser.getId());

        assertThat(detail.id()).isEqualTo(confirmation.id());
        assertThat(detail.catalogItemName()).isEqualTo(instantItem.getName());
        assertThat(detail.status()).isEqualTo(RedemptionStatus.COMPLETED.name());
        assertThat(detail.amount()).isEqualByComparingTo("50.00");
        assertThat(detail.currencyId()).isEqualTo("cash");
        assertThat(detail.submittedAt()).isNotNull();
    }

    @Test
    void listRedemptions_returnsPaginatedResults() {
        var req = new SubmitPersonalRedemptionRequest(
                instantItem.getId(), personalWallet.getId(), new BigDecimal("50.00"), "cash", null);
        submissionService.submitPersonalRedemption(req, testUser.getId());

        var page = submissionService.getPersonalRedemptions(testUser.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.getContent()).isNotEmpty();
        var item = page.getContent().get(0);
        assertThat(item.id()).isNotNull();
        assertThat(item.status()).isNotBlank();
        assertThat(item.amount()).isNotNull();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
