package com.tenxengage.app.security;

import com.tenxengage.app.security.AnalyticsExportRateLimiter.RateLimitResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting unit tests for the analytics CSV-export rate limiter (FR-08.5, spec § Security).
 *
 * <p>Locks in the <b>current, verified</b> export-throttle behavior that backs the
 * {@code GET /api/v1/redemption/analytics/advanced/liability-trend/export} 429 path:
 * <ol>
 *   <li>The first {@code MAX_REQUESTS_PER_WINDOW} acquisitions per tenant succeed; the next is denied
 *       with a {@code Retry-After} value of at least one second.</li>
 *   <li>The limit is keyed per tenant — one tenant exhausting its window never throttles another.</li>
 * </ol>
 *
 * <p>This is the query-side rate-limit's counterpart: the cross-story test plan's
 * "11th query → 429" scenario is intentionally NOT asserted here because the query-level
 * {@code RateLimitFilter} matches the analytics path by exact equality and therefore does not
 * apply to the {@code /advanced/**} sub-paths (documented deviation — see spec § Security Design).
 * The export limiter below is the throttle that actually protects the DB for this feature.
 *
 * <p>Pure unit test — no Spring context, DB, or Redis required.
 */
class AnalyticsExportRateLimiterTest {

    private final AnalyticsExportRateLimiter limiter = new AnalyticsExportRateLimiter();

    @Test
    void allowsUpToLimitThenDeniesWithRetryAfter() {
        UUID tenant = UUID.randomUUID();

        for (int i = 0; i < AnalyticsExportRateLimiter.MAX_REQUESTS_PER_WINDOW; i++) {
            assertThat(limiter.tryAcquireWithRetryAfter(tenant).allowed())
                    .as("acquisition %d within the window must be allowed", i + 1)
                    .isTrue();
        }

        RateLimitResult overLimit = limiter.tryAcquireWithRetryAfter(tenant);
        assertThat(overLimit.allowed())
                .as("acquisition beyond the per-window limit must be denied")
                .isFalse();
        assertThat(overLimit.retryAfterSeconds())
                .as("denied acquisition must carry a Retry-After of at least 1 second")
                .isGreaterThanOrEqualTo(1L);
    }

    @Test
    void limitIsScopedPerTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // Exhaust tenant A's window.
        for (int i = 0; i < AnalyticsExportRateLimiter.MAX_REQUESTS_PER_WINDOW; i++) {
            limiter.tryAcquireWithRetryAfter(tenantA);
        }
        assertThat(limiter.tryAcquireWithRetryAfter(tenantA).allowed())
                .as("tenant A is over its limit")
                .isFalse();

        // Tenant B has its own independent budget.
        assertThat(limiter.tryAcquireWithRetryAfter(tenantB).allowed())
                .as("tenant B must be unaffected by tenant A's exhausted window")
                .isTrue();
    }
}
