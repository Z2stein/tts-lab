package com.example.ttslab.projects.ttsworkbench;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void speakerSplitAnalysisReturnsTurns() throws Exception {
        when(ttsWorkbenchService.split("A: Hello", List.of())).thenReturn(new SpeakerSplitAnalysisResponse(List.of(
            new SpeakerSplitTurn("A", "Hello")
        )));

        mockMvc.perform(post("/api/projects/tts-workbench/speaker-split-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"A: Hello\",\"speakers\":[]}"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"turns":[{"speaker":"A","text":"Hello"}]}
                """));
    }

    @Test
    void emotionAnnotationAnalysisReturnsAnnotatedTurns() throws Exception {
        when(ttsWorkbenchService.annotate(List.of(new SpeakerSplitTurn("A", "Hello!")))).thenReturn(new EmotionAnnotationAnalysisResponse(List.of(
            new AnnotatedSpeakerTurn("A", "[urgent] Hello!")
        )));

        mockMvc.perform(post("/api/projects/tts-workbench/emotion-annotation-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"turns\":[{\"speaker\":\"A\",\"text\":\"Hello!\"}]}"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"turns":[{"speaker":"A","text":"[urgent] Hello!"}]}
                """));
    }

    @Test
    void finalRequestPreviewReturnsJsonShape() throws Exception {
        when(ttsWorkbenchService.buildFinalRequest(any(FinalTtsRequestPreviewRequest.class))).thenReturn(new FinalTtsRequestPreviewResponse(
            Map.of("prompt", "Prompt", "multiSpeakerMarkup", Map.of("turns", List.of())),
            Map.of("languageCode", "en-US", "modelName", "{{google-model}}", "multiSpeakerVoiceConfig", Map.of("speakerVoiceConfigs", List.of())),
            Map.of("audioEncoding", "MP3")
        ));

        mockMvc.perform(post("/api/projects/tts-workbench/final-request-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"Prompt\",\"speakers\":[],\"annotatedTurns\":[],\"languageCode\":\"en-US\",\"modelName\":\"{{google-model}}\",\"audioEncoding\":\"MP3\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"input":{"prompt":"Prompt","multiSpeakerMarkup":{"turns":[]}},"voice":{"languageCode":"en-US","modelName":"{{google-model}}","multiSpeakerVoiceConfig":{"speakerVoiceConfigs":[]}},"audioConfig":{"audioEncoding":"MP3"}}
                """));
    }
}
