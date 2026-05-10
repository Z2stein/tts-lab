package com.example.ttslab.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ttslab.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FileSystemFileStorageService")
class FileSystemFileStorageServiceTest {
    private FileSystemFileStorageService service;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setup() {
        StorageProperties props = new StorageProperties(
            tempDir.toString(),
            "test-app",
            "test",
            "main"
        );
        service = new FileSystemFileStorageService(props);
    }

    // ============ put() tests ============

    @Test
    @DisplayName("put writes file to storage")
    void putWritesFileToStorage() throws IOException {
        byte[] content = "test content".getBytes();
        String key = "test/file.txt";

        service.put(key, content, "text/plain");

        Path expectedPath = tempDir.resolve(key);
        assertThat(Files.exists(expectedPath)).isTrue();
        assertThat(Files.readAllBytes(expectedPath)).isEqualTo(content);
    }

    @Test
    @DisplayName("put creates parent directories")
    void putCreatesParentDirectories() throws IOException {
        byte[] content = "nested content".getBytes();
        String key = "level1/level2/level3/file.bin";

        service.put(key, content, "application/octet-stream");

        Path expectedPath = tempDir.resolve(key);
        assertThat(Files.exists(expectedPath)).isTrue();
        assertThat(Files.exists(expectedPath.getParent())).isTrue();
    }

    @Test
    @DisplayName("put overwrites existing file")
    void putOverwritesExistingFile() throws IOException {
        String key = "overwrite/test.txt";
        byte[] firstContent = "first".getBytes();
        byte[] secondContent = "second".getBytes();

        service.put(key, firstContent, "text/plain");
        service.put(key, secondContent, "text/plain");

        Path expectedPath = tempDir.resolve(key);
        assertThat(Files.readAllBytes(expectedPath)).isEqualTo(secondContent);
    }

    @Test
    @DisplayName("put handles empty content")
    void putHandlesEmptyContent() throws IOException {
        byte[] emptyContent = new byte[0];
        String key = "empty/file.bin";

        service.put(key, emptyContent, "application/octet-stream");

        Path expectedPath = tempDir.resolve(key);
        assertThat(Files.exists(expectedPath)).isTrue();
        assertThat(Files.size(expectedPath)).isEqualTo(0);
    }

    @Test
    @DisplayName("put handles large content")
    void putHandlesLargeContent() throws IOException {
        byte[] largeContent = new byte[1024 * 1024]; // 1MB
        Arrays.fill(largeContent, (byte) 42);
        String key = "large/file.bin";

        service.put(key, largeContent, "application/octet-stream");

        Path expectedPath = tempDir.resolve(key);
        assertThat(Files.size(expectedPath)).isEqualTo(largeContent.length);
        assertThat(Files.readAllBytes(expectedPath)).isEqualTo(largeContent);
    }

    // ============ get() tests ============

    @Test
    @DisplayName("get retrieves file from storage")
    void getRetrievesFileFromStorage() throws IOException {
        byte[] content = "test content".getBytes();
        String key = "test/retrieve.txt";
        service.put(key, content, "text/plain");

        StoredFile file = service.get(key, "text/plain", content.length);

        assertThat(file.contentType()).isEqualTo("text/plain");
        assertThat(file.sizeBytes()).isEqualTo(content.length);
        assertThat(file.content()).isNotNull();
    }

    @Test
    @DisplayName("get throws when file not found")
    void getThrowsWhenFileNotFound() {
        String missingKey = "nonexistent/file.txt";

        assertThatThrownBy(() -> service.get(missingKey, "text/plain", 100))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo("AUDIO_ASSET_FILE_NOT_FOUND");
    }

    @Test
    @DisplayName("get includes content type in response")
    void getIncludesContentType() throws IOException {
        byte[] content = "binary content".getBytes();
        String key = "media/audio.mp3";
        service.put(key, content, "audio/mpeg");

        StoredFile file = service.get(key, "audio/mpeg", content.length);

        assertThat(file.contentType()).isEqualTo("audio/mpeg");
    }

