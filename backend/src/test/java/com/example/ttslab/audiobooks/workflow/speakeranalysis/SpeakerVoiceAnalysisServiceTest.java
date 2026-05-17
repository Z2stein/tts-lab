package com.example.ttslab.audiobooks.workflow.speakeranalysis;

import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.workflow.DefaultAudiobookWorkflowPromptProvider;
import com.example.ttslab.audiobooks.workflow.service.DeterministicAudiobookWorkflowFallbackService;
import com.example.ttslab.chat.ChatResponse;
import com.example.ttslab.chat.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakerVoiceAnalysisServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void analyzeDetectsDialogueSpeakersAndLanguage() {
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

        SpeakerVoiceAnalysisResponse response = service.analyze("Alice: Hello\nBob: Hi");

        assertThat(response.projectId()).isNull();
        assertThat(response.projectTitle()).isEqualTo("Hello");
        assertThat(response.sourceLanguageCode()).isEqualTo("en-US");
        assertThat(response.productionLanguageCode()).isEqualTo("en-US");
        assertThat(response.speakers()).extracting(SpeakerVoiceAnalysisItem::speakerName).containsExactly("Alice", "Bob");
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
        assertThat(response.projectTitle()).isEqualTo("Hello");
        assertThat(response.sourceLanguageCode()).isEqualTo("en-US");
        assertThat(response.productionLanguageCode()).isEqualTo("en-US");
        verify(speakerCharacterRepository, org.mockito.Mockito.never()).saveAll(anyList());
        verify(speakerCharacterRepository, org.mockito.Mockito.never()).deleteByProjectId(anyString());
    }

    @Test
    void analyzeFallbackDetectsPolishAsSupportedLanguageCode() {
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

        SpeakerVoiceAnalysisResponse response = service.analyze("Szukam komody z serii IKEA Malm.");

        assertThat(response.sourceLanguageCode()).isEqualTo("pl-PL");
        assertThat(response.productionLanguageCode()).isEqualTo("pl-PL");
    }

    @Test
    void analyzeNormalizesProviderSourceLanguageCodeToSupportedValue() {
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
            "gemini"
        );
        when(chatService.ask(any())).thenReturn(new ChatResponse("""
            {"projectTitle":"Die Verborgene Spur","sourceLanguageCode":"de","speakers":[{"speakerName":"Mara","roleDescription":"Entschlossene Reisende","voiceSuggestion":"ZEPHYR"}]}
            """, null));

        SpeakerVoiceAnalysisResponse response = service.analyze("Mara: Hallo zusammen.");

        assertThat(response.projectTitle()).isEqualTo("Die Verborgene Spur");
        assertThat(response.sourceLanguageCode()).isEqualTo("de-DE");
        assertThat(response.productionLanguageCode()).isEqualTo("de-DE");
        assertThat(response.speakers()).extracting(SpeakerVoiceAnalysisItem::speakerName).containsExactly("Mara");
    }

    @Test
    void analyzeFallsBackToEnglishProductionLanguageForUnsupportedSourceLanguage() {
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
            "gemini"
        );
        when(chatService.ask(any())).thenReturn(new ChatResponse("""
            {"projectTitle":"Nordic Chronicle","sourceLanguageCode":"sv-SE","speakers":[{"speakerName":"Narrator","roleDescription":"Narration","voiceSuggestion":"IAPETUS"}]}
            """, null));

        SpeakerVoiceAnalysisResponse response = service.analyze("Hej världen.");

        assertThat(response.sourceLanguageCode()).isEqualTo("en-US");
        assertThat(response.productionLanguageCode()).isEqualTo("en-US");
    }
}
