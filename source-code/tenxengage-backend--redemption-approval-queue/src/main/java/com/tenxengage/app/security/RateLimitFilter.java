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

    // Path prefix -> max requests per window
    private static final Map<String, Integer> RATE_LIMITS = Map.of(
        "/api/v1/auth/login", 10,
        "/api/v1/auth/refresh", 20,
        "/api/v1/approvals/decide", 20
    );

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        Integer maxRequests = findRateLimit(path);

        if (maxRequests == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lazy cleanup of stale buckets
        cleanupIfNeeded();

        String clientIp = resolveClientIp(request);
        String key = clientIp + "|" + path;

        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(maxRequests));

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

    private Integer findRateLimit(String path) {
        for (Map.Entry<String, Integer> entry : RATE_LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
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
