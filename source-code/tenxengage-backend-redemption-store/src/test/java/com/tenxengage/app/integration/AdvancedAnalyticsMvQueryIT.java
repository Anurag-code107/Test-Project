package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.AdvancedAnalyticsFilter;
import com.tenxengage.app.dto.response.redemption.FailureBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.ItemBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionTrendResponse;
import com.tenxengage.app.dto.response.redemption.SegmentBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.TimeToFirstRedemptionResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.ClientRoleRepository;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyLocationRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.redemption.RedemptionAdvancedAnalyticsService;
import com.tenxengage.app.testdata.ClientCatalogItemConfigFixtures;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.PartnerFixtures;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import com.tenxengage.app.testdata.RedemptionRequestFixtures;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import com.tenxengage.app.testdata.UserFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests that verify the five analytics materialized views are correctly
 * populated from source tables and that the service layer reads/aggregates them
 * accurately.  Each test:
 * <ol>
 *   <li>Seeds source tables (users, partner_companies, locations, client_roles,
 *       redemption_catalog_items, redemption_requests)</li>
 *   <li>Issues {@code REFRESH MATERIALIZED VIEW} for each MV</li>
 *   <li>Calls the service and asserts on the returned DTOs</li>
 * </ol>
 *
 * <p>A separate tenant-isolation case seeds two clients and verifies that querying
 * as tenant A never returns tenant B's rows.
 */
