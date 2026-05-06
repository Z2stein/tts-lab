package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmotionAnnotationService {
    private static final String PROVIDER_GEMINI = "gemini";

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final TtsWorkbenchPromptProvider promptProvider;
    private final DeterministicTtsWorkbenchFallbackService fallbackService;
    private final String chatbotProvider;

    public EmotionAnnotationService(
        ChatService chatService,
        ObjectMapper objectMapper,
        TtsWorkbenchPromptProvider promptProvider,
        DeterministicTtsWorkbenchFallbackService fallbackService,
        @Value("${chatbot.provider:mock}") String chatbotProvider
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
            List<AnnotatedSpeakerTurn> parsed = parseProviderAnswer(answer);
            if (!parsed.isEmpty()) {
                return new EmotionAnnotationAnalysisResponse(parsed);
            }
        } catch (Exception ignored) {
            // The workbench must remain usable when the configured provider fails.
        }

        return new EmotionAnnotationAnalysisResponse(fallbackService.annotateEmotions(turns));
    }

    private List<AnnotatedSpeakerTurn> parseProviderAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }

        try {
            JsonNode turns = objectMapper.readTree(TtsWorkbenchJson.stripMarkdownFence(answer)).path("turns");
            if (!turns.isArray()) {
                return List.of();
            }

            List<AnnotatedSpeakerTurn> items = new ArrayList<>();
            for (JsonNode turn : turns) {
                String speaker = turn.path("speaker").asText("").trim();
                String text = turn.path("text").asText("").trim();
                if (!speaker.isBlank() && !text.isBlank()) {
                    items.add(new AnnotatedSpeakerTurn(speaker, text));
                }
            }
            return items;
        } catch (Exception ex) {
            return List.of();
        }
    }
}
