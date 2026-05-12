package com.example.ttslab.projects.ttsworkbench.service;

import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatService;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.projects.ttsworkbench.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EmotionAnnotationService {
    private static final String PROVIDER_GEMINI = "gemini";

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final TtsWorkbenchPromptProvider promptProvider;
    private final DeterministicTtsWorkbenchFallbackService fallbackService;
    private final String chatbotProvider;

    @Autowired
    public EmotionAnnotationService(
        ChatService chatService,
        ObjectMapper objectMapper,
        TtsWorkbenchPromptProvider promptProvider,
        DeterministicTtsWorkbenchFallbackService fallbackService,
        ChatbotProperties chatbotProperties
    ) {
        this(chatService, objectMapper, promptProvider, fallbackService, chatbotProperties == null ? "mock" : chatbotProperties.provider());
    }

    public EmotionAnnotationService(
        ChatService chatService,
        ObjectMapper objectMapper,
        TtsWorkbenchPromptProvider promptProvider,
        DeterministicTtsWorkbenchFallbackService fallbackService,
        String chatbotProvider
    ) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.promptProvider = promptProvider;
        this.fallbackService = fallbackService;
        this.chatbotProvider = chatbotProvider == null ? "mock" : chatbotProvider.trim().toLowerCase();
    }

    public EmotionAnnotationAnalysisResponse annotate(List<SpeakerSplitTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return new EmotionAnnotationAnalysisResponse(List.of());
        }

        if (!PROVIDER_GEMINI.equals(chatbotProvider)) {
            return new EmotionAnnotationAnalysisResponse(fallbackService.annotateEmotions(turns));
        }

        try {
            String answer = chatService.ask(new ChatRequest(promptProvider.getEmotionAnnotationPrompt(turns), null)).answer();
            return new EmotionAnnotationAnalysisResponse(parseProviderAnswer(answer));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TTS_WORKBENCH_PROVIDER_FAILED",
                "The emotion annotation provider is currently unavailable. Please try again later.",
                null,
                ex
            );
        }
    }

    private List<AnnotatedSpeakerTurn> parseProviderAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            throw invalidProviderResponse(null);
        }

        try {
            JsonNode turns = objectMapper.readTree(TtsWorkbenchJson.stripMarkdownFence(answer)).path("turns");
            if (!turns.isArray()) {
                throw invalidProviderResponse(null);
            }

            List<AnnotatedSpeakerTurn> items = new ArrayList<>();
            for (JsonNode turn : turns) {
                String speaker = turn.path("speaker").asText("").trim();
                String text = turn.path("text").asText("").trim();
                if (!speaker.isBlank() && !text.isBlank()) {
                    items.add(new AnnotatedSpeakerTurn(speaker, text));
                }
            }
            if (items.isEmpty()) {
                throw invalidProviderResponse(null);
            }
            return items;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidProviderResponse(ex);
        }
    }

    private ApiException invalidProviderResponse(Throwable cause) {
        return new ApiException(
            HttpStatus.BAD_GATEWAY,
            "TTS_WORKBENCH_PROVIDER_RESPONSE_INVALID",
            "The emotion annotation provider returned an invalid response. Please try again later.",
            null,
            cause
        );
    }
}
