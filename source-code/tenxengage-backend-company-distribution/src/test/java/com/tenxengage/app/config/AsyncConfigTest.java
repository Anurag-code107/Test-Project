package com.tenxengage.app.config;

import com.tenxengage.app.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void taskExecutorPropagatesAllContextsToAsyncThread() throws InterruptedException {
        AsyncConfig config = new AsyncConfig();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.taskExecutor();

        // Set up contexts in the calling thread
        UUID clientId = UUID.randomUUID();
        String subdomain = "acme";
        String requestId = UUID.randomUUID().toString();

        TenantContext.setClientId(clientId);
        TenantContext.setSubdomain(subdomain);
        MDC.put("requestId", requestId);

        SecurityContext securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new TestingAuthenticationToken("user@test.com", null, "ROLE_CLIENT_ADMIN"));
        SecurityContextHolder.setContext(securityContext);

        // Capture what the async thread sees
        AtomicReference<UUID> asyncClientId = new AtomicReference<>();
        AtomicReference<String> asyncSubdomain = new AtomicReference<>();
        AtomicReference<String> asyncRequestId = new AtomicReference<>();
        AtomicReference<String> asyncPrincipal = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            asyncClientId.set(TenantContext.getClientId());
            asyncSubdomain.set(TenantContext.getSubdomain());
            asyncRequestId.set(MDC.get("requestId"));
            asyncPrincipal.set(SecurityContextHolder.getContext().getAuthentication().getName());
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(asyncClientId.get()).isEqualTo(clientId);
        assertThat(asyncSubdomain.get()).isEqualTo(subdomain);
        assertThat(asyncRequestId.get()).isEqualTo(requestId);
        assertThat(asyncPrincipal.get()).isEqualTo("user@test.com");

        executor.shutdown();
    }

    @Test
    void taskExecutorCleansUpContextsAfterExecution() throws InterruptedException {
        AsyncConfig config = new AsyncConfig();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.taskExecutor();
        // Force single thread so second task reuses it
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);

        // First task: set contexts
        UUID clientId = UUID.randomUUID();
        TenantContext.setClientId(clientId);
        TenantContext.setSubdomain("acme");
        MDC.put("requestId", "req-1");
        SecurityContext securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(new TestingAuthenticationToken("user@test.com", null));
        SecurityContextHolder.setContext(securityContext);

        CountDownLatch firstDone = new CountDownLatch(1);
        executor.execute(firstDone::countDown);
        assertThat(firstDone.await(5, TimeUnit.SECONDS)).isTrue();

        // Clear calling-thread contexts before second task
        TenantContext.clear();
        MDC.clear();
        SecurityContextHolder.clearContext();

        // Second task: verify the pool thread's contexts were cleaned up
        AtomicReference<UUID> asyncClientId = new AtomicReference<>();
        AtomicReference<String> asyncSubdomain = new AtomicReference<>();
        AtomicReference<String> asyncRequestId = new AtomicReference<>();
        AtomicReference<Object> asyncAuth = new AtomicReference<>();
        CountDownLatch secondDone = new CountDownLatch(1);

        executor.execute(() -> {
            asyncClientId.set(TenantContext.getClientId());
            asyncSubdomain.set(TenantContext.getSubdomain());
            asyncRequestId.set(MDC.get("requestId"));
            asyncAuth.set(SecurityContextHolder.getContext().getAuthentication());
            secondDone.countDown();
        });

        assertThat(secondDone.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(asyncClientId.get()).isNull();
        assertThat(asyncSubdomain.get()).isNull();
        assertThat(asyncRequestId.get()).isNull();
        assertThat(asyncAuth.get()).isNull();

        executor.shutdown();
    }
}
