package com.example.ttslab.projects.ttsworkbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCompatibleRequestPlannerTest {
    private final ProviderCompatibleRequestPlanner planner = new ProviderCompatibleRequestPlanner(new ObjectMapper());

    @Test
    void threeSpeakerRequestIsSplitIntoProviderCompatibleChunks() {
        ProviderCompatibleRequestPlanResponse response = planner.plan(exampleRequest());

        assertEquals(3, response.chunks().size());
        assertEquals(List.of("Narrator", "Mara"), response.chunks().get(0).speakers());
        assertEquals(List.of("Jonas", "Mara"), response.chunks().get(1).speakers());
        assertEquals(List.of("Narrator"), response.chunks().get(2).speakers());
        assertTrue(response.chunks().stream()
            .allMatch(chunk -> chunk.speakers().size() <= ProviderCompatibleRequestPlanner.PROVIDER_SUPPORTED_SPEAKER_LIMIT));
    }

    @Test
    void everyChunkContainsOnlyRequiredSpeakerConfigs() {
        ProviderCompatibleRequestPlanResponse response = planner.plan(exampleRequest());

        assertEquals(List.of("Narrator", "Mara"), speakerConfigAliases(response.chunks().get(0)));
        assertEquals(List.of("Mara", "Jonas"), speakerConfigAliases(response.chunks().get(1)));
        assertEquals(List.of("Narrator"), speakerConfigAliases(response.chunks().get(2)));
    }

    @Test
    void turnOrderIsPreservedWithoutLossOrDuplication() {
        ProviderCompatibleRequestPlanResponse response = planner.plan(exampleRequest());

        List<String> text = response.chunks().stream()
            .flatMap(chunk -> turns(chunk).stream())
            .map(turn -> (String) turn.get("text"))
            .toList();

        assertEquals(List.of(
            "The rain hit the windows.",
            "So this is your surprise?",
            "I thought you would be pleased.",
            "Pleased is a generous word.",
            "Jonas looked away."
        ), text);
    }

    @Test
    void chunksPreserveSharedProviderRequestSettings() {
        ProviderCompatibleRequestPlanResponse response = planner.plan(exampleRequest());

        for (ProviderCompatibleRequestChunk chunk : response.chunks()) {
            assertEquals("A tense café conversation.", chunk.request().input().get("prompt"));
            assertEquals("en-US", chunk.request().voice().get("languageCode"));
            assertEquals("{{google-model}}", chunk.request().voice().get("modelName"));
            assertEquals("MP3", chunk.request().audioConfig().get("audioEncoding"));
        }
    }

    @Test
    void nullAndEmptyInputReturnsNoChunks() {
        assertTrue(planner.plan(null).chunks().isEmpty());
        assertTrue(planner.plan(new ProviderCompatibleRequestPlanRequest(null, null, null)).chunks().isEmpty());
        assertTrue(planner.plan(new ProviderCompatibleRequestPlanRequest(Map.of(), Map.of(), Map.of())).chunks().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> turns(ProviderCompatibleRequestChunk chunk) {
        Map<String, Object> markup = (Map<String, Object>) chunk.request().input().get("multiSpeakerMarkup");
        return (List<Map<String, Object>>) markup.get("turns");
    }

    @SuppressWarnings("unchecked")
    private List<String> speakerConfigAliases(ProviderCompatibleRequestChunk chunk) {
        Map<String, Object> multiSpeakerVoiceConfig = (Map<String, Object>) chunk.request().voice().get("multiSpeakerVoiceConfig");
        List<Map<String, Object>> configs = (List<Map<String, Object>>) multiSpeakerVoiceConfig.get("speakerVoiceConfigs");
        return configs.stream()
            .map(config -> (String) config.get("speakerAlias"))
            .toList();
    }

    private ProviderCompatibleRequestPlanRequest exampleRequest() {
        return new ProviderCompatibleRequestPlanRequest(
            Map.of(
                "prompt", "A tense café conversation.",
                "multiSpeakerMarkup", Map.of("turns", List.of(
                    Map.of("speaker", "Narrator", "text", "The rain hit the windows."),
                    Map.of("speaker", "Mara", "text", "So this is your surprise?"),
                    Map.of("speaker", "Jonas", "text", "I thought you would be pleased."),
                    Map.of("speaker", "Mara", "text", "Pleased is a generous word."),
                    Map.of("speaker", "Narrator", "text", "Jonas looked away.")
                ))
            ),
            Map.of(
                "languageCode", "en-US",
                "modelName", "{{google-model}}",
                "multiSpeakerVoiceConfig", Map.of("speakerVoiceConfigs", List.of(
                    Map.of("speakerAlias", "Narrator", "speakerId", "Schedar"),
                    Map.of("speakerAlias", "Mara", "speakerId", "Kore"),
                    Map.of("speakerAlias", "Jonas", "speakerId", "Iapetus")
                ))
            ),
            Map.of("audioEncoding", "MP3")
        );
    }
}
