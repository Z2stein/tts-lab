package com.example.ttslab.chat;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final ChatModel chatModel;

    public ChatService(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModel = chatModelProvider.getIfAvailable();
    }

    public ChatResponse ask(ChatRequest request) {
        if (chatModel == null) {
            throw new ChatProviderException("Chat provider not configured", null);
        }
        try {
            String answer = chatModel.call(new Prompt(new UserMessage(request.message()))).getResult().getOutput().getText();
            String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? java.util.UUID.randomUUID().toString()
                : request.conversationId();
            return new ChatResponse(answer == null ? "" : answer, conversationId);
        } catch (Exception ex) {
            throw new ChatProviderException("AI provider failed", ex);
        }
    }
}
