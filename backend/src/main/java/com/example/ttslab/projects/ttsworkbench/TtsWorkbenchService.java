package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TtsWorkbenchService {
    private static final String PROVIDER_GEMINI = "gemini";
    private static final Pattern SPEAKER_LINE = Pattern.compile("^\\s*([\\p{L}][\\p{L}0-9 ._'’-]{0,40})\\s*[:：-]\\s+(.+)\\s*$");
    private static final List<String> MOCK_VOICES = List.of(
        "Warm neutral voice",
        "Clear energetic voice",
        "Calm lower-pitched voice",
        "Bright conversational voice"
    );

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final String chatbotProvider;

    public TtsWorkbenchService(
        ChatService chatService,
        ObjectMapper objectMapper,
        @Value("${chatbot.provider:mock}") String chatbotProvider
    ) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.chatbotProvider = chatbotProvider == null ? "mock" : chatbotProvider.trim().toLowerCase();
    }

    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.isBlank()) {
            return new SpeakerVoiceAnalysisResponse(List.of());
        }

        if (!PROVIDER_GEMINI.equals(chatbotProvider)) {
            return new SpeakerVoiceAnalysisResponse(mockAnalyze(rawDialogue));
        }

        String answer = chatService.ask(new ChatRequest(buildPrompt(rawDialogue), null)).answer();
        List<SpeakerVoiceAnalysisItem> parsed = parseProviderAnswer(answer);
        if (parsed.isEmpty()) {
            return new SpeakerVoiceAnalysisResponse(mockAnalyze(rawDialogue));
        }

        return new SpeakerVoiceAnalysisResponse(parsed);
    }

    private String buildPrompt(String rawDialogue) {
        return """
            Analyze this raw dialogue for a text-to-speech workbench.
            Return only JSON with this shape:
            {"speakers":[{"speakerName":"...","roleDescription":"...","voiceSuggestion":"..."}]}
            Keep descriptions and voice suggestions short and practical.

            Dialogue:
            """ + rawDialogue;
    }

    private List<SpeakerVoiceAnalysisItem> mockAnalyze(String rawDialogue) {
        Map<String, Integer> speakerOrder = new LinkedHashMap<>();
        for (String line : rawDialogue.split("\\R")) {
            Matcher matcher = SPEAKER_LINE.matcher(line);
            if (matcher.matches()) {
                String speakerName = matcher.group(1).trim();
                speakerOrder.putIfAbsent(speakerName, speakerOrder.size());
            }
        }

        if (speakerOrder.isEmpty()) {
            return List.of(new SpeakerVoiceAnalysisItem(
                "Narrator",
                "Narrates dialogue without explicit speaker labels",
                MOCK_VOICES.getFirst()
            ));
        }

        List<SpeakerVoiceAnalysisItem> items = new ArrayList<>();
        speakerOrder.forEach((speakerName, index) -> items.add(new SpeakerVoiceAnalysisItem(
            speakerName,
            "Detected dialogue speaker",
            MOCK_VOICES.get(index % MOCK_VOICES.size())
        )));
        return items;
    }

    private List<SpeakerVoiceAnalysisItem> parseProviderAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }

        try {
            JsonNode speakers = objectMapper.readTree(stripMarkdownFence(answer)).path("speakers");
            if (!speakers.isArray()) {
                return List.of();
            }

            List<SpeakerVoiceAnalysisItem> items = new ArrayList<>();
            for (JsonNode speaker : speakers) {
                String speakerName = speaker.path("speakerName").asText("").trim();
                String roleDescription = speaker.path("roleDescription").asText("").trim();
                String voiceSuggestion = speaker.path("voiceSuggestion").asText("").trim();
                if (!speakerName.isBlank()) {
                    items.add(new SpeakerVoiceAnalysisItem(speakerName, roleDescription, voiceSuggestion));
                }
            }
            return items;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String stripMarkdownFence(String answer) {
        String trimmed = answer.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLineBreak = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineBreak < 0 || lastFence <= firstLineBreak) {
            return trimmed;
        }
        return trimmed.substring(firstLineBreak + 1, lastFence).trim();
    }
}
