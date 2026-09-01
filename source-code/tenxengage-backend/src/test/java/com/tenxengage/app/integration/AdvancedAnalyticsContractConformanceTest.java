package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.AdvancedAnalyticsFilter;
import com.tenxengage.app.dto.response.redemption.FailureBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.ItemBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionTrendResponse;
import com.tenxengage.app.dto.response.redemption.SegmentBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.TimeToFirstRedemptionResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.redemption.RedemptionAdvancedAnalyticsService;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.UserFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract conformance tests for the Advanced Redemption Analytics service (FR-08.x).
 *
 * <p>Covers three contract guarantees without needing real MV data:
 * <ol>
 *   <li>All five endpoints return a non-null response with an empty list when no data
 *       exists for the current tenant (HTTP 200 equivalent).</li>
 *   <li>A date-range span exceeding 365 days throws {@link BusinessRuleException}
 *       (HTTP 422) for every endpoint.</li>
 *   <li>A STARTER-tier client without the {@code redemption_analytics_advanced}
 *       feature flag throws {@link AccessDeniedException} (HTTP 403) for every endpoint.</li>
 * </ol>
 *
 * <p>Each test uses a freshly created client UUID so MV rows from other test classes
 * never leak into these queries.
 */
@Tag("integration")
class AdvancedAnalyticsContractConformanceTest extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionAdvancedAnalyticsService analyticsService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;

    private Client enterpriseClient;
    private Client starterClient;
    private User enterpriseUser;
    private User starterUser;

    @BeforeEach
    void setUp() {
        enterpriseClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        starterClient    = clientRepository.save(ClientFixtures.activeStarter().build());

        enterpriseUser = userRepository.save(
                UserFixtures.activeUser(enterpriseClient.getId(), null).build());
        starterUser = userRepository.save(
                UserFixtures.activeUser(starterClient.getId(), null).build());

        TenantContext.setClientId(enterpriseClient.getId());
        setSecurityContext(enterpriseUser);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        safeDelete(() -> userRepository.delete(enterpriseUser));
        safeDelete(() -> userRepository.delete(starterUser));
        safeDelete(() -> clientRepository.delete(enterpriseClient));
        safeDelete(() -> clientRepository.delete(starterClient));
    }

    // ── Empty list → 200 (no data for fresh tenant) ───────────────────────────

    @Test
    void getItemBreakdown_noData_returnsNonNullResponseWithEmptyItems() {
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), null);

        ItemBreakdownResponse response = analyticsService.getItemBreakdown(filter);

        assertThat(response).isNotNull();
        assertThat(response.dateWindow()).isNotNull();
        assertThat(response.items()).isNotNull().isEmpty();
    }

    @Test
    void getSegmentBreakdown_noData_returnsNonNullResponseWithEmptySegments() {
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), null, null);

        SegmentBreakdownResponse response = analyticsService.getSegmentBreakdown(filter);

        assertThat(response).isNotNull();
        assertThat(response.dateWindow()).isNotNull();
        assertThat(response.segments()).isNotNull().isEmpty();
    }

    @Test
    void getRedemptionTrend_noData_returnsNonNullResponseWithEmptyDataPoints() {
        RedemptionTrendResponse response = analyticsService.getRedemptionTrend(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(response).isNotNull();
        assertThat(response.dateWindow()).isNotNull();
        assertThat(response.dataPoints()).isNotNull().isEmpty();
    }

    @Test
    void getTimeToFirstRedemption_noData_returnsNonNullResponseWithEmptyRegions() {
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), null);

        TimeToFirstRedemptionResponse response =
                analyticsService.getTimeToFirstRedemption(filter);

        assertThat(response).isNotNull();
        assertThat(response.regions()).isNotNull().isEmpty();
        assertThat(response.filters()).isNotNull();
    }

    @Test
    void getFailureBreakdown_noData_returnsNonNullResponseWithEmptyFailureModes() {
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.now().minusDays(7), LocalDate.now(), null);

        FailureBreakdownResponse response = analyticsService.getFailureBreakdown(filter);

        assertThat(response).isNotNull();
        assertThat(response.dateWindow()).isNotNull();
        assertThat(response.failureModes()).isNotNull().isEmpty();
    }

    // ── span > 365 days → BusinessRuleException (HTTP 422) ───────────────────

    @Test
    void getItemBreakdown_spanOver365Days_throwsBusinessRuleException() {
        assertThatThrownBy(() ->
                analyticsService.getItemBreakdown(new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(366), LocalDate.now(), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getSegmentBreakdown_spanOver365Days_throwsBusinessRuleException() {
        assertThatThrownBy(() ->
                analyticsService.getSegmentBreakdown(new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(366), LocalDate.now(), null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getRedemptionTrend_spanOver365Days_throwsBusinessRuleException() {
        assertThatThrownBy(() ->
                analyticsService.getRedemptionTrend(
                        LocalDate.now().minusDays(366), LocalDate.now()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getTimeToFirstRedemption_spanOver365Days_throwsBusinessRuleException() {
        assertThatThrownBy(() ->
                analyticsService.getTimeToFirstRedemption(new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(366), LocalDate.now(), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getFailureBreakdown_spanOver365Days_throwsBusinessRuleException() {
        assertThatThrownBy(() ->
                analyticsService.getFailureBreakdown(new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(366), LocalDate.now(), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── Feature flag disabled (STARTER tier) → AccessDeniedException (HTTP 403)

    @Test
    void getItemBreakdown_starterClient_throwsAccessDeniedException() {
        TenantContext.setClientId(starterClient.getId());
        setSecurityContext(starterUser);

        assertThatThrownBy(() ->
                analyticsService.getItemBreakdown(new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(7), LocalDate.now(), null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getSegmentBreakdown_starterClient_throwsAccessDeniedException() {
        TenantContext.setClientId(starterClient.getId());
        setSecurityContext(starterUser);

        assertThatThrownBy(() ->
                analyticsService.getSegmentBreakdown(new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(7), LocalDate.now(), null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getRedemptionTrend_starterClient_throwsAccessDeniedException() {
        TenantContext.setClientId(starterClient.getId());
        setSecurityContext(starterUser);

        assertThatThrownBy(() ->
                analyticsService.getRedemptionTrend(
                        LocalDate.now().minusDays(7), LocalDate.now()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getTimeToFirstRedemption_starterClient_throwsAccessDeniedException() {
        TenantContext.setClientId(starterClient.getId());
        setSecurityContext(starterUser);

        assertThatThrownBy(() ->
                analyticsService.getTimeToFirstRedemption(new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(7), LocalDate.now(), null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getFailureBreakdown_starterClient_throwsAccessDeniedException() {
        TenantContext.setClientId(starterClient.getId());
        setSecurityContext(starterUser);

        assertThatThrownBy(() ->
                analyticsService.getFailureBreakdown(new AdvancedAnalyticsFilter(
                        LocalDate.now().minusDays(7), LocalDate.now(), null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
