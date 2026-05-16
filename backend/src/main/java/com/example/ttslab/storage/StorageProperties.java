package com.example.ttslab.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audiobooks.storage")
public record StorageProperties(
    String root,
    String appSlug,
    String environment,
    String branchSlug
) {
    public StorageProperties {
        root = normalize(root, "./data/audio-storage");
        appSlug = normalize(appSlug, "tts-lab");
        environment = normalize(environment, "feature");
        branchSlug = normalize(branchSlug, environment);
    }

    public Path rootPath() {
        return Path.of(root);
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
