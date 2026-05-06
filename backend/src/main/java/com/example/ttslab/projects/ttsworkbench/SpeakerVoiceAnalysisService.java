package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatService;
import com.example.ttslab.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SpeakerVoiceAnalysisService {
    private static final String PROVIDER_GEMINI = "gemini";
    private static final Logger log = LoggerFactory.getLogger(SpeakerVoiceAnalysisService.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final TtsWorkbenchPromptProvider promptProvider;
    private final DeterministicTtsWorkbenchFallbackService fallbackService;
    private final String chatbotProvider;

    public SpeakerVoiceAnalysisService(
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

    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.isBlank()) {
            log.debug("chatbotProvider:"+chatbotProvider);
            return new SpeakerVoiceAnalysisResponse(List.of());
        }

        if (!PROVIDER_GEMINI.equals(chatbotProvider)) {
            log.debug("chatbotProvider:"+chatbotProvider);
            return new SpeakerVoiceAnalysisResponse(fallbackService.analyzeSpeakers(rawDialogue));
        }

        try {
            String answer = chatService.ask(new ChatRequest(promptProvider.getSpeakerVoiceAnalysisPrompt(rawDialogue), null)).answer();
            return new SpeakerVoiceAnalysisResponse(parseProviderAnswer(answer));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TTS_WORKBENCH_PROVIDER_FAILED",
                "The speaker voice analysis provider is currently unavailable. Please try again later.",
                null,
                ex
            );
        }
    }

    private List<SpeakerVoiceAnalysisItem> parseProviderAnswer(String answer) {

        log.debug("start parsing answer:\n "+answer);

        if (answer == null || answer.isBlank()) {
            throw invalidProviderResponse(null);
        }

        try {
            JsonNode speakers = objectMapper.readTree(TtsWorkbenchJson.stripMarkdownFence(answer)).path("speakers");
            if (!speakers.isArray()) {
                throw invalidProviderResponse(null);
            }

            List<SpeakerVoiceAnalysisItem> items = new ArrayList<>();
            for (JsonNode speaker : speakers) {
                String speakerName = speaker.path("speakerName").asText("").trim();
                String roleDescription = speaker.path("roleDescription").asText("").trim();
                SpeakerVoice voiceSuggestion = SpeakerVoice.valueOf(speaker.path("voiceSuggestion").asText("").trim().toUpperCase());
                if (!speakerName.isBlank()) {
                    items.add(new SpeakerVoiceAnalysisItem(speakerName, roleDescription, voiceSuggestion));
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
            "The speaker voice analysis provider returned an invalid response. Please try again later.",
            null,
            cause
        );
    }
}
