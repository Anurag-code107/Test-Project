package com.tenxengage.app.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.request.redemption.AdvancedAnalyticsFilter;
import com.tenxengage.app.dto.response.redemption.LiabilityTrendResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.User;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting integration tests for Advanced Redemption Analytics (FR-08.x).
 *
 * <p>Covers the "Cross-Cutting Checks" section of {@code test-plan.md} that the three existing
 * IT classes do not: response caching and PII-free logging. These assert the system's
 * <b>current, verified behavior</b> so it cannot silently regress.
 *
 * <ol>
 *   <li><b>Redis cache hit</b> — a second identical query is served from the 60s Redis cache
 *       without re-reading the database (proven by deleting the underlying row between calls).</li>
 *   <li><b>No PII in logs</b> — the structured query log line emits only tenant/endpoint/date
 *       metadata, never a user email address or wallet balance.</li>
 * </ol>
 *
 * <p><b>Scope note (documented deviation):</b> the cross-story test plan also lists an
 * "11th query → 429" check for a per-tenant query rate limit. That limit is not enforced on the
 * {@code /advanced/**} endpoints — {@code RateLimitFilter} matches the analytics path by exact
 * equality (not prefix) and keys per IP, not per tenant. The DB is instead protected by the
 * export rate limiter (see {@code AnalyticsExportRateLimiterTest}) plus the 60s Redis cache
 * asserted below. See spec § Security Design for the rationale.
 *
 * <p>Requires the local stack ({@code docker compose up -d}: PostgreSQL + Redis), per
 * {@link AbstractLocalIntegrationTest}.
 */
@Tag("integration")
class AdvancedAnalyticsCrossCuttingIT extends AbstractLocalIntegrationTest {

    private static final String LIABILITY_TREND_CACHE = "advanced-analytics-liability-trend";

    @Autowired private RedemptionAdvancedAnalyticsService analyticsService;
    @Autowired private AdvancedAnalyticsFixtures fixtures;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CacheManager cacheManager;

    private Client enterpriseClient;
    private User testUser;

    @BeforeEach
    void setUp() {
        enterpriseClient = clientRepository.save(ClientFixtures.activeEnterprise().build());
        testUser = userRepository.save(UserFixtures.activeUser(enterpriseClient.getId(), null).build());

        TenantContext.setClientId(enterpriseClient.getId());
        setSecurityContext(testUser);
    }

    @AfterEach
    void tearDown() {
        // Evict our cache entries so a rerun (same Redis instance) starts clean.
        safeRun(() -> {
            Cache cache = cacheManager.getCache(LIABILITY_TREND_CACHE);
            if (cache != null) {
                cache.clear();
            }
        });

        TenantContext.clear();
        SecurityContextHolder.clearContext();

        safeRun(() -> jdbcTemplate.update(
                "DELETE FROM mv_liability_trend WHERE client_id = ?", enterpriseClient.getId()));
        safeRun(() -> userRepository.delete(testUser));
        safeRun(() -> clientRepository.delete(enterpriseClient));
    }

    // ── 1. Redis cache hit: second call served from cache, not DB ────────────────

    @Test
    void getLiabilityTrend_secondIdenticalCall_servedFromCacheNotDatabase() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to   = LocalDate.now();
        LocalDate periodDate = LocalDate.now().minusDays(1);

        // Seed one row, then read it — this first (cache-miss) call populates Redis.
        fixtures.insertLiabilityTrendRow(
                enterpriseClient.getId(), periodDate, "USD", new BigDecimal("1234.56"));

        LiabilityTrendResponse first = analyticsService.getLiabilityTrend(from, to);
        assertThat(first.dataPoints())
                .as("first call reads the seeded row from the DB")
                .hasSize(1);

        // Cache must now hold the entry under the tenant-scoped key.
        Cache cache = cacheManager.getCache(LIABILITY_TREND_CACHE);
        assertThat(cache).as("liability-trend cache must be configured").isNotNull();
        String cacheKey = analyticsService.buildLiabilityTrendCacheKey(from, to);
        assertThat(cache.get(cacheKey))
                .as("first call must populate the Redis cache entry")
                .isNotNull();

        // Remove the underlying row. A non-cached read would now return ZERO data points.
        jdbcTemplate.update(
                "DELETE FROM mv_liability_trend WHERE client_id = ?", enterpriseClient.getId());

        // Second identical call must still return the cached payload (1 row), proving it was
        // served from the 60s Redis cache and never re-queried the now-empty table.
        LiabilityTrendResponse second = analyticsService.getLiabilityTrend(from, to);
        assertThat(second.dataPoints())
                .as("second call is served from cache despite the DB row being deleted")
                .hasSize(1);
        assertThat(second.dataPoints().get(0).totalUnredeemedBalance())
                .isEqualByComparingTo("1234.56");
    }

    // ── 2. No PII in analytics query logs ────────────────────────────────────────

    @Test
    void itemBreakdownQuery_logsTenantContextButNoPii() {
        Logger serviceLogger =
                (Logger) LoggerFactory.getLogger(RedemptionAdvancedAnalyticsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        try {
            // Fresh tenant UUID → guaranteed cache miss → the query log line is emitted.
            analyticsService.getItemBreakdown(new AdvancedAnalyticsFilter(
                    LocalDate.now().minusDays(7), LocalDate.now(), null));

            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));

            assertThat(logs)
                    .as("query log line carries the tenant id for traceability")
                    .contains(enterpriseClient.getId().toString());
            assertThat(logs)
                    .as("no user email address may appear in analytics logs (NFR-2 privacy)")
                    .doesNotContain(testUser.getEmail())
                    .doesNotContain("@test.com");
        } finally {
            serviceLogger.detachAppender(appender);
            appender.stop();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void setSecurityContext(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN"))));
    }

    private void safeRun(Runnable action) {
        try { action.run(); } catch (Exception ignored) {}
    }
}
