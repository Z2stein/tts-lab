package com.example.ttslab.projects.ttsworkbench.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.projects.ttsworkbench.AnnotatedSpeakerTurn;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmotionAnnotationPersistenceServiceTest {
    @Test
    void persistStyledTextUpdatesMatchingPreviewSegments() {
        AudiobookSpeechSegmentRepository repository = mock(AudiobookSpeechSegmentRepository.class);
        EmotionAnnotationPersistenceService service = new EmotionAnnotationPersistenceService(repository);

        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS_WORKBENCH",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        AudiobookSpeechSegment first = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            null,
            null,
            null,
            null,
            "Hello",
            null,
            null
        );
        first.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        AudiobookSpeechSegment second = new AudiobookSpeechSegment(
            "segment-2",
            project,
            1,
            "Speech segment 2",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            null,
            null,
            null,
            null,
            "Hi",
            null,
            null
        );
        second.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);

        when(repository.findByProjectIdAndSegmentOriginOrderByOrderIndex(eq("project-1"), eq(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW)))
            .thenReturn(List.of(first, second));

        service.persistStyledText(project, List.of(
            new AnnotatedSpeakerTurn("A", "[calm] Hello"),
            new AnnotatedSpeakerTurn("B", "[warm] Hi")
        ));

        verify(repository).saveAll(anyList());
        org.assertj.core.api.Assertions.assertThat(first.getStyledText()).isEqualTo("[calm] Hello");
        org.assertj.core.api.Assertions.assertThat(second.getStyledText()).isEqualTo("[warm] Hi");
    }

    @Test
    void persistStyledTextRejectsSegmentCountMismatch() {
        AudiobookSpeechSegmentRepository repository = mock(AudiobookSpeechSegmentRepository.class);
        EmotionAnnotationPersistenceService service = new EmotionAnnotationPersistenceService(repository);

        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS_WORKBENCH",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );

        when(repository.findByProjectIdAndSegmentOriginOrderByOrderIndex(eq("project-1"), eq(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.persistStyledText(project, List.of(new AnnotatedSpeakerTurn("A", "Hello"))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The emotion annotations no longer match the current script preview.");
    }
}
