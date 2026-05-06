package com.example.ttslab.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    public ChatController(ChatService chatService, ChatRateLimitService chatRateLimitService,
                          ChatRateLimitProperties chatRateLimitProperties, ChatUsageIdentityResolver chatUsageIdentityResolver) {
        this.chatService = chatService;
        this.chatRateLimitService = chatRateLimitService;
        this.chatRateLimitProperties = chatRateLimitProperties;
        this.chatUsageIdentityResolver = chatUsageIdentityResolver;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, org.springframework.security.core.Authentication authentication, HttpServletRequest httpRequest) {
        String identifier = chatUsageIdentityResolver.resolve(authentication, httpRequest, chatRateLimitProperties.idHeader());
        ChatRateLimitResult result = chatRateLimitService.checkAndConsume(identifier);
        if (!result.allowed()) {
            throw new ChatRateLimitExceededException(result.retryAfterSeconds(), chatRateLimitProperties.window().toString(), chatRateLimitProperties.maxRequests());
        }
        return chatService.ask(request);
    }
}
