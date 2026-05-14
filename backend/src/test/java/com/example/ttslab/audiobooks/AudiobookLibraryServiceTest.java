package com.example.ttslab.audiobooks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ttslab.audiobooks.dto.AudiobookSummaryResponse;
import com.example.ttslab.audiobooks.model.*;
import com.example.ttslab.audiobooks.repository.AudioAssetRepository;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudiobookRepository;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.audiobooks.service.AudiobookMetadataCalculator;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.audiobooks.workflow.TtsAudioFile;
import com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderRequest;
import com.example.ttslab.storage.FileStorageService;
import com.example.ttslab.storage.StorageKeyBuilder;
import com.example.ttslab.storage.StorageProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

class AudiobookLibraryServiceTest {
    @Test
    void rejectsDownloadWhenAssetDoesNotBelongToCurrentUser() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = Mockito.mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = Mockito.mock(AudiobookSpeechSegmentRepository.class);
        AudioAssetRepository assetRepository = Mockito.mock(AudioAssetRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            projectRepository,
            segmentRepository,
            assetRepository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch")),
            new AudiobookMetadataCalculator(repository)
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
        AudiobookProjectRepository projectRepository = Mockito.mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = Mockito.mock(AudiobookSpeechSegmentRepository.class);
        AudioAssetRepository assetRepository = Mockito.mock(AudioAssetRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            projectRepository,
            segmentRepository,
            assetRepository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch")),
            new AudiobookMetadataCalculator(repository)
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
        AudiobookProjectRepository projectRepository = Mockito.mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = Mockito.mock(AudiobookSpeechSegmentRepository.class);
        AudioAssetRepository assetRepository = Mockito.mock(AudioAssetRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            projectRepository,
            segmentRepository,
            assetRepository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch")),
            new AudiobookMetadataCalculator(repository)
        );
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        // Create a project with stale metadata (0, 0, 0)
        AudiobookProject project = new AudiobookProject(
            "proj-1",
            "user-1",
            "Test Audiobook",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,  // Stale speechSegmentCount
            0,  // Stale speakerCount
            0,  // Stale totalDurationSeconds
            Instant.now(),
            Instant.now()
        );

        // Create 3 assets with 2 unique speakers and 19s total duration
        List<AudioAsset> assets = List.of(
            new AudioAsset("asset-1", "proj-1", "speech-segment-1", AudioAssetType.PREVIEW_MP3, 1, "key-1", "segment-1-narrator.mp3", "audio/mpeg", 1000L, 6, AudioAssetStatus.READY, Instant.now()),
            new AudioAsset("asset-2", "proj-1", "speech-segment-2", AudioAssetType.PREVIEW_MP3, 1, "key-2", "segment-2-mara.mp3", "audio/mpeg", 1000L, 5, AudioAssetStatus.READY, Instant.now()),
            new AudioAsset("asset-3", "proj-1", "speech-segment-3", AudioAssetType.PREVIEW_MP3, 1, "key-3", "segment-3-narrator.mp3", "audio/mpeg", 1000L, 8, AudioAssetStatus.READY, Instant.now())
        );

        when(repository.findProjectsForUser("user-1")).thenReturn(List.of(project));
        when(repository.findAssets("proj-1")).thenReturn(assets);

        AudiobookSummaryResponse response = service.list(user);

        assertEquals(1, response.items().size());
        AudiobookSummaryResponse.AudiobookSummaryItem item = response.items().get(0);

        // Verify metadata was calculated from assets, not read from project
        assertEquals(3, item.speechSegmentCount(), "Should calculate 3 segments from 3 ready assets");
        assertEquals(2, item.speakerCount(), "Should calculate 2 unique speakers (narrator, mara)");
        assertEquals(19, item.totalDurationSeconds(), "Should calculate 19 seconds total (6+5+8)");
    }

    @Test
    void persistAudioAssetDoesNotUpdateProjectMetadata() throws Exception {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = Mockito.mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = Mockito.mock(AudiobookSpeechSegmentRepository.class);
        AudioAssetRepository assetRepository = Mockito.mock(AudioAssetRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            projectRepository,
            segmentRepository,
            assetRepository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch")),
            new AudiobookMetadataCalculator(repository)
        );

        AudiobookProject project = new AudiobookProject(
            "proj-1",
            "user-1",
            "Test Audiobook",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
        AudiobookSpeechSegment previewSegment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null,
            null,
            "Hello",
            null,
            null
        );
        previewSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);

        when(segmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex("proj-1", AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW))
            .thenReturn(List.of(previewSegment));
        when(assetRepository.findBySegmentId("segment-1")).thenReturn(List.of());

        TtsAudioFile audioFile = new TtsAudioFile(new byte[] {1, 2, 3}, "audio/mpeg", "test.mp3");
        SingleSpeakerRenderRequest renderRequest = new SingleSpeakerRenderRequest(
            java.util.Map.of("text", "Hello", "segmentOrderIndex", 0),
            java.util.Map.of("speakerName", "Narrator", "name", "Kore"),
            java.util.Map.of()
        );

        service.persistAudioAsset(project, audioFile, renderRequest, 3, 1, 2, 19);

        // IMPORTANT: Verify updateProjectMetadata is NEVER called
        verify(repository, never()).updateProjectMetadata(any(), any(), any(), any());
        verify(segmentRepository, never()).save(any());

        // Verify asset and speech segment were created using JPA repositories
        ArgumentCaptor<AudioAsset> assetCaptor = ArgumentCaptor.forClass(AudioAsset.class);
        verify(assetRepository).save(assetCaptor.capture());
        assertEquals("segment-1", assetCaptor.getValue().getSpeechSegmentId());
        assertEquals(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW, previewSegment.getSegmentOrigin());
    }

    @Test
    void rejectsMissingOrInvalidSegmentOrderIndex() {
        AudiobookRepository repository = Mockito.mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = Mockito.mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = Mockito.mock(AudiobookSpeechSegmentRepository.class);
        AudioAssetRepository assetRepository = Mockito.mock(AudioAssetRepository.class);
        FileStorageService storage = Mockito.mock(FileStorageService.class);
        AudiobookLibraryService service = new AudiobookLibraryService(
            repository,
            projectRepository,
            segmentRepository,
            assetRepository,
            storage,
            new StorageKeyBuilder(new StorageProperties("./data", "app", "feature", "branch")),
            new AudiobookMetadataCalculator(repository)
        );
        AudiobookProject project = new AudiobookProject(
            "proj-1",
            "user-1",
            "Test Audiobook",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
        AudiobookSpeechSegment previewSegment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.now(),
            Instant.now(),
            "Narrator",
            null,
            "Kore",
            null,
            "Hello",
            null,
            null
        );
        previewSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);

        when(segmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex("proj-1", AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW))
            .thenReturn(List.of(previewSegment));

        TtsAudioFile audioFile = new TtsAudioFile(new byte[] {1, 2, 3}, "audio/mpeg", "test.mp3");

        assertThatThrownBy(() -> service.persistAudioAsset(
            project,
            audioFile,
            new SingleSpeakerRenderRequest(java.util.Map.of("text", "Hello"), java.util.Map.of("name", "Kore"), java.util.Map.of()),
            3,
            1,
            2,
            19
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("segment order index");

        assertThatThrownBy(() -> service.persistAudioAsset(
            project,
            audioFile,
            new SingleSpeakerRenderRequest(java.util.Map.of("text", "Hello", "segmentOrderIndex", -1), java.util.Map.of("name", "Kore"), java.util.Map.of()),
            3,
            1,
            2,
            19
        ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("segment order index");
    }
}


