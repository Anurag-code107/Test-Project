package com.tenxengage.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@Component
public class StorageCredentialValidator {

    private static final Logger log = LoggerFactory.getLogger(StorageCredentialValidator.class);

    private final String provider;
    private final String accessKey;
    private final String gcpProjectId;
    private final String localBaseDir;
    private final String bucket;
    private final Environment environment;

    public StorageCredentialValidator(
            @Value("${app.storage.provider:s3}") String provider,
            @Value("${app.storage.access-key:}") String accessKey,
            @Value("${app.storage.gcp.project-id:}") String gcpProjectId,
            @Value("${app.storage.local.base-dir:./storage-data}") String localBaseDir,
            @Value("${app.storage.bucket:}") String bucket,
            Environment environment) {
        this.provider = provider;
        this.accessKey = accessKey;
        this.gcpProjectId = gcpProjectId;
        this.localBaseDir = localBaseDir;
        this.bucket = bucket;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateStorageCredentials() {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (isLocal) {
            return;
        }

        switch (provider) {
            case "s3" -> validateS3Credentials();
            case "gcp" -> validateGcpCredentials();
            case "local" -> validateLocalStorage();
            default -> log.warn("Unknown storage provider: {}", provider);
        }
    }

    private void validateS3Credentials() {
        if ("minioadmin".equals(accessKey)) {
            throw new IllegalStateException(
                "STORAGE_ACCESS_KEY must be set to a secure value in non-local environments. "
                + "Do not use the default MinIO credentials.");
        }
    }

    private void validateGcpCredentials() {
        if (gcpProjectId == null || gcpProjectId.isBlank()) {
            log.warn("GCP_PROJECT_ID is empty. GCP storage may not function correctly.");
        }
    }

    private void validateLocalStorage() {
        Path baseDir = Path.of(localBaseDir, bucket);
        if (!Files.isDirectory(baseDir)) {
            try {
                Files.createDirectories(baseDir);
                log.info("Created local storage directory: {}", baseDir);
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Local storage base directory is not writable: " + baseDir, e);
            }
        }
    }
}
