package com.tenxengage.app.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tenant rate limiter for the analytics CSV export endpoint.
 *
 * <p>Limit: {@value #MAX_REQUESTS_PER_WINDOW} requests per tenant per {@value #WINDOW_SECONDS} seconds.
 * Keyed by {@code clientId} (tenant) — not per user — to prevent concurrent heavy downloads
 * from any admin in the same tenant saturating the DB (Security Design § Rate Limiting).</p>
 *
 * <p>Implementation uses a sliding-window log per tenant. Stale buckets are pruned on each
 * call; tenants whose entire window has expired are evicted from the map to bound memory.</p>
 *
 * <p><b>Note:</b> This is an in-process store. In a multi-instance deployment replace with a
 * Redis-backed counter (e.g. {@code INCR export:{clientId}} + {@code EXPIRE}).
 * For Phase 1 (1–10 concurrent admins per tenant) the in-process store is acceptable.</p>
 */
@Component
public class AnalyticsExportRateLimiter {

    static final int MAX_REQUESTS_PER_WINDOW = 3;
    static final long WINDOW_SECONDS = 60L;

    private static final Duration WINDOW = Duration.ofSeconds(WINDOW_SECONDS);

    /**
     * Result of a single atomic rate-limit check. Returned by
     * {@link #tryAcquireWithRetryAfter(UUID)} to eliminate the TOCTOU window that existed when
     * acquire and reset-time were separate synchronized calls.
     */
    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {}

    private final Map<UUID, List<Instant>> requestLog = new HashMap<>();

    /**
     * Atomically attempts to acquire a token and, if denied, computes the Retry-After
     * value — all inside a single synchronized block.
     *
     * @param clientId the tenant's client ID
     * @return {@link RateLimitResult} with {@code allowed=true} when within the limit;
     *         {@code allowed=false} with {@code retryAfterSeconds ≥ 1} when exceeded
     */
    public synchronized RateLimitResult tryAcquireWithRetryAfter(UUID clientId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);

        List<Instant> times = requestLog.computeIfAbsent(clientId, k -> new ArrayList<>());
        times.removeIf(t -> t.isBefore(cutoff));

        // Evict stale entries so tenants whose window fully expired do not leak map space.
        if (times.isEmpty()) {
            requestLog.remove(clientId);
        }

        if (times.size() >= MAX_REQUESTS_PER_WINDOW) {
            // times.get(0) is the oldest surviving entry — list is maintained in chronological
            // order; removeIf already pruned the head so this is always the correct oldest.
            Instant resetAt = times.get(0).plus(WINDOW);
            long seconds = Duration.between(now, resetAt).getSeconds();
            return new RateLimitResult(false, Math.max(1L, seconds));
        }

        times.add(now);
        // Re-associate the list if it was evicted above (window had fully expired).
        requestLog.putIfAbsent(clientId, times);
        return new RateLimitResult(true, 0L);
    }
}
