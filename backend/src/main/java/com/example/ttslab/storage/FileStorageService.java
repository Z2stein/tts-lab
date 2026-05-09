package com.example.ttslab.storage;

import java.io.IOException;

public interface FileStorageService {
    void put(String storageKey, byte[] content, String contentType) throws IOException;

    StoredFile get(String storageKey, String contentType, long sizeBytes) throws IOException;
}
