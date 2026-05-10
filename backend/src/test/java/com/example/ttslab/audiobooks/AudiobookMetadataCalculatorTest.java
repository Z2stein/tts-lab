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
    void calculateSceneCountReturnsNumberOfSegments() {
        List<SpeechSegment> segments = List.of(
            createSegment("seg-1"),
            createSegment("seg-2"),
            createSegment("seg-3")
        );
        when(repository.findSegmentsForProject("project-1")).thenReturn(segments);

        int count = calculator.calculateSceneCount("project-1");

        assertEquals(3, count);
    }

    @Test
    void calculateSceneCountReturnsZeroForNoSegments() {
        when(repository.findSegmentsForProject("project-1")).thenReturn(List.of());

        int count = calculator.calculateSceneCount("project-1");

        assertEquals(0, count);
    }

    @Test
    void calculateSpeakerCountReturnsNumberOfCharacters() {
        List<Character> characters = List.of(
            createCharacter("char-1", "Alice"),
            createCharacter("char-2", "Bob"),
            createCharacter("char-3", "Charlie")
        );
        when(repository.findCharactersForProject("project-1")).thenReturn(characters);

        int count = calculator.calculateSpeakerCount("project-1");

        assertEquals(3, count);
    }

    @Test
    void calculateSpeakerCountReturnsZeroForNoCharacters() {
        when(repository.findCharactersForProject("project-1")).thenReturn(List.of());

        int count = calculator.calculateSpeakerCount("project-1");

        assertEquals(0, count);
    }

    @Test
    void calculateTotalDurationSumAssetDurationsInSeconds() {
        // 6000ms + 5000ms + 8000ms = 19000ms = 19s
        List<AudioAsset> assets = List.of(
            createAssetWithDuration("asset-1", 6000L),
            createAssetWithDuration("asset-2", 5000L),
            createAssetWithDuration("asset-3", 8000L)
        );
        when(repository.findAssetsForProject("project-1")).thenReturn(assets);

        int duration = calculator.calculateTotalDurationSeconds("project-1");

        assertEquals(19, duration);
    }

    @Test
    void calculateTotalDurationHandlesNullDurations() {
        List<AudioAsset> assets = List.of(
            createAssetWithDuration("asset-1", 6000L),
            createAssetWithDuration("asset-2", null),
            createAssetWithDuration("asset-3", 8000L)
        );
        when(repository.findAssetsForProject("project-1")).thenReturn(assets);

        int duration = calculator.calculateTotalDurationSeconds("project-1");

        assertEquals(14, duration); // 6 + 0 (null) + 8 = 14 seconds
    }

    @Test
    void calculateTotalDurationReturnsZeroForNoAssets() {
        when(repository.findAssetsForProject("project-1")).thenReturn(List.of());

        int duration = calculator.calculateTotalDurationSeconds("project-1");

        assertEquals(0, duration);
    }

    @Test
    void calculateMetadataIntegration() {
        // Simulate real scenario: 3 segments, 2 characters, 3 assets (19s total)
        List<SpeechSegment> segments = List.of(
            createSegment("seg-1"),
            createSegment("seg-2"),
            createSegment("seg-3")
        );
        List<Character> characters = List.of(
            createCharacter("char-1", "Alice"),
            createCharacter("char-2", "Bob")
        );
        List<AudioAsset> assets = List.of(
            createAssetWithDuration("asset-1", 6000L),
            createAssetWithDuration("asset-2", 5000L),
            createAssetWithDuration("asset-3", 8000L)
        );

        when(repository.findSegmentsForProject("project-1")).thenReturn(segments);
        when(repository.findCharactersForProject("project-1")).thenReturn(characters);
        when(repository.findAssetsForProject("project-1")).thenReturn(assets);

        int sceneCount = calculator.calculateSceneCount("project-1");
        int speakerCount = calculator.calculateSpeakerCount("project-1");
        int duration = calculator.calculateTotalDurationSeconds("project-1");

        assertEquals(3, sceneCount); // 3 segments
        assertEquals(2, speakerCount); // 2 characters
        assertEquals(19, duration); // 19 seconds
    }

    // Helper methods for creating test data
    private SpeechSegment createSegment(String id) {
        return new SpeechSegment(
            id, "project-1", "char-1", 1, "Test text", null, false, false,
            Instant.now(), Instant.now()
        );
    }

    private Character createCharacter(String id, String name) {
        return new Character(
            id, "project-1", name, "Role description", "voice-key", 1, false,
            Instant.now(), Instant.now()
        );
    }

    private AudioAsset createAssetWithDuration(String id, Long durationMs) {
        return new AudioAsset(
            id, "project-1", null, AudioAssetType.SEGMENT_AUDIO,
            id + ".mp3", "s3://bucket/key", "audio/mpeg",
            durationMs, 1000000L, Instant.now()
        );
    }
}
