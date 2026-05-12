package com.example.ttslab.chat;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.prompts.PromptRequestStatus;
import com.example.ttslab.ratelimit.RequestRateLimitExceededException;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
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
    private final CurrentUserResolver currentUserResolver;
    private final PromptHistoryService promptHistoryService;
    private final RequestRateLimitService requestRateLimitService;
    private final RequestUsageMeasurer requestUsageMeasurer;
    private final String providerModelName;

    public ChatController(ChatService chatService, CurrentUserResolver currentUserResolver,
                          PromptHistoryService promptHistoryService, RequestRateLimitService requestRateLimitService,
                          RequestUsageMeasurer requestUsageMeasurer,
                          ChatbotProperties chatbotProperties,
                          @Value("${spring.ai.google.genai.chat.options.model:}") String chatModelName) {
        this.chatService = chatService;
        this.currentUserResolver = currentUserResolver;
        this.promptHistoryService = promptHistoryService;
        this.requestRateLimitService = requestRateLimitService;
        this.requestUsageMeasurer = requestUsageMeasurer;
        this.providerModelName = providerModelName(chatbotProperties == null ? "mock" : chatbotProperties.provider(), chatModelName);
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, org.springframework.security.core.Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        RequestRateLimitResult result = requestRateLimitService.checkAndConsume(
            user,
            ModelType.TEXT_MODEL,
            requestUsageMeasurer.measure(request.message(), requestRateLimitService.unit())
        );
        if (!result.allowed()) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, providerModelName, request.message(), PromptRequestStatus.RATE_LIMITED);
            throw new RequestRateLimitExceededException(result);
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
