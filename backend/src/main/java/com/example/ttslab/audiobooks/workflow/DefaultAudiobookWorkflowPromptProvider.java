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
                .map(SpeakerVoice::toString)
                .collect(Collectors.joining(", "));

        return """
                Analyze this prose/dialogue text for the audiobook workflow.
                
                Return only JSON with this shape:
                {"projectTitle":"...","sourceLanguageCode":"...","speakers":[{"speakerName":"...","roleDescription":"...","voiceSuggestion":"..."}]}

                Project title rules:
                - Generate a short, compelling audiobook project title.
                - Prefer 3 to 8 words.
                - Use title case.
                - Do not wrap the title in quotes.
                - Do not repeat the full story text.

                Source language rules:
                - Detect the primary spoken/written language of the text.
                - Return only the detected source language as sourceLanguageCode.
                - Use a BCP-47 language code when possible, for example en-US, de-DE, pl-PL, fr-FR, es-ES, ja-JP.
                - Do not infer, choose, or mention any production language or TTS language.
                
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
        if (speakers == null || speakers.isEmpty()) {
            throw new IllegalArgumentException("speakers cannot be null or empty");
        }
        return """
            Split this audiobook text into ordered speaker turns.
    
            Return only JSON:
            {"turns":[{"speaker":"...","text":"..."}]}
    
            Allowed speakers:
            %s
    
            Rules for all cases:
            - Use only speakerName values from the allowed speakers list.
            - Do not invent speakers.
            - Preserve all content that should be read aloud.
            - Do not summarize or rewrite.
            - Split mixed dialogue and prose in reading order.
            - Speaker labels are structural metadata, not spoken text.
              Example: `Zephyr — "Hello"` -> speaker `Zephyr`, text `Hello`.
              Example: `Zephyr: "Hello"` -> speaker `Zephyr`, text `Hello`.
            - Always remove speaker labels and separators from the text field.
            - Quoted dialogue belongs to the speaking character if that character is allowed.
    
            Rules if "Narrator" is in the allowed speakers list:
            - Real narration, scene description, action beats, and dialogue attribution belong to "Narrator".
            - Speaker labels still do not belong to "Narrator".
            - Use "Narrator" only for prose that should actually be read aloud.
    
            Rules if "Narrator" is NOT in the allowed speakers list:
            - Do not create Narrator turns.
            - Ignore structural speaker labels such as `Zephyr —`, `Puck:`, or `Charon said:` when they only identify the speaker.
            - Keep only the spoken quoted text for character dialogue.
            - If there is real narration that cannot be assigned to an allowed speaker, omit it rather than inventing "Narrator".
            - If the speaking character is not allowed, use "Other" if "Other" is allowed. Otherwise omit that dialogue rather than inventing a speaker.
    
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


