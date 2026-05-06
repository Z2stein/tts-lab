package com.example.ttslab.projects.ttsworkbench;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TtsWorkbenchServiceTest {

    private final TtsWorkbenchService service = new TtsWorkbenchService();

    @Test
    void analyzeSpeakerVoicesReturnsEmptyListForNull() {
        assertEquals(0, service.analyzeSpeakerVoices(null).speakers().size());
    }

    @Test
    void analyzeSpeakerVoicesReturnsEmptyListForBlankText() {
        assertEquals(0, service.analyzeSpeakerVoices("  \n\t ").speakers().size());
    }

    @Test
    void analyzeSpeakerVoicesReturnsUniqueSpeakersInInputOrder() {
        SpeakerVoiceAnalysisResponse response = service.analyzeSpeakerVoices("Alice: Hello\nBob: Hi\nAlice: Bye");

        assertEquals(2, response.speakers().size());
        assertEquals("Alice", response.speakers().get(0).speakerName());
        assertEquals("Bob", response.speakers().get(1).speakerName());
        assertEquals("Dialogue speaker detected from the script.", response.speakers().get(0).roleDescription());
        assertEquals("Use a clear, natural voice and adjust tone based on the line context.", response.speakers().get(0).voiceSuggestion());
    }
}
