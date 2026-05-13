package com.example.ttslab.audiobooks.workflow;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCloudTtsClientTest {
    @Test
    void missingBase64CredentialsFailBeforeProviderCall() {
        GoogleCloudTtsClient client = new GoogleCloudTtsClient(" ");

        TtsAudioCreationException exception = assertThrows(TtsAudioCreationException.class, () -> client.synthesize(renderRequest()));

        assertTrue(exception.configurationError());
    }

    @Test
    void invalidBase64CredentialsFailBeforeProviderCall() {
        GoogleCloudTtsClient client = new GoogleCloudTtsClient("not base64!");

        TtsAudioCreationException exception = assertThrows(TtsAudioCreationException.class, () -> client.synthesize(renderRequest()));

        assertTrue(exception.configurationError());
    }

    private SingleSpeakerRenderRequest renderRequest() {
        return new SingleSpeakerRenderRequest(
            Map.of("text", "Hello"),
            Map.of("languageCode", "en-US", "name", "Kore"),
            Map.of("audioEncoding", "MP3")
        );
    }
}

