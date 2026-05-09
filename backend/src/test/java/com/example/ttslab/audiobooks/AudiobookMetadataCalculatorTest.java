package com.example.ttslab.audiobooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AudiobookMetadataCalculatorTest {
    @Mock
    private AudiobookRepository repository;

    private AudiobookMetadataCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AudiobookMetadataCalculator(repository);
    }

    @Test
    void calculateSceneCountReturnsNumberOfReadyAssets() {
        // Setup: 3 READY assets, 1 GENERATING asset
        List<AudioAsset> assets = List.of(
            createAsset("asset-1", AudioAssetStatus.READY),
            createAsset("asset-2", AudioAssetStatus.READY),
            createAsset("asset-3", AudioAssetStatus.READY),
            createAsset("asset-4", AudioAssetStatus.GENERATING)
        );
        when(repository.findAssets("project-1")).thenReturn(assets);

        int count = calculator.calculateSceneCount("project-1");

        assertEquals(3, count);
    }

    @Test
    void calculateSceneCountReturnsZeroForNoReadyAssets() {
        List<AudioAsset> assets = List.of();
        when(repository.findAssets("project-1")).thenReturn(assets);

        int count = calculator.calculateSceneCount("project-1");

        assertEquals(0, count);
    }

    @Test
    void calculateSpeakerCountDeduplicatesRepeatedSpeakers() {
        // Setup: segment-1-narrator.mp3, segment-2-mara.mp3, segment-3-narrator.mp3
        List<AudioAsset> assets = List.of(
            createAsset("segment-1-narrator.mp3", AudioAssetStatus.READY),
            createAsset("segment-2-mara.mp3", AudioAssetStatus.READY),
            createAsset("segment-3-narrator.mp3", AudioAssetStatus.READY)
        );
        when(repository.findAssets("project-1")).thenReturn(assets);

        int count = calculator.calculateSpeakerCount("project-1");

        assertEquals(2, count); // narrator, mara
    }

    @Test
    void calculateSpeakerCountIgnoresGeneratingAssets() {
        List<AudioAsset> assets = List.of(
            createAsset("segment-1-narrator.mp3", AudioAssetStatus.READY),
            createAsset("segment-2-mara.mp3", AudioAssetStatus.GENERATING)
        );
        when(repository.findAssets("project-1")).thenReturn(assets);

        int count = calculator.calculateSpeakerCount("project-1");

        assertEquals(1, count); // only narrator (generating asset ignored)
    }

    @Test
    void calculateTotalDurationSumsSpeakingTime() {
        // Setup: 6s + 5s + 8s = 19s
        List<AudioAsset> assets = List.of(
            createAssetWithDuration("asset-1", 6, AudioAssetStatus.READY),
            createAssetWithDuration("asset-2", 5, AudioAssetStatus.READY),
            createAssetWithDuration("asset-3", 8, AudioAssetStatus.READY)
        );
        when(repository.findAssets("project-1")).thenReturn(assets);

        int duration = calculator.calculateTotalDurationSeconds("project-1");

        assertEquals(19, duration);
    }

    @Test
    void calculateTotalDurationHandlesNullDurations() {
        List<AudioAsset> assets = List.of(
            createAssetWithDuration("asset-1", 6, AudioAssetStatus.READY),
            createAssetWithDurationAndNull("asset-2", null, AudioAssetStatus.READY),
            createAssetWithDuration("asset-3", 8, AudioAssetStatus.READY)
        );
        when(repository.findAssets("project-1")).thenReturn(assets);

        int duration = calculator.calculateTotalDurationSeconds("project-1");

        assertEquals(14, duration); // 6 + null (0) + 8
    }

    @Test
    void calculateTotalDurationIgnoresGeneratingAssets() {
        List<AudioAsset> assets = List.of(
            createAssetWithDuration("asset-1", 6, AudioAssetStatus.READY),
            createAssetWithDuration("asset-2", 100, AudioAssetStatus.GENERATING)
        );
        when(repository.findAssets("project-1")).thenReturn(assets);

        int duration = calculator.calculateTotalDurationSeconds("project-1");

        assertEquals(6, duration); // only ready asset
    }

    @Test
    void calculateMetadataFor3SegmentsWith2Speakers() {
        // Integration test: simulate real scenario with 3 segments and 2 unique speakers
        List<AudioAsset> assets = List.of(
            createAssetWithDuration("segment-1-narrator.mp3", 6, AudioAssetStatus.READY),
            createAssetWithDuration("segment-2-mara.mp3", 5, AudioAssetStatus.READY),
            createAssetWithDuration("segment-3-narrator.mp3", 8, AudioAssetStatus.READY)
        );
        when(repository.findAssets("project-1")).thenReturn(assets);

        int sceneCount = calculator.calculateSceneCount("project-1");
        int speakerCount = calculator.calculateSpeakerCount("project-1");
        int duration = calculator.calculateTotalDurationSeconds("project-1");

        assertEquals(3, sceneCount); // 3 audio parts
        assertEquals(2, speakerCount); // narrator, mara
        assertEquals(19, duration); // 6 + 5 + 8
    }

    // Helper methods
    private AudioAsset createAsset(String filename, AudioAssetStatus status) {
        return new AudioAsset(
            "id-" + filename,
            "project-1",
            "scene-1",
            AudioAssetType.PREVIEW_MP3,
            1,
            "storage-key",
            filename,
            "audio/mpeg",
            1000L,
            null,
            status,
            Instant.now()
        );
    }

    private AudioAsset createAssetWithDuration(String filename, Integer duration, AudioAssetStatus status) {
        return new AudioAsset(
            "id-" + filename,
            "project-1",
            "scene-1",
            AudioAssetType.PREVIEW_MP3,
            1,
            "storage-key",
            filename,
            "audio/mpeg",
            1000L,
            duration,
            status,
            Instant.now()
        );
    }

    private AudioAsset createAssetWithDurationAndNull(String filename, Integer duration, AudioAssetStatus status) {
        return new AudioAsset(
            "id-" + filename,
            "project-1",
            "scene-1",
            AudioAssetType.PREVIEW_MP3,
            1,
            "storage-key",
            filename,
            "audio/mpeg",
            1000L,
            duration,
            status,
            Instant.now()
        );
    }
}
