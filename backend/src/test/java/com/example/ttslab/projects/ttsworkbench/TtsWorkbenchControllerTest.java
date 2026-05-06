package com.example.ttslab.projects.ttsworkbench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(TtsWorkbenchController.class)
class TtsWorkbenchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TtsWorkbenchService ttsWorkbenchService;

    @Test
    void speakerVoiceAnalysisReturnsSpeakers() throws Exception {
        when(ttsWorkbenchService.analyzeSpeakerVoices("Alice: Hello"))
            .thenReturn(new SpeakerVoiceAnalysisResponse(List.of(new SpeakerVoiceSuggestion(
                "Alice",
                "Narrator",
                "Warm voice"
            ))));

        mockMvc.perform(post("/api/projects/tts-workbench/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice: Hello\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.speakers[0].speakerName").value("Alice"))
            .andExpect(jsonPath("$.speakers[0].roleDescription").value("Narrator"))
            .andExpect(jsonPath("$.speakers[0].voiceSuggestion").value("Warm voice"));
    }

    @Test
    void speakerVoiceAnalysisReturnsEmptySpeakersWhenRawDialogueIsMissing() throws Exception {
        when(ttsWorkbenchService.analyzeSpeakerVoices(isNull()))
            .thenReturn(new SpeakerVoiceAnalysisResponse(List.of()));

        mockMvc.perform(post("/api/projects/tts-workbench/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"speakers\":[]}"));
    }

    @Test
    void speakerVoiceAnalysisRejectsInvalidJsonPayload() throws Exception {
        mockMvc.perform(post("/api/projects/tts-workbench/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice\""))
            .andExpect(status().isBadRequest());
    }
}
