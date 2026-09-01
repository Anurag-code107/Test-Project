package com.tenxengage.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter using a fixed-window token bucket per IP + path.
 * Protects authentication and public approval endpoints from brute-force attacks.
 *
 * For multi-instance deployments, replace with a Redis-backed rate limiter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long WINDOW_MS = 60_000; // 1-minute window
    private static final long CLEANUP_INTERVAL_MS = 300_000; // Clean stale buckets every 5 minutes

    // Exact request path -> max requests per window.
    private static final Map<String, Integer> RATE_LIMITS = Map.of(
        "/api/v1/auth/login", 10,
        "/api/v1/auth/refresh", 20,
        "/api/v1/approvals/decide", 20,
        "/api/v1/redemption/analytics", 10,
        // Bank linking triggers an external XTRM LinkBankBeneficiary call per request — cap it.
        "/api/v1/redemption/profile/bank-account", 5,
        // Card linking triggers an external XTRM LinkCard call per request (PCI-sensitive) — cap it tightly.
        "/api/v1/redemption/profile/card", 5,
        // Withdrawals hit XTRM UserWithdrawFund; initiate sends an OTP (tight), confirm may be retried (higher).
        "/api/v1/redemption/profile/withdrawals/initiate", 5,
        "/api/v1/redemption/profile/withdrawals/confirm", 10,
        // Wallet listing is a live XTRM GetBeneficiaryWallets call per request — cap it (read-only, so higher).
        "/api/v1/redemption/profile/wallets", 20
    );

    // Path PREFIX -> max requests per window (for path-variable routes). Matching requests share ONE bucket
    // keyed by the prefix, so e.g. DELETE /banks/{id} for different ids still counts together.
    private static final Map<String, Integer> PREFIX_RATE_LIMITS = Map.of(
        // Removing a bank hits XTRM DeleteBankBeneficiary; covers /banks/{id} (DELETE) and /banks/default
        // (PUT). GET /banks (no trailing slash) is intentionally unmatched — it's a local read.
        "/api/v1/redemption/profile/banks/", 10,
        // Removing a card hits XTRM DeleteCard; covers /cards/{id} (DELETE) and /cards/default (PUT).
        // GET /cards (no trailing slash) is intentionally unmatched — it's a local read.
        "/api/v1/redemption/profile/cards/", 10
    );

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        RateRule rule = findRule(path);

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lazy cleanup of stale buckets
        cleanupIfNeeded();

        String clientIp = resolveClientIp(request);
        String key = clientIp + "|" + rule.key();

        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(rule.limit()));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for IP {} on {}", clientIp, path);
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many requests. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Clears all rate-limit buckets. Intended for test isolation (the buckets are process-wide state). */
    public void clearBuckets() {
        buckets.clear();
    }

    /** Resolve the rate rule for a path: exact match first, then longest-applicable prefix; null if none. */
    private RateRule findRule(String path) {
        Integer exact = RATE_LIMITS.get(path);
        if (exact != null) {
            return new RateRule(path, exact);
        }
        for (Map.Entry<String, Integer> entry : PREFIX_RATE_LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return new RateRule(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    /** A resolved rate limit: {@code key} is the bucket key (exact path or prefix), {@code limit} the cap. */
    private record RateRule(String key, int limit) {
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the first (client) IP from the chain
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            lastCleanup = now;
            buckets.entrySet().removeIf(e -> e.getValue().isExpired(now));
        }
    }

    private static class TokenBucket {
        private final int maxTokens;
        private int tokens;
        private long windowStart;

        TokenBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = maxTokens;
            this.windowStart = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= WINDOW_MS) {
                tokens = maxTokens;
                windowStart = now;
            }
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        synchronized boolean isExpired(long now) {
            return now - windowStart > WINDOW_MS * 2;
        }
    }
}
