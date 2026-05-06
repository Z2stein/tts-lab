package com.example.ttslab.projects.ttsworkbench;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(TtsWorkbenchController.class)
class TtsWorkbenchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TtsWorkbenchService ttsWorkbenchService;

    @Test
    void speakerVoiceAnalysisReturnsSuggestedVoices() throws Exception {
        when(ttsWorkbenchService.analyze("Alice: Hello")).thenReturn(new SpeakerVoiceAnalysisResponse(List.of(
            new SpeakerVoiceAnalysisItem("Alice", "Detected dialogue speaker", "Warm neutral voice")
        )));

        mockMvc.perform(post("/api/projects/tts-workbench/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice: Hello\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"speakers":[{"speakerName":"Alice","roleDescription":"Detected dialogue speaker","voiceSuggestion":"Warm neutral voice"}]}
                """));
    }
}
