package com.example.ttslab.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ttslab.audiobooks.model.AudioAssetType;
import org.junit.jupiter.api.Test;

class StorageKeyBuilderTest {
    @Test
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
    void speechSegmentKeysUseSpeechSegmentScopedVersionedPath() {
        StorageKeyBuilder builder = new StorageKeyBuilder(new StorageProperties(
            "./data/audio",
            "studio",
            "dev",
            "dev"
        ));

        String key = builder.speechSegmentMp3("user-a", "project-b", "speech-segment-c", 3);

        assertThat(key).isEqualTo("studio/dev/dev/users/user-a/projects/project-b/speech-segments/speech-segment-c/v3.mp3");
    }
}
