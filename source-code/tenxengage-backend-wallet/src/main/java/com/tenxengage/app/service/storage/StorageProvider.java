package com.tenxengage.app.service.storage;

import java.io.InputStream;

public interface StorageProvider {

    String upload(String objectKey, InputStream stream, long size, String contentType);

    InputStream download(String objectKey);

    void delete(String objectKey);

    default String generatePresignedUrl(String objectKey, int durationMinutes) {
        throw new UnsupportedOperationException("Presigned URLs not supported by this provider");
    }
}
