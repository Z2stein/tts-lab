package com.example.ttslab.projects.ttsworkbench;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultTtsWorkbenchPromptProvider implements TtsWorkbenchPromptProvider {
    private final ObjectMapper objectMapper;

    public DefaultTtsWorkbenchPromptProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSpeakerVoiceAnalysisPrompt(String rawDialogue) {
        return """
            Analyze this raw dialogue for a text-to-speech workbench.
            Return only JSON with this shape:
            {"speakers":[{"speakerName":"...","roleDescription":"...","voiceSuggestion":"..."}]}
            Keep descriptions and voice suggestions short and practical.

            Dialogue:
            """ + rawDialogue;
    }

    @Override
    public String getSpeakerSplitPrompt(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers) {
        return """
            Split this raw dialogue into text-to-speech speaker turns.
            Return only JSON with this shape:
            {"turns":[{"speaker":"...","text":"..."}]}
            Use the provided speakers when possible and keep original wording.

            Speakers:
            %s

            Dialogue:
            %s
            """.formatted(toJson(speakers), rawDialogue);
    }

    @Override
    public String getEmotionAnnotationPrompt(List<SpeakerSplitTurn> turns) {
        return """
            Add simple text-to-speech markup to these speaker turns.
            Use only these markup tokens when helpful: [happy], [sad], [calm], [urgent], [sigh], [short pause], [medium pause].
            Return only JSON with this shape:
            {"turns":[{"speaker":"...","text":"..."}]}

            Turns:
            %s
            """.formatted(toJson(turns));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
