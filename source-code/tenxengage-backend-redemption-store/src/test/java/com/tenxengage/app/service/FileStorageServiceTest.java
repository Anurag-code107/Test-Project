package com.tenxengage.app.service;

import com.tenxengage.app.service.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private StorageProvider storageProvider;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(storageProvider);
    }

    @Test
    void upload_delegatesToStorageProvider() {
        InputStream stream = new ByteArrayInputStream("data".getBytes());
        when(storageProvider.upload("key.txt", stream, 4, "text/plain")).thenReturn("key.txt");

        String result = fileStorageService.upload("key.txt", stream, 4, "text/plain");

        assertThat(result).isEqualTo("key.txt");
        verify(storageProvider).upload("key.txt", stream, 4, "text/plain");
    }

    @Test
    void download_delegatesToStorageProvider() {
        InputStream expected = new ByteArrayInputStream("data".getBytes());
        when(storageProvider.download("key.txt")).thenReturn(expected);

        InputStream result = fileStorageService.download("key.txt");

        assertThat(result).isSameAs(expected);
        verify(storageProvider).download("key.txt");
    }

    @Test
    void delete_delegatesToStorageProvider() {
        fileStorageService.delete("key.txt");

        verify(storageProvider).delete("key.txt");
    }
}
