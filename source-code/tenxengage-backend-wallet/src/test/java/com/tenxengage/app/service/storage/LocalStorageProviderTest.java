package com.tenxengage.app.service.storage;

import com.tenxengage.app.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageProviderTest {

    @TempDir
    Path tempDir;

    private LocalStorageProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LocalStorageProvider(tempDir.toString(), "test-bucket");
    }

    @Test
    void upload_createsFileAndReturnsKey() throws Exception {
        String content = "hello world";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = provider.upload("test.txt", stream, content.length(), "text/plain");

        assertThat(result).isEqualTo("test.txt");
        assertThat(tempDir.resolve("test-bucket/test.txt")).exists();
        assertThat(tempDir.resolve("test-bucket/test.txt")).hasContent(content);
    }

    @Test
    void download_returnsFileContent() throws Exception {
        String content = "download me";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        provider.upload("download.txt", stream, content.length(), "text/plain");

        InputStream downloaded = provider.download("download.txt");

        assertThat(new String(downloaded.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
    }

    @Test
    void delete_removesFile() {
        String content = "delete me";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        provider.upload("deletable.txt", stream, content.length(), "text/plain");

        provider.delete("deletable.txt");

        assertThat(tempDir.resolve("test-bucket/deletable.txt")).doesNotExist();
    }

    @Test
    void upload_createsNestedDirectories() throws Exception {
        String content = "nested content";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        provider.upload("a/b/c/nested.txt", stream, content.length(), "text/plain");

        assertThat(tempDir.resolve("test-bucket/a/b/c/nested.txt")).exists();
        assertThat(tempDir.resolve("test-bucket/a/b/c/nested.txt")).hasContent(content);
    }

    @Test
    void download_nonexistentKey_throwsStorageException() {
        assertThatThrownBy(() -> provider.download("nonexistent.txt"))
            .isInstanceOf(StorageException.class)
            .hasMessageContaining("Object not found");
    }

    @Test
    void pathTraversal_throwsStorageException() {
        assertThatThrownBy(() -> provider.download("../../etc/passwd"))
            .isInstanceOf(StorageException.class)
            .hasMessageContaining("Path traversal attempt detected");
    }

    @Test
    void upload_pathTraversal_throwsStorageException() {
        InputStream stream = new ByteArrayInputStream("hack".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> provider.upload("../../../etc/evil", stream, 4, "text/plain"))
            .isInstanceOf(StorageException.class)
            .hasMessageContaining("Path traversal attempt detected");
    }
}
