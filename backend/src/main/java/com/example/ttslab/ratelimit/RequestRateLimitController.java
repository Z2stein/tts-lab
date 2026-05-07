package com.example.ttslab.ratelimit;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/request-limits")
public class RequestRateLimitController {
    private final CurrentUserResolver currentUserResolver;
    private final RequestRateLimitService requestRateLimitService;

    public RequestRateLimitController(CurrentUserResolver currentUserResolver, RequestRateLimitService requestRateLimitService) {
        this.currentUserResolver = currentUserResolver;
        this.requestRateLimitService = requestRateLimitService;
    }

    @GetMapping("/me")
    public RequestRateLimitSummaryResponse me(Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return requestRateLimitService.summary(user);
    }
}
