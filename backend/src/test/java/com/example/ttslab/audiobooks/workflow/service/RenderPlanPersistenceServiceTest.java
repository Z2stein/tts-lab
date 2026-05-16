package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.workflow.SpeakerVoice;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderPlanResponse;
import com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderRequest;
import com.example.ttslab.error.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RenderPlanPersistenceServiceTest {
    @Test
    void loadRenderPlanFromDatabaseConvertsSegmentsToRenderRequests() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = mock(AudiobookSpeechSegmentRepository.class);
        RenderPlanPersistenceService service = new RenderPlanPersistenceService(projectRepository, segmentRepository);

        AudiobookProject project = createProject("project-1", "Claude's Audiobook", "en-US", "google.generativeai-1.5-flash");
        AudiobookSpeechSegment segment = createSegment("segment-1", project, 0, "The Storyteller", "warm-voice", "Once upon a time...");
        project.getSpeechSegments().add(segment);

        when(projectRepository.findWithDetailsById("project-1")).thenReturn(Optional.of(project));

        SingleSpeakerRenderPlanResponse response = service.loadRenderPlanFromDatabase("project-1");

        assertThat(response.renderRequests()).hasSize(1);
        SingleSpeakerRenderRequest request = response.renderRequests().getFirst();
        assertThat(request.input().get("text")).isEqualTo("Once upon a time...");
        assertThat(request.input().get("segmentOrderIndex")).isEqualTo(0);
        assertThat(request.voice().get("speakerName")).isEqualTo("The Storyteller");
        assertThat(request.voice().get("name")).isEqualTo(SpeakerVoice.ACHIRD);
        assertThat(request.voice().get("modelName")).isEqualTo("google.generativeai-1.5-flash");
        assertThat(request.voice().get("languageCode")).isEqualTo("en-US");
        assertThat(request.audioConfig().get("audioEncoding")).isEqualTo("MP3");
    }

    @Test
    void loadRenderPlanFromDatabaseMultipleSegmentsInOrder() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = mock(AudiobookSpeechSegmentRepository.class);
        RenderPlanPersistenceService service = new RenderPlanPersistenceService(projectRepository, segmentRepository);

        AudiobookProject project = createProject("project-1", "My Book", "fr-FR", "model-name");
        AudiobookSpeechSegment first = createSegment("seg-1", project, 0, "Alice", "voice-a", "First line");
        AudiobookSpeechSegment second = createSegment("seg-2", project, 1, "Bob", "voice-b", "Second line");
        project.getSpeechSegments().addAll(List.of(first, second));

        when(projectRepository.findWithDetailsById("project-1")).thenReturn(Optional.of(project));

        SingleSpeakerRenderPlanResponse response = service.loadRenderPlanFromDatabase("project-1");

        assertThat(response.renderRequests()).hasSize(2);
        assertThat(response.renderRequests().get(0).input().get("text")).isEqualTo("First line");
        assertThat(response.renderRequests().get(0).input().get("segmentOrderIndex")).isEqualTo(0);
        assertThat(response.renderRequests().get(1).input().get("text")).isEqualTo("Second line");
        assertThat(response.renderRequests().get(1).input().get("segmentOrderIndex")).isEqualTo(1);
    }

    @Test
    void loadRenderPlanThrowsWhenProjectNotFound() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = mock(AudiobookSpeechSegmentRepository.class);
        RenderPlanPersistenceService service = new RenderPlanPersistenceService(projectRepository, segmentRepository);

        when(projectRepository.findWithDetailsById("missing-project")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadRenderPlanFromDatabase("missing-project"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Project not found with ID: missing-project");
    }

    @Test
    void loadRenderPlanThrowsWhenModelNameMissing() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = mock(AudiobookSpeechSegmentRepository.class);
        RenderPlanPersistenceService service = new RenderPlanPersistenceService(projectRepository, segmentRepository);

        AudiobookProject project = createProject("project-1", "Book", "en-US", null);
        when(projectRepository.findWithDetailsById("project-1")).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.loadRenderPlanFromDatabase("project-1"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Project does not have a model name configured for audio generation.");
    }

    @Test
    void loadRenderPlanThrowsWhenLanguageCodeMissing() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = mock(AudiobookSpeechSegmentRepository.class);
        RenderPlanPersistenceService service = new RenderPlanPersistenceService(projectRepository, segmentRepository);

        AudiobookProject project = createProject("project-1", "Book", null, "model-name");
        when(projectRepository.findWithDetailsById("project-1")).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.loadRenderPlanFromDatabase("project-1"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Project does not have a language code configured for audio generation.");
    }

    @Test
    void loadRenderPlanThrowsWhenNoSegmentsExist() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = mock(AudiobookSpeechSegmentRepository.class);
        RenderPlanPersistenceService service = new RenderPlanPersistenceService(projectRepository, segmentRepository);

        AudiobookProject project = createProject("project-1", "Book", "en-US", "model-name");
        when(projectRepository.findWithDetailsById("project-1")).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.loadRenderPlanFromDatabase("project-1"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Project has no script preview segments to render.");
    }

    @Test
    void loadRenderPlanThrowsWhenSegmentMissingStyledText() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = mock(AudiobookSpeechSegmentRepository.class);
        RenderPlanPersistenceService service = new RenderPlanPersistenceService(projectRepository, segmentRepository);

        AudiobookProject project = createProject("project-1", "Book", "en-US", "model-name");
        AudiobookSpeechSegment segment = createSegment("seg-1", project, 0, "Speaker", "voice", null);
        project.getSpeechSegments().add(segment);

        when(projectRepository.findWithDetailsById("project-1")).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.loadRenderPlanFromDatabase("project-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("does not have styled text");
    }

    @Test
    void loadRenderPlanThrowsWhenSegmentStyledTextIsEmpty() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        AudiobookSpeechSegmentRepository segmentRepository = mock(AudiobookSpeechSegmentRepository.class);
        RenderPlanPersistenceService service = new RenderPlanPersistenceService(projectRepository, segmentRepository);

        AudiobookProject project = createProject("project-1", "Book", "en-US", "model-name");
        AudiobookSpeechSegment segment = createSegment("seg-1", project, 0, "Speaker", "voice", "  ");
        project.getSpeechSegments().add(segment);

        when(projectRepository.findWithDetailsById("project-1")).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.loadRenderPlanFromDatabase("project-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("does not have styled text");
    }

    private AudiobookProject createProject(String id, String title, String languageCode, String modelName) {
        AudiobookProject project = new AudiobookProject(
            id,
            "user-1",
            title,
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            1,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        project.setProductionLanguageCode(languageCode);
        project.setProductionModelName(modelName);
        return project;
    }

    private AudiobookSpeechSegment createSegment(String id, AudiobookProject project, int orderIndex, String speakerName, String voiceName, String styledText) {
        SpeakerCharacter character = new SpeakerCharacter(
            id + "-char",
            project.getId(),
            0,
            speakerName,
            null,
            SpeakerVoice.ACHIRD,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            id,
            project,
            orderIndex,
            "Segment " + orderIndex,
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            styledText,
            styledText,
            character
        );
        segment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        return segment;
    }
}
