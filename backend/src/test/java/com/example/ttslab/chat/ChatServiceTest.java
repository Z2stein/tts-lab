package com.example.ttslab.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class ChatServiceTest {

    @Test
    void mockProviderReturnsDeterministicResponseWithoutChatModel() {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        ChatService service = new ChatService(provider, "mock");
        com.example.ttslab.chat.ChatResponse response = service.ask(new ChatRequest("hello", "c-1"));

        assertEquals("[mock] Echo: hello", response.answer());
        assertEquals("c-1", response.conversationId());
    }

    @Test
    void askReturnsAnswerAndGeneratedConversationIdForGeminiProvider() {
        ChatModel chatModel = mock(ChatModel.class);
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("answer")))));

        ChatService service = new ChatService(provider, "gemini");
        com.example.ttslab.chat.ChatResponse response = service.ask(new ChatRequest("hello", null));

        assertEquals("answer", response.answer());
        assertNotNull(response.conversationId());
    }

    @Test
    void askWrapsProviderErrorsForGeminiProvider() {
        ChatModel chatModel = mock(ChatModel.class);
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("down"));

        ChatService service = new ChatService(provider, "gemini");
        ChatProviderException ex = assertThrows(ChatProviderException.class, () -> service.ask(new ChatRequest("hello", "c-1")));
        assertFalse(ex.chatModelMissing());
    }

    @Test
    void chatModelMissingWhenGeminiProviderUnconfigured() {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        ChatService service = new ChatService(provider, "gemini");
        ChatProviderException ex = assertThrows(ChatProviderException.class, () -> service.ask(new ChatRequest("hello", null)));
        assertTrue(ex.chatModelMissing());
    }
}
