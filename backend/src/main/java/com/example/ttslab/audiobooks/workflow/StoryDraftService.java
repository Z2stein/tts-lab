package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatService;
import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.error.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StoryDraftService {
    private static final String PROVIDER_GEMINI = "gemini";
    private static final String FALLBACK_DRAFT =
        "Narrator: The story begins here.\nCharacter: Are you ready?\nNarrator: They were ready.";
    private static final Logger log = LoggerFactory.getLogger(StoryDraftService.class);

    private final ChatService chatService;
    private final AudiobookWorkflowPromptProvider promptProvider;
    private final String chatbotProvider;

    @Autowired
    public StoryDraftService(
        ChatService chatService,
        AudiobookWorkflowPromptProvider promptProvider,
        ChatbotProperties chatbotProperties
    ) {
        this(
            chatService,
            promptProvider,
            chatbotProperties == null ? "mock" : chatbotProperties.provider()
        );
    }

    public StoryDraftService(
        ChatService chatService,
        AudiobookWorkflowPromptProvider promptProvider,
        String chatbotProvider
    ) {
        this.chatService = chatService;
        this.promptProvider = promptProvider;
        this.chatbotProvider = chatbotProvider == null ? "mock" : chatbotProvider.trim().toLowerCase();
    }

    public GenerateStoryDraftResponse generate(String idea, List<String> enhancements) {
        List<String> safeEnhancements = enhancements == null ? List.of() : enhancements;

        if (!PROVIDER_GEMINI.equals(chatbotProvider)) {
            log.debug("Story draft using fallback, provider={}", chatbotProvider);
            return new GenerateStoryDraftResponse(FALLBACK_DRAFT);
        }

        try {
            String prompt = promptProvider.getStoryDraftPrompt(idea, safeEnhancements);
            String answer = chatService.ask(new ChatRequest(prompt, null)).answer();
            return new GenerateStoryDraftResponse(answer);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "STORY_DRAFT_PROVIDER_FAILED",
                "The story draft provider is currently unavailable. Please try again later.",
                null,
                ex
            );
        }
    }
}
