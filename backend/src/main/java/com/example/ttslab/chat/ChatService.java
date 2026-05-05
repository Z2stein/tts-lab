package com.example.ttslab.chat;

import java.util.UUID;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private static final String PROVIDER_GEMINI = "gemini";
    private static final String PROVIDER_MOCK = "mock";

    private final ChatModel chatModel;
    private final String chatbotProvider;

    public ChatService(
        ObjectProvider<ChatModel> chatModelProvider,
        @Value("${chatbot.provider:mock}") String chatbotProvider
    ) {
        this.chatModel = chatModelProvider.getIfAvailable();
        this.chatbotProvider = chatbotProvider == null ? PROVIDER_MOCK : chatbotProvider.trim().toLowerCase();
    }

    public ChatResponse ask(ChatRequest request) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
            ? UUID.randomUUID().toString()
            : request.conversationId();

        if (PROVIDER_MOCK.equals(chatbotProvider)) {
            return new ChatResponse("[mock] Echo: " + request.message(), conversationId);
        }

        if (!PROVIDER_GEMINI.equals(chatbotProvider)) {
            throw new ChatProviderException("Unsupported chatbot provider", null, true);
        }

        if (chatModel == null) {
            throw new ChatProviderException("Chat provider not configured", null, true);
        }

        try {
            String answer = chatModel.call(new Prompt(new UserMessage(request.message()))).getResult().getOutput().getText();
            return new ChatResponse(answer == null ? "" : answer, conversationId);
        } catch (Exception ex) {
            throw new ChatProviderException("AI provider failed", ex, false);
        }
    }
}
