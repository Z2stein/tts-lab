package com.example.ttslab.projects.ttsworkbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatResponse;
import com.example.ttslab.chat.ChatService;
import com.example.ttslab.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("EmotionAnnotationService")
class EmotionAnnotationServiceTest {

    private EmotionAnnotationService service;
    private ChatService mockChatService;
    private TtsWorkbenchPromptProvider mockPromptProvider;
    private DeterministicTtsWorkbenchFallbackService mockFallbackService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        mockChatService = mock(ChatService.class);
        mockPromptProvider = mock(TtsWorkbenchPromptProvider.class);
        mockFallbackService = mock(DeterministicTtsWorkbenchFallbackService.class);
        objectMapper = new ObjectMapper();

        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "mock"
        );
    }

    // ============ NULL/EMPTY INPUT TESTS ============

    @Test
    @DisplayName("annotate returns empty response for null turns")
    void annotateNullTurns() {
        EmotionAnnotationAnalysisResponse response = service.annotate(null);

        assertThat(response.turns()).isEmpty();
        verify(mockChatService, never()).ask(any());
        verify(mockFallbackService, never()).annotateEmotions(any());
    }

    @Test
    @DisplayName("annotate returns empty response for empty turns list")
    void annotateEmptyTurns() {
        EmotionAnnotationAnalysisResponse response = service.annotate(List.of());

        assertThat(response.turns()).isEmpty();
        verify(mockChatService, never()).ask(any());
    }

    // ============ PROVIDER ROUTING TESTS ============

    @Test
    @DisplayName("annotate uses fallback service when provider is not gemini")
    void annotateUsesFallbackWhenNotGemini() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "mock"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        AnnotatedSpeakerTurn annotated = new AnnotatedSpeakerTurn("Alice", "[happy] Hello");
        when(mockFallbackService.annotateEmotions(inputTurns)).thenReturn(List.of(annotated));

        EmotionAnnotationAnalysisResponse response = service.annotate(inputTurns);

        assertThat(response.turns()).hasSize(1);
        assertThat(response.turns().get(0).text()).isEqualTo("[happy] Hello");
        verify(mockFallbackService).annotateEmotions(inputTurns);
        verify(mockChatService, never()).ask(any());
    }

    @Test
    @DisplayName("annotate uses chat service when provider is gemini")
    void annotateUsesGeminiWhenConfigured() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        String prompt = "test prompt";
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": [{\"speaker\": \"Alice\", \"text\": \"[happy] Hello\"}]}");
        when(mockPromptProvider.getEmotionAnnotationPrompt(inputTurns)).thenReturn(prompt);
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        EmotionAnnotationAnalysisResponse result = service.annotate(inputTurns);

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).text()).contains("happy");
        verify(mockChatService).ask(any(ChatRequest.class));
        verify(mockFallbackService, never()).annotateEmotions(any());
    }

    // ============ JSON PARSING TESTS ============

    @Test
    @DisplayName("annotate parses valid JSON response with single turn")
    void annotateParseSingleTurn() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": [{\"speaker\": \"Alice\", \"text\": \"[excited] Hello!\"}]}");
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        EmotionAnnotationAnalysisResponse result = service.annotate(inputTurns);

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
        assertThat(result.turns().get(0).text()).isEqualTo("[excited] Hello!");
    }

    @Test
    @DisplayName("annotate parses JSON response with multiple turns")
    void annotateParseMultipleTurns() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(
            new SpeakerSplitTurn("Alice", "Hello"),
            new SpeakerSplitTurn("Bob", "Hi there")
        );
        ChatResponse response = mock(ChatResponse.class);
        String json = "{\"turns\": ["
            + "{\"speaker\": \"Alice\", \"text\": \"[happy] Hello\"},"
            + "{\"speaker\": \"Bob\", \"text\": \"[friendly] Hi there\"}"
            + "]}";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        EmotionAnnotationAnalysisResponse result = service.annotate(inputTurns);

        assertThat(result.turns()).hasSize(2);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
        assertThat(result.turns().get(1).speaker()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("annotate filters out turns with blank speaker")
    void annotateFiltersBlankSpeaker() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        String json = "{\"turns\": ["
            + "{\"speaker\": \"\", \"text\": \"[happy] Hello\"},"
            + "{\"speaker\": \"Alice\", \"text\": \"[excited] Hi\"}"
            + "]}";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        EmotionAnnotationAnalysisResponse result = service.annotate(inputTurns);

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("annotate filters out turns with blank text")
    void annotateFiltersBlankText() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        String json = "{\"turns\": ["
            + "{\"speaker\": \"Alice\", \"text\": \"\"},"
            + "{\"speaker\": \"Bob\", \"text\": \"[friendly] Hello\"}"
            + "]}";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        EmotionAnnotationAnalysisResponse result = service.annotate(inputTurns);

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("annotate trims whitespace from speaker and text")
    void annotateTrimsWhitespace() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        String json = "{\"turns\": [{\"speaker\": \"  Alice  \", \"text\": \"  [happy] Hello  \"}]}";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        EmotionAnnotationAnalysisResponse result = service.annotate(inputTurns);

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
        assertThat(result.turns().get(0).text()).isEqualTo("[happy] Hello");
    }

    @Test
    @DisplayName("annotate unwraps markdown fence from response")
    void annotateUnwrapsMarkdownFence() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        String json = "```json\n{\"turns\": [{\"speaker\": \"Alice\", \"text\": \"[happy] Hi\"}]}\n```";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        EmotionAnnotationAnalysisResponse result = service.annotate(inputTurns);

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
    }

    // ============ ERROR HANDLING TESTS ============

    @Test
    @DisplayName("annotate rethrows ApiException from chat service")
    void annotateRethrowsApiException() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ApiException originalException = new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CHAT_ERROR",
            "Chat service failed"
        );
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenThrow(originalException);

        assertThatThrownBy(() -> service.annotate(inputTurns))
            .isInstanceOf(ApiException.class)
            .isEqualTo(originalException);
    }

    @Test
    @DisplayName("annotate wraps generic exception in ApiException")
    void annotateWrapsGenericException() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenThrow(new RuntimeException("Network error"));

        assertThatThrownBy(() -> service.annotate(inputTurns))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_FAILED")
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("annotate throws when response is null")
    void annotateNullResponse() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn(null);
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.annotate(inputTurns))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("annotate throws when response is blank")
    void annotateBlankResponse() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("   ");
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.annotate(inputTurns))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("annotate throws when turns is not an array in response")
    void annotateTurnsNotArray() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": \"not an array\"}");
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.annotate(inputTurns))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("annotate throws when turns array is empty")
    void annotateEmptyTurnsArray() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": []}");
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.annotate(inputTurns))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("annotate throws when all turns are filtered out (blank speaker/text)")
    void annotateAllTurnsFiltered() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": [{\"speaker\": \"\", \"text\": \"\"}, {\"speaker\": \"  \", \"text\": \"   \"}]}");
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.annotate(inputTurns))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("annotate throws when response is invalid JSON")
    void annotateInvalidJson() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("not valid json");
        when(mockPromptProvider.getEmotionAnnotationPrompt(any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.annotate(inputTurns))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("annotate handles null provider configuration")
    void annotateNullProvider() {
        service = new EmotionAnnotationService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            null
        );

        List<SpeakerSplitTurn> inputTurns = List.of(new SpeakerSplitTurn("Alice", "Hello"));
        AnnotatedSpeakerTurn annotated = new AnnotatedSpeakerTurn("Alice", "[happy] Hello");
        when(mockFallbackService.annotateEmotions(inputTurns)).thenReturn(List.of(annotated));

        EmotionAnnotationAnalysisResponse result = service.annotate(inputTurns);

        assertThat(result.turns()).hasSize(1);
    }
}
