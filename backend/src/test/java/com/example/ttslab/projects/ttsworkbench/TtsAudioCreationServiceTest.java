package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.example.ttslab.projects.ttsworkbench.service.TtsAudioCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TtsAudioCreationServiceTest {
    @Test
    void mockProviderReturnsDeterministicMp3WithoutCallingGoogleTts() {
        GoogleTtsClient googleTtsClient = mock(GoogleTtsClient.class);
        TtsAudioCreationService service = new TtsAudioCreationService(provider(googleTtsClient), "mock");

        TtsAudioFile audioFile = service.createAudio(new SingleSpeakerRenderPlanResponse(List.of(renderRequest("Hello"))));

        assertEquals("audio/mpeg", audioFile.contentType());
        assertEquals("tts-render-request-1.mp3", audioFile.filename());
        assertArrayEquals(new byte[] {'I', 'D', '3'}, new byte[] {audioFile.content()[0], audioFile.content()[1], audioFile.content()[2]});
        assertTrue(new String(audioFile.content(), StandardCharsets.UTF_8).contains("TTS-LAB-MOCK-MP3"));
        assertTrue(new String(audioFile.content(), StandardCharsets.UTF_8).contains("Hello"));
        verify(googleTtsClient, never()).synthesize(any(SingleSpeakerRenderRequest.class));
    }

    @Test
    void geminiProviderCallsGoogleTtsClientForSingleRequest() {
        GoogleTtsClient googleTtsClient = mock(GoogleTtsClient.class);
        byte[] mp3 = new byte[] {'I', 'D', '3', 1};
        when(googleTtsClient.synthesize(any(SingleSpeakerRenderRequest.class))).thenReturn(mp3);
        TtsAudioCreationService service = new TtsAudioCreationService(provider(googleTtsClient), "gemini");

        TtsAudioFile audioFile = service.createAudio(new SingleSpeakerRenderPlanResponse(List.of(renderRequest("Real audio"))));

        assertEquals("audio/mpeg", audioFile.contentType());
        assertArrayEquals(mp3, audioFile.content());
        verify(googleTtsClient).synthesize(renderRequest("Real audio"));
    }

    @Test
    void multipleRequestsReturnSingleConcatenatedMp3() {
        TtsAudioCreationService service = new TtsAudioCreationService(provider(null), "mock");

        TtsAudioFile audioFile = service.createAudio(new SingleSpeakerRenderPlanResponse(List.of(
            renderRequest("First"),
            renderRequest("Second")
        )));

        assertEquals("audio/mpeg", audioFile.contentType());
        assertEquals("tts-render-plan.mp3", audioFile.filename());
        String content = new String(audioFile.content(), StandardCharsets.UTF_8);
        assertTrue(content.contains("First"));
        assertTrue(content.contains("Second"));
        assertTrue(content.indexOf("First") < content.indexOf("Second"));
    }

    @Test
    void emptyRequestsReturnStructuredValidationError() {
        TtsAudioCreationService service = new TtsAudioCreationService(provider(null), "mock");

        ApiException exception = assertThrows(ApiException.class, () -> service.createAudio(new SingleSpeakerRenderPlanResponse(List.of())));

        assertEquals("TTS_AUDIO_RENDER_REQUESTS_REQUIRED", exception.code());
        assertEquals(400, exception.status().value());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<GoogleTtsClient> provider(GoogleTtsClient googleTtsClient) {
        ObjectProvider<GoogleTtsClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(googleTtsClient);
        return provider;
    }

    private SingleSpeakerRenderRequest renderRequest(String text) {
        return new SingleSpeakerRenderRequest(
            Map.of("text", text),
            Map.of("languageCode", "en-US", "name", "Kore", "modelName", "{{google-model}}"),
            Map.of("audioEncoding", "MP3")
        );
    }
}
