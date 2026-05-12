package com.example.ttslab.chat;

import com.example.ttslab.config.ChatbotProperties;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private static final String PROVIDER_GEMINI = "gemini";
    private static final String PROVIDER_MOCK = "mock";
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final String chatbotProvider;

    @Autowired
    public ChatService(
        ObjectProvider<ChatModel> chatModelProvider,
        ChatbotProperties chatbotProperties
    ) {
        this(chatModelProvider, chatbotProperties == null ? PROVIDER_MOCK : chatbotProperties.provider());
    }

    public ChatService(
        ObjectProvider<ChatModel> chatModelProvider,
        String chatbotProvider
    ) {
        this.chatModelProvider = chatModelProvider;
        this.chatbotProvider = chatbotProvider == null ? PROVIDER_MOCK : chatbotProvider.trim().toLowerCase();
    }

    public ChatResponse ask(ChatRequest request) {
        log.debug("is called");
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
            ? UUID.randomUUID().toString()
            : request.conversationId();

        if (PROVIDER_MOCK.equals(chatbotProvider)) {
            log.debug("return Mock");
            return new ChatResponse("[mock] Echo: " + request.message(), conversationId);
        }

        if (!PROVIDER_GEMINI.equals(chatbotProvider)) {
            throw new ChatProviderException("Unsupported chatbot provider", null, true);
        }

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new ChatProviderException("Chat provider not configured", null, true);
        }

        try {
            String answer = chatModel.call(new Prompt(new UserMessage(request.message()))).getResult().getOutput().getText();
            log.debug("received answer"+answer);
            return new ChatResponse(answer == null ? "" : answer, conversationId);
        } catch (Exception ex) {
            throw new ChatProviderException("AI provider failed", ex, false);
        }
    }
}
