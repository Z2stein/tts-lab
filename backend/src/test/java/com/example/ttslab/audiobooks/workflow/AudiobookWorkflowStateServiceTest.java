package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.audiobooks.model.AudioAssetType;
import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.workflow.SpeakerVoice;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudiobookRepository;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerCharacterRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisUpdateService;
import com.example.ttslab.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class AudiobookWorkflowStateServiceTest {
    @Test
    void snapshotMarksSavedAudioCurrentOnlyForGeneratedWorkflowStage() {
        AudiobookRepository repository = mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookWorkflowStateService service = service(repository, projectRepository, speakerCharacterRepository);
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        AudiobookProject project = project("project-1", AudiobookWorkflowStage.AUDIO_GENERATED);
        project.setAudioAssetsCurrent(true);
        AudiobookSpeechSegment segment = previewSegment(project, "Mara", "We go now.");
        AudioAsset audioAsset = new AudioAsset(
            "asset-1",
            "project-1",
            "segment-1",
            AudioAssetType.PREVIEW_MP3,
            1,
            "storage-key",
            "preview.mp3",
            "audio/mpeg",
            42L,
            12,
            AudioAssetStatus.READY,
            Instant.parse("2026-05-12T10:00:00Z")
        );

        when(repository.findProjectForUser("project-1", "user-1")).thenReturn(Optional.of(project));
        when(repository.findPreviewSpeechSegments("project-1")).thenReturn(List.of(segment));
        when(repository.findAssets("project-1")).thenReturn(List.of(audioAsset));
        when(speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc("project-1")).thenReturn(List.of());

        AudiobookWorkflowSnapshotResponse generated = service.snapshot(user, "project-1");

        assertThat(generated.audioAssetsCurrent()).isTrue();
        assertThat(generated.performanceNotesStale()).isFalse();

        AudiobookProject staleProject = project("project-2", AudiobookWorkflowStage.SCRIPT_REVIEW);
        AudiobookSpeechSegment staleSegment = previewSegment(staleProject, "Mara", "We go now.");

        when(repository.findProjectForUser("project-2", "user-1")).thenReturn(Optional.of(staleProject));
        when(repository.findPreviewSpeechSegments("project-2")).thenReturn(List.of(staleSegment));
        when(repository.findAssets("project-2")).thenReturn(List.of(audioAsset));

        AudiobookWorkflowSnapshotResponse stale = service.snapshot(user, "project-2");

        assertThat(stale.audioAssetsCurrent()).isFalse();
        assertThat(stale.performanceNotesStale()).isFalse();
    }

    @Test
    void snapshotMarksPerformanceNotesStaleWhenPreviewRowsNeedChanges() {
        AudiobookRepository repository = mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookWorkflowStateService service = service(repository, projectRepository, speakerCharacterRepository);
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        AudiobookProject project = project("project-3", AudiobookWorkflowStage.SCRIPT_REVIEW);
        AudiobookSpeechSegment segment = previewSegment(project, "Mara", "We go now.");
        segment.setReviewStatus(com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.NEEDS_CHANGES);

        when(repository.findProjectForUser("project-3", "user-1")).thenReturn(Optional.of(project));
        when(repository.findPreviewSpeechSegments("project-3")).thenReturn(List.of(segment));
        when(repository.findAssets("project-3")).thenReturn(List.of());
        when(speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc("project-3")).thenReturn(List.of());

        AudiobookWorkflowSnapshotResponse stale = service.snapshot(user, "project-3");

        assertThat(stale.performanceNotesStale()).isTrue();
    }

    @Test
    void approveCastRejectsProjectsThatAreNotInCastReview() {
        AudiobookRepository repository = mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookWorkflowStateService service = service(repository, projectRepository, speakerCharacterRepository);
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        AudiobookProject project = project("project-4", AudiobookWorkflowStage.CAST_APPROVED);
        when(repository.findProjectForUser("project-4", "user-1")).thenReturn(Optional.of(project));
        when(repository.findPreviewSpeechSegments("project-4")).thenReturn(List.of());
        when(repository.findAssets("project-4")).thenReturn(List.of());
        when(speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc("project-4")).thenReturn(List.of());

        assertThatThrownBy(() -> service.approveCast(user, "project-4"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The cast must be created before it can be approved.");
        verify(projectRepository, never()).save(project);
    }

    @Test
    void approveScriptRejectsProjectsThatAreNotInScriptReview() {
        AudiobookRepository repository = mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookWorkflowStateService service = service(repository, projectRepository, speakerCharacterRepository);
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        AudiobookProject project = project("project-5", AudiobookWorkflowStage.CAST_APPROVED);
        AudiobookSpeechSegment segment = previewSegment(project, "Mara", "We go now.");

        when(repository.findProjectForUser("project-5", "user-1")).thenReturn(Optional.of(project));
        when(repository.findPreviewSpeechSegments("project-5")).thenReturn(List.of(segment));
        when(repository.findAssets("project-5")).thenReturn(List.of());
        when(speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc("project-5")).thenReturn(List.of());

        assertThatThrownBy(() -> service.approveScript(user, "project-5"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The script must be created before it can be approved.");
        verify(projectRepository, never()).save(project);
    }

    @Test
    void markPerformanceReadyRejectsProjectsThatHaveNotApprovedTheScript() {
        AudiobookRepository repository = mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookWorkflowStateService service = service(repository, projectRepository, speakerCharacterRepository);

        AudiobookProject project = project("project-6", AudiobookWorkflowStage.CAST_APPROVED);

        assertThatThrownBy(() -> service.markPerformanceReady(project))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The script must be approved before emotion and pacing can be saved.");
        verify(projectRepository, never()).save(project);
    }

    @Test
    void markAudioGeneratedRejectsProjectsThatHaveNotPreparedPerformance() {
        AudiobookRepository repository = mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookWorkflowStateService service = service(repository, projectRepository, speakerCharacterRepository);

        AudiobookProject project = project("project-7", AudiobookWorkflowStage.SCRIPT_APPROVED);

        assertThatThrownBy(() -> service.markAudioGenerated(project))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Emotion and pacing must be saved before audio can be marked as generated.");
        verify(projectRepository, never()).save(project);
    }

    @Test
    void finalizeAudioGenerationMarksAnAlreadyGeneratedProjectCurrent() {
        AudiobookRepository repository = mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookWorkflowStateService service = service(repository, projectRepository, speakerCharacterRepository);
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        AudiobookProject project = project("project-8", AudiobookWorkflowStage.AUDIO_GENERATED);
        project.setAudioAssetsCurrent(false);
        AudiobookSpeechSegment segment = previewSegment(project, "Mara", "We go now.");
        AudioAsset audioAsset = new AudioAsset(
            "asset-1",
            "project-8",
            "segment-1",
            AudioAssetType.PREVIEW_MP3,
            1,
            "storage-key",
            "preview.mp3",
            "audio/mpeg",
            42L,
            12,
            AudioAssetStatus.READY,
            Instant.parse("2026-05-12T10:00:00Z")
        );

        when(repository.findProjectForUser("project-8", "user-1")).thenReturn(Optional.of(project));
        when(repository.findPreviewSpeechSegments("project-8")).thenReturn(List.of(segment));
        when(repository.findAssets("project-8")).thenReturn(List.of(audioAsset));
        when(speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc("project-8")).thenReturn(List.of());

        AudiobookWorkflowSnapshotResponse snapshot = service.finalizeAudioGeneration(user, "project-8");

        assertThat(snapshot.workflowStage()).isEqualTo(AudiobookWorkflowStage.AUDIO_GENERATED);
        assertThat(snapshot.audioAssetsCurrent()).isTrue();
        verify(projectRepository).save(project);
    }

    @Test
    void approveCastReturnsUpdatedSnapshot() {
        AudiobookRepository repository = mock(AudiobookRepository.class);
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookWorkflowStateService service = service(repository, projectRepository, speakerCharacterRepository);
        CurrentUser user = new CurrentUser("user-1", "user@example.com", "User", List.of("USER"), "mock");

        AudiobookProject project = project("project-1", AudiobookWorkflowStage.CAST_REVIEW);
        AudiobookSpeechSegment segment = previewSegment(project, "Mara", "We go now.");

        when(repository.findProjectForUser("project-1", "user-1")).thenReturn(Optional.of(project));
        when(repository.findPreviewSpeechSegments("project-1")).thenReturn(List.of(segment));
        when(repository.findAssets("project-1")).thenReturn(List.of());
        when(speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc("project-1")).thenReturn(List.of(
            new SpeakerCharacter("character-1", "project-1", 0, "Mara", "Bold traveler", SpeakerVoice.ACHIRD, Instant.parse("2026-05-12T10:00:00Z"))
        ));

        AudiobookWorkflowSnapshotResponse approved = service.approveCast(user, "project-1");

        assertThat(approved.workflowStage()).isEqualTo(AudiobookWorkflowStage.CAST_APPROVED);
        assertThat(approved.audioAssetsCurrent()).isFalse();
    }

    private AudiobookProject project(String projectId, AudiobookWorkflowStage workflowStage) {
        AudiobookProject project = new AudiobookProject(
            projectId,
            "user-1",
            "Project",
            AudiobookProjectStatus.DRAFT,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        project.setWorkflowStage(workflowStage);
        project.setAudioAssetsCurrent(false);
        return project;
    }

    private AudiobookWorkflowStateService service(
        AudiobookRepository repository,
        AudiobookProjectRepository projectRepository,
        SpeakerCharacterRepository speakerCharacterRepository
    ) {
        return new AudiobookWorkflowStateService(
            repository,
            projectRepository,
            speakerCharacterRepository,
            mock(SpeakerVoiceAnalysisUpdateService.class),
            mock(AudiobookLibraryService.class)
        );
    }

    private AudiobookSpeechSegment previewSegment(AudiobookProject project, String speakerName, String originalText) {
        SpeakerCharacter character = new SpeakerCharacter(
            "character-1",
            project.getId(),
            0,
            speakerName,
            null,
            SpeakerVoice.ACHIRD,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            originalText,
            null,
            character
        );
        segment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        return segment;
    }
}
