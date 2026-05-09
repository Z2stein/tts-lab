package com.example.ttslab.storage;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StorageProperties {
    private final Path rootPath;
    private final String appSlug;
    private final String environment;
    private final String branchSlug;

    public StorageProperties(
        @Value("${audiobooks.storage.root:${AUDIOBOOK_STORAGE_ROOT:./data/audio-storage}}") String root,
        @Value("${audiobooks.storage.app-slug:${APP_SLUG:tts-lab}}") String appSlug,
        @Value("${audiobooks.storage.environment:${ENVIRONMENT:feature}}") String environment,
        @Value("${audiobooks.storage.branch-slug:${BRANCH_SLUG:${ENVIRONMENT:feature}}}") String branchSlug
    ) {
        this.rootPath = Path.of(root);
        this.appSlug = appSlug;
        this.environment = environment;
        this.branchSlug = branchSlug;
    }

    public Path rootPath() {
        return rootPath;
    }

    public String appSlug() {
        return appSlug;
    }

    public String environment() {
        return environment;
    }

    public String branchSlug() {
        return branchSlug;
    }
}
