package com.example.ttslab.storage;

import com.example.ttslab.audiobooks.AudioAssetType;
import org.springframework.stereotype.Component;

@Component
public class StorageKeyBuilder {
    private final StorageProperties storageProperties;

    public StorageKeyBuilder(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public String sceneMp3(String userId, String projectId, String sceneId, int version) {
        return base(userId, projectId) + "/scenes/" + slug(sceneId) + "/v" + version + ".mp3";
    }

    public String projectAsset(String userId, String projectId, AudioAssetType type, int version, String extension) {
        String folder = type == AudioAssetType.FULL_AUDIOBOOK ? "exports" : "assets";
        String filePrefix = type == AudioAssetType.FULL_AUDIOBOOK ? "audiobook" : type.name().toLowerCase();
        return base(userId, projectId) + "/" + folder + "/" + filePrefix + "-v" + version + "." + slug(extension);
    }

    private String base(String userId, String projectId) {
        return String.join("/",
            slug(storageProperties.appSlug()),
            slug(storageProperties.environment()),
            slug(storageProperties.branchSlug()),
            "users",
            slug(userId),
            "projects",
            slug(projectId)
        );
    }

    private String slug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
