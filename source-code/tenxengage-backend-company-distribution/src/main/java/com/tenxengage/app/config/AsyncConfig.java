package com.tenxengage.app.config;

import com.tenxengage.app.security.TenantContext;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("tenxengage-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Copies MDC, SecurityContext, and TenantContext from the calling thread
     * to the async thread, ensuring audit logging and other async operations
     * have access to the request's authentication and tenant identity.
     */
    private static class ContextPropagatingTaskDecorator implements TaskDecorator {
        @Override
        @NonNull
        public Runnable decorate(@NonNull Runnable runnable) {
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            SecurityContext securityContext = SecurityContextHolder.getContext();
            String subdomain = TenantContext.getSubdomain();
            UUID clientId = TenantContext.getClientId();

            return () -> {
                try {
                    if (mdcContext != null) {
                        MDC.setContextMap(mdcContext);
                    }
                    SecurityContextHolder.setContext(securityContext);
                    if (subdomain != null) {
                        TenantContext.setSubdomain(subdomain);
                    }
                    if (clientId != null) {
                        TenantContext.setClientId(clientId);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                    SecurityContextHolder.clearContext();
                    TenantContext.clear();
                }
            };
        }
    }
}
