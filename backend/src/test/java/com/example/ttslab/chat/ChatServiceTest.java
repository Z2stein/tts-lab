package com.example.ttslab.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.ai.chat.prompt.Prompt;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.ObjectProvider;

class ChatServiceTest {

    @Test
    void askReturnsAnswerAndGeneratedConversationId() {
        ChatModel chatModel = mock(ChatModel.class);
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("answer")))));

        ChatService service = new ChatService(provider);
        com.example.ttslab.chat.ChatResponse response = service.ask(new ChatRequest("hello", null));

        assertEquals("answer", response.answer());
        assertNotNull(response.conversationId());
    }

    @Test
    void askWrapsProviderErrors() {
        ChatModel chatModel = mock(ChatModel.class);
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("down"));

        ChatService service = new ChatService(provider);
        assertThrows(ChatProviderException.class, () -> service.ask(new ChatRequest("hello", "c-1")));
    }
}
