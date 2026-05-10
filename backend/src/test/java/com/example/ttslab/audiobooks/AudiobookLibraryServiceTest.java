package com.example.ttslab.audiobooks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.projects.ttsworkbench.TtsAudioFile;
import com.example.ttslab.storage.FileStorageService;
import com.example.ttslab.storage.StorageKeyBuilder;
import com.example.ttslab.storage.StorageProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AudiobookLibraryServiceTest {
    @Test
    void rejectsDownloadWhenAssetDoesNotBelongToCurrentUser() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        when(repository.findAssetForUser("project-1", "asset-1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assetForDownload(user, "project-1", "asset-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The requested audio asset was not found.");
    }

    @Test
    void rejectsDownloadWhenAssetIsNotReady() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");
        AudioAsset asset = new AudioAsset("asset-1", "project-1", null, AudioAssetType.PREVIEW_MP3, 1, "key", "a.mp3", "audio/mpeg", 3, null, AudioAssetStatus.GENERATING, Instant.now());

        when(repository.findAssetForUser("project-1", "asset-1", "user-1")).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.assetForDownload(user, "project-1", "asset-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The requested audio asset is not ready yet.");
    }

    @Test
    void listComputesMetadataFromAudioAssets() {
        // Test that list() calls the metadata calculator instead of reading persisted values
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        // Create a project with stale metadata (0, 0, 0)
        AudiobookProject project = new AudiobookProject(
            "proj-1",
            "user-1",
            "Test Audiobook",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS_WORKBENCH",
            0,  // Stale sceneCount
            0,  // Stale speakerCount
            0,  // Stale totalDurationSeconds
            Instant.now(),
            Instant.now()
        );

        // Create 3 assets with 2 unique speakers and 19s total duration
        List<AudioAsset> assets = List.of(
            new AudioAsset("asset-1", "proj-1", "scene-1", AudioAssetType.PREVIEW_MP3, 1, "key-1", "segment-1-narrator.mp3", "audio/mpeg", 1000L, 6, AudioAssetStatus.READY, Instant.now()),
            new AudioAsset("asset-2", "proj-1", "scene-2", AudioAssetType.PREVIEW_MP3, 1, "key-2", "segment-2-mara.mp3", "audio/mpeg", 1000L, 5, AudioAssetStatus.READY, Instant.now()),
            new AudioAsset("asset-3", "proj-1", "scene-3", AudioAssetType.PREVIEW_MP3, 1, "key-3", "segment-3-narrator.mp3", "audio/mpeg", 1000L, 8, AudioAssetStatus.READY, Instant.now())
        );

        when(repository.findProjectsForUser("user-1")).thenReturn(List.of(project));
        when(repository.findAssets("proj-1")).thenReturn(assets);

        AudiobookSummaryResponse response = service.list(user);

        assertEquals(1, response.items().size());
        AudiobookSummaryResponse.AudiobookSummaryItem item = response.items().get(0);

        // Verify metadata was calculated from assets, not read from project
        assertEquals(3, item.sceneCount(), "Should calculate 3 segments from 3 ready assets");
        assertEquals(2, item.speakerCount(), "Should calculate 2 unique speakers (narrator, mara)");
        assertEquals(19, item.totalDurationSeconds(), "Should calculate 19 seconds total (6+5+8)");
    }

    @Test
    void persistAudioAssetDoesNotUpdateProjectMetadata() throws Exception {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );

        AudiobookProject project = new AudiobookProject(
            "proj-1",
            "user-1",
            "Test Audiobook",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS_WORKBENCH",
            0,
            null,
            null,
            Instant.now(),
            Instant.now()
        );

        TtsAudioFile audioFile = new TtsAudioFile(new byte[] {1, 2, 3}, "audio/mpeg", "test.mp3");

        service.persistAudioAsset(project, audioFile, 3, 1, 2, 19);

        // IMPORTANT: Verify updateProjectMetadata is NEVER called
        verify(repository, never()).updateProjectMetadata(any(), any(), any(), any());

        // But verify asset and scene were created
        verify(repository).addScene(any());
        verify(repository).addAsset(any());
    }

    @Test
    void getProjectForUserThrowsNotFoundWhenProjectDoesNotExist() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        when(repository.findProjectForUser("missing-proj", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProjectForUser("missing-proj", user))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The audiobook project was not found.");
    }

    @Test
    void detailReturnsProjectWithScenesAndAssets() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        AudiobookProject project = new AudiobookProject(
            "proj-1",
            "user-1",
            "Test Book",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS",
            2,
            1,
            60,
            Instant.now(),
            Instant.now()
        );

        List<AudiobookSpeechSegment> scenes = List.of(
            new AudiobookSpeechSegment(
                "scene-1",
                "proj-1",
                1,
                "Opening",
                AudiobookSpeechSegmentReviewStatus.APPROVED,
                30,
                Instant.now(),
                Instant.now(),
                "Narrator",
                "Story voice",
                "Clear voice",
                "[calm] Once upon a time"
            ),
            new AudiobookSpeechSegment(
                "scene-2",
                "proj-1",
                2,
                "Adventure",
                AudiobookSpeechSegmentReviewStatus.PENDING,
                30,
                Instant.now(),
                Instant.now(),
                "Hero",
                "Brave character",
                "Strong voice",
                "[excited] Let's go!"
            )
        );

        List<AudioAsset> assets = List.of(
            new AudioAsset(
                "asset-1",
                "proj-1",
                "scene-1",
                AudioAssetType.PREVIEW_MP3,
                1,
                "key-1",
                "scene1.mp3",
                "audio/mpeg",
                5000L,
                30,
                AudioAssetStatus.READY,
                Instant.now()
            )
        );

        when(repository.findProjectForUser("proj-1", "user-1")).thenReturn(Optional.of(project));
        when(repository.findScenes("proj-1")).thenReturn(scenes);
        when(repository.findAssets("proj-1")).thenReturn(assets);

        AudiobookDetailResponse detail = service.detail(user, "proj-1");

        assertEquals("proj-1", detail.id());
        assertEquals("Test Book", detail.title());
        assertEquals(2, detail.scenes().size());
        assertEquals(1, detail.audioAssets().size());
        assertEquals("Opening", detail.scenes().get(0).title());
        assertEquals("scene1.mp3", detail.audioAssets().get(0).filename());
    }

    @Test
    void detailThrowsNotFoundWhenProjectDoesNotBelongToUser() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        when(repository.findProjectForUser("proj-1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(user, "proj-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The requested audiobook was not found.");
    }

    @Test
    void createProjectForGenerationCreatesProjectWithTimestamp() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        Instant before = Instant.now();
        AudiobookProject project = service.createProjectForGeneration(user);
        Instant after = Instant.now();

        assertEquals("user-1", project.userId());
        assertEquals(AudiobookProjectStatus.NEEDS_REVIEW, project.status());
        assertEquals("TTS_WORKBENCH", project.sourceType());
        assertEquals(0, project.sceneCount());
        assertEquals(null, project.speakerCount());
        assertEquals(null, project.totalDurationSeconds());
        assertTrue(project.title().startsWith("Generated audiobook "));

        verify(repository).createProject(project);
    }

    @Test
    void persistGeneratedPreviewCreatesCompleteProjectWithSceneAndAsset() throws Exception {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        TtsAudioFile audioFile = new TtsAudioFile(
            new byte[] {1, 2, 3, 4, 5},
            "audio/mpeg",
            "preview.mp3"
        );

        service.persistGeneratedPreview(user, audioFile, 1, 120);

        // Verify transactional method was called with proper arguments
        verify(repository).createProjectWithAsset(any(AudiobookProject.class), any(AudiobookSpeechSegment.class), any(AudioAsset.class));
    }

    @Test
    void persistAudioAssetWithSpeakerMetadataCreatesSceneWithAllFields() throws Exception {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch"))
        );

        AudiobookProject project = new AudiobookProject(
            "proj-1",
            "user-1",
            "Test",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS",
            0,
            null,
            null,
            Instant.now(),
            Instant.now()
        );

        TtsAudioFile audioFile = new TtsAudioFile(new byte[] {1, 2, 3}, "audio/mpeg", "test.mp3");

        AudioAsset asset = service.persistAudioAsset(
            project,
            audioFile,
            3,
            1,
            2,
            90,
            "Alice",
            "Brave explorer",
            "Kore",
            "[enthusiastic] Let's explore!"
        );

        assertEquals("test.mp3", asset.filename());
        assertEquals(3, asset.sizeBytes());
        assertEquals(AudioAssetStatus.READY, asset.status());

        verify(repository).addScene(any());
        verify(repository).addAsset(any());
    }
}
