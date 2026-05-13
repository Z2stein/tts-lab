package com.example.ttslab.audiobooks.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import org.springframework.stereotype.Component;

import static org.springframework.ai.util.json.JsonParser.toJson;

@Component
public class DefaultAudiobookWorkflowPromptProvider implements AudiobookWorkflowPromptProvider {
    private final ObjectMapper objectMapper;

    private static final List<String> KNOWN_TTS_MARKUP_STYLES = List.of(
            // Simple application-level emotions
            "[happy]",
            "[sad]",
            "[calm]",
            "[urgent]",

            // Common Gemini-style emotional / reaction tags
            "[amazed]",
            "[crying]",
            "[curious]",
            "[excited]",
            "[excitedly]",
            "[bored]",
            "[reluctantly]",
            "[scared]",
            "[panicked]",
            "[serious]",
            "[sarcastic]",
            "[sarcastically]",
            "[mischievously]",
            "[tired]",
            "[trembling]",

            // Voice delivery / speaking style
            "[whispers]",
            "[whispering]",
            "[shouting]",
            "[robotic]",
            "[very fast]",
            "[extremely fast]",
            "[very slow]",

            // Non-speech reactions
            "[sigh]",
            "[sighs]",
            "[gasp]",
            "[giggles]",
            "[laughs]",
            "[laughing]",
            "[uhm]",

            // Pauses
            "[short pause]",
            "[medium pause]",
            "[long pause]"
    );

    public DefaultAudiobookWorkflowPromptProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSpeakerVoiceAnalysisPrompt(String rawDialogue) {

        String availableVoices = Arrays.stream(SpeakerVoice.values())
                .map(v -> "Key: " + v.getKey() + " Style: " + v.getStyle())
                .collect(Collectors.joining(", "));

        return """
                Analyze this prose/dialogue text for the audiobook workflow.
                
                Return only JSON with this shape:
                {"speakers":[{"speakerName":"...","roleDescription":"...","voiceSuggestion":"..."}]}
                
                Important:
                - If the text contains narration, descriptions, action beats, dialogue attribution, or non-quoted prose, you MUST include a speaker named "Narrator".
                - "Narrator" is a pseudo-speaker for all prose that is not directly spoken by a character.
                - Character names should be used for quoted spoken dialogue.
                - Keep descriptions and voice suggestions short and practical.
                
                speakerName cannot contain whitespace or non-alphanumeric characters.
                
                Constraint for 'voiceSuggestion':
                You MUST use one of the uppercase keys from the list below.
                Do not include the style description in the JSON value.
                Available Voice Keys: %s
                
                Text:
                %s
                """.formatted(availableVoices, rawDialogue);
    }

    @Override
    public String getSpeakerSplitPrompt(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers) {
        return """
                Split this prose/dialogue text into ordered audiobook workflow turns.
                
                Return only JSON with this shape:
                {"turns":[{"speaker":"...","text":"..."}]}
                
                Critical preservation rules:
                - Preserve ALL original wording.
                - Do not summarize.
                - Do not remove narration.
                - Do not merge narration into character speech.
                - Every piece of non-quoted prose must become a turn spoken by "Narrator".
                - Quoted speech must become a turn spoken by the character who says it.
                - Dialogue attribution and action beats belong to "Narrator".
                  Example: Mara folded the letter twice, then unfolded it again.
                - If one paragraph contains narration and speech, split it in reading order.
                - Use the provided speakers when possible.
                - If narration exists and "Narrator" is not in the provided speakers, still use "Narrator".
                
                speaker cannot contain whitespace or non-alphanumeric characters.
                
                Speakers:
                %s
                
                Text:
                %s
                """.formatted(toJson(speakers), rawDialogue);
    }

    @Override
    public String getEmotionAnnotationPrompt(List<SpeakerSplitTurn> turns) {
        return """
                Add expressive audiobook workflow markup to these speaker turns.
                
                Goal:
                Make the dialogue sound lively, natural, and emotionally engaging.
                The spoken result should feel like performed dialogue, not plain reading.
                
                Use these known markup tokens:
                %s
                
                Markup rules:
                - Every SpeakerSplitTurn needs at least one audio style tag. Repiticion is allowed.  
                - Use markup actively when it improves emotion, rhythm, tension, humor, hesitation, surprise, or dramatic effect.
                - Prefer fitting emotional and reaction tags over neutral delivery.
                - You may combine multiple suitable tags in one turn if the scene benefits from it.
                - Use pauses to improve timing and dramatic rhythm.
                - Do not overload every sentence with markup.
                - Do not invent new markup tokens.
                - Keep the original wording mostly unchanged, but you may add small non-verbal reactions like [sigh], [gasp], [laughs], or pauses when appropriate.
                
                speaker cannot contain whitespace or non-alphanumeric characters.
                
                Return only JSON with this shape:
                {"turns":[{"speaker":"...","text":"..."}]}
                
                Turns:
                %s
                """.formatted(toKnownTtsMarkupStylesText(), toJson(turns));

    }

    private String toKnownTtsMarkupStylesText() {
        return String.join(", ", KNOWN_TTS_MARKUP_STYLES);
    }


    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}


