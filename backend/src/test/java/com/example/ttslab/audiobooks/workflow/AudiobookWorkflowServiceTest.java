package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerCharacterRepository;
import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatResponse;
import com.example.ttslab.chat.ChatService;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.audiobooks.workflow.service.*;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudiobookWorkflowServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mockProviderReturnsDeterministicSpeakersWithoutCallingChatProvider() {
        ChatService chatService = mock(ChatService.class);
        AudiobookWorkflowService service = createService(chatService, "mock");

        SpeakerVoiceAnalysisResponse response = service.analyze("Alice: Hello\nBob: Hi", null);

        assertEquals(2, response.speakers().size());
        assertEquals("Alice", response.speakers().get(0).speakerName());
        assertEquals("Bob", response.speakers().get(1).speakerName());
        verify(chatService, never()).ask(any(ChatRequest.class));
    }

    @Test
    void mockProviderSplitsDialogueWithoutCallingChatProvider() {
        ChatService chatService = mock(ChatService.class);
        AudiobookWorkflowService service = createService(chatService, "mock");

        SpeakerSplitAnalysisResponse response = service.split("A: First line\ncontinued\nB: Second line", List.of(), null);

        assertEquals(List.of(
            new SpeakerSplitTurn("A", "First line continued"),
            new SpeakerSplitTurn("B", "Second line")
        ), response.turns());
        verify(chatService, never()).ask(any(ChatRequest.class));
    }

    @Test
    void mockProviderAnnotatesEmotionsWithoutCallingChatProvider() {
        ChatService chatService = mock(ChatService.class);
        AudiobookWorkflowService service = createService(chatService, "mock");

        EmotionAnnotationAnalysisResponse response = service.annotate(List.of(
            new SpeakerSplitTurn("A", "Yesterday was everything fine and now I cannot believe you did this!"),
            new SpeakerSplitTurn("B", "I know you are hurt. Please, let us just talk.")
        ), null);

        assertEquals("A", response.turns().get(0).speaker());
        assertTrue(response.turns().get(0).text().startsWith("[happy]"));
        assertTrue(response.turns().get(0).text().contains("[short pause]"));
        assertTrue(response.turns().get(0).text().contains("[urgent]"));
        assertEquals("[calm] I know you are hurt. [short pause] Please, let us just talk.", response.turns().get(1).text());
        verify(chatService, never()).ask(any(ChatRequest.class));
    }

    @Test
    void finalRequestBuilderCreatesProviderRequestShape() {
        AudiobookWorkflowService service = createService(mock(ChatService.class), "mock");

        FinalTtsRequestPreviewResponse response = service.buildFinalRequest(new FinalTtsRequestPreviewRequest(
            "A conversation between Speaker A and Speaker B.",
            List.of(
                new SpeakerVoiceAnalysisItem("A", "Emotional speaker", SpeakerVoice.FENRIR),
                new SpeakerVoiceAnalysisItem("B", "Calm speaker", SpeakerVoice.ACHERNAR)
            ),
            List.of(
                new AnnotatedSpeakerTurn("A", "[urgent] Hello!"),
                new AnnotatedSpeakerTurn("B", "[calm] Hi.")
            ),
            "en-US",
            "{{google-model}}",
            "MP3"
        ));

        assertEquals("A conversation between Speaker A and Speaker B.", response.input().get("prompt"));
        assertEquals("en-US", response.voice().get("languageCode"));
        assertEquals("MP3", response.audioConfig().get("audioEncoding"));
        assertTrue(response.voice().toString().contains("speakerAlias=A"));
        assertTrue(response.voice().toString().contains("speakerId=Fenrir"));
    }

    @Test
    void geminiProviderUsesChatServiceAndParsesSpeakerJson() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.ask(any(ChatRequest.class))).thenReturn(new ChatResponse("""
            {"speakers":[{"speakerName":"Narrator","roleDescription":"Guides the scene","voiceSuggestion":"ZEPHYR"}]}
            """, "c-1"));
        AudiobookWorkflowService service = createService(chatService, "gemini");

        SpeakerVoiceAnalysisResponse response = service.analyze("Once upon a time", null);

        assertEquals(1, response.speakers().size());
        assertEquals("Narrator", response.speakers().getFirst().speakerName());
        assertEquals("ZEPHYR", response.speakers().getFirst().voiceSuggestion().name());
    }

    @Test
    void geminiProviderReturnsStructuredErrorForInvalidSpeakerModelOutput() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.ask(any(ChatRequest.class))).thenReturn(new ChatResponse("not-json", "c-1"));
        AudiobookWorkflowService service = createService(chatService, "gemini");

        ApiException exception = assertThrows(ApiException.class, () -> service.analyze("Alice: Hello", null));

        assertEquals("AUDIOBOOK_WORKFLOW_PROVIDER_RESPONSE_INVALID", exception.code());
        assertEquals(502, exception.status().value());
    }

    @Test
    void geminiProviderReturnsStructuredErrorForInvalidSplitModelOutput() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.ask(any(ChatRequest.class))).thenReturn(new ChatResponse("not-json", "c-1"));
        AudiobookWorkflowService service = createService(chatService, "gemini");

        ApiException exception = assertThrows(ApiException.class, () -> service.split("A: Hello", List.of(
            new SpeakerVoiceAnalysisItem("A", "Speaker A", SpeakerVoice.FENRIR)
        ), null));

        assertEquals("AUDIOBOOK_WORKFLOW_PROVIDER_RESPONSE_INVALID", exception.code());
        assertEquals(502, exception.status().value());
    }

    @Test
    void geminiProviderReturnsStructuredErrorForInvalidEmotionModelOutput() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.ask(any(ChatRequest.class))).thenReturn(new ChatResponse("not-json", "c-1"));
        AudiobookWorkflowService service = createService(chatService, "gemini");

        ApiException exception = assertThrows(ApiException.class, () -> service.annotate(List.of(new SpeakerSplitTurn("A", "Please talk.")), null));

        assertEquals("AUDIOBOOK_WORKFLOW_PROVIDER_RESPONSE_INVALID", exception.code());
        assertEquals(502, exception.status().value());
    }

    @Test
    void blankDialogueReturnsNoSpeakers() {
        ChatService chatService = mock(ChatService.class);
        AudiobookWorkflowService service = createService(chatService, "mock");

        SpeakerVoiceAnalysisResponse response = service.analyze("   ", null);

        assertEquals(0, response.speakers().size());
        verify(chatService, never()).ask(any(ChatRequest.class));
    }

    private AudiobookWorkflowService createService(ChatService chatService, String provider) {
        DeterministicAudiobookWorkflowFallbackService fallbackService = new DeterministicAudiobookWorkflowFallbackService();
        DefaultAudiobookWorkflowPromptProvider promptProvider = new DefaultAudiobookWorkflowPromptProvider(objectMapper);
        AudiobookProjectRepository audiobookProjectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        return new AudiobookWorkflowService(
            new SpeakerVoiceAnalysisService(chatService, objectMapper, promptProvider, fallbackService, audiobookProjectRepository, speakerCharacterRepository, provider),
            new SpeakerSplitAnalysisService(chatService, objectMapper, promptProvider, fallbackService, provider),
            new EmotionAnnotationService(chatService, objectMapper, promptProvider, fallbackService, provider),
            new FinalTtsRequestBuilder(),
            new SingleSpeakerRenderPlanner(objectMapper),
            mock(TtsAudioCreationService.class)
        );
    }
}


