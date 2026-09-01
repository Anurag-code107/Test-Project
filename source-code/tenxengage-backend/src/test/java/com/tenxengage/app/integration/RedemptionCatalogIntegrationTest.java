package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.UpsertClientCatalogItemConfigRequest;
import com.tenxengage.app.dto.request.UpsertRegionConfigRequest;
import com.tenxengage.app.dto.request.UpdateTenantRedemptionSettingsRequest;
import com.tenxengage.app.dto.response.CatalogBrowseItemResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.ClientCatalogRegionConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RewardBalance;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RewardBalanceRepository;
import com.tenxengage.app.repository.TenantRedemptionSettingsRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.RedemptionCatalogAdminService;
import com.tenxengage.app.service.RedemptionCatalogBrowseService;
import com.tenxengage.app.service.TenantRedemptionCatalogService;
import com.tenxengage.app.testdata.ClientFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Tag("integration")
class RedemptionCatalogIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionCatalogAdminService adminService;
    @Autowired private TenantRedemptionCatalogService tenantCatalogService;
    @Autowired private RedemptionCatalogBrowseService browseService;

    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientCatalogItemConfigRepository configRepository;
    @Autowired private ClientCatalogRegionConfigRepository regionConfigRepository;
    @Autowired private RewardBalanceRepository balanceRepository;
    @Autowired private TenantRedemptionSettingsRepository settingsRepository;

    private Client testClient;
    private User testUser;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        testUser = userRepository.save(User.builder()
                .email("catalog-it-" + UUID.randomUUID() + "@test.com")
                .firstName("Test").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(testClient.getId())
                .build());
        TenantContext.setClientId(testClient.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(testUser), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ─── IT-01 ─────────────────────────────────────────────────────────────────

    @Test
    void it01_fullActivationFlow_createEnableBrowse() {
        RedemptionCatalogItem item = catalogItemRepository.save(activeNonCashItem("AMZN-001", "USD"));
        adminService.activateCatalogItem(item.getId());
        enableForTenant(item.getId(), testClient.getId());
        balanceRepository.save(balance(testClient.getId(), testUser.getId(), "USD", "200.00"));

        Page<CatalogBrowseItemResponse> page = browseService.browsePartnerCatalog(
                "USD", null, PageRequest.of(0, 20));

        assertThat(page.getContent()).anyMatch(r -> r.id().equals(item.getId()));
    }

    // ─── IT-02 ─────────────────────────────────────────────────────────────────

    @Test
    void it02_deactivationPropagation_itemHiddenFromBrowse() {
        RedemptionCatalogItem item = catalogItemRepository.save(activeNonCashItem("AMZN-002", "USD"));
        adminService.activateCatalogItem(item.getId());
        enableForTenant(item.getId(), testClient.getId());
        balanceRepository.save(balance(testClient.getId(), testUser.getId(), "USD", "200.00"));

        assertThat(browseService.browsePartnerCatalog("USD", null, PageRequest.of(0, 20)).getContent())
                .anyMatch(r -> r.id().equals(item.getId()));

        adminService.deactivateCatalogItem(item.getId());

        assertThat(browseService.browsePartnerCatalog("USD", null, PageRequest.of(0, 20)).getContent())
                .noneMatch(r -> r.id().equals(item.getId()));
    }

    // ─── IT-03 ─────────────────────────────────────────────────────────────────

    @Test
    void it03_processingModeOverride_partnerSeesEffectiveBatchMode() {
        RedemptionCatalogItem item = catalogItemRepository.save(
                RedemptionCatalogItem.builder()
                        .name("Instant Gift Card")
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                        .geographicScope(new String[]{"US"})
                        .providerItemId("BATCH-001")
                        .isActive(true)
                        .build());

        configRepository.save(ClientCatalogItemConfig.builder()
                .clientId(testClient.getId())
                .redemptionCatalogItemId(item.getId())
                .enabled(true)
                .processingModeOverride(RedemptionProcessingMode.BATCH)
                .build());
        settingsRepository.save(
                com.tenxengage.app.entity.TenantRedemptionSettings.builder()
                        .clientId(testClient.getId())
                        .batchCadence(BatchCadence.WEEKLY)
                        .build());
        balanceRepository.save(balance(testClient.getId(), testUser.getId(), "USD", "200.00"));

        Page<CatalogBrowseItemResponse> page = browseService.browsePartnerCatalog(
                "USD", null, PageRequest.of(0, 20));

        CatalogBrowseItemResponse response = page.getContent().stream()
                .filter(r -> r.id().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item not found in catalog"));

        assertThat(response.effectiveProcessingMode()).isEqualTo(RedemptionProcessingMode.BATCH);
        assertThat(response.estimatedPayoutTimeline()).contains("7 days");
    }

    // ─── IT-04 ─────────────────────────────────────────────────────────────────

    @Test
    void it04_shortfallIndicator_canAffordFalseWhenBalanceBelowMinWallet() {
        RedemptionCatalogItem item = catalogItemRepository.save(
                RedemptionCatalogItem.builder()
                        .name("Bank Transfer")
                        .category(RedemptionCategory.CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(new BigDecimal("10.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.BATCH)
                        .geographicScope(new String[]{"US"})
                        .isActive(true)
                        .build());

        configRepository.save(ClientCatalogItemConfig.builder()
                .clientId(testClient.getId())
                .redemptionCatalogItemId(item.getId())
                .enabled(true)
                .minWalletBalanceOverride(new BigDecimal("100.00"))
                .build());
        balanceRepository.save(balance(testClient.getId(), testUser.getId(), "USD", "50.00"));

        Page<CatalogBrowseItemResponse> page = browseService.browsePartnerCatalog(
                "USD", null, PageRequest.of(0, 20));

        CatalogBrowseItemResponse response = page.getContent().stream()
                .filter(r -> r.id().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item not found in catalog"));

        assertThat(response.canAfford()).isFalse();
        assertThat(response.shortfallAmount()).isEqualByComparingTo("50.00");
    }

    // ─── IT-05 ─────────────────────────────────────────────────────────────────

    @Test
    void it05_emptyCatalog_browseReturnsEmptyNotError() {
        catalogItemRepository.save(activeNonCashItem("NO-CONFIG", "USD"));
        balanceRepository.save(balance(testClient.getId(), testUser.getId(), "USD", "100.00"));
        // No ClientCatalogItemConfig saved → item absent from browse

        Page<CatalogBrowseItemResponse> page = browseService.browsePartnerCatalog(
                "USD", null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ─── IT-06 ─────────────────────────────────────────────────────────────────

    @Test
    void it06_regionalRestriction_fallbackAndExplicitDisable() {
        RedemptionCatalogItem item = catalogItemRepository.save(
                RedemptionCatalogItem.builder()
                        .name("GB/US Gift Card")
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                        .geographicScope(new String[]{"US", "GB"})
                        .providerItemId("GBITEM-001")
                        .isActive(true)
                        .build());

        enableForTenant(item.getId(), testClient.getId());
        regionConfigRepository.save(ClientCatalogRegionConfig.builder()
                .clientId(testClient.getId())
                .redemptionCatalogItemId(item.getId())
                .regionCode("US")
                .enabled(true)
                .build());
        balanceRepository.save(balance(testClient.getId(), testUser.getId(), "USD", "100.00"));

        // Step 3: GB has no regional row → fallback to tenant-level enabled=true → visible
        Page<CatalogBrowseItemResponse> withFallback = browseService.browsePartnerCatalog(
                "USD", "GB", PageRequest.of(0, 20));
        assertThat(withFallback.getContent()).anyMatch(r -> r.id().equals(item.getId()));

        // Step 4-5: Add GB regional config with enabled=false → item absent
        tenantCatalogService.upsertRegionConfig(item.getId(), "GB",
                new UpsertRegionConfigRequest(false));

        Page<CatalogBrowseItemResponse> afterExplicitDisable = browseService.browsePartnerCatalog(
                "USD", "GB", PageRequest.of(0, 20));
        assertThat(afterExplicitDisable.getContent()).noneMatch(r -> r.id().equals(item.getId()));
    }

    // ─── IT-07 ─────────────────────────────────────────────────────────────────

    @Test
    void it07_noRegionalOverride_itemVisibleFromAnyRegion() {
        RedemptionCatalogItem item = catalogItemRepository.save(
                RedemptionCatalogItem.builder()
                        .name("Global Gift Card")
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                        .geographicScope(new String[]{"US", "GB", "IN"})
                        .providerItemId("GLOBAL-001")
                        .isActive(true)
                        .build());

        enableForTenant(item.getId(), testClient.getId());
        balanceRepository.save(balance(testClient.getId(), testUser.getId(), "USD", "100.00"));

        assertThat(browseService.browsePartnerCatalog("USD", "IN", PageRequest.of(0, 20)).getContent())
                .anyMatch(r -> r.id().equals(item.getId()));
        assertThat(browseService.browsePartnerCatalog("USD", "GB", PageRequest.of(0, 20)).getContent())
                .anyMatch(r -> r.id().equals(item.getId()));
    }

    // ─── IT-08 ─────────────────────────────────────────────────────────────────

    @Test
    void it08_geographicScopeViolation_rejectsRegionOutsideScope() {
        RedemptionCatalogItem item = catalogItemRepository.save(
                RedemptionCatalogItem.builder()
                        .name("US Only Card")
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                        .geographicScope(new String[]{"US"})
                        .providerItemId("US-ONLY-001")
                        .isActive(true)
                        .build());

        assertThatThrownBy(() -> tenantCatalogService.upsertRegionConfig(
                item.getId(), "DE", new UpsertRegionConfigRequest(true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Region DE is not supported");

        assertThat(regionConfigRepository
                .findByClientIdAndRedemptionCatalogItemIdAndRegionCode(
                        testClient.getId(), item.getId(), "DE"))
                .isEmpty();
    }

    // ─── IT-13 ────────────────────────────────────────────────────────────────

    @Test
    void it13_batchCadenceUpdate_payoutTimelineReflectsNewCadence() {
        RedemptionCatalogItem item = catalogItemRepository.save(
                RedemptionCatalogItem.builder()
                        .name("Batch Gift Card")
                        .category(RedemptionCategory.NON_CASH)
                        .currencyId("USD")
                        .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                        .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                        .geographicScope(new String[]{"US"})
                        .providerItemId("BATCH-CAD-001")
                        .isActive(true)
                        .build());

        configRepository.save(ClientCatalogItemConfig.builder()
                .clientId(testClient.getId())
                .redemptionCatalogItemId(item.getId())
                .enabled(true)
                .processingModeOverride(RedemptionProcessingMode.BATCH)
                .build());
        balanceRepository.save(balance(testClient.getId(), testUser.getId(), "USD", "100.00"));

        // Set DAILY first
        tenantCatalogService.updateTenantSettings(
                new UpdateTenantRedemptionSettingsRequest(BatchCadence.DAILY, null));

        String dailyTimeline = browseService.browsePartnerCatalog("USD", null, PageRequest.of(0, 20))
                .getContent().stream()
                .filter(r -> r.id().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item not found"))
                .estimatedPayoutTimeline();

        assertThat(dailyTimeline).contains("Tomorrow");

        // Update to WEEKLY
        tenantCatalogService.updateTenantSettings(
                new UpdateTenantRedemptionSettingsRequest(BatchCadence.WEEKLY, null));

        String weeklyTimeline = browseService.browsePartnerCatalog("USD", null, PageRequest.of(0, 20))
                .getContent().stream()
                .filter(r -> r.id().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item not found after weekly update"))
                .estimatedPayoutTimeline();

        assertThat(weeklyTimeline).contains("7 days");
        assertThat(weeklyTimeline).isNotEqualTo(dailyTimeline);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private RedemptionCatalogItem activeNonCashItem(String providerItemId, String currency) {
        return RedemptionCatalogItem.builder()
                .name("Gift Card " + providerItemId)
                .category(RedemptionCategory.NON_CASH)
                .currencyId(currency)
                .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .geographicScope(new String[]{"US", "GB", "IN"})
                .providerItemId(providerItemId)
                .isActive(false)
                .build();
    }

    private void enableForTenant(UUID itemId, UUID clientId) {
        configRepository.save(ClientCatalogItemConfig.builder()
                .clientId(clientId)
                .redemptionCatalogItemId(itemId)
                .enabled(true)
                .build());
    }

    private RewardBalance balance(UUID clientId, UUID userId, String currency, String amount) {
        return RewardBalance.builder()
                .clientId(clientId)
                .userId(userId)
                .currencyId(currency)
                .balance(new BigDecimal(amount))
                .build();
    }
}