@Tag("integration")
class AdvancedAnalyticsMvQueryIT extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionAdvancedAnalyticsService analyticsService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private LocationLevelRepository locationLevelRepository;
    @Autowired private LocationValueRepository locationValueRepository;
    @Autowired private PartnerCompanyLocationRepository partnerCompanyLocationRepository;
    @Autowired private ClientRoleRepository clientRoleRepository;
    @Autowired private RedemptionCatalogItemRepository catalogItemRepository;
    @Autowired private ClientCatalogItemConfigRepository catalogConfigRepository;
    @Autowired private RewardWalletRepository walletRepository;
    @Autowired private RedemptionRequestRepository redemptionRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // Per-test fixtures
    private Client client;
    private User testUser;
    private PartnerCompany company;
    private LocationLevel regionLevel;
    private LocationValue regionValue;
    private ClientRole role;
    private RedemptionCatalogItem catalogItem;
    private RewardWallet wallet;
    private RedemptionRequest request;

    @BeforeEach
    void setUp() {
        // MODE_INHERITABLETHREADLOCAL lets the async executor thread inherit a copy of the
        // SecurityContext so that any @Async service code can resolve the actor.
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

        client = clientRepository.save(ClientFixtures.activeEnterprise().build());

        // ── Location hierarchy (depth-0 = region) ────────────────────────────
        // v_user_region resolves region by joining partner_company_locations
        // → location_values → location_levels WHERE depth = 0.
        regionLevel = locationLevelRepository.save(LocationLevel.builder()
                .clientId(client.getId())
                .name("Region")
                .depth(0)
                .build());
        regionValue = locationValueRepository.save(LocationValue.builder()
                .clientId(client.getId())
                .level(regionLevel)
                .name("EMEA")
                .code("EMEA")
                .build());

        // ── Partner company → EMEA region ────────────────────────────────────
        company = partnerCompanyRepository.save(
                PartnerFixtures.activeReseller(client.getId()).build());
        partnerCompanyLocationRepository.save(PartnerCompanyLocation.builder()
                .clientId(client.getId())
                .partnerCompany(company)
                .locationValue(regionValue)
                .build());

        // ── Client role (base_role_name used by segment breakdown MV) ────────
        role = clientRoleRepository.save(ClientRole.builder()
                .clientId(client.getId())
                .name("Sales Manager")
                .baseRoleName("SALES_MANAGER")
                .system(false)
                .defaultRole(false)
                .roleType("EXTERNAL")
                .build());

        // ── User with partner company + role so v_user_region returns "EMEA" ─
        testUser = userRepository.save(UserFixtures.activeUser(client.getId(), company.getId())
                .clientRoleId(role.getId())
                .build());

        // ── Catalog item + tenant config ──────────────────────────────────────
        catalogItem = catalogItemRepository.save(
                RedemptionCatalogItemFixtures.activeNonCashItem().currencyId("USD").build());
        catalogConfigRepository.save(
                ClientCatalogItemConfigFixtures.enabledConfig(client.getId(), catalogItem.getId()).build());

        // ── Wallet + one COMPLETED redemption request ─────────────────────────
        // submittedAt = Instant.now() → period_date = today for the MV GROUP BY.
        // currencyId overrides the fixture default ("cash") to "USD" so assertions
        // use the expected currency symbol.
        wallet = walletRepository.save(
                RewardWalletFixtures.individualWalletWithBalance(
                        client.getId(), testUser.getId(), new BigDecimal("5000.00")).build());
        request = redemptionRequestRepository.save(
                RedemptionRequestFixtures.defaultPersonal(
                        client.getId(), testUser.getId(), wallet.getId(), catalogItem.getId())
                        .status(RedemptionStatus.COMPLETED)
                        .currencyId("USD")
                        .submittedAt(Instant.now())
                        .completedAt(Instant.now())
                        .deleted(false)
                        .build());

        // Refresh all five analytics MVs so the above rows are visible to queries.
        refreshAllMvs();

        TenantContext.setClientId(client.getId());
        setSecurityContext(testUser);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);

        // Delete in reverse FK dependency order.
        safeDelete(() -> redemptionRequestRepository.delete(request));
        safeDelete(() -> walletRepository.delete(wallet));
        safeDelete(() -> catalogConfigRepository
                .findByClientIdAndRedemptionCatalogItemId(client.getId(), catalogItem.getId())
                .ifPresent(catalogConfigRepository::delete));
        safeDelete(() -> catalogItemRepository.delete(catalogItem));
        safeDelete(() -> userRepository.delete(testUser));
        safeDelete(() -> partnerCompanyLocationRepository.deleteByPartnerCompanyId(company.getId()));
        safeDelete(() -> locationValueRepository.delete(regionValue));
        safeDelete(() -> locationLevelRepository.delete(regionLevel));
        safeDelete(() -> partnerCompanyRepository.delete(company));
        safeDelete(() -> clientRoleRepository.delete(role));
        safeDelete(() -> clientRepository.delete(client));
    }

    // ── MV query assertions ───────────────────────────────────────────────────

    @Test
    void getItemBreakdown_seededCompletedRequest_returnsRowWithExpectedCurrencyAndCount() {
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), null);

        ItemBreakdownResponse response = analyticsService.getItemBreakdown(filter);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).currencyId()).isEqualTo("USD");
        assertThat(response.items().get(0).totalRedeemedCount()).isEqualTo(1L);
    }

    @Test
    void getItemBreakdown_itemWithMoreFailuresThanCompletions_rateStaysWithin0to100() {
        // setUp seeds 1 COMPLETED request for catalogItem (USD, today, testUser).
        // Add 2 FAILED requests for the SAME item/user/currency/day so all three land in
        // one MV row: completed=1, failed=2, COUNT(*)=3 → per-row redemption_rate = 33.33.
        // Regression guard: the old (completed - failed - cancelled)/completed formula
        // produced (1-2-0)/1 = -100% here.
        RedemptionRequest failed1 = redemptionRequestRepository.save(
                RedemptionRequestFixtures.defaultPersonal(
                        client.getId(), testUser.getId(), wallet.getId(), catalogItem.getId())
                        .status(RedemptionStatus.FAILED)
                        .currencyId("USD")
                        .submittedAt(Instant.now())
                        .deleted(false)
                        .build());
        RedemptionRequest failed2 = redemptionRequestRepository.save(
                RedemptionRequestFixtures.defaultPersonal(
                        client.getId(), testUser.getId(), wallet.getId(), catalogItem.getId())
                        .status(RedemptionStatus.FAILED)
                        .currencyId("USD")
                        .submittedAt(Instant.now())
                        .deleted(false)
                        .build());
        refreshAllMvs();

        try {
            ItemBreakdownResponse response = analyticsService.getItemBreakdown(
                    new AdvancedAnalyticsFilter(LocalDate.now().minusDays(7), LocalDate.now(), null));

            assertThat(response.items()).hasSize(1);
            var item = response.items().get(0);
            assertThat(item.totalRedeemedCount())
                    .as("total_redeemed_count is COMPLETED-only after V30")
                    .isEqualTo(1L);
            assertThat(item.redemptionRate())
                    .as("redemption rate must never be negative and must stay within 0–100")
                    .isBetween(0.0, 100.0);
            assertThat(item.redemptionRate())
                    .as("1 COMPLETED of 3 total requests → 33.33%")
                    .isCloseTo(33.33, within(0.01));
        } finally {
            safeDelete(() -> redemptionRequestRepository.delete(failed1));
            safeDelete(() -> redemptionRequestRepository.delete(failed2));
        }
    }

    @Test
    void getSegmentBreakdown_seededCompletedRequest_returnsRowWithRegionAndRole() {
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), null, null);

        SegmentBreakdownResponse response = analyticsService.getSegmentBreakdown(filter);

        assertThat(response).isNotNull();
        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).region()).isEqualTo("EMEA");
        assertThat(response.segments().get(0).role()).isEqualTo("SALES_MANAGER");
        assertThat(response.segments().get(0).currencyId()).isEqualTo("USD");
        assertThat(response.segments().get(0).totalRedeemedCount()).isEqualTo(1L);
        // redemptionRate is a percentage (0–100), same scale as item/trend and the contract.
        // 1 of 1 requests COMPLETED → 100.00. Guards against re-introducing a /100 division.
        assertThat(response.segments().get(0).redemptionRate()).isEqualByComparingTo("100.00");
    }

    @Test
    void getSegmentBreakdown_commaSeparatedRegionFilter_matchesAnyListedRegion() {
        // setUp seeds one EMEA / SALES_MANAGER / USD row. A multi-select region filter arrives
        // as a comma-separated string and must match rows whose region is ANY listed value
        // (IN clause). The previous single-value `region = :region` bound the whole string
        // "EMEA,APAC" as one literal and returned 0 rows.
        SegmentBreakdownResponse response = analyticsService.getSegmentBreakdown(
                new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(7), LocalDate.now(), "EMEA,APAC", null));

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).region()).isEqualTo("EMEA");
    }

    @Test
    void getSegmentBreakdown_commaSeparatedRoleFilter_matchesAnyListedRole() {
        SegmentBreakdownResponse response = analyticsService.getSegmentBreakdown(
                new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(7), LocalDate.now(), null, "SALES_MANAGER,CLIENT_ADMIN"));

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).role()).isEqualTo("SALES_MANAGER");
    }

    @Test
    void getRedemptionTrend_seededCompletedRequest_returnsDataPointWithCurrencyAndCount() {
        RedemptionTrendResponse response = analyticsService.getRedemptionTrend(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(response).isNotNull();
        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).currencyId()).isEqualTo("USD");
        assertThat(response.dataPoints().get(0).redeemedCount()).isEqualTo(1L);
    }

    @Test
    void getTimeToFirstRedemption_seededCompletedRequest_returnsRegionWithSampleCount() {
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), null);

        TimeToFirstRedemptionResponse response =
                analyticsService.getTimeToFirstRedemption(filter);

        assertThat(response).isNotNull();
        assertThat(response.regions()).hasSize(1);
        assertThat(response.regions().get(0).region()).isEqualTo("EMEA");
        assertThat(response.regions().get(0).sampleCount()).isEqualTo(1L);
    }

    @Test
    void getFailureBreakdown_completedRequest_returnsRowWithZeroFailureAndCorrectTotal() {
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), null);

        FailureBreakdownResponse response = analyticsService.getFailureBreakdown(filter);

        assertThat(response).isNotNull();
        assertThat(response.failureModes()).hasSize(1);
        assertThat(response.failureModes().get(0).currencyId()).isEqualTo("USD");
        assertThat(response.failureModes().get(0).totalCount()).isEqualTo(1L);
        assertThat(response.failureModes().get(0).failedCount()).isEqualTo(0L);
        assertThat(response.failureModes().get(0).cancelledCount()).isEqualTo(0L);
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation_queryAsTenantA_doesNotSeeTenantBRows() {
        // Seed tenant B with a EUR request so the currencies are distinguishable.
        Client clientB = clientRepository.save(ClientFixtures.activeEnterprise().build());
        TenantContext.setClientId(clientB.getId());

        User userB = userRepository.save(
                UserFixtures.activeUser(clientB.getId(), null).build());
        // Reuse the global catalog item; wallet still needs its own clientId/userId.
        RewardWallet walletB = walletRepository.save(
                RewardWalletFixtures.individualWalletWithBalance(
                        clientB.getId(), userB.getId(), new BigDecimal("5000.00")).build());
        RedemptionRequest requestB = redemptionRequestRepository.save(
                RedemptionRequestFixtures.defaultPersonal(
                        clientB.getId(), userB.getId(), walletB.getId(), catalogItem.getId())
                        .status(RedemptionStatus.COMPLETED)
                        .currencyId("EUR")
                        .submittedAt(Instant.now())
                        .completedAt(Instant.now())
                        .deleted(false)
                        .build());

        // Refresh so the B rows appear in the MV alongside A's rows.
        refreshAllMvs();

        // Switch back to tenant A — the service filters by client_id.
        TenantContext.setClientId(client.getId());
        setSecurityContext(testUser);

        RedemptionTrendResponse response = analyticsService.getRedemptionTrend(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).currencyId()).isEqualTo("USD");
        assertThat(response.dataPoints())
                .noneMatch(dp -> "EUR".equals(dp.currencyId()));

        // Cleanup tenant B data (reverse FK order)
        safeDelete(() -> redemptionRequestRepository.delete(requestB));
        safeDelete(() -> walletRepository.delete(walletB));
        safeDelete(() -> userRepository.delete(userB));
        safeDelete(() -> clientRepository.delete(clientB));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshAllMvs() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_item_redemption_breakdown");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_segment_redemption_breakdown");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_time_to_first_redemption");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_redemption_rate_trend");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_failure_mode_breakdown");
    }

    private void setSecurityContext(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN"))));
    }

    private void safeDelete(Runnable action) {
        try { action.run(); } catch (Exception ignored) {}
    }
}
