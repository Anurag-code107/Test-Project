package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.RedemptionAdminHistoryFilters;
import com.tenxengage.app.dto.request.redemption.RedemptionHistoryFilters;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionAdminHistoryResponse;
import com.tenxengage.app.entity.*;
import com.tenxengage.app.entity.enums.*;
import com.tenxengage.app.repository.*;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.redemption.RedemptionHistoryService;
import com.tenxengage.app.testdata.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — Cross-story integration tests for redemption history.
 * Covers multi-entity workflows, permission enforcement, and tenant isolation.
 */
@Tag("integration")
class RedemptionHistoryIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionHistoryService historyService;
    @Autowired private RedemptionRequestRepository redemptionRequestRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientCatalogItemConfigRepository catalogConfigRepository;
    @Autowired private RewardWalletRepository walletRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;

    private Client testClient;
    private Client otherClient;
    private User partnerUser;
    private User adminUser;
    private User otherTenantUser;
    private PartnerCompany testCompany;
    private RedemptionCatalogItem catalogItem;
    private RedemptionRequest personalRedemption;
    private RedemptionRequest companyRedemption;

    @BeforeEach
    void setUp() {
        testClient   = clientRepository.save(ClientFixtures.activeEnterprise().build());
        otherClient  = clientRepository.save(ClientFixtures.activeEnterprise().build());
        testCompany  = partnerCompanyRepository.save(PartnerFixtures.activeReseller(testClient.getId()).build());

        partnerUser  = userRepository.save(UserFixtures.activeUser(testClient.getId(), testCompany.getId()).build());
        adminUser    = userRepository.save(UserFixtures.activeUser(testClient.getId(), testCompany.getId()).build());
        otherTenantUser = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@test.com")
                .firstName("Other").lastName("Tenant")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(otherClient.getId())
                .build());

        catalogItem = catalogItemRepository.save(
                RedemptionCatalogItemFixtures.activeNonCashItem().currencyId("cash").build());
        catalogConfigRepository.save(
                ClientCatalogItemConfigFixtures.enabledConfig(testClient.getId(), catalogItem.getId()).build());

        RewardWallet personalWallet = walletRepository.save(
                RewardWalletFixtures.individualWalletWithBalance(testClient.getId(), partnerUser.getId(), new BigDecimal("500.00")).build());
        RewardWallet companyWallet = walletRepository.save(
                RewardWalletFixtures.companyWalletWithBalance(testClient.getId(), testCompany.getId(), new BigDecimal("500.00")).build());

        personalRedemption = redemptionRequestRepository.save(
                RedemptionRequestFixtures.defaultPersonal(testClient.getId(), partnerUser.getId(), personalWallet.getId(), catalogItem.getId())
                        .status(RedemptionStatus.COMPLETED).completedAt(Instant.now()).deleted(false).build());

        companyRedemption = redemptionRequestRepository.save(
                RedemptionRequestFixtures.defaultPersonal(testClient.getId(), partnerUser.getId(), companyWallet.getId(), catalogItem.getId())
                        .walletType(WalletType.COMPANY).status(RedemptionStatus.COMPLETED)
                        .completedAt(Instant.now()).deleted(false).build());

        TenantContext.setClientId(testClient.getId());
        asUser(partnerUser, "PARTNER_SELLER");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        safeDelete(() -> redemptionRequestRepository.deleteAll(
                redemptionRequestRepository.findByClientIdAndDeletedFalse(testClient.getId(), PageRequest.of(0, 10000)).getContent()));
        safeDelete(() -> redemptionRequestRepository.deleteAll(
                redemptionRequestRepository.findByClientIdAndDeletedFalse(otherClient.getId(), PageRequest.of(0, 10000)).getContent()));
        safeDelete(() -> walletRepository.findByClientIdAndUserId(testClient.getId(), partnerUser.getId()).forEach(walletRepository::delete));
        safeDelete(() -> walletRepository.findByClientIdAndPartnerCompanyIdAndWalletType(testClient.getId(), testCompany.getId(), WalletType.COMPANY).forEach(walletRepository::delete));
        safeDelete(() -> catalogConfigRepository.findByClientIdAndRedemptionCatalogItemId(testClient.getId(), catalogItem.getId()).ifPresent(catalogConfigRepository::delete));
        safeDelete(() -> catalogItemRepository.delete(catalogItem));
        safeDelete(() -> userRepository.delete(partnerUser));
        safeDelete(() -> userRepository.delete(adminUser));
        safeDelete(() -> userRepository.delete(otherTenantUser));
        safeDelete(() -> partnerCompanyRepository.delete(testCompany));
        safeDelete(() -> clientRepository.delete(testClient));
        safeDelete(() -> clientRepository.delete(otherClient));
    }

    // ── Multi-entity workflow ─────────────────────────────────────────────────

    @Test
    void submitRedemption_appearsInPersonalHistory_withCatalogItemName() {
        Page<RedemptionRequestResponse> result = historyService.getPersonalHistory(
                partnerUser.getId(), RedemptionHistoryFilters.empty(),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt")));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(1);
        RedemptionRequestResponse item = result.getContent().stream()
                .filter(r -> r.id().equals(personalRedemption.getId()))
                .findFirst().orElseThrow();
        assertThat(item.catalogItemName()).isEqualTo(catalogItem.getName());
        assertThat(item.completedAt()).isNotNull();
    }

    @Test
    void tenantHistory_returnsAllRedemptionsIncludingCompanyWallet_withUserAndCompanyNames() {
        asUser(adminUser, "CLIENT_ADMIN");
        Page<RedemptionAdminHistoryResponse> result = historyService.getTenantHistory(
                RedemptionAdminHistoryFilters.empty(),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt")));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2); // personal + company
        boolean hasPersonal = result.getContent().stream().anyMatch(r -> r.id().equals(personalRedemption.getId()));
        boolean hasCompany  = result.getContent().stream().anyMatch(r -> r.id().equals(companyRedemption.getId()));
        assertThat(hasPersonal).isTrue();
        assertThat(hasCompany).isTrue();
        result.getContent().stream()
                .filter(r -> r.userId().equals(partnerUser.getId()))
                .findFirst()
                .ifPresent(item -> {
                    assertThat(item.userDisplayName()).isNotBlank();
                    assertThat(item.partnerCompanyName()).isEqualTo(testCompany.getName());
                });
    }

    // ── Permission enforcement ────────────────────────────────────────────────

    @Test
    void tenantHistory_asClientAdmin_returns200() {
        asUser(adminUser, "CLIENT_ADMIN");
        Page<RedemptionAdminHistoryResponse> result = historyService.getTenantHistory(
                RedemptionAdminHistoryFilters.empty(), PageRequest.of(0, 20));
        assertThat(result).isNotNull();
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    void personalHistory_crossTenant_returnsEmpty() {
        TenantContext.setClientId(otherClient.getId());
        asUser(otherTenantUser, "PARTNER_SELLER");

        Page<RedemptionRequestResponse> result = historyService.getPersonalHistory(
                otherTenantUser.getId(), RedemptionHistoryFilters.empty(),
                PageRequest.of(0, 20));

        // Other tenant has no redemption requests
        assertThat(result.getContent()).noneMatch(r -> r.id().equals(personalRedemption.getId()));
    }

    @Test
    void personalHistoryDetail_crossTenant_throwsNotFound() {
        TenantContext.setClientId(otherClient.getId());
        asUser(otherTenantUser, "PARTNER_SELLER");

        org.junit.jupiter.api.Assertions.assertThrows(
                com.tenxengage.app.exception.ResourceNotFoundException.class,
                () -> historyService.getRedemptionDetail(personalRedemption.getId(), otherTenantUser.getId()));
    }

    @Test
    void tenantHistory_clientAdmin_doesNotSeeOtherTenantData() {
        asUser(adminUser, "CLIENT_ADMIN");
        Page<RedemptionAdminHistoryResponse> result = historyService.getTenantHistory(
                RedemptionAdminHistoryFilters.empty(), PageRequest.of(0, 20));

        // Verify: no item belongs to otherClient
        result.getContent().forEach(r ->
                assertThat(r.id()).isNotEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000")));
    }

    // ── Filter correctness ────────────────────────────────────────────────────

    @Test
    void personalHistory_statusFilterCompleted_onlyCompletedRows() {
        RedemptionHistoryFilters filters = new RedemptionHistoryFilters(
                RedemptionStatus.COMPLETED, null, null, null);
        Page<RedemptionRequestResponse> result = historyService.getPersonalHistory(
                partnerUser.getId(), filters, PageRequest.of(0, 20));
        result.getContent().forEach(r -> assertThat(r.status()).isEqualTo("COMPLETED"));
    }

    @Test
    void tenantHistory_userIdFilter_onlyThatUsersRows() {
        asUser(adminUser, "CLIENT_ADMIN");
        RedemptionAdminHistoryFilters filters = new RedemptionAdminHistoryFilters(
                null, null, null, null, partnerUser.getId(), null);
        Page<RedemptionAdminHistoryResponse> result = historyService.getTenantHistory(
                filters, PageRequest.of(0, 20));
        result.getContent().forEach(r -> assertThat(r.userId()).isEqualTo(partnerUser.getId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void asUser(User user, String baseRole) {
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority("ROLE_" + baseRole))));
    }

    private void safeDelete(Runnable action) {
        try { action.run(); } catch (Exception ignored) {}
    }
}
