package com.tenxengage.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class JwtSecretValidator {

    private final String jwtSecret;
    private final String approvalSecret;
    private final Environment environment;

    public JwtSecretValidator(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.approval.token-secret}") String approvalSecret,
            Environment environment) {
        this.jwtSecret = jwtSecret;
        this.approvalSecret = approvalSecret;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateSecrets() {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (isLocal) {
            return;
        }

        if (jwtSecret.startsWith("default-dev-secret")) {
            throw new IllegalStateException(
                "JWT_SECRET must be set to a secure value in non-local environments. "
                + "Do not use the default development secret.");
        }
        if (approvalSecret.startsWith("default-approval-secret")) {
            throw new IllegalStateException(
                "APPROVAL_TOKEN_SECRET must be set to a secure value in non-local environments. "
                + "Do not use the default development secret.");
        }
    }
}
