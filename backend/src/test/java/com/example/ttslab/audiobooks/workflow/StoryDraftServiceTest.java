package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatResponse;
import com.example.ttslab.chat.ChatService;
import com.example.ttslab.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryDraftServiceTest {

    private final ChatService chatService = mock(ChatService.class);
    private final AudiobookWorkflowPromptProvider promptProvider = mock(AudiobookWorkflowPromptProvider.class);

    @Test
    void generateReturnsDraftFromAiAnswerOnSuccess() {
        when(promptProvider.getStoryDraftPrompt("A lighthouse", List.of()))
            .thenReturn("some prompt");
        when(chatService.ask(any(ChatRequest.class)))
            .thenReturn(new ChatResponse("Narrator: The light shone.", "conv-1"));

        StoryDraftService service = new StoryDraftService(chatService, promptProvider, "gemini");
        GenerateStoryDraftResponse response = service.generate("A lighthouse", List.of());

        assertThat(response.storyDraft()).isEqualTo("Narrator: The light shone.");
    }

    @Test
    void generateIncludesEnhancementsInPromptCall() {
        when(promptProvider.getStoryDraftPrompt("An idea", List.of("Make it dramatic")))
            .thenReturn("dramatic prompt");
        when(chatService.ask(any(ChatRequest.class)))
            .thenReturn(new ChatResponse("Narrator: Drama!", "conv-2"));

        StoryDraftService service = new StoryDraftService(chatService, promptProvider, "gemini");
        service.generate("An idea", List.of("Make it dramatic"));

        verify(promptProvider).getStoryDraftPrompt("An idea", List.of("Make it dramatic"));
    }

    @Test
    void generateReturnsMockDraftWhenProviderIsNotGemini() {
        StoryDraftService service = new StoryDraftService(chatService, promptProvider, "mock");
        GenerateStoryDraftResponse response = service.generate("Any idea", List.of());

        assertThat(response.storyDraft()).isNotBlank();
    }

    @Test
    void generateWrapsProviderFailureAsApiException() {
        when(promptProvider.getStoryDraftPrompt(any(), any())).thenReturn("prompt");
        when(chatService.ask(any(ChatRequest.class))).thenThrow(new RuntimeException("provider down"));

        StoryDraftService service = new StoryDraftService(chatService, promptProvider, "gemini");

        assertThatThrownBy(() -> service.generate("A lighthouse", List.of()))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiEx = (ApiException) ex;
                assertThat(apiEx.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(apiEx.code()).isEqualTo("STORY_DRAFT_PROVIDER_FAILED");
            });
    }
}
