package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.response.redemption.AnalyticsRefreshStatusResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.AuditLogRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.redemption.RedemptionAdvancedAnalyticsService;
import com.tenxengage.app.testdata.AdvancedAnalyticsFixtures;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.UserFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Cross-story integration tests for FR-08.x Advanced Redemption Analytics.
 *
 * <p>Covers five cross-story guarantees:
 * <ol>
 *   <li>getLiabilityTrend with 366-day span → BusinessRuleException (365-day cap)</li>
 *   <li>exportLiabilityTrend happy path → CSV bytes non-empty + audit row written async</li>
 *   <li>exportLiabilityTrend with 366-day span → throws + NO audit row written</li>
 *   <li>Tenant isolation: A's liability rows invisible to B's query</li>
 *   <li>Feature-flag gate: STARTER → AccessDeniedException; ENTERPRISE → succeeds</li>
 * </ol>
 */
@Tag("integration")
class AdvancedAnalyticsIntegrationTest extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionAdvancedAnalyticsService analyticsService;
    @Autowired private AdvancedAnalyticsFixtures fixtures;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Client enterpriseClient;
    private Client starterClient;
    private Client otherClient;
    private User testUser;
    private User starterUser;

    @BeforeEach
    void setUp() {
        // logAsync is @Async — MODE_INHERITABLETHREADLOCAL lets the executor thread
        // inherit a copy of the SecurityContext so AuditLogService can resolve the actor.
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

        enterpriseClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        starterClient    = clientRepository.save(ClientFixtures.activeStarter().build());
        otherClient      = clientRepository.save(ClientFixtures.activeEnterprise().build());

        testUser    = userRepository.save(UserFixtures.activeUser(enterpriseClient.getId(), null).build());
        starterUser = userRepository.save(UserFixtures.activeUser(starterClient.getId(), null).build());

        TenantContext.setClientId(enterpriseClient.getId());
        setSecurityContext(testUser);
    }

    @AfterEach
    void tearDown() {
        // Clean up audit logs while TenantContext is still active so any Hibernate
        // tenant filter (if applied to AuditLog) matches the right client.
        safeDelete(() -> auditLogRepository.findByActorIdAndResourceType(
                testUser.getId(), AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT)
                .forEach(auditLogRepository::delete));

        TenantContext.clear();
        SecurityContextHolder.clearContext();
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);

        // Liability trend rows — no FK to clients, safe to delete in any order.
        safeDelete(() -> jdbcTemplate.update(
                "DELETE FROM mv_liability_trend WHERE client_id = ?",
                enterpriseClient.getId()));
        safeDelete(() -> jdbcTemplate.update(
                "DELETE FROM mv_liability_trend WHERE client_id = ?",
                otherClient.getId()));

        safeDelete(() -> userRepository.delete(testUser));
        safeDelete(() -> userRepository.delete(starterUser));
        safeDelete(() -> clientRepository.delete(enterpriseClient));
        safeDelete(() -> clientRepository.delete(starterClient));
        safeDelete(() -> clientRepository.delete(otherClient));
    }

    // ── 1. getLiabilityTrend 365-day cap ─────────────────────────────────────

    @Test
    void getLiabilityTrend_366DaySpan_throwsBusinessRuleException() {
        LocalDate from = LocalDate.now().minusDays(366);
        LocalDate to   = LocalDate.now();

        assertThatThrownBy(() -> analyticsService.getLiabilityTrend(from, to))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── 2. exportLiabilityTrend happy path ───────────────────────────────────

    @Test
    void exportLiabilityTrend_seededRows_returnsCsvBytesAndWritesAuditRow() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to   = LocalDate.now();
        fixtures.insertLiabilityTrendRow(enterpriseClient.getId(),
                LocalDate.now().minusDays(1), "USD", new BigDecimal("5000.00"));

        long countBefore = auditLogRepository.countByClientIdAndResourceType(
                enterpriseClient.getId(), AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT);

        byte[] csv = analyticsService.exportLiabilityTrend(from, to);

        assertThat(csv).isNotEmpty();

        // logAsync runs in a separate thread — poll until the executor commits the audit row.
        await().atMost(3, SECONDS).until(() ->
                auditLogRepository.countByClientIdAndResourceType(
                        enterpriseClient.getId(),
                        AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT) > countBefore);

        assertThat(auditLogRepository.countByClientIdAndResourceType(
                enterpriseClient.getId(), AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT))
                .isEqualTo(countBefore + 1);
    }

    // ── 3. exportLiabilityTrend 365-day cap — audit row must NOT be written ──

    @Test
    void exportLiabilityTrend_366DaySpan_throwsAndNoAuditRowWritten() {
        long countBefore = auditLogRepository.countByClientIdAndResourceType(
                enterpriseClient.getId(), AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT);

        assertThatThrownBy(() ->
                analyticsService.exportLiabilityTrend(
                        LocalDate.now().minusDays(366), LocalDate.now()))
                .isInstanceOf(BusinessRuleException.class);

        // No async work was dispatched — count must be unchanged synchronously.
        assertThat(auditLogRepository.countByClientIdAndResourceType(
                enterpriseClient.getId(), AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT))
                .isEqualTo(countBefore);
    }

    // ── 4. Tenant isolation ───────────────────────────────────────────────────

    @Test
    void getLiabilityTrend_tenantIsolation_returnsOnlyCurrentTenantRows() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate from      = LocalDate.now().minusDays(30);
        LocalDate to        = LocalDate.now();

        fixtures.insertLiabilityTrendRow(
                enterpriseClient.getId(), yesterday, "USD", new BigDecimal("1000.00"));
        fixtures.insertLiabilityTrendRow(
                otherClient.getId(), yesterday, "EUR", new BigDecimal("9999.99"));

        // Query as enterpriseClient — otherClient's EUR row must not appear.
        var response = analyticsService.getLiabilityTrend(from, to);

        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).currencyId()).isEqualTo("USD");
        assertThat(response.dataPoints().get(0).totalUnredeemedBalance())
                .isEqualByComparingTo("1000.00");
    }

    // ── 5. Feature-flag gate ─────────────────────────────────────────────────

    @Test
    void getRefreshStatus_starterTier_throwsAccessDeniedException() {
        TenantContext.setClientId(starterClient.getId());
        setSecurityContext(starterUser);

        assertThatThrownBy(() -> analyticsService.getRefreshStatus())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getRefreshStatus_enterpriseTier_returnsStatus() {
        // Enterprise context already set in @BeforeEach.
        AnalyticsRefreshStatusResponse status = analyticsService.getRefreshStatus();
        assertThat(status).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
