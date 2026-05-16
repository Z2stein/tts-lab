package com.example.ttslab.auth;

import com.example.ttslab.prompts.CurrentUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {
    private static final Logger log = LoggerFactory.getLogger(MeController.class);
    private final CurrentUserResolver currentUserResolver;

    public MeController(CurrentUserResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/api/me")
    public CurrentUser me(Authentication authentication) {
        CurrentUser currentUser = currentUserResolver.resolve(authentication);
        log.info("GET /api/me called (id={}, authMode={})", currentUser.id(), currentUser.authMode());
        return currentUser;
    }
}
