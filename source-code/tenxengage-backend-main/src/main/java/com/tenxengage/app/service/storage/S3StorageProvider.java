package com.tenxengage.app.service.storage;

import com.tenxengage.app.exception.StorageException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3", matchIfMissing = true)
public class S3StorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(S3StorageProvider.class);

    private final MinioClient minioClient;
    private final String bucket;

    public S3StorageProvider(MinioClient minioClient,
                             @Value("${app.storage.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        log.info("S3 storage provider initialized with bucket: {}", bucket);
    }

    @Override
    public String upload(String objectKey, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(stream, size, -1)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .build());
            log.info("Uploaded object: {}", objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to S3 storage: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
        } catch (Exception e) {
            throw new StorageException("Failed to download file from S3 storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
            log.info("Deleted object: {}", objectKey);
        } catch (Exception e) {
            throw new StorageException("Failed to delete file from S3 storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey, int durationMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectKey)
                .expiry(durationMinutes, TimeUnit.MINUTES)
                .build());
        } catch (Exception e) {
            throw new StorageException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }
}
