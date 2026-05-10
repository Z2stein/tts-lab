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

@DisplayName("SpeakerSplitAnalysisService")
class SpeakerSplitAnalysisServiceTest {

    private SpeakerSplitAnalysisService service;
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

        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "mock"
        );
    }

    // ============ NULL/BLANK INPUT TESTS ============

    @Test
    @DisplayName("split returns empty response for null dialogue")
    void splitNullDialogue() {
        SpeakerSplitAnalysisResponse response = service.split(null, List.of());

        assertThat(response.turns()).isEmpty();
        verify(mockChatService, never()).ask(any());
        verify(mockFallbackService, never()).splitDialogue(any());
    }

    @Test
    @DisplayName("split returns empty response for blank dialogue")
    void splitBlankDialogue() {
        SpeakerSplitAnalysisResponse response = service.split("   ", List.of());

        assertThat(response.turns()).isEmpty();
        verify(mockChatService, never()).ask(any());
    }

    @Test
    @DisplayName("split returns empty response for empty string dialogue")
    void splitEmptyDialogue() {
        SpeakerSplitAnalysisResponse response = service.split("", List.of());

        assertThat(response.turns()).isEmpty();
    }

    // ============ PROVIDER ROUTING TESTS ============

    @Test
    @DisplayName("split uses fallback service when provider is not gemini")
    void splitUsesFallbackWhenNotGemini() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "mock"
        );

        SpeakerSplitTurn turn1 = new SpeakerSplitTurn("Alice", "Hello");
        when(mockFallbackService.splitDialogue("test dialogue")).thenReturn(List.of(turn1));

        SpeakerSplitAnalysisResponse response = service.split("test dialogue", List.of());

        assertThat(response.turns()).hasSize(1);
        assertThat(response.turns().get(0).speaker()).isEqualTo("Alice");
        verify(mockFallbackService).splitDialogue("test dialogue");
        verify(mockChatService, never()).ask(any());
    }

    @Test
    @DisplayName("split uses chat service when provider is gemini")
    void splitUsesGeminiWhenConfigured() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        String prompt = "test prompt";
        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": [{\"speaker\": \"Alice\", \"text\": \"Hello\"}]}");
        when(mockPromptProvider.getSpeakerSplitPrompt("test dialogue", List.of())).thenReturn(prompt);
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        SpeakerSplitAnalysisResponse result = service.split("test dialogue", List.of());

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
        verify(mockChatService).ask(any(ChatRequest.class));
        verify(mockFallbackService, never()).splitDialogue(any());
    }

    // ============ SPEAKER LIST HANDLING ============

    @Test
    @DisplayName("split handles null speakers list")
    void splitNullSpeakersList() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": [{\"speaker\": \"Bob\", \"text\": \"Hi\"}]}");
        when(mockPromptProvider.getSpeakerSplitPrompt("dialogue", List.of())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        SpeakerSplitAnalysisResponse result = service.split("dialogue", null);

        assertThat(result.turns()).hasSize(1);
        verify(mockPromptProvider).getSpeakerSplitPrompt("dialogue", List.of());
    }

    // ============ JSON PARSING TESTS ============

    @Test
    @DisplayName("split parses valid JSON response with single turn")
    void splitParsesSingleTurn() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": [{\"speaker\": \"Alice\", \"text\": \"Hello world\"}]}");
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        SpeakerSplitAnalysisResponse result = service.split("test", List.of());

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
        assertThat(result.turns().get(0).text()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("split parses valid JSON response with multiple turns")
    void splitsParseMultipleTurns() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        String json = "{\"turns\": ["
            + "{\"speaker\": \"Alice\", \"text\": \"Hello\"},"
            + "{\"speaker\": \"Bob\", \"text\": \"Hi there\"}"
            + "]}";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        SpeakerSplitAnalysisResponse result = service.split("test", List.of());

        assertThat(result.turns()).hasSize(2);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
        assertThat(result.turns().get(1).speaker()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("split filters out turns with blank speaker")
    void splitFiltersBlankSpeaker() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        String json = "{\"turns\": ["
            + "{\"speaker\": \"\", \"text\": \"Hello\"},"
            + "{\"speaker\": \"Alice\", \"text\": \"Hi\"}"
            + "]}";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        SpeakerSplitAnalysisResponse result = service.split("test", List.of());

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("split filters out turns with blank text")
    void splitFiltersBlankText() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        String json = "{\"turns\": ["
            + "{\"speaker\": \"Alice\", \"text\": \"\"},"
            + "{\"speaker\": \"Bob\", \"text\": \"Hello\"}"
            + "]}";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        SpeakerSplitAnalysisResponse result = service.split("test", List.of());

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("split trims whitespace from speaker and text")
    void splitTrimsWhitespace() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        String json = "{\"turns\": [{\"speaker\": \"  Alice  \", \"text\": \"  Hello  \"}]}";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        SpeakerSplitAnalysisResponse result = service.split("test", List.of());

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
        assertThat(result.turns().get(0).text()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("split wraps JSON response in markdown fence")
    void splitUnwrapsMarkdownFence() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        String json = "```json\n{\"turns\": [{\"speaker\": \"Alice\", \"text\": \"Hi\"}]}\n```";
        when(response.answer()).thenReturn(json);
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        SpeakerSplitAnalysisResponse result = service.split("test", List.of());

        assertThat(result.turns()).hasSize(1);
        assertThat(result.turns().get(0).speaker()).isEqualTo("Alice");
    }

    // ============ ERROR HANDLING TESTS ============

    @Test
    @DisplayName("split rethrows ApiException from chat service")
    void splitRethrowsApiException() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ApiException originalException = new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CHAT_ERROR",
            "Chat service failed"
        );
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenThrow(originalException);

        assertThatThrownBy(() -> service.split("test", List.of()))
            .isInstanceOf(ApiException.class)
            .isEqualTo(originalException);
    }

    @Test
    @DisplayName("split wraps generic exception in ApiException")
    void splitWrapsGenericException() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenThrow(new RuntimeException("Network error"));

        assertThatThrownBy(() -> service.split("test", List.of()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_FAILED")
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("split throws when response is null")
    void splitNullResponse() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn(null);
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.split("test", List.of()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("split throws when response is blank")
    void splitBlankResponse() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("   ");
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.split("test", List.of()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("split throws when turns is not an array in response")
    void splitTurnsNotArray() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": \"not an array\"}");
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.split("test", List.of()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("split throws when turns array is empty")
    void splitEmptyTurnsArray() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": []}");
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.split("test", List.of()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("split throws when all turns are filtered out (blank speaker/text)")
    void splitAllTurnsFiltered() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("{\"turns\": [{\"speaker\": \"\", \"text\": \"\"}, {\"speaker\": \"  \", \"text\": \"   \"}]}");
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.split("test", List.of()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("split throws when response is invalid JSON")
    void splitInvalidJson() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            "gemini"
        );

        ChatResponse response = mock(ChatResponse.class);
        when(response.answer()).thenReturn("not valid json");
        when(mockPromptProvider.getSpeakerSplitPrompt(any(), any())).thenReturn("prompt");
        when(mockChatService.ask(any(ChatRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> service.split("test", List.of()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID");
    }

    @Test
    @DisplayName("split throws when provider null when configured as gemini")
    void splitNullProvider() {
        service = new SpeakerSplitAnalysisService(
            mockChatService,
            objectMapper,
            mockPromptProvider,
            mockFallbackService,
            null
        );

        // Should use mock provider, not fail
        SpeakerSplitTurn turn = new SpeakerSplitTurn("Alice", "Hello");
        when(mockFallbackService.splitDialogue("test")).thenReturn(List.of(turn));

        SpeakerSplitAnalysisResponse result = service.split("test", List.of());

        assertThat(result.turns()).hasSize(1);
    }
}
