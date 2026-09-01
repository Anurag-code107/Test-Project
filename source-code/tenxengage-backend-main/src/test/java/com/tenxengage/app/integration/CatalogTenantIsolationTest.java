package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.UpsertClientCatalogItemConfigRequest;
import com.tenxengage.app.dto.request.UpsertRegionConfigRequest;
import com.tenxengage.app.dto.response.ClientCatalogRegionConfigResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientCatalogRegionConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RewardBalance;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RewardBalanceRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.RedemptionCatalogBrowseService;
import com.tenxengage.app.service.TenantRedemptionCatalogService;
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
class CatalogTenantIsolationTest extends AbstractLocalIntegrationTest {

    @Autowired private TenantRedemptionCatalogService tenantCatalogService;
    @Autowired private RedemptionCatalogBrowseService browseService;

    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientCatalogRegionConfigRepository regionConfigRepository;
    @Autowired private RewardBalanceRepository balanceRepository;

    private Client clientA;
    private Client clientB;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        clientA = clientRepository.save(ClientFixtures.activeEnterprise().build());
        clientB = clientRepository.save(ClientFixtures.activeEnterprise().build());

        userA = userRepository.save(User.builder()
                .email("tenant-a-" + UUID.randomUUID() + "@test.com")
                .firstName("A").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientA.getId())
                .build());
        userB = userRepository.save(User.builder()
                .email("tenant-b-" + UUID.randomUUID() + "@test.com")
                .firstName("B").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientB.getId())
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ─── IT-09 ─────────────────────────────────────────────────────────────────

    @Test
    void it09_tenantIsolation_clientBCatalogConfigInvisibleToClientB() {
        RedemptionCatalogItem item = catalogItemRepository.save(globalActiveItem("ISO-001"));

        // ClientA enables the item and saves a balance
        withTenant(clientA.getId(), userA);
        tenantCatalogService.upsertItemConfig(item.getId(),
                new UpsertClientCatalogItemConfigRequest(true, RedemptionProcessingMode.BATCH,
                        null, null, null));
        balanceRepository.save(balance(clientA.getId(), userA.getId(), "USD", "100.00"));

        // ClientA browse → sees the item
        assertThat(browseService.browsePartnerCatalog("USD", null, PageRequest.of(0, 20)).getContent())
                .anyMatch(r -> r.id().equals(item.getId()));

        // Switch to ClientB context
        withTenant(clientB.getId(), userB);
        balanceRepository.save(balance(clientB.getId(), userB.getId(), "USD", "100.00"));

        // ClientB browse → item absent (no config for clientB)
        assertThat(browseService.browsePartnerCatalog("USD", null, PageRequest.of(0, 20)).getContent())
                .noneMatch(r -> r.id().equals(item.getId()));

        // ClientB's config list → empty (or item with enabled=false)
        boolean clientBHasEnabledConfig = tenantCatalogService
                .getTenantCatalog(null, null, null, PageRequest.of(0, 50))
                .getContent()
                .stream()
                .anyMatch(c -> c.id().equals(item.getId()) && c.enabled());
        assertThat(clientBHasEnabledConfig).isFalse();
    }

    // ─── IT-10 ─────────────────────────────────────────────────────────────────

    @Test
    void it10_tenantIsolation_regionalConfigScopedToTenant() {
        RedemptionCatalogItem item = catalogItemRepository.save(globalActiveItem("REG-ISO-001"));

        // ClientA adds a regional config for US with enabled=false
        withTenant(clientA.getId(), userA);
        regionConfigRepository.save(ClientCatalogRegionConfig.builder()
                .clientId(clientA.getId())
                .redemptionCatalogItemId(item.getId())
                .regionCode("US")
                .enabled(false)
                .build());

        // Switch to ClientB context
        withTenant(clientB.getId(), userB);

        // ClientB queries regional configs for same item → empty
        List<ClientCatalogRegionConfigResponse> clientBRegions =
                tenantCatalogService.getRegionalConfigs(item.getId());

        assertThat(clientBRegions).isEmpty();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void withTenant(UUID clientId, User user) {
        TenantContext.setClientId(clientId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(user), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private RedemptionCatalogItem globalActiveItem(String providerItemId) {
        return RedemptionCatalogItem.builder()
                .name("Global Item " + providerItemId)
                .category(RedemptionCategory.NON_CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .geographicScope(new String[]{"US", "GB", "IN"})
                .providerItemId(providerItemId)
                .isActive(true)
                .build();
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
