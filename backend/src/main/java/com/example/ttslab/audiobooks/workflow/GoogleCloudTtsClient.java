package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.config.AudiobookWorkflowGoogleProperties;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GoogleCloudTtsClient implements GoogleTtsClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleCloudTtsClient.class);

    // Google rejects requests above 4000 UTF-8 bytes; stay below that with a safety margin.
    private static final int MAX_INPUT_BYTES = 3800;

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
            AudiobookSpeechSegment segment = project.getSpeechSegments().get(targetSegmentIndex);
            VoiceSelectionParams voice = parseToGoogleVoice(project, targetSegmentIndex);
            AudioConfig audioConfig = parseToGoogleAudioConfig(project);
            List<String> chunks = TtsTextChunker.chunkByUtf8Bytes(segment.getStyledText(), MAX_INPUT_BYTES);

            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            for (String chunk : chunks) {
                SynthesizeSpeechRequest synthesizeSpeechRequest = SynthesizeSpeechRequest.newBuilder()
                        .setInput(SynthesisInput.newBuilder().setText(chunk).build())
                        .setVoice(voice)
                        .setAudioConfig(audioConfig)
                        .build();

                log.debug("sending Request to Google:" + synthesizeSpeechRequest.toString());

                SynthesizeSpeechResponse response = client.synthesizeSpeech(synthesizeSpeechRequest);
                audio.writeBytes(response.getAudioContent().toByteArray());
            }

            return audio.toByteArray();
        } catch (TtsAudioCreationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech client failed", ex, TtsAudioCreationException.Kind.PROVIDER);
        } catch (RuntimeException ex) {
            throw mapProviderException(ex);
        }
    }

    private TtsAudioCreationException mapProviderException(RuntimeException ex) {
        if (isInputTooLarge(ex)) {
            return new TtsAudioCreationException(
                "Google Cloud Text-to-Speech input exceeds the provider size limit",
                ex,
                TtsAudioCreationException.Kind.INPUT_TOO_LARGE
            );
        }
        return new TtsAudioCreationException(
            "Google Cloud Text-to-Speech project failed",
            ex,
            TtsAudioCreationException.Kind.PROVIDER
        );
    }

    private static boolean isInputTooLarge(Throwable ex) {
        for (Throwable current = ex; current != null && current != current.getCause(); current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("longer than the limit")) {
                return true;
            }
        }
        return false;
    }

    private TextToSpeechSettings settings() throws IOException {
        return TextToSpeechSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials()))
            .build();
    }

    private GoogleCredentials credentials() {
        if (serviceAccountJsonBase64.isBlank()) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech credentials are not configured", null, TtsAudioCreationException.Kind.CONFIGURATION);
        }

        try {
            byte[] serviceAccountJson = Base64.getDecoder().decode(serviceAccountJsonBase64);
            return GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson));
        } catch (IllegalArgumentException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech credentials are not valid Base64", ex, TtsAudioCreationException.Kind.CONFIGURATION);
        } catch (IOException ex) {
            throw new TtsAudioCreationException("Google Cloud Text-to-Speech credentials are not valid service account JSON", ex, TtsAudioCreationException.Kind.CONFIGURATION);
        }
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
}