    @Test
    @DisplayName("get includes file size in response")
    void getIncludesFileSize() throws IOException {
        byte[] content = "123456789".getBytes();
        String key = "size/test.bin";
        service.put(key, content, "application/octet-stream");

        StoredFile file = service.get(key, "application/octet-stream", content.length);

        assertThat(file.sizeBytes()).isEqualTo(content.length);
    }

    // ============ pathFor() path traversal security tests ============

    @Test
    @DisplayName("pathFor rejects path traversal with ../")
    void pathForRejectsParentDirectoryTraversal() {
        String maliciousKey = "../../../etc/passwd";

        assertThatThrownBy(() -> service.get(maliciousKey, "text/plain", 100))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo("INVALID_STORAGE_KEY");
    }

    @Test
    @DisplayName("pathFor rejects absolute paths")
    void pathForRejectsAbsolutePaths() {
        String absolutePath = "/etc/passwd";

        assertThatThrownBy(() -> service.get(absolutePath, "text/plain", 100))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo("INVALID_STORAGE_KEY");
    }

    @Test
    @DisplayName("pathFor normalizes and validates safe paths")
    void pathForAllowsSafeRelativePaths() throws IOException {
        byte[] content = "safe content".getBytes();
        String safeKey = "users/user123/projects/proj456/file.txt";

        service.put(safeKey, content, "text/plain");

        StoredFile file = service.get(safeKey, "text/plain", content.length);
        assertThat(file).isNotNull();
    }

    @Test
    @DisplayName("pathFor handles paths with dots safely")
    void pathForHandlesDotsInFilenames() throws IOException {
        byte[] content = "file with dots".getBytes();
        String keyWithDots = "archive/backup.2024.01.15.tar.gz";

        service.put(keyWithDots, content, "application/gzip");

        StoredFile file = service.get(keyWithDots, "application/gzip", content.length);
        assertThat(file).isNotNull();
    }

    // ============ edge case tests ============

    @Test
    @DisplayName("get with empty storage key throws")
    void getWithEmptyKeyThrows() {
        assertThatThrownBy(() -> service.get("", "text/plain", 100))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo("INVALID_STORAGE_KEY");
    }

    @Test
    @DisplayName("put and get with special characters in key")
    void putAndGetWithSpecialCharactersInKey() throws IOException {
        byte[] content = "special".getBytes();
        String key = "files/user-id_123.test.data";

        service.put(key, content, "application/octet-stream");

        StoredFile file = service.get(key, "application/octet-stream", content.length);
        assertThat(file).isNotNull();
    }

    @Test
    @DisplayName("put with deeply nested path")
    void putWithDeeplyNestedPath() throws IOException {
        byte[] content = "deep".getBytes();
        String deepKey = "a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/file.txt";

        service.put(deepKey, content, "text/plain");

        StoredFile file = service.get(deepKey, "text/plain", content.length);
        assertThat(file).isNotNull();
    }

    @Test
    @DisplayName("get returns open input stream")
    void getReturnsOpenInputStream() throws IOException {
        byte[] content = "stream test".getBytes();
        String key = "stream/test.txt";
        service.put(key, content, "text/plain");

        StoredFile file = service.get(key, "text/plain", content.length);

        assertThat(file.content()).isNotNull();
        assertThat(file.content().available()).isGreaterThan(0);
    }

    @Test
    @DisplayName("put creates directories with correct permissions")
    void putCreatesDirectoriesWithCorrectPermissions() throws IOException {
        byte[] content = "perm test".getBytes();
        String key = "permissions/nested/directory/file.txt";

        service.put(key, content, "text/plain");

        Path parent = tempDir.resolve("permissions/nested/directory");
        assertThat(Files.isDirectory(parent)).isTrue();
        assertThat(Files.isReadable(parent)).isTrue();
        assertThat(Files.isWritable(parent)).isTrue();
    }
}
