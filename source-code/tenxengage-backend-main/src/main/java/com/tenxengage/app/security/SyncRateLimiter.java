package com.tenxengage.app.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SyncRateLimiter {

    private final ConcurrentHashMap<UUID, List<Instant>> requestLog = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_WINDOW = 2;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public synchronized boolean tryAcquire(UUID userId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);

        List<Instant> times = requestLog.computeIfAbsent(userId, k -> new ArrayList<>());
        times.removeIf(t -> t.isBefore(cutoff));

        if (times.size() >= MAX_REQUESTS_PER_WINDOW) {
            return false;
        }
        times.add(now);
        return true;
    }
}
