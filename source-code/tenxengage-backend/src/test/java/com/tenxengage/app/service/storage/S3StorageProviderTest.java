package com.tenxengage.app.service.storage;

import com.tenxengage.app.exception.StorageException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageProviderTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private GetObjectResponse getObjectResponse;

    private S3StorageProvider provider;

    @BeforeEach
    void setUp() {
        provider = new S3StorageProvider(minioClient, "test-bucket");
    }

    @Test
    void upload_delegatesToMinioClient() throws Exception {
        InputStream stream = new ByteArrayInputStream("data".getBytes());

        String result = provider.upload("key.txt", stream, 4, "text/plain");

        assertThat(result).isEqualTo("key.txt");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_wrapsExceptionInStorageException() throws Exception {
        InputStream stream = new ByteArrayInputStream("data".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class)))
            .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> provider.upload("key.txt", stream, 4, "text/plain"))
            .isInstanceOf(StorageException.class)
            .hasMessageContaining("Failed to upload");
    }

    @Test
    void download_delegatesToMinioClient() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObjectResponse);

        InputStream result = provider.download("key.txt");

        assertThat(result).isNotNull();
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    void download_wrapsExceptionInStorageException() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
            .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> provider.download("key.txt"))
            .isInstanceOf(StorageException.class)
            .hasMessageContaining("Failed to download");
    }

    @Test
    void delete_delegatesToMinioClient() throws Exception {
        provider.delete("key.txt");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void delete_wrapsExceptionInStorageException() throws Exception {
        doThrow(new RuntimeException("S3 error"))
            .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> provider.delete("key.txt"))
            .isInstanceOf(StorageException.class)
            .hasMessageContaining("Failed to delete");
    }
}
