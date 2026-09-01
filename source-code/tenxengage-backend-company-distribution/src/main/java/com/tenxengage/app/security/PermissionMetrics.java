package com.tenxengage.app.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Metrics for permission resolution and enforcement.
 * Exposed via Spring Actuator for monitoring dashboards.
 */
@Component
public class PermissionMetrics {

    private final MeterRegistry registry;

    public PermissionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordPermissionDenied(String permissionKey) {
        Counter.builder("permission.denied")
                .tag("permission", permissionKey)
                .description("Count of permission denied events")
                .register(registry)
                .increment();
    }

    public Timer.Sample startResolution() {
        return Timer.start(registry);
    }

    public void recordResolution(Timer.Sample sample) {
        sample.stop(Timer.builder("permission.resolution")
                .description("Time to resolve effective permissions")
                .register(registry));
    }
}
