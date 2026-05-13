package com.example.ttslab.audiobooks.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleSpeakerRenderPlannerTest {
    private final SingleSpeakerRenderPlanner planner = new SingleSpeakerRenderPlanner(new ObjectMapper());

    @Test
    void groupsOnlyConsecutiveTurnsFromSameSpeakerIntoProviderRenderRequests() {
        SingleSpeakerRenderPlanResponse response = planner.plan(exampleRequest());

        assertEquals(4, response.renderRequests().size());
        assertRenderRequest(response.renderRequests().get(0), "Schedar", "The rain hit the windows.\nThe café was nearly empty.");
        assertRenderRequest(response.renderRequests().get(1), "Kore", "So this is your surprise?");
        assertRenderRequest(response.renderRequests().get(2), "Iapetus", "I thought you would be pleased.");
        assertRenderRequest(response.renderRequests().get(3), "Kore", "Pleased is a generous word.");
    }

    @Test
    void everyRenderRequestUsesProviderRequestShapeAndSettings() {
        SingleSpeakerRenderPlanResponse response = planner.plan(exampleRequest());

        for (SingleSpeakerRenderRequest renderRequest : response.renderRequests()) {
            assertEquals("en-US", renderRequest.voice().get("languageCode"));
            assertEquals("{{google-model}}", renderRequest.voice().get("modelName"));
            assertEquals("MP3", renderRequest.audioConfig().get("audioEncoding"));
        }
    }

    @Test
    void turnOrderIsPreservedWithoutLossOrDuplication() {
        SingleSpeakerRenderPlanResponse response = planner.plan(exampleRequest());

        List<String> texts = response.renderRequests().stream()
            .map(renderRequest -> (String) renderRequest.input().get("text"))
            .toList();

        assertEquals(List.of(
            "The rain hit the windows.\nThe café was nearly empty.",
            "So this is your surprise?",
            "I thought you would be pleased.",
            "Pleased is a generous word."
        ), texts);
    }

    @Test
    void missingVoiceConfigFallsBackToBlankVoiceName() {
        SingleSpeakerRenderPlanResponse response = planner.plan(new SingleSpeakerRenderPlanRequest(
            Map.of("multiSpeakerMarkup", Map.of("turns", List.of(Map.of("speaker", "Unknown", "text", "Hello")))),
            Map.of("languageCode", "en-US", "modelName", "model", "multiSpeakerVoiceConfig", Map.of("speakerVoiceConfigs", List.of())),
            Map.of("audioEncoding", "MP3")
        ));

        assertEquals("", response.renderRequests().getFirst().voice().get("name"));
    }

    @Test
    void nullAndEmptyInputReturnsNoRenderRequests() {
        assertTrue(planner.plan(null).renderRequests().isEmpty());
        assertTrue(planner.plan(new SingleSpeakerRenderPlanRequest(null, null, null)).renderRequests().isEmpty());
        assertTrue(planner.plan(new SingleSpeakerRenderPlanRequest(Map.of(), Map.of(), Map.of())).renderRequests().isEmpty());
    }

    private void assertRenderRequest(SingleSpeakerRenderRequest renderRequest, String voiceName, String text) {
        assertEquals(Map.of("text", text), renderRequest.input());
        assertEquals("en-US", renderRequest.voice().get("languageCode"));
        assertEquals(voiceName, renderRequest.voice().get("name"));
        assertEquals("{{google-model}}", renderRequest.voice().get("modelName"));
        assertEquals(Map.of("audioEncoding", "MP3"), renderRequest.audioConfig());
    }

    private SingleSpeakerRenderPlanRequest exampleRequest() {
        return new SingleSpeakerRenderPlanRequest(
            Map.of(
                "prompt", "A tense café conversation.",
                "multiSpeakerMarkup", Map.of("turns", List.of(
                    Map.of("speaker", "Narrator", "text", "The rain hit the windows."),
                    Map.of("speaker", "Narrator", "text", "The café was nearly empty."),
                    Map.of("speaker", "Mara", "text", "So this is your surprise?"),
                    Map.of("speaker", "Jonas", "text", "I thought you would be pleased."),
                    Map.of("speaker", "Mara", "text", "Pleased is a generous word.")
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

