package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.config.AudiobookWorkflowGoogleProperties;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GoogleCloudTtsClient implements GoogleTtsClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleCloudTtsClient.class);
    private final String serviceAccountJsonBase64;

    @Autowired
    public GoogleCloudTtsClient(ObjectProvider<AudiobookWorkflowGoogleProperties> propertiesProvider) {
        this(propertiesProvider == null || propertiesProvider.getIfAvailable() == null
            ? ""
            : propertiesProvider.getIfAvailable().serviceAccountJsonB64());
    }

    public GoogleCloudTtsClient(String serviceAccountJsonBase64) {
        this.serviceAccountJsonBase64 = serviceAccountJsonBase64 == null ? "" : serviceAccountJsonBase64.trim();
    }

    @Override
    public byte[] synthesize(AudiobookProject project, int targetSegmentIndex) {
        try (TextToSpeechClient client = TextToSpeechClient.create(settings())) {
            SynthesizeSpeechRequest synthesizeSpeechRequest = SynthesizeSpeechRequest.newBuilder()
                    .setInput(parseToGoogleInput(project.getSpeechSegments().get(targetSegmentIndex)))
                    .setVoice(parseToGoogleVoice(project,targetSegmentIndex))
                    .setAudioConfig(parseToGoogleAudioConfig(project))
                    .build();

            log.debug("sending Request to Google:" +synthesizeSpeechRequest.toString());

            SynthesizeSpeechResponse response = client.synthesizeSpeech(synthesizeSpeechRequest);

            return response.getAudioContent().toByteArray();
        } catch (TtsAudioCreationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech client failed", ex, false);
        } catch (RuntimeException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech project failed", ex, false);
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

    private SynthesisInput parseToGoogleInput(AudiobookSpeechSegment speechSegment) {
        log.debug("speechSegment:" + speechSegment.toString());
        return SynthesisInput.newBuilder()
            .setText(speechSegment.getStyledText())
            .build();
    }

    private VoiceSelectionParams parseToGoogleVoice( AudiobookProject project, int targetSegmentIndex) {
        log.debug("project:" + project.toString());
        VoiceSelectionParams.Builder builder = VoiceSelectionParams.newBuilder()
            .setLanguageCode(project.getProductionLanguageCode())
            .setModelName(project.getProductionModelName())
            .setName(project.getSpeechSegments().get(targetSegmentIndex).getCharacter().getVoiceSuggestion().getKey());
        return builder.build();
    }

    private AudioConfig parseToGoogleAudioConfig(AudiobookProject project) {
        log.debug("project:" + project.toString());
        String encoding = project.getProductionAudioEncoding();
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


