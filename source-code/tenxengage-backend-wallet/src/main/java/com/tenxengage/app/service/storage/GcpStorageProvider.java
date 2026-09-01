package com.tenxengage.app.service.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.tenxengage.app.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "gcp")
public class GcpStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(GcpStorageProvider.class);

    private final Storage storage;
    private final String bucket;

    public GcpStorageProvider(Storage storage,
                              @Value("${app.storage.bucket}") String bucket) {
        this.storage = storage;
        this.bucket = bucket;
        log.info("GCP storage provider initialized with bucket: {}", bucket);
    }

    @Override
    public String upload(String objectKey, InputStream stream, long size, String contentType) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey))
                .setContentType(contentType != null ? contentType : "application/octet-stream")
                .build();
            storage.createFrom(blobInfo, stream);
            log.info("Uploaded object to GCP: {}", objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to GCP storage: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            byte[] content = storage.readAllBytes(BlobId.of(bucket, objectKey));
            return new ByteArrayInputStream(content);
        } catch (Exception e) {
            throw new StorageException("Failed to download file from GCP storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            storage.delete(BlobId.of(bucket, objectKey));
            log.info("Deleted object from GCP: {}", objectKey);
        } catch (Exception e) {
            throw new StorageException("Failed to delete file from GCP storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey, int durationMinutes) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey)).build();
            URL url = storage.signUrl(blobInfo, durationMinutes, TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature());
            return url.toString();
        } catch (Exception e) {
            throw new StorageException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }
}
