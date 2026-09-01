package com.tenxengage.app.service.storage;

import com.tenxengage.app.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local")
public class LocalStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);

    private final Path basePath;

    public LocalStorageProvider(
            @Value("${app.storage.local.base-dir:./storage-data}") String baseDir,
            @Value("${app.storage.bucket}") String bucket) {
        this.basePath = Path.of(baseDir, bucket).toAbsolutePath().normalize();
        log.info("Local storage provider initialized with base path: {}", this.basePath);
    }

    @Override
    public String upload(String objectKey, InputStream stream, long size, String contentType) {
        Path targetPath = resolveAndValidate(objectKey);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Uploaded object to local storage: {}", objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to local storage: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        Path targetPath = resolveAndValidate(objectKey);
        try {
            if (!Files.exists(targetPath)) {
                throw new StorageException("Object not found in local storage: " + objectKey);
            }
            return new FileInputStream(targetPath.toFile());
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Failed to download file from local storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path targetPath = resolveAndValidate(objectKey);
        try {
            Files.deleteIfExists(targetPath);
            log.info("Deleted object from local storage: {}", objectKey);
        } catch (Exception e) {
            throw new StorageException("Failed to delete file from local storage: " + e.getMessage(), e);
        }
    }

    private Path resolveAndValidate(String objectKey) {
        Path resolved = basePath.resolve(objectKey).toAbsolutePath().normalize();
        if (!resolved.startsWith(basePath)) {
            throw new StorageException("Path traversal attempt detected for key: " + objectKey);
        }
        return resolved;
    }
}
