package com.tenxengage.app.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3", matchIfMissing = true)
    public MinioClient minioClient(
            @Value("${app.storage.endpoint}") String endpoint,
            @Value("${app.storage.access-key}") String accessKey,
            @Value("${app.storage.secret-key}") String secretKey,
            @Value("${app.storage.bucket}") String bucket) {
        MinioClient client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();

        // Auto-create bucket on startup
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created S3 bucket: {}", bucket);
            } else {
                log.info("S3 bucket already exists: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("Could not initialize S3 bucket '{}': {}", bucket, e.getMessage());
        }

        return client;
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "gcp")
    public Storage gcpStorage(@Value("${app.storage.gcp.project-id:}") String projectId) {
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (projectId != null && !projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        Storage storage = builder.build().getService();
        log.info("GCP storage client initialized");
        return storage;
    }
}
