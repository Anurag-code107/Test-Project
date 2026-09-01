package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RewardBalance;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RewardBalanceRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.RedemptionCatalogBrowseService;
import com.tenxengage.app.service.XoxodaySyncJobService;
import com.tenxengage.app.testdata.ClientFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@Tag("integration")
class XoxodaySyncIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private XoxodaySyncJobService syncJobService;
    @Autowired private RedemptionCatalogBrowseService browseService;

    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientCatalogItemConfigRepository configRepository;
    @Autowired private RewardBalanceRepository balanceRepository;

    private Client testClient;
    private User testUser;

    @BeforeEach
    void setUp() {
        testClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        testUser = userRepository.save(User.builder()
                .email("sync-it-" + UUID.randomUUID() + "@test.com")
                .firstName("Sync").lastName("Tester")
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

    // ─── IT-11 ─────────────────────────────────────────────────────────────────

    @Test
    void it11_xoxodaySync_deactivatesItemAbsentFromApiResponse() {
        // XoxodayApiClientStub always returns List.of() — any active NON_CASH item
        // with a providerItemId gets auto-deactivated by runSync()
        RedemptionCatalogItem item = catalogItemRepository.save(RedemptionCatalogItem.builder()
                .name("Xoxoday Gift Card")
                .category(RedemptionCategory.NON_CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .geographicScope(new String[]{"US"})
                .providerItemId("xox-123")
                .isActive(true)
                .build());

        ClientCatalogItemConfig config = configRepository.save(ClientCatalogItemConfig.builder()
                .clientId(testClient.getId())
                .redemptionCatalogItemId(item.getId())
                .enabled(true)
                .build());
        balanceRepository.save(RewardBalance.builder()
                .clientId(testClient.getId())
                .userId(testUser.getId())
                .currencyId("USD")
                .balance(new BigDecimal("100.00"))
                .build());

        // Pre-condition: item visible in browse
        assertThat(browseService.browsePartnerCatalog("USD", null, PageRequest.of(0, 20)).getContent())
                .anyMatch(r -> r.id().equals(item.getId()));

        // Run sync — stub returns empty → item absent → deactivated
        syncJobService.runSync();

        // IT-11 assertion 1: item deactivated
        RedemptionCatalogItem loaded = catalogItemRepository.findById(item.getId()).orElseThrow();
        assertThat(loaded.isActive()).isFalse();
        assertThat(loaded.getXoxodayLastSyncedAt()).isNotNull();

        // IT-11 assertion 2: ClientCatalogItemConfig.enabled preserved (NOT touched by sync)
        ClientCatalogItemConfig loadedConfig = configRepository.findById(config.getId()).orElseThrow();
        assertThat(loadedConfig.isEnabled()).isTrue();

        // IT-11 assertion 3: browse excludes deactivated item
        assertThat(browseService.browsePartnerCatalog("USD", null, PageRequest.of(0, 20)).getContent())
                .noneMatch(r -> r.id().equals(item.getId()));
    }

    // ─── IT-12 ─────────────────────────────────────────────────────────────────

    @Test
    void it12_syncTransientFailure_itemsNotDeactivated() {
        RedemptionCatalogItem item = catalogItemRepository.save(RedemptionCatalogItem.builder()
                .name("Surviving Gift Card")
                .category(RedemptionCategory.NON_CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .geographicScope(new String[]{"US"})
                .providerItemId("xox-survive")
                .isActive(true)
                .build());

        // Simulate all retries exhausted → recovery handler called directly
        syncJobService.handleSyncFailure(new RuntimeException("Simulated API timeout"));

        // IT-12 assertion: item stays active after failure recovery
        RedemptionCatalogItem loaded = catalogItemRepository.findById(item.getId()).orElseThrow();
        assertThat(loaded.isActive()).isTrue();

        // Sync status records the failure
        assertThat(syncJobService.getSyncStatus()).isEqualTo("FAILED");
        assertThat(syncJobService.getFailedSyncCount()).isGreaterThanOrEqualTo(1);
    }
}
