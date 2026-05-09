package com.example.ttslab.storage;

import com.example.ttslab.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FileSystemFileStorageService implements FileStorageService {
    private final StorageProperties storageProperties;

    public FileSystemFileStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void put(String storageKey, byte[] content, String contentType) throws IOException {
        Path path = pathFor(storageKey);
        Files.createDirectories(path.getParent());
        Files.write(path, content);
    }

    @Override
    public StoredFile get(String storageKey, String contentType, long sizeBytes) throws IOException {
        Path path = pathFor(storageKey);
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AUDIO_ASSET_FILE_NOT_FOUND", "The requested audio file is not available.");
        }
        return new StoredFile(Files.newInputStream(path), contentType, sizeBytes);
    }

    private Path pathFor(String storageKey) {
        Path root = storageProperties.rootPath().toAbsolutePath().normalize();
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_KEY", "The requested audio file is invalid.");
        }
        return path;
    }
}
