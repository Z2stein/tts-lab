package com.example.ttslab.prompts;

import com.example.ttslab.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prompts")
public class PromptHistoryController {
    private final CurrentUserResolver currentUserResolver;
    private final PromptHistoryService promptHistoryService;

    public PromptHistoryController(CurrentUserResolver currentUserResolver, PromptHistoryService promptHistoryService) {
        this.currentUserResolver = currentUserResolver;
        this.promptHistoryService = promptHistoryService;
    }

    @GetMapping("/history")
    public PromptHistoryResponse history(@RequestParam(required = false) ModelType modelType, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return new PromptHistoryResponse(promptHistoryService.findForUser(user, modelType));
    }
}
