package com.tenxengage.app.integration.redemption;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.dto.response.redemption.RedemptionAnalyticsSummaryResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LedgerEntry;
import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.AnalyticsExportRateLimiter;
import com.tenxengage.app.security.AnalyticsExportRateLimiter.RateLimitResult;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.service.redemption.RedemptionAnalyticsService;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.RewardWalletFixtures;
import com.tenxengage.app.testdata.UserFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 — Tenant isolation and rate limit enforcement for analytics.
 *
 * T-07: Verifies that a CLIENT_ADMIN calling GET /analytics only sees their own
 * tenant's ledger data — never another tenant's currencyIds.
 *
 * T-08/T-09 (HTTP-layer, 401 and rate limit on summary): verified by
 * RedemptionAnalyticsControllerTest — those require the security filter chain.
 *
 * T-10: Verifies AnalyticsExportRateLimiter: 3 requests allowed per 60s window;
 * 4th is denied with retryAfterSeconds ≥ 1.
 *
 * Covers: T-07, T-10 from test-plan.md.
 */
@Tag("integration")
class RedemptionAnalyticsIsolationIT extends AbstractLocalIntegrationTest {

    @Autowired private RedemptionAnalyticsService analyticsService;
    @Autowired private AnalyticsExportRateLimiter exportRateLimiter;
    @Autowired private CacheManager cacheManager;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RewardWalletRepository rewardWalletRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;

    private Client tenantA;
    private Client tenantB;
    private User userA;
    private User userB;
    private RewardWallet walletA;
    private RewardWallet walletB;

    @BeforeEach
    void setUp() {
        tenantA = clientRepository.save(ClientFixtures.activeEnterprise().build());
        tenantB = clientRepository.save(ClientFixtures.activeEnterprise().build());
        userA = userRepository.save(UserFixtures.activeUser(tenantA.getId(), null).build());
        userB = userRepository.save(UserFixtures.activeUser(tenantB.getId(), null).build());

        walletA = rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(tenantA.getId(), userA.getId())
                        .currencyId("cash")
                        .build());
        walletB = rewardWalletRepository.save(
                RewardWalletFixtures.individualWallet(tenantB.getId(), userB.getId())
                        .currencyId("points") // distinct from tenant A
                        .build());

        // Seed ledger entries: tenant A has "cash", tenant B has "points"
        saveLedgerEntry(tenantA.getId(), walletA.getId(), "cash");
        saveLedgerEntry(tenantB.getId(), walletB.getId(), "points");
    }

    @AfterEach
    void tearDown() {
        deleteLedger(tenantA.getId());
        deleteLedger(tenantB.getId());
        rewardWalletRepository.delete(walletA);
        rewardWalletRepository.delete(walletB);
        userRepository.delete(userA);
        userRepository.delete(userB);
        clientRepository.delete(tenantA);
        clientRepository.delete(tenantB);
        SecurityContextHolder.clearContext();
        // Evict cache entries written by this test's unique tenantA/tenantB UUIDs
        var cache = cacheManager.getCache("redemption-analytics");
        if (cache != null) cache.clear();
    }

    /**
     * T-07: Tenant A CLIENT_ADMIN sees only "cash" (Tenant A's currencyId).
     * "points" (Tenant B's currencyId) must not appear.
     */
    @Test
    void analyticsSummary_returnsOnlyCallerTenantData() {
        setSecurityContext(userA);

        LocalDate today = LocalDate.now();
        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(today.minusDays(30), today);

        Set<String> currencyIds = response.redemptionRates().stream()
                .map(r -> r.currencyId())
                .collect(Collectors.toSet());

        assertThat(currencyIds).contains("cash");
        assertThat(currencyIds).doesNotContain("points");
    }

    /**
     * T-07 (inverse): Tenant B CLIENT_ADMIN sees only "points" — never "cash".
     */
    @Test
    void analyticsSummary_returnsOnlyCallerTenantData_tenantB() {
        setSecurityContext(userB);

        LocalDate today = LocalDate.now();
        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(today.minusDays(30), today);

        Set<String> currencyIds = response.redemptionRates().stream()
                .map(r -> r.currencyId())
                .collect(Collectors.toSet());

        assertThat(currencyIds).contains("points");
        assertThat(currencyIds).doesNotContain("cash");
    }

    /**
     * T-07 (cache): Second call with identical tenant + date params returns the cached response,
     * even after the underlying ledger data is deleted.
     */
    @Test
    void getAnalyticsSummary_cacheHit_returnsCachedResponseAfterDataDeleted() {
        setSecurityContext(userA);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(30);

        // First call — caches the response (tenantA has "cash" from @BeforeEach)
        RedemptionAnalyticsSummaryResponse first = analyticsService.getAnalyticsSummary(from, today);
        assertThat(first.redemptionRates()).isNotEmpty();

        // Delete the underlying data — a fresh query would return empty results
        deleteLedger(tenantA.getId());

        // Second call with identical params → cache hit → stale data returned
        RedemptionAnalyticsSummaryResponse second = analyticsService.getAnalyticsSummary(from, today);
        assertThat(second.redemptionRates())
                .as("Cache hit: second call must return the cached redemption rates")
                .isEqualTo(first.redemptionRates());
    }

    /**
     * T-10: Export rate limiter — 3 calls allowed, 4th denied with retryAfterSeconds ≥ 1.
     *
     * Uses a synthetic clientId so the count is isolated from any other test
     * that may have acquired tokens for real tenants.
     */
    @Test
    void exportRateLimiter_allows3ThenDenies4th() {
        UUID isolatedClientId = UUID.randomUUID();

        RateLimitResult r1 = exportRateLimiter.tryAcquireWithRetryAfter(isolatedClientId);
        RateLimitResult r2 = exportRateLimiter.tryAcquireWithRetryAfter(isolatedClientId);
        RateLimitResult r3 = exportRateLimiter.tryAcquireWithRetryAfter(isolatedClientId);
        RateLimitResult r4 = exportRateLimiter.tryAcquireWithRetryAfter(isolatedClientId);

        assertThat(r1.allowed()).as("1st request should be allowed").isTrue();
        assertThat(r2.allowed()).as("2nd request should be allowed").isTrue();
        assertThat(r3.allowed()).as("3rd request should be allowed").isTrue();
        assertThat(r4.allowed()).as("4th request within 60s should be denied").isFalse();
        assertThat(r4.retryAfterSeconds()).as("Retry-After must be ≥ 1s").isGreaterThanOrEqualTo(1L);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void saveLedgerEntry(UUID clientId, UUID walletId, String currencyId) {
        LedgerEntry le = LedgerEntry.builder()
                .clientId(clientId)
                .rewardWalletId(walletId)
                .entryType(LedgerEntryType.CREDIT)
                .amount(new BigDecimal("100.00"))
                .currencyId(currencyId)
                .availableBalanceBefore(BigDecimal.ZERO)
                .availableBalanceAfter(new BigDecimal("100.00"))
                .reservedBalanceBefore(BigDecimal.ZERO)
                .reservedBalanceAfter(BigDecimal.ZERO)
                .build();
        ledgerEntryRepository.save(le);
    }

    private void deleteLedger(UUID clientId) {
        ledgerEntryRepository.deleteAll(
                ledgerEntryRepository.findByClientId(clientId, PageRequest.of(0, 10000)).getContent());
    }

    private void setSecurityContext(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        var token = new UsernamePasswordAuthenticationToken(
                details, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
