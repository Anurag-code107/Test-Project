package com.tenxengage.app.service;

import com.tenxengage.app.service.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final StorageProvider storageProvider;

    public FileStorageService(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String upload(String objectKey, InputStream stream, long size, String contentType) {
        log.debug("Uploading object: {}", objectKey);
        return storageProvider.upload(objectKey, stream, size, contentType);
    }

    public InputStream download(String objectKey) {
        log.debug("Downloading object: {}", objectKey);
        return storageProvider.download(objectKey);
    }

    public void delete(String objectKey) {
        log.debug("Deleting object: {}", objectKey);
        storageProvider.delete(objectKey);
    }
}
