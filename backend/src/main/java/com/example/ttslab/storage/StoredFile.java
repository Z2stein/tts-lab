package com.example.ttslab.storage;

import java.io.InputStream;

public record StoredFile(
    InputStream content,
    String contentType,
    long sizeBytes
) {
}
