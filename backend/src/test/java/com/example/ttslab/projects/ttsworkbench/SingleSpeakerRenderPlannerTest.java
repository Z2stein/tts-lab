package com.example.ttslab.projects.ttsworkbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleSpeakerRenderPlannerTest {
    private final SingleSpeakerRenderPlanner planner = new SingleSpeakerRenderPlanner(new ObjectMapper());

    @Test
    void groupsOnlyConsecutiveTurnsFromSameSpeakerIntoSingleSpeakerRenderRequests() {
        SingleSpeakerRenderPlanResponse response = planner.plan(exampleRequest());

        assertEquals(4, response.renderRequests().size());
        assertRenderRequest(response.renderRequests().get(0), 1, List.of(0, 1), "Narrator", "Schedar", "The rain hit the windows.\nThe café was nearly empty.");
        assertRenderRequest(response.renderRequests().get(1), 2, List.of(2), "Mara", "Kore", "So this is your surprise?");
        assertRenderRequest(response.renderRequests().get(2), 3, List.of(3), "Jonas", "Iapetus", "I thought you would be pleased.");
        assertRenderRequest(response.renderRequests().get(3), 4, List.of(4), "Mara", "Kore", "Pleased is a generous word.");
    }

    @Test
    void everyRenderRequestIncludesProviderSettings() {
        SingleSpeakerRenderPlanResponse response = planner.plan(exampleRequest());

        for (SingleSpeakerRenderRequest renderRequest : response.renderRequests()) {
            assertEquals("en-US", renderRequest.languageCode());
            assertEquals("{{google-model}}", renderRequest.modelName());
            assertEquals("MP3", renderRequest.audioEncoding());
        }
    }

    @Test
    void turnOrderIsPreservedWithoutLossOrDuplication() {
        SingleSpeakerRenderPlanResponse response = planner.plan(exampleRequest());

        List<Integer> originalTurnIndexes = response.renderRequests().stream()
            .flatMap(renderRequest -> renderRequest.originalTurnIndexes().stream())
            .toList();

        assertEquals(List.of(0, 1, 2, 3, 4), originalTurnIndexes);
    }

    @Test
    void missingVoiceConfigFallsBackToBlankVoiceId() {
        SingleSpeakerRenderPlanResponse response = planner.plan(new SingleSpeakerRenderPlanRequest(
            Map.of("multiSpeakerMarkup", Map.of("turns", List.of(Map.of("speaker", "Unknown", "text", "Hello")))),
            Map.of("languageCode", "en-US", "modelName", "model", "multiSpeakerVoiceConfig", Map.of("speakerVoiceConfigs", List.of())),
            Map.of("audioEncoding", "MP3")
        ));

        assertEquals("", response.renderRequests().getFirst().voiceId());
    }

    @Test
    void nullAndEmptyInputReturnsNoRenderRequests() {
        assertTrue(planner.plan(null).renderRequests().isEmpty());
        assertTrue(planner.plan(new SingleSpeakerRenderPlanRequest(null, null, null)).renderRequests().isEmpty());
        assertTrue(planner.plan(new SingleSpeakerRenderPlanRequest(Map.of(), Map.of(), Map.of())).renderRequests().isEmpty());
    }

    private void assertRenderRequest(
        SingleSpeakerRenderRequest renderRequest,
        int renderIndex,
        List<Integer> originalTurnIndexes,
        String speakerName,
        String voiceId,
        String text
    ) {
        assertEquals(renderIndex, renderRequest.renderIndex());
        assertEquals(originalTurnIndexes, renderRequest.originalTurnIndexes());
        assertEquals(speakerName, renderRequest.speakerName());
        assertEquals(voiceId, renderRequest.voiceId());
        assertEquals(text, renderRequest.text());
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
