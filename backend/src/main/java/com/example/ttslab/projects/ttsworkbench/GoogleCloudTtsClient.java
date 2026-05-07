package com.example.ttslab.projects.ttsworkbench;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleCloudTtsClient implements GoogleTtsClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleCloudTtsClient.class);
    private final String serviceAccountJsonBase64;

    public GoogleCloudTtsClient(
        @Value("${tts-workbench.google.service-account-json-b64:}") String serviceAccountJsonBase64
    ) {
        this.serviceAccountJsonBase64 = serviceAccountJsonBase64 == null ? "" : serviceAccountJsonBase64.trim();
    }

    @Override
    public byte[] synthesize(SingleSpeakerRenderRequest request) {
        try (TextToSpeechClient client = TextToSpeechClient.create(settings())) {
            log.debug("sending Request to Google:" +request);
            SynthesizeSpeechResponse response = client.synthesizeSpeech(input(request.input()), voice(request.voice()), audioConfig(request.audioConfig()));
            return response.getAudioContent().toByteArray();
        } catch (TtsAudioCreationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech client failed", ex, false);
        } catch (RuntimeException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech request failed", ex, false);
        }
    }

    private TextToSpeechSettings settings() throws IOException {
        return TextToSpeechSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials()))
            .build();
    }

    private GoogleCredentials credentials() {
        if (serviceAccountJsonBase64.isBlank()) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech credentials are not configured", null, true);
        }

        try {
            byte[] serviceAccountJson = Base64.getDecoder().decode(serviceAccountJsonBase64);
            return GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson));
        } catch (IllegalArgumentException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech credentials are not valid Base64", ex, true);
        } catch (IOException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech credentials are not valid service account JSON", ex, true);
        }
    }

    private SynthesisInput input(Map<String, Object> input) {
        return SynthesisInput.newBuilder()
            .setText(stringValue(input, "text"))
            .build();
    }

    private VoiceSelectionParams voice(Map<String, Object> voice) {
        VoiceSelectionParams.Builder builder = VoiceSelectionParams.newBuilder()
            .setLanguageCode(stringValue(voice, "languageCode"));
        String name = stringValue(voice, "name");
        if (!name.isBlank()) {
            builder.setName(name);
        }
        return builder.build();
    }

    private AudioConfig audioConfig(Map<String, Object> audioConfig) {
        String encoding = stringValue(audioConfig, "audioEncoding");
        return AudioConfig.newBuilder()
            .setAudioEncoding(encoding.isBlank() ? AudioEncoding.MP3 : AudioEncoding.valueOf(encoding))
            .build();
    }

    private String stringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }
}
