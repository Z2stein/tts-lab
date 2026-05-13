package com.example.ttslab.audiobooks.workflow.speakeranalysis;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.chat.ChatService;
import com.example.ttslab.audiobooks.workflow.DefaultAudiobookWorkflowPromptProvider;
import com.example.ttslab.audiobooks.workflow.SpeakerVoice;
import com.example.ttslab.audiobooks.workflow.service.DeterministicAudiobookWorkflowFallbackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakerVoiceAnalysisServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void analyzePersistsDetectedCharactersForProject() {
        ChatService chatService = mock(ChatService.class);
        AudiobookProjectRepository audiobookProjectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        DeterministicAudiobookWorkflowFallbackService fallbackService = new DeterministicAudiobookWorkflowFallbackService();
        DefaultAudiobookWorkflowPromptProvider promptProvider = new DefaultAudiobookWorkflowPromptProvider(objectMapper);
        SpeakerVoiceAnalysisService service = new SpeakerVoiceAnalysisService(
            chatService,
            objectMapper,
            promptProvider,
            fallbackService,
            audiobookProjectRepository,
            speakerCharacterRepository,
            "mock"
        );

        String projectId = "project-1";
        AudiobookProject project = new AudiobookProject(
            projectId,
            "user-1",
            "Speaker analysis",
            AudiobookProjectStatus.DRAFT,
            "voice_analysis",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        when(audiobookProjectRepository.findById(projectId)).thenReturn(Optional.of(project));

        SpeakerVoiceAnalysisResponse response = service.analyze("Alice: Hello\nBob: Hi", projectId);

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.speakers()).extracting(SpeakerVoiceAnalysisItem::speakerName).containsExactly("Alice", "Bob");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SpeakerCharacter>> charactersCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(speakerCharacterRepository).deleteByProjectId(projectId);
        verify(speakerCharacterRepository).saveAll(charactersCaptor.capture());
        verify(audiobookProjectRepository).save(project);

        List<SpeakerCharacter> characters = charactersCaptor.getValue();
        assertThat(characters).hasSize(2);
        assertThat(characters).extracting(SpeakerCharacter::getProjectId).containsOnly(projectId);
        assertThat(characters).extracting(SpeakerCharacter::getSortOrder).containsExactly(0, 1);
        assertThat(characters).extracting(SpeakerCharacter::getSpeakerName).containsExactly("Alice", "Bob");
        assertThat(characters).extracting(SpeakerCharacter::getVoiceSuggestion).containsExactly(SpeakerVoice.ZEPHYR, SpeakerVoice.PUCK);
        assertThat(project.getSpeakerCount()).isEqualTo(2);
        assertThat(project.getUpdatedAt()).isNotNull();
    }

    @Test
    void analyzeWithoutProjectIdDoesNotPersistCharacters() {
        ChatService chatService = mock(ChatService.class);
        AudiobookProjectRepository audiobookProjectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        DeterministicAudiobookWorkflowFallbackService fallbackService = new DeterministicAudiobookWorkflowFallbackService();
        DefaultAudiobookWorkflowPromptProvider promptProvider = new DefaultAudiobookWorkflowPromptProvider(objectMapper);
        SpeakerVoiceAnalysisService service = new SpeakerVoiceAnalysisService(
            chatService,
            objectMapper,
            promptProvider,
            fallbackService,
            audiobookProjectRepository,
            speakerCharacterRepository,
            "mock"
        );

        SpeakerVoiceAnalysisResponse response = service.analyze("Alice: Hello");

        assertThat(response.projectId()).isNull();
        verify(speakerCharacterRepository, org.mockito.Mockito.never()).saveAll(anyList());
        verify(speakerCharacterRepository, org.mockito.Mockito.never()).deleteByProjectId(anyString());
    }
}


