package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerSplitAnalysisService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisService;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitTurn;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakerSplitPersistenceServiceTest {
    @Test
    void splitAndPersistStoresOriginalTextAndCharacterIds() {
        SpeakerSplitAnalysisService splitAnalysisService = mock(SpeakerSplitAnalysisService.class);
        SpeakerVoiceAnalysisService speakerVoiceAnalysisService = mock(SpeakerVoiceAnalysisService.class);
        AudiobookSpeechSegmentRepository speechSegmentRepository = mock(AudiobookSpeechSegmentRepository.class);

        SpeakerSplitPersistenceService service = new SpeakerSplitPersistenceService(
            splitAnalysisService,
            speakerVoiceAnalysisService,
            speechSegmentRepository
        );

        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        List<SpeakerVoiceAnalysisItem> speakers = List.of(
            new SpeakerVoiceAnalysisItem("Alice", "Lead", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHIRD),
            new SpeakerVoiceAnalysisItem("Bob", "Friend", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHERNAR)
        );
        List<SpeakerCharacter> characters = List.of(
            new SpeakerCharacter("character-1", "project-1", 0, "Alice", "Lead", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHIRD, Instant.parse("2026-05-12T10:00:00Z")),
            new SpeakerCharacter("character-2", "project-1", 1, "Bob", "Friend", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHERNAR, Instant.parse("2026-05-12T10:00:00Z"))
        );

        when(speakerVoiceAnalysisService.syncProjectCharacters(eq("project-1"), anyList())).thenReturn(characters);
        when(splitAnalysisService.split(eq("Alice: Hello\nBob: Hi"), eq(speakers))).thenReturn(new SpeakerSplitAnalysisResponse(List.of(
            new SpeakerSplitTurn("Alice", "Hello"),
            new SpeakerSplitTurn("Bob", "Hi")
        )));

        service.splitAndPersist(project, "Alice: Hello\nBob: Hi", speakers);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AudiobookSpeechSegment>> segmentsCaptor = ArgumentCaptor.forClass((Class) List.class);
        InOrder inOrder = org.mockito.Mockito.inOrder(speechSegmentRepository, speakerVoiceAnalysisService, splitAnalysisService);
        inOrder.verify(speechSegmentRepository).deleteByProjectIdAndSegmentOrigin("project-1", AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        inOrder.verify(speakerVoiceAnalysisService).syncProjectCharacters(eq("project-1"), anyList());
        inOrder.verify(splitAnalysisService).split(eq("Alice: Hello\nBob: Hi"), eq(speakers));
        verify(speechSegmentRepository).saveAll(segmentsCaptor.capture());

        List<AudiobookSpeechSegment> segments = segmentsCaptor.getValue();
        assertThat(segments).hasSize(2);
        assertThat(segments).extracting(AudiobookSpeechSegment::getOrderIndex).containsExactly(0, 1);
        assertThat(segments).extracting(AudiobookSpeechSegment::getOriginalText).containsExactly("Hello", "Hi");
        assertThat(segments).extracting(AudiobookSpeechSegment::getCharacterId).containsExactly("character-1", "character-2");
        assertThat(segments).extracting(AudiobookSpeechSegment::getSpeakerName).allMatch(value -> value == null);
        assertThat(segments).extracting(AudiobookSpeechSegment::getReviewStatus).containsOnly(AudiobookSpeechSegmentReviewStatus.PENDING);
        assertThat(segments).extracting(AudiobookSpeechSegment::getSegmentOrigin).containsOnly(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
    }

    @Test
    void splitAndPersistRejectsUnknownSpeakersWithoutDeletingExistingSegments() {
        SpeakerSplitAnalysisService splitAnalysisService = mock(SpeakerSplitAnalysisService.class);
        SpeakerVoiceAnalysisService speakerVoiceAnalysisService = mock(SpeakerVoiceAnalysisService.class);
        AudiobookSpeechSegmentRepository speechSegmentRepository = mock(AudiobookSpeechSegmentRepository.class);

        SpeakerSplitPersistenceService service = new SpeakerSplitPersistenceService(
            splitAnalysisService,
            speakerVoiceAnalysisService,
            speechSegmentRepository
        );

        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );

        when(speakerVoiceAnalysisService.syncProjectCharacters(eq("project-1"), anyList())).thenReturn(List.of(
            new SpeakerCharacter("character-1", "project-1", 0, "Alice", "Lead", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHIRD, Instant.parse("2026-05-12T10:00:00Z"))
        ));
        when(splitAnalysisService.split(eq("Alice: Hello"), anyList())).thenReturn(new SpeakerSplitAnalysisResponse(List.of(
            new SpeakerSplitTurn("Bob", "Hello")
        )));

        assertThatThrownBy(() -> service.splitAndPersist(project, "Alice: Hello", List.of(
            new SpeakerVoiceAnalysisItem("Alice", "Lead", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHIRD)
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The split dialogue referenced an unknown speaker.");

        verify(speechSegmentRepository).deleteByProjectIdAndSegmentOrigin(eq("project-1"), eq(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW));
        verify(speechSegmentRepository, never()).saveAll(anyList());
    }

    @Test
    void splitAndPersistAllowsNarratorWhenIncludedInSpeakerList() {
        SpeakerSplitAnalysisService splitAnalysisService = mock(SpeakerSplitAnalysisService.class);
        SpeakerVoiceAnalysisService speakerVoiceAnalysisService = mock(SpeakerVoiceAnalysisService.class);
        AudiobookSpeechSegmentRepository speechSegmentRepository = mock(AudiobookSpeechSegmentRepository.class);

        SpeakerSplitPersistenceService service = new SpeakerSplitPersistenceService(
            splitAnalysisService,
            speakerVoiceAnalysisService,
            speechSegmentRepository
        );

        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        List<SpeakerVoiceAnalysisItem> speakers = List.of(
            new SpeakerVoiceAnalysisItem("Narrator", "Narration", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHIRD),
            new SpeakerVoiceAnalysisItem("Alice", "Lead", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHERNAR)
        );
        List<SpeakerCharacter> characters = List.of(
            new SpeakerCharacter("character-1", "project-1", 0, "Narrator", "Narration", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHIRD, Instant.parse("2026-05-12T10:00:00Z")),
            new SpeakerCharacter("character-2", "project-1", 1, "Alice", "Lead", com.example.ttslab.audiobooks.workflow.SpeakerVoice.ACHERNAR, Instant.parse("2026-05-12T10:00:00Z"))
        );

        when(speakerVoiceAnalysisService.syncProjectCharacters(eq("project-1"), anyList())).thenReturn(characters);
        when(splitAnalysisService.split(anyString(), eq(speakers))).thenReturn(new SpeakerSplitAnalysisResponse(List.of(
            new SpeakerSplitTurn("Narrator", "Once upon a time..."),
            new SpeakerSplitTurn("Alice", "Hello!")
        )));

        service.splitAndPersist(project, "Once upon a time... Alice said Hello!", speakers);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AudiobookSpeechSegment>> segmentsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(speechSegmentRepository).saveAll(segmentsCaptor.capture());

        List<AudiobookSpeechSegment> segments = segmentsCaptor.getValue();
        assertThat(segments).hasSize(2);
        assertThat(segments).extracting(AudiobookSpeechSegment::getCharacterId).containsExactly("character-1", "character-2");
    }
}


