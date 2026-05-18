package com.example.ttslab.audiobooks.workflow.speakeranalysis;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.workflow.DefaultAudiobookWorkflowPromptProvider;
import com.example.ttslab.audiobooks.workflow.SpeakerVoice;
import com.example.ttslab.audiobooks.workflow.service.DeterministicAudiobookWorkflowFallbackService;
import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatResponse;
import com.example.ttslab.chat.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

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

        SpeakerVoiceAnalysisResponse response = service.analyze("Alice: Hello\nBob: Hi", null);

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

        SpeakerVoiceAnalysisResponse response = service.analyze("Alice: Hello", null);

        assertThat(response.projectId()).isNull();
        assertThat(response.projectTitle()).isEqualTo("Hello");
        assertThat(response.sourceLanguageCode()).isEqualTo("en-US");
        assertThat(response.productionLanguageCode()).isEqualTo("en-US");
        verify(speakerCharacterRepository, org.mockito.Mockito.never()).saveAll(anyList());
        verify(speakerCharacterRepository, org.mockito.Mockito.never()).deleteByProjectId(anyString());
    }

    @Test
    void genderInformationIncludedInVoiceAnalysisPrompt() {
        DefaultAudiobookWorkflowPromptProvider promptProvider = new DefaultAudiobookWorkflowPromptProvider(objectMapper);

        String prompt = promptProvider.getSpeakerVoiceAnalysisPrompt("sample dialogue", null);

        assertThat(prompt).contains("MALE");
        assertThat(prompt).contains("FEMALE");
        assertThat(prompt).contains("Match speaker gender with voice gender");
        assertThat(prompt).contains("female").contains("FEMALE voice");
        assertThat(prompt).contains("male").contains("MALE voice");
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

        SpeakerVoiceAnalysisResponse response = service.analyze("Szukam komody z serii IKEA Malm.", null);

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

        SpeakerVoiceAnalysisResponse response = service.analyze("Mara: Hallo zusammen.", null);

        assertThat(response.projectTitle()).isEqualTo("Die Verborgene Spur");
        assertThat(response.sourceLanguageCode()).isEqualTo("de-DE");
        assertThat(response.productionLanguageCode()).isEqualTo("de-DE");
        assertThat(response.speakers()).extracting(SpeakerVoiceAnalysisItem::speakerName).containsExactly("Mara");
    }

    @Test
    void femaleCharacterGetsFemaleVoice() {
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

        String aiResponseText = """
            {
              "projectTitle": "Female Character Story",
              "sourceLanguageCode": "en-US",
              "speakers": [
                {
                  "speakerName": "Elena",
                  "roleDescription": "Female protagonist",
                  "voiceSuggestion": "DESPINA"
                }
              ]
            }
            """;
        when(chatService.ask(any(ChatRequest.class))).thenReturn(new ChatResponse(aiResponseText, null));

        SpeakerVoiceAnalysisResponse response = service.analyze("Elena: This is my story", null);

        assertThat(response.speakers()).hasSize(1);
        SpeakerVoiceAnalysisItem item = response.speakers().get(0);
        assertThat(item.speakerName()).isEqualTo("Elena");
        assertThat(item.voiceSuggestion()).isEqualTo(SpeakerVoice.DESPINA);
        assertThat(SpeakerVoice.DESPINA.getGender()).isEqualTo(SpeakerVoice.Gender.FEMALE);
    }

    @Test
    void maleCharacterGetsMaleVoice() {
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

        String aiResponseText = """
            {
              "projectTitle": "Male Character Story",
              "sourceLanguageCode": "en-US",
              "speakers": [
                {
                  "speakerName": "Marcus",
                  "roleDescription": "Male protagonist",
                  "voiceSuggestion": "CHARON"
                }
              ]
            }
            """;
        when(chatService.ask(any(ChatRequest.class))).thenReturn(new ChatResponse(aiResponseText, null));

        SpeakerVoiceAnalysisResponse response = service.analyze("Marcus: Hello there", null);

        assertThat(response.speakers()).hasSize(1);
        SpeakerVoiceAnalysisItem item = response.speakers().get(0);
        assertThat(item.speakerName()).isEqualTo("Marcus");
        assertThat(item.voiceSuggestion()).isEqualTo(SpeakerVoice.CHARON);
        assertThat(SpeakerVoice.CHARON.getGender()).isEqualTo(SpeakerVoice.Gender.MALE);
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

        SpeakerVoiceAnalysisResponse response = service.analyze("Hej världen.", null);

        assertThat(response.sourceLanguageCode()).isEqualTo("en-US");
        assertThat(response.productionLanguageCode()).isEqualTo("en-US");
    }
}
