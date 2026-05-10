package com.example.ttslab.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ttslab.audiobooks.AudioAssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StorageKeyBuilder")
class StorageKeyBuilderTest {
    @Test
    @DisplayName("separates keys by app environment branch user and project")
    void separatesKeysByAppEnvironmentBranchUserAndProject() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data/audio",
            "tts-lab",
            "feature",
            "f-library-42"
        ));

        String key = builder.projectAsset("User 1", "Project 9", AudioAssetType.FULL_AUDIOBOOK, 2, "mp3");

        assertThat(key).isEqualTo("tts-lab/feature/f-library-42/users/user-1/projects/project-9/exports/audiobook-v2.mp3");
    }

    @Test
    @DisplayName("scene keys use scene scoped versioned path")
    void sceneKeysUseSceneScopedVersionedPath() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data/audio",
            "studio",
            "dev",
            "dev"
        ));

        String key = builder.sceneMp3("user-a", "project-b", "scene-c", 3);

        assertThat(key).isEqualTo("studio/dev/dev/users/user-a/projects/project-b/scenes/scene-c/v3.mp3");
    }

    @Test
    @DisplayName("projectAsset uses exports folder for FULL_AUDIOBOOK type")
    void projectAssetUsesExportsFolderForFullAudiobook() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "app",
            "prod",
            "main"
        ));

        String key = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "zip");

        assertThat(key).contains("/exports/audiobook-v1.zip");
    }

    @Test
    @DisplayName("projectAsset uses assets folder for non-FULL_AUDIOBOOK types")
    void projectAssetUsesAssetsFolderForOtherTypes() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "app",
            "prod",
            "main"
        ));

        String key = builder.projectAsset("user-1", "project-1", AudioAssetType.PREVIEW_MP3, 1, "mp3");

        assertThat(key).contains("/assets/preview_mp3-v1.mp3");
    }

    @Test
    @DisplayName("slug converts to lowercase")
    void slugConvertsToLowercase() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "TTS-LAB",
            "PROD",
            "MAIN"
        ));

        String key = builder.projectAsset("USER", "PROJECT", AudioAssetType.FULL_AUDIOBOOK, 1, "mp3");

        assertThat(key).containsIgnoringCase("tts-lab");
        assertThat(key).containsIgnoringCase("prod");
    }

    @Test
    @DisplayName("slug removes special characters")
    void slugRemovesSpecialCharacters() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "tts@lab#2024",
            "test-env",
            "main"
        ));

        String key = builder.projectAsset("user@1", "project#1", AudioAssetType.FULL_AUDIOBOOK, 1, "mp3");

        assertThat(key).doesNotContain("@");
        assertThat(key).doesNotContain("#");
        assertThat(key).contains("user-1");
        assertThat(key).contains("project-1");
    }

    @Test
    @DisplayName("slug handles null values")
    void slugHandlesNullValues() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            null,
            null,
            null
        ));

        String key = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "mp3");

        assertThat(key).contains("unknown");
    }

    @Test
    @DisplayName("slug trims whitespace")
    void slugTrimsWhitespace() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "  tts-lab  ",
            "  prod  ",
            "  main  "
        ));

        String key = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "mp3");

        assertThat(key).contains("tts-lab");
    }

    @Test
    @DisplayName("slug removes consecutive hyphens")
    void slugRemovesConsecutiveHyphens() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "tts---lab",
            "prod",
            "main"
        ));

        String key = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "mp3");

        assertThat(key).contains("tts-lab");
        assertThat(key).doesNotContain("---");
    }

    @Test
    @DisplayName("slug removes leading and trailing hyphens")
    void slugRemovesLeadingTrailingHyphens() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "-tts-lab-",
            "-prod-",
            "-main-"
        ));

        String key = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "mp3");

        assertThat(key).contains("tts-lab");
        assertThat(key).doesNotContain("-tts-");
    }

    @Test
    @DisplayName("projectAsset handles different audio asset types")
    void projectAssetHandlesDifferentAssetTypes() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "app",
            "prod",
            "main"
        ));

        String previewMp3 = builder.projectAsset("user-1", "project-1", AudioAssetType.PREVIEW_MP3, 1, "mp3");
        String sceneMp3 = builder.projectAsset("user-1", "project-1", AudioAssetType.SCENE_MP3, 1, "mp3");
        String fullAudiobook = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "zip");

        assertThat(previewMp3).contains("preview_mp3");
        assertThat(sceneMp3).contains("scene_mp3");
        assertThat(fullAudiobook).contains("audiobook");
    }

    @Test
    @DisplayName("projectAsset includes version number")
    void projectAssetIncludesVersionNumber() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "app",
            "prod",
            "main"
        ));

        String v1 = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "mp3");
        String v5 = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 5, "mp3");

        assertThat(v1).contains("-v1.");
        assertThat(v5).contains("-v5.");
    }

    @Test
    @DisplayName("sceneMp3 includes version number")
    void sceneMp3IncludesVersionNumber() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "app",
            "prod",
            "main"
        ));

        String v1 = builder.sceneMp3("user-1", "project-1", "scene-1", 1);
        String v10 = builder.sceneMp3("user-1", "project-1", "scene-1", 10);

        assertThat(v1).contains("/v1.mp3");
        assertThat(v10).contains("/v10.mp3");
    }

    @Test
    @DisplayName("projectAsset preserves file extension")
    void projectAssetPreservesFileExtension() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data",
            "app",
            "prod",
            "main"
        ));

        String mp3 = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "mp3");
        String wav = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "wav");
        String zip = builder.projectAsset("user-1", "project-1", AudioAssetType.FULL_AUDIOBOOK, 1, "zip");

        assertThat(mp3).endsWith(".mp3");
        assertThat(wav).endsWith(".wav");
        assertThat(zip).endsWith(".zip");
    }
}
