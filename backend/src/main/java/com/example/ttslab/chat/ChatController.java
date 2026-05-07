package com.example.ttslab.chat;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.prompts.PromptRequestStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final ChatRateLimitService chatRateLimitService;
    private final ChatRateLimitProperties chatRateLimitProperties;
    private final ChatUsageIdentityResolver chatUsageIdentityResolver;
    private final CurrentUserResolver currentUserResolver;
    private final PromptHistoryService promptHistoryService;
    private final String providerModelName;

    public ChatController(ChatService chatService, ChatRateLimitService chatRateLimitService,
                          ChatRateLimitProperties chatRateLimitProperties, ChatUsageIdentityResolver chatUsageIdentityResolver,
                          CurrentUserResolver currentUserResolver, PromptHistoryService promptHistoryService,
                          @Value("${chatbot.provider:mock}") String chatbotProvider,
                          @Value("${spring.ai.google.genai.chat.options.model:}") String chatModelName) {
        this.chatService = chatService;
        this.chatRateLimitService = chatRateLimitService;
        this.chatRateLimitProperties = chatRateLimitProperties;
        this.chatUsageIdentityResolver = chatUsageIdentityResolver;
        this.currentUserResolver = currentUserResolver;
        this.promptHistoryService = promptHistoryService;
        this.providerModelName = providerModelName(chatbotProvider, chatModelName);
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, org.springframework.security.core.Authentication authentication, HttpServletRequest httpRequest) {
        String identifier = chatUsageIdentityResolver.resolve(authentication, httpRequest, chatRateLimitProperties.idHeader());
        ChatRateLimitResult result = chatRateLimitService.checkAndConsume(identifier);
        CurrentUser user = currentUserResolver.resolve(authentication);
        if (!result.allowed()) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, providerModelName, request.message(), PromptRequestStatus.RATE_LIMITED);
            throw new ChatRateLimitExceededException(result.retryAfterSeconds(), chatRateLimitProperties.window().toString(), chatRateLimitProperties.maxRequests());
        }
        try {
            ChatResponse response = chatService.ask(request);
            promptHistoryService.record(user, ModelType.TEXT_MODEL, providerModelName, request.message(), PromptRequestStatus.SUCCESS);
            return response;
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, providerModelName, request.message(), PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    private String providerModelName(String chatbotProvider, String chatModelName) {
        String provider = chatbotProvider == null || chatbotProvider.isBlank() ? "mock" : chatbotProvider.trim();
        if ("mock".equalsIgnoreCase(provider)) {
            return "mock";
        }
        if (chatModelName == null || chatModelName.isBlank()) {
            return provider;
        }
        return provider + "/" + chatModelName.trim();
    }
}
